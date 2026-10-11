package io.grasscutter;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.aster.AsterActivityHandler;

import java.util.Set;

/** Real Fischl, avatar-card, constellation and weapon metadata, all restored after each test. */
final class AsterSpecialTestFixture implements AutoCloseable {
    final AsterShopTestFixture base = new AsterShopTestFixture();

    AsterSpecialTestFixture() throws Exception {
        var event = base.event;
        event.load(
                "RewardExcelConfigData.json",
                RewardData.class,
                GameData.getRewardDataMap(),
                id -> id == 3000000);
        event.load(
                "MaterialExcelConfigData.json",
                ItemData.class,
                GameData.getItemDataMap(),
                id -> Set.of(1031, 1131, 221).contains(id));
        event.load(
                "WeaponExcelConfigData.json",
                ItemData.class,
                GameData.getItemDataMap(),
                id -> id == 15101);
        event.load(
                "AvatarTalentExcelConfigData.json",
                AvatarTalentData.class,
                GameData.getAvatarTalentDataMap(),
                id -> id >= 311 && id <= 316);
        event.load(
                "AvatarSkillExcelConfigData.json",
                AvatarSkillData.class,
                GameData.getAvatarSkillDataMap(),
                id -> id == 10313);
        event.load(
                "AvatarSkillDepotExcelConfigData.json",
                AvatarSkillDepotData.class,
                GameData.getAvatarSkillDepotDataMap(),
                id -> id == 3101);
        event.load(
                "AvatarExcelConfigData.json",
                AvatarData.class,
                GameData.getAvatarDataMap(),
                id -> id == 10000031);
        for (var mission : GameData.getAsterMissionDataMap().values()) {
            int id = mission.getWatcherId();
            event.data
                    .getWatcherInfoMap()
                    .putIfAbsent(
                            id,
                            PlayerActivityData.WatcherInfo.of()
                                    .watcherId(id)
                                    .totalProgress(
                                            GameData.getActivityWatcherDataMap()
                                                    .get(id)
                                                    .getProgress())
                                    .build());
        }
        AsterSpecialRewardsTest.eligible(event.data);
        var handler = new AsterActivityHandler();
        handler.setActivityConfigItem(event.config);
        event.config.setActivityHandler(handler);
        event.data.setActivityHandler(handler);
    }

    @Override
    public void close() throws Exception {
        base.close();
    }
}
