package io.grasscutter;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.MpPlayGroupData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;

import java.util.*;

/** Selected real play-2 resources and the original day-8 chapter gate. */
final class AsterBigTestFixture implements AutoCloseable {
    final AsterMidTestFixture original = new AsterMidTestFixture();
    final long now = original.now;
    final ActivityConfigItem config = original.config;
    final PlayerActivityData host = original.data;
    final PlayerActivityData guest = data(10002);

    AsterBigTestFixture() throws Exception {
        original.load(
                "MpPlayGroupExcelConfigData.json",
                MpPlayGroupData.class,
                GameData.getMpPlayGroupDataMap(),
                id -> id == 2);
        original.load(
                "MaterialExcelConfigData.json",
                ItemData.class,
                GameData.getItemDataMap(),
                id -> id == 110);
        original.load(
                "GadgetExcelConfigData.json",
                GadgetData.class,
                GameData.getGadgetDataMap(),
                id -> id == AsterBigRewards.GADGET);
        original.load(
                "../Server/DropTableExcelConfigData.json",
                DropTableData.class,
                GameData.getDropTableDataMap(),
                id -> id >= 210020000 && id <= 210020080);
        config.setBeginTime(new Date(now - 8 * 86400000L));
        config.onLoad();
    }

    PlayerActivityData data(int uid) {
        return PlayerActivityData.of()
                .uid(uid)
                .activityId(2001)
                .scheduleId(config.getScheduleId())
                .watcherInfoMap(new HashMap<>())
                .detail("{}")
                .build();
    }

    String offer() {
        var progress = AsterSchedule.progress(host);
        progress.openBigReward(
                Map.of(
                        host.getUid(),
                        AsterBigRewards.reward(0),
                        guest.getUid(),
                        AsterBigRewards.reward(8)));
        host.setDetail(progress);
        return progress.bigReward().token();
    }

    @Override
    public void close() {
        original.close();
    }
}
