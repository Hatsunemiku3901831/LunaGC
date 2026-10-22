package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify.ServerBuffChangeType;
import emu.grasscutter.net.proto.ServerBuffOuterClass.ServerBuff;
import java.util.*;

/** One removable source per selected card, independent of normal ability/modifier instance IDs. */
public final class TowerBlessingEffects {
    private final Player player;
    private final Set<Integer> issued = new HashSet<>();
    private final List<Slot> slots = new ArrayList<>();
    private final Map<EntityAvatar, List<Layer>> layers = new IdentityHashMap<>();
    private final Set<EntityMonster> rewardedKills = Collections.newSetFromMap(new WeakHashMap<>());
    private Scene scene;
    private List<Integer> cards = List.of();

    private record Slot(int uid, int buffId, String root, AbilityData data) {}

    private static final class Layer {
        final Slot slot;
        final Ability ability;
        final Set<String> allowed = new HashSet<>();
        final Map<Integer, String> nativeInstances = new HashMap<>();
        final Set<String> statChildren = new HashSet<>();
        final Map<String, Long> procEnds = new HashMap<>();
        long lastBurst = Long.MIN_VALUE;

        Layer(Slot slot, EntityAvatar target, Player player) {
            this.slot = slot;
            ability = new Ability(slot.data, target, player);
            collect(slot.root);
            ability.registerModifier(
                    target,
                    new AbilityModifierController(ability, slot.data, slot.data.modifiers.get(slot.root)));
        }

        void collect(String name) {
            if (name == null || !allowed.add(name)) return;
            var modifier = slot.data.modifiers.get(name);
            if (modifier == null) return;
            actions(modifier.onAdded);
            actions(modifier.onRemoved);
            if (modifier.modifierMixins != null)
                for (var mixin : modifier.modifierMixins) {
                    if (mixin == null) continue;
                    if (mixin.modifierName != null) for (var child : mixin.getModifierNames()) collect(child);
                    actions(mixin.actions);
                    actions(mixin.actionQueue);
                    actions(mixin.onKill);
                }
        }

        void actions(AbilityModifierAction[] actions) {
            if (actions == null) return;
            for (var action : actions)
                if (action != null) {
                    if (action.type == AbilityModifierAction.Type.ApplyModifier
                            || action.type == AbilityModifierAction.Type.AttachModifier)
                        collect(action.modifierName);
                    actions(action.successActions);
                    actions(action.failActions);
                    actions(action.actions);
                }
        }

        void refreshConditions(EntityAvatar target) {
            var wanted = new HashSet<String>();
            var root = slot.data.modifiers.get(slot.root);
            if (root.onAdded != null)
                for (var action : root.onAdded) {
                    if (action == null || action.type != AbilityModifierAction.Type.ApplyModifier) continue;
                    var child = slot.data.modifiers.get(action.modifierName);
                    if (child == null || !AbilityPropertyModifier.hasProperties(child)) continue;
                    @SuppressWarnings("unchecked")
                    var predicates = (List<Map<String, Object>>) (List<?>) action.predicates;
                    if (PredicateEvaluator.all(predicates, ability, target, target, action))
                        wanted.add(action.modifierName);
                }
            for (var name : new HashSet<>(statChildren))
                if (!wanted.contains(name)) ability.removeModifier(target, name);
            for (var name : wanted)
                if (!statChildren.contains(name))
                    ability.registerModifier(
                            target,
                            new AbilityModifierController(ability, slot.data, slot.data.modifiers.get(name)));
            statChildren.clear();
            statChildren.addAll(wanted);
        }
    }

    public TowerBlessingEffects(Player player) {
        this.player = player;
    }

    public synchronized void activate(Scene target, List<Integer> selected) {
        if (scene != target
                || selected.size() < cards.size()
                || !selected.subList(0, cards.size()).equals(cards)) {
            clear();
            scene = target;
        }
        if (!cards.equals(selected)) {
            var added = new ArrayList<Slot>();
            for (int id : selected.subList(cards.size(), selected.size())) {
                var card = GameData.getTowerBuffDataMap().get(id);
                var buff = card == null ? null : GameData.getBuffDataMap().get(card.getBuffId());
                var data = buff == null ? null : GameData.getAbilityData(buff.getAbilityName());
                if (data == null || !data.modifiers.containsKey(buff.getModifierName())) continue;
                int uid = player.getBuffManager().reserveBuffUid();
                issued.add(uid);
                var slot = new Slot(uid, buff.getId(), buff.getModifierName(), data);
                slots.add(slot);
                added.add(slot);
            }
            for (var entry : layers.entrySet())
                for (var slot : added) entry.getValue().add(new Layer(slot, entry.getKey(), player));
            notify(
                    ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_ADD_SERVER_BUFF,
                    new ArrayList<>(layers.keySet()),
                    added);
            cards = List.copyOf(selected);
        }
        refresh();
    }

    public synchronized void refresh() {
        if (scene == null || player.getScene() != scene) return;
        var active = player.getTeamManager().getActiveTeamSnapshot();
        for (var old : new ArrayList<>(layers.keySet())) if (!active.contains(old)) remove(old);
        for (var target : active) {
            if (target.getScene() != scene) continue;
            var current = layers.get(target);
            if (current == null) {
                current = new ArrayList<>();
                for (var slot : slots) current.add(new Layer(slot, target, player));
                layers.put(target, current);
                notify(ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_ADD_SERVER_BUFF, List.of(target));
            }
            for (var layer : current) layer.refreshConditions(target);
        }
    }

    public synchronized void resend() {
        refresh();
        notify(
                ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_ADD_SERVER_BUFF,
                new ArrayList<>(layers.keySet()));
    }

    private void notify(ServerBuffChangeType change, List<EntityAvatar> targets) {
        notify(change, targets, slots);
    }

    private void notify(
            ServerBuffChangeType change, List<EntityAvatar> targets, List<Slot> selected) {
        if (targets.isEmpty() || selected.isEmpty()) return;
        var proto = ServerBuffChangeNotify.newBuilder().setServerBuffChangeType(change);
        targets.forEach(target -> proto.addAvatarGuidList(target.getAvatar().getGuid()));
        for (var slot : selected)
            proto.addServerBuffList(
                    ServerBuff.newBuilder()
                            .setServerBuffUid(slot.uid)
                            .setServerBuffId(slot.buffId)
                            .setServerBuffType(2));
        var packet = new BasePacket(PacketOpcodes.ServerBuffChangeNotify);
        packet.setData(proto);
        player.sendPacket(packet);
    }

    private void remove(EntityAvatar target) {
        notify(ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF, List.of(target));
        var removed = layers.remove(target);
        if (removed != null) for (var layer : removed) layer.ability.removeInstalledModifiers();
    }

    public synchronized void clear() {
        for (var target : new ArrayList<>(layers.keySet())) remove(target);
        slots.clear();
        cards = List.of();
        scene = null;
        rewardedKills.clear();
    }

    /** Kill procs belong to the actual attacking avatar, including its owned projectiles. */
    public synchronized void onKill(EntityMonster monster, int killerId) {
        if (scene == null || player.getScene() != scene || monster.getScene() != scene) return;
        var caster = AbilityTalentResolver.caster(player, scene.getEntityById(killerId));
        var current = layers.get(caster);
        if (current == null || !rewardedKills.add(monster)) return;
        for (var layer : current) {
            var root = layer.slot.data.modifiers.get(layer.slot.root);
            if (root.modifierMixins == null) continue;
            for (var mixin : root.modifierMixins)
                if (mixin != null && mixin.type == AbilityMixinData.Type.DoActionByKillingMixin)
                    eventActions(layer, caster, monster, mixin.onKill, 0);
        }
    }

    public synchronized void onBurst(EntityAvatar caster) {
        var current = layers.get(caster);
        if (scene == null || player.getScene() != scene || current == null) return;
        long now = System.nanoTime();
        for (var layer : current) {
            if (layer.lastBurst != Long.MIN_VALUE && now - layer.lastBurst < 200_000_000L) continue;
            var root = layer.slot.data.modifiers.get(layer.slot.root);
            if (root.modifierMixins == null) continue;
            for (var mixin : root.modifierMixins)
                if (mixin != null && mixin.type == AbilityMixinData.Type.OnAvatarUseSkillMixin) {
                    layer.lastBurst = now;
                    eventActions(layer, caster, null, mixin.onTriggerUltimateSkill, 0);
                }
        }
    }

    private void eventActions(
            Layer layer,
            EntityAvatar caster,
            EntityMonster killed,
            AbilityModifierAction[] actions,
            int depth) {
        if (actions == null || depth > 32) return;
        var ability = layer.ability;
        var properties =
                emu.grasscutter.game.ability.actions.AbilityActionHandler.propertiesFor(ability, caster);
        for (var action : actions) {
            if (action == null || action.type == null) continue;
            switch (action.type) {
                case Predicated -> {
                    var target = "Other".equals(action.target) ? killed : caster;
                    boolean accepted =
                            target != null
                                    && PredicateEvaluator.all(
                                            action.targetPredicates, ability, caster, target, action);
                    eventActions(
                            layer,
                            caster,
                            killed,
                            accepted ? action.successActions : action.failActions,
                            depth + 1);
                }
                case Randomed -> eventActions(
                        layer,
                        caster,
                        killed,
                        java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                                        < action.chance.get(properties, 0f)
                                ? action.successActions
                                : action.failActions,
                        depth + 1);
                case ApplyModifier, AttachModifier -> {
                    var modifier = layer.slot.data.modifiers.get(action.modifierName);
                    if (modifier == null) continue;
                    long now = System.nanoTime();
                    if (layer.procEnds.getOrDefault(action.modifierName, Long.MIN_VALUE) > now) continue;
                    if ("Unique".equals(modifier.stacking) && modifier.duration != null) {
                        float duration = modifier.duration.get(properties, 0f);
                        if (Float.isFinite(duration) && duration > 0)
                            layer.procEnds.put(action.modifierName, now + (long) (duration * 1_000_000_000d));
                    }
                    eventActions(layer, caster, killed, modifier.onAdded, depth + 1);
                }
                case ModifyAvatarSkillCD -> {
                    var depot = caster.getAvatar().getSkillDepot();
                    if (depot == null || action.skillSlot == null) continue;
                    for (int slot : action.skillSlot) {
                        int id =
                                slot == 3
                                        ? depot.getEnergySkill()
                                        : slot > 0 && slot <= depot.getSkills().size()
                                                ? depot.getSkills().get(slot - 1)
                                                : 0;
                        float ratio =
                                action.legacyCdRatio == null ? 0f : action.legacyCdRatio.get(properties, 0f);
                        player
                                .getAbilityManager()
                                .getSkillCooldown()
                                .modifyRemaining(caster.getAvatar(), id, 0, ratio);
                    }
                    player.getAbilityManager().getSkillCooldown().synchronize();
                }
                case HealHP -> player
                        .getAbilityManager()
                        .executeActionNow(ability, action, ByteString.EMPTY, caster);
                default -> {}
            }
        }
    }

    public synchronized boolean handles(AbilityInvokeEntry invoke) {
        return invoke.getHead().getFPDAONBEBHKValue() == 1
                && issued.contains(invoke.getHead().getServerBuffUid());
    }

    /** Server-buff UIDs use a separate namespace: never insert their IDs in normal modifiers. */
    public synchronized boolean accept(AbilityInvokeEntry invoke) throws Exception {
        if (scene == null || player.getScene() != scene) return false;
        var target =
                player.getTeamManager().getActiveTeamSnapshot().stream()
                        .filter(entity -> entity.getId() == invoke.getEntityId() && entity.getScene() == scene)
                        .findFirst()
                        .orElse(null);
        var current = layers.get(target);
        if (current == null) return false;
        var layer =
                current.stream()
                        .filter(candidate -> candidate.slot.uid == invoke.getHead().getServerBuffUid())
                        .findFirst()
                        .orElse(null);
        if (layer == null) return false;
        var data = layer.slot.data;
        if (invoke.getArgumentType()
                == emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                        .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE) {
            var change = AbilityMetaModifierChange.parseFrom(invoke.getAbilityData());
            int index = change.getModifierLocalId();
            if (index < 0 || index >= data.modifiers.size()) return false;
            var name = new ArrayList<>(data.modifiers.keySet()).get(index);
            if (!layer.allowed.contains(name)) return false;
            int instance = invoke.getHead().getInstancedModifierId();
            if (change.getAction() == ModifierAction.MODIFIER_ACTION_ADDED) {
                layer.nativeInstances.put(instance, name);
                if (!layer.ability.getModifiers(target).containsKey(name))
                    layer.ability.registerModifier(
                            target, new AbilityModifierController(layer.ability, data, data.modifiers.get(name)));
            } else if (change.getAction() == ModifierAction.MODIFIER_ACTION_REMOVED) {
                if (!name.equals(layer.nativeInstances.remove(instance))) return false;
                if (!name.equals(layer.slot.root) && !layer.statChildren.contains(name))
                    layer.ability.removeModifier(target, name);
            }
            return true;
        }
        int local = invoke.getHead().getLocalId();
        int container = local & 7, modifier = (local >>> 3) & 63;
        if ((container != 3 && container != 4)
                || modifier >= data.modifiers.size()
                || !layer.allowed.contains(new ArrayList<>(data.modifiers.keySet()).get(modifier)))
            return false;
        var action = data.localIdToAction.get(local);
        if (action == null) return true; // Damage/movement mixins execute natively.
        var root = data.modifiers.get(layer.slot.root);
        if (root.modifierMixins != null
                && Arrays.stream(root.modifierMixins)
                        .anyMatch(
                                mixin ->
                                        mixin != null
                                                && (mixin.type == AbilityMixinData.Type.DoActionByKillingMixin
                                                        || mixin.type == AbilityMixinData.Type.OnAvatarUseSkillMixin)))
            return true; // These server-owned events must not be paid again by a native callback.
        // Native modifier metadata already confirms attachment changes. Processing the same
        // ApplyModifier action would add a second server stack.
        if (action.type == AbilityModifierAction.Type.ApplyModifier
                || action.type == AbilityModifierAction.Type.AttachModifier
                || action.type == AbilityModifierAction.Type.RemoveModifier) return true;
        var controller =
                layer
                        .ability
                        .getModifiers(target)
                        .get(new ArrayList<>(data.modifiers.keySet()).get(modifier));
        if (controller == null) return false;
        AbilityCooldownInvocation.run(
                controller,
                () ->
                        player
                                .getAbilityManager()
                                .executeActionNow(layer.ability, action, invoke.getAbilityData(), target));
        return true;
    }
}
