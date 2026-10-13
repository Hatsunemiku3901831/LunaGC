package emu.grasscutter.game.ability;

import com.google.gson.JsonElement;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;

/** Resource state masks: any configured state matches, then reject inverts that result. */
final class AbilityStateBinding {
    private AbilityStateBinding() {}

    static String desired(Ability ability, GameEntity bearer, AbilityMixinData mixin) {
        if (mixin.elementTypeModifier != null
                || mixin.doAfterDie != null
                || mixin.isUnique != null
                || mixin.NHPDOIELPCE != null
                || !AbilityActionSequence.supportedPredicates(mixin.predicates, 0)) return null;
        var states = states(mixin.abilityStates);
        var reject = flag(mixin.reject);
        var name = mixin.modifierName;
        if (states == null
                || reject == null
                || name == null
                || !name.isJsonPrimitive()
                || !name.getAsJsonPrimitive().isString()
                || name.getAsString().isEmpty()) return null;
        var child = ability.getData().modifiers.get(name.getAsString());
        if (child == null
                || !AbilityModifierStacking.supported(child)
                || AbilityActionSequence.callbacks(ability.getData(), child.onAdded) == null
                || AbilityActionSequence.callbacks(ability.getData(), child.onRemoved) == null)
            return null;
        boolean matched;
        synchronized (bearer.getInstancedModifiers()) {
            matched =
                    bearer.getAbilityHpModifier().lifecycleControllers().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(
                                    controller ->
                                            states.contains(controller.getModifierData().state));
        }
        return matched != reject
                        && PredicateEvaluator.all(
                                mixin.predicates, ability, ability.getOwner(), bearer, null)
                ? name.getAsString()
                : null;
    }

    private static Boolean flag(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return false;
        return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()
                ? raw.getAsBoolean()
                : null;
    }

    private static Set<AbilityModifier.State> states(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return Set.of();
        if (!raw.isJsonArray()) return null;
        var result = EnumSet.noneOf(AbilityModifier.State.class);
        for (var value : raw.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            if ("None".equals(value.getAsString())) continue; // The native zero mask never matches.
            try {
                result.add(AbilityModifier.State.valueOf(value.getAsString()));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
        return result;
    }
}
