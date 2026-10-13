package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.TryEnterVehicleMixin)
public final class TryEnterVehicleMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData data, ByteString payload, GameEntity target) {
        if (ability == null
                || ability.getManager() == null
                || !"Natsaurus".equals(data.vehicleType)) return false;
        var source =
                NatsaurusVehicleHelper.vehicleGadgetIdFor(target) != 0
                        ? target
                        : ability.getManager().getScanTarget();
        if (source == null) return true;
        return NatsaurusVehicleHelper.board(ability.getPlayerOwner(), source) != null;
    }
}
