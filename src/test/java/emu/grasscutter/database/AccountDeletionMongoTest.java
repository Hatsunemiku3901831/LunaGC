package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSessionManager;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;

/** Destructive calls are restricted to fresh, isolated fixture databases. */
class AccountDeletionMongoTest {
    private static final int UID = 65123;
    private static final String ACCOUNT_ID = "delete-fixture";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Map<String, String> OWNERS =
            Map.ofEntries(
                    Map.entry("achievements", "uid"), Map.entry("activities", "uid"),
                    Map.entry("homes", "ownerUid"), Map.entry("scene_buildings", "ownerUid"),
                    Map.entry("mail", "ownerUid"), Map.entry("avatars", "ownerId"),
                    Map.entry("gachas", "ownerId"), Map.entry("items", "ownerId"),
                    Map.entry("quests", "ownerUid"), Map.entry("battlepass", "ownerUid"),
                    Map.entry("dailytasks", "ownerUid"), Map.entry("group_instances", "ownerUid"),
                    Map.entry("music_game_beatmaps", "authorUid"),
                            Map.entry("open_world_spawns", "ownerUid"));
    private MongoClient client;
    private Datastore game, accounts;
    private Field gameField, accountField, serverField;
    private Object oldGame, oldAccounts, oldServer;
    private ServerRunMode oldMode;
    private Account target;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        gameField = field(DatabaseManager.class, "gameDatastore");
        accountField = field(DatabaseManager.class, "dispatchDatastore");
        serverField = field(Grasscutter.class, "gameServer");
        oldGame = gameField.get(null);
        oldAccounts = accountField.get(null);
        oldServer = serverField.get(null);
        oldMode = Grasscutter.getRunMode();
        client = MongoClients.create(uri);
        String id = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(client, "lunagc_account_delete_game_" + id);
        accounts = Morphia.createDatastore(client, "lunagc_account_delete_accounts_" + id);
        game.getMapper().map(Player.class, Account.class);
        accounts.getMapper().map(Account.class);
        gameField.set(null, game);
        accountField.set(null, accounts);
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        var server = new GameServer();
        field(GameServer.class, "players").set(server, new ConcurrentHashMap<>());
        serverField.set(null, server);
        target = new Account();
        target.setId(ACCOUNT_ID);
        target.setUsername("delete-fixture");
        accounts.insert(target);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @AfterEach
    void close() throws Exception {
        if (client == null) return;
        try {
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        } finally {
            gameField.set(null, oldGame);
            accountField.set(null, oldAccounts);
            serverField.set(null, oldServer);
            Grasscutter.setRunModeOverride(oldMode);
            game.getDatabase().drop();
            accounts.getDatabase().drop();
            client.close();
        }
    }

    private MongoCollection<Document> rows(String name) {
        return game.getDatabase().getCollection(name);
    }

    private MongoCollection<Document> accountRows() {
        return accounts.getDatabase().getCollection("accounts");
    }

    private void profile(boolean decodable) {
        var row = new Document("_id", UID).append("accountId", ACCOUNT_ID);
        if (decodable) row.append("teamManager", new Document());
        rows("players").insertOne(row);
    }

    private void drainedDelete() throws Exception {
        DatabaseHelper.deleteAccount(target);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
    }

    @Test
    void anAccountWithoutAPlayerIsStillDeleted() throws Exception {
        drainedDelete();
        assertEquals(0, accountRows().countDocuments(new Document("_id", ACCOUNT_ID)));
    }

    @Test
    void offlineDeletionDoesNotRequireAnInitializedGameServer() throws Exception {
        serverField.set(null, null);
        assertDoesNotThrow(() -> DatabaseHelper.deleteAccount(target));
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(0, accountRows().countDocuments());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "achievements",
                "activities",
                "homes",
                "scene_buildings",
                "mail",
                "avatars",
                "gachas",
                "items",
                "quests",
                "battlepass",
                "dailytasks",
                "group_instances",
                "music_game_beatmaps",
                "open_world_spawns"
            })
    void cleanupRemovesEveryKnownOwnedCollectionAndPreservesOtherPlayers(String collection)
            throws Exception {
        profile(true);
        String owner = OWNERS.get(collection);
        rows(collection).insertOne(new Document(owner, UID).append("payload", "remove"));
        rows(collection).insertOne(new Document(owner, UID + 1).append("payload", "keep"));
        drainedDelete();
        assertEquals(0, rows(collection).countDocuments(new Document(owner, UID)), collection);
        assertEquals(1, rows(collection).countDocuments(new Document(owner, UID + 1)), collection);
        assertEquals(0, rows("players").countDocuments(new Document("_id", UID)));
        assertEquals(0, accountRows().countDocuments(new Document("_id", ACCOUNT_ID)));
    }

    @Test
    void rawOwnershipCanBeReadWithoutRunningPlayerLoadCallbacks() throws Exception {
        profile(false);
        assertDoesNotThrow(() -> DatabaseHelper.deleteAccount(target));
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(0, rows("players").countDocuments());
        assertEquals(0, accountRows().countDocuments());
    }

    @Test
    void bothDirectionsOfFriendshipAreRemovedWithoutAffectingUnrelatedFriends() throws Exception {
        profile(true);
        rows("friendships")
                .insertMany(
                        List.of(
                                new Document("ownerId", UID).append("friendId", UID + 1),
                                new Document("ownerId", UID + 1).append("friendId", UID),
                                new Document("ownerId", UID + 1).append("friendId", UID + 2)));
        drainedDelete();
        assertEquals(1, rows("friendships").countDocuments());
        assertEquals(UID + 2, rows("friendships").find().first().getInteger("friendId"));
    }

    @Test
    void uidOwnershipRecordsAndOtherAccountsSurviveCleanup() throws Exception {
        profile(true);
        String owner = UUID.randomUUID().toString();
        accounts.getDatabase()
                .getCollection("account_uid_owners")
                .insertOne(new Document("_id", ACCOUNT_ID).append("ownerId", owner));
        rows("player_uid_reservations")
                .insertOne(
                        new Document("_id", UID)
                                .append("ownerId", owner)
                                .append("operationId", UUID.randomUUID().toString())
                                .append("kind", "account"));
        Document before = rows("player_uid_reservations").find().first();
        accountRows().insertOne(new Document("_id", "another-account").append("username", "keep"));
        drainedDelete();
        assertEquals(before, rows("player_uid_reservations").find().first());
        assertEquals(
                1, accounts.getDatabase().getCollection("account_uid_owners").countDocuments());
        assertEquals(1, accountRows().countDocuments());
        assertEquals("another-account", accountRows().find().first().getString("_id"));
    }

    @Test
    void deletionWaitsForAnAcceptedAccountSaveAndFinishesBeforeReturning() throws Exception {
        profile(true);
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queue = DatabaseHelper.getEventExecutorAccount();
        var maintenance = Executors.newSingleThreadExecutor();
        for (int i = 0; i < queue.getCorePoolSize(); i++)
            queue.submit(
                    () -> {
                        ready.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        try {
            target.setEmail("pending@fixture.invalid");
            target.save();
            var started = new CountDownLatch(1);
            var deleting =
                    maintenance.submit(
                            () -> {
                                started.countDown();
                                DatabaseHelper.deleteAccount(target);
                            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(DatabaseHelper.getEventExecutor().awaitIdle(TIMEOUT));
            // The queued account write must finish before deletion can complete.
            assertThrows(TimeoutException.class, () -> deleting.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            deleting.get(10, TimeUnit.SECONDS);
            assertEquals(0, accountRows().countDocuments(new Document("_id", ACCOUNT_ID)));
        } finally {
            release.countDown();
            maintenance.shutdownNow();
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        }
    }

    @Test
    void confirmedUidOwnershipAlsoCleansDataWhenNoPlayerDocumentRemains() throws Exception {
        String owner = UUID.randomUUID().toString();
        accounts.getDatabase()
                .getCollection("account_uid_owners")
                .insertOne(new Document("_id", ACCOUNT_ID).append("ownerId", owner));
        rows("player_uid_reservations")
                .insertOne(
                        new Document("_id", UID)
                                .append("ownerId", owner)
                                .append("operationId", UUID.randomUUID().toString())
                                .append("kind", "account"));
        rows("items").insertOne(new Document("ownerId", UID).append("itemId", 202));
        rows("dailytasks").insertOne(new Document("ownerUid", UID));
        Document claim = rows("player_uid_reservations").find().first();
        drainedDelete();
        assertEquals(0, rows("items").countDocuments());
        assertEquals(0, rows("dailytasks").countDocuments());
        assertEquals(claim, rows("player_uid_reservations").find().first());
        assertEquals(0, accountRows().countDocuments());
    }

    static Stream<Object> invalidUids() {
        return Stream.of(
                0,
                -1,
                emu.grasscutter.GameConstants.SERVER_CONSOLE_UID,
                (long) Integer.MAX_VALUE + 1,
                "65123");
    }

    @ParameterizedTest
    @MethodSource("invalidUids")
    void invalidProfileIdsRefuseCleanupBeforeAnyDelete(Object uid) {
        rows("players").insertOne(new Document("_id", uid).append("accountId", ACCOUNT_ID));
        rows("items").insertOne(new Document("ownerId", uid));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.deleteAccount(target));
        assertEquals(1, rows("players").countDocuments());
        assertEquals(1, rows("items").countDocuments());
        assertEquals(1, accountRows().countDocuments());
    }

    @Test
    void allLegacyProfilesOfTheExplicitAccountAreCleanedTogether() throws Exception {
        profile(false);
        rows("players").insertOne(new Document("_id", UID + 1).append("accountId", ACCOUNT_ID));
        rows("players").insertOne(new Document("_id", UID + 2).append("accountId", "unrelated"));
        rows("items")
                .insertMany(
                        List.of(
                                new Document("ownerId", UID),
                                new Document("ownerId", UID + 1),
                                new Document("ownerId", UID + 2)));
        drainedDelete();
        assertEquals(1, rows("players").countDocuments());
        assertEquals(1, rows("items").countDocuments());
        assertEquals(UID + 2, rows("items").find().first().getInteger("ownerId"));
    }

    @Test
    void foreignUidMetadataRefusesTheEntireCleanup() {
        profile(false);
        String ours = UUID.randomUUID().toString();
        String theirs = UUID.randomUUID().toString();
        accounts.getDatabase()
                .getCollection("account_uid_owners")
                .insertOne(new Document("_id", ACCOUNT_ID).append("ownerId", ours));
        rows("player_uid_reservations")
                .insertOne(
                        new Document("_id", UID)
                                .append("ownerId", theirs)
                                .append("operationId", UUID.randomUUID().toString())
                                .append("kind", "account"));
        rows("items").insertOne(new Document("ownerId", UID));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.deleteAccount(target));
        assertEquals(1, accountRows().countDocuments());
        assertEquals(1, rows("items").countDocuments());
        assertEquals(1, rows("players").countDocuments());
    }

    @Test
    void anOwnedReservationCannotEraseAnotherAccountsProfile() {
        String owner = UUID.randomUUID().toString();
        accounts.getDatabase()
                .getCollection("account_uid_owners")
                .insertOne(new Document("_id", ACCOUNT_ID).append("ownerId", owner));
        rows("player_uid_reservations")
                .insertOne(
                        new Document("_id", UID)
                                .append("ownerId", owner)
                                .append("operationId", UUID.randomUUID().toString())
                                .append("kind", "account"));
        rows("players").insertOne(new Document("_id", UID).append("accountId", "another-account"));
        rows("mail").insertOne(new Document("ownerUid", UID));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.deleteAccount(target));
        assertEquals(1, accountRows().countDocuments());
        assertEquals(1, rows("mail").countDocuments());
        assertEquals(1, rows("players").countDocuments());
    }

    private Player online(Runnable closed) throws Exception {
        var server = (GameServer) serverField.get(null);
        var player = new Player();
        player.setUid(UID);
        player.setAccount(target);
        field(Player.class, "accountId").set(player, ACCOUNT_ID);
        player.setSession(
                new GameSession(server) {
                    @Override
                    public void close() {
                        closed.run();
                    }
                });
        server.getPlayers().put(UID, player);
        return player;
    }

    @Test
    void cleanupWaitsForLogoutAndItsFinalQueuedSaves() throws Exception {
        profile(false);
        var jobs = Executors.newFixedThreadPool(2);
        var requested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = (GameServer) serverField.get(null);
        var closes = new AtomicInteger();
        online(
                () -> {
                    closes.incrementAndGet();
                    requested.countDown();
                    jobs.submit(
                            () -> {
                                try {
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException e) {
                                    throw new AssertionError(e);
                                }
                                DatabaseHelper.asyncOperation(
                                        () ->
                                                rows("mail")
                                                        .insertOne(new Document("ownerUid", UID)));
                                server.getPlayers().remove(UID);
                            });
                });
        try {
            var deleting = jobs.submit(() -> DatabaseHelper.deleteAccount(target));
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> deleting.get(30, TimeUnit.MILLISECONDS));
            assertEquals(1, accountRows().countDocuments());
            release.countDown();
            deleting.get(10, TimeUnit.SECONDS);
            assertEquals(1, closes.get());
            assertEquals(0, rows("mail").countDocuments());
            assertEquals(0, accountRows().countDocuments());
        } finally {
            release.countDown();
            jobs.shutdownNow();
        }
    }

    @Test
    void anUnfinishedLogoutFailsWithoutRemovingStoredData() throws Exception {
        profile(false);
        var closes = new AtomicInteger();
        online(closes::incrementAndGet);
        assertThrows(
                IllegalStateException.class,
                () -> AccountDeletionService.delete(target, Duration.ofMillis(20), TIMEOUT));
        assertEquals(1, closes.get());
        assertEquals(1, rows("players").countDocuments());
        assertEquals(1, accountRows().countDocuments());
    }

    @Test
    void theGameLogicThreadRefusesToBlockItsOwnLogoutCallback() throws Exception {
        profile(false);
        var closes = new AtomicInteger();
        online(closes::incrementAndGet);
        GameSessionManager.getLogicThread()
                .submit(
                        () ->
                                assertThrows(
                                        IllegalStateException.class,
                                        () -> DatabaseHelper.deleteAccount(target)))
                .get(5, TimeUnit.SECONDS);
        assertEquals(0, closes.get());
        assertEquals(1, rows("players").countDocuments());
        assertEquals(1, accountRows().countDocuments());
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failed) {
            throw failed.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private MongoCollection<Document> observed(
            MongoCollection<Document> actual, String write, boolean after, AtomicBoolean armed) {
        return (MongoCollection<Document>)
                Proxy.newProxyInstance(
                        MongoCollection.class.getClassLoader(),
                        new Class<?>[] {MongoCollection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals(write)
                                    && armed.compareAndSet(true, false)) {
                                if (after) call(actual, method, args);
                                throw new IllegalStateException("Deletion ACK unavailable");
                            }
                            Object result = call(actual, method, args);
                            return result instanceof MongoCollection<?> collection
                                    ? observed(
                                            (MongoCollection<Document>) collection,
                                            write,
                                            after,
                                            armed)
                                    : result;
                        });
    }

    private Datastore observe(Datastore actual, String collection, String write, boolean after) {
        var armed = new AtomicBoolean(true);
        var database =
                (MongoDatabase)
                        Proxy.newProxyInstance(
                                MongoDatabase.class.getClassLoader(),
                                new Class<?>[] {MongoDatabase.class},
                                (proxy, method, args) -> {
                                    Object result = call(actual.getDatabase(), method, args);
                                    return method.getName().equals("getCollection")
                                                    && args[0].equals(collection)
                                            ? observed(
                                                    (MongoCollection<Document>) result,
                                                    write,
                                                    after,
                                                    armed)
                                            : result;
                                });
        return (Datastore)
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) ->
                                method.getName().equals("getDatabase")
                                        ? database
                                        : call(actual, method, args));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptedCollectionDeletionRetainsTheAccountAndCanBeRetried(boolean after)
            throws Exception {
        profile(false);
        rows("items").insertOne(new Document("ownerId", UID));
        gameField.set(null, observe(game, "items", "deleteMany", after));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.deleteAccount(target));
        assertEquals(1, accountRows().countDocuments());
        assertEquals(1, rows("players").countDocuments());
        assertEquals(after ? 0 : 1, rows("items").countDocuments());
        assertFalse(DatabaseWriteBarrier.maintenanceActive());
        gameField.set(null, game);
        drainedDelete();
        assertEquals(0, accountRows().countDocuments());
        assertEquals(0, rows("items").countDocuments());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accountDeleteAcknowledgementFailureCanBeConfirmedByAnIdempotentRetry(boolean after)
            throws Exception {
        profile(false);
        accountField.set(null, observe(accounts, "accounts", "deleteOne", after));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.deleteAccount(target));
        assertEquals(after ? 0 : 1, accountRows().countDocuments());
        assertEquals(0, rows("players").countDocuments());
        accountField.set(null, accounts);
        drainedDelete();
        assertEquals(0, accountRows().countDocuments());
    }

    @Test
    void aDirectAcknowledgedSaveFinishesBeforeCleanupBegins() throws Exception {
        profile(false);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var proxy =
                (Datastore)
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (ignored, method, args) -> {
                                    if (method.getName().equals("save")) {
                                        entered.countDown();
                                        assertTrue(release.await(5, TimeUnit.SECONDS));
                                    }
                                    return call(game, method, args);
                                });
        gameField.set(null, proxy);
        var building = new SceneBuilding(UID, 3, 1, new Position(), new Position(), true);
        var jobs = Executors.newFixedThreadPool(2);
        try {
            var saving = jobs.submit(() -> DatabaseHelper.saveGameSync(building));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var deleting = jobs.submit(() -> DatabaseHelper.deleteAccount(target));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (!DatabaseWriteBarrier.maintenanceActive() && System.nanoTime() < deadline)
                Thread.sleep(1);
            assertTrue(DatabaseWriteBarrier.maintenanceActive());
            assertThrows(TimeoutException.class, () -> deleting.get(30, TimeUnit.MILLISECONDS));
            release.countDown();
            saving.get(5, TimeUnit.SECONDS);
            deleting.get(10, TimeUnit.SECONDS);
            assertEquals(0, rows("scene_buildings").countDocuments());
            assertEquals(0, accountRows().countDocuments());
        } finally {
            release.countDown();
            jobs.shutdownNow();
        }
    }
}
