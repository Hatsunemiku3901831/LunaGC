package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin)
public final class DoActionOnVehicleInteractPostMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData data, ByteString payload, GameEntity target) {
        return ability != null
                && ability.getManager() != null
                && ability.getManager()
                        .executeCallbackActionsNow(
                                ability,
                                NatsaurusVehicleHelper.isRiding(ability.getPlayerOwner())
                                        ? data.onVehicleIn
                                        : data.onVehicleOut,
                                payload,
                                target,
                                target);
    }
}
