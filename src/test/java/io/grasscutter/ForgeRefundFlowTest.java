package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.forging.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.ForgeStartRspOuterClass.ForgeStartRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Start and cancel real inventory queues, including historical data and persistence faults. */
class ForgeRefundFlowTest {
    private AbilityTestFixture environment;
    private RefundPlayer player;
    private GameServerPacketHandler dispatcher;
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private Map<Integer, ForgeData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private boolean oldResinUsage;
    private int saves, failSave;
    private final Gson gson = new Gson();

    static class RefundPlayer extends ForgeStartFlowTest.ForgePlayer {
        boolean refuseRefund, pretendRefund;
        int rejectRefundNumber, refunds;
        Runnable afterRefund;
        final Inventory refundInventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item) {
                        if (refuseRefund || ++refunds == rejectRefundNumber) return false;
                        if (pretendRefund) return true;
                        boolean result = super.addItem(item);
                        if (result) {
                            var callback = afterRefund;
                            afterRefund = null;
                            if (callback != null) callback.run();
                        }
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return refundInventory;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldRecipes = new HashMap<>(GameData.getForgeDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getForgeDataMap().clear();
        GameData.getItemDataMap().clear();
        oldResinUsage = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        recipe("{}");
        for (int id : List.of(101001, 101002, 104011, 101004, 104013)) item(id, "ITEM_MATERIAL");
        for (int id : List.of(202, 106)) item(id, "ITEM_VIRTUAL");
        player = new RefundPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 3);
        player.setMora(1000);
        player.setForgePoints(300000);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Confirmation fault");
                                return method.getName().equals("delete")
                                        ? com.mongodb.client.result.DeleteResult.acknowledged(1)
                                        : args[0];
                            }
                            return null;
                        }));
        assertTrue(player.getInventory().addItem(101001, 100));
        assertTrue(player.getInventory().addItem(101002, 100));
        assertTrue(player.getInventory().addItem(101004, 100));
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        GameData.getForgeDataMap().clear();
        GameData.getForgeDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResinUsage;
        environment.close();
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

    private void recipe(String overrides) {
        var json =
                com.google.gson.JsonParser.parseString(
                                "{id:11001,playerLevel:2,isDefaultShow:true,queueNum:40,forgeTime:600,forgePoint:400,scoinCost:5,resultItemId:104011,showItemId:104011,resultItemCount:1,materialItems:[{id:101001,count:2},{id:0,count:0}]}")
                        .getAsJsonObject();
        com.google.gson.JsonParser.parseString(overrides)
                .getAsJsonObject()
                .entrySet()
                .forEach(entry -> json.add(entry.getKey(), entry.getValue()));
        GameData.getForgeDataMap().put(11001, gson.fromJson(json, ForgeData.class));
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private void request(int opcode, byte[] body) {
        dispatcher.handle(player.getSession(), opcode, new byte[0], body);
    }

    private ActiveForgeData start(int id, int count) throws Exception {
        request(
                PacketOpcodes.ForgeStartReq,
                ForgeStartReq.newBuilder()
                        .setForgeId(id)
                        .setForgeCount(count)
                        .build()
                        .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(0, ForgeStartRsp.parseFrom(packet.getData()).getRetcode());
        saves = 0;
        player.refunds = 0;
        return player.getActiveForges().get(player.getActiveForges().size() - 1);
    }

    private ActiveForgeData start(int count) throws Exception {
        return start(11001, count);
    }

    private void sendCancel(int queue) {
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder()
                        .setForgeQueueId(queue)
                        .setManipulateTypeValue(1)
                        .build()
                        .toByteArray());
    }

    private ForgeQueueManipulateRsp last() throws Exception {
        return ForgeQueueManipulateRsp.parseFrom(
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    private ForgeQueueManipulateRsp cancel(int queue) throws Exception {
        sendCancel(queue);
        return last();
    }

    private int state(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getRefundState").invoke(queue));
    }

    private int paymentVersion(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getPaymentVersion").invoke(queue));
    }

    private void legacy(ActiveForgeData queue) throws Exception {
        for (String name :
                List.of("paymentVersion", "paidCostsPerUnit", "paidForgePointsPerUnit")) {
            try {
                var field = ActiveForgeData.class.getDeclaredField(name);
                field.setAccessible(true);
                if (field.getType() == int.class) field.setInt(queue, 0);
                else field.set(queue, null);
            } catch (NoSuchFieldException oldImplementation) {
            }
        }
    }

    private void actualResinRecipe() throws Exception {
        var rows =
                JsonUtils.loadToList(
                        java.nio.file.Path.of(
                                "/resources/ExcelBinOutput/ForgeExcelConfigData.json"),
                        ForgeData.class);
        GameData.getForgeDataMap()
                .put(
                        12001,
                        rows.stream()
                                .filter(row -> row.getId() == 12001)
                                .findFirst()
                                .orElseThrow());
    }

    @Test
    void cancellationRestoresActualCostsAndReturnsHints() throws Exception {
        var queue = start(2);
        assertEquals(1, paymentVersion(queue));
        assertEquals(96, stock(101001));
        var result = cancel(1);
        assertEquals(0, result.getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
        assertEquals(300000, player.getForgePoints());
        assertTrue(player.getActiveForges().isEmpty());
        assertEquals(
                4,
                result.getReturnItemListList().stream()
                        .filter(h -> h.getItemId() == 101001)
                        .findFirst()
                        .orElseThrow()
                        .getCount());
        assertEquals(
                player.getInventory().getItemById(101001).getGuid(),
                result.getReturnItemListList().stream()
                        .filter(h -> h.getItemId() == 101001)
                        .findFirst()
                        .orElseThrow()
                        .getGuid());
        assertEquals(
                10,
                result.getReturnItemListList().stream()
                        .filter(h -> h.getItemId() == 202)
                        .findFirst()
                        .orElseThrow()
                        .getCount());
        assertEquals(4, saves);
    }

    @Test
    void refundUsesOriginalCostsAfterResourceChanges() throws Exception {
        start(2);
        recipe("{forgePoint:4000,scoinCost:50,materialItems:[{id:101001,count:20}]}");
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
        assertEquals(300000, player.getForgePoints());
    }

    @Test
    void refundWorksFromSnapshotAfterRecipeIsRemoved() throws Exception {
        start(2);
        GameData.getForgeDataMap().clear();
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
    }

    @Test
    void paidResinIsRefundedEvenAfterUsageIsDisabled() throws Exception {
        actualResinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        start(12001, 1);
        assertEquals(90, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(100, stock(101004));
        assertEquals(1000, player.getMora());
        assertEquals(10, player.resinMissionProgress);
        assertEquals(
                10,
                last().getReturnItemListList().stream()
                        .filter(h -> h.getItemId() == 106)
                        .findFirst()
                        .orElseThrow()
                        .getCount());
    }

    @Test
    void originallyFreeResinCannotBeRefundedAfterUsageIsEnabled() throws Exception {
        actualResinRecipe();
        start(12001, 1);
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertTrue(last().getReturnItemListList().stream().noneMatch(h -> h.getItemId() == 106));
    }

    @Test
    void refundKeepsResinRechargeRunningBelowCap() throws Exception {
        actualResinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        start(12001, 1);
        long recharge = player.getNextResinRefresh();
        assertTrue(recharge > 0);
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(recharge, player.getNextResinRefresh());
    }

    @Test
    void refundStopsResinRechargeAtTheConfiguredCap() throws Exception {
        actualResinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        int cap = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.cap;
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, cap);
        start(12001, 1);
        assertTrue(player.getNextResinRefresh() > 0);
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(cap, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, player.getNextResinRefresh());
    }

    @Test
    void finishedOutputsMustBeCollectedBeforeCancellation() throws Exception {
        var queue = start(2);
        queue.setStartTime(Utils.getCurrentSeconds() - 601);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, state(queue));
    }

    @Test
    void afterPartialCollectionOnlyRemainingUnitsAreRefunded() throws Exception {
        var queue = start(3);
        queue.setStartTime(Utils.getCurrentSeconds() - 601);
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder()
                        .setForgeQueueId(1)
                        .setManipulateTypeValue(0)
                        .build()
                        .toByteArray());
        assertEquals(0, last().getRetcode());
        assertEquals(2, queue.getCount());
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(98, stock(101001));
        assertEquals(995, player.getMora());
        assertEquals(299600, player.getForgePoints());
        assertEquals(1, stock(104011));
    }

    @Test
    void repeatedCancellationCannotRefundAgain() throws Exception {
        start(2);
        assertEquals(0, cancel(1).getRetcode());
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
    }

    @Test
    void simultaneousCancellationHasOneRefund() throws Exception {
        start(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs = List.of(pool.submit(() -> sendCancel(1)), pool.submit(() -> sendCancel(1)));
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
        int successes = 0;
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp
                    && ForgeQueueManipulateRsp.parseFrom(packet.getData()).getRetcode() == 0)
                successes++;
        assertEquals(1, successes);
    }

    @Test
    void fullMaterialStackKeepsQueueUntilRefundFits() throws Exception {
        var queue = start(2);
        assertTrue(player.getInventory().addItem(101001, 904));
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(1000, stock(101001));
        assertEquals(990, player.getMora());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, state(queue));
        assertTrue(player.getInventory().removeItem(101001, 4));
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(1000, stock(101001));
        assertEquals(1000, player.getMora());
    }

    @Test
    void missingMaterialDefinitionRejectsAllRefundRows() throws Exception {
        var queue = start(2);
        var definition = GameData.getItemDataMap().remove(101001);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(990, player.getMora());
        assertSame(queue, player.getActiveForges().get(0));
        GameData.getItemDataMap().put(101001, definition);
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void moraOverflowRejectsBeforeMaterialRefund() throws Exception {
        var queue = start(2);
        player.setMora(Integer.MAX_VALUE);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(Integer.MAX_VALUE, player.getMora());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, state(queue));
    }

    @Test
    void resinLimitRejectsBeforeAnyRefund() throws Exception {
        actualResinRecipe();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        var queue = start(12001, 1);
        player.setProperty(
                PlayerProperty.PROP_PLAYER_RESIN,
                player.getPropertyMax(PlayerProperty.PROP_PLAYER_RESIN) - 1);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(97, stock(101004));
        assertEquals(900, player.getMora());
        assertSame(queue, player.getActiveForges().get(0));
    }

    @Test
    void forgePointsCapUsesLongArithmetic() throws Exception {
        start(2);
        player.setForgePoints(299999);
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(300000, player.getForgePoints());
    }

    @Test
    void duplicateInputRowsAreRefundedAsTheirAggregatedPaidCost() throws Exception {
        recipe("{materialItems:[{id:101001,count:2},{id:101001,count:3}]}");
        start(2);
        assertEquals(90, stock(101001));
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        var refunds =
                last().getReturnItemListList().stream()
                        .filter(h -> h.getItemId() == 101001)
                        .toList();
        assertEquals(1, refunds.size());
        assertEquals(10, refunds.get(0).getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void confirmationFailurePreservesReceiptAndPreventsReplay(int stage) throws Exception {
        var queue = start(2);
        failSave = stage;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(1, player.getActiveForges().size());
        assertNotEquals(0, state(queue));
        int ore = stock(101001), mora = player.getMora(), points = player.getForgePoints();
        failSave = 0;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(ore, stock(101001));
        assertEquals(mora, player.getMora());
        assertEquals(points, player.getForgePoints());
        assertEquals(stage == 1 ? 96 : 100, ore);
    }

    @Test
    void refusedRefundCannotClearQueueOrReturnCurrency() throws Exception {
        var queue = start(2);
        player.refuseRefund = true;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(990, player.getMora());
        assertEquals(1, state(queue));
        player.refuseRefund = false;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
    }

    @Test
    void nominalSuccessWithoutRefundIsNotAcknowledged() throws Exception {
        var queue = start(2);
        player.pretendRefund = true;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(1, state(queue));
    }

    @Test
    void partiallyRefundedMaterialsCannotBeRefundedTwice() throws Exception {
        recipe("{materialItems:[{id:101001,count:2},{id:101002,count:3}]}");
        var queue = start(2);
        player.rejectRefundNumber = 2;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(94, stock(101002));
        assertEquals(990, player.getMora());
        assertEquals(1, state(queue));
        player.rejectRefundNumber = 0;
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(94, stock(101002));
    }

    @Test
    void refundReentryCannotCancelCollectOrStartReservedQueue() throws Exception {
        start(2);
        int[] nested = {0, 0, 0};
        player.afterRefund =
                () -> {
                    try {
                        nested[0] = cancel(1).getRetcode();
                        request(
                                PacketOpcodes.ForgeQueueManipulateReq,
                                ForgeQueueManipulateReq.newBuilder()
                                        .setForgeQueueId(1)
                                        .setManipulateTypeValue(0)
                                        .build()
                                        .toByteArray());
                        nested[1] = last().getRetcode();
                        request(
                                PacketOpcodes.ForgeStartReq,
                                ForgeStartReq.newBuilder()
                                        .setForgeId(11001)
                                        .setForgeCount(1)
                                        .build()
                                        .toByteArray());
                        var packet =
                                player.packets.stream()
                                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                                        .reduce((a, b) -> b)
                                        .orElseThrow();
                        nested[2] = ForgeStartRsp.parseFrom(packet.getData()).getRetcode();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, cancel(1).getRetcode());
        for (int code : nested) assertNotEquals(0, code);
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void legacyNonResinQueueUsesValidatedResourceRefund() throws Exception {
        var queue = start(2);
        legacy(queue);
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void legacyResinQueueRequiresReconciliationWithoutGuessingPayment() throws Exception {
        actualResinRecipe();
        var queue = start(12001, 1);
        legacy(queue);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(97, stock(101004));
        assertEquals(900, player.getMora());
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertSame(queue, player.getActiveForges().get(0));
    }

    @Test
    void legacyNegativeCostsCannotSubtractInsteadOfRefunding() throws Exception {
        var queue = start(2);
        legacy(queue);
        recipe("{scoinCost:-5}");
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(990, player.getMora());
    }

    @Test
    void legacyOverflowingCostsCannotWrap() throws Exception {
        var queue = start(2);
        legacy(queue);
        recipe("{forgePoint:2147483647,scoinCost:2147483647}");
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(990, player.getMora());
    }

    @Test
    void damagedQueueCountCannotScaleRefundBeyondPaidUnits() throws Exception {
        var queue = start(2);
        queue.setCount(3);
        assertNotEquals(0, cancel(1).getRetcode());
        assertEquals(96, stock(101001));
        assertEquals(990, player.getMora());
    }

    @Test
    void zeroFeeQueueCanBeCanceledWithoutFakeRefundItems() throws Exception {
        recipe("{forgePoint:0,scoinCost:0,materialItems:[{id:0,count:0}]}");
        start(1);
        assertEquals(0, cancel(1).getRetcode());
        assertTrue(last().getReturnItemListList().isEmpty());
        assertEquals(100, stock(101001));
        assertEquals(1000, player.getMora());
    }

    @Test
    void actualOreRecipeCancellationRestoresExactResourceCosts() throws Exception {
        var rows =
                JsonUtils.loadToList(
                        java.nio.file.Path.of(
                                "/resources/ExcelBinOutput/ForgeExcelConfigData.json"),
                        ForgeData.class);
        var definition =
                rows.stream().filter(row -> row.getId() == 11003).findFirst().orElseThrow();
        item(101003, "ITEM_MATERIAL");
        assertTrue(player.getInventory().addItem(101003, 100));
        GameData.getForgeDataMap().put(11003, definition);
        start(11003, 2);
        assertEquals(92, stock(101003));
        assertEquals(0, cancel(1).getRetcode());
        assertEquals(100, stock(101003));
        assertEquals(1000, player.getMora());
        assertEquals(300000, player.getForgePoints());
    }

    @dev.morphia.annotations.Entity(value = "forge_refund_test", useDiscriminator = false)
    static class StoredForge {
        @dev.morphia.annotations.Id String id;
        ActiveForgeData queue;
    }

    @Test
    void paidCostSnapshotSurvivesMongoAndResourceChange() throws Exception {
        var queue = start(2);
        mongo(
                queue,
                loaded -> {
                    try {
                        assertEquals(1, paymentVersion(loaded));
                        player.getActiveForges().set(0, loaded);
                        recipe(
                                "{scoinCost:50,forgePoint:4000,materialItems:[{id:101001,count:20}]}");
                        assertEquals(0, cancel(1).getRetcode());
                        assertEquals(100, stock(101001));
                        assertEquals(1000, player.getMora());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    void pendingRefundSurvivesMongoAndBlocksReplay() throws Exception {
        var queue = start(2);
        player.refuseRefund = true;
        assertNotEquals(0, cancel(1).getRetcode());
        mongo(
                queue,
                loaded -> {
                    try {
                        assertEquals(1, state(loaded));
                        player.getActiveForges().set(0, loaded);
                        player.refuseRefund = false;
                        assertNotEquals(0, cancel(1).getRetcode());
                        assertEquals(96, stock(101001));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    void legacyMongoQueueWithoutNewReceiptFieldsKeepsValidatedRefundPath() throws Exception {
        var queue = start(2);
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgerefund150_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "old";
                row.queue = queue;
                store.save(row);
                var updates = new ArrayList<org.bson.conversions.Bson>();
                for (String field :
                        List.of(
                                "paymentVersion",
                                "paidCostsPerUnit",
                                "paidUnitsRemaining",
                                "paidForgePointsPerUnit",
                                "refundState"))
                    updates.add(com.mongodb.client.model.Updates.unset("queue." + field));
                client.getDatabase(name)
                        .getCollection("forge_refund_test")
                        .updateOne(
                                com.mongodb.client.model.Filters.eq("_id", "old"),
                                com.mongodb.client.model.Updates.combine(updates));
                var loaded = store.find(StoredForge.class).first().queue;
                assertEquals(0, paymentVersion(loaded));
                assertEquals(0, state(loaded));
                player.getActiveForges().set(0, loaded);
                assertEquals(0, cancel(1).getRetcode());
                assertEquals(100, stock(101001));
                assertEquals(1000, player.getMora());
                assertEquals(300000, player.getForgePoints());
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgerefund150_"));
                client.getDatabase(name).drop();
            }
        }
    }

    private void mongo(ActiveForgeData queue, java.util.function.Consumer<ActiveForgeData> check)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgerefund150_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "queue";
                row.queue = queue;
                store.save(row);
                check.accept(store.find(StoredForge.class).first().queue);
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgerefund150_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
