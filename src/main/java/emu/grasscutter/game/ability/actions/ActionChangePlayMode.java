package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;

import java.util.Set;

/** Mirror the local avatar's native enter/exit action without inventing a nightsoul global flag. */
@AbilityAction(AbilityModifierAction.Type.ChangePlayMode)
public final class ActionChangePlayMode extends AbilityActionHandler {
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
        if (ability == null
                || action == null
                || action.target != null && !TARGETS.contains(action.target)) return false;
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
                || target == null
                || action.target != null && !TARGETS.contains(action.target)
                || action.fromPlayMode == null
                || action.toPlayMode == null
                || !AbilityGlobalValues.isCurrent(ability, target)) return false;
        var player = ability.getPlayerOwner();
        if (player == null || player.getTeamManager().getActiveTeamSnapshot().isEmpty())
            return false;
        var current = player.getTeamManager().getCurrentAvatarEntity();
        EntityAvatar recipient = target instanceof EntityAvatar avatar ? avatar : null;
        if (target == player.getTeamManager().getEntity()
                || player.getWorld() != null && target == player.getWorld().getEntity())
            recipient = current;
        if (recipient == null
                || recipient != current
                || recipient.getPlayer() != player
                || !AbilityGlobalValues.isCurrent(ability, recipient)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        return recipient.getAbilityPlayMode().change(action.fromPlayMode, action.toPlayMode);
    }
}
