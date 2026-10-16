package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.*;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Real Mongo operations, including retained delayed CAS documents and lost acknowledgements. */
class ActivitySaveGuardRecoveryTest {
    static final int UID = 74402, ACTIVITY = 2014, SCHEDULE = 2014771;
    MongoClient client;
    Datastore store, previous;
    FaultStorage fault;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        previous = DatabaseManager.getGameDatastore();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_activity_save_recovery_"
                                + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(PlayerActivityData.class);
        ActivitySaveFenceMongoTest.setStore(store);
        fault = new FaultStorage(new ActivitySaveGuard.MongoStorage(store));
    }

    @AfterEach
    void close() throws Exception {
        if (store == null) return;
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        ActivitySaveFenceMongoTest.setStore(previous);
        store.getDatabase().drop();
        client.close();
    }

    PlayerActivityData fresh() {
        return PlayerActivityData.of()
                .uid(UID)
                .activityId(ACTIVITY)
                .scheduleId(SCHEDULE)
                .activityCoins(new HashMap<>(Map.of(141, 3)))
                .detail("original")
                .build();
    }

    PlayerActivityData seed() {
        var data = fresh();
        data.saveSync();
        return data;
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

    PlayerActivityData instrument(PlayerActivityData data, boolean loaded) throws Exception {
        var field = PlayerActivityData.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        field.set(
                data,
                new ActivitySaveGuard(
                        data, loaded ? row() : null, loaded ? store : null, ignored -> fault));
        return data;
    }

    void coins(PlayerActivityData data, int amount) {
        data.getActivityCoins().put(141, amount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lostReplacementAcknowledgementsRecognizeOnlyOurOwnWrite(boolean committed)
            throws Exception {
        seed();
        var data = instrument(read(), true);
        coins(data, 10);
        fault.next = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, data::saveSync);
        assertEquals(committed ? 10 : 3, read().getActivityCoins().get(141));
        coins(data, 20);
        data.saveSync();
        assertEquals(20, read().getActivityCoins().get(141));
        assertEquals(committed ? 0 : 1, fault.advances);
        assertEquals(3, data.getSaveStamp().getRevision());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainBarriersRetireDelayedOldWritesWithoutChangingBusinessFields(boolean committed)
            throws Exception {
        seed();
        var data = instrument(read(), true);
        coins(data, 10);
        data.setScheduleId(SCHEDULE + 1);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, data::saveSync);
        coins(data, 20);
        data.setScheduleId(SCHEDULE + 2);
        fault.nextAdvance = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, data::saveSync);
        assertEquals(3, read().getActivityCoins().get(141));
        assertEquals(SCHEDULE, read().getScheduleId());
        data.saveSync();
        var saved = row();
        assertFalse(fault.flushReplacement());
        assertEquals(saved, row());
        assertEquals(20, read().getActivityCoins().get(141));
        assertEquals(SCHEDULE + 2, read().getScheduleId());
        assertEquals(3, data.getSaveStamp().getRevision());
    }

    @Test
    void delayedOriginalWinningBeforeTheBarrierIsRecognized() throws Exception {
        seed();
        var data = instrument(read(), true);
        coins(data, 10);
        data.setScheduleId(SCHEDULE + 1);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, data::saveSync);
        coins(data, 20);
        data.setScheduleId(SCHEDULE + 2);
        fault.beforeAdvance = () -> assertTrue(fault.flushReplacement());
        data.saveSync();
        assertEquals(20, read().getActivityCoins().get(141));
        assertEquals(SCHEDULE + 2, read().getScheduleId());
        assertEquals(3, data.getSaveStamp().getRevision());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aForeignWriterDuringUncertaintyFencesTheOldCopy(boolean ownCommitted) throws Exception {
        seed();
        var data = instrument(read(), true);
        coins(data, 10);
        fault.next = ownCommitted ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, data::saveSync);
        var foreign = read();
        coins(foreign, 30);
        foreign.saveSync();
        var saved = row();
        assertThrows(IllegalStateException.class, data::saveSync);
        assertThrows(IllegalStateException.class, data::saveSync);
        if (!ownCommitted) assertFalse(fault.flushReplacement());
        assertEquals(saved, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainInsertsRetainTheExactOriginalDocument(boolean committed) throws Exception {
        var data = instrument(fresh(), false);
        coins(data, 10);
        fault.next = committed ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, data::saveSync);
        coins(data, 20);
        data.saveSync();
        assertEquals(20, read().getActivityCoins().get(141));
        assertEquals(2, data.getSaveStamp().getRevision());
        if (!committed) {
            var saved = row();
            assertThrows(
                    com.mongodb.MongoWriteException.class,
                    () -> fault.delegate.insert(fault.delayedDocument));
            assertEquals(saved, row());
        }
    }

    @Test
    void unreadablePendingStateCannotPermitAnotherWrite() throws Exception {
        seed();
        var data = instrument(read(), true);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, data::saveSync);
        var before = row();
        int writes = fault.writes;
        fault.unreadable = true;
        assertThrows(IllegalStateException.class, data::saveSync);
        assertEquals(writes, fault.writes);
        assertEquals(before, row());
        fault.unreadable = false;
        data.saveSync();
        assertFalse(fault.flushReplacement());
    }

    @Test
    void recreatedSameIdHasADifferentWriteToken() {
        var stale = seed();
        var old = row();
        store.getDatabase()
                .getCollection("activities")
                .deleteOne(new Document("_id", old.get("_id")));
        var recreated = fresh();
        recreated.setId(stale.getId());
        recreated.saveSync();
        assertEquals(stale.getSaveStamp().getRevision(), recreated.getSaveStamp().getRevision());
        assertNotEquals(stale.getSaveStamp().getToken(), recreated.getSaveStamp().getToken());
        var saved = row();
        assertThrows(IllegalStateException.class, stale::saveSync);
        assertEquals(saved, row());
    }

    @Test
    void replayReplacementInheritsTheConfirmedRevisionAndFencesTheOldObject() {
        var previous = seed();
        var replacement = fresh();
        replacement.setScheduleId(SCHEDULE + 1);
        replacement.getActivityCoins().clear();
        replacement.inheritSaveIdentity(previous);
        replacement.saveSync();
        assertEquals(previous.getId(), replacement.getId());
        assertEquals(2, replacement.getSaveStamp().getRevision());
        var saved = row();
        assertThrows(IllegalStateException.class, previous::saveSync);
        assertEquals(saved, row());
        assertEquals(SCHEDULE + 1, read().getScheduleId());
        assertTrue(read().getActivityCoins().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replayCannotEraseAnUncertainOrFrozenLedger(boolean frozen) throws Exception {
        var data = instrument(seed(), true);
        if (frozen) data.blockUnconfirmedActivityCoins();
        else {
            fault.next = Fault.DEFER;
            assertThrows(IllegalStateException.class, data::saveSync);
        }
        var saved = row();
        var replacement = fresh();
        replacement.setScheduleId(SCHEDULE + 1);
        assertThrows(IllegalStateException.class, () -> replacement.inheritSaveIdentity(data));
        assertEquals(saved, row());
    }

    @Test
    void legacyStringRowsRetainTheirExactIdentity() {
        var data = fresh();
        var oid = new ObjectId();
        data.setId(oid.toHexString());
        var legacy = store.getMapper().toDocument(data);
        legacy.put("_id", oid.toHexString());
        legacy.remove("scheduleId");
        legacy.remove("saveStamp");
        store.getDatabase().getCollection("activities").insertOne(legacy);
        var loaded = read();
        assertEquals(0, loaded.getScheduleId());
        assertEquals(legacy, row());
        loaded.setScheduleId(SCHEDULE);
        loaded.saveSync();
        assertEquals(legacy.get("_id"), row().get("_id"));
        assertEquals(SCHEDULE, read().getScheduleId());
        assertEquals(1, loaded.getSaveStamp().getRevision());
    }

    @Test
    void incompatibleBsonIdentitiesAreRejectedWithoutWriting() {
        var legacy = store.getMapper().toDocument(fresh());
        legacy.put("_id", new ObjectId());
        store.getDatabase().getCollection("activities").insertOne(legacy);
        assertThrows(RuntimeException.class, this::read);
        assertEquals(legacy, row());
    }

    @Test
    void onlyOneIndependentCopyCanCommitTheSameRevision() throws Exception {
        seed();
        var copies = List.of(read(), read());
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                var data = copies.get(i);
                coins(data, 10 + i);
                futures.add(
                        pool.submit(
                                () -> {
                                    assertTrue(go.await(5, TimeUnit.SECONDS));
                                    try {
                                        data.saveSync();
                                        return true;
                                    } catch (IllegalStateException conflict) {
                                        return false;
                                    }
                                }));
            }
            go.countDown();
            assertNotEquals(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(2, read().getSaveStamp().getRevision());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void malformedStoredStampIsRejectedWithoutWriting() {
        var data = fresh();
        data.setId("invalid-stamp");
        var legacy = store.getMapper().toDocument(data);
        legacy.put(
                "saveStamp",
                new Document("revision", -1L).append("token", UUID.randomUUID().toString()));
        store.getDatabase().getCollection("activities").insertOne(legacy);
        assertThrows(RuntimeException.class, this::read);
        assertEquals(legacy, row());
    }

    enum Fault {
        NONE,
        BEFORE,
        AFTER,
        DEFER
    }

    static class FaultStorage implements ActivitySaveGuard.Storage {
        final ActivitySaveGuard.Storage delegate;
        Fault next = Fault.NONE, nextAdvance = Fault.NONE;
        Document delayedFilter, delayedDocument;
        int writes, advances;
        boolean unreadable;
        Runnable beforeAdvance;

        FaultStorage(ActivitySaveGuard.Storage delegate) {
            this.delegate = delegate;
        }

        public Document read(Object id) {
            if (unreadable) throw new IllegalStateException("injected read failure");
            return delegate.read(id);
        }

        public void insert(Document document) {
            writes++;
            var fault = next;
            next = Fault.NONE;
            if (fault == Fault.DEFER) delayedDocument = document;
            if (fault == Fault.BEFORE || fault == Fault.DEFER)
                throw new IllegalStateException("injected before insert");
            delegate.insert(document);
            if (fault == Fault.AFTER)
                throw new IllegalStateException("injected lost insert acknowledgement");
        }

        public boolean replace(Document filter, Document document) {
            writes++;
            var fault = next;
            next = Fault.NONE;
            if (fault == Fault.DEFER) {
                delayedFilter = filter;
                delayedDocument = document;
            }
            if (fault == Fault.BEFORE || fault == Fault.DEFER)
                throw new IllegalStateException("injected before replacement");
            boolean result = delegate.replace(filter, document);
            if (fault == Fault.AFTER)
                throw new IllegalStateException("injected lost replacement acknowledgement");
            return result;
        }

        public boolean advance(Document filter, ActivitySaveStamp stamp) {
            advances++;
            if (beforeAdvance != null) {
                beforeAdvance.run();
                beforeAdvance = null;
            }
            var fault = nextAdvance;
            nextAdvance = Fault.NONE;
            if (fault == Fault.BEFORE) throw new IllegalStateException("injected before barrier");
            boolean result = delegate.advance(filter, stamp);
            if (fault == Fault.AFTER)
                throw new IllegalStateException("injected lost barrier acknowledgement");
            return result;
        }

        boolean flushReplacement() {
            return delegate.replace(delayedFilter, delayedDocument);
        }
    }
}
