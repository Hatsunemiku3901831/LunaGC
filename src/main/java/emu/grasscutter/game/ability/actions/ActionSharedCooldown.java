package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;

import java.util.Set;

@AbilityAction({
    AbilityModifierAction.Type.AvatarShareCDSkillStart,
    AbilityModifierAction.Type.ActivitySkillShareCDStart,
    AbilityModifierAction.Type.ClearShareCD
})
public final class ActionSharedCooldown extends AbilityActionHandler {
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

    public static boolean supported(AbilityModifierAction action) {
        if (action == null
                || action.type == null
                || action.target != null && !TARGETS.contains(action.target)) return false;
        if (action.type == AbilityModifierAction.Type.ClearShareCD)
            return action.shareCDID > 0
                    && GameData.getShareCDDataMap().containsKey(action.shareCDID);
        if (action.skillID <= 0 || action.shareCDType == null) return false;
        int group = 0;
        if (action.type == AbilityModifierAction.Type.AvatarShareCDSkillStart) {
            var data = GameData.getAvatarSkillDataMap().get(action.skillID);
            if (data != null) group = data.getShareCDID();
        } else if (action.type == AbilityModifierAction.Type.ActivitySkillShareCDStart) {
            var data = GameData.getActivitySkillDataMap().get(action.skillID);
            if (data != null) group = data.getShareCDID();
        }
        var data = GameData.getShareCDDataMap().get(group);
        return group > 0
                && data != null
                && data.getMaxChargeNum() > 0
                && data.getMaxChargeNum() <= 64
                && data.durationMs(action.shareCDType) >= 0;
    }

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
                || target == null
                || !supported(action)
                || !AbilityGlobalValues.isCurrent(ability, target)) return false;
        var player = ability.getPlayerOwner();
        if (player == null || player.getAbilityManager() == null) return false;
        EntityAvatar recipient = target instanceof EntityAvatar avatar ? avatar : null;
        if (target instanceof EntityWeapon weapon && weapon.getCasterAvatar() != null)
            recipient = weapon.getCasterAvatar().getAsEntity();
        if (target == player.getTeamManager().getEntity()
                || player.getWorld() != null && target == player.getWorld().getEntity())
            recipient = player.getTeamManager().getCurrentAvatarEntity();
        if (recipient == null
                || recipient.getPlayer() != player
                || !AbilityGlobalValues.isCurrent(ability, recipient)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var cooldown = player.getAbilityManager().getSharedCooldown();
        return AbilityCooldownInvocation.recordShared(
                ability,
                action,
                recipient,
                () ->
                        switch (action.type) {
                            case AvatarShareCDSkillStart -> cooldown.startAvatarSkill(
                                    action.skillID, action.shareCDType);
                            case ActivitySkillShareCDStart -> cooldown.startActivitySkill(
                                    action.skillID, action.shareCDType);
                            case ClearShareCD -> cooldown.clear(action.shareCDID);
                            default -> false;
                        });
    }
}
