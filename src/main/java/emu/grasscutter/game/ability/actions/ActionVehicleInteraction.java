package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction({
    AbilityModifierAction.Type.TriggerVehicleOff,
    AbilityModifierAction.Type.TriggerVehicleOn
})
public final class ActionVehicleInteraction extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        return executeResolved(ability, action, payload, getTarget(ability, target, action.target));
    }

    @Override
    public boolean executeResolved(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        if (ability == null || ability.getPlayerOwner() == null) return false;
        if (action.type == AbilityModifierAction.Type.TriggerVehicleOff) {
            NatsaurusVehicleHelper.eject(ability.getPlayerOwner());
            return true;
        }
        return NatsaurusVehicleHelper.board(ability.getPlayerOwner(), target) != null;
    }
}
