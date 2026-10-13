package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeStartBatchReqOuterClass.FurnitureMakeStartBatchReq;
import emu.grasscutter.net.proto.FurnitureMakeStartBatchRspOuterClass.FurnitureMakeStartBatchRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartInfoOuterClass.FurnitureMakeStartInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class FurnitureBatchStartFlowTest {
    static class BatchPlayer extends InvestigationRewardTest.ClaimPlayer {
        GameHome home;
        int payCalls, refuseId, partialId;
        boolean pretend;
        Runnable afterPay;
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean payItem(int id, int count) {
                        payCalls++;
                        if (id == refuseId) return false;
                        if (pretend) return true;
                        boolean result = super.payItem(id, id == partialId ? count - 1 : count);
                        if (afterPay != null) {
                            var hook = afterPay;
                            afterPay = null;
                            hook.run();
                        }
                        return result;
                    }
                };

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private BatchPlayer player;
    private AbilityTestFixture environment;
    private Field datastore;
    private Object oldStore;
    private int saves, failSave;

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
        item(360101, "ITEM_FURNITURE");
        item(360102, "ITEM_FURNITURE");
        item(101311, "ITEM_MATERIAL");
        item(101312, "ITEM_MATERIAL");
        recipe(38, 360101, 4, 2, 57600);
        recipe(39, 360102, 2, 1, 14400);
        player.getUnlockedFurniture().addAll(List.of(360101, 360102));
        level(15, 0);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Write fault");
                                if (method.getName().equals("delete"))
                                    return com.mongodb.client.result.DeleteResult.acknowledged(1);
                                return args[0];
                            }
                            return null;
                        }));
        assertTrue(player.inventory.addItem(101311, 100));
        assertTrue(player.inventory.addItem(101312, 50));
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, oldStore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    void item(int id, String kind) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + kind + "',stackLimit:9999}", ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(int id, int output, int a, int b, int time) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{configID:"
                                        + id
                                        + ",furnitureItemID:"
                                        + output
                                        + ",count:1,exp:60,makeTime:"
                                        + time
                                        + ",materialItems:[{id:101311,count:"
                                        + a
                                        + "},{id:101312,count:"
                                        + b
                                        + "}]}",
                                FurnitureMakeConfigData.class));
    }

    void level(int capacity, int reduction) {
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:"
                                        + capacity
                                        + ",furnitureMakeTimeReduction:"
                                        + reduction
                                        + "}",
                                HomeWorldLevelData.class));
    }

    FurnitureMakeStartInfo choose(int id) {
        return FurnitureMakeStartInfo.newBuilder().setMakeId(id).build();
    }

    List<FurnitureMakeStartInfo> two() {
        return List.of(choose(38), choose(39));
    }

    InvestigationRewardTest.ClaimSession session() {
        return (InvestigationRewardTest.ClaimSession) player.getSession();
    }

    Method starter() {
        Method method = null;
        try {
            method = player.getFurnitureManager().getClass().getMethod("startBatch", List.class);
        } catch (NoSuchMethodException old) {
        }
        assertNotNull(method, "Native batch start is not connected");
        return method;
    }

    FurnitureMakeStartBatchRsp result() throws Exception {
        assertTrue(session().responses.stream().noneMatch(p -> p.getOpcode() == 21056));
        var responses = session().responses.stream().filter(p -> p.getOpcode() == 9832).toList();
        assertEquals(1, responses.size());
        return FurnitureMakeStartBatchRsp.parseFrom(responses.get(0).getData());
    }

    FurnitureMakeStartBatchRsp start(List<FurnitureMakeStartInfo> entries) throws Exception {
        session().responses.clear();
        starter().invoke(player.getFurnitureManager(), entries);
        return result();
    }

    void untouched() {
        assertEquals(100, player.inventory.getItemCountById(101311));
        assertEquals(50, player.inventory.getItemCountById(101312));
        assertEquals(0, saves);
        assertEquals(0, player.payCalls);
        assertNull(player.home.getPendingFurnitureStart());
    }

    List<FurnitureMakeSlotItem> planned(FurnitureStartReceipt receipt) throws Exception {
        Object result = null;
        try {
            result = receipt.getClass().getMethod("getJobs").invoke(receipt);
        } catch (NoSuchMethodException old) {
        }
        assertNotNull(result, "Receipt does not retain batch plans");
        return (List<FurnitureMakeSlotItem>) result;
    }

    @Test
    void twoDifferentRecipesAreQueuedAfterOneAggregatePayment() throws Exception {
        var response = start(two());
        assertEquals(0, response.getRetcode());
        assertEquals(2, response.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
        assertEquals(94, player.inventory.getItemCountById(101311));
        assertEquals(47, player.inventory.getItemCountById(101312));
        assertEquals(2, player.payCalls);
        assertEquals(6, saves);
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertEquals(0, player.home.getExp());
    }

    @Test
    void duplicateRecipesCreateIndependentPaidJobs() throws Exception {
        assertEquals(0, start(List.of(choose(38), choose(38))).getRetcode());
        assertEquals(92, player.inventory.getItemCountById(101311));
        assertEquals(46, player.inventory.getItemCountById(101312));
        assertEquals(
                List.of(0, 1),
                player.home.getFurnitureMakeSlotItemList().stream()
                        .map(FurnitureMakeSlotItem::getIndex)
                        .toList());
        assertNotSame(
                player.home.getFurnitureMakeSlotItemList().get(0).getPlan(),
                player.home.getFurnitureMakeSlotItemList().get(1).getPlan());
    }

    @Test
    void receiptReservesEveryPlanBeforeTheFirstPayment() throws Exception {
        player.afterPay =
                () -> {
                    var receipt = player.home.getPendingFurnitureStart();
                    assertNotNull(receipt);
                    assertEquals(1, receipt.getState());
                    assertEquals(2, assertDoesNotThrow(() -> planned(receipt)).size());
                    assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
                    assertEquals(6, receipt.getCosts().get(0).getCount());
                };
        assertEquals(0, start(two()).getRetcode());
    }

    @Test
    void oldJobsAndGapsKeepTheirIndexes() throws Exception {
        var old = FurnitureMakeSlotItem.of().index(1).makeId(38).beginTime(1).durTime(1).build();
        player.home.getFurnitureMakeSlotItemList().add(old);
        player.home
                .getFurnitureMakeSlotItemList()
                .add(
                        FurnitureMakeSlotItem.of()
                                .index(3)
                                .makeId(39)
                                .beginTime(2)
                                .durTime(2)
                                .build());
        var response = start(two());
        assertEquals(0, response.getRetcode());
        assertEquals(4, response.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
        assertEquals(
                List.of(1, 3, 0, 2),
                player.home.getFurnitureMakeSlotItemList().stream()
                        .map(FurnitureMakeSlotItem::getIndex)
                        .toList());
        assertSame(old, player.home.getFurnitureMakeSlotItemList().get(0));
    }

    @Test
    void nullLegacyQueueCanStartABatch() throws Exception {
        player.home.setFurnitureMakeSlotItemList(null);
        assertEquals(0, start(two()).getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void fifteenSlotsCanStartTogetherAndTheNextJobIsFull() throws Exception {
        var entries = new ArrayList<FurnitureMakeStartInfo>();
        for (int i = 0; i < 15; i++) entries.add(choose(38));
        assertEquals(0, start(entries).getRetcode());
        assertEquals(15, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE,
                start(List.of(choose(39))).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
    }

    @Test
    void wholeBatchCapacityIsCheckedBeforePaying() throws Exception {
        level(1, 0);
        assertEquals(Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE, start(two()).getRetcode());
        untouched();
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void existingJobsCountAgainstBatchCapacity() throws Exception {
        level(2, 0);
        player.home
                .getFurnitureMakeSlotItemList()
                .add(
                        FurnitureMakeSlotItem.of()
                                .index(2)
                                .makeId(38)
                                .beginTime(1)
                                .durTime(1)
                                .build());
        assertEquals(Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE, start(two()).getRetcode());
        untouched();
    }

    @Test
    void invalidLaterRecipeRejectsTheEntireBatch() throws Exception {
        GameData.getFurnitureMakeConfigDataMap().remove(39);
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @Test
    void laterLockedFurnitureDoesNotChargeEarlierRecipe() throws Exception {
        player.getUnlockedFurniture().remove(360102);
        assertEquals(Retcode.RET_FURNITURE_MAKE_LOCKED_VALUE, start(two()).getRetcode());
        untouched();
    }

    @Test
    void individuallyAffordableRecipesCannotOverspendTheCombinedCost() throws Exception {
        recipe(38, 360101, 60, 2, 57600);
        recipe(39, 360102, 60, 1, 14400);
        assertEquals(Retcode.RET_HOME_FURNITURE_COUNT_NOT_ENOUGH_VALUE, start(two()).getRetcode());
        untouched();
    }

    @Test
    void combinedMaterialOverflowIsRejectedBeforePayment() throws Exception {
        recipe(38, 360101, Integer.MAX_VALUE, 2, 57600);
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @Test
    void badProductInLaterRecipeRejectsAllJobs() throws Exception {
        item(360102, "ITEM_MATERIAL");
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @Test
    void unknownAvatarInLaterInstructionRejectsAllJobs() throws Exception {
        assertNotEquals(
                0,
                start(List.of(choose(38), choose(39).toBuilder().setAvatarId(10000073).build()))
                        .getRetcode());
        untouched();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonpositiveRecipeCannotCreateAJob(int id) throws Exception {
        assertNotEquals(0, start(List.of(choose(38), choose(id))).getRetcode());
        untouched();
    }

    @Test
    void nullInstructionRejectsTheBatch() throws Exception {
        assertNotEquals(0, start(Arrays.asList(choose(38), null)).getRetcode());
        untouched();
    }

    @Test
    void emptyBatchIsNotASuccessfulNoOp() throws Exception {
        assertNotEquals(0, start(List.of()).getRetcode());
        untouched();
    }

    @Test
    void missingSelectionListIsRejected() throws Exception {
        assertNotEquals(0, start(null).getRetcode());
        untouched();
    }

    @Test
    void missingHomeDoesNotSpendAnything() throws Exception {
        player.home = null;
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, start(two()).getRetcode());
        assertEquals(0, player.payCalls);
        assertEquals(0, saves);
    }

    @Test
    void foreignHomeDoesNotSpendOrQueueJobs() throws Exception {
        player.home.setOwnerUid(player.getUid() + 1);
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, start(two()).getRetcode());
        untouched();
    }

    @Test
    void duplicateExistingIndexesAreRejected() throws Exception {
        var slot = FurnitureMakeSlotItem.of().index(1).makeId(38).beginTime(1).durTime(1).build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidCapacityStopsBeforeReservation(int capacity) throws Exception {
        level(capacity, 0);
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @Test
    void negativeReductionStopsBeforeReservation() throws Exception {
        level(15, -1);
        assertNotEquals(0, start(two()).getRetcode());
        untouched();
    }

    @Test
    void reducedAndInstantJobsFreezeTheirActualDurations() throws Exception {
        level(15, 14400);
        assertEquals(0, start(two()).getRetcode());
        var jobs = player.home.getFurnitureMakeSlotItemList();
        assertEquals(43200, jobs.get(0).getDurTime());
        assertEquals(0, jobs.get(1).getDurTime());
        assertEquals(jobs.get(0).getBeginTime(), jobs.get(1).getBeginTime());
        for (var job : jobs) assertTrue(job.getPlan().isValidFor(job));
    }

    @Test
    void changesDuringPaymentDoNotChangeReservedPlans() throws Exception {
        player.afterPay = () -> recipe(39, 360102, 20, 10, Integer.MAX_VALUE);
        assertEquals(0, start(two()).getRetcode());
        var plan = player.home.getFurnitureMakeSlotItemList().get(1).getPlan();
        assertEquals(14400, plan.getMakeTime());
        assertEquals(2, plan.getMaterials().get(0).getCount());
        assertEquals(94, player.inventory.getItemCountById(101311));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void failedAcknowledgementKeepsPlansAndBlocksEveryReplay(int stage) throws Exception {
        failSave = stage;
        assertNotEquals(0, start(two()).getRetcode());
        var receipt = player.home.getPendingFurnitureStart();
        assertNotNull(receipt);
        assertEquals(2, planned(receipt).size());
        int calls = player.payCalls,
                amount = player.inventory.getItemCountById(101311),
                writes = saves;
        failSave = 0;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, start(two()).getRetcode());
        assertEquals(calls, player.payCalls);
        assertEquals(amount, player.inventory.getItemCountById(101311));
        assertEquals(writes, saves);
        player.getFurnitureManager().startMake(38, 0);
        assertEquals(calls, player.payCalls);
        player.getFurnitureManager().take(0, 38, true);
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void refusedFirstPaymentKeepsAReservationWithoutJobs() throws Exception {
        player.refuseId = 101311;
        assertNotEquals(0, start(two()).getRetcode());
        assertNotNull(player.home.getPendingFurnitureStart());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertEquals(100, player.inventory.getItemCountById(101311));
    }

    @Test
    void refusedLaterPaymentDoesNotPretendTheBatchSucceeded() throws Exception {
        player.refuseId = 101312;
        assertNotEquals(0, start(two()).getRetcode());
        assertEquals(94, player.inventory.getItemCountById(101311));
        assertEquals(50, player.inventory.getItemCountById(101312));
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertNotNull(player.home.getPendingFurnitureStart());
    }

    @Test
    void pretendPaymentCannotQueueJobs() throws Exception {
        player.pretend = true;
        assertNotEquals(0, start(two()).getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertNotNull(player.home.getPendingFurnitureStart());
    }

    @Test
    void partialPaymentCannotQueueJobs() throws Exception {
        player.partialId = 101311;
        assertNotEquals(0, start(two()).getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertNotNull(player.home.getPendingFurnitureStart());
    }

    @Test
    void reentrantBatchCannotChargeTheSameMaterialsTwice() throws Exception {
        player.afterPay =
                () -> {
                    assertEquals(
                            Retcode.RET_SVR_ERROR_VALUE,
                            assertDoesNotThrow(() -> start(two())).getRetcode());
                    session().responses.clear();
                };
        assertEquals(0, start(two()).getRetcode());
        assertEquals(94, player.inventory.getItemCountById(101311));
        assertEquals(2, player.payCalls);
    }

    @Test
    void savedReservationIncludesEveryFrozenPlanAndBlocksAfterReload() throws Exception {
        failSave = 6;
        assertNotEquals(0, start(two()).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture183_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                player.home.setPlayer(player);
                var receipt = player.home.getPendingFurnitureStart();
                assertNotNull(receipt);
                assertEquals(3, receipt.getState());
                var plans = planned(receipt);
                assertEquals(2, plans.size());
                for (var job : plans) assertTrue(job.getPlan().isValidFor(job));
                failSave = 0;
                int count = player.inventory.getItemCountById(101311);
                assertNotEquals(0, start(two()).getRetcode());
                assertEquals(count, player.inventory.getItemCountById(101311));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture183_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void legacyReceiptWithoutJobListRemainsReadable() throws Exception {
        assertTrue(planned(new FurnitureStartReceipt()).isEmpty());
    }

    PacketHandler handler() throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.recv.HandlerFurnitureMakeStartBatchReq");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        assertEquals(871, type.getAnnotation(Opcodes.class).value());
        return (PacketHandler) type.getConstructor().newInstance();
    }

    @Test
    void nativeHandlerStartsTheRequestedJobsAndSendsOneBatchReply() throws Exception {
        handler()
                .handle(
                        player.getSession(),
                        new byte[0],
                        FurnitureMakeStartBatchReq.newBuilder()
                                .addAllMakeInfoList(two())
                                .build()
                                .toByteArray());
        assertEquals(0, result().getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void malformedNativePayloadCannotSpendAnything() throws Exception {
        handler().handle(player.getSession(), new byte[0], new byte[] {0x62, (byte) 0x80});
        assertNotEquals(0, result().getRetcode());
        untouched();
    }

    @Test
    void missingNativePlayerReceivesOneFailure() throws Exception {
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), null));
        assertDoesNotThrow(() -> handler().handle(player.getSession(), new byte[0], new byte[0]));
        assertNotEquals(0, result().getRetcode());
        untouched();
    }

    @Test
    void everyCurrentRecipePaysForBothJobsOrRejectsWithoutSpending() throws Exception {
        Path root = Path.of(System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"));
        for (String kind : List.of("Material", "HomeWorldFurniture"))
            for (var data :
                    gson.fromJson(
                            Files.readString(
                                    root.resolve(
                                            "ExcelBinOutput/" + kind + "ExcelConfigData.json")),
                            ItemData[].class)) {
                data.onLoad();
                GameData.getItemDataMap().put(data.getId(), data);
            }
        var definitions =
                gson.fromJson(
                        Files.readString(
                                root.resolve("ExcelBinOutput/FurnitureMakeExcelConfigData.json")),
                        FurnitureMakeConfigData[].class);
        int valid = 0, invalid = 0;
        for (var recipe : definitions) {
            recipe.onLoad();
            GameData.getFurnitureMakeConfigDataMap().put(recipe.getId(), recipe);
            var product = GameData.getItemDataMap().get(recipe.getFurnitureItemID());
            player.getUnlockedFurniture().add(product.getId());
            player.home.getFurnitureMakeSlotItemList().clear();
            Map<Integer, Integer> costs = new LinkedHashMap<>();
            for (var cost : recipe.getMaterialItems())
                costs.merge(cost.getId(), cost.getCount(), Integer::sum);
            Map<Integer, Integer> before = new HashMap<>();
            for (var cost : costs.entrySet()) {
                int add =
                        cost.getValue() * 2 + 1 - player.inventory.getItemCountById(cost.getKey());
                if (add > 0) assertTrue(player.inventory.addItem(cost.getKey(), add));
                before.put(cost.getKey(), player.inventory.getItemCountById(cost.getKey()));
            }
            var response = start(List.of(choose(recipe.getId()), choose(recipe.getId())));
            if (product.getStackLimit() == 0) {
                invalid++;
                assertNotEquals(0, response.getRetcode());
                assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
            } else {
                valid++;
                assertEquals(0, response.getRetcode(), "recipe " + recipe.getId());
                assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
            }
            for (var cost : costs.entrySet())
                assertEquals(
                        before.get(cost.getKey())
                                - (product.getStackLimit() == 0 ? 0 : cost.getValue() * 2),
                        player.inventory.getItemCountById(cost.getKey()));
        }
        assertEquals(1396, valid);
        assertEquals(9, invalid);
    }
}
