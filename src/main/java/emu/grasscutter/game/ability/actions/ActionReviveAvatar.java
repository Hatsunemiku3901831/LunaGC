package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityAvatarRevival;
import emu.grasscutter.game.ability.AbilityAutomaticRevival;
import emu.grasscutter.game.ability.AbilityRevivalSkillControl;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction({
    AbilityModifierAction.Type.ReviveAvatar,
    AbilityModifierAction.Type.ReviveDeadAvatar
})
public final class ActionReviveAvatar extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        return revive(ability, action, target, false);
    }

    @Override
    public boolean executeResolved(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        return revive(ability, action, target, true);
    }

    private boolean revive(
            Ability ability, AbilityModifierAction action, GameEntity target, boolean resolved) {
        if (AbilityAutomaticRevival.nativeAbility(ability))
            return AbilityAutomaticRevival.nativeConfirmation(ability,target);
        if (AbilityRevivalSkillControl.nativeAbility(ability))
            return ability.getManager()
                    .getRevivalSkillControl()
                    .nativeAction(ability, action, target, false);
        if (ability == null || target == null || !AbilityAvatarRevival.supported(action))
            return false;
        if (AbilityRevivalSkillControl.controlled(action))
            return AbilityRevivalSkillControl.run(
                    ability, action, () -> reviveRecipients(ability, action, target, resolved));
        reviveRecipients(ability, action, target, resolved);
        return true;
    }

    private boolean reviveRecipients(
            Ability ability, AbilityModifierAction action, GameEntity target, boolean resolved) {
        boolean revived = false;
        for (var avatar : AbilityAvatarRevival.recipients(ability, action, target, resolved)) {
            synchronized (avatar) {
                if (!AbilityAvatarRevival.eligible(ability, action, avatar)
                        || !conditionsPass(ability, action, avatar)) continue;
                var health = AbilityAvatarRevival.health(ability, action, avatar);
                if (health.isPresent()) revived |= avatar.revive((float) health.getAsDouble());
            }
        }
        return revived;
    }
}
