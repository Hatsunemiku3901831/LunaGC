package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.server.ActivityCondGroup;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityInfoNotifyOuterClass.ActivityInfoNotify;
import emu.grasscutter.net.proto.ActivityUpdateWatcherNotifyOuterClass.ActivityUpdateWatcherNotify;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original quest conditions through the real quest system, with a disposable Mongo database. */
class AsterQuestTest {
    private AsterMidTestFixture resources;
    private final List<Runnable> restore = new ArrayList<>();
    private com.mongodb.client.MongoClient client;
    private Datastore store;
    private TestPlayer player;

    static final class TestPlayer extends Player {
        ActivityManager activity;
        GameServer server;
        final TeamManager team = new TeamManager(this);
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}

        @Override
        public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value);
            return true;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        resources = new AsterMidTestFixture();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_aster_quests_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(GameMainQuest.class, PlayerActivityData.class);
        replaceField(DatabaseManager.class, null, "gameDatastore", store);
        var mode = Grasscutter.getRunMode();
        restore.add(() -> Grasscutter.setRunModeOverride(mode));
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
        boolean scripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        boolean quests = Grasscutter.getConfig().server.game.gameOptions.questing.enabled;
        restore.add(
                () -> {
                    Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
                    Grasscutter.getConfig().server.game.gameOptions.questing.enabled = quests;
                });
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        Grasscutter.getConfig().server.game.gameOptions.questing.enabled = true;
        player = new TestPlayer();
        player.setUid(10001);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        player.server = new GameServer();
        replaceField(
                GameServer.class, player.server, "questSystem", new QuestSystem(player.server));
        var combat = GameData.getConfigGlobalCombat();
        restore.add(() -> GameData.setConfigGlobalCombat(combat));
        GameData.setConfigGlobalCombat(
                new Gson()
                        .fromJson(
                                "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[]}}",
                                emu.grasscutter.data.binout.config.ConfigGlobalCombat.class));
        player.setWorld(new World(player.server, player));
        player.setSession(
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        player.packets.add(packet);
                    }
                });
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        player.setScene(
                new Scene(
                        player.getWorld(),
                        new Gson().fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class)));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        player.getPlayerProgress().setPlayer(player);
        var priorQuests = new HashMap<>(GameData.getQuestDataMap());
        var priorMains = new HashMap<>(GameData.getMainQuestDataMap());
        var priorCache = new HashMap<>(GameData.getBeginCondQuestMap());
        var priorTalks = new HashMap<>(GameData.getQuestTalkMap());
        restore.add(
                () -> {
                    GameData.getQuestDataMap().clear();
                    GameData.getQuestDataMap().putAll(priorQuests);
                    GameData.getMainQuestDataMap().clear();
                    GameData.getMainQuestDataMap().putAll(priorMains);
                    GameData.getBeginCondQuestMap().clear();
                    GameData.getBeginCondQuestMap().putAll(priorCache);
                    GameData.getQuestTalkMap().clear();
                    GameData.getQuestTalkMap().putAll(priorTalks);
                });
        GameData.getQuestDataMap().clear();
        GameData.getMainQuestDataMap().clear();
        GameData.getBeginCondQuestMap().clear();
        var gson = new Gson();
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class);
        for (var row : rows)
            if (Set.of(41111, 41112, 41113, 41126, 41127, 41131).contains(row.getMainId())) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        for (int id : List.of(41111, 41112, 41113, 41126, 41127, 41131)) {
            var main =
                    gson.fromJson(
                            Files.readString(Path.of("resources/BinOutput/Quest", id + ".json")),
                            MainQuestData.class);
            main.onLoad();
            GameData.getMainQuestDataMap().put(id, main);
        }
        resources.load(
                "NewActivityExcelConfigData.json",
                ActivityData.class,
                GameData.getActivityDataMap(),
                id -> id == 2001);
        resources.load(
                "NewActivityCondExcelConfigData.json",
                ActivityCondExcelConfigData.class,
                GameData.getActivityCondExcelConfigDataMap(),
                id -> id >= 2001001 && id <= 2001405);
        var priorGroups = new HashMap<>(GameData.getActivityCondGroupMap());
        restore.add(
                () -> {
                    GameData.getActivityCondGroupMap().clear();
                    GameData.getActivityCondGroupMap().putAll(priorGroups);
                });
        var groups =
                gson.fromJson(
                        Files.readString(Path.of("resources/Server/ActivityCondGroups.json")),
                        ActivityCondGroup[].class);
        for (var group : groups)
            if (group.getCondGroupId() == 2001001)
                GameData.getActivityCondGroupMap().put(group.getCondGroupId(), group);
        replaceField(
                ActivityManager.class,
                null,
                "configuration",
                new ActivityManager.Configuration(Map.of(), Map.of()));
        player.activity = new ActivityManager(player);
        setDay(8);
        resources.config.setActivityHandler(new AsterActivityHandler());
        ActivityManager.installConfiguration(
                ActivityManager.prepareConfiguration(List.of(resources.config)));
        var data = resources.config.getActivityHandler().initPlayerActivityData(player);
        data.setPlayer(player);
        data.saveSync();
        player.activity.getPlayerActivityDataMap().put(2001, data);
        player.activity.refreshActivities();
        drain();
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().getAcceptProgressLists().clear();
        store.getDatabase().getCollection("quests").deleteMany(new org.bson.Document());
        player.packets.clear();
    }

    private void replaceField(Class<?> type, Object receiver, String name, Object value)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        var previous = field.get(receiver);
        restore.add(
                () -> {
                    try {
                        field.set(receiver, previous);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                });
        field.set(receiver, value);
    }

    private void setDay(int day) {
        resources.config.setBeginTime(new Date(resources.now - (day - 1L) * 86400000L));
        resources.config.setEndTime(new Date(resources.now + 30 * 86400000L));
        resources.config.onLoad();
    }

    private void finished(int id) {
        var meta = GameData.getQuestDataMap().get(id);
        var manager = player.getQuestManager();
        var main = manager.getMainQuestById(meta.getMainId());
        if (main == null) main = manager.addMainQuest(meta);
        main.getChildQuestById(id).setState(QuestState.QUEST_STATE_FINISHED);
    }

    private boolean started(int id) {
        var quest = player.getQuestManager().getQuestById(id);
        return quest != null && quest.getState() != QuestState.QUEST_STATE_UNSTARTED;
    }

    private void trigger(QuestCond type, int id) throws Exception {
        player.getQuestManager().queueEvent(type, id);
        drain();
    }

    private void drain() throws Exception {
        for (int i = 0; i < 4; i++)
            QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (resources == null) return;
        drain();
        for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
        store.getDatabase().drop();
        client.close();
        resources.close();
    }

    @Test
    void thirdChapterCannotStartBeforeDayEight() throws Exception {
        setDay(3);
        finished(4111213);
        trigger(QuestCond.QUEST_COND_STATE_EQUAL, 4111213);
        assertFalse(started(4111301));
    }

    @Test
    void thirdChapterCannotStartWithoutPreviousChapter() throws Exception {
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        assertFalse(started(4111301));
    }

    @Test
    void expiredActivityPredicateCannotBeReusedFromEarlierEvent() throws Exception {
        // Original second chapter explicitly uses AND; this isolates stale predicate reuse.
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001002);
        assertFalse(started(4111200));
        setDay(15);
        finished(4111114);
        trigger(QuestCond.QUEST_COND_STATE_EQUAL, 4111114);
        assertFalse(started(4111200));
    }

    @Test
    void liveRefreshStartsFirstChapterWithoutRelogin() throws Exception {
        setDay(1);
        player.activity.refreshActivities();
        drain();
        assertTrue(started(4111101));
        assertFalse(started(4111200));
        assertFalse(started(4111301));
    }

    @Test
    void chapterThreeStartsWhenBothCurrentPredicatesAreMet() throws Exception {
        finished(4111213);
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        assertTrue(started(4111301));
        assertEquals(
                QuestState.QUEST_STATE_UNFINISHED,
                player.getQuestManager().getQuestById(4111301).getState());
        var stored =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", 41113))
                        .first();
        assertNotNull(stored);
        assertEquals(
                QuestState.QUEST_STATE_UNFINISHED, stored.getChildQuestById(4111301).getState());
    }

    @Test
    void finishingPrerequisiteDoesNotNeedAnEarlierActivityEvent() throws Exception {
        setDay(3);
        finished(4111114);
        trigger(QuestCond.QUEST_COND_STATE_EQUAL, 4111114);
        assertTrue(started(4111200));
        assertFalse(started(4111301));
    }

    @Test
    void changedPrerequisiteCannotReuseAnEarlierPositiveResult() throws Exception {
        setDay(1);
        finished(4111114);
        trigger(QuestCond.QUEST_COND_STATE_EQUAL, 4111114);
        assertFalse(started(4111200));
        player.getQuestManager().getQuestById(4111114).setState(QuestState.QUEST_STATE_UNFINISHED);
        setDay(3);
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001002);
        assertFalse(started(4111200));
    }

    @Test
    void dailyUnlockStartsOnlyChapterWithFinishedPrerequisite() throws Exception {
        finished(4111213);
        player.activity.onDailyReset(resources.now);
        drain();
        assertTrue(started(4111301));
        assertFalse(started(4111200));
    }

    @Test
    void loginTriggerUsesAssignedCurrentManagerAndNativeQuestNotification() throws Exception {
        setDay(1);
        player.activity.triggerActivityConditions();
        drain();
        assertTrue(started(4111101));
        boolean notified = false;
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.QuestListUpdateNotify) {
                var notify = QuestListUpdateNotify.parseFrom(packet.getData());
                notified |=
                        notify.getQuestListList().stream()
                                .anyMatch(q -> q.getQuestId() == 4111101 && q.getState() == 2);
            }
        assertTrue(notified);
    }

    @Test
    void repeatedRefreshKeepsStartedQuestAndSavedProgress() throws Exception {
        finished(4111213);
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        var quest = player.getQuestManager().getQuestById(4111301);
        quest.setFinishProgress(0, 1);
        int accepted = quest.getAcceptTime();
        player.activity.refreshActivities();
        player.activity.refreshActivities();
        drain();
        assertSame(quest, player.getQuestManager().getQuestById(4111301));
        assertEquals(accepted, quest.getAcceptTime());
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false"})
    void disabledScriptsOrQuestsCannotStartActivityQuests(boolean scripts, boolean quests)
            throws Exception {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
        Grasscutter.getConfig().server.game.gameOptions.questing.enabled = quests;
        player.activity.refreshActivities();
        player.activity.onDailyReset(resources.now);
        drain();
        assertTrue(player.getQuestManager().getMainQuests().isEmpty());
    }

    @Test
    void adventureRankStillControlsIntroduction() throws Exception {
        setDay(1);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 19);
        player.activity.refreshActivities();
        drain();
        assertFalse(started(4111101));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        player.activity.refreshActivities();
        drain();
        assertTrue(started(4111101));
    }

    @Test
    void closedScheduleCannotStartQuestsOnRefresh() throws Exception {
        resources.config.setDisabled(true);
        ActivityManager.installConfiguration(
                ActivityManager.prepareConfiguration(List.of(resources.config)));
        player.activity.refreshActivities();
        drain();
        assertTrue(player.getQuestManager().getMainQuests().isEmpty());
    }

    @Test
    void currentQuestCompletionImmediatelyUnlocksThirdChapterSideQuestsAndStoryMilestone()
            throws Exception {
        // Seed prior story progress; execute the actual terminal quest completion, not a GM grant.
        finished(4111213);
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        var quest = player.getQuestManager().getQuestById(4111309);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        quest.finish();
        drain();
        for (int id : List.of(4112601, 4112701, 4113115))
            assertTrue(started(id), "side quest " + id);
        var data = player.activity.getPlayerActivityDataMap().get(2001);
        assertTrue(data.getWatcherInfoMap().get(1200130).isFinished());
        assertFalse(data.getWatcherInfoMap().get(1200131).isFinished());
        assertFalse(data.getWatcherInfoMap().get(1200132).isFinished());
        var saved =
                store.find(PlayerActivityData.class)
                        .filter(Filters.eq("uid", player.getUid()))
                        .first();
        assertTrue(saved.getWatcherInfoMap().get(1200130).isFinished());
        boolean condition = false, watcher = false;
        for (var packet : player.packets) {
            if (packet.getOpcode() == PacketOpcodes.ActivityInfoNotify)
                condition |=
                        ActivityInfoNotify.parseFrom(packet.getData())
                                .getActivityInfo()
                                .getMeetCondListList()
                                .contains(2001303);
            if (packet.getOpcode() == PacketOpcodes.ActivityUpdateWatcherNotify)
                watcher |=
                        ActivityUpdateWatcherNotify.parseFrom(packet.getData())
                                                .getWatcherInfo()
                                                .getWatcherId()
                                        == 1200130
                                && ActivityUpdateWatcherNotify.parseFrom(packet.getData())
                                                .getWatcherInfo()
                                                .getCurProgress()
                                        == 1;
        }
        assertTrue(condition);
        assertTrue(watcher);
    }

    @Test
    void unrelatedQuestChangeDoesNotPublishActivityPackets() throws Exception {
        player.activity.onQuestStateChanged(4111101);
        drain();
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void completedThirdChapterDoesNotUnlockSideQuestsAfterContentClosure() throws Exception {
        setDay(15);
        finished(4111309);
        player.getQuestManager().getQuestById(4111309).triggerStateEvents();
        drain();
        for (int id : List.of(4112601, 4112701, 4113115)) assertFalse(started(id));
    }

    @Test
    void activityReloadRestoresStoryMilestoneFromCompletedQuest() throws Exception {
        finished(4111309);
        player.activity.refreshActivities();
        drain();
        var data = player.activity.getPlayerActivityDataMap().get(2001);
        assertEquals(1, data.getWatcherInfoMap().get(1200130).getCurProgress());
        assertFalse(data.getWatcherInfoMap().get(1200131).isFinished());
        assertFalse(data.getWatcherInfoMap().get(1200132).isFinished());
        var saved =
                store.find(PlayerActivityData.class)
                        .filter(Filters.eq("uid", player.getUid()))
                        .first();
        assertEquals(1, saved.getWatcherInfoMap().get(1200130).getCurProgress());
    }

    @Test
    void freshReplayKeepsCompletedQuestAndRestoresOnlyItsStoryMilestone() throws Exception {
        finished(4111309);
        var quest = player.getQuestManager().getQuestById(4111309);
        var old = player.activity.getPlayerActivityDataMap().get(2001);
        String id = old.getId();
        old.getWatcherInfoMap().values().forEach(w -> w.setCurProgress(w.getTotalProgress()));
        old.setDetail(new AsterProgress());
        old.saveSync();
        resources.config.setScheduleId(resources.config.getScheduleId() + 1);
        ActivityManager.installConfiguration(
                ActivityManager.prepareConfiguration(List.of(resources.config)));
        player.activity.refreshActivities();
        drain();
        var data = player.activity.getPlayerActivityDataMap().get(2001);
        assertNotSame(old, data);
        assertEquals(id, data.getId());
        assertEquals(resources.config.getScheduleId(), data.getScheduleId());
        assertSame(quest, player.getQuestManager().getQuestById(4111309));
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(1, data.getWatcherInfoMap().get(1200130).getCurProgress());
        for (var entry : data.getWatcherInfoMap().entrySet())
            if (entry.getKey() != 1200130) assertEquals(0, entry.getValue().getCurProgress());
        assertFalse(AsterSchedule.progress(data).specialRewardTaken());
        assertEquals(0, AsterSchedule.progress(data).midCollectCount());
        assertEquals(0, AsterSchedule.progress(data).bigCollectCount());
        boolean published = false;
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.ActivityInfoNotify)
                published |=
                        ActivityInfoNotify.parseFrom(packet.getData())
                                .getActivityInfo()
                                .getWatcherInfoListList()
                                .stream()
                                .anyMatch(
                                        w ->
                                                w.getWatcherId() == 1200130
                                                        && w.getCurProgress() == 1);
        assertTrue(published);
    }

    @Test
    void coldQuestAndActivityLoadRestoresMissingStoryEvent() throws Exception {
        finished(4111309);
        player.getQuestManager().getMainQuestById(41113).save();
        drain();
        player.getQuestManager().getMainQuests().clear();
        var loaded = ResourceLoader.class.getDeclaredField("loadedAll");
        loaded.setAccessible(true);
        boolean previous = loaded.getBoolean(null);
        try {
            loaded.setBoolean(null, true);
            player.getQuestManager().loadFromDatabase();
        } finally {
            loaded.setBoolean(null, previous);
        }
        assertEquals(
                QuestState.QUEST_STATE_FINISHED,
                player.getQuestManager().getQuestById(4111309).getState());
        player.activity.getPlayerActivityDataMap().clear();
        player.activity.refreshActivities();
        drain();
        var data = player.activity.getPlayerActivityDataMap().get(2001);
        assertEquals(1, data.getWatcherInfoMap().get(1200130).getCurProgress());
        assertFalse(AsterSchedule.progress(data).specialRewardTaken());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedLoadSaveDoesNotPublishOptimisticStoryProgress(boolean loseAcknowledgement)
            throws Exception {
        finished(4111309);
        player.packets.clear();
        var data = player.activity.getPlayerActivityDataMap().get(2001);
        var previous = data.getWatcherInfoMap().get(1200130);
        String detail = data.getDetail();
        var fault = ActivitySaveFaults.failNextWrite(data, loseAcknowledgement);
        assertThrows(IllegalStateException.class, () -> player.activity.refreshActivities());
        assertEquals(1, fault.get());
        assertSame(previous, data.getWatcherInfoMap().get(1200130));
        assertEquals(0, previous.getCurProgress());
        assertEquals(detail, data.getDetail());
        assertTrue(player.packets.isEmpty());
        var saved =
                store.find(PlayerActivityData.class)
                        .filter(Filters.eq("uid", player.getUid()))
                        .first();
        assertEquals(
                loseAcknowledgement ? 1 : 0,
                saved.getWatcherInfoMap().get(1200130).getCurProgress());
        player.activity.refreshActivities();
        drain();
        assertEquals(1, data.getWatcherInfoMap().get(1200130).getCurProgress());
        assertEquals(
                1,
                store.find(PlayerActivityData.class)
                        .filter(Filters.eq("uid", player.getUid()))
                        .first()
                        .getWatcherInfoMap()
                        .get(1200130)
                        .getCurProgress());
        assertFalse(AsterSchedule.progress(data).specialRewardTaken());
        assertEquals(
                QuestState.QUEST_STATE_FINISHED,
                player.getQuestManager().getQuestById(4111309).getState());
    }

    private void customQuest(String combination, String predicates) {
        var gson = new Gson();
        var quest =
                gson.fromJson(
                        "{subId:9000001,mainId:90000,order:1,acceptCondComb:'"
                                + combination
                                + "',acceptCond:"
                                + predicates
                                + ",finishCond:[],failCond:[],beginExec:[],finishExec:[],failExec:[]}",
                        QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getId(), quest);
        var main =
                gson.fromJson(
                        "{id:90000,subQuests:[{subId:9000001,order:1}]}", MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(90000, main);
    }

    @ParameterizedTest
    @CsvSource({"LOGIC_NONE,false", "LOGIC_AND,false", "LOGIC_OR,true", "LOGIC_NOT,false"})
    void explicitCombinationsArePreservedAndDefaultRequiresEveryPredicate(
            String combination, boolean expected) throws Exception {
        customQuest(
                combination,
                "[{type:'QUEST_COND_STATE_EQUAL',param:[4111213,3]},{type:'QUEST_COND_ACTIVITY_COND',param:[2001003,1]}]");
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        assertEquals(expected, started(9000001));
    }

    @Test
    void cachedEventOnlyPredicateKeepsItsOriginalArguments() throws Exception {
        customQuest(
                "LOGIC_NONE",
                "[{type:'QUEST_COND_ITEM_GIVING_FINISHED',param:[60001,7]},{type:'QUEST_COND_ACTIVITY_COND',param:[2001003,1]}]");
        setDay(1);
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_ITEM_GIVING_FINISHED, 60001, 7);
        drain();
        assertFalse(started(9000001));
        setDay(8);
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        assertTrue(started(9000001));
    }

    @Test
    void missingHandlerCannotPassDefaultConjunction() throws Exception {
        customQuest(
                "LOGIC_NONE",
                "[{type:'QUEST_COND_ACTIVITY_COND',param:[2001003,1]},{type:'QUEST_COND_CITY_LEVEL_EQUAL_GREATER',param:[1,99]}]");
        trigger(QuestCond.QUEST_COND_ACTIVITY_COND, 2001003);
        assertFalse(started(9000001));
    }

    @Test
    void emptyAcceptanceStillStartsFromNoneEvent() throws Exception {
        customQuest("LOGIC_NONE", "[]");
        trigger(QuestCond.QUEST_COND_NONE, 0);
        assertTrue(started(9000001));
    }
}
