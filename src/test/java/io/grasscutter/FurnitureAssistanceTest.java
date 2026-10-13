package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class FurnitureAssistanceTest {
    final Gson gson =
            new GsonBuilder()
                    .setExclusionStrategies(
                            new ExclusionStrategy() {
                                public boolean shouldSkipField(FieldAttributes field) {
                                    return field.getAnnotation(
                                                    dev.morphia.annotations.Transient.class)
                                            != null;
                                }

                                public boolean shouldSkipClass(Class<?> type) {
                                    return false;
                                }
                            })
                    .create();
    final int now = (int) Instant.parse("2026-10-13T08:00:00Z").getEpochSecond();
    final AtomicInteger saves = new AtomicInteger();
    Field datastore;
    Object previous;
    GameHome home;
    String durable;
    boolean failWrite, failAfterWrite;

    @BeforeEach
    void setup() throws Exception {
        home = newHome(10001);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previous = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) {
                                saves.incrementAndGet();
                                if (failWrite) throw new IllegalStateException("Before commit");
                                durable = gson.toJson(args[0]);
                                if (failAfterWrite)
                                    throw new IllegalStateException("Acknowledgement lost");
                                return args[0];
                            }
                            throw new UnsupportedOperationException(method.getName());
                        }));
    }

    @AfterEach
    void cleanup() throws Exception {
        datastore.set(null, previous);
    }

    GameHome newHome(int owner) {
        return GameHome.of()
                .ownerUid(owner)
                .level(1)
                .exp(0)
                .furnitureMakeSlotItemList(new ArrayList<>())
                .build();
    }

    FurnitureMakeSlotItem job(int index, int acceleration, int duration) {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(index)
                        .makeId(38)
                        .avatarId(0)
                        .beginTime(now)
                        .durTime(duration)
                        .accelerateTime(acceleration)
                        .build();
        home.getFurnitureMakeSlotItemList().add(slot);
        return slot;
    }

    int assist(GameHome target, int guest, int time) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.game.home.FurnitureAssistance");
        } catch (ClassNotFoundException missing) {
        }
        assertNotNull(type, "Missing durable furniture assistance business operation");
        try {
            return (int)
                    type.getMethod("assist", GameHome.class, int.class, int.class)
                            .invoke(null, target, guest, time);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Assistance leaked an exception", failure.getCause());
        }
    }

    int assist(int guest) throws Exception {
        return assist(home, guest, now);
    }

    Map<String, Object> days() {
        var tree = gson.toJsonTree(home).getAsJsonObject();
        assertTrue(tree.has("furnitureAssistanceDays"), "Quota must be saved in the host document");
        return gson.fromJson(tree.get("furnitureAssistanceDays"), Map.class);
    }

    @Test
    void assistsAllUnfinishedJobsWithOneAcknowledgedHomeWrite() throws Exception {
        job(0, 0, 86400);
        job(1, 0, 36000);
        assertEquals(0, assist(10002));
        assertEquals(
                List.of(14400, 14400),
                home.getFurnitureMakeSlotItemList().stream()
                        .map(FurnitureMakeSlotItem::getAccelerateTime)
                        .toList());
        assertEquals(1, saves.get());
        var loaded = gson.fromJson(durable, GameHome.class);
        assertEquals(14400, loaded.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
        assertEquals(1, days().size());
        assertFalse(durable.contains("pendingFurnitureAssistanceWrite"));
    }

    @Test
    void sameGuestCannotAssistNewJobsOnTheSameDay() throws Exception {
        job(0, 0, 86400);
        assertEquals(0, assist(10002));
        var later = job(1, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(10002));
        assertEquals(0, later.getAccelerateTime());
        assertEquals(1, saves.get());
    }

    @Test
    void sameGuestMayAssistDifferentHosts() throws Exception {
        job(0, 0, 86400);
        assertEquals(0, assist(10002));
        var other = newHome(10003);
        other.getFurnitureMakeSlotItemList().add(job(1, 0, 86400).snapshot());
        assertEquals(0, assist(other, 10002, now));
        assertEquals(14400, other.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
    }

    @Test
    void otherGuestsCannotExceedPerJobLimitOrConsumeQuotaWithoutChanges() throws Exception {
        job(0, 0, 86400);
        assertEquals(0, assist(10002));
        assertEquals(Retcode.RET_FURNITURE_MAKE_ACCELERATE_LIMIT_VALUE, assist(10003));
        assertEquals(1, days().size());
        var fresh = job(1, 0, 86400);
        assertEquals(0, assist(10003));
        assertEquals(14400, fresh.getAccelerateTime());
    }

    @Test
    void partiallyAcceleratedJobOnlyReceivesRemainingAllowance() throws Exception {
        var slot = job(0, 3600, 86400);
        assertEquals(0, assist(10002));
        assertEquals(14400, slot.getAccelerateTime());
        assertEquals((long) now + 72000, slot.getCompletionTime());
    }

    @Test
    void skipsCompletedJobsAndCanCompleteAShortJobImmediately() throws Exception {
        var finished = job(0, 0, 0);
        var shortJob = job(1, 0, 3600);
        assertEquals(0, assist(10002));
        assertEquals(0, finished.getAccelerateTime());
        assertEquals(14400, shortJob.getAccelerateTime());
        assertTrue(shortJob.getCompletionTime() <= now);
    }

    @Test
    void usesWideCompletionTimeNearIntegerBoundary() throws Exception {
        job(0, 0, Integer.MAX_VALUE);
        assertEquals(0, assist(10002));
        assertTrue(
                home.getFurnitureMakeSlotItemList().get(0).getCompletionTime() > Integer.MAX_VALUE);
    }

    @Test
    void quotaSurvivesReload() throws Exception {
        job(0, 0, 86400);
        assertEquals(0, assist(10002));
        home = gson.fromJson(durable, GameHome.class);
        job(1, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(10002));
    }

    @Test
    void resetsAtFourInChinaServerTime() throws Exception {
        job(0, 0, 86400);
        int before = (int) Instant.parse("2026-10-13T19:59:59Z").getEpochSecond();
        assertEquals(0, assist(home, 10002, before));
        var fresh = job(1, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(home, 10002, before));
        assertEquals(0, assist(home, 10002, before + 1));
        assertEquals(14400, fresh.getAccelerateTime());
    }

    @Test
    void wallClockRollbackDoesNotReopenConsumedQuota() throws Exception {
        job(0, 0, 86400);
        assertEquals(0, assist(10002));
        job(1, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(home, 10002, now - 86400));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10001})
    void rejectsInvalidAndOwnerGuest(int guest) throws Exception {
        job(0, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(guest));
        assertEquals(0, saves.get());
        assertEquals(0, home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
    }

    @Test
    void missingHomeOrInvalidTimeCannotMutateAnything() throws Exception {
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(null, 10002, now));
        job(0, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, assist(home, 10002, 0));
        assertEquals(0, saves.get());
    }

    @Test
    void noQueueAndAllCompletedReturnDifferentErrors() throws Exception {
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE, assist(10002));
        job(0, 0, 0);
        assertEquals(Retcode.RET_FURNITURE_MAKE_IS_FINISH_VALUE, assist(10002));
        assertEquals(0, saves.get());
    }

    @Test
    void corruptQueueRejectsEntireBatchBeforeAnyMutation() throws Exception {
        var good = job(0, 0, 86400);
        job(0, 0, 86400);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, assist(10002));
        assertEquals(0, good.getAccelerateTime());
        assertEquals(0, saves.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"index", "makeId", "avatarId", "beginTime", "durTime", "accelerateTime"})
    void corruptScalarFailsWithoutConsumingQuota(String name) throws Exception {
        var slot = job(0, 0, 86400);
        var field = FurnitureMakeSlotItem.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(slot, -1);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, assist(10002));
        assertEquals(0, saves.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "pendingFurnitureStart",
                "pendingFurnitureTake",
                "pendingFurnitureCancel",
                "pendingFurnitureBulk"
            })
    void doesNotChangeReservedEconomicJobs(String name) throws Exception {
        job(0, 0, 86400);
        var field = GameHome.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(home, field.getType().getConstructor().newInstance());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(10002));
        assertEquals(0, saves.get());
        assertEquals(0, home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
    }

    @Test
    void failedWriteKeepsOneCandidateAndRetryConfirmsWithoutAnotherReduction() throws Exception {
        var slot = job(0, 0, 86400);
        failWrite = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(10002));
        assertNull(durable);
        assertEquals(14400, slot.getAccelerateTime());
        assertEquals(1, days().size());
        failWrite = false;
        assertEquals(0, assist(10002));
        assertEquals(14400, slot.getAccelerateTime());
        assertEquals(2, saves.get());
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(10002));
    }

    @Test
    void acknowledgementLossAndReloadCannotApplyTwice() throws Exception {
        job(0, 0, 86400);
        failAfterWrite = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(10002));
        assertNotNull(durable);
        home = gson.fromJson(durable, GameHome.class);
        failAfterWrite = false;
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(10002));
        assertEquals(14400, home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
        assertEquals(1, saves.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void realMongoKeepsQuotaAndQueueAtomicAcrossColdReload(int failure) throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database =
                "lunagc_furniture_help_" + UUID.randomUUID().toString().replace("-", "");
        job(0, 0, 86400);
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            try {
                var store = dev.morphia.Morphia.createDatastore(client, database);
                store.getMapper().map(GameHome.class);
                store.save(home);
                datastore.set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    if (failure == 1 && method.getName().equals("save"))
                                        throw new IllegalStateException("Before Mongo write");
                                    Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                    if (failure == 2 && method.getName().equals("save"))
                                        throw new IllegalStateException(
                                                "Mongo committed; acknowledgement lost");
                                    return result;
                                }));
                assertEquals(failure == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE, assist(10002));
                datastore.set(null, store);
                home = store.find(GameHome.class).first();
                assertNotNull(home);
                var raw = store.getDatabase().getCollection("homes").find().first();
                assertNotNull(raw);
                assertFalse(raw.containsKey("pendingFurnitureAssistanceWrite"));
                if (failure == 1) {
                    assertEquals(0, home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
                    assertEquals(0, assist(10002));
                    home = store.find(GameHome.class).first();
                }
                assertEquals(14400, home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
                assertEquals(1, days().size());
                assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(10002));
            } finally {
                datastore.set(null, previous);
                assertTrue(database.startsWith("lunagc_furniture_help_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void failedConfirmationBlocksAnotherGuestWithoutConsumingTheirQuota() throws Exception {
        job(0, 0, 86400);
        failWrite = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(10002));
        var fresh = job(1, 0, 86400);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(10003));
        assertEquals(0, fresh.getAccelerateTime());
        assertEquals(1, days().size());
        failWrite = false;
        assertEquals(0, assist(10003));
        assertEquals(14400, fresh.getAccelerateTime());
    }

    @Test
    void concurrentRequestsSerializeOneSuccessAndOneConsumedQuota() throws Exception {
        job(0, 0, 86400);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Integer> operation =
                    () -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return assist(10002);
                    };
            var a = executor.submit(operation);
            var b = executor.submit(operation);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(
                    Set.of(0, Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE),
                    Set.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS)));
            assertEquals(1, saves.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
