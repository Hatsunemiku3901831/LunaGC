package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Phlogiston;

@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.ChangePhlogiston)
public final class ActionChangePhlogiston extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability,
            AbilityModifier.AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null
                || action == null
                || action.ratio == null
                || !("Add".equals(action.determineType) || "Sub".equals(action.determineType)))
            return false;
        var amount = action.ratio.tryGet(propertiesFor(ability));
        if (amount.isEmpty()) return false;
        float delta = (float) amount.getAsDouble();
        if ("Sub".equals(action.determineType)) delta = -delta;
        return conditionsPass(ability, action, target) && Phlogiston.change(ability, target, delta);
    }
}
