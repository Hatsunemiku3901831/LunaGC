package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartInfoOuterClass.FurnitureMakeStartInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Exercises confirmed payment through the real start path and never reconstructs it from balances.
 */
class FurniturePaidStartRecoveryTest {
    FurnitureStartRecoveryTest common;
    FurnitureStartFlowTest.CraftPlayer player;
    final Gson gson = new Gson();

    @BeforeEach
    void setup() throws Exception {
        common = new FurnitureStartRecoveryTest();
        common.setup();
        player = common.player;
    }

    @AfterEach
    void cleanup() throws Exception {
        common.cleanup();
    }

    void paid() throws Exception {
        common.fixtureField("failSave", 4);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.fixture.start().getRetcode());
        assertEquals(2, player.home.getPendingFurnitureStart().getState());
        common.snapshot();
        common.fixtureField("failSave", 0);
    }

    void paidBatch() throws Exception {
        common.fixtureField("failSave", 4);
        var entry = FurnitureMakeStartInfo.newBuilder().setMakeId(38).build();
        player.getFurnitureManager().startBatch(List.of(entry, entry));
        assertEquals(2, player.home.getPendingFurnitureStart().getState());
        common.snapshot();
        common.fixtureField("failSave", 0);
    }

    void assertJobs(List<FurnitureMakeSlotItem> expected) {
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(
                gson.toJson(expected), gson.toJson(player.home.getFurnitureMakeSlotItemList()));
    }

    void refused() throws Exception {
        var receipt = player.home.getPendingFurnitureStart();
        var queue = player.home.getFurnitureMakeSlotItemList();
        var original = gson.toJson(queue);
        int writes = common.saves();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        assertSame(queue, player.home.getFurnitureMakeSlotItemList());
        assertEquals(original, gson.toJson(queue));
        assertEquals(writes, common.saves());
        common.costsStillPaidOnce(1);
    }

    @Test
    void confirmedSinglePaymentRestoresTheOriginalJobWithoutAnotherDebit() throws Exception {
        paid();
        var jobs = player.home.getPendingFurnitureStart().getJobs();
        int writes = common.saves();
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        var result = common.query();
        assertEquals(0, result.getRetcode());
        assertJobs(jobs);
        assertEquals(
                jobs.get(0).toProto(), result.getFurnitureMakeSlot().getFurnitureMakeDataList(0));
        assertEquals(writes + 1, common.saves());
        common.costsStillPaidOnce(1);
    }

    @Test
    void entireConfirmedBatchIsRestoredTogether() throws Exception {
        paidBatch();
        var jobs = player.home.getPendingFurnitureStart().getJobs();
        assertEquals(2, jobs.size());
        assertEquals(0, common.query().getRetcode());
        assertJobs(jobs);
        common.costsStillPaidOnce(2);
    }

    @Test
    void secondQueryDoesNotReinsertOrWriteTheBatch() throws Exception {
        paidBatch();
        assertEquals(0, common.query().getRetcode());
        int writes = common.saves();
        assertEquals(0, common.query().getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(writes, common.saves());
        common.costsStillPaidOnce(2);
    }

    @Test
    void aDirectStartRetryStillCannotChargeAgain() throws Exception {
        paid();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.fixture.start().getRetcode());
        assertEquals(2, player.home.getPendingFurnitureStart().getState());
        common.costsStillPaidOnce(1);
        assertEquals(0, common.query().getRetcode());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        common.costsStillPaidOnce(1);
    }

    @Test
    void aNewRequestAfterReconciliationCanPayForAnotherJob() throws Exception {
        paid();
        assertEquals(0, common.query().getRetcode());
        assertEquals(0, common.fixture.start().getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        common.costsStillPaidOnce(2);
    }

    @Test
    void existingUnrelatedJobsRemainInPlace() throws Exception {
        var other = FurnitureMakeSlotItem.of().index(7).makeId(999).beginTime(1).durTime(1).build();
        player.home.getFurnitureMakeSlotItemList().add(other);
        paid();
        var original = player.home.getFurnitureMakeSlotItemList().get(0);
        var job = player.home.getPendingFurnitureStart().getJobs().get(0);
        assertEquals(0, common.query().getRetcode());
        assertSame(original, player.home.getFurnitureMakeSlotItemList().get(0));
        assertEquals(job.toProto(), player.home.getFurnitureMakeSlotItemList().get(1).toProto());
        common.costsStillPaidOnce(1);
    }

    @Test
    void alreadyPresentMatchingPaidJobsAreNotDuplicated() throws Exception {
        paidBatch();
        var jobs = player.home.getPendingFurnitureStart().getJobs();
        player.home.getFurnitureMakeSlotItemList().addAll(jobs);
        common.snapshot();
        assertEquals(0, common.query().getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        assertNull(player.home.getPendingFurnitureStart());
        common.costsStillPaidOnce(2);
    }

    @Test
    void partialBatchDoesNotInventTheMissingRemainder() throws Exception {
        paidBatch();
        player.home
                .getFurnitureMakeSlotItemList()
                .add(player.home.getPendingFurnitureStart().getJobs().get(0));
        var receipt = player.home.getPendingFurnitureStart();
        int writes = common.saves();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(writes, common.saves());
        common.costsStillPaidOnce(2);
    }

    @Test
    void changedLiveRecipeCannotRewriteThePaidDefinition() throws Exception {
        paid();
        var jobs = player.home.getPendingFurnitureStart().getJobs();
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, common.query().getRetcode());
        assertJobs(jobs);
        common.costsStillPaidOnce(1);
    }

    @Test
    void legitimateLaterMaterialChangesDoNotBecomeAnotherPayment() throws Exception {
        paid();
        assertTrue(player.inventory.addItem(101311, 20));
        assertTrue(player.inventory.addItem(101312, 10));
        assertEquals(0, common.query().getRetcode());
        assertEquals(56, player.inventory.getItemCountById(101311));
        assertEquals(28, player.inventory.getItemCountById(101312));
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void materialsNoLongerInInventoryDoNotEraseAConfirmedPayment() throws Exception {
        paid();
        assertTrue(player.inventory.payItem(101311, 36));
        assertTrue(player.inventory.payItem(101312, 18));
        assertEquals(0, common.query().getRetcode());
        assertEquals(0, player.inventory.getItemCountById(101311));
        assertEquals(0, player.inventory.getItemCountById(101312));
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void confirmedFreeRecipeCanResumeWithoutInventingCosts() throws Exception {
        common.fixture.recipe("[]");
        common.fixtureField("failSave", 2);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.fixture.start().getRetcode());
        assertEquals(2, player.home.getPendingFurnitureStart().getState());
        assertTrue(player.home.getPendingFurnitureStart().getCosts().isEmpty());
        common.fixtureField("failSave", 0);
        assertEquals(0, common.query().getRetcode());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
    }

    @Test
    void failedQueueSaveRestoresBothOriginalReferencesAndCanRetry() throws Exception {
        paid();
        var receipt = player.home.getPendingFurnitureStart();
        var queue = player.home.getFurnitureMakeSlotItemList();
        common.fixtureField("failSave", common.saves() + 1);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        assertSame(queue, player.home.getFurnitureMakeSlotItemList());
        assertTrue(queue.isEmpty());
        common.costsStillPaidOnce(1);
        common.fixtureField("failSave", 0);
        assertEquals(0, common.query().getRetcode());
        assertJobs(receipt.getJobs());
        common.costsStillPaidOnce(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void unconfirmedPaymentsStayReservedWithoutBalanceInference(int stage) throws Exception {
        common.fixtureField("failSave", stage);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.fixture.start().getRetcode());
        assertEquals(1, player.home.getPendingFurnitureStart().getState());
        common.fixtureField("failSave", 0);
        int wood = player.inventory.getItemCountById(101311),
                other = player.inventory.getItemCountById(101312);
        var receipt = player.home.getPendingFurnitureStart();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertEquals(wood, player.inventory.getItemCountById(101311));
        assertEquals(other, player.inventory.getItemCountById(101312));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "duplicateJob",
                "missingPlan",
                "badTotal",
                "badObject",
                "missingJobs",
                "nullQueue",
                "collision",
                "badTime"
            })
    void incompleteOrConflictingPaidEvidenceStaysUntouched(String kind) throws Exception {
        paid();
        var receipt = gson.toJsonTree(player.home.getPendingFurnitureStart()).getAsJsonObject();
        var jobs = receipt.getAsJsonArray("jobs");
        switch (kind) {
            case "duplicateJob" -> jobs.add(jobs.get(0).deepCopy());
            case "missingPlan" -> jobs.get(0).getAsJsonObject().remove("plan");
            case "badTotal" -> receipt.getAsJsonArray("costs")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("count", 3);
            case "badObject" -> receipt.getAsJsonArray("costs")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("objectId", "bad");
            case "missingJobs" -> receipt.remove("jobs");
            case "nullQueue" -> player.home.setFurnitureMakeSlotItemList(null);
            case "collision" -> player.home
                    .getFurnitureMakeSlotItemList()
                    .add(
                            FurnitureMakeSlotItem.of()
                                    .index(0)
                                    .makeId(999)
                                    .beginTime(1)
                                    .durTime(1)
                                    .build());
            case "badTime" -> jobs.get(0).getAsJsonObject().addProperty("beginTime", 0);
        }
        player.home.setPendingFurnitureStart(gson.fromJson(receipt, FurnitureStartReceipt.class));
        refused();
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void anotherPendingOperationPreventsRequeue(String kind) throws Exception {
        paid();
        switch (kind) {
            case "take" -> player.home.setPendingFurnitureTake(new FurnitureTakeReceipt());
            case "cancel" -> player.home.setPendingFurnitureCancel(new FurnitureCancelReceipt());
            case "bulk" -> player.home.setPendingFurnitureBulk(new FurnitureBulkReceipt());
        }
        refused();
    }

    @Test
    void concurrentQueriesRestoreOneQueueAndConfirmOneWrite() throws Exception {
        paid();
        int writes = common.saves();
        player.packets.clear();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    player.getFurnitureManager().queryStatus();
                                    return null;
                                }));
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            var replies =
                    player.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                            .toList();
            assertEquals(2, replies.size());
            for (var reply : replies)
                assertEquals(0, FurnitureMakeRsp.parseFrom(reply.getData()).getRetcode());
            assertEquals(writes + 1, common.saves());
            assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
            assertNull(player.home.getPendingFurnitureStart());
            common.costsStillPaidOnce(1);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void actualMongoPaymentAndAmbiguousQueueWritesSurviveReload(int ambiguity) throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        var name = "lunagc_test_paid191_" + UUID.randomUUID().toString().replace("-", "");
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var previous = field.get(null);
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store =
                    dev.morphia.Morphia.createDatastore(
                            client,
                            name,
                            dev.morphia.mapping.MapperOptions.builder()
                                    .storeEmpties(true)
                                    .storeNulls(false)
                                    .build());
            store.getMapper().map(GameHome.class, GameItem.class);
            try {
                player.home.setId("paid-start");
                store.save(player.inventory.getItemById(101311));
                store.save(player.inventory.getItemById(101312));
                var writes = new java.util.concurrent.atomic.AtomicInteger();
                field.set(
                        null,
                        proxy(
                                store,
                                (method, args) -> {
                                    if (method.getName().equals("save")
                                            && args.length == 2
                                            && writes.incrementAndGet() == 4)
                                        throw new IllegalStateException(
                                                "Interrupted paid home save");
                                },
                                false));
                assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.fixture.start().getRetcode());
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                assertEquals(2, player.home.getPendingFurnitureStart().getState());
                assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
                assertEquals(
                        36,
                        store.find(GameItem.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "itemId", 101311))
                                .first()
                                .getCount());
                assertEquals(
                        18,
                        store.find(GameItem.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "itemId", 101312))
                                .first()
                                .getCount());
                field.set(null, store);
                if (ambiguity != 0)
                    field.set(
                            null,
                            proxy(
                                    store,
                                    (method, args) -> {
                                        if (method.getName().equals("save") && args.length == 2)
                                            throw new IllegalStateException(
                                                    "Queue saved; acknowledgement lost");
                                    },
                                    true));
                assertEquals(
                        ambiguity == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE,
                        common.query().getRetcode());
                if (ambiguity != 0) {
                    assertNotNull(player.home.getPendingFurnitureStart());
                    assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
                }
                field.set(null, store);
                if (ambiguity == 2) assertEquals(0, common.query().getRetcode());
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                assertNull(player.home.getPendingFurnitureStart());
                assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
                assertEquals(0, common.query().getRetcode());
                common.costsStillPaidOnce(1);
                assertEquals(
                        36,
                        store.find(GameItem.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "itemId", 101311))
                                .first()
                                .getCount());
                assertEquals(
                        18,
                        store.find(GameItem.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "itemId", 101312))
                                .first()
                                .getCount());
            } finally {
                field.set(null, previous);
                assertTrue(name.startsWith("lunagc_test_paid191_"));
                client.getDatabase(name).drop();
            }
        }
    }

    interface Fault {
        void run(Method method, Object[] args);
    }

    Object proxy(dev.morphia.Datastore store, Fault fault, boolean after) {
        return Proxy.newProxyInstance(
                dev.morphia.Datastore.class.getClassLoader(),
                new Class<?>[] {dev.morphia.Datastore.class},
                (object, method, args) -> {
                    if (!after) fault.run(method, args);
                    Object result;
                    try {
                        result = method.invoke(store, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                    if (after) fault.run(method, args);
                    return result;
                });
    }
}
