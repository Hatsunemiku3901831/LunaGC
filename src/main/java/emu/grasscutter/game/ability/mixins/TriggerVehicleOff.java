package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.TriggerVehicleOff)
public final class TriggerVehicleOff extends AbilityMixinHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData data, ByteString payload, GameEntity target) {
        if (ability == null || ability.getPlayerOwner() == null) return false;
        NatsaurusVehicleHelper.eject(ability.getPlayerOwner());
        return true;
    }
}
