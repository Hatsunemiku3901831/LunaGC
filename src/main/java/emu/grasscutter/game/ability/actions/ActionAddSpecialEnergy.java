package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.AddSpecialEnergy)
public final class ActionAddSpecialEnergy extends AbilityActionHandler {
    @Override
    public boolean execute(Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (target == null || target.getFightProperties() == null) return false;
        if (!conditionsPass(ability, action, target)) return true;
        float delta = action.writtenValue().get(propertiesFor(ability), 0f);
        if (!Float.isFinite(delta)) return false;
        if (delta != 0f) target.addSpecialEnergy(delta);
        return true;
    }
}
