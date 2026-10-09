package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.*;

import java.util.Set;

@AbilityAction({
    AbilityModifierAction.Type.AddAvatarSkillInfo,
    AbilityModifierAction.Type.RemoveAvatarSkillInfo
})
public final class ActionAvatarSkillInfo extends AbilityActionHandler {
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CurLocalAvatar",
                    "OriginOwner",
                    "CasterOriginOwner",
                    "Team");

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null || action == null) return false;
        return executeResolved(
                ability,
                action,
                data,
                resolveTarget(
                        ability, target == null ? ability.getOwner() : target, action.target));
    }

    @Override
    public boolean executeResolved(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null
                || action == null
                || action.skillID <= 0
                || target == null
                || action.target != null && !TARGETS.contains(action.target)
                || !AbilityGlobalValues.isCurrent(ability, target)) return false;
        var player = ability.getPlayerOwner();
        if (player == null) return false;
        EntityAvatar recipient = target instanceof EntityAvatar avatar ? avatar : null;
        if (target instanceof EntityWeapon weapon && weapon.getCasterAvatar() != null)
            recipient = weapon.getCasterAvatar().getAsEntity();
        if (target == player.getTeamManager().getEntity()
                || player.getWorld() != null && target == player.getWorld().getEntity())
            recipient = player.getTeamManager().getCurrentAvatarEntity();
        if (recipient == null
                || recipient.getPlayer() != player
                || !AbilityGlobalValues.isCurrent(ability, recipient)) return false;
        if (!conditionsPass(ability, action, recipient)) return true;
        return switch (action.type) {
            case AddAvatarSkillInfo -> recipient
                    .getAbilityAvatarSkillInfo()
                    .add(ability, action.skillID);
            case RemoveAvatarSkillInfo -> recipient
                    .getAbilityAvatarSkillInfo()
                    .remove(action.skillID);
            default -> false;
        };
    }
}
