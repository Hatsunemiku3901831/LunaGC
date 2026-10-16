package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.ServerBuffType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

import java.util.*;

/** The normal quartz pickup and resource ice that explicitly breaks on one BloodSmash hit. */
public final class ScarletQuartz {
    private static final String HAS = "HAS_DULINS_BLOOD_BUFF";
    private static final String MARK = "HAS_BLOOD_BUFF_MARK_NORMAL";
    private static final String USED = "DULINS_BLOOD_BUFF_USED";
    private final Player player;
    private final Set<EntityAvatar> recipients = Collections.newSetFromMap(new IdentityHashMap<>());

    public ScarletQuartz(Player player) {
        this.player = player;
    }

    public static boolean supports(BuffData data) {
        return data != null
                && data.getId() == 500101
                && data.getGroupId() == 51
                && data.getServerBuffType() == ServerBuffType.SERVER_BUFF_TEAM
                && "Dulins_Blood_Buff".equals(data.getAbilityName())
                && "SERVER_Dulins_Blood_Smash_Ice_Buff".equals(data.getModifierName());
    }

    /** Called under the owning buff manager's lock, including after a party change. */
    public void refresh() {
        for (var old : new ArrayList<>(recipients)) {
            if (current(old)) continue;
            set(old, HAS, 0);
            set(old, MARK, 0);
            recipients.remove(old);
        }
        for (var member : player.getTeamManager().getActiveTeamSnapshot()) {
            if (!current(member)) continue;
            boolean added = recipients.add(member);
            set(member, HAS, 1);
            set(member, MARK, 1);
            if (added) set(member, USED, 0);
        }
    }

    public void removed(boolean consumed) {
        var members = Collections.newSetFromMap(new IdentityHashMap<EntityAvatar, Boolean>());
        members.addAll(recipients);
        members.addAll(player.getTeamManager().getActiveTeamSnapshot());
        for (var member : members) {
            if (member == null || member.getPlayer() != player) continue;
            set(member, HAS, 0);
            set(member, MARK, 0);
            set(member, USED, consumed ? 1 : 0);
        }
        recipients.clear();
    }

    private boolean current(EntityAvatar member) {
        var scene = player.getScene();
        if (member == null
                || scene == null
                || member.getPlayer() != player
                || member.getScene() != scene
                || player.getWorld() != scene.getWorld()
                || !member.isInSceneParty()) return false;
        var registered = scene.getEntities().get(member.getId());
        return registered == null || registered == member;
    }

    private void set(EntityAvatar member, String key, float value) {
        var previous = member.getGlobalAbilityValues().put(key, value);
        if (previous != null && Float.compare(previous, value) == 0 || !current(member)) return;
        member.onAbilityValueUpdate();
        member.getScene()
                .broadcastPacket(new PacketServerGlobalValueChangeNotify(member, key, value));
    }

    public static boolean trySmash(
            Scene scene, GameEntity attacker, GameEntity target, float damage) {
        if (!(target instanceof EntityGadget ice)
                || !Float.isFinite(damage)
                || damage < 0
                || !singleSmashIce(ice)) return false;
        var owner =
                attacker instanceof EntityAvatar avatar
                        ? avatar
                        : attacker instanceof EntityClientGadget gadget
                                ? gadget.getCombatOwner()
                                : null;
        if (owner == null) return false;
        var player = owner.getPlayer();
        if (player == null) return false;
        synchronized (scene) {
            if (!ice.isAlive()
                    || scene.getEntities().get(ice.getId()) != ice
                    || attacker.getScene() != scene
                    || scene.getEntityById(attacker.getId()) != attacker
                    || !attacker.isAlive()
                    || owner.getScene() != scene
                    || !owner.isAlive()
                    || !owner.isInSceneParty()
                    || player.getScene() != scene
                    || player.getWorld() != scene.getWorld()
                    || attacker instanceof EntityClientGadget gadget
                            && gadget.getCombatOwner() != owner
                    || !player.getBuffManager().consumeScarletQuartz()) return false;
            scene.killEntity(ice, attacker.getId());
            return true;
        }
    }

    private static boolean singleSmashIce(EntityGadget ice) {
        var config = ice.getConfigGadget();
        if (config == null || config.getAbilities() == null) return false;
        for (var ability : ice.getInstancedAbilities()) {
            var data = ability.getData();
            if (ability.getOwner() != ice
                    || data == null
                    || data.onAdded == null
                    || data.modifiers == null
                    || GameData.getAbilityData(data.abilityName) != data
                    || config.getAbilities().stream()
                            .noneMatch(
                                    a -> a != null && data.abilityName.equals(a.getAbilityName())))
                continue;
            for (var start : data.onAdded) {
                if (start == null
                        || start.type != AbilityModifierAction.Type.ApplyModifier
                        || !self(start)
                        || !empty(start.predicates)
                        || !empty(start.targetPredicates)) continue;
                var modifier = data.modifiers.get(start.modifierName);
                if (modifier != null
                        && modifier.elementType == ElementType.Ice
                        && modifier.onBeingHit != null
                        && Arrays.stream(modifier.onBeingHit).anyMatch(ScarletQuartz::smashAction))
                    return true;
            }
        }
        return false;
    }

    private static boolean self(AbilityModifierAction action) {
        return (action.target == null || "Self".equals(action.target))
                && action.otherTargets == null;
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean bloodPredicate(List<?> values) {
        if (values == null || values.size() != 1 || !(values.get(0) instanceof Map<?, ?> predicate))
            return false;
        return predicate.keySet().equals(Set.of("$type", "attackTags", "target"))
                && "CCHPEOBKOBO".equals(predicate.get("$type"))
                && "Target".equals(predicate.get("target"))
                && List.of("BloodSmash").equals(predicate.get("attackTags"));
    }

    private static boolean smashAction(AbilityModifierAction action) {
        if (action == null) return false;
        if (action.type == AbilityModifierAction.Type.KillSelf)
            return self(action)
                    && bloodPredicate(action.predicates)
                    && empty(action.targetPredicates);
        if (action.type != AbilityModifierAction.Type.Predicated
                || !empty(action.predicates)
                || !bloodPredicate(action.targetPredicates)
                || action.successActions == null
                || action.successActions.length != 1
                || action.otherTargets != null
                || !"Target".equals(action.target)) return false;
        var leaf = action.successActions[0];
        return leaf != null
                && leaf.type == AbilityModifierAction.Type.KillSelf
                && self(leaf)
                && empty(leaf.predicates)
                && empty(leaf.targetPredicates);
    }
}
