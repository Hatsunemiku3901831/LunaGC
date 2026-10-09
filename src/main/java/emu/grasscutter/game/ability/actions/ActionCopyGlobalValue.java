package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.CopyGlobalValue)
public final class ActionCopyGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity entity) {
        if (ability == null || action == null || entity == null) return false;
        if (!conditionsPass(ability, action, entity)) return true;
        // Get the entities referred to.
        var source = this.getTarget(ability, entity, action.srcTarget);
        var destination = this.getTarget(ability, entity, action.dstTarget);
        // Check the entities.
        if (source == null || destination == null) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source or destination is null");
            return false;
        }

        // An action can name neither key - Skirk's pickable handler does - and the map these go
        // into rejects a null key outright.
        if (action.srcKey == null || action.srcKey.isBlank() || action.dstKey == null || action.dstKey.isBlank()) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source or destination key is null");
            return false;
        }

        if (!AbilityGlobalValues.isCurrent(ability,source)) return false;
        // Get the global value.
        var value = source.getGlobalAbilityValues().get(action.srcKey);
        if (value == null) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source value is null");
            return false;
        }

        return AbilityGlobalValues.copy(ability,destination,action.dstKey,value);
    }
}
