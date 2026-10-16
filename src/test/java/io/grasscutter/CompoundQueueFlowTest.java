package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.cooking.*;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.PlayerCompoundMaterialRspOuterClass.PlayerCompoundMaterialRsp;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.net.proto.TakeCompoundOutputRspOuterClass.TakeCompoundOutputRsp;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Actual queue/payment/inventory behavior; persistence confirmation faults are controlled. */
class CompoundQueueFlowTest {
    private AbilityTestFixture environment;
    private ProcessPlayer player;
    private final Gson gson = new Gson();
    private Map<Integer, CompoundData> previousRecipes;
    private Map<Integer, ItemData> previousItems;
    private Object previousDatastore;
    private java.lang.reflect.Field datastore;
    private GuardedPlayerTestStore playerStore;
    private int saves, failSave;

    static class ProcessPlayer extends InvestigationRewardTest.ClaimPlayer {
        boolean refuse, throwGrant;
        @dev.morphia.annotations.Transient Runnable afterGrant;
        final Inventory processInventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(ItemParamData item, ActionReason reason) {
                        if (refuse) return false;
                        if (throwGrant) throw new IllegalStateException("Grant fault");
                        boolean result = super.addItem(item, reason);
                        var callback = afterGrant;
                        afterGrant = null;
                        if (result && callback != null) callback.run();
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return processInventory;
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        previousRecipes = new HashMap<>(GameData.getCompoundDataMap());
        previousItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getCompoundDataMap().clear();
        GameData.getItemDataMap().clear();
        recipe(1001, 1, 1, 1, 99);
        recipe(1002, 2, 1, 1, 99);
        for (int id : List.of(100017, 110001, 110002))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                                    ItemData.class));
        CookingCompoundManager.initialize();
        player = new ProcessPlayer();
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        playerStore =
                new GuardedPlayerTestStore(
                        player,
                        () -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Confirmation fault");
                        });
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMapper"))
                                return playerStore.datastore().getMapper();
                            if (!method.getName().equals("save")) return null;
                            if (args.length == 2 && ++saves == failSave)
                                throw new IllegalStateException("Confirmation fault");
                            return args[0];
                        }));
        assertTrue(player.getInventory().addItem(100017, 100));
        player.packets.clear();
    }

    @AfterEach
    void close() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, previousDatastore);
        if (playerStore != null) playerStore.close();
        GameData.getCompoundDataMap().clear();
        GameData.getCompoundDataMap().putAll(previousRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(previousItems);
        CookingCompoundManager.initialize();
        if (environment != null) environment.close();
    }

    private void recipe(int id, int group, int input, int output, int limit) {
        GameData.getCompoundDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",groupID:"
                                        + group
                                        + ",isDefaultUnlocked:true,costTime:60,queueSize:"
                                        + limit
                                        + ",inputVec:[{id:100017,count:"
                                        + input
                                        + "},{id:0,count:0}],outputVec:[{id:"
                                        + (id == 1001 ? 110001 : 110002)
                                        + ",count:"
                                        + output
                                        + "},{id:0,count:0}]}",
                                CompoundData.class));
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private int enqueue(int id, int count) throws Exception {
        synchronized (player.getCookingCompoundManager()) {
            player.packets.clear();
            player.getCookingCompoundManager()
                    .handlePlayerCompoundMaterialReq(
                            PlayerCompoundMaterialReq.newBuilder()
                                    .setCompoundId(id)
                                    .setCount(count)
                                    .build());
            var packet =
                    player.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCompoundMaterialRsp)
                            .findFirst()
                            .orElseThrow();
            return PlayerCompoundMaterialRsp.parseFrom(packet.getData()).getRetcode();
        }
    }

    private void ready(int id, int count) {
        player.getActiveCookCompounds()
                .put(
                        id,
                        new ActiveCookCompoundData(
                                id, 60, count, Utils.getCurrentSeconds() - count * 60 - 1));
    }

    private TakeCompoundOutputRsp take(int group, int id, boolean all) throws Exception {
        player.getCookingCompoundManager()
                .handleTakeCompoundOutputReq(
                        TakeCompoundOutputReq.newBuilder()
                                .setCompoundGroupId(group)
                                .setCompoundId(id)
                                .setIsClaimAll(all)
                                .build());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return TakeCompoundOutputRsp.parseFrom(packet.getData());
    }

    @Test
    void enqueueConsumesExactOriginalMaterialsAndCreatesQueue() throws Exception {
        assertEquals(0, enqueue(1001, 3));
        assertEquals(97, stock(100017));
        assertEquals(3, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void invalidAmountsCannotChangeStockOrQueue(int count) throws Exception {
        assertNotEquals(0, enqueue(1001, count));
        assertEquals(100, stock(100017));
        assertTrue(player.getActiveCookCompounds().isEmpty());
    }

    @Test
    void firstQueueCannotExceedItsLimit() throws Exception {
        assertNotEquals(0, enqueue(1001, 100));
        assertEquals(100, stock(100017));
        assertTrue(player.getActiveCookCompounds().isEmpty());
    }

    @Test
    void missingRecipeCannotConsumeMaterials() throws Exception {
        GameData.getCompoundDataMap().remove(1001);
        assertNotEquals(0, enqueue(1001, 1));
        assertEquals(100, stock(100017));
    }

    @Test
    void multipliedCostCannotOverflowIntoFreeProcessing() throws Exception {
        recipe(1001, 1, Integer.MAX_VALUE, 1, 99);
        assertNotEquals(0, enqueue(1001, 2));
        assertEquals(100, stock(100017));
    }

    @Test
    void repeatedIngredientRowsAreAggregatedBeforePayment() throws Exception {
        GameData.getCompoundDataMap()
                .put(
                        1001,
                        gson.fromJson(
                                "{id:1001,groupID:1,isDefaultUnlocked:true,costTime:60,queueSize:99,inputVec:[{id:100017,count:60},{id:100017,count:60}],outputVec:[{id:110001,count:1}]}",
                                CompoundData.class));
        assertNotEquals(0, enqueue(1001, 1));
        assertEquals(100, stock(100017));
        assertTrue(player.getActiveCookCompounds().isEmpty());
    }

    @Test
    void existingQueueLimitCannotBeBypassed() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 60, 98, Utils.getCurrentSeconds()));
        assertNotEquals(0, enqueue(1001, 2));
        assertEquals(98, player.getActiveCookCompounds().get(1001).getTotalCount());
        assertEquals(100, stock(100017));
    }

    @Test
    void completedItemsRemainReadyWhenNewItemsAreAppended() throws Exception {
        ready(1001, 2);
        assertEquals(0, enqueue(1001, 2));
        var queue = player.getActiveCookCompounds().get(1001);
        assertEquals(4, queue.getTotalCount());
        assertEquals(2, queue.getOutputCount(Utils.getCurrentSeconds()));
    }

    @Test
    void groupClaimDeliversExactReadyItemsAndRemovesFinishedQueue() throws Exception {
        ready(1001, 3);
        var result = take(1, 0, false);
        assertEquals(0, result.getRetcode());
        assertEquals(3, stock(110001));
        assertEquals(3, result.getItemList(0).getCount());
        assertTrue(player.getActiveCookCompounds().isEmpty());
    }

    @Test
    void recipeSelectorDoesNotClaimOtherRecipesInSameGroup() throws Exception {
        recipe(1002, 1, 1, 1, 99);
        CookingCompoundManager.initialize();
        ready(1001, 2);
        ready(1002, 3);
        assertEquals(0, take(1, 1001, false).getRetcode());
        assertEquals(2, stock(110001));
        assertEquals(0, stock(110002));
        assertTrue(player.getActiveCookCompounds().containsKey(1002));
    }

    @Test
    void claimAllCollectsAllFinishedGroups() throws Exception {
        ready(1001, 2);
        ready(1002, 3);
        assertEquals(0, take(0, 0, true).getRetcode());
        assertEquals(2, stock(110001));
        assertEquals(3, stock(110002));
        assertTrue(player.getActiveCookCompounds().isEmpty());
    }

    @Test
    void unfinishedQueueIsNotConsumed() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 60, 3, Utils.getCurrentSeconds()));
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(3, player.getActiveCookCompounds().get(1001).getTotalCount());
        assertEquals(0, stock(110001));
    }

    @Test
    void fullBagCannotConsumeOutputQueue() throws Exception {
        ready(1001, 2);
        player.getInventory()
                .getInventoryTypes()
                .put(ItemType.ITEM_MATERIAL.getValue(), new MaterialInventoryTab(0));
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void stackClampCannotConsumeOutputQueue() throws Exception {
        ready(1001, 2);
        assertTrue(player.getInventory().addItem(110001, 999));
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(999, stock(110001));
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void multipliedOutputCannotOverflow() throws Exception {
        recipe(1001, 1, 1, Integer.MAX_VALUE, 99);
        ready(1001, 2);
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(0, stock(110001));
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void missingOutputDefinitionCannotConsumeQueue() throws Exception {
        ready(1001, 2);
        GameData.getItemDataMap().remove(110001);
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void partialClaimLeavesOnlyUnfinishedItems() throws Exception {
        player.getActiveCookCompounds()
                .put(
                        1001,
                        new ActiveCookCompoundData(1001, 60, 3, Utils.getCurrentSeconds() - 121));
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, stock(110001));
        assertEquals(1, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void repeatedClaimCannotGrantAgain() throws Exception {
        ready(1001, 2);
        assertEquals(0, take(1, 0, false).getRetcode());
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, stock(110001));
    }

    @Test
    void refusedGrantStaysUnclaimableAndCannotBeRetried() throws Exception {
        ready(1001, 2);
        player.refuse = true;
        assertNotEquals(0, take(1, 0, false).getRetcode());
        player.refuse = false;
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(0, stock(110001));
    }

    @Test
    void reservationFailureNeverGrants() throws Exception {
        ready(1001, 2);
        failSave = 1;
        assertNotEquals(0, take(1, 0, false).getRetcode());
        assertEquals(0, stock(110001));
        failSave = 0;
        assertNotEquals(0, take(1, 0, false).getRetcode());
    }

    @Test
    void grantCallbackCannotCollectReservedQueueAgain() throws Exception {
        ready(1001, 2);
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, take(1, 0, false).getRetcode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, stock(110001));
    }

    @Test
    void grantCallbackCannotAppendToReservedQueue() throws Exception {
        ready(1001, 2);
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, enqueue(1001, 1));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(100, stock(100017));
    }

    @Test
    void actualFlourResourceRunsThroughEnqueueAndCollection() throws Exception {
        var recipe =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/CompoundExcelConfigData.json"),
                                CompoundData.class)
                        .stream()
                        .filter(r -> r.getId() == 1001)
                        .findFirst()
                        .orElseThrow();
        GameData.getCompoundDataMap().put(1001, recipe);
        assertEquals(0, enqueue(1001, 3));
        ready(1001, 3);
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(97, stock(100017));
        assertEquals(3, stock(110001));
    }

    @Test
    void elapsedTimeUsesLongArithmetic() throws Exception {
        var queue = new ActiveCookCompoundData(1001, 60, 3, -1700000000);
        assertEquals(3, queue.getOutputCount(1700000000));
    }

    @Test
    void legacyMissingCostTimeRemainsCollectable() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 0, 3, Utils.getCurrentSeconds()));
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(3, stock(110001));
    }

    @Test
    void parallelEnqueueRespectsLimitAndActualPayments() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> enqueue(1001, 30)));
            int successes = 0;
            for (var job : jobs) if (job.get(15, TimeUnit.SECONDS) == 0) successes++;
            assertEquals(3, successes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(90, player.getActiveCookCompounds().get(1001).getTotalCount());
        assertEquals(10, stock(100017));
    }

    private void request(int opcode, byte[] body) throws Exception {
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new GameServerPacketHandler(PacketHandler.class).handle(session, opcode, new byte[0], body);
    }

    @Test
    void registeredRequestsEnqueueAndCollectActualCostsAndOutput() throws Exception {
        player.packets.clear();
        request(
                PacketOpcodes.PlayerCompoundMaterialReq,
                PlayerCompoundMaterialReq.newBuilder()
                        .setCompoundId(1001)
                        .setCount(2)
                        .build()
                        .toByteArray());
        assertEquals(98, stock(100017));
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
        ready(1001, 2);
        request(
                PacketOpcodes.TakeCompoundOutputReq,
                TakeCompoundOutputReq.newBuilder().setCompoundGroupId(1).build().toByteArray());
        var result =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(0, TakeCompoundOutputRsp.parseFrom(result.getData()).getRetcode());
        assertEquals(2, stock(110001));
    }

    @Test
    void truncatedEnqueueGetsFailureWithoutConsumingMaterial() throws Exception {
        player.packets.clear();
        request(PacketOpcodes.PlayerCompoundMaterialReq, new byte[] {8, (byte) 0x80});
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCompoundMaterialRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, PlayerCompoundMaterialRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(100, stock(100017));
    }

    @Test
    void truncatedClaimGetsFailureAndPreservesQueue() throws Exception {
        ready(1001, 2);
        player.packets.clear();
        request(PacketOpcodes.TakeCompoundOutputReq, new byte[] {8, (byte) 0x80});
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, TakeCompoundOutputRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void appendingToLegacyInstantQueueKeepsOldOutputReadyAndTimesNewWork() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 0, 2, Utils.getCurrentSeconds()));
        assertEquals(0, enqueue(1001, 1));
        var queue = player.getActiveCookCompounds().get(1001);
        assertEquals(3, queue.getTotalCount());
        assertEquals(2, queue.getOutputCount(Utils.getCurrentSeconds()));
        assertEquals(0, take(1, 0, false).getRetcode());
        assertEquals(2, stock(110001));
        assertEquals(1, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void negativePersistedProcessingTimeCannotAppendOrConsumeMaterial() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, -1, 2, Utils.getCurrentSeconds()));
        assertNotEquals(0, enqueue(1001, 1));
        assertEquals(100, stock(100017));
    }

    @Test
    void unrepresentableMigrationTimeFailsBeforePayment() throws Exception {
        GameData.getCompoundDataMap()
                .put(
                        1001,
                        gson.fromJson(
                                "{id:1001,groupID:1,isDefaultUnlocked:true,costTime:2147483647,queueSize:99,inputVec:[{id:100017,count:1}],outputVec:[{id:110001,count:1}]}",
                                CompoundData.class));
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 0, 2, Utils.getCurrentSeconds()));
        assertNotEquals(0, enqueue(1001, 1));
        assertEquals(100, stock(100017));
    }

    @dev.morphia.annotations.Entity(value = "compound_queue_test", useDiscriminator = false)
    static class StoredQueue {
        @dev.morphia.annotations.Id String id;
        ActiveCookCompoundData queue;
    }

    @Test
    void reservedQueueSurvivesMongoReloadAndCannotRegrant() throws Exception {
        ready(1001, 2);
        player.refuse = true;
        assertNotEquals(0, take(1, 0, false).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database =
                "lunagc_test_compound142_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredQueue.class);
            try {
                var record = new StoredQueue();
                record.id = "player1";
                record.queue = player.getActiveCookCompounds().get(1001);
                store.save(record);
                var loaded = store.find(StoredQueue.class).first();
                assertNotNull(loaded);
                assertEquals(1, loaded.queue.getClaimState());
                player.getActiveCookCompounds().put(1001, loaded.queue);
                player.refuse = false;
                assertNotEquals(0, take(1, 0, false).getRetcode());
                assertEquals(0, stock(110001));
            } finally {
                assertTrue(database.startsWith("lunagc_test_compound142_"));
                client.getDatabase(database).drop();
            }
        }
    }
}
