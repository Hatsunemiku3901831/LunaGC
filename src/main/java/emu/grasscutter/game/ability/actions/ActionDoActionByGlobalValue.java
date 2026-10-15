package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.ability.AbilityGlobalValueDispatch;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.DoActionByGlobalValue)
public final class ActionDoActionByGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        if (ability == null || !AbilityGlobalValueDispatch.supported(action)) return false;
        return executeResolved(
                ability,
                action,
                payload,
                resolveTarget(
                        ability, target == null ? ability.getOwner() : target, action.target));
    }

    @Override
    public boolean executeResolved(
            Ability ability, AbilityModifierAction action, ByteString payload, GameEntity target) {
        if (ability == null || target == null || !AbilityGlobalValueDispatch.supported(action))
            return false;
        if (!conditionsPass(ability, action, target)) return true;
        return AbilityCooldownInvocation.recordApplication(
                ability,
                action,
                () -> {
                    var chosen = AbilityGlobalValueDispatch.choose(ability, action, target);
                    return chosen.isPresent()
                            && ability.getManager()
                                    .executeCallbackActionsNow(
                                            ability, chosen.get(), payload, target, target);
                });
    }
}
