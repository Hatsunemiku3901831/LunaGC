package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.ActorProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.*;

/** Shared removable property layers; the HP entry point is retained for existing callers. */
public final class AbilityHpModifier {
    private final GameEntity entity;
    private float appliedRatio;
    private float appliedMaxHp;
    private boolean hasHpBaseline;
    private float baselineMaxHp, baselinePercent, lastMaxHp, lastPercent;
    private final AbilityPropertyModifier otherProperties;
    private final Map<Ability, Map<String, List<AbilityModifierController>>> serverStacks =
            new IdentityHashMap<>();

    public AbilityHpModifier(GameEntity entity) {
        this.entity = entity;
        this.otherProperties = new AbilityPropertyModifier(entity);
    }

    public static boolean hasRatio(AbilityModifier data) {
        var value = data != null && data.properties != null ? data.properties.Actor_MaxHPRatio : null;
        return value != null && (value.isDynamic() || value.getConstant() != 0f);
    }

    // The registry lock is also the stat recalculation lock for an attached avatar or monster.
    public void registered(AbilityModifierController controller) {
        entity.getAbilityStaminaCost().registered(controller);
        var ability = controller.getAbility();
        String name = controller.getName();
        if (entity.getInstancedModifiers().containsValue(controller)) {
            // The client's IDs now describe the real instances, including a server-requested buff.
            var pending=serverStacks.get(ability);
            var stack=pending==null?null:pending.get(name);
            if(stack!=null && stack.contains(controller)) unregisterServerStack(controller);
            else removeServerStacks(ability, name);
        } else if (isMultiple(controller.getModifierData())) {
            var stack =
                    serverStacks
                            .computeIfAbsent(ability, key -> new LinkedHashMap<>())
                            .computeIfAbsent(name, key -> new ArrayList<>());
            if (!stack.contains(controller)) stack.add(controller);
        }
    }

    public boolean removeServerStacks(Ability ability, String name) {
        var named = serverStacks.get(ability);
        if (named == null || named.remove(name) == null) return false;
        if (named.isEmpty()) serverStacks.remove(ability);
        return true;
    }

    public AbilityModifierController unregisterServerStack(AbilityModifierController controller) {
        var named = serverStacks.get(controller.getAbility());
        if (named == null) return null;
        var stack = named.get(controller.getName());
        if (stack == null) return null;
        stack.remove(controller);
        if (!stack.isEmpty()) return stack.get(stack.size() - 1);
        named.remove(controller.getName());
        if (named.isEmpty()) serverStacks.remove(controller.getAbility());
        return null;
    }

    private static boolean isMultiple(AbilityModifier data) {
        return data.stacking != null && data.stacking.startsWith("Multiple");
    }

    private List<AbilityModifierController> controllers() {
        var controllers = new ArrayList<AbilityModifierController>();
        controllers.addAll(entity.getInstancedModifiers().values());
        entity
                .getAppliedAbilityModifiers()
                .values()
                .forEach(named -> controllers.addAll(named.values()));
        serverStacks.values().forEach(named -> named.values().forEach(controllers::addAll));
        return controllers;
    }

    List<AbilityModifierController> activeControllers() {
        var seen = Collections.newSetFromMap(new IdentityHashMap<AbilityModifierController, Boolean>());
        var unique = new IdentityHashMap<Ability, Set<String>>();
        var result = new ArrayList<AbilityModifierController>();
        for (var controller : controllers()) {
            if (controller == null || !seen.add(controller)) continue;
            if (!isMultiple(controller.getModifierData()) && !unique.computeIfAbsent(controller.getAbility(),
                    key -> new HashSet<>()).add(controller.getName())) continue;
            result.add(controller);
        }
        return result;
    }
    Collection<AbilityModifierController> lifecycleControllers() { return controllers(); }

    AbilityModifierController pendingServerController(Ability ability,String name) {
        var named=serverStacks.get(ability);
        var stack=named==null?null:named.get(name);
        var candidates=stack==null?activeControllers():stack;
        for(var controller:candidates) {
            if(controller.getAbility()==ability && name.equals(controller.getName())
                    && !entity.getInstancedModifiers().containsValue(controller) && controller.hasServerCallbacks())
                return controller;
        }
        return null;
    }

    /** A locally applied callback may precede its client instance assignment. */
    AbilityModifierController pendingCooldownController(Ability ability, String name) {
        var named = serverStacks.get(ability);
        var stack = named == null ? null : named.get(name);
        var candidates = stack == null ? activeControllers() : stack;
        return candidates.stream()
                .filter(c -> c.getAbility() == ability && name.equals(c.getName())
                        && c.hasCooldownCallbacks()
                        && !entity.getInstancedModifiers().containsValue(c))
                .findFirst().orElse(null);
    }
    AbilityModifierController pendingAttachmentController(Ability ability,String name) {
        return controllers().stream().filter(c -> c.getAbility()==ability && name.equals(c.getName())
                && c.hasAttachmentCallbacks() && !entity.getInstancedModifiers().containsValue(c)).findFirst().orElse(null);
    }

    /** Known expression keys on this bearer can identify a first, hashed global-value update. */
    public Set<String> getReferencedKeys() {
        synchronized (entity.getInstancedModifiers()) {
            var keys = new LinkedHashSet<String>();
            for (var controller : controllers()) {
                if (controller == null || controller.getModifierData().properties == null) continue;
                for (var entry : controller.getModifierData().properties.getValues().entrySet()) {
                    var value = entry.getValue();
                    if ((ActorProperty.getFightProperty(entry.getKey()) == null
                            && !AbilityStaminaCost.PROPERTY.equals(entry.getKey())) || value == null
                            || !value.isDynamic() || value.getOps() == null) continue;
                    for (var token : value.getOps()) {
                        if (token != null
                                && token.op == DynamicFloat.StackOp.Op.KEY
                                && token.sValue != null
                                && !token.sValue.isEmpty()) {
                            keys.add(token.sValue);
                        }
                    }
                }
            }
            return keys;
        }
    }

    private float ratio() {
        double sum = 0;
        for (var controller : activeControllers()) {
            if (!hasRatio(controller.getModifierData())) continue;
            var data = controller.getModifierData();
            sum +=
                    data.properties.Actor_MaxHPRatio.get(
                            AbilityActionHandler.propertiesFor(controller.getAbility(), entity), 0f);
        }
        return Double.isFinite(sum) && Math.abs(sum) <= Float.MAX_VALUE ? (float) sum : 0f;
    }

    /** Call only after the raw stat pass has replaced this layer's previous values. */
    public void afterStatRecalc() {
        synchronized (entity.getInstancedModifiers()) {
            appliedRatio = 0f;
            appliedMaxHp = 0f;
            hasHpBaseline = false;
            otherProperties.reset();
            refresh(false);
        }
    }

    public void refresh(boolean notify) {
        synchronized (entity.getInstancedModifiers()) {
            var properties = entity.getFightProperties();
            if (properties == null) return;
            synchronized (properties) {
                float hp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float percent = entity.getFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT);
                refreshHp();
                var changed = otherProperties.refresh();
                if (Float.compare(hp, entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)) != 0
                        || Float.compare(percent, entity.getFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT)) != 0) {
                    changed.addAll(List.of(FightProperty.FIGHT_PROP_HP_PERCENT,
                            FightProperty.FIGHT_PROP_MAX_HP, FightProperty.FIGHT_PROP_CUR_HP));
                }
                if (notify && !changed.isEmpty()) publish(changed);
            }
        }
    }

    private void refreshHp() {
        synchronized (entity.getInstancedModifiers()) {
            var properties = entity.getFightProperties();
            if (properties == null) return;
            synchronized (properties) {
                float oldMax = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float current = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                float baseHp = entity.getFightProperty(FightProperty.FIGHT_PROP_BASE_HP);
                float oldPercent = entity.getFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT);
                if (!Float.isFinite(oldMax)
                        || oldMax <= 0f
                        || !Float.isFinite(current)
                        || !Float.isFinite(baseHp)
                        || baseHp <= 0f
                        || !Float.isFinite(oldPercent)) return;
                float baseMax = hasHpBaseline && Float.compare(oldMax, lastMaxHp) == 0
                        ? baselineMaxHp : oldMax - appliedMaxHp;
                float basePercent = hasHpBaseline && Float.compare(oldPercent, lastPercent) == 0
                        ? baselinePercent : oldPercent - appliedRatio;
                float nextRatio = ratio();
                if (nextRatio == 0f && appliedRatio == 0f && appliedMaxHp == 0f) return;
                double scale = 1;
                if (entity instanceof EntityMonster) {
                    double compound =
                            entity.getFightProperty(FightProperty.FIGHT_PROP_HP)
                                    + (double) baseHp * (1 + basePercent);
                    if (compound > 0 && Double.isFinite(compound)) scale = baseMax / compound;
                }
                // Use the local compound-stat model: an HP% term adds base HP, not final HP.
                double delta = (double) baseHp * nextRatio * scale;
                double nextMax = baseMax + delta;
                double nextPercent = (double) basePercent + nextRatio;
                if (!Double.isFinite(nextMax)
                        || nextMax <= 0
                        || nextMax > Float.MAX_VALUE
                        || !Double.isFinite(nextPercent)
                        || Math.abs(nextPercent) > Float.MAX_VALUE) {
                    nextRatio = 0f;
                    delta = 0;
                    nextMax = baseMax;
                    nextPercent = basePercent;
                }
                if (nextMax <= 0 || !Double.isFinite(nextMax)) return;
                float max = (float) nextMax;
                float hp = (float) (max * Math.max(0, Math.min(1, (double) current / oldMax)));
                boolean changed =
                        Float.compare(oldMax, max) != 0 || Float.compare(oldPercent, (float) nextPercent) != 0;
                if (!changed) return;
                entity.setFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT, (float) nextPercent);
                entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, max);
                entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
                appliedRatio = nextRatio;
                appliedMaxHp = (float) delta;
                hasHpBaseline = nextRatio != 0f || delta != 0;
                baselineMaxHp = baseMax;
                baselinePercent = basePercent;
                lastMaxHp = max;
                lastPercent = (float) nextPercent;
            }
        }
    }

    public void publish() {
        var properties = otherProperties.contributedProperties();
        properties.addAll(List.of(FightProperty.FIGHT_PROP_HP_PERCENT, FightProperty.FIGHT_PROP_MAX_HP, FightProperty.FIGHT_PROP_CUR_HP));
        publish(properties);
    }

    private void publish(Set<FightProperty> properties) {
        publishEntity(properties);
        if (entity instanceof EntityAvatar avatar
                && avatar.getPlayer() != null
                && avatar.getPlayer().hasSentLoginPackets()
                && avatar.getPlayer().getTeamManager().getActiveTeam().contains(avatar)) {
            avatar.getPlayer().sendPacket(new PacketAvatarFightPropNotify(avatar.getAvatar()));
        }
    }

    public void publishEntityHp() {
        var properties = otherProperties.contributedProperties();
        properties.addAll(List.of(FightProperty.FIGHT_PROP_HP_PERCENT, FightProperty.FIGHT_PROP_MAX_HP, FightProperty.FIGHT_PROP_CUR_HP));
        publishEntity(properties);
    }

    private void publishEntity(Set<FightProperty> properties) {
        if (entity.getScene() != null && entity.getScene().getEntityById(entity.getId()) == entity) {
            entity
                    .getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(
                                    entity,
                                    new ArrayList<>(properties)));
        }
    }

    public boolean hasAppliedRatio() {
        synchronized (entity.getInstancedModifiers()) {
            return appliedRatio != 0f || otherProperties.hasContribution();
        }
    }
}
