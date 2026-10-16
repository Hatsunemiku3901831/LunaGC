package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A resource-defined unique effect that removes itself at its interval cannot restart early. */
public final class AbilityModifierInterval {
    private AbilityModifierInterval() {}

    public static boolean supported(
            Ability ability, AbilityModifier data, String name, GameEntity target) {
        if (ability == null
                || data == null
                || target == null
                || !"Unique".equals(data.stacking)
                || !data.isUnique
                || data.duration == null
                || data.thinkInterval == null
                || data.onThinkInterval == null
                || data.onThinkInterval.length != 1) return false;
        var end = data.onThinkInterval[0];
        if (end == null
                || end.type != AbilityModifier.AbilityModifierAction.Type.RemoveUniqueModifier
                || !Objects.equals(name, end.modifierName)
                || !"Self".equals(end.target)
                || end.otherTargets != null
                || end.predicates != null && !end.predicates.isEmpty()) return false;
        var properties = AbilityActionHandler.propertiesFor(ability, target);
        float duration = data.duration.get(properties, 0),
                interval = data.thinkInterval.get(properties, 0);
        return Float.isFinite(duration)
                && duration > 0
                && duration == interval
                && AbilityActionSequence.callbacks(ability.getData(), data.onAdded) != null
                && AbilityActionSequence.callbacks(ability.getData(), data.onRemoved) != null;
    }

    public static boolean apply(
            Ability ability,
            AbilityModifier data,
            String name,
            GameEntity target,
            BooleanSupplier current) {
        if (!supported(ability, data, name, target) || !current.getAsBoolean()) return false;
        AbilityModifierController created;
        var retired = new ArrayList<AbilityModifierController>();
        synchronized (target.getInstancedModifiers()) {
            for (var existing : target.getAbilityHpModifier().activeControllers()) {
                var source = existing.getAbility();
                if (name.equals(existing.getName())
                        && source.getOwner() == ability.getOwner()
                        && Objects.equals(
                                source.getData().abilityName, ability.getData().abilityName)) {
                    if (!existing.ownsIntervalDuration() || existing.intervalCurrent(target))
                        return true;
                    retired.add(existing);
                }
            }
            for (var previous : retired)
                previous.getAbility().retireModifierLocked(target, previous);
            created = new AbilityModifierController(ability, ability.getData(), data);
            if (!created.prepareServerCallbacks(target)) return false;
            ability.registerModifier(target, created);
        }
        for (var previous : retired) previous.runServerOnRemoved();
        created.runServerOnAdded(current);
        return true;
    }
}
