package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import dev.morphia.Datastore;
import dev.morphia.Morphia;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;

import org.bson.Document;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/** Real Mongo writes through the same synchronous and queued entry points used by gameplay. */
class PlayerSaveFenceMongoTest {
    private static final int UID = 74201;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private MongoClient client;
    private Datastore store, previous;

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
                        "lunagc_player_fence_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Player.class);
        setStore(store);
    }

    private static void setStore(Datastore value) throws Exception {
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

    private Player fresh() throws Exception {
        var player = new Player();
        player.setUid(UID);
        var field = Player.class.getDeclaredField("teamManager");
        field.setAccessible(true);
        field.set(player, new TeamManager(player));
        player.getTpsAmmunition().put(1003, 3);
        player.getArtifactTransmuterData().setProgressUnits(1500);
        return player;
    }

    private Player seed() throws Exception {
        var player = fresh();
        DatabaseHelper.saveGameSync(player);
        return player;
    }

    private Player read() {
        return DatabaseHelper.getPlayerByUid(UID);
    }

    private Document row() {
        return store.getDatabase().getCollection("players").find(new Document("_id", UID)).first();
    }

    @Test
    void anExplicitOldCopyCannotReplaceANewerWholePlayerResult() throws Exception {
        seed();
        var stale = read();
        var current = read();
        current.getTpsAmmunition().put(1003, 19);
        current.getArtifactTransmuterData().setProgressUnits(4500);
        DatabaseHelper.saveGameSync(current);
        var committed = row();
        stale.getArtifactTransmuterData().setProgressUnits(2000);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(stale));
        assertEquals(committed, row());
    }

    @Test
    void aQueuedOldCopyCannotRollBackANewerAcknowledgedWrite() throws Exception {
        seed();
        var stale = read();
        var current = read();
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        Document committed;
        try (var held = new HeldQueue()) {
            stale.save();
            current.getTpsAmmunition().put(1003, 20);
            DatabaseHelper.saveGameSync(current);
            committed = row();
        }
        assertEquals(committed, row());
        assertEquals(failures + 1, DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    @Test
    void aLoadedCopyCannotResurrectADeletedPlayer() throws Exception {
        seed();
        var copy = read();
        store.getDatabase().getCollection("players").deleteOne(new Document("_id", UID));
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(copy));
        assertNull(row());
    }

    @Test
    void aFreshObjectWithTheSameUidCannotOverwriteAnExistingPlayer() throws Exception {
        seed();
        var committed = row();
        var duplicate = fresh();
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(duplicate));
        assertEquals(committed, row());
    }

    @Test
    void readingAnOldDocumentDoesNotWriteOrQueueAMigration() throws Exception {
        var legacy = store.getMapper().toDocument(fresh());
        legacy.remove("saveStamp");
        store.getDatabase().getCollection("players").insertOne(legacy);
        long completed = DatabaseHelper.getEventExecutor().snapshot().completed();
        assertNotNull(read());
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(completed, DatabaseHelper.getEventExecutor().snapshot().completed());
        assertEquals(legacy, row());
    }

    @Test
    void aQueuedSaveOfTheSameLiveInstanceUsesItsLatestConfirmedState() throws Exception {
        var player = seed();
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        try (var held = new HeldQueue()) {
            player.save();
            player.getTpsAmmunition().put(1003, 21);
            DatabaseHelper.saveGameSync(player);
            player.getArtifactTransmuterData().setProgressUnits(5100);
        }
        var cold = read();
        assertEquals(21, cold.getTpsAmmunition().get(1003));
        assertEquals(5100, cold.getArtifactTransmuterData().getProgressUnits());
        assertEquals(failures, DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    @Test
    void anEncodingRaceRetriesWithoutConsumingARevisionOrLosingTheQueuedSave() throws Exception {
        var player = seed();
        var values =
                new HashMap<Integer, Integer>() {
                    boolean fail = true;

                    @Override
                    public Set<Map.Entry<Integer, Integer>> entrySet() {
                        if (fail) {
                            fail = false;
                            throw new ConcurrentModificationException("injected encoder race");
                        }
                        return super.entrySet();
                    }
                };
        values.put(1003, 22);
        var field = Player.class.getDeclaredField("tpsAmmunition");
        field.setAccessible(true);
        field.set(player, values);
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        player.save();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(22, read().getTpsAmmunition().get(1003));
        assertEquals(2, read().getSaveStamp().getRevision());
        assertEquals(failures, DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    @Test
    void queuedPlayerSavesRetainTheirCapturedDatabaseAndRejectLaterRedirection() throws Exception {
        var player = seed();
        var other =
                Morphia.createDatastore(
                        client,
                        "lunagc_player_fence_scope_"
                                + UUID.randomUUID().toString().replace("-", ""));
        other.getMapper().map(Player.class);
        try {
            try (var held = new HeldQueue()) {
                player.getTpsAmmunition().put(1003, 23);
                player.save();
                setStore(other);
            }
            assertEquals(0, other.getDatabase().getCollection("players").countDocuments());
            assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveGameSync(player));
            setStore(store);
            assertEquals(23, read().getTpsAmmunition().get(1003));
        } finally {
            setStore(store);
            other.getDatabase().drop();
        }
    }

    @Test
    void aDuplicateNewPlayerQueuedSaveIsReportedInsteadOfRetriedAsAReplacement() throws Exception {
        seed();
        var committed = row();
        var duplicate = fresh();
        long failures = DatabaseHelper.getEventExecutor().snapshot().failed();
        duplicate.save();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(failures + 1, DatabaseHelper.getEventExecutor().snapshot().failed());
        assertEquals(committed, row());
    }

    @Test
    void newPlayerUidAllocationConfirmsCreationBeforeReturningToLogin() throws Exception {
        var player = fresh();
        try (var held = new HeldQueue()) {
            DatabaseHelper.generatePlayerUid(player, UID);
            assertNotNull(row());
            assertEquals(1, player.getSaveStamp().getRevision());
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
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        }
    }
}
