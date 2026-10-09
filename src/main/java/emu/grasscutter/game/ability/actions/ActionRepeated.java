package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.Repeated)
public final class ActionRepeated extends AbilityActionHandler {
    @Override public boolean execute(Ability ability,AbilityModifierAction action,ByteString payload,GameEntity target) {
        if (action==null || !AbilityGlobalValues.isCurrent(ability,target)) return false;
        if (!conditionsPass(ability,action,target)) return true;
        var count=AbilityRepeatCount.resolve(ability,action);
        if (count.isEmpty()) return false;
        if (action.actions==null || count.getAsInt()==0) return true;
        return AbilityActionBudget.run(()->{
            if (!AbilityActionBudget.consume()) return false;
            for (int i=0;i<count.getAsInt();i++) {
                if (!AbilityGlobalValues.isCurrent(ability,target)
                        || !ability.getManager().executeCallbackActionsNow(ability,action.actions,payload,target,target)) return false;
            }
            return true;
        });
    }
}
