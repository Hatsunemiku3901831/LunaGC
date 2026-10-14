package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Phlogiston;

@AbilityMixin(value = AbilityMixinData.Type.PhlogistonCostMixin)
public class PhlogistonCostMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability,
            AbilityMixinData mixinData,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null || mixinData == null || mixinData.speed == null) return false;
        var cost = mixinData.speed.tryGet(AbilityActionHandler.propertiesFor(ability));
        return cost.isPresent()
                && cost.getAsDouble() >= 0
                && Phlogiston.change(ability, target, (float) -cost.getAsDouble());
    }
}
