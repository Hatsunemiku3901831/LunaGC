package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;

import java.util.*;

/** Validate an entire food recipe before claiming any healing or creating any child modifier. */
final class FoodBuffGraph {
    private static final Set<AbilityModifierAction.Type> LEAVES =
            Set.of(
                    AbilityModifierAction.Type.HealHP,
                            AbilityModifierAction.Type.SetOverrideMapValue,
                    AbilityModifierAction.Type.Randomed, AbilityModifierAction.Type.Predicated,
                    AbilityModifierAction.Type.RandomAction, AbilityModifierAction.Type.Repeated);
    private final AbilityData data;
    private final Set<AbilityModifier> path = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AbilityModifier, Integer> validated = new IdentityHashMap<>();
    private final Set<AbilityModifierAction> claims =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private int steps;

    private FoodBuffGraph(AbilityData data) {
        this.data = data;
    }

    static FoodBuffGraph prepare(AbilityData data, AbilityModifier root) {
        if (data == null
                || data.modifiers == null
                || root == null
                || !FoodBuffHealing.hasHealing(data, root)) return null;
        var graph = new FoodBuffGraph(data);
        return graph.modifier(root, false, 0) ? graph : null;
    }

    Set<AbilityModifierAction> claims() {
        return Collections.unmodifiableSet(claims);
    }

    private static boolean present(Object[] values) {
        return values != null && values.length > 0;
    }

    private static boolean fixedTime(DynamicFloat value) {
        return value == null
                || !value.isDynamic()
                        && Float.isFinite(value.getConstant())
                        && value.getConstant() >= 0;
    }

    private boolean modifier(AbilityModifier modifier, boolean visualOnly, int depth) {
        if (modifier == null || depth > 32 || ++steps > 512 || !path.add(modifier)) return false;
        int mode = visualOnly ? 2 : 1;
        try {
            if ((validated.getOrDefault(modifier, 0) & mode) != 0) return true;
            if (!AbilityModifierStacking.supported(modifier)
                    || !fixedTime(modifier.duration)
                    || !fixedTime(modifier.thinkInterval)
                    || modifier.properties != null
                    || present(modifier.onBeingHit)
                    || present(modifier.onAttackLanded)
                    || present(modifier.onHittingOther)
                    || present(modifier.onKill)
                    || present(modifier.onCrash)
                    || present(modifier.onAvatarIn)
                    || present(modifier.onAvatarOut)
                    || present(modifier.onReconnect)
                    || present(modifier.onChangeAuthority)
                    || present(modifier.onVehicleIn)
                    || present(modifier.onVehicleOut)
                    || present(modifier.onZoneEnter)
                    || present(modifier.onZoneExit)
                    || present(modifier.onHeal)
                    || present(modifier.onBeingHealed)) return false;
            if (visualOnly && FoodBuffHealing.hasHealing(data, modifier)) return false;
            if (!actions(modifier.onAdded, visualOnly, depth)
                    || !actions(modifier.onThinkInterval, visualOnly, depth)
                    || !actions(modifier.onRemoved, visualOnly, depth)) return false;
            if (modifier.modifierMixins != null) {
                if (modifier.modifierMixins.length > 256) return false;
                for (var mixin : modifier.modifierMixins) {
                    // Animator state is client-owned. Its referenced branch must be purely visual.
                    if (mixin == null
                            || mixin.type != AbilityMixinData.Type.AttachToStateIDMixin
                            || mixin.modifierName == null
                            || mixin.modifierName.isJsonNull()
                            || mixin.stateIDs == null
                            || mixin.stateIDs.isEmpty()
                            || mixin.stateIDs.stream().anyMatch(x -> x == null || x.isBlank())
                            || mixin.target != null
                                    && !Set.of("Self", "Target").contains(mixin.target))
                        return false;
                    if (mixin.modifierName.isJsonArray()) {
                        if (mixin.modifierName.getAsJsonArray().size() > 256) return false;
                        for (var name : mixin.modifierName.getAsJsonArray())
                            if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString())
                                return false;
                    } else if (!mixin.modifierName.isJsonPrimitive()
                            || !mixin.modifierName.getAsJsonPrimitive().isString()) return false;
                    for (var name : mixin.getModifierNames())
                        if (!modifier(data.modifiers.get(name), true, depth + 1)) return false;
                }
            }
            validated.merge(modifier, visualOnly ? 3 : 1, (a, b) -> a | b);
            return true;
        } finally {
            path.remove(modifier);
        }
    }

    private boolean actions(AbilityModifierAction[] actions, boolean visualOnly, int depth) {
        if (depth > 32 || ++steps > 512) return false;
        var callbacks = AbilityActionSequence.callbacks(data, actions);
        if (callbacks == null) return false;
        for (var action : callbacks) {
            if (++steps > 512
                    || action.otherTargets != null
                    || !action.hasSupportedOffStageConfig()) return false;
            boolean visual = AbilityActionSequence.visual(action.type);
            boolean modifierAction = AbilityActionSequence.isModifierAction(action.type);
            if (!visual && !modifierAction && (visualOnly || !LEAVES.contains(action.type)))
                return false;
            AbilityModifier child = modifierAction ? data.modifiers.get(action.modifierName) : null;
            if (modifierAction && child == null) return false;
            boolean childHeals = child != null && FoodBuffHealing.hasHealing(data, child);
            boolean currentAvatar = "CurLocalAvatar".equals(action.target);
            if (currentAvatar
                    ? !visual && (!modifierAction || childHeals)
                    : action.target != null && !Set.of("Self", "Target").contains(action.target))
                return false;
            if (modifierAction) {
                if (action.type == AbilityModifierAction.Type.RemoveModifier) {
                    if (!actions(child.onRemoved, visualOnly, depth + 1)) return false;
                } else if (!modifier(child, visualOnly || currentAvatar, depth + 1)) return false;
            }
            // Let native state/visual callbacks operate; own every route that could repeat a heal.
            if (!visualOnly && !visual && (!modifierAction || childHeals)) claims.add(action);
        }
        return true;
    }

    static boolean instantHealing(AbilityData data, AbilityModifier modifier) {
        return FoodBuffHealing.hasHealing(data, modifier)
                && fixedTime(modifier.duration)
                && (modifier.duration == null || modifier.duration.getConstant() == 0)
                && !present(modifier.onThinkInterval)
                && !present(modifier.onRemoved)
                && !present(modifier.modifierMixins)
                && modifier.properties == null;
    }
}
