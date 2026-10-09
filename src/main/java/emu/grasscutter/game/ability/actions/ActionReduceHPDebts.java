package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.HpDebtSettlement;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.ReduceHPDebts)
public final class ActionReduceHPDebts extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || !(target instanceof EntityAvatar)) return false;
        synchronized (target) {
            if (!conditionsPass(ability, action, target)) return true;
            float amount = action.writtenValue().get(propertiesFor(ability), 0f);
            HpDebtSettlement.repay(target, amount);
        }
        return true;
    }
}
