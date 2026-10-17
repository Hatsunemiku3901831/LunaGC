package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;
import dev.morphia.annotations.Entity;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.*;

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
import java.util.function.Consumer;
import java.util.stream.Stream;

/** UID admission must reserve persistent ownership before a Player document exists. */
class PlayerUidReservationMongoTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private MongoClient client;
    private Datastore game, dispatch;
    private Object oldGame, oldDispatch;
    private ServerRunMode oldMode;
    private Field gameField, dispatchField;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        gameField = DatabaseManager.class.getDeclaredField("gameDatastore");
        dispatchField = DatabaseManager.class.getDeclaredField("dispatchDatastore");
        gameField.setAccessible(true);
        dispatchField.setAccessible(true);
        oldGame = gameField.get(null);
        oldDispatch = dispatchField.get(null);
        oldMode = Grasscutter.getRunMode();
        client = MongoClients.create(uri);
        String id = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(client, "lunagc_uid_game_" + id);
        dispatch = Morphia.createDatastore(client, "lunagc_uid_dispatch_" + id);
        for (var store : List.of(game, dispatch))
            store.getMapper().map(Account.class, DatabaseCounter.class);
        gameField.set(null, game);
        dispatchField.set(null, dispatch);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
    }

    @AfterEach
    void close() throws Exception {
        if (client == null) return;
        try {
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        } finally {
            gameField.set(null, oldGame);
            dispatchField.set(null, oldDispatch);
            Grasscutter.setRunModeOverride(oldMode);
            game.getDatabase().drop();
            dispatch.getDatabase().drop();
            client.close();
        }
    }

    @Test
    void anExplicitUidIsUnavailableToTheNextAllocationBeforeAnyPlayerWasSaved() {
        int first = DatabaseHelper.getNextPlayerId(18001);
        int second = DatabaseHelper.getNextPlayerId(18001);
        assertEquals(18001, first);
        assertNotEquals(first, second);
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void autoAllocationCannotUseAnUnbuiltAccountsSpecifiedUid() {
        assertNotNull(DatabaseHelper.createAccountWithUid("specified", 10001));
        assertNotEquals(10001, DatabaseHelper.getNextPlayerId(0));
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void aNewSpecifiedUidAccountCannotTakeAnAlreadyReservedAnonymousUid() {
        assertEquals(18002, DatabaseHelper.getNextPlayerId(18002));
        assertNull(DatabaseHelper.createAccountWithUid("too-late", 18002));
    }

    @Test
    void reservationSurvivesReplacingTheDatastoreHandle() throws Exception {
        assertEquals(18003, DatabaseHelper.getNextPlayerId(18003));
        var reopened = Morphia.createDatastore(client, game.getDatabase().getName());
        reopened.getMapper().map(Account.class, DatabaseCounter.class);
        gameField.set(null, reopened);
        assertNotEquals(18003, DatabaseHelper.getNextPlayerId(18003));
    }

    @Test
    void theConsoleUidIsNotAllocatedToAPlayer() {
        assertNotEquals(
                GameConstants.SERVER_CONSOLE_UID,
                DatabaseHelper.getNextPlayerId(GameConstants.SERVER_CONSOLE_UID));
    }

    @Test
    void aMissingCounterStartsAboveExistingPlayerIdsWithoutDecodingTheirProfiles() {
        game.getDatabase()
                .getCollection("players")
                .insertOne(new Document("_id", 10008).append("accountId", "historic-account"));
        assertEquals(10009, DatabaseHelper.getNextPlayerId(0));
        assertEquals(1, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void separateDispatchReservationsAlsoProtectAutomaticPlayerUids() {
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        assertNotNull(DatabaseHelper.createAccountWithUid("dispatch-specified", 10001));
        assertNotEquals(10001, DatabaseHelper.getNextPlayerId(0));
        assertEquals(0, game.getDatabase().getCollection("accounts").countDocuments());
    }

    private MongoCollection<Document> claims() {
        return game.getDatabase().getCollection("player_uid_reservations");
    }

    private MongoCollection<Document> owners() {
        return game.getDatabase().getCollection("account_uid_owners");
    }

    private <T extends Player> T fresh(T player, Account account) {
        try {
            var field = Player.class.getDeclaredField("accountId");
            field.setAccessible(true);
            field.set(player, account.getId());
            player.setAccount(account);
            var team = Player.class.getDeclaredField("teamManager");
            team.setAccessible(true);
            team.set(player, new TeamManager(player));
            return player;
        } catch (ReflectiveOperationException failed) {
            throw new AssertionError(failed);
        }
    }

    private Player fresh(Account account) {
        return fresh(new Player(), account);
    }

    @Test
    void repeatedAndColdAccountReservationsKeepOneOwnedUid() throws Exception {
        var account = DatabaseHelper.createAccountWithUid("stable", 18300);
        var first = PlayerUidAllocator.reserve(account);
        assertEquals(18300, first.uid());
        Document before = claims().find().first();
        var reopened = Morphia.createDatastore(client, game.getDatabase().getName());
        reopened.getMapper().map(Account.class, DatabaseCounter.class);
        gameField.set(null, reopened);
        var second = PlayerUidAllocator.reserve(DatabaseHelper.getAccountById(account.getId()));
        assertEquals(first.uid(), second.uid());
        assertEquals(before, claims().find().first());
        assertEquals(1, claims().countDocuments());
        assertEquals(1, owners().countDocuments());
    }

    @Test
    void parallelReservationsForOneAccountConvergeBeforeAnyProfileExists() throws Exception {
        var account = DatabaseHelper.createAccount("parallel-owner");
        var pool = Executors.newFixedThreadPool(6);
        try {
            var start = new CountDownLatch(1);
            List<Future<Integer>> pending = new ArrayList<>();
            for (int i = 0; i < 6; i++)
                pending.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return PlayerUidAllocator.reserve(account).uid();
                                }));
            start.countDown();
            Set<Integer> results = new HashSet<>();
            for (var task : pending) results.add(task.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.size());
            assertEquals(1, claims().countDocuments());
            assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelFirstLoginsCreateExactlyOneGuardedProfile() throws Exception {
        var account = DatabaseHelper.createAccount("parallel-profile");
        var pool = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(2);
        try {
            List<Future<Player>> pending = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                pending.add(
                        pool.submit(
                                () ->
                                        DatabaseHelper.getOrCreatePlayer(
                                                account,
                                                Player.class,
                                                () -> {
                                                    entered.countDown();
                                                    try {
                                                        assertTrue(
                                                                entered.await(5, TimeUnit.SECONDS));
                                                    } catch (InterruptedException e) {
                                                        throw new AssertionError(e);
                                                    }
                                                    return fresh(account);
                                                })));
            var first = pending.get(0).get(15, TimeUnit.SECONDS);
            var second = pending.get(1).get(15, TimeUnit.SECONDS);
            assertEquals(first.getUid(), second.getUid());
            assertEquals(account.getId(), first.getAccountId());
            assertEquals(account.getId(), second.getAccountId());
            assertEquals(1, game.getDatabase().getCollection("players").countDocuments());
            assertEquals(1, first.getSaveStamp().getRevision());
            assertEquals(1, second.getSaveStamp().getRevision());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailedFactoryRetainsItsReservationForTheNextLogin() {
        var account = DatabaseHelper.createAccount("factory-retry");
        assertThrows(
                IllegalStateException.class,
                () ->
                        DatabaseHelper.getOrCreatePlayer(
                                account,
                                Player.class,
                                () -> {
                                    throw new IllegalStateException("Factory unavailable");
                                }));
        int reserved = claims().find().first().getInteger("_id");
        var saved = DatabaseHelper.getOrCreatePlayer(account, Player.class, () -> fresh(account));
        assertEquals(reserved, saved.getUid());
        assertEquals(1, game.getDatabase().getCollection("players").countDocuments());
        var cold =
                DatabaseHelper.getOrCreatePlayer(
                        account,
                        Player.class,
                        () -> {
                            fail("A stored profile must not invoke the factory");
                            return null;
                        });
        assertEquals(reserved, cold.getUid());
    }

    @Test
    void theFactoryCannotBindAnotherAccountsPlayerToATicket() {
        var first = DatabaseHelper.createAccount("first-owner");
        var other = DatabaseHelper.createAccount("other-owner");
        var reservation = PlayerUidAllocator.reserve(first);
        assertThrows(
                IllegalStateException.class,
                () ->
                        PlayerUidAllocator.createOrLoad(
                                reservation, Player.class, () -> fresh(other)));
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
        assertEquals(
                reservation.uid(),
                PlayerUidAllocator.createOrLoad(reservation, Player.class, () -> fresh(first))
                        .getUid());
    }

    @Test
    void aLegacyProfilesUidIsAdoptedWithoutReplacingItsSave() {
        var account = new Account();
        account.setId("legacy-identity");
        account.setUsername("legacy-profile");
        game.insert(account);
        var player = fresh(account);
        player.setUid(19000);
        DatabaseHelper.saveGameSync(player);
        Document before = game.getDatabase().getCollection("players").find().first();
        var loaded =
                DatabaseHelper.getOrCreatePlayer(
                        account,
                        Player.class,
                        () -> {
                            fail("Existing profile should load");
                            return null;
                        });
        assertEquals(19000, loaded.getUid());
        assertEquals(before, game.getDatabase().getCollection("players").find().first());
        assertEquals(1, owners().countDocuments());
        assertEquals(19000, claims().find().first().getInteger("_id"));
    }

    @Test
    void equalAccountIdsFromDifferentAccountDatabasesCannotAdoptTheSameProfile() {
        var first = DatabaseHelper.createAccount("first-database-owner");
        var saved = DatabaseHelper.getOrCreatePlayer(first, Player.class, () -> fresh(first));
        Document before = game.getDatabase().getCollection("players").find().first();
        var other = new Account();
        other.setId(first.getId());
        other.setUsername("different-database-owner");
        dispatch.insert(other);
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        assertThrows(
                IllegalStateException.class,
                () -> DatabaseHelper.getOrCreatePlayer(other, Player.class, () -> fresh(other)));
        assertEquals(saved.getUid(), before.getInteger("_id"));
        assertEquals(before, game.getDatabase().getCollection("players").find().first());
    }

    @Test
    void resettingTheAccountCounterCannotRecycleADeletedAccountsUidIdentity() {
        var first = DatabaseHelper.createAccountWithUid("deleted-owner", 19100);
        game.getDatabase().getCollection("accounts").deleteOne(new Document("_id", first.getId()));
        game.getDatabase().getCollection("counters").deleteOne(new Document("_id", "Account"));
        assertNull(DatabaseHelper.createAccountWithUid("reuse-old-uid", 19100));
        var next = DatabaseHelper.createAccount("next-owner");
        assertNotEquals(first.getId(), next.getId());
        assertNotEquals(19100, PlayerUidAllocator.reserve(next).uid());
    }

    @Test
    void aTicketKeepsBothDatastoreDestinationsWhenTheRuntimeChanges() throws Exception {
        var account = DatabaseHelper.createAccount("pinned-profile");
        var reservation = PlayerUidAllocator.reserve(account);
        gameField.set(null, dispatch);
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        var saved =
                PlayerUidAllocator.createOrLoad(reservation, Player.class, () -> fresh(account));
        assertEquals(reservation.uid(), saved.getUid());
        assertEquals(1, game.getDatabase().getCollection("players").countDocuments());
        assertEquals(0, dispatch.getDatabase().getCollection("players").countDocuments());
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> MongoCollection<T> observeCollection(
            MongoCollection<T> actual,
            String methodName,
            boolean after,
            AtomicBoolean armed,
            AtomicReference<Document> deferred) {
        return (MongoCollection<T>)
                Proxy.newProxyInstance(
                        MongoCollection.class.getClassLoader(),
                        new Class<?>[] {MongoCollection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals(methodName)
                                    && armed.compareAndSet(true, false)) {
                                if (deferred != null)
                                    deferred.set(new Document((Document) args[0]));
                                if (after) call(actual, method, args);
                                throw new IllegalStateException(
                                        "Write acknowledgement unavailable");
                            }
                            Object result = call(actual, method, args);
                            return result instanceof MongoCollection<?> collection
                                    ? observeCollection(
                                            collection, methodName, after, armed, deferred)
                                    : result;
                        });
    }

    private Datastore observe(
            Datastore actual, String collection, String methodName, boolean after) {
        return observe(actual, collection, methodName, after, null);
    }

    private Datastore observe(
            Datastore actual,
            String collection,
            String methodName,
            boolean after,
            AtomicReference<Document> deferred) {
        var armed = new AtomicBoolean(true);
        var database =
                (MongoDatabase)
                        Proxy.newProxyInstance(
                                MongoDatabase.class.getClassLoader(),
                                new Class<?>[] {MongoDatabase.class},
                                (proxy, method, args) -> {
                                    Object value = call(actual.getDatabase(), method, args);
                                    return method.getName().equals("getCollection")
                                                    && args[0].equals(collection)
                                            ? observeCollection(
                                                    (MongoCollection<?>) value,
                                                    methodName,
                                                    after,
                                                    armed,
                                                    deferred)
                                            : value;
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
    void unknownUidClaimAcknowledgementsRetainAnyCommittedIdentity(boolean after) throws Exception {
        var account = DatabaseHelper.createAccount("claim-ack");
        gameField.set(null, observe(game, "player_uid_reservations", "insertOne", after));
        assertThrows(RuntimeException.class, () -> PlayerUidAllocator.reserve(account));
        Document committed = claims().find().first();
        assertEquals(after, committed != null);
        gameField.set(null, game);
        var retry = PlayerUidAllocator.reserve(account);
        assertEquals(after ? 10001 : 10002, retry.uid());
        if (after) assertEquals(committed, claims().find().first());
        assertEquals(1, claims().countDocuments());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyAccountIdentityAcknowledgementsAreRecoveredFromStorage(boolean after)
            throws Exception {
        var account = new Account();
        account.setId("legacy-ack");
        account.setUsername("legacy-ack");
        game.insert(account);
        gameField.set(null, observe(game, "account_uid_owners", "insertOne", after));
        assertThrows(RuntimeException.class, () -> PlayerUidAllocator.reserve(account));
        Document committed = owners().find().first();
        assertEquals(after, committed != null);
        gameField.set(null, game);
        var retry = PlayerUidAllocator.reserve(account);
        assertEquals(10001, retry.uid());
        if (after) assertEquals(committed, owners().find().first());
        assertEquals(1, owners().countDocuments());
        assertEquals(1, claims().countDocuments());
    }

    @Entity(value = "players", useDiscriminator = false)
    static class WriteFaultPlayer extends Player {
        transient Datastore target;
        transient Runnable before;
        transient Consumer<Document> after;
        transient boolean bound;

        @Override
        public synchronized PlayerSaveGuard getSaveGuard() {
            if (target != null && !bound) {
                InitialPlayerTestWrites.bind(this, target, before, after);
                bound = true;
            }
            return super.getSaveGuard();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theFirstProfileInsertReconcilesActualStorageWithoutSelectingAnotherUid(boolean after) {
        var account = DatabaseHelper.createAccount("profile-ack");
        var initial = fresh(new WriteFaultPlayer(), account);
        initial.target = game;
        var armed = new AtomicBoolean(true);
        Runnable fail =
                () -> {
                    if (armed.compareAndSet(true, false))
                        throw new IllegalStateException("Profile ACK unavailable");
                };
        initial.before = after ? () -> {} : fail;
        initial.after = after ? ignored -> fail.run() : ignored -> {};
        if (after) {
            var confirmed = DatabaseHelper.getOrCreatePlayer(account, Player.class, () -> initial);
            assertEquals(10001, confirmed.getUid());
            assertNotSame(initial, confirmed);
        } else {
            assertThrows(
                    RuntimeException.class,
                    () -> DatabaseHelper.getOrCreatePlayer(account, Player.class, () -> initial));
            assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
        }
        var retried = DatabaseHelper.getOrCreatePlayer(account, Player.class, () -> fresh(account));
        assertEquals(10001, retried.getUid());
        Document before = game.getDatabase().getCollection("players").find().first();
        assertEquals(1, retried.getSaveStamp().getRevision());
        assertEquals(1, game.getDatabase().getCollection("players").countDocuments());
        if (!after) {
            assertThrows(RuntimeException.class, () -> initial.getSaveGuard().save(game));
            assertEquals(before, game.getDatabase().getCollection("players").find().first());
        }
    }

    @Test
    void removedOrChangedOwnershipCannotBeUsedToCreateAProfile() {
        var account = DatabaseHelper.createAccount("owner-check");
        var ticket = PlayerUidAllocator.reserve(account);
        owners().updateOne(
                        new Document("_id", account.getId()),
                        new Document(
                                "$set", new Document("ownerId", UUID.randomUUID().toString())));
        assertThrows(
                IllegalStateException.class,
                () -> PlayerUidAllocator.createOrLoad(ticket, Player.class, () -> fresh(account)));
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void theConsoleProfileDoesNotPreventTheNormalAutomaticFloor() {
        game.getDatabase()
                .getCollection("players")
                .insertOne(new Document("_id", GameConstants.SERVER_CONSOLE_UID));
        assertEquals(10001, DatabaseHelper.getNextPlayerId(0));
    }

    @Test
    void anExhaustedHighestProfileDoesNotWrapTheUidCounter() {
        game.getDatabase()
                .getCollection("players")
                .insertOne(new Document("_id", Integer.MAX_VALUE));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.getNextPlayerId(0));
        assertEquals(0, claims().countDocuments());
        assertEquals(18301, DatabaseHelper.getNextPlayerId(18301));
    }

    @Test
    void racingSpecifiedAndAutomaticAllocationCannotBothOwnTheRequestedUid() throws Exception {
        var account = DatabaseHelper.createAccount("automatic-contender");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var automatic =
                    pool.submit(
                            () -> {
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return PlayerUidAllocator.reserve(account);
                            });
            var specified =
                    pool.submit(
                            () -> {
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return DatabaseHelper.createAccountWithUid(
                                        "specified-contender", 10001);
                            });
            start.countDown();
            var autoResult = automatic.get(15, TimeUnit.SECONDS);
            var specifiedResult = specified.get(15, TimeUnit.SECONDS);
            if (specifiedResult != null) {
                assertNotEquals(10001, autoResult.uid());
                assertEquals(10001, PlayerUidAllocator.reserve(specifiedResult).uid());
            } else assertEquals(10001, autoResult.uid());
            assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
        } finally {
            pool.shutdownNow();
        }
    }

    static Stream<Document> malformedClaims() {
        return Stream.of(
                new Document("operationId", "not-a-uuid"),
                new Document("operationId", null),
                new Document("kind", null),
                new Document("kind", "unknown"));
    }

    @ParameterizedTest
    @MethodSource("malformedClaims")
    void aMalformedOwnedReservationCannotAllocateAReplacementUid(Document replacement) {
        var account = DatabaseHelper.createAccount("malformed-owned");
        var ticket = PlayerUidAllocator.reserve(account);
        claims().updateOne(new Document("_id", ticket.uid()), new Document("$set", replacement));
        Document before = claims().find().first();
        assertThrows(IllegalStateException.class, () -> PlayerUidAllocator.reserve(account));
        assertEquals(before, claims().find().first());
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void aDeletedAccountCannotUseItsOldTicket() {
        var account = DatabaseHelper.createAccount("removed-before-creation");
        var ticket = PlayerUidAllocator.reserve(account);
        game.getDatabase()
                .getCollection("accounts")
                .deleteOne(new Document("_id", account.getId()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        PlayerUidAllocator.createOrLoad(
                                ticket,
                                Player.class,
                                () -> {
                                    fail("The factory must not run for a removed account");
                                    return null;
                                }));
        assertEquals(1, claims().countDocuments());
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void specifiedAccountInsertFailuresCannotReleaseOrRedirectItsUidClaim(boolean after)
            throws Exception {
        gameField.set(null, observe(game, "accounts", "insertOne", after));
        assertThrows(
                RuntimeException.class,
                () -> DatabaseHelper.createAccountWithUid("specified-ack", 18400));
        gameField.set(null, game);
        Document reserved = claims().find(new Document("_id", 18400)).first();
        assertNotNull(reserved);
        var account = DatabaseHelper.getAccountByName("specified-ack");
        if (after) {
            assertNotNull(account);
            assertEquals(18400, PlayerUidAllocator.reserve(account).uid());
        } else {
            assertNull(account);
            assertNull(DatabaseHelper.createAccountWithUid("another-request", 18400));
        }
        assertEquals(reserved, claims().find(new Document("_id", 18400)).first());
        assertEquals(0, game.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void aBareAnonymousUidDoesNotActAsAnAccountOwnershipTicket() throws Exception {
        int bare = DatabaseHelper.getNextPlayerId(18500);
        var player = new Player();
        var team = Player.class.getDeclaredField("teamManager");
        team.setAccessible(true);
        team.set(player, new TeamManager(player));
        DatabaseHelper.generatePlayerUid(player, bare);
        assertNotEquals(bare, player.getUid());
        assertEquals(2, claims().countDocuments());
        assertNotNull(
                game.getDatabase()
                        .getCollection("players")
                        .find(new Document("_id", player.getUid()))
                        .first());
    }

    @Test
    void aLateUidInsertCannotGiveOneAccountTwoReservations() throws Exception {
        var account = DatabaseHelper.createAccount("late-uid");
        var deferred = new AtomicReference<Document>();
        gameField.set(null, observe(game, "player_uid_reservations", "insertOne", false, deferred));
        assertThrows(RuntimeException.class, () -> PlayerUidAllocator.reserve(account));
        assertNotNull(deferred.get());
        gameField.set(null, game);
        var retry = PlayerUidAllocator.reserve(account);
        assertEquals(10002, retry.uid());
        Document before = claims().find().first();
        assertThrows(
                com.mongodb.MongoWriteException.class, () -> claims().insertOne(deferred.get()));
        assertEquals(1, claims().countDocuments());
        assertEquals(before, claims().find().first());
        assertEquals(retry.uid(), PlayerUidAllocator.reserve(account).uid());
    }

    @Test
    void aLateLegacyIdentityInsertCannotReplaceTheConfirmedOwner() throws Exception {
        var account = new Account();
        account.setId("late-identity");
        account.setUsername("late-identity");
        game.insert(account);
        var deferred = new AtomicReference<Document>();
        gameField.set(null, observe(game, "account_uid_owners", "insertOne", false, deferred));
        assertThrows(RuntimeException.class, () -> PlayerUidAllocator.reserve(account));
        assertNotNull(deferred.get());
        gameField.set(null, game);
        var retry = PlayerUidAllocator.reserve(account);
        Document before = owners().find().first();
        assertThrows(
                com.mongodb.MongoWriteException.class, () -> owners().insertOne(deferred.get()));
        assertEquals(1, owners().countDocuments());
        assertEquals(before, owners().find().first());
        assertEquals(retry.uid(), PlayerUidAllocator.reserve(account).uid());
    }
}
