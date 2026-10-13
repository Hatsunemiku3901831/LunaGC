package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.RemoveUniqueModifier)
public final class ActionRemoveUniqueModifier extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        if (ability == null
                || action == null
                || target == null
                || !AbilityGlobalValues.isCurrent(ability, target)
                || action.modifierName == null
                || action.modifierName.isBlank()) return false;
        if (!conditionsPass(ability, action, target)) return true;
        ability.removeUniqueModifier(target, action.modifierName);
        return true;
    }
}
