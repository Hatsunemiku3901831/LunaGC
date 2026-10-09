package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.AttachModifier)
public final class ActionAttachModifier extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        Grasscutter.getLogger().debug("[Ability] AttachModifier: {}", action.modifierName);

        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) {
            Grasscutter.getLogger().debug("Modifier {} not found", action.modifierName);
            return false;
        }

        if ("AllPlayerAvatars".equals(action.target)) {
            return applyToAllPlayerAvatars(ability, action, abilityData);
        }

        return executeResolved(ability, action, abilityData, target);
    }

    @Override
    public boolean executeResolved(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) return false;

        Grasscutter.getLogger().debug("[Ability] AttachModifier fallback target={}", action.target);

        if (!conditionsPass(ability, action, target)) return true;
        ability.registerModifier(
                target, new AbilityModifierController(ability, ability.getData(), modifierData));
        if ("AllPlayerAvatars".equals(action.target))
            ability.getManager()
                    .executeCallbackActionsNow(
                            ability, modifierData.onAdded, abilityData, target, target);
        return true;
    }

    private boolean applyToAllPlayerAvatars(
            Ability ability, AbilityModifierAction action, ByteString abilityData) {
        var player = ability.getPlayerOwner();
        if (player == null) return false;

        var team = player.getTeamManager().getActiveTeamSnapshot();
        var seen = new java.util.HashSet<Integer>();
        for (var avatarEntity : team) {
            if (!seen.add(avatarEntity.getId())) continue;
            executeResolved(ability, action, abilityData, avatarEntity);
        }
        return true;
    }
}
