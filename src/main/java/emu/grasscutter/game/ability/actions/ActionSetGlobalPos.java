package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

/** Writes a deterministic position without guessing unconfirmed terrain or bone transforms. */
@AbilityAction(AbilityModifierAction.Type.SetGlobalPos)
public final class ActionSetGlobalPos extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        return write(ability, action, target, false);
    }

    @Override
    public boolean executeResolved(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        return write(ability, action, target, true);
    }

    private boolean write(
            Ability ability, AbilityModifierAction action, GameEntity target, boolean resolved) {
        var recipient = AbilityBornPosition.recipient(ability, action, target, resolved);
        if (recipient == null
                || action.born == null
                || action.born.isJsonNull()
                || !AbilityBornPosition.targetWriteSupported(action, ability, recipient)
                || !conditionsPass(ability, action, recipient)) return false;
        var position = AbilityBornPosition.resolve(action.born, recipient, recipient);
        return position != null
                && recipient.getAbilityGlobalPositions().write(ability, action.key, position);
    }
}
