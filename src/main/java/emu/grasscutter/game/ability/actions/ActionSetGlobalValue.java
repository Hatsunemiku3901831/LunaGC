package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.GameEntity;

/**
 * Writes an ability's global value.
 *
 * <p>Both versions honor the configured min/max range only when useLimitRange is enabled.
 * Unbounded writes retain their value even though the absent bounds default to zero.
 */
@AbilityAction({
    AbilityModifierAction.Type.SetGlobalValue,
    AbilityModifierAction.Type.SetGlobalValueV2
})
public final class ActionSetGlobalValue extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var value=action.writtenValue().tryGet(propertiesFor(ability));
        return value.isPresent() && AbilityGlobalValues.write(ability,action,target,(float)value.getAsDouble(),false);
    }
}
