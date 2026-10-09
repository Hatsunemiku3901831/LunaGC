package emu.grasscutter.game.ability.actions;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import com.google.protobuf.ByteString;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilitySkillCooldown;
import emu.grasscutter.game.ability.AbilityTalentResolver;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.managers.stamina.Consumption;
import emu.grasscutter.game.managers.stamina.ConsumptionType;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.server.game.GameSession;

@AbilityAction(AbilityModifierAction.Type.AvatarSkillStart)
public class ActionAvatarSkillStart extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null || action == null) return false;
        return executeResolved(
                ability,
                action,
                abilityData,
                resolveTarget(
                        ability, target == null ? ability.getOwner() : target, action.target));
    }

    @Override
    public boolean executeResolved(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null
                || action == null
                || target == null
                || !AbilityGlobalValues.isCurrent(ability, target)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var owner = ability.getOwner();
        var player = ability.getPlayerOwner();
        EntityAvatar recipient =
                target instanceof EntityAvatar value ? value : ability.getCasterEntity();
        if (recipient != null
                && player != null
                && recipient.getPlayer() == player
                && AbilitySkillCooldown.ownsSkill(recipient.getAvatar(), action.skillID)
                && AbilityGlobalValues.isCurrent(ability, recipient)) {
            var data = GameData.getAvatarSkillDataMap().get(action.skillID);
            if (data != null && data.getShareCDID() == 0) {
                int count =
                        recipient
                                .getAvatar()
                                .getSkillInfoCharges()
                                .getOrDefault(action.skillID, data.getMaxChargeNum());
                // A single recovery is idempotent until it expires. Multiple charges need a
                // proved client-instance identity before recording both autonomous callbacks
                // and their confirmations; their explicit start path remains available.
                if (AbilityManager.isServerOwnedChain() && count != 1) return true;
                var multiplier =
                        action.cdTimeRatio != null ? action.cdTimeRatio : action.legacyCdRatio;
                var ratio =
                        multiplier == null
                                ? java.util.OptionalDouble.of(1)
                                : multiplier.tryGet(propertiesFor(ability, recipient));
                var duration =
                        AbilityTalentResolver.cooldownSeconds(
                                recipient.getAvatar(), action.skillID, data.getCdTime());
                if (ratio.isPresent() && ratio.getAsDouble() >= 0 && duration.isPresent())
                    player.getAbilityManager()
                            .getSkillCooldown()
                            .start(
                                    recipient.getAvatar(),
                                    action.skillID,
                                    duration.getAsDouble() * ratio.getAsDouble());
            }
        }
        // Server-owned callbacks only record the cooldown; resource actions already run locally.
        if (AbilityManager.isServerOwnedChain()) return true;
        float costStaminaRatio = action.costStaminaRatio.get(ability);

        if (costStaminaRatio != 1.0f && costStaminaRatio != 0.0f) {
            if (player != null) {
                StaminaManager staminaManager = player.getStaminaManager();
                GameSession session = player.getSession();

                int staminaCost = (int) (costStaminaRatio * 100);

                Consumption consumption =
                        new Consumption(ConsumptionType.FIGHT, -Math.abs(staminaCost));

                staminaManager.updateStaminaRelative(session, consumption, true);
                staminaManager.staminaRecoverDelay = 0;
            }
        }

        if (action.skillID == 11065 && recipient != null) {
            Avatar avatar = recipient.getAvatar();
            if (GAME_OPTIONS.energyUsage) {
                avatar.clearSpecialEnergy();
            } else {

                avatar.addSpecialEnergy(
                        avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
            }
        }
        if (owner instanceof EntityAvatar avatar) {
            avatar.getPlayer()
                    .getQuestManager()
                    .queueEvent(QuestContent.QUEST_CONTENT_SKILL, action.skillID);
        } else {
            Grasscutter.getLogger()
                    .warn(
                            "AvatarSkillStart not implemented for other entities than EntityAvatar"
                                    + " right now");
            return false;
        }

        return true;
    }
}
