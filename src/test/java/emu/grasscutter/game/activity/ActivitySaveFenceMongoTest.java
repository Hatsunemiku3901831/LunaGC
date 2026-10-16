package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Cold independent copies must not overwrite a newer activity ledger or replay. */
class ActivitySaveFenceMongoTest {
    static final int UID = 74401, ACTIVITY = 2014, SCHEDULE = 2014771;
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    MongoClient client;
    Datastore store, previous;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        previous = DatabaseManager.getGameDatastore();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_activity_fence_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(PlayerActivityData.class);
        setStore(store);
    }

    static void setStore(Datastore value) throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, value);
    }

    @AfterEach
    void close() throws Exception {
        if (store == null) return;
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        setStore(previous);
        store.getDatabase().drop();
        client.close();
    }

    PlayerActivityData fresh() {
        return PlayerActivityData.of()
                .uid(UID)
                .activityId(ACTIVITY)
                .scheduleId(SCHEDULE)
                .activityCoins(new HashMap<>(Map.of(141, 10)))
                .detail("old")
                .build();
    }

    PlayerActivityData seed() {
        var value = fresh();
        value.saveSync();
        return value;
    }

    PlayerActivityData read() {
        return DatabaseHelper.getPlayerActivityData(UID, ACTIVITY);
    }

    Document row() {
        return store.getDatabase()
                .getCollection("activities")
                .find(new Document("uid", UID))
                .first();
    }

    @Test
    void aColdOldCopyCannotOverwriteANewerBalance() {
        seed();
        var old = read();
        var current = read();
        current.getActivityCoins().put(141, 30);
        current.saveSync();
        var committed = row();
        old.getActivityCoins().put(141, 5);
        assertThrows(IllegalStateException.class, old::saveSync);
        assertEquals(committed, row());
    }

    @Test
    void anOldWatcherSaveCannotRollBackANewerLedger() {
        seed();
        var old = read();
        var current = read();
        current.getActivityCoins().put(141, 40);
        current.saveSync();
        var committed = row();
        old.getWatcherInfoMap()
                .put(8, PlayerActivityData.WatcherInfo.of().watcherId(8).curProgress(5).build());
        old.setDetailJson("unconfirmed reward");
        assertThrows(IllegalStateException.class, old::saveSync);
        assertEquals(committed, row());
    }

    @Test
    void anOldScheduleCannotOverwriteANewReplay() {
        seed();
        var old = read();
        var current = read();
        current.setScheduleId(SCHEDULE + 1);
        current.getActivityCoins().clear();
        current.saveSync();
        var committed = row();
        assertThrows(IllegalStateException.class, old::saveSync);
        assertEquals(committed, row());
    }

    @Test
    void aLoadedCopyCannotResurrectADeletedActivity() {
        seed();
        var old = read();
        store.getDatabase().getCollection("activities").deleteOne(new Document("uid", UID));
        assertThrows(IllegalStateException.class, old::saveSync);
        assertNull(row());
    }

    @Test
    void aFreshObjectCannotReplaceAnExistingId() {
        var value = seed();
        var committed = row();
        var duplicate = fresh();
        duplicate.setId(value.getId());
        assertThrows(RuntimeException.class, duplicate::saveSync);
        assertEquals(committed, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyOneLegacyReaderCanSaveTheVersionItRead(boolean explicitNull) {
        var legacy = fresh();
        legacy.setId("legacy-activity");
        var document = store.getMapper().toDocument(legacy);
        document.remove("saveStamp");
        if (explicitNull) document.put("saveStamp", null);
        store.getDatabase().getCollection("activities").insertOne(document);
        var old = read();
        var current = read();
        assertEquals(document, row());
        current.getActivityCoins().put(141, 20);
        current.saveSync();
        var committed = row();
        assertThrows(IllegalStateException.class, old::saveSync);
        assertEquals(committed, row());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "uid", "activity"})
    void changingLoadedIdentityCannotCreateOrOverwriteAnotherRow(String field) {
        seed();
        var value = read();
        var committed = row();
        switch (field) {
            case "id" -> value.setId("another-id");
            case "uid" -> value.setUid(UID + 1);
            default -> value.setActivityId(ACTIVITY + 1);
        }
        assertThrows(IllegalStateException.class, value::saveSync);
        assertEquals(committed, row());
        assertEquals(1, store.getDatabase().getCollection("activities").countDocuments());
    }

    @Test
    void aQueuedOldCopyCannotRollBackANewerAcknowledgedWrite() throws Exception {
        seed();
        var old = read();
        var current = read();
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        Document committed;
        try (var held = new HeldQueue()) {
            DatabaseHelper.savePlayerActivityData(old);
            current.getActivityCoins().put(141, 20);
            current.saveSync();
            committed = row();
        }
        assertEquals(committed, row());
        assertEquals(failures + 1, DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    @Test
    void queuedDuplicateIdsAreReportedInsteadOfRetriedAsUnconditionalReplacements()
            throws Exception {
        var value = seed();
        var committed = row();
        var duplicate = fresh();
        duplicate.setId(value.getId());
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        DatabaseHelper.savePlayerActivityData(duplicate);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(failures + 1, DatabaseHelper.getEventExecutor().snapshot().failed());
        assertEquals(committed, row());
    }

    @Test
    void queuedSaveOfTheSameLiveObjectUsesTheLatestConfirmedRevision() throws Exception {
        var data = seed();
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        try (var held = new HeldQueue()) {
            DatabaseHelper.savePlayerActivityData(data);
            data.getActivityCoins().put(141, 20);
            data.saveSync();
            data.setDetailJson("latest");
        }
        assertEquals(20, read().getActivityCoins().get(141));
        assertEquals("latest", read().getDetail());
        assertEquals(failures, DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    @Test
    void genericSaveEntryPointsCannotBypassAFrozenLedger() throws Exception {
        var data = seed();
        data.blockUnconfirmedActivityCoins();
        var saved = row();
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveGameSync(data));
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        DatabaseHelper.savePlayerActivityData(data);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(failures + 1, DatabaseHelper.getEventExecutor().snapshot().failed());
        assertEquals(saved, row());
    }

    @Test
    void aScheduleChangeWithoutARevisionStillFencesTheOldCopy() {
        var data = seed();
        store.getDatabase()
                .getCollection("activities")
                .updateOne(
                        new Document("uid", UID),
                        new Document("$set", new Document("scheduleId", SCHEDULE + 1)));
        var saved = row();
        assertThrows(IllegalStateException.class, data::saveSync);
        assertEquals(saved, row());
    }

    @Test
    void queueRetainsTheOriginalDatastoreAndRejectsLaterRedirection() throws Exception {
        var value = seed();
        var other =
                Morphia.createDatastore(
                        client,
                        "lunagc_activity_scope_" + UUID.randomUUID().toString().replace("-", ""));
        other.getMapper().map(PlayerActivityData.class);
        try {
            try (var held = new HeldQueue()) {
                value.getActivityCoins().put(141, 25);
                DatabaseHelper.savePlayerActivityData(value);
                setStore(other);
            }
            assertEquals(0, other.getDatabase().getCollection("activities").countDocuments());
            assertThrows(IllegalStateException.class, value::saveSync);
            setStore(store);
            assertEquals(25, read().getActivityCoins().get(141));
        } finally {
            setStore(store);
            other.getDatabase().drop();
        }
    }

    static class HeldQueue implements AutoCloseable {
        final CountDownLatch release = new CountDownLatch(1);

        HeldQueue() throws Exception {
            var queue = DatabaseHelper.getEventExecutor();
            var entered = new CountDownLatch(queue.getCorePoolSize());
            for (int i = 0; i < queue.getCorePoolSize(); i++)
                queue.execute(
                        () -> {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                            }
                        });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        public void close() throws Exception {
            release.countDown();
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        }
    }
}
