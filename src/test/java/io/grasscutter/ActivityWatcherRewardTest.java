package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.*;

import dev.morphia.*;
import dev.morphia.annotations.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameServer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;

/** Real watcher entry, inventory and Mongo writes, with controlled activity write failures. */
class ActivityWatcherRewardTest {
    static final int ACTIVITY = 2014,
            SCHEDULE = 2014771,
            WATCHER = 9000101,
            REWARD = 9000201,
            MATERIAL = 104003;
    final Gson gson = new Gson();
    final List<Runnable> restore = new ArrayList<>();
    MongoClient client;
    Datastore store, previous;
    PlayerActivityData data;
    ActivityConfigItem config;
    RewardData reward;
    RewardPlayer player;

    @Entity(value = "players", useDiscriminator = false)
    static final class RewardPlayer extends Player {
        @Transient ActivityManager activity;
        @Transient GameServer server;
        @Transient int failPacket;
        @Transient Runnable packetHook;
        @Transient int hookOpcode;
        @Transient final List<BasePacket> packets = new ArrayList<>();

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {}

        @Override
        public boolean setProperty(PlayerProperty p, int n) {
            getProperties().put(p.getId(), n);
            return true;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            if (packet.getOpcode() == hookOpcode && packetHook != null) {
                var callback = packetHook;
                packetHook = null;
                callback.run();
            }
            if (packet.getOpcode() == failPacket) {
                failPacket = 0;
                throw new IllegalStateException("injected notification failure");
            }
            packets.add(packet);
        }
    }

    static void field(Class<?> type, Object owner, String name, Object value) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(owner, value);
    }

    <T> void preserve(Int2ObjectMap<T> map) {
        var before = new HashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(before);
                });
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        previous = DatabaseManager.getGameDatastore();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_watcher_reward_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(RewardPlayer.class, PlayerActivityData.class, GameItem.class);
        field(DatabaseManager.class, null, "gameDatastore", store);
        var mode = Grasscutter.getRunMode();
        restore.add(() -> Grasscutter.setRunModeOverride(mode));
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
        var configurationField = ActivityManager.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        var oldConfig = (ActivityManager.Configuration) configurationField.get(null);
        restore.add(() -> ActivityManager.installConfiguration(oldConfig));
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(Map.of(), Map.of(), Map.of()));
        preserve(GameData.getItemDataMap());
        preserve(GameData.getActivityDataMap());
        preserve(GameData.getActivityWatcherDataMap());
        preserve(GameData.getRewardDataMap());
        item(MATERIAL, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 99999);
        item(201, "ITEM_VIRTUAL", "MATERIAL_ADSORBATE", 1);
        item(202, "ITEM_VIRTUAL", "MATERIAL_ADSORBATE", 1);
        item(141, "ITEM_VIRTUAL", "MATERIAL_NONE", 1);
        item(11401, "ITEM_WEAPON", "WEAPON_MATERIAL_NONE", 1);
        var meta =
                gson.fromJson(
                        "{id:"
                                + WATCHER
                                + ",rewardID:"
                                + REWARD
                                + ",progress:1,triggerConfig:{triggerType:'TRIGGER_FINISH_DUNGEON',paramList:[]}}",
                        ActivityWatcherData.class);
        meta.onLoad();
        GameData.getActivityWatcherDataMap().put(WATCHER, meta);
        var activity =
                gson.fromJson(
                        "{activityId:"
                                + ACTIVITY
                                + ",activityType:'NEW_ACTIVITY_GENERAL',watcherId:["
                                + WATCHER
                                + "],activityCoinIdList:[141]}",
                        ActivityData.class);
        activity.onLoad();
        GameData.getActivityDataMap().put(ACTIVITY, activity);
        player = new RewardPlayer();
        player.setUid(74403);
        player.server = new GameServer();
        field(Player.class, player, "teamManager", new TeamManager(player));
        player.activity = new ActivityManager(player);
        player.setMora(100);
        field(
                GameServer.class,
                player.server,
                "inventorySystem",
                new emu.grasscutter.game.systems.InventorySystem(player.server));
        config = new ActivityConfigItem();
        config.setActivityId(ACTIVITY);
        config.setScheduleId(SCHEDULE);
        config.setBeginTime(new Date(System.currentTimeMillis() - 10000));
        config.setEndTime(new Date(System.currentTimeMillis() + 86400000));
        config.onLoad();
        var handler = new DefaultActivityHandler();
        handler.setActivityConfigItem(config);
        handler.initWatchers(Map.of());
        config.setActivityHandler(handler);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(ACTIVITY, config), Map.of(SCHEDULE, config), Map.of(141, ACTIVITY)));
        data =
                PlayerActivityData.of()
                        .uid(player.getUid())
                        .activityId(ACTIVITY)
                        .scheduleId(SCHEDULE)
                        .player(player)
                        .watcherInfoMap(
                                new HashMap<>(
                                        Map.of(
                                                WATCHER,
                                                PlayerActivityData.WatcherInfo.of()
                                                        .watcherId(WATCHER)
                                                        .totalProgress(1)
                                                        .curProgress(1)
                                                        .build())))
                        .build();
        data.saveSync();
        player.activity.getPlayerActivityDataMap().put(ACTIVITY, data);
        reward = new RewardData();
        reward.rewardId = REWARD;
        rows(new ItemParamData(MATERIAL, 5));
        GameData.getRewardDataMap().put(REWARD, reward);
        DatabaseHelper.saveGameSync(player);
        player.packets.clear();
    }

    ItemData item(int id, String type, String material, int stack) {
        var d =
                gson.fromJson(
                        "{id:"
                                + id
                                + ",itemType:'"
                                + type
                                + "',materialType:'"
                                + material
                                + "',stackLimit:"
                                + stack
                                + ",rankLevel:4}",
                        ItemData.class);
        d.onLoad();
        GameData.getItemDataMap().put(id, d);
        return d;
    }

    void rows(ItemParamData... rows) {
        reward.rewardItemList = List.of(rows);
    }

    int claim() {
        try {
            return data.takeWatcherReward(WATCHER);
        } catch (RuntimeException error) {
            return 1;
        }
    }

    Document row() {
        return store.getDatabase()
                .getCollection("activities")
                .find(new Document("_id", data.getId()))
                .first();
    }

    boolean taken() {
        return data.getWatcherInfoMap().get(WATCHER).isTakenReward();
    }

    void failureBeforeNextActivityWrite() throws Exception {
        GuardedActivityTestStore.bind(
                data,
                store,
                store,
                true,
                document -> {
                    throw new IllegalStateException("activity write unavailable");
                },
                ignored -> {});
    }

    void failureAt(int nth, boolean after) throws Exception {
        int[] attempts = {0};
        GuardedActivityTestStore.bind(
                data,
                store,
                store,
                true,
                document -> {
                    if (++attempts[0] == nth && !after)
                        throw new IllegalStateException("before activity ACK " + nth);
                },
                document -> {
                    if (attempts[0] == nth && after)
                        throw new IllegalStateException("lost activity ACK " + nth);
                });
    }

    void cold() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        data = DatabaseHelper.getPlayerActivityData(player.getUid(), ACTIVITY);
        data.setPlayer(player);
        player.activity.getPlayerActivityDataMap().put(ACTIVITY, data);
    }

    String pendingId() {
        return data.getPendingWatcherReward().getOperationId();
    }

    void seedStack(int count) throws Exception {
        var item = new GameItem(MATERIAL, count);
        assertTrue(player.getInventory().addItem(item));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        DatabaseHelper.saveGameSync(item);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client == null) return;
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        field(DatabaseManager.class, null, "gameDatastore", previous);
        for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
        store.getDatabase().drop();
        client.close();
    }

    @Test
    void anUnconfirmedReservationCannotGrantAnything() throws Exception {
        failureBeforeNextActivityWrite();
        assertNotEquals(0, claim());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
        assertFalse(taken());
    }

    @Test
    void aFullBagRejectsTheEntireBatchBeforeAnyCurrencyIsGranted() {
        player.getInventory()
                .createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(0));
        rows(new ItemParamData(202, 20), new ItemParamData(MATERIAL, 5));
        assertNotEquals(0, claim());
        assertEquals(100, player.getMora());
        assertFalse(taken());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void invalidCountsAreNotConvertedIntoOneFreeItem(int count) {
        rows(new ItemParamData(MATERIAL, count));
        assertNotEquals(0, claim());
        assertFalse(taken());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void duplicateRowsAreCheckedAsOneStackBeforeGrant() {
        item(MATERIAL, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 5);
        rows(new ItemParamData(MATERIAL, 3), new ItemParamData(MATERIAL, 3));
        assertNotEquals(0, claim());
        assertFalse(taken());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void unknownItemsCannotConsumeTheWatcherOrGrantTheOtherRows() {
        rows(new ItemParamData(202, 20), new ItemParamData(99999999, 1));
        assertNotEquals(0, claim());
        assertEquals(100, player.getMora());
        assertFalse(taken());
    }

    @Test
    void aStaleReplayCannotReceiveRewards() {
        data.setScheduleId(SCHEDULE + 1);
        assertNotEquals(0, claim());
        assertFalse(taken());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void disusedWatchersCannotBeClaimed() throws Exception {
        field(
                ActivityWatcherData.class,
                GameData.getActivityWatcherDataMap().get(WATCHER),
                "isDisuse",
                true);
        assertNotEquals(0, claim());
        assertFalse(taken());
    }

    @Test
    void equipmentCountCreatesThatManyIndependentInstances() throws Exception {
        rows(new ItemParamData(11401, 2));
        assertEquals(0, claim());
        assertEquals(2, player.getInventory().getInventoryTab(ItemType.ITEM_WEAPON).getSize());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        assertEquals(
                2,
                store.getDatabase()
                        .getCollection("items")
                        .countDocuments(new Document("ownerId", player.getUid())));
    }

    @Test
    void partialDeliveryCannotBeRepeatedAfterColdRead() throws Exception {
        player.failPacket = PacketOpcodes.StoreItemChangeNotify;
        assertNotEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        cold();
        assertEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        assertTrue(taken());
        assertNotNull(row().get("lastWatcherReward"));
        assertNull(row().get("pendingWatcherReward"));
    }

    @Test
    void successfulDeliveryHasAnAcknowledgedClaimRecord() {
        assertEquals(0, claim());
        assertTrue(taken());
        assertNotNull(row().get("lastWatcherReward"));
        assertNull(row().get("pendingWatcherReward"));
    }

    @Test
    void aConsumedNewInstanceStillProvesItsOriginalDeliveryAfterReconnect() throws Exception {
        player.failPacket = PacketOpcodes.StoreItemChangeNotify;
        assertNotEquals(0, claim());
        assertTrue(player.getInventory().removeItem(MATERIAL, 5));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        cold();
        assertEquals(0, claim());
        assertTrue(taken());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
        assertTrue(DatabaseHelper.getInventoryItems(player).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void liveRecoveryDoesNotRepeatAcknowledgedOrUnstartedRows(int nth) throws Exception {
        failureAt(nth, false);
        assertNotEquals(0, claim());
        assertEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        assertTrue(taken());
        assertNotEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void lostAcknowledgementsDoNotRepeatRewards(int nth) throws Exception {
        failureAt(nth, true);
        assertNotEquals(0, claim());
        assertEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        assertTrue(taken());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void cancelledUnstartedPlansRetireTheirLateWrites(int nth) throws Exception {
        var late = new DelayedActivitySave(data, store, nth);
        assertNotEquals(0, claim());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
        String id = pendingId();
        assertTrue(data.getWatcherRewards().cancel(id).contains(id));
        assertFalse(late.release());
        assertFalse(taken());
        cold();
        assertNull(data.getPendingWatcherReward());
        assertEquals(ActivityWatcherReceipt.CANCELLED, data.getLastWatcherReward().getStage());
        assertEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void anAppliedFinalWriteCannotBeCancelledEvenWhenItsAcknowledgementIsLate() throws Exception {
        var late = new DelayedActivitySave(data, store, 4);
        assertNotEquals(0, claim());
        String id = pendingId();
        late.commitBeforeNextCheck();
        assertThrows(IllegalStateException.class, () -> data.getWatcherRewards().cancel(id));
        assertTrue(taken());
        assertNull(data.getPendingWatcherReward());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void liveCheckpointRecoveryRetiresDelayedWritesWithoutRepeatingAnExistingStack()
            throws Exception {
        seedStack(10);
        var late = new DelayedActivitySave(data, store, 3);
        assertNotEquals(0, claim());
        assertEquals(15, player.getInventory().getItemCountById(MATERIAL));
        assertEquals(0, claim());
        assertFalse(late.release());
        assertEquals(15, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void coldAmbiguousExistingStacksAreRetainedWithoutGuessingTheirDelta() throws Exception {
        seedStack(10);
        player.failPacket = PacketOpcodes.StoreItemChangeNotify;
        assertNotEquals(0, claim());
        cold();
        assertNotEquals(0, claim());
        assertEquals(15, player.getInventory().getItemCountById(MATERIAL));
        assertEquals(ActivityWatcherReceipt.STARTED, data.getPendingWatcherReward().getStage());
        assertFalse(taken());
        String id = pendingId();
        assertThrows(IllegalStateException.class, () -> data.getWatcherRewards().cancel(id));
    }

    @Test
    void coldGlobalCurrencyAmbiguityCannotBeInferredFromAnEqualBalance() throws Exception {
        rows(new ItemParamData(202, 20));
        player.failPacket = PacketOpcodes.StoreItemChangeNotify;
        assertNotEquals(0, claim());
        cold();
        player.setMora(100);
        assertNotEquals(0, claim());
        assertEquals(100, player.getMora());
        assertFalse(taken());
        assertNotNull(data.getPendingWatcherReward());
    }

    @ParameterizedTest
    @CsvSource({"3,false", "3,true", "4,false", "4,true", "5,false", "5,true"})
    void activityCurrencyIsRecoveredFromTheSameReservedOperation(int nth, boolean after)
            throws Exception {
        rows(new ItemParamData(141, 7), new ItemParamData(MATERIAL, 5));
        failureAt(nth, after);
        int result = claim();
        if (nth == 5) assertEquals(0, result);
        else assertNotEquals(0, result);
        var receipt =
                data.getPendingWatcherReward() != null
                        ? data.getPendingWatcherReward()
                        : data.getLastWatcherReward();
        String linked = receipt.getLines().get(0).getCoinOperationId();
        if (nth == 4 && !after) {
            assertThrows(
                    IllegalStateException.class, () -> data.getCoinTransaction().cancel(linked));
            assertEquals(linked, data.getPendingCoinOperation().getOperationId());
        }
        cold();
        if (nth == 5) assertTrue(taken());
        else assertEquals(0, claim());
        assertEquals(linked, data.getLastCoinOperation().getOperationId());
        assertEquals(7, data.getActivityCoins().get(141));
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        cold();
        assertTrue(taken());
        assertEquals(7, ActivityCurrencies.balance(player, 141));
    }

    @Test
    void aPendingSourceBlocksOtherCreditsPaymentsSavesAndReplay() throws Exception {
        assertTrue(ActivityCurrencies.credit(player, 141, 20));
        failureAt(2, false);
        assertNotEquals(0, claim());
        assertFalse(ActivityCurrencies.credit(player, 141, 2));
        assertFalse(ActivityCurrencies.pay(player, Map.of(141, 1)));
        assertThrows(IllegalStateException.class, data::saveSync);
        config.setScheduleId(SCHEDULE + 1);
        assertThrows(IllegalStateException.class, player.activity::refreshActivities);
        assertEquals(SCHEDULE, data.getScheduleId());
        config.setScheduleId(SCHEDULE);
        assertEquals(1, ActivityWatcherRewards.list(player).size());
        String id = pendingId();
        String before = row().toJson();
        assertTrue(data.getWatcherRewards().audit(id).contains("核对不改"));
        assertEquals(before, row().toJson());
        assertThrows(
                IllegalStateException.class,
                () -> data.getWatcherRewards().resume(UUID.randomUUID().toString()));
        assertEquals(id, pendingId());
    }

    @Test
    void reentrantClaimDuringInventoryNotificationCannotRunTheSamePlanAgain() {
        int[] nested = {-1};
        player.hookOpcode = PacketOpcodes.StoreItemChangeNotify;
        player.packetHook = () -> nested[0] = claim();
        assertEquals(0, claim());
        assertNotEquals(0, nested[0]);
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void aFrozenBatchKeepsItsOriginalRewardAndEquipmentInstances() throws Exception {
        rows(new ItemParamData(11401, 2), new ItemParamData(MATERIAL, 5));
        failureAt(2, false);
        assertNotEquals(0, claim());
        var ids =
                data.getPendingWatcherReward().getLines().stream()
                        .filter(l -> l.getKind() == ActivityWatcherReceipt.EQUIPMENT)
                        .map(ActivityWatcherReceipt.Line::getObjectId)
                        .toList();
        assertEquals(2, new HashSet<>(ids).size());
        rows(new ItemParamData(MATERIAL, 900));
        cold();
        assertEquals(0, claim());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
        for (String id : ids)
            assertNotNull(
                    store.getDatabase()
                            .getCollection("items")
                            .find(new Document("_id", new org.bson.types.ObjectId(id)))
                            .first());
    }

    @Test
    void aChangedItemDefinitionKeepsTheFrozenPlanForReview() throws Exception {
        failureAt(2, false);
        assertNotEquals(0, claim());
        String id = pendingId();
        item(MATERIAL, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 99998);
        assertNotEquals(0, claim());
        assertEquals(id, pendingId());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
        assertFalse(taken());
    }

    @Test
    void unsupportedVirtualRewardsRejectTheEntireBatch() {
        item(131, "ITEM_VIRTUAL", "MATERIAL_NONE", 1);
        rows(new ItemParamData(202, 20), new ItemParamData(131, 1));
        assertNotEquals(0, claim());
        assertEquals(100, player.getMora());
        assertNull(data.getPendingWatcherReward());
        assertFalse(taken());
    }

    @Test
    void anUnfinishedOrForeignWatcherCannotClaimAnything() throws Exception {
        var watcher = data.getWatcherInfoMap().get(WATCHER);
        watcher.setCurProgress(0);
        assertNotEquals(0, claim());
        watcher.setCurProgress(1);
        var other =
                gson.fromJson(
                        "{activityId:"
                                + ACTIVITY
                                + ",activityType:'NEW_ACTIVITY_GENERAL',watcherId:[]}",
                        ActivityData.class);
        other.onLoad();
        GameData.getActivityDataMap().put(ACTIVITY, other);
        assertNotEquals(0, claim());
        assertFalse(taken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uid", "schedule", "cursor", "template", "phase", "count"})
    void malformedOrForeignDurablePlansFailClosedOnColdRead(String change) throws Exception {
        failureAt(2, false);
        assertNotEquals(0, claim());
        var raw = row();
        var pending = raw.get("pendingWatcherReward", Document.class);
        switch (change) {
            case "uid" -> pending.put("uid", player.getUid() + 1);
            case "schedule" -> pending.put("scheduleId", SCHEDULE + 1);
            case "cursor" -> pending.put("cursor", 999);
            case "phase" -> pending.put("stage", ActivityWatcherReceipt.COMPLETED);
            case "template" -> ((Document) pending.getList("lines", Document.class).get(0))
                    .put("template", "{}");
            default -> pending.getList("lines", Document.class).get(0).put("count", 0);
        }
        store.getDatabase()
                .getCollection("activities")
                .replaceOne(new Document("_id", data.getId()), raw);
        assertThrows(
                RuntimeException.class,
                () -> DatabaseHelper.getPlayerActivityData(player.getUid(), ACTIVITY));
    }

    @Test
    void ordinaryCurrenciesAndFurnitureAreAcknowledgedWithOriginalQuantities() throws Exception {
        item(361001, "ITEM_FURNITURE", "MATERIAL_NONE", 9999);
        rows(new ItemParamData(201, 12), new ItemParamData(202, 20), new ItemParamData(361001, 7));
        assertEquals(0, claim());
        assertEquals(12, player.getPrimogems());
        assertEquals(120, player.getMora());
        assertEquals(7, player.getInventory().getItemCountById(361001));
        cold();
        assertTrue(taken());
        var saved =
                store.getDatabase()
                        .getCollection("players")
                        .find(new Document("_id", player.getUid()))
                        .first();
        assertNotNull(saved);
        assertNotNull(data.getLastWatcherReward());
    }

    @Test
    void additionalCurrencyFieldsAreIncludedWithoutChangingTheirQuantity() throws Exception {
        field(RewardData.class, reward, "hcoin", 12);
        field(RewardData.class, reward, "scoin", 20);
        assertEquals(0, claim());
        assertEquals(12, player.getPrimogems());
        assertEquals(120, player.getMora());
        assertEquals(5, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void unsupportedAdditionalRewardsCannotBeSilentlyDropped() throws Exception {
        field(RewardData.class, reward, "playerExp", 20);
        assertNotEquals(0, claim());
        assertFalse(taken());
        assertNull(data.getPendingWatcherReward());
        assertEquals(0, player.getInventory().getItemCountById(MATERIAL));
    }

    @Test
    void originalExcelAliasesAndUnusedSlotsAreLoadedBeforeAdmission() {
        reward =
                gson.fromJson(
                        "{rewardId:"
                                + REWARD
                                + ",rewardItemList:[{itemId:201,itemCount:20},{itemId:0,itemCount:0}]}",
                        RewardData.class);
        reward.onLoad();
        GameData.getRewardDataMap().put(REWARD, reward);
        assertEquals(1, reward.getRewardItemList().size());
        assertEquals(0, claim());
        assertEquals(20, player.getPrimogems());
        assertTrue(taken());
    }

    @Test
    void uniqueCosmeticsCanRecoverAfterTheirUnlockNotificationFailed() throws Exception {
        int card = 210062;
        var definition =
                gson.fromJson(
                        "{id:"
                                + card
                                + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NAMECARD',useOnGain:true,stackLimit:1,itemUse:[{useOp:'ITEM_USE_GAIN_NAME_CARD',useParam:[]}]}",
                        ItemData.class);
        definition.onLoad();
        GameData.getItemDataMap().put(card, definition);
        rows(new ItemParamData(card, 1));
        player.failPacket = PacketOpcodes.UnlockNameCardNotify;
        assertNotEquals(0, claim());
        assertTrue(player.getNameCardList().contains(card));
        cold();
        player.getNameCardList().remove(card);
        assertEquals(0, claim());
        assertTrue(player.getNameCardList().contains(card));
        cold();
        assertTrue(taken());
        assertEquals(0, player.getInventory().getItemCountById(card));
    }
}
