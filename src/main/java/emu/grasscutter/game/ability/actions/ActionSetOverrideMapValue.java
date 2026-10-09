package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityOverrideMap;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.SetOverrideMapValue)
public final class ActionSetOverrideMapValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null) return false;
        if (target == null) target = ability.getOwner();
        if (!conditionsPass(ability, action, target)) return true;
        var properties = propertiesFor(ability);
        var evaluated = action.writtenValue().tryGet(properties);
        if (evaluated.isEmpty()) return false;
        float value = (float)evaluated.getAsDouble();
        if (action.useLimitRange) {
            var lower = action.minValue.tryGet(properties);
            var upper = action.maxValue.tryGet(properties);
            if (lower.isEmpty() || upper.isEmpty()) return false;
            float minimum = (float)lower.getAsDouble();
            float maximum = (float)upper.getAsDouble();
            if (minimum > maximum) return false;
            value = Math.max(minimum, Math.min(maximum, value));
        }
        return AbilityOverrideMap.write(ability, action, target, value);
    }
}
