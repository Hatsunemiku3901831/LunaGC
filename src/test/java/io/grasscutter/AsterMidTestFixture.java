package io.grasscutter;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.nio.file.*;
import java.util.*;
import java.util.function.IntPredicate;

/** The selected local resources, with every global map restored after each test. */
final class AsterMidTestFixture implements AutoCloseable {
    final long now = System.currentTimeMillis();
    final ActivityConfigItem config = new ActivityConfigItem();
    final PlayerActivityData data;
    private final List<Runnable> restore = new ArrayList<>();

    AsterMidTestFixture() throws Exception {
        Grasscutter.getConfig();
        load(
                "AsterLittleExcelConfigData.json",
                AsterLittleData.class,
                GameData.getAsterLittleDataMap(),
                i -> true);
        load(
                "AsterMissionExcelConfigData.json",
                AsterMissionData.class,
                GameData.getAsterMissionDataMap(),
                i -> true);
        load(
                "AsterStageExcelConfigData.json",
                AsterStageData.class,
                GameData.getAsterStageDataMap(),
                i -> true);
        load(
                "AsterActivityPerviewExcelConfigData.json",
                AsterPreviewData.class,
                GameData.getAsterPreviewDataMap(),
                i -> true);
        load(
                "AsterMidExcelConfigData.json",
                AsterMidData.class,
                GameData.getAsterMidDataMap(),
                i -> true);
        load(
                "AsterMidGroupsExcelConfigData.json",
                AsterMidGroupsData.class,
                GameData.getAsterMidGroupsDataMap(),
                i -> true);
        load(
                "AsterMidDifficultyExcelConfigData.json",
                AsterMidDifficultyData.class,
                GameData.getAsterMidDifficultyDataMap(),
                i -> true);
        load(
                "NewActivityWatcherConfigData.json",
                ActivityWatcherData.class,
                GameData.getActivityWatcherDataMap(),
                i -> i >= 1200101 && i <= 1200132);
        load(
                "RewardExcelConfigData.json",
                RewardData.class,
                GameData.getRewardDataMap(),
                i -> i >= 3000001 && i <= 3000015);
        load(
                "MaterialExcelConfigData.json",
                ItemData.class,
                GameData.getItemDataMap(),
                i -> Set.of(102, 105, 109).contains(i));
        load(
                "GadgetExcelConfigData.json",
                GadgetData.class,
                GameData.getGadgetDataMap(),
                i -> Set.of(70950015, 70950019, 70950020, 70950021).contains(i));
        load(
                "../Server/DropTableExcelConfigData.json",
                DropTableData.class,
                GameData.getDropTableDataMap(),
                i -> i >= 210010000 && i <= 210010080);
        config.setActivityId(2001);
        config.setActivityType(1100);
        config.setScheduleId(2001710);
        config.setBeginTime(new Date(now - 3 * 86400000L));
        config.setEndTime(new Date(now + 26 * 86400000L));
        config.onLoad();
        var watchers = new HashMap<Integer, PlayerActivityData.WatcherInfo>();
        for (int id = 1200110; id <= 1200115; id++)
            watchers.put(
                    id,
                    PlayerActivityData.WatcherInfo.of()
                            .watcherId(id)
                            .totalProgress(
                                    GameData.getActivityWatcherDataMap().get(id).getProgress())
                            .build());
        data =
                PlayerActivityData.of()
                        .uid(10001)
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .watcherInfoMap(watchers)
                        .detail("{}")
                        .build();
        var progress = AsterSchedule.progress(data);
        progress.startMidBatch(1);
        data.setDetail(progress);
    }

    <T extends GameResource> void load(
            String name, Class<T> type, Int2ObjectMap<T> target, IntPredicate include)
            throws Exception {
        var before = new HashMap<>(target);
        restore.add(
                () -> {
                    target.clear();
                    target.putAll(before);
                });
        var rows =
                new Gson()
                        .fromJson(
                                Files.readString(Path.of("resources/ExcelBinOutput", name)),
                                java.lang.reflect.Array.newInstance(type, 0).getClass());
        for (Object row : (Object[]) rows) {
            T resource = type.cast(row);
            if (include.test(resource.getId())) {
                resource.onLoad();
                target.put(resource.getId(), resource);
            }
        }
    }

    int offer(int index, int difficulty) {
        var progress = AsterSchedule.progress(data);
        int camp =
                GameData.getAsterMidDataMap().get(progress.midRefreshId()).getCampVec().get(index);
        if (!progress.offerMidReward(camp, difficulty))
            throw new IllegalStateException("Test camp already pending");
        data.setDetail(progress);
        return camp;
    }

    @Override
    public void close() {
        for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
    }
}
