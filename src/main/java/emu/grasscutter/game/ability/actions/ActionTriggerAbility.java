package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.TriggerAbility)
public final class ActionTriggerAbility extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        Grasscutter.getLogger().debug("[Ability] TriggerAbility: {}", action.abilityName);
        return ability.getManager().triggerAbility(target, action.abilityName);
    }
}
