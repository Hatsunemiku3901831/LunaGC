package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.ItemUseGainAvatar;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.proto.AddNoGachaAvatarCardTransferItemOuterClass.AddNoGachaAvatarCardTransferItem;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketAddNoGachaAvatarCardNotify;

import java.util.*;

/** Fischl's original avatar card and repeat-conversion rules, with acknowledged entity writes. */
public final class AsterSpecialRewardDelivery {
    public static final int AVATAR = 10000031, CARD = 1031, TALENT = 1131, STARGLITTER = 221;

    private AsterSpecialRewardDelivery() {}

    private static boolean resourcesAvailable(int rewardId) {
        var reward = GameData.getRewardDataMap().get(rewardId);
        var card = GameData.getItemDataMap().get(CARD);
        var avatar = GameData.getAvatarDataMap().get(AVATAR);
        var depot = GameData.getAvatarSkillDepotDataMap().get(3101);
        var weapon = GameData.getItemDataMap().get(15101);
        return rewardId == AsterSpecialRewards.REWARD_ID
                && reward != null
                && reward.getId() == rewardId
                && reward.getRewardItemList() != null
                && reward.getRewardItemList().size() == 1
                && reward.getRewardItemList().get(0).getId() == CARD
                && reward.getRewardItemList().get(0).getCount() == 1
                && card != null
                && card.getId() == CARD
                && card.getItemType() == ItemType.ITEM_MATERIAL
                && card.getMaterialType() == MaterialType.MATERIAL_AVATAR
                && card.isUseOnGain()
                && card.getRankLevel() == 4
                && card.getItemUseActions() != null
                && card.getItemUseActions().size() == 1
                && card.getItemUseActions().get(0) instanceof ItemUseGainAvatar gain
                && gain.getI() == AVATAR
                && avatar != null
                && avatar.getId() == AVATAR
                && "AVATAR_FORMAL".equals(avatar.getUseType())
                && "QUALITY_PURPLE".equals(avatar.getQualityType())
                && avatar.getSkillDepotId() == 3101
                && avatar.getSkillDepot() == depot
                && depot != null
                && depot.getTalentCostItemId() == TALENT
                && avatar.getInitialWeapon() == 15101
                && weapon != null
                && weapon.getId() == 15101
                && weapon.getItemType() == ItemType.ITEM_WEAPON
                && !weapon.isUseOnGain();
    }

    private static List<ItemParamData> transfers(Player player) {
        int constellation = InventorySystem.checkPlayerAvatarConstellationLevel(player, AVATAR);
        return constellation < 0
                ? List.of()
                : constellation >= 6
                        ? List.of(new ItemParamData(STARGLITTER, 5))
                        : List.of(new ItemParamData(TALENT, 1), new ItemParamData(STARGLITTER, 2));
    }

    public static int validate(Player player, int rewardId) {
        if (player == null || !resourcesAvailable(rewardId)) return Retcode.RET_SVR_ERROR_VALUE;
        var owned = player.getAvatars().getAvatarById(AVATAR);
        if (owned != null
                && (owned.getPlayer() != player
                        || owned.getTrialAvatarId() != 0
                        || owned.getSkillDepot() == null
                        || owned.getSkillDepotId() != 3101)) return Retcode.RET_SVR_ERROR_VALUE;
        var inventory = player.getInventory();
        if (owned == null) {
            var tab = inventory.getInventoryTab(ItemType.ITEM_WEAPON);
            return tab == null
                    ? Retcode.RET_SVR_ERROR_VALUE
                    : tab.getSize() >= tab.getMaxCapacity()
                            ? Retcode.RET_ITEM_EXCEED_LIMIT_VALUE
                            : 0;
        }
        var tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
        if (tab == null) return Retcode.RET_SVR_ERROR_VALUE;
        int newStacks = 0;
        for (var item : transfers(player)) {
            var definition = GameData.getItemDataMap().get(item.getId());
            if (definition == null
                    || definition.getId() != item.getId()
                    || definition.isUseOnGain()
                    || definition.getStackLimit() <= 0
                    || definition.getItemType() != ItemType.ITEM_MATERIAL
                    || definition.getMaterialType()
                            != (item.getId() == TALENT
                                    ? MaterialType.MATERIAL_TALENT
                                    : MaterialType.MATERIAL_CONSUME))
                return Retcode.RET_SVR_ERROR_VALUE;
            var existing = tab.getItemById(item.getId());
            if (existing == null) newStacks++;
            if (existing != null && existing.getCount() < 0) return Retcode.RET_SVR_ERROR_VALUE;
            if ((long) inventory.getItemCountById(item.getId()) + item.getCount()
                    > definition.getStackLimit()) return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        }
        return (long) tab.getSize() + newStacks > tab.getMaxCapacity()
                ? Retcode.RET_ITEM_EXCEED_LIMIT_VALUE
                : 0;
    }

    /** Caller holds the inventory monitor until the activity receipt is also acknowledged. */
    public static void grant(Player player, int rewardId) {
        if (validate(player, rewardId) != 0)
            throw new IllegalStateException("Aster special reward preflight changed");
        var avatar = player.getAvatars().getAvatarById(AVATAR);
        if (avatar == null) {
            avatar = new Avatar(AVATAR);
            player.addAvatar(avatar, false);
            if (player.getAvatars().getAvatarById(AVATAR) != avatar
                    || avatar.getWeapon() == null
                    || avatar.getWeapon().getItemId() != 15101
                    || avatar.getWeapon().getEquipCharacter() != AVATAR)
                throw new IllegalStateException("Aster avatar or starting weapon was not granted");
            DatabaseHelper.saveGameSync(avatar.getWeapon());
            DatabaseHelper.saveGameSync(avatar);
            DatabaseHelper.saveGameSync(player);
            player.sendPacket(
                    new PacketAddNoGachaAvatarCardNotify(
                            avatar, ActionReason.AsterTakeSpecialReward));
        } else {
            var transfer = new ArrayList<AddNoGachaAvatarCardTransferItem>();
            for (var param : transfers(player)) {
                int before = player.getInventory().getItemCountById(param.getId());
                boolean isNew = player.getInventory().getItemById(param.getId()) == null;
                if (!player.getInventory().addItem(param, ActionReason.AsterTakeSpecialReward)
                        || (long) player.getInventory().getItemCountById(param.getId()) - before
                                != param.getCount())
                    throw new IllegalStateException("Aster avatar conversion was not granted");
                DatabaseHelper.saveGameSync(player.getInventory().getItemById(param.getId()));
                transfer.add(
                        AddNoGachaAvatarCardTransferItem.newBuilder()
                                .setItemId(param.getId())
                                .setCount(param.getCount())
                                .setIsNew(isNew)
                                .build());
            }
            DatabaseHelper.saveGameSync(player);
            player.sendPacket(
                    new PacketAddNoGachaAvatarCardNotify(
                            avatar, ActionReason.AsterTakeSpecialReward, transfer));
        }
    }
}
