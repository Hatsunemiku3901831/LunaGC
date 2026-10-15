package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;

/** Resource parameter writes belong to a live ability instance, including its dependent modifiers. */
public final class AbilityOverrideMap {
    private AbilityOverrideMap() {}

    public static boolean write(Ability source, AbilityModifierAction action, GameEntity target, float value) {
        if (source == null || action == null || action.overrideMapKey == null || action.overrideMapKey.isBlank()
                || !Float.isFinite(value)
                || !AbilityExecutionContext.canTrigger(source.getOwner(), source.getPlayerOwner())) return false;
        var destination = resolve(source, action.abilityName, target);
        if (destination == null
                || !AbilityExecutionContext.canTrigger(destination.getOwner(), destination.getPlayerOwner())) return false;
        var parameters = destination.getAbilitySpecials();
        synchronized (parameters) {
            destination.markOverrideWritten(action.overrideMapKey);
            if (parameters.containsKey(action.overrideMapKey)
                    && Float.compare(parameters.getFloat(action.overrideMapKey), value) == 0) return true;
            parameters.put(action.overrideMapKey, value);
        }
        // Modifier refresh can execute callbacks; never hold the parameter lock while doing it.
        destination.refreshModifierTargets(true);
        return true;
    }

    private static Ability resolve(Ability source, String name, GameEntity target) {
        if (name == null || name.isEmpty()) return source;
        if (target == null) return null;
        if (target == source.getOwner() && name.equals(source.getData().abilityName)) return source;
        for (var candidate : target.getInstancedAbilities())
            if (candidate != null && name.equals(candidate.getData().abilityName)) return candidate;
        return target.getDynamicAbilities().get(name);
    }
}
