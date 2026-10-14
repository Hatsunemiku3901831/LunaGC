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
        if (ability == null || action == null || target == null) return false;
        boolean current =
                action.value != null
                        || action.costType != null
                        || action.rawPhlogistonCostType != null;
        String mode =
                current
                        ? action.costType != null
                                ? action.costType
                                : action.rawPhlogistonCostType != null
                                        ? action.rawPhlogistonCostType
                                        : "Cost"
                        : action.determineType;
        var expression = current ? action.value : action.ratio;
        if (expression == null
                || !("Cost".equals(mode)
                        || "Sub".equals(mode)
                        || "Add".equals(mode)
                        || "Set".equals(mode))) return false;
        var amount = expression.tryGet(propertiesFor(ability));
        if (amount.isEmpty()) return false;
        float delta = (float) amount.getAsDouble();
        if ("Sub".equals(mode) || "Cost".equals(mode)) delta = -delta;
        return conditionsPass(ability, action, target)
                && Phlogiston.mutate(ability, target, delta, "Set".equals(mode));
    }
}
