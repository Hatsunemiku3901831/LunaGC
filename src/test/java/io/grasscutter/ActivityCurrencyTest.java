package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;


import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.battlepass.*;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ActivityInfoNotifyOuterClass.ActivityInfoNotify;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass.BuyGoodsReq;
import emu.grasscutter.net.proto.BuyGoodsRspOuterClass.BuyGoodsRsp;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.recv.HandlerBuyGoodsReq;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/** Real resources, activity documents, inventory payments and existing activity-info protocol. */
class ActivityCurrencyTest extends QuestTestFixture {
    static final Path RESOURCES = Path.of("resources/ExcelBinOutput");
    static List<ActivityData> activities;
    static List<ShopGoodsData> goods;
    static List<ActivityShopData> shops;
    static List<ActivityShopSheetData> sheets;
    static List<ItemData> items;
    static final Map<Integer, Integer> OWNERS = new TreeMap<>();
    private final List<Runnable> restores = new ArrayList<>();

    @BeforeAll
    static void resourceDefinitions() throws Exception {
        var gson = new Gson();
        var raw =
                JsonParser.parseString(
                                Files.readString(
                                        RESOURCES.resolve("NewActivityExcelConfigData.json")))
                        .getAsJsonArray();
        activities = new ArrayList<>();
        for (var element : raw) {
            var row = element.getAsJsonObject();
            activities.add(gson.fromJson(row, ActivityData.class));
            if (row.has("activityCoinIdList"))
                for (var coin : row.getAsJsonArray("activityCoinIdList"))
                    OWNERS.put(coin.getAsInt(), row.get("activityId").getAsInt());
        }
        goods =
                JsonUtils.loadToList(
                        RESOURCES.resolve("ShopGoodsExcelConfigData.json"), ShopGoodsData.class);
        goods.forEach(ShopGoodsData::onLoad);
        shops =
                JsonUtils.loadToList(
                        RESOURCES.resolve("ActivityShopOverallExcelConfigData.json"),
                        ActivityShopData.class);
        sheets =
                JsonUtils.loadToList(
                        RESOURCES.resolve("ActivityShopSheetExcelConfigData.json"),
                        ActivityShopSheetData.class);
        items =
                JsonUtils.loadToList(
                                RESOURCES.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(
                                item ->
                                        item.getItemType() == ItemType.ITEM_VIRTUAL
                                                || item.getId() == 104003)
                        .toList();
        items.forEach(ItemData::onLoad);
        var virtualIds = new HashSet<Integer>();
        for (var item : items)
            if (item.getItemType() == ItemType.ITEM_VIRTUAL) virtualIds.add(item.getId());
        var sources = new HashMap<Integer, Integer>();
        for (var history :
                JsonUtils.loadToList(
                        Path.of("src/main/resources/defaults/data/ActivityHistory.json"),
                        HistoricalActivity.class))
            sources.put(history.getScheduleId(), history.getActivityId());
        var shopOwners = new HashMap<Integer, Integer>();
        for (var shop : shops) {
            var owner = sources.get(shop.getScheduleId());
            if (owner != null) shopOwners.put(shop.getShopTypeId(), owner);
        }
        for (var good : goods) {
            var owner = shopOwners.get(good.getShopType());
            if (owner == null || owner == 2001)
                continue; // Aster already has its own receipt ledger.
            for (var cost : good.getCostItems()) {
                if (cost.getCount() <= 0 || !virtualIds.contains(cost.getId())) continue;
                assertNull(BeyondCurrency.propertyForItem(cost.getId()));
                assertFalse(
                        Set.of(101, 102, 105, 106, 107, 121, 201, 202, 203, 204, 207)
                                .contains(cost.getId()));
                var previous = OWNERS.putIfAbsent(cost.getId(), owner);
                assertTrue(previous == null || previous.equals(owner));
            }
        }
        assertEquals(67, OWNERS.size());
        assertEquals(2006, OWNERS.get(128));
        assertEquals(2014, OWNERS.get(141));
        assertFalse(OWNERS.containsKey(109));
        assertFalse(OWNERS.containsKey(110));
    }

    static Stream<Arguments> resourceCurrencies() {
        return OWNERS.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void activityResources() throws Exception {
        player.getSession().setPlayer(player);
        player.getSession().setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
        preserve(GameData.getActivityDataMap());
        preserve(GameData.getActivityWatcherDataMap());
        preserve(GameData.getMapByResourceDef(ShopGoodsData.class));
        preserve(GameData.getActivityShopDataMap());
        preserve(GameData.getActivityShopSheetDataMap());
        preserve(GameData.getItemDataMap());
        replaceField(
                GameData.class, null, "shopGoods", new HashMap<Integer, List<ShopGoodsData>>());
        for (var row : activities) {
            row.onLoad();
            GameData.getActivityDataMap().put(row.getId(), row);
        }
        var shopResources =
                (Int2ObjectMap<ShopGoodsData>) GameData.getMapByResourceDef(ShopGoodsData.class);
        for (var row : goods) shopResources.put(row.getId(), row);
        for (var row : shops) GameData.getActivityShopDataMap().put(row.getId(), row);
        for (var row : sheets) GameData.getActivityShopSheetDataMap().put(row.getId(), row);
        for (var row : items) GameData.getItemDataMap().put(row.getId(), row);
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new BattlePassSystem(player.server));
        replaceField(
                GameServer.class,
                player.server,
                "inventorySystem",
                new InventorySystem(player.server));
        replaceField(
                emu.grasscutter.game.player.Player.class,
                player,
                "battlePassManager",
                new BattlePassManager(player));
        ActivityManager.installConfiguration(new ActivityManager.Configuration(Map.of(), Map.of()));
        player.activity = new ActivityManager(player);
        player.packets.clear();
    }

    private <T> void preserve(Int2ObjectMap<T> map) {
        var old = new HashMap<>(map);
        restores.add(
                () -> {
                    map.clear();
                    old.forEach((id, value) -> map.put(id.intValue(), value));
                });
        map.clear();
    }

    @AfterEach
    void restoreActivityResources() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    private ActivityConfigItem config(int id) {
        var item = new ActivityConfigItem();
        item.setActivityId(id);
        item.setActivityType(
                ActivityType.getTypeByName(GameData.getActivityDataMap().get(id).getActivityType())
                        .getValue());
        item.setScheduleId(id * 1000 + 771);
        item.setBeginTime(new Date(System.currentTimeMillis() - 3600000));
        item.setEndTime(new Date(System.currentTimeMillis() + 30L * 86400000));
        item.onLoad();
        return item;
    }

    private void enable(int... ids) {
        var configs = Arrays.stream(ids).mapToObj(this::config).toList();
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(configs));
        player.activity = new ActivityManager(player);
        player.packets.clear();
    }

    private boolean give(int id, int count) {
        return player.getInventory().addItem(new GameItem(id, count));
    }

    private int balance(int id) {
        return player.getInventory().getItemCountById(id);
    }

    @ParameterizedTest
    @MethodSource("resourceCurrencies")
    void everyResourceBoundCurrencyCanBeGrantedReadAndSpent(int coin, int activity) {
        enable(activity);
        assertTrue(give(coin, 9));
        assertEquals(9, balance(coin));
        assertNull(player.getInventory().getItemById(coin));
        assertTrue(player.getInventory().payItem(coin, 7));
        assertEquals(2, balance(coin));
        assertFalse(player.getInventory().payItem(coin, 3));
        assertEquals(2, balance(coin));
    }

    @Test
    void anInactiveCurrencyCannotBecomeAnAcceptedNoOp() {
        assertFalse(give(128, 9));
        assertFalse(give(141, 9));
        assertEquals(0, balance(128));
        assertEquals(0, balance(141));
        assertFalse(player.getInventory().payItem(141, 1));
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.StoreItemChangeNotify));
    }

    @Test
    void creditedCoinsUseTheExistingActivityInfoMapAndSurviveColdReload() throws Exception {
        enable(2014);
        assertTrue(give(141, 60));
        assertEquals(60, balance(141));
        var info =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ActivityInfoNotify)
                        .map(
                                p -> {
                                    try {
                                        return ActivityInfoNotify.parseFrom(p.getData())
                                                .getActivityInfo();
                                    } catch (Exception e) {
                                        throw new AssertionError(e);
                                    }
                                })
                        .filter(p -> p.getActivityId() == 2014)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(Map.of(141, 60), info.getActivityCoinMapMap());
        var original = player.activity.getPlayerActivityDataMap().get(2014);
        player.activity = new ActivityManager(player);
        assertNotSame(original, player.activity.getPlayerActivityDataMap().get(2014));
        assertEquals(60, balance(141));
        assertEquals(
                Map.of(141, 60),
                player.activity.getInfoProtoByActivityId(2014).getActivityCoinMapMap());
    }

    @Test
    void aFreshReplayResetsCoinsButAResumePreservesThem() {
        enable(2014);
        assertTrue(give(141, 60));
        assertEquals(60, balance(141));
        var config = ActivityManager.getScheduleActivityConfigMap().values().iterator().next();
        var id = player.activity.getPlayerActivityDataMap().get(2014).getId();
        config.setDisabled(true);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        player.activity.refreshActivities();
        assertEquals(0, balance(141));
        config.setDisabled(false);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        player.activity.refreshActivities();
        assertEquals(60, balance(141));
        config.setScheduleId(config.getScheduleId() + 1);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        player.activity.refreshActivities();
        assertEquals(0, balance(141));
        assertEquals(id, player.activity.getPlayerActivityDataMap().get(2014).getId());
    }

    @Test
    void emptyWatcherDocumentsSurviveAColdResume() throws Exception {
        enable(2014);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var config = ActivityManager.getScheduleActivityConfigMap().values().iterator().next();
        config.setDisabled(true);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        player.activity.refreshActivities();
        config.setDisabled(false);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        assertDoesNotThrow(player.activity::refreshActivities);
        assertNotNull(player.activity.getPlayerActivityDataMap().get(2014).getWatcherInfoMap());
    }

    @Test
    void expiredAndStaleScheduleDataCannotReceiveOrSpendCoins() {
        enable(2014);
        assertTrue(give(141, 60));
        assertEquals(60, balance(141));
        var data = player.activity.getPlayerActivityDataMap().get(2014);
        data.setScheduleId(data.getScheduleId() - 1);
        assertFalse(give(141, 1));
        assertFalse(player.getInventory().payItem(141, 1));
        data.setScheduleId(data.getScheduleId() + 1);
        var config = ActivityManager.getScheduleActivityConfigMap().values().iterator().next();
        config.setEndTime(new Date(System.currentTimeMillis() - 1));
        assertFalse(give(141, 1));
        assertFalse(player.getInventory().payItem(141, 1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothPaymentOverloadsAggregateAnActivitysCurrenciesBeforeDebiting(boolean array) {
        enable(2006);
        assertTrue(give(127, 50));
        assertTrue(give(128, 40));
        player.setProperty(PlayerProperty.PROP_PLAYER_SCOIN, 100);
        var costs =
                List.of(
                        new ItemParamData(127, 3),
                        new ItemParamData(127, 7),
                        new ItemParamData(128, 5),
                        new ItemParamData(202, 10));
        assertTrue(
                array
                        ? player.getInventory().payItems(costs.toArray(ItemParamData[]::new), 2)
                        : player.getInventory().payItems(costs, 2));
        assertEquals(30, balance(127));
        assertEquals(30, balance(128));
        assertEquals(80, player.getMora());
        var insufficient = List.of(new ItemParamData(127, 31), new ItemParamData(202, 1));
        assertFalse(player.getInventory().payItems(insufficient));
        assertEquals(30, balance(127));
        assertEquals(80, player.getMora());
    }

    @Test
    void unsupportedCrossActivityPaymentsAreRejectedBeforeEitherDebit() {
        enable(2006, 2014);
        assertTrue(give(128, 40));
        assertTrue(give(141, 40));
        assertEquals(40, balance(128));
        assertEquals(40, balance(141));
        assertFalse(
                player.getInventory()
                        .payItems(List.of(new ItemParamData(128, 1), new ItemParamData(141, 1))));
        assertEquals(40, balance(128));
        assertEquals(40, balance(141));
    }

    @Test
    void overflowingOrNonpositiveCreditsDoNotAlterTheBalance() {
        enable(2014);
        assertTrue(give(141, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, balance(141));
        assertFalse(give(141, 1));
        assertFalse(give(141, -1));
        assertFalse(give(141, 0));
        assertEquals(Integer.MAX_VALUE, balance(141));
    }

    @Test
    void concurrentCreditsDoNotLoseAnyAcceptedAmount() throws Exception {
        enable(2014);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    for (int j = 0; j < 5; j++) assertTrue(give(141, 1));
                                    return null;
                                }));
            start.countDown();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
            assertEquals(20, balance(141));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void coinsAreAvailableToRewardCapacityChecksOnlyForTheirCurrentReplay() {
        var reward = List.of(new GameItem(141, 3));
        synchronized (player.getInventory()) {
            assertFalse(RewardCapacity.canReceive(player, reward, false));
        }
        enable(2014);
        synchronized (player.getInventory()) {
            assertTrue(RewardCapacity.canReceive(player, reward, false));
        }
        assertTrue(give(141, Integer.MAX_VALUE - 2));
        synchronized (player.getInventory()) {
            assertFalse(RewardCapacity.canReceive(player, reward, false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedCoinAcknowledgementsFreezeFurtherMutationsAndDoNotDebitOrdinaryCosts(
            boolean committed) throws Exception {
        enable(2006);
        assertTrue(give(127, 50));
        assertEquals(50, balance(127));
        player.setProperty(PlayerProperty.PROP_PLAYER_SCOIN, 100);
        var writes = ActivitySaveFaults.failNextWrite(player.activity.getPlayerActivityDataMap().get(2006), committed);
        player.packets.clear();
        assertThrows(
                IllegalStateException.class,
                () ->
                        player.getInventory()
                                .payItems(
                                        List.of(
                                                new ItemParamData(202, 10),
                                                new ItemParamData(127, 5))));
        assertEquals(100, player.getMora());
        assertEquals(1, writes.get());
        assertFalse(give(127, 1));
        assertFalse(player.getInventory().payItem(127, 1));
        assertEquals(1, writes.get());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ActivityInfoNotify));
    }

    @Test
    void activityInfoMapUsesTheCurrentProtocolFieldNumber() throws Exception {
        enable(2014);
        assertTrue(give(141, 6));
        var encoded = player.activity.getInfoProtoByActivityId(2014).toByteArray();
        var expected = new java.io.ByteArrayOutputStream();
        var mapEntry = new java.io.ByteArrayOutputStream();
        var entry = com.google.protobuf.CodedOutputStream.newInstance(mapEntry);
        entry.writeUInt32(1, 141);
        entry.writeUInt32(2, 6);
        entry.flush();
        var output = com.google.protobuf.CodedOutputStream.newInstance(expected);
        output.writeByteArray(1715, mapEntry.toByteArray());
        output.flush();
        assertTrue(
                java.util.HexFormat.of()
                        .formatHex(encoded)
                        .contains(java.util.HexFormat.of().formatHex(expected.toByteArray())));
        assertEquals(Map.of(141, 6), ActivityInfo.parseFrom(encoded).getActivityCoinMapMap());
    }

    @Test
    void theOriginalSummerExchangePriceIsPaidThroughTheRealBuyHandler() throws Exception {
        enable(2014);
        var config = ActivityManager.getScheduleActivityConfigMap().values().iterator().next();
        config.setBeginTime(new Date(System.currentTimeMillis() - 3L * 86400000));
        config.setOpenTime(config.getBeginTime());
        var shop =
                shops.stream()
                        .filter(row -> row.getScheduleId() == 2014001)
                        .findFirst()
                        .orElseThrow();
        var source =
                goods.stream()
                        .filter(
                                row ->
                                        row.getShopType() == shop.getShopTypeId()
                                                && Set.of(202, 104003).contains(row.getItemId()))
                        .findFirst()
                        .orElseThrow();
        var info = new ShopInfo(source);
        var catalog = new ShopSystem(player.server);
        var entries = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        entries.put(shop.getShopTypeId(), List.of(info));
        catalog.getCatalog().replaceBase(entries);
        replaceField(GameServer.class, player.server, "shopSystem", catalog);
        assertTrue(give(141, 10000));
        int before = balance(source.getItemId());
        player.packets.clear();
        new HandlerBuyGoodsReq()
                .handle(
                        player.getSession(),
                        new byte[0],
                        BuyGoodsReq.newBuilder()
                                .setShopType(shop.getShopTypeId())
                                .setBuyCount(1)
                                .setGoods(ShopGoods.newBuilder().setGoodsId(source.getGoodsId()))
                                .build()
                                .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.BuyGoodsRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, BuyGoodsRsp.parseFrom(packet.getData()).getRetcode());
        int price =
                source.getCostItems().stream()
                        .filter(c -> c.getId() == 141)
                        .mapToInt(ItemParamData::getCount)
                        .sum();
        assertTrue(price > 0);
        assertEquals(10000 - price, balance(141));
        assertEquals(before + source.getItemCount(), balance(source.getItemId()));
        player.activity = new ActivityManager(player);
        assertEquals(10000 - price, balance(141));
    }

    @Test
    void anActiveBlitzRushCurrencyReachesInventoryThroughTheDungeonRewardPath() throws Exception {
        enable(2006);
        var data =
                JsonUtils.loadToList(
                                RESOURCES.resolve("DungeonExcelConfigData.json"), DungeonData.class)
                        .stream()
                        .filter(row -> row.getId() == 7301)
                        .findFirst()
                        .orElseThrow();
        replaceField(DungeonData.class, data, "passCond", 0);
        var preview =
                gson.fromJson(
                        "{id:31902,previewItems:[{id:128,count:'6'}]}", RewardPreviewData.class);
        preview.onLoad();
        replaceField(DungeonData.class, data, "rewardPreviewData", preview);
        var drop = new DungeonDropEntry();
        drop.setItems(List.of(128));
        drop.setItemProbabilities(List.of(1));
        drop.setCounts(List.of(6));
        drop.setProbabilities(List.of(1));
        var old = GameData.getDungeonDropDataMap().put(7301, List.of(drop));
        restores.add(
                () -> {
                    if (old == null) GameData.getDungeonDropDataMap().remove(7301);
                    else GameData.getDungeonDropDataMap().put(7301, old);
                });
        replaceField(
                GameServer.class,
                player.server,
                "dropSystem",
                new emu.grasscutter.game.drop.DropSystem(player.server));
        var dungeon = new DungeonManager(player.getScene(), data);
        assertTrue(dungeon.getStatueDrops(player, false, 0));
        assertEquals(6, balance(128));
        assertTrue(dungeon.hasClaimedDomainReward(player.getUid()));
        assertFalse(dungeon.getStatueDrops(player, false, 0));
        assertEquals(6, balance(128));
    }

    @Test
    void aNotificationFailureDoesNotRepeatOrUndoTheCommittedCredit() throws Exception {
        enable(2014);
        var packets =
                new CopyOnWriteArrayList<BasePacket>() {
                    @Override
                    public boolean add(BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.ActivityInfoNotify)
                            throw new IllegalStateException("activity notification unavailable");
                        return super.add(packet);
                    }
                };
        replaceField(TestPlayer.class, player, "packets", packets);
        assertTrue(give(141, 9));
        assertEquals(9, balance(141));
        assertTrue(give(141, 1));
        assertEquals(10, balance(141));
        var cold = DatabaseHelper.getPlayerActivityData(player.getUid(), 2014);
        assertNotNull(cold);
        assertEquals(10, cold.getActivityCoins().get(141));
    }

    @Test
    void aReplayRefreshWaitsForTheInventoryWriter() throws Exception {
        enable(2014);
        assertTrue(give(141, 9));
        var old = player.activity.getPlayerActivityDataMap().get(2014);
        var config = ActivityManager.getScheduleActivityConfigMap().values().iterator().next();
        config.setScheduleId(config.getScheduleId() + 1);
        ActivityManager.installConfiguration(ActivityManager.prepareConfiguration(List.of(config)));
        var pool = Executors.newSingleThreadExecutor();
        Future<?> refresh;
        try {
            synchronized (player.getInventory()) {
                var entered = new CountDownLatch(1);
                refresh =
                        pool.submit(
                                () -> {
                                    entered.countDown();
                                    player.activity.refreshActivities();
                                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> refresh.get(100, TimeUnit.MILLISECONDS));
                assertSame(old, player.activity.getPlayerActivityDataMap().get(2014));
            }
            refresh.get(5, TimeUnit.SECONDS);
            assertNotSame(old, player.activity.getPlayerActivityDataMap().get(2014));
            assertEquals(0, balance(141));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {141, 202})
    void conflictingOrGlobalCurrencyDeclarationsDoNotReplaceTheLiveConfiguration(int coin) {
        enable(2014);
        assertTrue(give(141, 9));
        var before = ActivityManager.getScheduleActivityConfigMap();
        var row =
                JsonParser.parseString(gson.toJson(GameData.getActivityDataMap().get(2017)))
                        .getAsJsonObject();
        row.add("activityCoinIdList", gson.toJsonTree(List.of(coin)));
        var conflicting = gson.fromJson(row, ActivityData.class);
        conflicting.onLoad();
        GameData.getActivityDataMap().put(2017, conflicting);
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityManager.prepareConfiguration(List.of(config(2014))));
        assertSame(before, ActivityManager.getScheduleActivityConfigMap());
        assertEquals(9, balance(141));
    }
}
