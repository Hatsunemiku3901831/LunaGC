package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeTakeInfoOuterClass.FurnitureMakeTakeInfo;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllReqOuterClass.TakeFurnitureMakeAllReq;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllRspOuterClass.TakeFurnitureMakeAllRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurnitureBulkFlowTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;

    static class BatchPlayer extends InvestigationRewardTest.ClaimPlayer {
        transient GameHome home;
        transient int rejectId, grants, missions;
        transient boolean pretend;
        transient Runnable afterGrant;
        final transient Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item, boolean skip, boolean notify) {
                        if (item.getItemType() == ItemType.ITEM_FURNITURE) {
                            grants++;
                            if (item.getItemId() == rejectId) return false;
                            if (pretend) return true;
                        }
                        boolean result = super.addItem(item, skip, notify);
                        if (item.getItemType() == ItemType.ITEM_FURNITURE && afterGrant != null) {
                            var hook = afterGrant;
                            afterGrant = null;
                            hook.run();
                        }
                        return result;
                    }
                };
        final transient BattlePassManager bp =
                new BattlePassManager() {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {
                        if (type == WatcherTriggerType.TRIGGER_FURNITURE_MAKE) missions += progress;
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return bp;
        }
    }

    private BatchPlayer player;
    private AbilityTestFixture environment;
    private Field datastore;
    private Object oldStore;
    private int saves, failSave;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new BatchPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .level(1)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        clear(GameData.getItemDataMap());
        clear(GameData.getFurnitureMakeConfigDataMap());
        clear(GameData.getHomeWorldLevelDataMap());
        item(360101, "ITEM_FURNITURE", 100);
        item(360102, "ITEM_FURNITURE", 100);
        item(107013, "ITEM_MATERIAL", 100);
        recipe(38, 360101);
        recipe(39, 360102);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
        playerStore =
                new emu.grasscutter.game.player.GuardedPlayerTestStore(
                        player,
                        () -> {
                            if (emu.grasscutter.game.inventory.ObservedItemTestWrites
                                            .isSynchronousWrite()
                                    && ++saves == failSave)
                                throw new IllegalStateException("Write fault");
                        });
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMapper"))
                                return playerStore.datastore().getMapper();
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Write fault");
                                if (method.getName().equals("delete"))
                                    return com.mongodb.client.result.DeleteResult.acknowledged(1);
                                return method.invoke(playerStore.datastore(), args);
                            }
                            return null;
                        }));
        itemWrites =
                new emu.grasscutter.game.inventory.ObservedItemTestWrites(
                        player,
                        playerStore.datastore(),
                        DatabaseManager.getGameDatastore(),
                        doc -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Item write fault");
                        },
                        doc -> {});
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (playerStore != null) playerStore.close();
        datastore.set(null, oldStore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    void item(int id, String type, int limit) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:" + limit + "}",
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(int id, int product) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{configID:"
                                        + id
                                        + ",furnitureItemID:"
                                        + product
                                        + ",count:1,exp:60,makeTime:3600,materialItems:[]}",
                                FurnitureMakeConfigData.class));
    }

    FurnitureMakeSlotItem job(int index, int make, boolean complete) {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(index)
                        .makeId(make)
                        .beginTime(Utils.getCurrentSeconds() - (complete ? 7200 : 0))
                        .durTime(3600)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        return slot;
    }

    FurnitureMakeTakeInfo choose(int index, int make, boolean fast) {
        return FurnitureMakeTakeInfo.newBuilder()
                .setIndex(index)
                .setMakeId(make)
                .setIsFastFinish(fast)
                .build();
    }

    TakeFurnitureMakeAllRsp take(List<FurnitureMakeTakeInfo> choices) throws Exception {
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        Method method = null;
        try {
            method = player.getFurnitureManager().getClass().getMethod("takeAll", List.class);
        } catch (NoSuchMethodException absent) {
        }
        assertNotNull(method, "Batch claim is not connected");
        try {
            method.invoke(player.getFurnitureManager(), choices);
        } finally {
            player.setSession(old);
        }
        assertTrue(
                session.responses.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp));
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeAllRsp)
                        .toList();
        assertEquals(1, packets.size());
        return TakeFurnitureMakeAllRsp.parseFrom(packets.get(0).getData());
    }

    List<FurnitureMakeTakeInfo> two() {
        return List.of(choose(0, 38, false), choose(1, 39, false));
    }

    void ready() {
        job(0, 38, true);
        job(1, 39, true);
    }

    Object pending() {
        try {
            return player.home.getClass().getMethod("getPendingFurnitureBulk").invoke(player.home);
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    void untouched() {
        assertEquals(0, player.grants);
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
        assertEquals(0, player.missions);
    }

    @Test
    void selectedFinishedJobsProduceOneBatchResponse() throws Exception {
        ready();
        var response = take(two());
        assertEquals(0, response.getRetcode());
        assertEquals(2, response.getOutputItemListList().size());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(1, player.inventory.getItemCountById(360102));
        assertEquals(120, player.home.getExp());
        assertEquals(2, player.missions);
        assertNull(pending());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void unselectedJobsStayInTheQueue() throws Exception {
        ready();
        var other = job(2, 38, false);
        assertEquals(0, take(two()).getRetcode());
        assertEquals(List.of(other), player.home.getFurnitureMakeSlotItemList());
    }

    @Test
    void sameFurnitureIsAggregatedAndTrustAwardedOnce() throws Exception {
        job(0, 38, true);
        job(1, 38, true);
        var response = take(List.of(choose(0, 38, false), choose(1, 38, false)));
        assertEquals(0, response.getRetcode());
        assertEquals(1, response.getOutputItemListList().size());
        assertEquals(2, response.getOutputItemList(0).getCount());
        assertEquals(60, player.home.getExp());
        assertEquals(2, player.missions);
    }

    @Test
    void unfinishedSelectionWithoutFastFinishRejectsTheWholeBatch() throws Exception {
        job(0, 38, true);
        job(1, 39, false);
        assertNotEquals(0, take(two()).getRetcode());
        untouched();
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void invalidLaterRecipeCannotGrantTheEarlierOne() throws Exception {
        ready();
        GameData.getFurnitureMakeConfigDataMap().remove(39);
        assertNotEquals(0, take(two()).getRetcode());
        untouched();
    }

    @Test
    void allVialsAreCheckedBeforeAnyGrant() throws Exception {
        job(0, 38, false);
        job(1, 39, false);
        assertTrue(player.inventory.addItem(107013, 1));
        assertNotEquals(0, take(List.of(choose(0, 38, true), choose(1, 39, true))).getRetcode());
        untouched();
        assertEquals(1, player.inventory.getItemCountById(107013));
    }

    @Test
    void eachUnfinishedSelectedJobPaysOneVial() throws Exception {
        job(0, 38, false);
        job(1, 39, false);
        assertTrue(player.inventory.addItem(107013, 2));
        assertEquals(0, take(List.of(choose(0, 38, true), choose(1, 39, true))).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(107013));
    }

    @Test
    void staleFastFlagOnCompletedJobDoesNotSpendAnotherVial() throws Exception {
        job(0, 38, true);
        job(1, 39, false);
        assertTrue(player.inventory.addItem(107013, 1));
        assertEquals(0, take(List.of(choose(0, 38, true), choose(1, 39, true))).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(107013));
    }

    @Test
    void aggregateOutputLimitIsCheckedBeforeAnyGrant() throws Exception {
        job(0, 38, true);
        job(1, 38, true);
        assertTrue(player.inventory.addItem(360101, 99));
        player.grants = 0;
        assertNotEquals(0, take(List.of(choose(0, 38, false), choose(1, 38, false))).getRetcode());
        assertEquals(0, player.grants);
        assertEquals(99, player.inventory.getItemCountById(360101));
    }

    @Test
    void missingLaterSlotCannotConsumeAnEarlierJob() throws Exception {
        ready();
        assertNotEquals(0, take(List.of(choose(0, 38, false), choose(9, 39, false))).getRetcode());
        untouched();
    }

    @Test
    void duplicateRequestedIndexCannotClaimTwice() throws Exception {
        ready();
        assertNotEquals(0, take(List.of(choose(0, 38, false), choose(0, 38, false))).getRetcode());
        untouched();
    }

    @Test
    void negativeIndexCannotReachTheClaimLoop() throws Exception {
        ready();
        assertNotEquals(0, take(List.of(choose(-1, 38, false))).getRetcode());
        untouched();
    }

    @Test
    void foreignHomeCannotBeClaimed() throws Exception {
        ready();
        player.home.setOwnerUid(9);
        assertNotEquals(0, take(two()).getRetcode());
        untouched();
    }

    @Test
    void noHomeReturnsOneFailure() throws Exception {
        player.home = null;
        assertNotEquals(0, take(two()).getRetcode());
    }

    @Test
    void nullAndEmptySelectionsCannotInventAClaim() throws Exception {
        ready();
        assertNotEquals(0, take(null).getRetcode());
        assertNotEquals(0, take(List.of()).getRetcode());
        untouched();
    }

    @Test
    void nullSelectionElementReturnsFailure() throws Exception {
        ready();
        assertNotEquals(0, take(Arrays.asList(choose(0, 38, false), null)).getRetcode());
        untouched();
    }

    @Test
    void corruptRemainingQueueIsRejectedBeforeAnyGrant() throws Exception {
        ready();
        player.home.getFurnitureMakeSlotItemList().add(null);
        assertNotEquals(0, take(two()).getRetcode());
        untouched();
    }

    @Test
    void refusedSecondGrantKeepsDurableBatchProgress() throws Exception {
        ready();
        player.rejectId = 360102;
        assertNotEquals(0, take(two()).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
        assertNotNull(pending());
        int count = player.grants;
        player.rejectId = 0;
        assertNotEquals(0, take(two()).getRetcode());
        assertEquals(count, player.grants);
    }

    @Test
    void pretendGrantCannotCompleteTheBatch() throws Exception {
        ready();
        player.pretend = true;
        assertNotEquals(0, take(two()).getRetcode());
        assertNotNull(pending());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void reentryCannotStartAnotherBatch() throws Exception {
        ready();
        final int[] ret = {0};
        player.afterGrant =
                () -> {
                    try {
                        ret[0] = take(two()).getRetcode();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, take(two()).getRetcode());
        assertNotEquals(0, ret[0]);
        assertEquals(1, player.inventory.getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void interruptedWritesCannotReplayAnyBatchGrant(int stage) throws Exception {
        ready();
        failSave = stage;
        assertNotEquals(0, take(two()).getRetcode());
        assertNotNull(pending());
        int first = player.inventory.getItemCountById(360101),
                second = player.inventory.getItemCountById(360102),
                missions = player.missions;
        failSave = 0;
        assertNotEquals(0, take(two()).getRetcode());
        assertEquals(first, player.inventory.getItemCountById(360101));
        assertEquals(second, player.inventory.getItemCountById(360102));
        assertEquals(missions, player.missions);
    }

    @Test
    void pendingBatchBlocksIndividualClaimsAndNewCrafting() throws Exception {
        ready();
        player.rejectId = 360102;
        assertNotEquals(0, take(two()).getRetcode());
        player.rejectId = 0;
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().take(1, 39, false);
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, TakeFurnitureMakeRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360102));
    }

    @Test
    void batchProgressSurvivesMongoReload() throws Exception {
        ready();
        player.rejectId = 360102;
        assertNotEquals(0, take(two()).getRetcode());
        assertNotNull(pending());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture178_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                player.home.setPlayer(player);
                assertNotNull(pending());
                var entries =
                        (List<?>) pending().getClass().getMethod("getEntries").invoke(pending());
                assertEquals(2, entries.size());
                assertEquals(
                        true,
                        entries.get(0).getClass().getMethod("isCompleted").invoke(entries.get(0)));
                player.rejectId = 0;
                assertNotEquals(0, take(two()).getRetcode());
                assertEquals(1, player.inventory.getItemCountById(360101));
                assertEquals(0, player.inventory.getItemCountById(360102));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture178_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void nativeBatchHandlerPreservesTheSelections() throws Exception {
        ready();
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.recv.HandlerTakeFurnitureMakeAllReq");
        } catch (ClassNotFoundException absent) {
        }
        assertNotNull(type);
        var handler = (PacketHandler) type.getConstructor().newInstance();
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        handler.handle(
                session,
                new byte[0],
                TakeFurnitureMakeAllReq.newBuilder()
                        .addAllTakeInfoList(two())
                        .build()
                        .toByteArray());
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeAllRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, TakeFurnitureMakeAllRsp.parseFrom(packet.getData()).getRetcode());
    }

    @Test
    void malformedNativeBatchCannotGrantAnything() throws Exception {
        ready();
        Class<?> type =
                Class.forName("emu.grasscutter.server.packet.recv.HandlerTakeFurnitureMakeAllReq");
        var handler = (PacketHandler) type.getConstructor().newInstance();
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        handler.handle(session, new byte[0], new byte[] {0x4a, (byte) 0x80});
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeAllRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, TakeFurnitureMakeAllRsp.parseFrom(packet.getData()).getRetcode());
        untouched();
    }

    @Test
    void invalidAggregateTrustIsRejectedBeforeAnyGrant() throws Exception {
        ready();
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        39,
                        gson.fromJson(
                                "{configID:39,furnitureItemID:360102,count:1,exp:2147483647,makeTime:3600,materialItems:[]}",
                                FurnitureMakeConfigData.class));
        assertNotEquals(0, take(two()).getRetcode());
        untouched();
    }

    @Test
    void pendingBatchBlocksNewCraftingAndCancellation() throws Exception {
        ready();
        player.rejectId = 360102;
        assertNotEquals(0, take(two()).getRetcode());
        player.rejectId = 0;
        player.getUnlockedFurniture().add(360101);
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().startMake(38, 0);
        var start =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(
                0,
                emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp
                        .parseFrom(start.getData())
                        .getRetcode());
        session.responses.clear();
        player.getFurnitureManager().cancel(1, 39);
        var cancel =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeCancelRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(
                0,
                emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp
                        .parseFrom(cancel.getData())
                        .getRetcode());
    }

    @Test
    void nativeMaximumSizedQueueCanBeClaimed() throws Exception {
        for (int i = 0; i < 15; i++) job(i, 38, true);
        var choices =
                java.util.stream.IntStream.range(0, 15)
                        .mapToObj(i -> choose(i, 38, false))
                        .toList();
        assertEquals(0, take(choices).getRetcode());
        assertEquals(15, player.inventory.getItemCountById(360101));
        assertEquals(60, player.home.getExp());
        assertEquals(15, player.missions);
    }

    @Test
    void bothJobsUseTheirSavedPlansAfterRecipeRemoval() throws Exception {
        player.getUnlockedFurniture().add(360101);
        player.getUnlockedFurniture().add(360102);
        player.getFurnitureManager().startMake(38, 0);
        player.getFurnitureManager().startMake(39, 0);
        for (var slot : player.home.getFurnitureMakeSlotItemList())
            slot.setBeginTime(Utils.getCurrentSeconds() - 7200);
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, take(two()).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(1, player.inventory.getItemCountById(360102));
    }

    @Test
    void completedBatchCannotBeClaimedAgain() throws Exception {
        ready();
        assertEquals(0, take(two()).getRetcode());
        int grants = player.grants;
        assertNotEquals(0, take(two()).getRetcode());
        assertEquals(grants, player.grants);
    }

    @Test
    void currentRecipesCanBeStartedAndClaimedInPairs() throws Exception {
        var root =
                java.nio.file.Path.of(
                        System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"),
                        "ExcelBinOutput");
        for (String table : List.of("Material", "HomeWorldFurniture"))
            for (var data :
                    gson.fromJson(
                            java.nio.file.Files.readString(
                                    root.resolve(table + "ExcelConfigData.json")),
                            ItemData[].class)) {
                data.onLoad();
                GameData.getItemDataMap().put(data.getId(), data);
            }
        for (var data :
                gson.fromJson(
                        java.nio.file.Files.readString(
                                root.resolve("HomeworldLevelExcelConfigData.json")),
                        HomeWorldLevelData[].class))
            GameData.getHomeWorldLevelDataMap().put(data.getId(), data);
        int verified = 0;
        for (var data :
                gson.fromJson(
                        java.nio.file.Files.readString(
                                root.resolve("FurnitureMakeExcelConfigData.json")),
                        FurnitureMakeConfigData[].class)) {
            var output = GameData.getItemDataMap().get(data.getFurnitureItemID());
            if (output.getStackLimit() == 0) continue;
            data.onLoad();
            GameData.getFurnitureMakeConfigDataMap().put(data.getId(), data);
            player.home.setLevel(3);
            player.home.setExp(0);
            player.getUnlockedFurniture().add(output.getId());
            for (var cost : data.getMaterialItems()) {
                int required = cost.getCount() * 2 + 1,
                        current = player.inventory.getItemCountById(cost.getId());
                if (current < required)
                    assertTrue(player.inventory.addItem(cost.getId(), required - current));
            }
            for (int i = 0; i < 2; i++) player.getFurnitureManager().startMake(data.getId(), 0);
            assertEquals(
                    2, player.home.getFurnitureMakeSlotItemList().size(), "recipe " + data.getId());
            var selections = new ArrayList<FurnitureMakeTakeInfo>();
            for (var slot : player.home.getFurnitureMakeSlotItemList()) {
                slot.setBeginTime(Utils.getCurrentSeconds() - slot.getDurTime() - 1);
                selections.add(choose(slot.getIndex(), slot.getMakeId(), false));
            }
            int before = player.inventory.getItemCountById(output.getId());
            var response = take(selections);
            assertEquals(0, response.getRetcode(), "recipe " + data.getId());
            assertEquals(
                    before + data.getCount() * 2,
                    player.inventory.getItemCountById(output.getId()));
            assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
            verified++;
        }
        assertEquals(1396, verified);
    }
}
