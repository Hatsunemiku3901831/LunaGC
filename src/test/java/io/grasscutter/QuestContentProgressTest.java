package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QuestProgressUpdateNotifyOuterClass.QuestProgressUpdateNotify;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Original objectives through real content handlers, with isolated quest state and Mongo. */
class QuestContentProgressTest {
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private com.mongodb.client.MongoClient client;
    private Datastore store;
    private TestPlayer player;

    static final class TestPlayer extends Player {
        GameServer server;
        ActivityManager activity;
        final TeamManager team = new TeamManager(this);
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public ActivityManager getActivityManager() {
            return activity;
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
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_quest_progress_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(GameMainQuest.class);
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
        var oldCombat = GameData.getConfigGlobalCombat();
        restore.add(() -> GameData.setConfigGlobalCombat(oldCombat));
        GameData.setConfigGlobalCombat(
                gson.fromJson(
                        "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[]}}",
                        ConfigGlobalCombat.class));
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
                        gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class)));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        player.getPlayerProgress().setPlayer(player);
        replaceField(
                ActivityManager.class,
                null,
                "configuration",
                new ActivityManager.Configuration(Map.of(), Map.of()));
        player.activity = new ActivityManager(player);
        preserve(GameData.getQuestDataMap());
        preserve(GameData.getMainQuestDataMap());
        preserve(GameData.getQuestTalkMap());
        var cache = new HashMap<String, List<QuestData>>();
        GameData.getBeginCondQuestMap()
                .forEach((key, value) -> cache.put(key, new ArrayList<>(value)));
        restore.add(
                () -> {
                    GameData.getBeginCondQuestMap().clear();
                    GameData.getBeginCondQuestMap().putAll(cache);
                });
        GameData.getQuestDataMap().clear();
        GameData.getMainQuestDataMap().clear();
        GameData.getQuestTalkMap().clear();
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class);
        var parents = Set.of(11005, 11103, 19090, 309);
        for (var row : rows)
            if (parents.contains(row.getMainId())) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        for (int id : parents) {
            var main =
                    gson.fromJson(
                            Files.readString(Path.of("resources/BinOutput/Quest", id + ".json")),
                            MainQuestData.class);
            main.onLoad();
            GameData.getMainQuestDataMap().put(id, main);
        }
        // These tests seed an active objective; automatic acceptance of later story steps is
        // separate.
        GameData.getBeginCondQuestMap().clear();
        player.packets.clear();
    }

    private <K, V> void preserve(Map<K, V> map) {
        var previous = new HashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(previous);
                });
    }

    private void replaceField(Class<?> type, Object receiver, String name, Object value)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        var old = field.get(receiver);
        restore.add(
                () -> {
                    try {
                        field.set(receiver, old);
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                });
        field.set(receiver, value);
    }

    private GameQuest active(int id) throws Exception {
        var meta = GameData.getQuestDataMap().get(id);
        var main = player.getQuestManager().addMainQuest(meta);
        var quest = main.getChildQuestById(id);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        main.save();
        drain();
        player.packets.clear();
        return quest;
    }

    private void event(QuestContent type, int id) throws Exception {
        player.getQuestManager().queueEvent(type, id);
        drain();
    }

    private void event(QuestContent type, String text, int... params) throws Exception {
        player.getQuestManager().queueEvent(type, text, params);
        drain();
    }

    // Predicate-specific cases retain the real quest lifecycle but use controlled conditions.
    private GameQuest controlled(String finish, String fail, String logic) throws Exception {
        var json = gson.toJsonTree(GameData.getQuestDataMap().get(1100509)).getAsJsonObject();
        json.add("finishCond", gson.fromJson(finish, com.google.gson.JsonElement.class));
        json.add("failCond", gson.fromJson(fail, com.google.gson.JsonElement.class));
        json.addProperty("finishCondComb", logic);
        json.addProperty("failCondComb", "LOGIC_AND");
        var meta = gson.fromJson(json, QuestData.class);
        meta.onLoad();
        GameData.getQuestDataMap().put(meta.getId(), meta);
        GameData.getBeginCondQuestMap().clear();
        return active(meta.getId());
    }

    private List<QuestProgressUpdateNotify> notifications() throws Exception {
        var result = new ArrayList<QuestProgressUpdateNotify>();
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.QuestProgressUpdateNotify)
                result.add(QuestProgressUpdateNotify.parseFrom(packet.getData()));
        return result;
    }

    private void drain() throws Exception {
        for (int n = 0; n < 4; n++)
            QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
    }

    private GameQuest stored(GameQuest quest) {
        var main =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(main);
        return main.getChildQuestById(quest.getSubQuestId());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client == null) return;
        drain();
        for (int n = restore.size() - 1; n >= 0; n--) restore.get(n).run();
        store.getDatabase().drop();
        client.close();
    }

    @Test
    void unrelatedInteractionCannotClearTheFirstOriginalObjective() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710099);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void threeOriginalInteractionsAccumulateAndCompleteTheRealQuest() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710033);
        assertArrayEquals(new int[] {1, 1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void originalSevenInteractionGoalCannotFinishAfterOneInteraction() throws Exception {
        var quest = active(1110302);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
    }

    @Test
    void originalRepeatedGoalKeepsIndependentCountersAndRequiresAllThree() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 3}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void partialInteractionProgressIsPersistedBeforeCompletion() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {1, 0, 0}, stored(quest).getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, stored(quest).getState());
    }

    @Test
    void originalTempleQuestStillUsesItsCompletedDungeonHistory() throws Exception {
        var quest = active(30901);
        player.getPlayerProgress().markDungeonAsComplete(1001);
        drain();
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        player.getPlayerProgress().markDungeonAsComplete(1);
        drain();
        assertArrayEquals(new int[] {1, 1, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        player.getPlayerProgress().markDungeonAsComplete(1003);
        drain();
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void originalSevenInteractionGoalFinishesExactlyOnTheSeventhEvent() throws Exception {
        var quest = active(1110302);
        for (int n = 1; n <= 7; n++) {
            event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
            assertArrayEquals(new int[] {n}, quest.getFinishProgressList());
            assertEquals(
                    n < 7 ? QuestState.QUEST_STATE_UNFINISHED : QuestState.QUEST_STATE_FINISHED,
                    quest.getState());
        }
        int packets = player.packets.size();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
        assertArrayEquals(new int[] {7}, quest.getFinishProgressList());
        assertEquals(packets, player.packets.size());
    }

    @Test
    void aSatisfiedSingleGoalDoesNotAccumulateOrNotifyAgain() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(1, notifications().size());
        player.packets.clear();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710099);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void notificationContainsTheEntireNewProgressSnapshot() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        player.packets.clear();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var updates = notifications();
        assertEquals(1, updates.size());
        assertEquals(quest.getSubQuestId(), updates.get(0).getQuestId());
        assertEquals(List.of(1, 2), updates.get(0).getFinishProgressListList());
        assertEquals(List.of(), updates.get(0).getFailProgressListList());
    }

    @Test
    void coldMongoQuestCanResumeItsIndependentCounters() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var main =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(main);
        main.setOwner(player);
        for (var child : main.getChildQuests().values()) {
            child.setMainQuest(main);
            child.setConfig(GameData.getQuestDataMap().get(child.getSubQuestId()));
        }
        player.getQuestManager().getMainQuests().put(main.getParentQuestId(), main);
        quest = main.getChildQuestById(1909002);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void liveQuestVariableMustBecomeFalseAgainBeforeTheOtherGoalFinishes() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_QUEST_VAR_EQUAL',param:[0,1]},"
                                + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]}]",
                        "[]",
                        "LOGIC_AND");
        quest.getMainQuest().setQuestVar(0, 1);
        drain();
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        quest.getMainQuest().setQuestVar(0, 0);
        drain();
        assertArrayEquals(new int[] {0, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        quest.getMainQuest().setQuestVar(0, 1);
        drain();
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void failureGoalsHaveTheirOwnCountsAndPersistTheFailedState() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710099]}]",
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]},"
                            + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710032],count:2}]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1}, quest.getFailProgressList());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1, 1}, stored(quest).getFailProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FAILED, stored(quest).getState());
        assertArrayEquals(new int[] {1, 2}, stored(quest).getFailProgressList());
    }

    @Test
    void luaProgressIsASnapshotAndRepeatedNotificationsDoNotAddCounts() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'counted_lua',count:3}]",
                        "[]",
                        "LOGIC_AND");
        player.getPlayerProgress().addToCurrentProgress("counted_lua", 2);
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        player.getPlayerProgress().addToCurrentProgress("counted_lua", 1);
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertArrayEquals(new int[] {3}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void execProgressResetMustUpdateTheActiveCounter() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_ADD_QUEST_PROGRESS',param:[110051],count:3}]",
                        "[]",
                        "LOGIC_AND");
        player.getPlayerProgress().addToCurrentProgress("110051", 2);
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        player.getPlayerProgress().resetCurrentProgress("110051");
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        player.getPlayerProgress().addToCurrentProgress("110051", 3);
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void adventureRankUsesCurrentLevelRatherThanNumberOfLevelEvents() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_PLAYER_LEVEL_UP',param:[0,0],count:25}]",
                        "[]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 20);
        assertArrayEquals(new int[] {20}, quest.getFinishProgressList());
        for (int n = 0; n < 5; n++) event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 20);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {20}, quest.getFinishProgressList());
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 25);
        event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 25);
        assertArrayEquals(new int[] {25}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void itemQuantityUsesInventoryAndCanDecreaseBeforeTheOtherGoalFinishes() throws Exception {
        var counts = new HashMap<Integer, Integer>();
        replaceField(
                Player.class,
                player,
                "inventory",
                new Inventory(player) {
                    @Override
                    public int getItemCountById(int id) {
                        return counts.getOrDefault(id, 0);
                    }
                });
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_OBTAIN_ITEM',param:[100001],count:3},"
                                + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]}]",
                        "[]",
                        "LOGIC_AND");
        counts.put(100001, 3);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {3, 0}, quest.getFinishProgressList());
        counts.put(100001, 1);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        counts.put(100001, 4);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {3, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void orCombinationMustWaitUntilOneCounterReachesItsTarget() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031],count:2},"
                            + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710032],count:3}]",
                        "[]",
                        "LOGIC_OR");
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {2, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void oldTalkHistoryCannotCountAnUnrelatedNewTalkTowardARepeatedGoal() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1100599],count:2}]",
                        "[]",
                        "LOGIC_AND");
        GameData.getQuestTalkMap().put(1100599, 11005);
        quest.getMainQuest()
                .getTalks()
                .put(1100599, gson.fromJson("{id:1100599}", MainQuestData.TalkData.class));
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100599);
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100500);
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100599);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void alreadyActiveQuestDoesNotRequireAnAcceptConditionForCompletion() throws Exception {
        var quest = active(1100509);
        replaceField(QuestData.class, quest.getQuestData(), "acceptCond", null);
        for (int id : new int[] {70710031, 70710032, 70710033})
            event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, id);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void unmatchedUnsupportedContentCannotEraseOrInventProgress() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_ACTIVITY_TRIGGER_UPDATE',param:[1],count:3}]",
                        "[]",
                        "LOGIC_AND");
        quest.setFinishProgress(0, 2);
        event(QuestContent.QUEST_CONTENT_ACTIVITY_TRIGGER_UPDATE, 1);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void unknownCounterIsZeroAndCannotCompleteAnUnmatchedLuaGoal() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'missing_count',count:3}]",
                        "[]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "other_count", 0);
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void completedGroupGoalSurvivesUnloadingBeforeTheNextGroupIsCleared() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_CLEAR_GROUP_MONSTER',param:[133003001]},"
                                + "{type:'QUEST_CONTENT_CLEAR_GROUP_MONSTER',param:[133003002]}]",
                        "[]",
                        "LOGIC_AND");
        var field = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var groups =
                (Map<Integer, SceneGroupInstance>) field.get(player.getScene().getScriptManager());
        var first = SceneGroup.of(133003001);
        first.monsters = Map.of();
        groups.put(first.id, new SceneGroupInstance(first, player));
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, first.id);
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        groups.remove(first.id);
        var second = SceneGroup.of(133003002);
        second.monsters = Map.of();
        groups.put(second.id, new SceneGroupInstance(second, player));
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, second.id);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }
}
