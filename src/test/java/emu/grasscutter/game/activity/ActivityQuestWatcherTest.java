package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.activity.ActivityWatcherData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.activity.watcher.FinishQuestWatcher;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ActivityUpdateWatcherNotifyOuterClass.ActivityUpdateWatcherNotify;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

class ActivityQuestWatcherTest {
    private final Gson gson = new Gson();
    private Map<Integer, QuestData> oldQuests;
    private Map<Integer, MainQuestData> oldMains;
    private Map<String, List<QuestData>> oldCache;
    private TestPlayer player;

    static class TestPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class Progress extends PlayerActivityData {
        int writes;
        boolean failWrite;

        Progress(Player player, ActivityWatcher watcher) {
            super(null, player.getUid(), 2001, 2001710, new HashMap<>(), null, player, null);
            getWatcherInfoMap().put(watcher.getWatcherId(), WatcherInfo.init(watcher));
        }

        @Override
        public void save() {
            writes++;
        }

        @Override
        public void saveSync() {
            writes++;
            if (failWrite) throw new IllegalStateException("quest watcher write failed");
        }

        int value() {
            return getWatcherInfoMap().get(1200130).getCurProgress();
        }
    }

    @BeforeEach
    void setup() {
        Grasscutter.getConfig();
        oldQuests = new HashMap<>(GameData.getQuestDataMap());
        oldMains = new HashMap<>(GameData.getMainQuestDataMap());
        oldCache = new HashMap<>();
        GameData.getBeginCondQuestMap()
                .forEach((key, value) -> oldCache.put(key, new ArrayList<>(value)));
        GameData.getBeginCondQuestMap().clear();
        player = new TestPlayer();
        player.setUid(10001);
        for (int id : List.of(4111308, 4111309)) {
            var row =
                    gson.fromJson(
                            "{subId:"
                                    + id
                                    + ",mainId:41113,acceptCond:[],finishCond:[],failCond:[],beginExec:[],finishExec:[],failExec:[]}",
                            QuestData.class);
            row.onLoad();
            GameData.getQuestDataMap().put(id, row);
        }
        var main =
                gson.fromJson(
                        "{id:41113,subQuests:[{subId:4111308},{subId:4111309}]}",
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(41113, main);
        player.getQuestManager().getMainQuests().put(41113, new GameMainQuest(player, 41113));
    }

    @AfterEach
    void cleanup() {
        GameData.getQuestDataMap().clear();
        GameData.getQuestDataMap().putAll(oldQuests);
        GameData.getMainQuestDataMap().clear();
        GameData.getMainQuestDataMap().putAll(oldMains);
        GameData.getBeginCondQuestMap().clear();
        GameData.getBeginCondQuestMap().putAll(oldCache);
    }

    private FinishQuestWatcher watcher(String quests) {
        var meta =
                gson.fromJson(
                        "{id:1200130,progress:1,triggerConfig:"
                                + "{triggerType:'TRIGGER_FINISH_QUEST_AND',paramList:['"
                                + quests
                                + "']}}",
                        ActivityWatcherData.class);
        meta.onLoad();
        var watcher = new FinishQuestWatcher();
        watcher.setWatcherId(meta.getId());
        watcher.setActivityWatcherData(meta);
        return watcher;
    }

    private void state(int id, QuestState state) {
        player.getQuestManager().getQuestById(id).setState(state);
    }

    @Test
    void questEventAloneCannotCompleteAnUnfinishedQuestCondition() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_UNFINISHED);
        watcher.trigger(data, "4111309");
        assertEquals(0, data.value());
        assertEquals(0, data.writes);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void conjunctionRequiresBothQuestsAndCannotCountRepeatedFirstEvent() {
        var watcher = watcher("4111308,4111309");
        var data = new Progress(player, watcher);
        state(4111308, QuestState.QUEST_STATE_FINISHED);
        watcher.trigger(data, "4111308");
        watcher.trigger(data, "4111308");
        assertEquals(0, data.value());
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        watcher.trigger(data, "4111309");
        assertEquals(1, data.value());
        assertEquals(1, data.writes);
    }

    @Test
    void completedSingleQuestAdvancesExactlyOnce() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        watcher.trigger(data, "4111309");
        watcher.trigger(data, "4111309");
        assertEquals(1, data.value());
        assertEquals(1, data.writes);
        assertEquals(1, player.packets.size());
    }

    @Test
    void completedQuestsAreRebuiltWithoutConsumingAnEventOrSavingInsideTheLoadHook()
            throws Exception {
        var watcher = watcher("4111308, 4111309,4111309");
        var data = new Progress(player, watcher);
        state(4111308, QuestState.QUEST_STATE_FINISHED);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        assertTrue(watcher.refreshProgress(data));
        assertFalse(watcher.refreshProgress(data));
        assertEquals(1, data.value());
        assertEquals(0, data.writes);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void incompleteOrMissingPrerequisiteIsNotRepaired() {
        var watcher = watcher("4111308,4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        assertFalse(watcher.refreshProgress(data));
        player.getQuestManager().getMainQuestById(41113).getChildQuests().remove(4111308);
        assertFalse(watcher.refreshProgress(data));
        assertEquals(0, data.value());
    }

    @Test
    void unrelatedOrMalformedEventDoesNotPublishACompletedCondition() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        watcher.trigger(data, "4111308");
        watcher.trigger(data, "NaN");
        watcher.trigger(data, (String) null);
        watcher.trigger(data);
        assertEquals(0, data.value());
        assertEquals(0, data.writes);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void successfulWritePrecedesTheNativeWatcherNotification() throws Exception {
        var watcher = watcher("4111309");
        var data =
                new Progress(player, watcher) {
                    @Override
                    public void saveSync() {
                        assertTrue(player.packets.isEmpty());
                        super.saveSync();
                    }
                };
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        watcher.trigger(data, "4111309");
        var packet = ActivityUpdateWatcherNotify.parseFrom(player.packets.get(0).getData());
        assertEquals(2001, packet.getActivityId());
        assertEquals(1200130, packet.getWatcherInfo().getWatcherId());
        assertEquals(1, packet.getWatcherInfo().getCurProgress());
        assertFalse(packet.getWatcherInfo().getIsTakenReward());
    }

    @Test
    void failedWriteRestoresThePriorObjectAndCanBeRetriedWithoutDuplicateProgress() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        var previous = data.getWatcherInfoMap().get(1200130);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        data.failWrite = true;
        assertThrows(IllegalStateException.class, () -> watcher.trigger(data, "4111309"));
        assertSame(previous, data.getWatcherInfoMap().get(1200130));
        assertEquals(0, data.value());
        assertTrue(player.packets.isEmpty());
        data.failWrite = false;
        watcher.trigger(data, "4111309");
        watcher.trigger(data, "4111309");
        assertEquals(1, data.value());
        assertEquals(2, data.writes);
        assertEquals(1, player.packets.size());
    }

    @Test
    void completedProgressAndRewardFlagsRemainUnchangedAfterAQuestStateRollback() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        var previous = data.getWatcherInfoMap().get(1200130);
        previous.setCurProgress(1);
        previous.setTakenReward(true);
        assertFalse(watcher.refreshProgress(data));
        watcher.trigger(data, "4111309");
        assertSame(previous, data.getWatcherInfoMap().get(1200130));
        assertTrue(previous.isTakenReward());
        assertEquals(0, data.writes);
    }

    @Test
    void repairPreservesExistingRewardFlagAndLeavesOtherConditionsAlone() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        data.getWatcherInfoMap().get(1200130).setTakenReward(true);
        var other =
                PlayerActivityData.WatcherInfo.of()
                        .watcherId(1200131)
                        .totalProgress(14)
                        .curProgress(6)
                        .build();
        data.getWatcherInfoMap().put(1200131, other);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        assertTrue(watcher.refreshProgress(data));
        assertTrue(data.getWatcherInfoMap().get(1200130).isTakenReward());
        assertSame(other, data.getWatcherInfoMap().get(1200131));
        assertEquals(6, other.getCurProgress());
    }

    @Test
    void absentOrForeignPlayerCannotRebuildProgress() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        data.setUid(10002);
        assertFalse(watcher.refreshProgress(data));
        data.setUid(10001);
        data.setPlayer(null);
        assertFalse(watcher.refreshProgress(data));
        assertFalse(watcher.refreshProgress(null));
        assertEquals(0, data.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NaN", "0", "-1", "4111309,", "4111309,NaN", "2147483648"})
    void invalidQuestListsCannotProduceACondition(String params) {
        var watcher = watcher(params);
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        assertFalse(watcher.refreshProgress(data));
        watcher.trigger(data, "4111309");
        assertEquals(0, data.value());
        assertEquals(0, data.writes);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "id:1200201,progress:1",
                "id:1200130,progress:2",
                "id:1200130,progress:0",
                "id:1200130,progress:1,isDisuse:true"
            })
    void unsupportedMetadataDoesNotRewriteTheSavedCondition(String fields) {
        var meta =
                gson.fromJson(
                        "{"
                                + fields
                                + ",triggerConfig:"
                                + "{triggerType:'TRIGGER_FINISH_QUEST_AND',paramList:['4111309']}}",
                        ActivityWatcherData.class);
        meta.onLoad();
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        var previous = data.getWatcherInfoMap().get(1200130);
        watcher.setActivityWatcherData(meta);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        assertFalse(watcher.refreshProgress(data));
        assertSame(previous, data.getWatcherInfoMap().get(1200130));
    }

    @Test
    void corruptOrAbsentSavedWatcherIsNotInventedDuringRefresh() {
        var watcher = watcher("4111309");
        var data = new Progress(player, watcher);
        state(4111309, QuestState.QUEST_STATE_FINISHED);
        var info = data.getWatcherInfoMap().get(1200130);
        info.setTotalProgress(2);
        assertFalse(watcher.refreshProgress(data));
        info.setTotalProgress(1);
        info.setCurProgress(-1);
        assertFalse(watcher.refreshProgress(data));
        data.getWatcherInfoMap().clear();
        assertFalse(watcher.refreshProgress(data));
        assertTrue(data.getWatcherInfoMap().isEmpty());
    }

    @Test
    void everyActiveResourceQuestConjunctionAcceptsAllItsFinishedPrerequisites() throws Exception {
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of(
                                        "resources/ExcelBinOutput/NewActivityWatcherConfigData.json")),
                        ActivityWatcherData[].class);
        int checked = 0;
        for (var meta : rows) {
            if (meta.getTriggerConfig() == null
                    || !"TRIGGER_FINISH_QUEST_AND".equals(meta.getTriggerConfig().getTriggerType())
                    || meta.isDisuse()) continue;
            meta.onLoad();
            for (String entry : meta.getTriggerConfig().getParamList())
                for (String id : entry.split(",")) installFinishedQuest(Integer.parseInt(id));
            var watcher = new FinishQuestWatcher();
            watcher.setWatcherId(meta.getId());
            watcher.setActivityWatcherData(meta);
            var data = new Progress(player, watcher);
            assertTrue(watcher.refreshProgress(data), "original watcher " + meta.getId());
            assertFalse(watcher.refreshProgress(data));
            assertEquals(1, data.getWatcherInfoMap().get(meta.getId()).getCurProgress());
            assertEquals(0, data.writes);
            checked++;
        }
        assertEquals(81, checked);
    }

    private void installFinishedQuest(int id) {
        int parent = id / 100;
        var row =
                gson.fromJson(
                        "{subId:"
                                + id
                                + ",mainId:"
                                + parent
                                + ",acceptCond:[],finishCond:[],failCond:[],beginExec:[],finishExec:[],failExec:[]}",
                        QuestData.class);
        row.onLoad();
        GameData.getQuestDataMap().put(id, row);
        var mainData =
                gson.fromJson(
                        "{id:" + parent + ",subQuests:[{subId:" + id + "}]}", MainQuestData.class);
        mainData.onLoad();
        GameData.getMainQuestDataMap().put(parent, mainData);
        var main = player.getQuestManager().getMainQuestById(parent);
        if (main == null) {
            main = new GameMainQuest(player, parent);
            player.getQuestManager().getMainQuests().put(parent, main);
        }
        var quest = new GameQuest(main, row);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        main.getChildQuests().put(id, quest);
    }
}
