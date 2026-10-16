package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import dev.morphia.Datastore;
import dev.morphia.Morphia;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Faults wrap actual Mongo operations; delayed operations retain their original CAS and document.
 */
class PlayerSaveGuardRecoveryTest {
    static final int UID = 74202;
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
                        "lunagc_player_save_recovery_"
                                + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Player.class);
        setStore(store);
        fault = new FaultStorage(new PlayerSaveGuard.MongoStorage(store));
    }

    static void setStore(Datastore value) throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, value);
    }

    @AfterEach
    void close() throws Exception {
        if (store == null) return;
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        setStore(previous);
        store.getDatabase().drop();
        client.close();
    }

    Player fresh() throws Exception {
        var player = new Player();
        player.setUid(UID);
        var team = Player.class.getDeclaredField("teamManager");
        team.setAccessible(true);
        team.set(player, new TeamManager(player));
        player.getTpsAmmunition().put(1003, 3);
        return player;
    }

    Player seed() throws Exception {
        var player = fresh();
        save(player);
        return player;
    }

    Player read() {
        return DatabaseHelper.getPlayerByUid(UID);
    }

    Document row() {
        return store.getDatabase().getCollection("players").find(new Document("_id", UID)).first();
    }

    Player instrument(Player player, boolean loaded) throws Exception {
        var field = Player.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        field.set(
                player,
                new PlayerSaveGuard(player, player.getSaveStamp(), loaded, ignored -> fault));
        return player;
    }

    void save(Player player) {
        DatabaseHelper.saveGameSync(player);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aLostWriteAcknowledgementCannotConsumeAnUnrelatedRevision(boolean committed)
            throws Exception {
        seed();
        var player = instrument(read(), true);
        player.getTpsAmmunition().put(1003, 10);
        fault.next = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, () -> save(player));
        assertEquals(committed ? 10 : 3, read().getTpsAmmunition().get(1003));
        player.getTpsAmmunition().put(1003, 20);
        save(player);
        assertEquals(20, read().getTpsAmmunition().get(1003));
        assertEquals(committed ? 0 : 1, fault.advances);
        assertEquals(3, player.getSaveStamp().getRevision());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUncertainBarrierCanBeRetriedWithoutOverwritingTheBusinessFields(boolean committed)
            throws Exception {
        seed();
        var player = instrument(read(), true);
        player.getTpsAmmunition().put(1003, 10);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(player));
        player.getTpsAmmunition().put(1003, 20);
        fault.nextAdvance = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, () -> save(player));
        assertEquals(3, read().getTpsAmmunition().get(1003));
        save(player);
        var committedRow = row();
        assertFalse(fault.flushReplacement());
        assertEquals(committedRow, row());
        assertEquals(20, read().getTpsAmmunition().get(1003));
        assertEquals(3, player.getSaveStamp().getRevision());
    }

    @Test
    void aDelayedOriginalWinningBeforeTheBarrierIsRecognizedAsOurOwnWrite() throws Exception {
        seed();
        var player = instrument(read(), true);
        player.getTpsAmmunition().put(1003, 10);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(player));
        player.getTpsAmmunition().put(1003, 20);
        fault.beforeAdvance = () -> assertTrue(fault.flushReplacement());
        save(player);
        assertEquals(20, read().getTpsAmmunition().get(1003));
        assertEquals(3, player.getSaveStamp().getRevision());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aForeignWriterDuringUncertaintyFencesTheOldCopy(boolean ownCommitted) throws Exception {
        seed();
        var player = instrument(read(), true);
        player.getTpsAmmunition().put(1003, 10);
        fault.next = ownCommitted ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(player));
        var foreign = read();
        foreign.getTpsAmmunition().put(1003, 30);
        save(foreign);
        var committed = row();
        assertThrows(IllegalStateException.class, () -> save(player));
        assertThrows(IllegalStateException.class, () -> save(player));
        if (!ownCommitted) assertFalse(fault.flushReplacement());
        assertEquals(committed, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainNewInsertsRetainTheirExactIdentityUntilConfirmed(boolean committed)
            throws Exception {
        var player = instrument(fresh(), false);
        player.getTpsAmmunition().put(1003, 10);
        fault.next = committed ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(player));
        player.getTpsAmmunition().put(1003, 20);
        save(player);
        assertEquals(20, read().getTpsAmmunition().get(1003));
        assertEquals(2, player.getSaveStamp().getRevision());
        if (!committed) {
            var saved = row();
            assertThrows(
                    com.mongodb.MongoWriteException.class,
                    () -> fault.delegate.insert(fault.delayedDocument));
            assertEquals(saved, row());
        }
    }

    @Test
    void anUnreadableUncertainStateDoesNotPermitAnotherWrite() throws Exception {
        seed();
        var player = instrument(read(), true);
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(player));
        var before = row();
        int writes = fault.writes;
        fault.unreadable = true;
        assertThrows(IllegalStateException.class, () -> save(player));
        assertEquals(writes, fault.writes);
        assertEquals(before, row());
        fault.unreadable = false;
        save(player);
        assertFalse(fault.flushReplacement());
    }

    @Test
    void aRecreatedUidAtTheSameRevisionHasADifferentWriteIdentity() throws Exception {
        seed();
        var stale = read();
        store.getDatabase().getCollection("players").deleteOne(new Document("_id", UID));
        var recreated = seed();
        assertEquals(stale.getSaveStamp().getRevision(), recreated.getSaveStamp().getRevision());
        assertNotEquals(stale.getSaveStamp().getToken(), recreated.getSaveStamp().getToken());
        var committed = row();
        assertThrows(IllegalStateException.class, () -> save(stale));
        assertEquals(committed, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldDocumentsGetTheirFirstStampOnlyThroughAnExplicitConditionalSave(boolean explicitNull)
            throws Exception {
        var legacy = store.getMapper().toDocument(fresh());
        legacy.remove("saveStamp");
        if (explicitNull) legacy.put("saveStamp", null);
        store.getDatabase().getCollection("players").insertOne(legacy);
        var stale = read();
        var current = read();
        assertEquals(legacy, row());
        assertNull(current.getSaveStamp());
        current.getTpsAmmunition().put(1003, 30);
        save(current);
        assertEquals(1, read().getSaveStamp().getRevision());
        var committed = row();
        assertThrows(IllegalStateException.class, () -> save(stale));
        assertEquals(committed, row());
    }

    @Test
    void onlyOneOfTwoIndependentCopiesCanCommitTheSameReadRevision() throws Exception {
        seed();
        var first = read();
        var second = read();
        first.getTpsAmmunition().put(1003, 10);
        second.getTpsAmmunition().put(1003, 20);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<Future<Boolean>>();
            for (var player : List.of(first, second))
                tasks.add(
                        pool.submit(
                                () -> {
                                    assertTrue(go.await(5, TimeUnit.SECONDS));
                                    try {
                                        save(player);
                                        return true;
                                    } catch (IllegalStateException conflict) {
                                        return false;
                                    }
                                }));
            go.countDown();
            assertNotEquals(
                    tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(2, read().getSaveStamp().getRevision());
            assertTrue(Set.of(10, 20).contains(read().getTpsAmmunition().get(1003)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void changingTheUidOfALoadedCopyCannotCreateOrOverwriteAnotherPlayer() throws Exception {
        seed();
        var player = read();
        var committed = row();
        player.setUid(UID + 1);
        assertThrows(IllegalStateException.class, () -> save(player));
        assertEquals(committed, row());
        assertNull(DatabaseHelper.getPlayerByUid(UID + 1));
    }

    enum Fault {
        NONE,
        BEFORE,
        AFTER,
        DEFER
    }

    static class FaultStorage implements PlayerSaveGuard.Storage {
        final PlayerSaveGuard.Storage delegate;
        Fault next = Fault.NONE, nextAdvance = Fault.NONE;
        Document delayedFilter, delayedDocument;
        int advances, writes;
        boolean unreadable;
        Runnable beforeAdvance;

        FaultStorage(PlayerSaveGuard.Storage delegate) {
            this.delegate = delegate;
        }

        public Document read(int uid) {
            if (unreadable) throw new IllegalStateException("injected read failure");
            return delegate.read(uid);
        }

        public void insert(Document document) {
            writes++;
            Fault fault = next;
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
            Fault fault = next;
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

        public boolean advance(Document filter, PlayerSaveStamp stamp) {
            advances++;
            if (beforeAdvance != null) {
                beforeAdvance.run();
                beforeAdvance = null;
            }
            Fault fault = nextAdvance;
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
