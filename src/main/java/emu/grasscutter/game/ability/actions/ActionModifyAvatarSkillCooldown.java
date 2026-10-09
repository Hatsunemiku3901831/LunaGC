package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;

@AbilityAction(AbilityModifierAction.Type.ModifyAvatarSkillCD)
public final class ActionModifyAvatarSkillCooldown extends AbilityActionHandler {
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
        // Slot, minimum and absolute-time operations use distinct client branches. Do not silently
        // treat one of those as an explicit-ID delta/ratio operation.
        if (ability == null
                || action == null
                || !(target instanceof EntityAvatar avatar)
                || action.skillID <= 0
                || action.absoluteCdTime != null
                || action.cdMin != null
                || action.skillSlot != null && action.skillSlot.length != 0
                || !AbilityGlobalValues.isCurrent(ability, avatar)) return false;
        var player = ability.getPlayerOwner();
        if (player == null || avatar.getPlayer() != player) return false;
        if (!conditionsPass(ability, action, avatar)) return true;
        var properties = propertiesFor(ability, avatar);
        var delta =
                action.cdDelta == null
                        ? java.util.OptionalDouble.of(0)
                        : action.cdDelta.tryGet(properties);
        var operand = action.cdRatio != null ? action.cdRatio : action.legacyCdRatio;
        var ratio = operand == null ? java.util.OptionalDouble.of(0) : operand.tryGet(properties);
        return delta.isPresent()
                && ratio.isPresent()
                && player.getAbilityManager()
                        .getSkillCooldown()
                        .modifyRemaining(
                                avatar.getAvatar(),
                                action.skillID,
                                (float) delta.getAsDouble(),
                                (float) ratio.getAsDouble());
    }
}
