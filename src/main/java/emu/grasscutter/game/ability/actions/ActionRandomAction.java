package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.RandomAction)
public final class ActionRandomAction extends AbilityActionHandler {
    @Override public boolean execute(Ability ability,AbilityModifierAction action,ByteString payload,GameEntity target) {
        if (action==null || !AbilityGlobalValues.isCurrent(ability,target)) return false;
        if (!conditionsPass(ability,action,target)) return true;
        var selected=AbilityRandomQueue.choose(ability,action,target);
        return selected.isPresent() && ability.getManager().executeCallbackActionsNow(ability,selected.get(),payload,target,target);
    }
}
