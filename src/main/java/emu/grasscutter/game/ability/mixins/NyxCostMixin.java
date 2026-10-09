package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NyxValue;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.NyxCostMixin)
public final class NyxCostMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData mixin, ByteString data, GameEntity target) {
        // A supplied value that evaluates to zero must stay zero, rather than falling back to +1.
        var delta = mixin.value != null ? mixin.value : mixin.speed != DynamicFloat.ZERO ? mixin.speed
                : mixin.ratio != DynamicFloat.ONE ? mixin.ratio : DynamicFloat.ZERO;
        return NyxValue.change(ability, target, delta, mixin.minValue, mixin.maxValue, false);
    }
}
