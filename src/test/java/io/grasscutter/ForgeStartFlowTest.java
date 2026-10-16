package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.forging.*;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.ForgeStartRspOuterClass.ForgeStartRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Start requests must confirm a valid queue and exact costs before reporting success. */
class ForgeStartFlowTest {
    private AbilityTestFixture environment;
    private ForgePlayer player;
    private GameServerPacketHandler dispatcher;
    private Map<Integer, ForgeData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, DropTableData> oldDrops;
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private GuardedPlayerTestStore playerStore;
    private int confirmations, failConfirmation;
    private final Gson gson = new Gson();
    private boolean oldResinUsage;

    static class ForgePlayer extends InvestigationRewardTest.ClaimPlayer {
        transient int resinMissionProgress;
        final transient emu.grasscutter.game.battlepass.BattlePassManager battlePass =
                new emu.grasscutter.game.battlepass.BattlePassManager(this) {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {
                        if (type == WatcherTriggerType.TRIGGER_COST_MATERIAL && param == 106)
                            resinMissionProgress += progress;
                    }

                    @Override
                    public void triggerMission(String type, int param, int progress) {}
                };

        @Override
        public emu.grasscutter.game.battlepass.BattlePassManager getBattlePassManager() {
            return battlePass;
        }

        transient boolean refusePay, throwAfterPay;
        transient Runnable afterPay;
        final transient Inventory forgeInventory =
                new Inventory(this) {
                    @Override
                    public boolean payItems(
                            Iterable<ItemParamData> costs, int quantity, ActionReason reason) {
                        if (refusePay) return false;
                        boolean result = super.payItems(costs, quantity, reason);
                        if (result) {
                            var callback = afterPay;
                            afterPay = null;
                            if (callback != null) callback.run();
                            if (throwAfterPay) throw new IllegalStateException("Payment fault");
                        }
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return forgeInventory;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldResinUsage = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        oldRecipes = new HashMap<>(GameData.getForgeDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldDrops = new HashMap<>(GameData.getDropTableDataMap());
        GameData.getForgeDataMap().clear();
        GameData.getItemDataMap().clear();
        recipe("{}");
        item(101001, "ITEM_MATERIAL");
        item(104011, "ITEM_MATERIAL");
        item(202, "ITEM_VIRTUAL");
        player = new ForgePlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.setMora(1000);
        player.setForgePoints(300000);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        playerStore = new GuardedPlayerTestStore(player, () -> {
            if (++confirmations == failConfirmation) throw new IllegalStateException("Confirmation fault");
        });
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMapper")) return playerStore.datastore().getMapper();
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++confirmations == failConfirmation)
                                    throw new IllegalStateException("Confirmation fault");
                                return method.getName().equals("delete")
                                        ? com.mongodb.client.result.DeleteResult.acknowledged(1)
                                        : args[0];
                            }
                            return null;
                        }));
        assertTrue(player.getInventory().addItem(101001, 100));
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        if (playerStore != null) playerStore.close();
        GameData.getForgeDataMap().clear();
        GameData.getForgeDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getDropTableDataMap().clear();
        GameData.getDropTableDataMap().putAll(oldDrops);
        environment.close();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResinUsage;
    }

    private void recipe(String overrides) {
        var json =
                com.google.gson.JsonParser.parseString(
                                "{id:11001,playerLevel:2,isDefaultShow:true,forgeTime:3,queueNum:40,forgePoint:400,scoinCost:5,resultItemId:104011,resultItemCount:1,showItemId:104011,materialItems:[{id:101001,count:2},{id:0,count:0}]}"
                                    + " ")
                        .getAsJsonObject();
        com.google.gson.JsonParser.parseString(overrides)
                .getAsJsonObject()
                .entrySet()
                .forEach(e -> json.add(e.getKey(), e.getValue()));
        GameData.getForgeDataMap().put(11001, gson.fromJson(json, ForgeData.class));
    }

    private void item(int id, String type) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'"
                                        + type
                                        + "',materialType:'MATERIAL_NONE',stackLimit:1000}",
                                ItemData.class));
    }

    private int stock() {
        return player.getInventory().getItemCountById(101001);
    }

    private void request(int opcode, byte[] body) {
        dispatcher.handle(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player),
                opcode,
                new byte[0],
                body);
    }

    private void sendStart(int id, int count, int avatar) {
        request(
                PacketOpcodes.ForgeStartReq,
                ForgeStartReq.newBuilder()
                        .setForgeId(id)
                        .setForgeCount(count)
                        .setAvatarId(avatar)
                        .build()
                        .toByteArray());
    }

    private int start(int count) throws Exception {
        sendStart(11001, count, 0);
        return lastStart();
    }

    private int lastStart() throws Exception {
        return ForgeStartRsp.parseFrom(
                        player.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData())
                .getRetcode();
    }

    private static int state(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getStartState").invoke(queue));
    }

    private void unchanged() {
        assertEquals(100, stock());
        assertEquals(1000, player.getMora());
        assertEquals(300000, player.getForgePoints());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void validStartConfirmsExactCostsAndQueue() throws Exception {
        assertEquals(0, start(2));
        assertEquals(96, stock());
        assertEquals(990, player.getMora());
        assertEquals(299200, player.getForgePoints());
        assertEquals(1, player.getActiveForges().size());
        var queue = player.getActiveForges().get(0);
        assertEquals(11001, queue.getForgeId());
        assertEquals(2, queue.getCount());
        assertEquals(3, queue.getForgeTime());
        assertEquals(0, state(queue));
        assertEquals(4, confirmations);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 41})
    void invalidCountsNeverChargeOrQueue(int count) throws Exception {
        assertNotEquals(0, start(count));
        unchanged();
    }

    @Test
    void lockedDiagramCannotBeForged() throws Exception {
        recipe("{isDefaultShow:false}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void unlockedDiagramCanBeForged() throws Exception {
        recipe("{isDefaultShow:false}");
        player.getUnlockedForgingBlueprints().add(11001);
        assertEquals(0, start(1));
        assertEquals(98, stock());
    }

    @Test
    void rankGateAlsoAppliesToUnlockedDiagram() throws Exception {
        recipe("{isDefaultShow:false,playerLevel:46}");
        player.getUnlockedForgingBlueprints().add(11001);
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void unknownRecipeFailsWithoutCosts() throws Exception {
        sendStart(123, 1, 0);
        assertNotEquals(0, lastStart());
        unchanged();
    }

    @Test
    void unknownAssistantFailsWithoutCosts() throws Exception {
        sendStart(11001, 1, 10000073);
        assertNotEquals(0, lastStart());
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidRecipeTimeFailsBeforeCosts(int time) throws Exception {
        recipe("{forgeTime:" + time + "}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void queueQuantityLimitIsRequired() throws Exception {
        recipe("{queueNum:0}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void overflowingPointCostNeverCreatesPoints() throws Exception {
        recipe("{forgePoint:2147483647}");
        assertNotEquals(0, start(2));
        unchanged();
    }

    @Test
    void overflowingMoraCostNeverCreatesMora() throws Exception {
        recipe("{scoinCost:2147483647}");
        assertNotEquals(0, start(2));
        unchanged();
    }

    @Test
    void negativePointCostFailsBeforeCosts() throws Exception {
        recipe("{forgePoint:-1}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void negativeMoraCostFailsBeforeCosts() throws Exception {
        recipe("{scoinCost:-1}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void overflowingTimestampFailsBeforeCosts() throws Exception {
        recipe("{forgeTime:2147483647}");
        assertNotEquals(0, start(2));
        unchanged();
    }

    @Test
    void missingProductCannotConsumeInputs() throws Exception {
        GameData.getItemDataMap().remove(104011);
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void missingInputDefinitionCannotConsumeInputs() throws Exception {
        GameData.getItemDataMap().remove(101001);
        assertNotEquals(0, start(1));
        assertEquals(100, player.getInventory().getFirstItem(101001).getCount());
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void negativeInputCannotAddMaterials() throws Exception {
        recipe("{materialItems:[{id:101001,count:-1}]}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void duplicateInputsAreAggregatedBeforeCheckingInventory() throws Exception {
        recipe("{materialItems:[{id:101001,count:60},{id:101001,count:60}]}");
        assertNotEquals(0, start(1));
        unchanged();
    }

    @Test
    void insufficientPointsCannotConsumeMaterials() throws Exception {
        player.setForgePoints(399);
        assertNotEquals(0, start(1));
        assertEquals(100, stock());
        assertEquals(1000, player.getMora());
        assertEquals(399, player.getForgePoints());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void fullQueuesCannotConsumeMaterials() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 2);
        assertEquals(0, start(1));
        int ore = stock(), mora = player.getMora();
        assertNotEquals(0, start(1));
        assertEquals(ore, stock());
        assertEquals(mora, player.getMora());
        assertEquals(1, player.getActiveForges().size());
    }

    @Test
    void simultaneousStartsRespectCapacityAndExactCosts() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs =
                    List.of(
                            pool.submit(() -> sendStart(11001, 1, 0)),
                            pool.submit(() -> sendStart(11001, 1, 0)));
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, player.getActiveForges().size());
        assertEquals(98, stock());
        assertEquals(995, player.getMora());
        assertEquals(299600, player.getForgePoints());
        assertEquals(
                2,
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void confirmationFailureLeavesPendingQueueAndBlocksReplay(int stage) throws Exception {
        failConfirmation = stage;
        assertNotEquals(0, start(1));
        assertEquals(1, player.getActiveForges().size());
        assertEquals(1, state(player.getActiveForges().get(0)));
        int ore = stock(), mora = player.getMora(), points = player.getForgePoints();
        failConfirmation = 0;
        assertNotEquals(0, start(1));
        assertEquals(ore, stock());
        assertEquals(mora, player.getMora());
        assertEquals(points, player.getForgePoints());
        assertEquals(stage == 1 ? 100 : 98, ore);
        assertEquals(stage == 1 ? 1000 : 995, mora);
    }

    @Test
    void paymentReentryCannotStartOrCancelUnconfirmedQueue() throws Exception {
        player.afterPay =
                () -> {
                    try {
                        assertNotEquals(0, start(1));
                        request(
                                PacketOpcodes.ForgeQueueManipulateReq,
                                ForgeQueueManipulateReq.newBuilder()
                                        .setForgeQueueId(1)
                                        .setManipulateTypeValue(1)
                                        .build()
                                        .toByteArray());
                        var packet =
                                player.packets.stream()
                                        .filter(
                                                p ->
                                                        p.getOpcode()
                                                                == PacketOpcodes
                                                                        .ForgeQueueManipulateRsp)
                                        .reduce((a, b) -> b)
                                        .orElseThrow();
                        assertNotEquals(
                                0,
                                ForgeQueueManipulateRsp.parseFrom(packet.getData()).getRetcode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, start(1));
        assertEquals(1, player.getActiveForges().size());
        assertEquals(98, stock());
        assertEquals(995, player.getMora());
    }

    @Test
    void paymentFaultCannotChargeTwice() throws Exception {
        player.throwAfterPay = true;
        assertNotEquals(0, start(1));
        assertEquals(98, stock());
        player.throwAfterPay = false;
        assertNotEquals(0, start(1));
        assertEquals(98, stock());
        assertEquals(1, state(player.getActiveForges().get(0)));
    }

    @Test
    void refusedPaymentCannotReportSuccessOrAdvanceTime() throws Exception {
        player.refusePay = true;
        assertNotEquals(0, start(1));
        assertEquals(100, stock());
        assertEquals(1, state(player.getActiveForges().get(0)));
        assertEquals(
                0,
                player.getActiveForges().get(0).getFinishedCount(Utils.getCurrentSeconds() + 100));
    }

    @Test
    void malformedRequestReturnsTypedFailure() throws Exception {
        request(PacketOpcodes.ForgeStartReq, new byte[] {48, (byte) 0x80});
        assertNotEquals(0, lastStart());
        unchanged();
    }

    @Test
    void realOreRecipeAcceptsResourceLimitAndZeroFillers() throws Exception {
        var rows =
                emu.grasscutter.utils.JsonUtils.loadToList(
                        java.nio.file.Path.of(
                                "/resources/ExcelBinOutput/ForgeExcelConfigData.json"),
                        ForgeData.class);
        var data = rows.stream().filter(r -> r.getId() == 11001).findFirst().orElseThrow();
        GameData.getForgeDataMap().put(11001, data);
        assertEquals(40, data.getQueueNum());
        assertEquals(0, start(40));
        assertEquals(20, stock());
        assertEquals(800, player.getMora());
        assertEquals(284000, player.getForgePoints());
        assertEquals(40, player.getActiveForges().get(0).getCount());
    }

    private void resinRecipe() throws Exception {
        var rows =
                emu.grasscutter.utils.JsonUtils.loadToList(
                        java.nio.file.Path.of(
                                "/resources/ExcelBinOutput/ForgeExcelConfigData.json"),
                        ForgeData.class);
        var recipe = rows.stream().filter(row -> row.getId() == 12001).findFirst().orElseThrow();
        GameData.getForgeDataMap().put(12001, recipe);
        item(101004, "ITEM_MATERIAL");
        item(104013, "ITEM_MATERIAL");
        item(106, "ITEM_VIRTUAL");
        item(102, "ITEM_VIRTUAL");
        item(105, "ITEM_VIRTUAL");
        var drops =
                emu.grasscutter.utils.JsonUtils.loadToList(
                        java.nio.file.Path.of("/resources/Server/DropTableExcelConfigData.json"),
                        DropTableData.class);
        GameData.getDropTableDataMap()
                .put(
                        208001500,
                        drops.stream()
                                .filter(row -> row.getId() == 208001500)
                                .findFirst()
                                .orElseThrow());
        assertTrue(player.getInventory().addItem(101004, 10));
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 3);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
    }

    @Test
    void realResinRecipeConfirmsVirtualAndMaterialCosts() throws Exception {
        resinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        sendStart(12001, 1, 0);
        assertEquals(0, lastStart());
        assertEquals(7, player.getInventory().getItemCountById(101004));
        assertEquals(900, player.getMora());
        assertEquals(90, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(300000, player.getForgePoints());
        assertEquals(1, player.getActiveForges().size());
        assertEquals(10, player.resinMissionProgress);
    }

    @Test
    void disabledResinUsageDoesNotLeaveAnUnconfirmedQueue() throws Exception {
        resinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 0);
        sendStart(12001, 1, 0);
        assertEquals(0, lastStart());
        assertEquals(7, player.getInventory().getItemCountById(101004));
        assertEquals(900, player.getMora());
        assertEquals(0, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, state(player.getActiveForges().get(0)));
    }

    @Test
    void recipeWorldLevelGateIsCheckedBeforePayment() throws Exception {
        resinRecipe();
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 2);
        sendStart(12001, 1, 0);
        assertNotEquals(0, lastStart());
        assertEquals(10, player.getInventory().getItemCountById(101004));
        assertEquals(1000, player.getMora());
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void legacyQueueWithZeroTimeDoesNotDivideByZero() {
        var queue = new ActiveForgeData();
        queue.setCount(2);
        queue.setStartTime(100);
        assertEquals(0, queue.getFinishedCount(200));
        assertEquals(2, queue.getUnfinishedCount(200));
        assertEquals(0, queue.getNextFinishTimestamp(200));
    }

    @Test
    void futureQueueHasZeroFinishedAndNoNegativeCounts() {
        var queue = new ActiveForgeData();
        queue.setCount(2);
        queue.setForgeTime(3);
        queue.setStartTime(100);
        assertEquals(0, queue.getFinishedCount(90));
        assertEquals(2, queue.getUnfinishedCount(90));
        assertEquals(103, queue.getNextFinishTimestamp(90));
    }

    @Test
    void corruptTimestampDoesNotWrapIntoACompletedQueue() {
        var queue = new ActiveForgeData();
        queue.setCount(Integer.MAX_VALUE);
        queue.setForgeTime(Integer.MAX_VALUE);
        queue.setStartTime(100);
        assertEquals(0, queue.getTotalFinishTimestamp());
        assertEquals(0, queue.getNextFinishTimestamp(200));
        assertEquals(0, queue.getFinishedCount(200));
    }

    @dev.morphia.annotations.Entity(value = "forge_start_test", useDiscriminator = false)
    static class StoredForge {
        @dev.morphia.annotations.Id String id;
        ActiveForgeData queue;
    }

    @Test
    void pendingStartSurvivesMongoReload() throws Exception {
        failConfirmation = 1;
        assertNotEquals(0, start(1));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgestart147_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "pending";
                row.queue = player.getActiveForges().get(0);
                store.save(row);
                var loaded = store.find(StoredForge.class).first();
                assertEquals(1, state(loaded.queue));
                player.getActiveForges().set(0, loaded.queue);
                failConfirmation = 0;
                assertNotEquals(0, start(1));
                assertEquals(100, stock());
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgestart147_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void oldQueueWithoutStartReceiptDefaultsToAvailable() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgestart147_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "old";
                row.queue = new ActiveForgeData();
                row.queue.setForgeId(11001);
                row.queue.setCount(2);
                row.queue.setForgeTime(3);
                row.queue.setStartTime(Utils.getCurrentSeconds());
                store.save(row);
                client.getDatabase(name)
                        .getCollection("forge_start_test")
                        .updateOne(
                                com.mongodb.client.model.Filters.eq("_id", "old"),
                                com.mongodb.client.model.Updates.unset("queue.startState"));
                var loaded = store.find(StoredForge.class).first();
                assertEquals(0, state(loaded.queue));
                assertEquals(2, loaded.queue.getFinishedCount(Utils.getCurrentSeconds() + 6));
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgestart147_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
