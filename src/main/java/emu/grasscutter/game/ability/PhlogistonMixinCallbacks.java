package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Phlogiston;

import java.util.Arrays;

/** One client timer notification (135) applies one configured fuel interval. */
public final class PhlogistonMixinCallbacks {
    // Verified client constructor's permutation input is float bits 0x3e99999a.
    public static final float DEFAULT_INTERVAL = 0.3f;

    public boolean apply(
            Ability ability,
            AbilityMixinData mixin,
            ByteString payload,
            GameEntity bearer,
            AbilityModifierController parent) {
        if (ability == null
                || mixin == null
                || !mixin.enable
                || mixin.type != AbilityMixinData.Type.ChangePhlogistonMixin
                || payload == null
                || !payload.isEmpty()
                || bearer == null
                || ability.getPlayerOwner() == null
                || bearer instanceof emu.grasscutter.game.entity.EntityAvatar avatar
                        && avatar.getPlayer() != ability.getPlayerOwner()
                || !AbilityGlobalValues.isCurrent(ability, bearer)) return false;
        var scene = ability.getPlayerOwner().getScene();
        if (scene == null) return false;
        synchronized (scene) {
            if (!scope(ability, mixin, bearer, parent)
                    || !PredicateEvaluator.all(
                            mixin.predicates, ability, ability.getOwner(), bearer, null))
                return false;
            var speed =
                    mixin.speed == DynamicFloat.ZERO && mixin.rawPhlogistonSpeed != null
                            ? mixin.rawPhlogistonSpeed
                            : mixin.speed;
            if (speed == null) return false;
            var properties = AbilityActionHandler.propertiesFor(ability, bearer);
            var rate = speed.tryGet(properties);
            var configured =
                    mixin.interval != null
                            ? mixin.interval
                            : mixin.currentPhlogistonInterval != null
                                    ? mixin.currentPhlogistonInterval
                                    : mixin.rawPhlogistonInterval != null
                                            ? mixin.rawPhlogistonInterval
                                            : mixin.extraPhlogistonInterval;
            var step =
                    configured == null
                            ? java.util.OptionalDouble.of(DEFAULT_INTERVAL)
                            : configured.tryGet(properties);
            if (rate.isEmpty()
                    || step.isEmpty()
                    || rate.getAsDouble() < 0
                    || step.getAsDouble() <= 0) return false;
            var mode =
                    Phlogiston.ChangeMode.fromConfig(
                            mixin.costType != null
                                    ? mixin.costType
                                    : mixin.rawPhlogistonCostType != null
                                            ? mixin.rawPhlogistonCostType
                                            : "Cost");
            // Current resources contain Cost and Add. Set's timer semantics need a client pairing.
            if (mode != Phlogiston.ChangeMode.COST && mode != Phlogiston.ChangeMode.ADD)
                return false;
            double amount = rate.getAsDouble() * step.getAsDouble();
            if (!Double.isFinite(amount) || amount > Float.MAX_VALUE) return false;
            return Phlogiston.change(
                    ability,
                    bearer,
                    (float) (mode == Phlogiston.ChangeMode.COST ? -amount : amount),
                    mixin.targetType != null
                            ? mixin.targetType
                            : mixin.rawPhlogistonTargetType != null
                                    ? mixin.rawPhlogistonTargetType
                                    : "Auto",
                    mixin.phlogistonUsesRatio()
                            ? mode == Phlogiston.ChangeMode.COST
                                    ? Phlogiston.RatioBasis.CURRENT
                                    : Phlogiston.RatioBasis.CAPACITY
                            : Phlogiston.RatioBasis.ABSOLUTE);
        }
    }

    private static boolean contains(AbilityMixinData[] values, AbilityMixinData mixin) {
        return values != null && Arrays.stream(values).anyMatch(value -> value == mixin);
    }

    static boolean scope(
            Ability ability,
            AbilityMixinData mixin,
            GameEntity bearer,
            AbilityModifierController parent) {
        if (parent != null) {
            synchronized (bearer.getInstancedModifiers()) {
                return parent.getAbility() == ability
                        && bearer.getAbilityHpModifier().lifecycleControllers().contains(parent)
                        && contains(parent.getModifierData().modifierMixins, mixin);
            }
        }
        var defaults = ability.getData().modifiers.get("Default");
        return contains(ability.getData().abilityMixins, mixin)
                || defaults != null && contains(defaults.modifierMixins, mixin);
    }
}
