package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.HpDebtSettlement;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.AddHPDebts)
public final class ActionAddHPDebts extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        synchronized (target) {
            if (!conditionsPass(ability, action, target)) return true;
            var owner = ability.getOwner();
            if (owner instanceof EntityWeapon) {
                var player = ability.getPlayerOwner();
                owner = player == null ? null : player.getTeamManager().getCurrentAvatarEntity();
            }
            if (owner == null) return false;
            float amount = action.writtenValue().get(propertiesFor(ability, owner), 0f);
            HpDebtSettlement.add(target, amount);
        }
        return true;
    }
}
