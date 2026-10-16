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
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCompoundMaterialBoostReqOuterClass.PlayerCompoundMaterialBoostReq;
import emu.grasscutter.net.proto.PlayerCompoundMaterialBoostRspOuterClass.PlayerCompoundMaterialBoostRsp;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.net.proto.TakeCompoundOutputRspOuterClass.TakeCompoundOutputRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Registered requests, actual inventory/time changes, and controlled persistence faults. */
class CompoundBoostFlowTest {
    private AbilityTestFixture environment;
    private BoostPlayer player;
    private GameServerPacketHandler dispatcher;
    private final Gson gson = new Gson();
    private Map<Integer, CompoundData> oldRecipes;
    private Map<Integer, CompoundBoostData> oldRates;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private GuardedPlayerTestStore playerStore;
    private int saves, failSave;

    static class BoostPlayer extends InvestigationRewardTest.ClaimPlayer {
        boolean refusePay, throwAfterPay;
        int refuseOutput;
        @dev.morphia.annotations.Transient Runnable afterPay, afterGrant;
        final Inventory boostInventory =
                new Inventory(this) {
                    @Override
                    public boolean payItems(
                            Iterable<ItemParamData> costs, int quantity, ActionReason reason) {
                        var rows = new ArrayList<ItemParamData>();
                        costs.forEach(rows::add);
                        boolean ore = rows.stream().anyMatch(item -> item.getId() == 101001);
                        if (ore && refusePay) return false;
                        boolean result = super.payItems(rows, quantity, reason);
                        if (ore && result) {
                            var callback = afterPay;
                            afterPay = null;
                            if (callback != null) callback.run();
                            if (throwAfterPay) throw new IllegalStateException("Payment fault");
                        }
                        return result;
                    }

                    @Override
                    public boolean addItem(ItemParamData item, ActionReason reason) {
                        if (item.getId() == refuseOutput) return false;
                        boolean result = super.addItem(item, reason);
                        if (result && item.getId() >= 110001 && item.getId() <= 110002) {
                            var callback = afterGrant;
                            afterGrant = null;
                            if (callback != null) callback.run();
                        }
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return boostInventory;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldRecipes = new HashMap<>(GameData.getCompoundDataMap());
        oldRates = new HashMap<>(GameData.getCompoundBoostDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        GameData.getCompoundDataMap().clear();
        GameData.getCompoundBoostDataMap().clear();
        GameData.getItemDataMap().clear();
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        recipe(1001, 1, 60);
        recipe(1002, 1, 60);
        recipe(1003, 2, 60);
        rate(101001, 20);
        for (int id : List.of(101001, 101002, 100017, 110001, 110002, 220060)) item(id, 1000);
        GameData.getInvestigationDataMap()
                .put(
                        10003,
                        gson.fromJson(
                                "{id:10003,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                InvestigationData.class));
        GameData.getInvestigationTargetDataMap()
                .put(
                        60017,
                        gson.fromJson(
                                "{id:60017,investigationId:10003,progress:3,triggerConfig:{triggerType:'TRIGGER_DO_COMPOUND',paramList:['','','']}}",
                                InvestigationTargetData.class));
        CookingCompoundManager.initialize();
        player = new BoostPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
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
        assertTrue(player.getInventory().addItem(220060, 1));
        assertTrue(player.getInventory().addItem(100017, 100));
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        if (playerStore != null) playerStore.close();
        GameData.getCompoundDataMap().clear();
        GameData.getCompoundDataMap().putAll(oldRecipes);
        GameData.getCompoundBoostDataMap().clear();
        GameData.getCompoundBoostDataMap().putAll(oldRates);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        CookingCompoundManager.initialize();
        environment.close();
    }

    private void item(int id, int limit) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:"
                                        + limit
                                        + "}",
                                ItemData.class));
    }

    private void recipe(int id, int group, int time) {
        GameData.getCompoundDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",groupID:"
                                        + group
                                        + ",isDefaultUnlocked:true,costTime:"
                                        + time
                                        + ",queueSize:99,inputVec:[{id:100017,count:1}],outputVec:[{id:"
                                        + (id == 1002 ? 110002 : 110001)
                                        + ",count:1}]}",
                                CompoundData.class));
    }

    private void rate(int id, int seconds) {
        GameData.getCompoundBoostDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:" + id + ",boostTime:" + seconds + "}",
                                CompoundBoostData.class));
    }

    private ActiveCookCompoundData queue(int id, int time, int count, int start) {
        var queue = new ActiveCookCompoundData(id, time, count, start);
        player.getActiveCookCompounds().put(id, queue);
        return queue;
    }

    private ActiveCookCompoundData pending(int id, int count) {
        return queue(id, 60, count, Utils.getCurrentSeconds());
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private int progress() {
        return player.getPlayerProgress().getInvestigation().get(60017);
    }

    private void request(int opcode, byte[] body) {
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        dispatcher.handle(session, opcode, new byte[0], body);
    }

    private void sendBoost(int group, int ore, int count, boolean all) {
        request(
                PacketOpcodes.PlayerCompoundMaterialBoostReq,
                PlayerCompoundMaterialBoostReq.newBuilder()
                        .setTargetCompoundGroupId(group)
                        .setConsumeMaterialId(ore)
                        .setConsumeMaterialCount(count)
                        .setIsBoostAll(all)
                        .build()
                        .toByteArray());
    }

    private PlayerCompoundMaterialBoostRsp lastBoost() throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCompoundMaterialBoostRsp)
                        .reduce((a, b) -> b)
                        .orElse(null);
        assertNotNull(packet, "Registered boost request must receive its typed response");
        return PlayerCompoundMaterialBoostRsp.parseFrom(packet.getData());
    }

    private PlayerCompoundMaterialBoostRsp boost(int group, int ore, int count, boolean all)
            throws Exception {
        sendBoost(group, ore, count, all);
        return lastBoost();
    }

    private int take(int group) throws Exception {
        request(
                PacketOpcodes.TakeCompoundOutputReq,
                TakeCompoundOutputReq.newBuilder().setCompoundGroupId(group).build().toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return TakeCompoundOutputRsp.parseFrom(packet.getData()).getRetcode();
    }

    private static int boostState(ActiveCookCompoundData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getBoostState").invoke(queue));
    }

    @Test
    void partialBoostConsumesMineralAndMovesTimeWithoutGranting() throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        var result = boost(1, 101001, 1, false);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getTakeStatusValue());
        assertEquals(99, stock(101001));
        assertEquals(
                time - 20,
                player.getActiveCookCompounds().get(1001).getOutputTime(Utils.getCurrentSeconds()));
        assertEquals(0, stock(110001));
        assertEquals(0, progress());
        assertEquals(1, result.getCompoundQueDataListCount());
    }

    @Test
    void fullBoostCapsMineralsAndAutomaticallyCollects() throws Exception {
        pending(1001, 2);
        var result = boost(1, 101001, 999, true);
        assertEquals(0, result.getRetcode());
        assertEquals(2, result.getTakeStatusValue());
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
        assertEquals(2, progress());
        assertEquals(2, result.getTakeItemList(0).getCount());
        assertTrue(player.getActiveCookCompounds().isEmpty());
        assertEquals(1, result.getCompoundQueDataListCount());
        var cleared = result.getCompoundQueDataList(0);
        assertEquals(1001, cleared.getCompoundId());
        assertEquals(0, cleared.getOutputCount());
        assertEquals(0, cleared.getWaitCount());
        assertEquals(0, cleared.getOutputTime());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp));
    }

    @Test
    void consumingLastMineralsConfirmsRemovalBeforeAdvancing() throws Exception {
        pending(1001, 2);
        assertTrue(player.getInventory().removeItem(101001, 99));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var result = boost(1, 101001, 1, false);
        assertEquals(0, result.getRetcode());
        assertEquals(0, stock(101001));
        assertNull(player.getInventory().getItemById(101001));
        assertEquals(4, saves);
        assertEquals(0, boostState(player.getActiveCookCompounds().get(1001)));
    }

    @Test
    void failedLastMineralRemovalAcknowledgementBlocksReplay() throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        assertTrue(player.getInventory().removeItem(101001, 99));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        failSave = 2;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(0, stock(101001));
        assertEquals(1, boostState(player.getActiveCookCompounds().get(1001)));
        assertEquals(time, queue.getOutputTime(Utils.getCurrentSeconds()));
        failSave = 0;
        assertTrue(player.getInventory().addItem(101001, 1));
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(1, stock(101001));
    }

    @Test
    void missingLampCannotConsumeMinerals() throws Exception {
        pending(1001, 2);
        assertTrue(player.getInventory().removeItem(220060, 1));
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void insufficientMineralsCannotAdvanceQueue() throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        assertTrue(player.getInventory().removeItem(101001, 100));
        assertNotEquals(0, boost(1, 101001, 2, false).getRetcode());
        assertEquals(time, queue.getOutputTime(Utils.getCurrentSeconds()));
    }

    @Test
    void unknownMineralCannotAdvanceQueue() throws Exception {
        pending(1001, 2);
        assertTrue(player.getInventory().addItem(101002, 10));
        assertNotEquals(0, boost(1, 101002, 1, false).getRetcode());
        assertEquals(10, stock(101002));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void invalidAmountsCannotConsumeOrAdvance(int count) throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        assertNotEquals(0, boost(1, 101001, count, false).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(time, queue.getOutputTime(Utils.getCurrentSeconds()));
    }

    @Test
    void unknownGroupCannotConsumeMinerals() throws Exception {
        pending(1001, 2);
        assertNotEquals(0, boost(99, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void onlyTargetGroupIsAccelerated() throws Exception {
        pending(1001, 2);
        var other = pending(1003, 2);
        int time = other.getOutputTime(Utils.getCurrentSeconds());
        assertEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(time, other.getOutputTime(Utils.getCurrentSeconds()));
        assertSame(other, player.getActiveCookCompounds().get(1003));
    }

    @Test
    void partialBudgetIsSharedInRecipeOrder() throws Exception {
        int start = Utils.getCurrentSeconds();
        queue(1001, 60, 1, start - 20);
        queue(1002, 60, 1, start);
        assertEquals(0, boost(1, 101001, 3, false).getRetcode());
        assertEquals(
                1,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
        var second = player.getActiveCookCompounds().get(1002);
        assertEquals(0, second.getOutputCount(Utils.getCurrentSeconds()));
        assertTrue(Math.abs(second.getOutputTime(Utils.getCurrentSeconds()) - (start + 40)) <= 1);
        assertEquals(97, stock(101001));
        assertEquals(0, progress());
    }

    @Test
    void fullModeUsesLongestQueueAndCollectsWholeGroup() throws Exception {
        pending(1001, 2);
        pending(1002, 1);
        pending(1003, 2);
        var result = boost(1, 101001, 6, true);
        assertEquals(0, result.getRetcode());
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
        assertEquals(1, stock(110002));
        assertEquals(3, progress());
        assertEquals(Set.of(1003), player.getActiveCookCompounds().keySet());
    }

    @Test
    void forgedFullModeCannotCompleteAnUnderfundedGroup() throws Exception {
        pending(1001, 2);
        assertNotEquals(0, boost(1, 101001, 1, true).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(0, stock(110001));
        assertEquals(
                0,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
    }

    @Test
    void bagFullKeepsCompletedQueueAndAllowsLaterManualCollection() throws Exception {
        pending(1001, 2);
        assertTrue(player.getInventory().addItem(110001, 999));
        var result = boost(1, 101001, 6, true);
        assertEquals(0, result.getRetcode());
        assertEquals(3, result.getTakeStatusValue());
        assertEquals(94, stock(101001));
        assertEquals(999, stock(110001));
        assertEquals(0, progress());
        assertEquals(
                2,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
        assertTrue(player.getInventory().removeItem(110001, 1));
        assertEquals(0, take(1));
        assertEquals(1000, stock(110001));
        assertEquals(2, progress());
    }

    @Test
    void partialModeDoesNotAutomaticallyTakeCompletedOutput() throws Exception {
        pending(1001, 1);
        var result = boost(1, 101001, 3, false);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getTakeStatusValue());
        assertEquals(0, stock(110001));
        assertEquals(
                1,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
        assertEquals(0, progress());
    }

    @Test
    void missingOutputDefinitionFailsBeforeCharging() throws Exception {
        pending(1001, 2);
        GameData.getItemDataMap().remove(110001);
        assertNotEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(
                0,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
    }

    @Test
    void completedGroupCannotBeBoostedOrChargedAgain() throws Exception {
        pending(1001, 2);
        assertEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertTrue(
                player.getActiveCookCompounds().isEmpty(),
                "Successful full boost removes its queue");
        assertNotEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
        assertEquals(2, progress());
    }

    @Test
    void simultaneousRequestsHaveExactPaymentsAndTotalAcceleration() throws Exception {
        var queue = pending(1001, 10);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        player.packets.clear();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs =
                    List.of(
                            pool.submit(() -> sendBoost(1, 101001, 1, false)),
                            pool.submit(() -> sendBoost(1, 101001, 1, false)));
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        var responses =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCompoundMaterialBoostRsp)
                        .toList();
        assertEquals(2, responses.size());
        for (var response : responses)
            assertEquals(
                    0, PlayerCompoundMaterialBoostRsp.parseFrom(response.getData()).getRetcode());
        assertEquals(98, stock(101001));
        assertEquals(
                time - 40,
                player.getActiveCookCompounds().get(1001).getOutputTime(Utils.getCurrentSeconds()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void confirmationFailuresLeaveReceiptBlockedAndDoNotReplay(int stage) throws Exception {
        pending(1001, 2);
        failSave = stage;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        int before = stock(101001),
                time =
                        player.getActiveCookCompounds()
                                .get(1001)
                                .getOutputTime(Utils.getCurrentSeconds());
        assertEquals(1, boostState(player.getActiveCookCompounds().get(1001)));
        failSave = 0;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(before, stock(101001));
        assertEquals(
                time,
                player.getActiveCookCompounds().get(1001).getOutputTime(Utils.getCurrentSeconds()));
        assertEquals(stage == 1 ? 100 : 99, before);
    }

    @Test
    void refusedPaymentDoesNotAdvanceOrRetry() throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        player.refusePay = true;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        player.refusePay = false;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(time, queue.getOutputTime(Utils.getCurrentSeconds()));
    }

    @Test
    void paymentExceptionAfterConsumptionCannotChargeAgain() throws Exception {
        pending(1001, 2);
        player.throwAfterPay = true;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(99, stock(101001));
        player.throwAfterPay = false;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(99, stock(101001));
        assertEquals(
                0,
                player.getActiveCookCompounds()
                        .get(1001)
                        .getOutputCount(Utils.getCurrentSeconds()));
    }

    @Test
    void paymentReentryCannotBoostCollectOrAppendReservedGroup() throws Exception {
        pending(1001, 2);
        player.afterPay =
                () -> {
                    try {
                        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
                        assertNotEquals(0, take(1));
                        request(
                                PacketOpcodes.PlayerCompoundMaterialReq,
                                PlayerCompoundMaterialReq.newBuilder()
                                        .setCompoundId(1001)
                                        .setCount(1)
                                        .build()
                                        .toByteArray());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(99, stock(101001));
        assertEquals(100, stock(100017));
        assertEquals(2, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void grantReentryCannotPayForClaimReservedGroup() throws Exception {
        pending(1001, 2);
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
    }

    @Test
    void partiallyGrantedFullGroupCannotRegrantOutputs() throws Exception {
        pending(1001, 2);
        pending(1002, 1);
        player.refuseOutput = 110002;
        assertNotEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
        assertEquals(0, stock(110002));
        assertEquals(0, progress());
        player.refuseOutput = 0;
        assertNotEquals(0, boost(1, 101001, 6, true).getRetcode());
        assertNotEquals(0, take(1));
        assertEquals(94, stock(101001));
        assertEquals(2, stock(110001));
        assertEquals(0, stock(110002));
    }

    @Test
    void fullyReadyQueuesDoNotConsumeMinerals() throws Exception {
        queue(1001, 60, 2, Utils.getCurrentSeconds() - 121);
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void legacyCompletedQueueCanBeTakenWithOtherFullyBoostedQueue() throws Exception {
        queue(1001, 0, 2, Utils.getCurrentSeconds());
        pending(1002, 1);
        assertEquals(0, boost(1, 101001, 3, true).getRetcode());
        assertEquals(2, stock(110001));
        assertEquals(1, stock(110002));
        assertEquals(3, progress());
    }

    @Test
    void invalidStoredProcessingTimeCannotConsumeMinerals() throws Exception {
        queue(1001, -1, 2, Utils.getCurrentSeconds());
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void queueBeyondResourceLimitCannotConsumeMinerals() throws Exception {
        pending(1001, 100);
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void unrepresentableShiftFailsBeforePayment() throws Exception {
        recipe(1001, 1, Integer.MAX_VALUE);
        rate(101001, Integer.MAX_VALUE);
        queue(1001, Integer.MAX_VALUE, 3, Utils.getCurrentSeconds());
        assertNotEquals(0, boost(1, 101001, 3, true).getRetcode());
        assertEquals(100, stock(101001));
    }

    @Test
    void allActualMineralRatesReachTheRegisteredHandler() throws Exception {
        var rates =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/CompoundBoostExcelConfigData.json"),
                        CompoundBoostData.class);
        assertEquals(8, rates.size());
        GameData.getCompoundBoostDataMap().clear();
        rates.forEach(row -> GameData.getCompoundBoostDataMap().put(row.getId(), row));
        for (var rate : rates) {
            item(rate.getId(), 1000);
            if (stock(rate.getId()) == 0)
                assertTrue(player.getInventory().addItem(rate.getId(), 100));
            int start = Utils.getCurrentSeconds();
            queue(1001, 60, 10, start);
            int before = stock(rate.getId());
            var result = boost(1, rate.getId(), 1, false);
            assertEquals(0, result.getRetcode());
            assertEquals(before - 1, stock(rate.getId()));
            assertEquals(
                    rate.getBoostTime() / 60,
                    player.getActiveCookCompounds().get(1001).getOutputCount(start));
        }
    }

    @Test
    void truncatedRequestFailsWithoutChargingOrMovingQueue() throws Exception {
        var queue = pending(1001, 2);
        int time = queue.getOutputTime(Utils.getCurrentSeconds());
        request(PacketOpcodes.PlayerCompoundMaterialBoostReq, new byte[] {24, (byte) 0x80});
        assertNotEquals(0, lastBoost().getRetcode());
        assertEquals(100, stock(101001));
        assertEquals(time, queue.getOutputTime(Utils.getCurrentSeconds()));
    }

    @dev.morphia.annotations.Entity(value = "compound_boost_test", useDiscriminator = false)
    static class StoredBoost {
        @dev.morphia.annotations.Id String id;
        ActiveCookCompoundData queue;
    }

    @Test
    void pendingBoostSurvivesMongoReloadAndCannotRepeatPayment() throws Exception {
        pending(1001, 2);
        failSave = 1;
        assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database = "lunagc_test_boost146_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredBoost.class);
            try {
                var record = new StoredBoost();
                record.id = "player";
                record.queue = player.getActiveCookCompounds().get(1001);
                store.save(record);
                var loaded = store.find(StoredBoost.class).first();
                assertNotNull(loaded);
                assertEquals(1, boostState(loaded.queue));
                player.getActiveCookCompounds().put(1001, loaded.queue);
                failSave = 0;
                assertNotEquals(0, boost(1, 101001, 1, false).getRetcode());
                assertEquals(100, stock(101001));
            } finally {
                assertTrue(database.startsWith("lunagc_test_boost146_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void oldMongoQueueWithoutBoostFieldRemainsAvailable() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database = "lunagc_test_boost146_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredBoost.class);
            try {
                var record = new StoredBoost();
                record.id = "old";
                record.queue = new ActiveCookCompoundData(1001, 60, 2, Utils.getCurrentSeconds());
                store.save(record);
                client.getDatabase(database)
                        .getCollection("compound_boost_test")
                        .updateOne(
                                com.mongodb.client.model.Filters.eq("_id", "old"),
                                com.mongodb.client.model.Updates.unset("queue.boostState"));
                var loaded = store.find(StoredBoost.class).first();
                assertNotNull(loaded);
                assertEquals(0, boostState(loaded.queue));
                player.getActiveCookCompounds().put(1001, loaded.queue);
                assertEquals(0, boost(1, 101001, 1, false).getRetcode());
                assertEquals(99, stock(101001));
            } finally {
                assertTrue(database.startsWith("lunagc_test_boost146_"));
                client.getDatabase(database).drop();
            }
        }
    }
}
