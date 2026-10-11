package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AsterSpecialRewardDeliveryTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void missingOrMismatchedRewardDefinitionsCannotReserveTheSpecialReward(int missing)
            throws Exception {
        try (var f = new AsterSpecialTestFixture()) {
            if (missing == 0) GameData.getRewardDataMap().remove(3000000);
            if (missing == 1) GameData.getItemDataMap().remove(1031);
            if (missing == 2) GameData.getAvatarSkillDepotDataMap().remove(3101);
            if (missing == 3) GameData.getItemDataMap().remove(15101);
            if (missing == 4) GameData.getRewardDataMap().get(3000000).rewardId = 3000001;
            assertEquals(
                    Retcode.RET_SVR_ERROR_VALUE,
                    AsterSpecialRewardDelivery.validate(f.base.player, 3000000));
            assertFalse(AsterSchedule.progress(f.base.event.data).hasPendingReward());
            assertEquals(0, f.base.player.getAvatars().getAvatarCount());
        }
    }

    @Test
    void fullWeaponInventoryRejectsANewAvatarBeforeReservationOrOwnershipChanges()
            throws Exception {
        try (var f = new AsterSpecialTestFixture()) {
            f.base.player
                    .getInventory()
                    .createInventoryTab(ItemType.ITEM_WEAPON, new EquipInventoryTab(0));
            assertEquals(
                    Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                    AsterSpecialRewards.take(f.base.event.data));
            assertFalse(AsterSchedule.progress(f.base.event.data).hasPendingReward());
            assertEquals(0, f.base.player.getAvatars().getAvatarCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void avatarConversionMustFitBothStacksBeforeCreatingEitherOne(int full) throws Exception {
        try (var f = new AsterSpecialTestFixture()) {
            var player = f.base.player;
            var avatar = new Avatar(10000031);
            avatar.setOwner(player);
            player.getAvatars().getAvatars().put(10000031, avatar);
            var tab = new MaterialInventoryTab(full == 0 ? 1 : 2);
            player.getInventory().createInventoryTab(ItemType.ITEM_MATERIAL, tab);
            if (full > 0) {
                int id = full == 1 ? 1131 : 221;
                var item = new GameItem(id);
                item.setCount(GameData.getItemDataMap().get(id).getStackLimit());
                tab.onAddItem(item);
            }
            // A C6-equivalent token stock only needs the starglitter stack; make it full too.
            if (full == 1) {
                var glitter = new GameItem(221);
                glitter.setCount(GameData.getItemDataMap().get(221).getStackLimit());
                tab.onAddItem(glitter);
            }
            assertEquals(
                    Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                    AsterSpecialRewards.take(f.base.event.data));
            assertFalse(AsterSchedule.progress(f.base.event.data).hasPendingReward());
            assertEquals(1, player.getAvatars().getAvatarCount());
            assertEquals(full == 0 ? 0 : full == 1 ? 2 : 1, tab.getSize());
        }
    }

    @Test
    void anotherRewardIdCannotUseFischlsDeliveryPath() throws Exception {
        try (var f = new AsterSpecialTestFixture()) {
            assertEquals(
                    Retcode.RET_SVR_ERROR_VALUE,
                    AsterSpecialRewardDelivery.validate(f.base.player, 3000001));
        }
    }
}
