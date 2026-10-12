package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerInvestigationAllInfoNotifyOuterClass.PlayerInvestigationAllInfoNotify;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;

/** Real notification decoding and player progress, with controlled inventory and definitions. */
class InvestigationProgressTest {
    @dev.morphia.annotations.Entity(value = "handbook_progress_test", useDiscriminator = false)
    public static class StoredProgress {
        @dev.morphia.annotations.Id public String id;
        public emu.grasscutter.game.player.PlayerProgress progress;
    }

    private AbilityTestFixture environment;
    private BookPlayer player;
    private final Gson gson =
            new com.google.gson.GsonBuilder()
                    .addSerializationExclusionStrategy(new PersistenceFields())
                    .addDeserializationExclusionStrategy(new PersistenceFields())
                    .create();

    private static class PersistenceFields implements com.google.gson.ExclusionStrategy {
        @Override
        public boolean shouldSkipField(com.google.gson.FieldAttributes field) {
            return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return false;
        }
    }

    private Int2ObjectMap<Object> chapters, targets;
    private Map<Integer, Object> oldChapters, oldTargets;

    static class BookInventory extends Inventory {
        final List<GameItem> items = new ArrayList<>();

        BookInventory(Player owner) {
            super(owner);
        }

        @Override
        public Iterator<GameItem> iterator() {
            return items.iterator();
        }
    }

    static class BookPlayer extends AbilityTestFixture.TestPlayer {
        final BookInventory inventory = new BookInventory(this);
        final emu.grasscutter.game.battlepass.BattlePassManager battlePass =
                new emu.grasscutter.game.battlepass.BattlePassManager(this) {
                    @Override
                    public boolean refreshMissions() {
                        return false;
                    }

                    @Override
                    public void save() {}
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public emu.grasscutter.game.battlepass.BattlePassManager getBattlePassManager() {
            return battlePass;
        }
    }

    private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... values)
            throws Exception {
        try {
            return owner.getMethod(name, types).invoke(null, values);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new BookPlayer();
        player.getProperties().put(PlayerProperty.PROP_PLAYER_LEVEL.getId(), 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        player.getOpenStates().put(2801, 1);
        chapters =
                (Int2ObjectMap<Object>)
                        invoke(GameData.class, "getInvestigationDataMap", new Class<?>[0]);
        targets =
                (Int2ObjectMap<Object>)
                        invoke(GameData.class, "getInvestigationTargetDataMap", new Class<?>[0]);
        oldChapters = new HashMap<>(chapters);
        oldTargets = new HashMap<>(targets);
        chapters.clear();
        targets.clear();
        chapter(10001, 1, "OPEN_ADVENTURE_MANUAL_CITY_MENGDE");
    }

    @AfterEach
    void close() {
        if (oldChapters != null) {
            chapters.clear();
            chapters.putAll(oldChapters);
        }
        if (oldTargets != null) {
            targets.clear();
            targets.putAll(oldTargets);
        }
        if (environment != null) environment.close();
    }

    private void chapter(int id, int level, String gate) throws Exception {
        var type = Class.forName("emu.grasscutter.data.excels.InvestigationData");
        chapters.put(
                id,
                gson.fromJson(
                        "{id:"
                                + id
                                + ",investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:"
                                + level
                                + ",unlockOpenStateType:'"
                                + gate
                                + "',rewardId:450001}",
                        type));
    }

    private void target(int id, String trigger, String params, int total) throws Exception {
        var type = Class.forName("emu.grasscutter.data.excels.InvestigationTargetData");
        targets.put(
                id,
                gson.fromJson(
                        "{id:"
                                + id
                                + ",investigationId:10001,questId:6000001,progress:"
                                + total
                                + ",triggerConfig:{triggerType:'"
                                + trigger
                                + "',paramList:"
                                + params
                                + "}}",
                        type));
    }

    private void call(String method, Class<?>[] types, Object... values) throws Exception {
        invoke(
                Class.forName("emu.grasscutter.game.player.InvestigationHandbook"),
                method,
                types,
                values);
    }

    private void login() throws Exception {
        call("onPlayerLogin", new Class<?>[] {Player.class}, player);
    }

    private void refresh() throws Exception {
        call("refreshAndNotify", new Class<?>[] {Player.class}, player);
    }

    private void trigger(String type, int param, int amount) throws Exception {
        call(
                "trigger",
                new Class<?>[] {Player.class, String.class, int.class, int.class},
                player,
                type,
                param,
                amount);
    }

    private PlayerInvestigationAllInfoNotify snapshot() throws Exception {
        login();
        return PlayerInvestigationAllInfoNotify.parseFrom(
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == PacketOpcodes.PlayerInvestigationAllInfoNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    private int progress(int id) throws Exception {
        return snapshot().getInvestigationTargetListList().stream()
                .filter(t -> t.getQuestId() == id)
                .findFirst()
                .orElseThrow()
                .getProgress();
    }

    @Test
    void loginPublishesResourceTargetIdAndChapterCounts() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "['','','']", 3);
        var data = snapshot();
        assertEquals(10001, data.getInvestigationList(0).getId());
        assertEquals(1, data.getInvestigationList(0).getTotalProgress());
        assertEquals(60001, data.getInvestigationTargetList(0).getQuestId());
        assertEquals(3, data.getInvestigationTargetList(0).getTotalProgress());
        assertEquals(1, data.getInvestigationTargetList(0).getStateValue());
    }

    @Test
    void closedManualDoesNotUnlockItselfOrPublishTargets() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 1);
        player.getOpenStates().put(1100, 0);
        assertEquals(0, snapshot().getInvestigationTargetListCount());
        assertEquals(0, player.getOpenStates().get(1100));
    }

    @Test
    void lockedChapterDoesNotAccumulateAnEvent() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 1);
        player.getOpenStates().put(1101, 0);
        trigger("TRIGGER_DO_FORGE", 0, 1);
        player.getOpenStates().put(1101, 1);
        assertEquals(0, progress(60001));
    }

    @Test
    void rankRequirementIsEnforcedBeforeEvents() throws Exception {
        chapter(10001, 46, "OPEN_STATE_NONE");
        target(60001, "TRIGGER_DO_FORGE", "[]", 1);
        assertEquals(0, snapshot().getInvestigationTargetListCount());
    }

    @Test
    void namedUnknownOpenStateRemainsBlocking() throws Exception {
        chapter(10001, 1, "OPEN_UNKNOWN_NEW_MANUAL_GATE");
        assertEquals(0, snapshot().getInvestigationListCount());
    }

    @Test
    void eventProgressClampsAndCompletesTheChapter() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 3);
        trigger("TRIGGER_DO_FORGE", 0, Integer.MAX_VALUE);
        trigger("TRIGGER_DO_FORGE", 0, Integer.MAX_VALUE);
        var data = snapshot();
        assertEquals(3, progress(60001));
        assertEquals(2, data.getInvestigationTargetList(0).getStateValue());
        assertEquals(2, data.getInvestigationList(0).getStateValue());
        assertTrue(
                player.packets.stream()
                        .anyMatch(
                                p ->
                                        p.getOpcode()
                                                == PacketOpcodes.PlayerInvestigationTargetNotify));
    }

    @Test
    void zeroAndNegativeEventsDoNotChangeProgress() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 5);
        trigger("TRIGGER_DO_FORGE", 0, 0);
        trigger("TRIGGER_DO_FORGE", 0, -1);
        assertEquals(0, progress(60001));
    }

    @Test
    void monsterFilterAcceptsSemicolonAndCommaButNotZeroOrAnotherMonster() throws Exception {
        target(60001, "TRIGGER_MONSTER_DIE", "['22010201;24010101,24010201','','']", 3);
        trigger("TRIGGER_MONSTER_DIE", 0, 1);
        trigger("TRIGGER_MONSTER_DIE", 29010104, 1);
        assertEquals(0, progress(60001));
        trigger("TRIGGER_MONSTER_DIE", 22010201, 1);
        trigger("TRIGGER_MONSTER_DIE", 24010101, 1);
        trigger("TRIGGER_MONSTER_DIE", 24010201, 1);
        assertEquals(3, progress(60001));
    }

    @Test
    void malformedFilterDoesNotBecomeAWildcard() throws Exception {
        target(60001, "TRIGGER_MONSTER_DIE", "['22010201;bad','','']", 1);
        trigger("TRIGGER_MONSTER_DIE", 22010201, 1);
        assertEquals(0, progress(60001));
    }

    @Test
    void unknownTriggerCannotCompleteATarget() throws Exception {
        target(60001, "TRIGGER_UNKNOWN_FUTURE_EVENT", "[]", 1);
        trigger("TRIGGER_UNKNOWN_FUTURE_EVENT", 0, 1);
        assertEquals(0, progress(60001));
    }

    @Test
    void unlockedAreaCatchesUpOnLoginAndDoesNotRegress() throws Exception {
        target(60001, "TRIGGER_UNLOCK_AREA", "['2','','']", 1);
        player.getUnlockedSceneAreas(3).add(2);
        assertEquals(1, progress(60001));
        player.getUnlockedSceneAreas(3).clear();
        refresh();
        assertEquals(1, progress(60001));
    }

    @Test
    void permanentPointCountUsesEachScenesDistinctDiscoveries() throws Exception {
        target(60001, "TRIGGER_UNLOCK_TRANS_POINT", "[]", 4);
        player.getUnlockedScenePoints(3).addAll(Set.of(1, 2));
        player.getUnlockedScenePoints(4).add(1);
        assertEquals(3, progress(60001));
    }

    @Test
    void completingADungeonDoesNotPretendItsFirstRewardWasClaimed() throws Exception {
        target(62004, "TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD", "['4022,4023','','']", 1);
        player.getPlayerProgress().getCompletedDungeons().add(4022);
        assertEquals(0, progress(62004));
    }

    @Test
    void aRecordedRewardOnlyCompletesItsMatchingFirstPassTarget() throws Exception {
        target(62004, "TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD", "['4022,4023','','']", 1);
        Object history =
                player.getPlayerProgress()
                        .getClass()
                        .getMethod("getInvestigation")
                        .invoke(player.getPlayerProgress());
        history.getClass().getMethod("recordFirstDungeonReward", int.class).invoke(history, 4024);
        assertEquals(0, progress(62004));
        history.getClass().getMethod("recordFirstDungeonReward", int.class).invoke(history, 4022);
        assertEquals(1, progress(62004));
    }

    @Test
    void progressIsIndependentBetweenPlayersAndSurvivesSerialization() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 3);
        trigger("TRIGGER_DO_FORGE", 0, 2);
        String saved = gson.toJson(player.getPlayerProgress());
        var other = new BookPlayer();
        other.getProperties().put(PlayerProperty.PROP_PLAYER_LEVEL.getId(), 45);
        other.getOpenStates().putAll(player.getOpenStates());
        call("onPlayerLogin", new Class<?>[] {Player.class}, other);
        var otherData = PlayerInvestigationAllInfoNotify.parseFrom(other.packets.get(0).getData());
        assertEquals(0, otherData.getInvestigationTargetList(0).getProgress());
        var restored = gson.fromJson(saved, emu.grasscutter.game.player.PlayerProgress.class);
        var field = Player.class.getDeclaredField("playerProgress");
        field.setAccessible(true);
        field.set(other, restored);
        other.packets.clear();
        call("onPlayerLogin", new Class<?>[] {Player.class}, other);
        otherData = PlayerInvestigationAllInfoNotify.parseFrom(other.packets.get(0).getData());
        assertEquals(2, otherData.getInvestigationTargetList(0).getProgress());
    }

    @Test
    void concurrentCountersDoNotLoseAcceptedEvents() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 100);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 40; i++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    try {
                                        trigger("TRIGGER_DO_FORGE", 0, 1);
                                    } catch (Exception failure) {
                                        throw new RuntimeException(failure);
                                    }
                                }));
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(40, progress(60001));
    }

    private GameItem equipment(int id, String type, int rank, int level) {
        var item = new GameItem();
        item.setItemId(id);
        item.setCount(1);
        item.setLevel(level);
        item.setItemData(
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',rankLevel:" + rank + "}",
                        emu.grasscutter.data.excels.ItemData.class));
        player.inventory.items.add(item);
        return item;
    }

    @Test
    void relicMilestonesUseInternalLevelWithoutCompletingOneLevelEarly() throws Exception {
        target(60019, "TRIGGER_RELIQUARY_UPGRADE", "['5','1','1']", 1);
        var relic = equipment(61341, "ITEM_RELIQUARY", 4, 4);
        assertEquals(0, progress(60019));
        relic.setLevel(5);
        refresh();
        assertEquals(1, progress(60019));
    }

    @Test
    void educationRelicMilestoneRequiresBothFourStarPieces() throws Exception {
        target(62037, "TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL", "['9','4','2']", 1);
        equipment(74432, "ITEM_RELIQUARY", 4, 9);
        assertEquals(0, progress(62037));
        equipment(74422, "ITEM_RELIQUARY", 4, 9);
        assertEquals(1, progress(62037));
    }

    @Test
    void exactRankRelicTargetDoesNotCountFiveStarPiecesAsFourStar() throws Exception {
        target(62037, "TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL", "['9','4','2']", 1);
        equipment(74432, "ITEM_RELIQUARY", 4, 9);
        equipment(75543, "ITEM_RELIQUARY", 5, 20);
        assertEquals(0, progress(62037));
    }

    @Test
    void specificWeaponTargetChecksBothTheIdAndItsCurrentLevel() throws Exception {
        target(62029, "TRIGGER_SPECIFIED_WEAPON_UPGRADE", "['11406','20','']", 1);
        equipment(11401, "ITEM_WEAPON", 4, 90);
        var desired = equipment(11406, "ITEM_WEAPON", 4, 19);
        assertEquals(0, progress(62029));
        desired.setLevel(20);
        refresh();
        assertEquals(1, progress(62029));
    }

    @Test
    void invalidOrDisusedTargetsAreNotPublishedOrCountedInTheChapter() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 1);
        var type = Class.forName("emu.grasscutter.data.excels.InvestigationTargetData");
        targets.put(
                60002,
                gson.fromJson("{id:60002,investigationId:10001,progress:1,isDisuse:true}", type));
        targets.put(60003, gson.fromJson("{id:60003,investigationId:10001,progress:0}", type));
        targets.put(60004, gson.fromJson("{id:60004,investigationId:99999,progress:1}", type));
        var data = snapshot();
        assertEquals(1, data.getInvestigationTargetListCount());
        assertEquals(1, data.getInvestigationList(0).getTotalProgress());
    }

    @Test
    void liveBattlePassEventUpdatesTheIndependentHandbookBeforeAnEmptyMissionLookup()
            throws Exception {
        target(60016, "TRIGGER_DO_FORGE", "[]", 3);
        var missions = GameData.getBattlePassMissionDataMap();
        var previous = new HashMap<>(missions);
        try {
            missions.clear();
            var events =
                    new emu.grasscutter.game.battlepass.BattlePassSystem(
                            environment.scene.getWorld().getServer());
            events.triggerMission(player, "TRIGGER_DO_FORGE", 0, 2);
            assertEquals(2, progress(60016));
            assertTrue(
                    player.packets.stream()
                            .anyMatch(
                                    p ->
                                            p.getOpcode()
                                                    == PacketOpcodes
                                                            .PlayerInvestigationTargetNotify));
        } finally {
            missions.clear();
            missions.putAll(previous);
        }
    }

    @Test
    void oldSavesWithoutTheNewStateRemainValid() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 3);
        var restored = gson.fromJson("{}", emu.grasscutter.game.player.PlayerProgress.class);
        var field = Player.class.getDeclaredField("playerProgress");
        field.setAccessible(true);
        field.set(player, restored);
        trigger("TRIGGER_DO_FORGE", 0, 2);
        assertEquals(2, progress(60001));
    }

    @Test
    void embeddedProgressPersistsThroughTheRealMongoCodecAndRestoresTheNextLogin()
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri, "An explicitly isolated Mongo URI is required");
        String database =
                "lunagc_test_investigation129_" + UUID.randomUUID().toString().replace("-", "");
        target(60001, "TRIGGER_DO_FORGE", "[]", 3);
        trigger("TRIGGER_DO_FORGE", 0, 2);
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredProgress.class);
            try {
                var record = new StoredProgress();
                record.id = "player1";
                record.progress = player.getPlayerProgress();
                store.save(record);
                var loaded = store.find(StoredProgress.class).first();
                assertNotNull(loaded);
                assertEquals("player1", loaded.id);
                var field = Player.class.getDeclaredField("playerProgress");
                field.setAccessible(true);
                field.set(player, loaded.progress);
                player.packets.clear();
                assertEquals(2, progress(60001));
                trigger("TRIGGER_DO_FORGE", 0, 1);
                assertEquals(3, progress(60001));
            } finally {
                assertTrue(database.startsWith("lunagc_test_investigation129_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void refreshPublishesNewlyUnlockedTargetsEvenBeforeTheyHaveProgress() throws Exception {
        target(60001, "TRIGGER_DO_FORGE", "[]", 3);
        player.getOpenStates().put(1101, 0);
        assertEquals(0, snapshot().getInvestigationTargetListCount());
        player.packets.clear();
        player.getOpenStates().put(1101, 1);
        refresh();
        var data = PlayerInvestigationAllInfoNotify.parseFrom(player.packets.get(0).getData());
        assertEquals(1, data.getInvestigationTargetListCount());
        assertEquals(0, data.getInvestigationTargetList(0).getProgress());
    }

    @Test
    void completeResourceDefinitionsLoadThroughTheStandardMapBinding() throws Exception {
        chapters.clear();
        targets.clear();
        var chapterType = Class.forName("emu.grasscutter.data.excels.InvestigationData");
        var targetType = Class.forName("emu.grasscutter.data.excels.InvestigationTargetData");
        assertSame(
                chapters,
                invoke(
                        GameData.class,
                        "getMapByResourceDef",
                        new Class<?>[] {Class.class},
                        chapterType));
        assertSame(
                targets,
                invoke(
                        GameData.class,
                        "getMapByResourceDef",
                        new Class<?>[] {Class.class},
                        targetType));
        var path = java.nio.file.Path.of("resources/ExcelBinOutput");
        var chapterRows =
                (Object[])
                        gson.fromJson(
                                java.nio.file.Files.readString(
                                        path.resolve("InvestigationConfigData.json")),
                                java.lang.reflect.Array.newInstance(chapterType, 0).getClass());
        var targetRows =
                (Object[])
                        gson.fromJson(
                                java.nio.file.Files.readString(
                                        path.resolve("InvestigationTargetConfigData.json")),
                                java.lang.reflect.Array.newInstance(targetType, 0).getClass());
        for (var row : chapterRows)
            chapters.put((Integer) chapterType.getMethod("getId").invoke(row), row);
        int activeTargets = 0;
        for (var row : targetRows) {
            int id = (Integer) targetType.getMethod("getId").invoke(row);
            targets.put(id, row);
            int parent = (Integer) targetType.getMethod("getInvestigationId").invoke(row);
            if (!(Boolean) targetType.getMethod("isDisuse").invoke(row)
                    && (parent >= 10001 && parent <= 10009 || parent >= 30001 && parent <= 30004))
                activeTargets++;
        }
        assertEquals(22, chapters.size());
        assertEquals(250, targets.size());
        var data = snapshot();
        assertEquals(13, data.getInvestigationListCount());
        assertEquals(activeTargets, data.getInvestigationTargetListCount());
        assertEquals(
                activeTargets,
                data.getInvestigationListList().stream().mapToInt(c -> c.getTotalProgress()).sum());
    }
}
