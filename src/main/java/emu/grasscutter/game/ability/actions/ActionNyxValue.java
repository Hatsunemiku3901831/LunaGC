package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NyxValue;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction({AbilityModifierAction.Type.NyxAdd, AbilityModifierAction.Type.NyxSet})
public final class ActionNyxValue extends AbilityActionHandler {
    @Override
    public boolean execute(Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (target == null) return false;
        if (!conditionsPass(ability, action, target)) return true;
        return NyxValue.change(ability, target, action.writtenValue(), action.minValue,
                action.maxValue, action.type == AbilityModifierAction.Type.NyxSet);
    }
}
