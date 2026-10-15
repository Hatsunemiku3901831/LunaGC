package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.HpDebtHealConversion;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

@AbilityAction(AbilityModifierAction.Type.HealHP)
public final class ActionHealHP extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || action == null || target == null) return false;
        if (!ability.isServerFoodHealing()
                && ability.getPlayerOwner() != null
                && ability.getPlayerOwner().getBuffManager().ownsHealing(ability, action)) return true;
        var owner = ability.getOwner();

        if (owner instanceof EntityClientGadget ownerGadget) {
            owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());
            if (DebugConstants.LOG_ABILITIES) {
                Grasscutter.getLogger()
                        .debug(
                                "Owner {} has top owner {}: {}",
                                ability.getOwner(),
                                ownerGadget.getOwnerEntityId(),
                                owner);
            }
        }
        if (owner instanceof EntityClientGadget ownerGadget) {
            owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());

            if (ownerGadget.gadgetId == 41089013
                    || ownerGadget.gadgetId == 41089012
                    || ownerGadget.gadgetId == 41089011) {
                if (owner == null) {
                    owner = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
                }
            }
        }

        if (owner == null) return false;

        float finalAmount;
        float abilityRatio;
        synchronized (target) {
            if (!conditionsPass(ability, action, target)) return true;
            if (target instanceof EntityAvatar avatar
                    && (!avatar.isAlive() || avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f))
                return true;
            var properties = propertiesFor(ability, owner);

            var amountByCasterMaxHPRatio = action.amountByCasterMaxHPRatio.get(properties, 0);
            var amountByCasterAttackRatio = action.amountByCasterAttackRatio.get(properties, 0);
            var amountByCasterCurrentHPRatio = action.amountByCasterCurrentHPRatio.get(properties, 0);
            var amountByCasterCurrentHPDebtsRatio =
                    action.amountByCasterCurrentHPDebtsRatio.get(properties, 0);
            var amountByCasterDefRatio = action.amountByCasterDefRatio.get(properties, 0);
            var amountByTargetCurrentHPRatio = action.amountByTargetCurrentHPRatio.get(properties, 0);
            var amountByTargetMaxHPRatio = action.amountByTargetMaxHPRatio.get(properties, 0);
            var amountToRegenerate = action.amount.get(properties, 0);

            amountToRegenerate +=
                    amountByCasterMaxHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            amountToRegenerate +=
                    amountByCasterAttackRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
            amountToRegenerate +=
                    amountByCasterCurrentHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            amountToRegenerate +=
                    amountByCasterCurrentHPDebtsRatio
                            * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            amountToRegenerate +=
                    amountByCasterDefRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE);

            abilityRatio = 1.0f;
            if (!action.ignoreAbilityProperty && !ability.isServerFoodHealing())
                abilityRatio +=
                        owner.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD)
                                + target.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);

            amountToRegenerate +=
                    amountByTargetCurrentHPRatio * target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            amountToRegenerate +=
                    amountByTargetMaxHPRatio * target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

            String healTag = action.healTag;
            if ("MizukiBurstSelf".equals(healTag)) amountToRegenerate *= 2f;
            finalAmount = amountToRegenerate * abilityRatio * action.healRatio.get(properties, 1f);
            if (!Float.isFinite(finalAmount) || finalAmount <= 0f) return true;

            if (target instanceof EntityAvatar clorinde
                    && clorinde.getAvatar().getAvatarId() == 10000098) {
                float dodgeHealFlag = "Clorinde_ElementalArt_Heal".equals(healTag) ? 0f : 1f;
                target.getGlobalAbilityValues().put("_ABILITY_Clorinde_Dodge_HealFlag", dodgeHealFlag);
                target
                        .getWorld()
                        .broadcastPacket(
                                new PacketServerGlobalValueChangeNotify(
                                        target, "_ABILITY_Clorinde_Dodge_HealFlag", dodgeHealFlag));
            }

            if (HpDebtHealConversion.consume(ability, action, target, finalAmount)) return true;

            float realHeal = target.heal(finalAmount, action.muteHealEffect);
            if (realHeal > 0 && !action.muteHealEffect) {
                target
                        .getWorld()
                        .broadcastPacket(new PacketEvtBeingHealedNotify(owner, target, finalAmount, realHeal));
            }
        }

        if (finalAmount > 0) {
            var healOwner = owner;
            for (var mod : healOwner.getInstancedModifiersSnapshot()) {
                var modData = mod.getModifierData();
                var modAbility = mod.getAbility();
                if (modData != null && modData.onHeal != null && modAbility != null) {
                    ability
                            .getManager()
                            .executeHealingCallbacks(modAbility, modData.onHeal, healOwner, action.healTag);
                }
            }
        }

        if (finalAmount > 0
                && "Avatar_Furina_Constellation_2".equals(ability.getData().abilityName)
                && target instanceof EntityAvatar furinaTarget
                && furinaTarget.getAvatar().getAvatarId() == 10000089) {
            var player = ability.getPlayerOwner();
            if (player != null) {
                float baseAmount = finalAmount / abilityRatio;
                var seen = new java.util.HashSet<Long>();
                seen.add(furinaTarget.getAvatar().getGuid());
                for (var member : player.getTeamManager().getActiveTeam()) {
                    if (!seen.add(member.getAvatar().getGuid())) continue;
                    float memberRatio = 1.0f;
                    if (!action.ignoreAbilityProperty)
                        memberRatio +=
                                owner.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD)
                                        + member.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);
                    float spreadAmount = baseAmount * memberRatio;
                    float spreadReal;
                    synchronized (member) {
                        if (HpDebtHealConversion.consume(ability, action, member, spreadAmount)) continue;
                        spreadReal = member.heal(spreadAmount, action.muteHealEffect);
                    }
                    if (spreadReal > 0 && !action.muteHealEffect) {
                        member
                                .getWorld()
                                .broadcastPacket(
                                        new PacketEvtBeingHealedNotify(owner, member, spreadAmount, spreadReal));
                    }
                }
            }
        }

        return true;
    }
}
