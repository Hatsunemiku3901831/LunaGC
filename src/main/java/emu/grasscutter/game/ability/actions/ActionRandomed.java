package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.ability.AbilityRandomBranch;
import emu.grasscutter.game.entity.GameEntity;

/**
 * Takes one branch or the other, as often as the config says.
 *
 * <p>With no handler at all neither branch ever ran, so anything behind a chance simply never
 * happened. The branches are dispatched the same way a Predicated block's are, including the
 * restriction on what a chain the client did not ask for is allowed to do.
 */
@AbilityAction(AbilityModifier.AbilityModifierAction.Type.Randomed)
public final class ActionRandomed extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action == null || !AbilityGlobalValues.isCurrent(ability,target)) return false;
        if (!conditionsPass(ability,action,target)) return true;
        var chosen=AbilityRandomBranch.choose(ability,action);
        if (chosen.isEmpty()) return false;
        return ability.getManager().executeCallbackActionsNow(ability,
                chosen.get()?action.successActions:action.failActions,abilityData,target,target);
    }
}
