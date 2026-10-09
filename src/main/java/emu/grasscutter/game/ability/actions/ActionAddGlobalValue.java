package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.AddGlobalValue)
public final class ActionAddGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var value=action.writtenValue().tryGet(propertiesFor(ability));
        return value.isPresent() && AbilityGlobalValues.write(ability,action,target,(float)value.getAsDouble(),true);
    }
}
