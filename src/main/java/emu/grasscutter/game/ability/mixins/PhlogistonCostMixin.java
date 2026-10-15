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
        if (cost.isEmpty() || cost.getAsDouble() < 0) return false;
        var basis =
                mixinData.phlogistonUsesRatio()
                        ? Phlogiston.RatioBasis.CURRENT
                        : Phlogiston.RatioBasis.ABSOLUTE;
        String pool =
                mixinData.targetType != null
                        ? mixinData.targetType
                        : mixinData.rawPhlogistonTargetType;
        float amount = (float) -cost.getAsDouble();
        // An omitted target retains the legacy owner's pool, including an empty Saurian tank.
        return pool == null
                ? Phlogiston.mutate(ability, target, amount, false, basis)
                : Phlogiston.change(ability, target, amount, pool, basis);
    }
}
