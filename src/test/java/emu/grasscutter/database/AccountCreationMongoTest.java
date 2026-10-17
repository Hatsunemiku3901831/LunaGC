package emu.grasscutter.database;

import static com.mongodb.client.model.Indexes.ascending;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Account creation must reserve IDs and confirm insert-only storage before reporting success. */
class AccountCreationMongoTest {
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
        game = Morphia.createDatastore(client, "lunagc_account_game_" + id);
        dispatch = Morphia.createDatastore(client, "lunagc_account_dispatch_" + id);
        for (var store : List.of(game, dispatch)) {
            store.getMapper().map(Account.class, DatabaseCounter.class);
            store.getDatabase()
                    .getCollection("accounts")
                    .createIndex(ascending("username"), new IndexOptions().unique(true));
        }
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

    private MongoCollection<Document> accounts(Datastore store) {
        return store.getDatabase().getCollection("accounts");
    }

    private MongoCollection<Document> counters() {
        return game.getDatabase().getCollection("counters");
    }

    private Document account(String id) {
        return accounts(game).find(new Document("_id", id)).first();
    }

    private void counter(long value) {
        counters().insertOne(new Document("_id", "Account").append("count", value));
    }

    static class HeldQueue implements AutoCloseable {
        private final DatabaseWriteQueue queue;
        private final CountDownLatch release = new CountDownLatch(1);

        HeldQueue(DatabaseWriteQueue queue) throws Exception {
            this.queue = queue;
            var entered = new CountDownLatch(queue.getCorePoolSize());
            for (int i = 0; i < queue.getCorePoolSize(); i++)
                queue.submit(
                        () -> {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            assertTrue(queue.awaitIdle(TIMEOUT));
        }
    }

    @Test
    void twoReservationsCannotReuseAnIdWhileAsyncWritesAreHeld() throws Exception {
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutor())) {
            int first = DatabaseManager.getNextId(Account.class);
            int second = DatabaseManager.getNextId(Account.class);
            assertEquals(10001, first);
            assertEquals(first + 1, second);
        }
    }

    @Test
    void theCounterIsConfirmedBeforeItsIdIsReturned() throws Exception {
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutor())) {
            int id = DatabaseManager.getNextId(Account.class);
            var stored = counters().find(new Document("_id", "Account")).first();
            assertNotNull(stored);
            assertEquals(id, ((Number) stored.get("count")).intValue());
        }
    }

    @Test
    void creationIsStoredBeforeReturningEvenWhenTheAccountQueueIsBusy() throws Exception {
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            Account created = DatabaseHelper.createAccount("new-player");
            assertNotNull(created);
            assertNotNull(account(created.getId()));
            assertEquals(created.getId(), DatabaseHelper.getAccountByName("new-player").getId());
        }
    }

    @Test
    void aStaleCounterCannotReplaceAnExistingAccount() throws Exception {
        var original = new Account();
        original.setId("10001");
        original.setUsername("existing-player");
        original.setEmail("fixture@example.invalid");
        game.insert(original);
        Document before = account(original.getId());
        Account created;
        try (var heldDefault = new HeldQueue(DatabaseHelper.getEventExecutor());
                var heldAccounts = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            created = DatabaseHelper.createAccount("another-player");
        }
        assertEquals(before, account("10001"));
        assertNotNull(created);
        assertNotEquals("10001", created.getId());
        assertNotNull(account(created.getId()));
    }

    @Test
    void concurrentAttemptsWithOneNameHaveOnlyOneSuccessfulCreator() throws Exception {
        try (var heldDefault = new HeldQueue(DatabaseHelper.getEventExecutor());
                var heldAccounts = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            var pool = Executors.newFixedThreadPool(6);
            try {
                var ready = new CountDownLatch(6);
                var start = new CountDownLatch(1);
                List<Future<Account>> attempts = new ArrayList<>();
                for (int i = 0; i < 6; i++)
                    attempts.add(
                            pool.submit(
                                    () -> {
                                        ready.countDown();
                                        assertTrue(start.await(5, TimeUnit.SECONDS));
                                        return DatabaseHelper.createAccount("same-player");
                                    }));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                int winners = 0;
                for (var attempt : attempts)
                    if (attempt.get(10, TimeUnit.SECONDS) != null) winners++;
                assertEquals(1, winners);
                assertEquals(1, accounts(game).countDocuments());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void passwordCreationUsesTheSameConfirmedInsertPath() throws Exception {
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            Account created =
                    DatabaseHelper.createAccountWithPassword("password-fixture", "fixture-only");
            assertNotNull(created);
            assertNotNull(account(created.getId()));
            assertEquals(
                    "fixture-only", DatabaseHelper.getAccountById(created.getId()).getPassword());
        }
    }

    @Test
    void theHistoricFloorIsRetainedWhenACounterWasReset() {
        counter(7);
        assertEquals(10001, DatabaseManager.getNextId(Account.class));
    }

    @Test
    void anExhaustedCounterDoesNotWrapOrChangeTheStoredHighWaterMark() {
        counter(Integer.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> DatabaseManager.getNextId(Account.class));
        assertEquals(
                (long) Integer.MAX_VALUE,
                ((Number) counters().find().first().get("count")).longValue());
    }

    @Test
    void splitDispatchCreationKeepsTheExistingGameCounterAndWritesOnlyTheAccountStore()
            throws Exception {
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        counter(18000);
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            Account created = DatabaseHelper.createAccount("dispatch-player");
            assertNotNull(created);
            assertEquals("18001", created.getId());
            assertNull(account(created.getId()));
            assertNotNull(accounts(dispatch).find(new Document("_id", created.getId())).first());
            assertEquals(0, dispatch.getDatabase().getCollection("counters").countDocuments());
        }
    }

    @Test
    void existingUsernamesRemainReadableAndCannotBeOverwritten() {
        var original = new Account();
        original.setId("11222");
        original.setUsername("existing@example.invalid");
        game.insert(original);
        assertEquals("11222", DatabaseHelper.getAccountByName(original.getUsername()).getId());
        assertNull(DatabaseHelper.createAccount(original.getUsername()));
        assertEquals(1, accounts(game).countDocuments());
    }

    @Test
    void independentDatastoreHandlesReserveOneSequenceWithoutAJvmLock() throws Exception {
        var other = Morphia.createDatastore(client, game.getDatabase().getName());
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            List<Future<Integer>> pending = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                var store = i % 2 == 0 ? game : other;
                pending.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return DatabaseManager.reserveNextId(store, "Account", 10000);
                                }));
            }
            start.countDown();
            Set<Integer> ids = new TreeSet<>();
            for (var task : pending) assertTrue(ids.add(task.get(10, TimeUnit.SECONDS)));
            assertEquals(64, ids.size());
            assertEquals(10064, ((Number) counters().find().first().get("count")).intValue());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelCounterExhaustionNeverAdvancesPastInt32() throws Exception {
        counter((long) Integer.MAX_VALUE - 1);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> pending = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                pending.add(
                        pool.submit(
                                () -> {
                                    try {
                                        return DatabaseManager.reserveNextId(
                                                game, "Account", 10000);
                                    } catch (IllegalStateException exhausted) {
                                        return null;
                                    }
                                }));
            int successes = 0;
            for (var task : pending) {
                var id = task.get(10, TimeUnit.SECONDS);
                if (id != null) {
                    successes++;
                    assertEquals(Integer.MAX_VALUE, id);
                }
            }
            assertEquals(1, successes);
            assertEquals(
                    (long) Integer.MAX_VALUE,
                    ((Number) counters().find().first().get("count")).longValue());
        } finally {
            pool.shutdownNow();
        }
    }

    static Stream<Document> invalidCounters() {
        return Stream.of(
                new Document("count", -1),
                new Document("count", 1.5),
                new Document("count", "7"),
                new Document("count", null),
                new Document(),
                new Document("count", (long) Integer.MAX_VALUE + 1));
    }

    @ParameterizedTest
    @MethodSource("invalidCounters")
    void invalidOrOverflowedCountersAreNotResetOrTruncated(Document value) {
        value.put("_id", "Account");
        counters().insertOne(value);
        Document before = counters().find().first();
        assertThrows(RuntimeException.class, () -> DatabaseManager.getNextId(Account.class));
        assertEquals(before, counters().find().first());
        assertEquals(0, accounts(game).countDocuments());
    }

    @ParameterizedTest
    @ValueSource(strings = {"原神测试", "player.name", "new@example.invalid"})
    void existingUsernameCharacterRulesArePreserved(String name) {
        var result = DatabaseHelper.createAccount(name);
        assertNotNull(result);
        assertEquals(name, DatabaseHelper.getAccountById(result.getId()).getUsername());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void emptyNewNamesDoNotReserveAnIdOrCreateAnAccount(String name) {
        assertNull(DatabaseHelper.createAccount(name));
        assertEquals(0, accounts(game).countDocuments());
        assertEquals(0, counters().countDocuments());
    }

    @Test
    void onlyOneAccountCanReserveTheSamePositivePlayerUid() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            List<Future<Account>> pending = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String name = "uid-contender-" + i;
                pending.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return DatabaseHelper.createAccountWithUid(name, 56321);
                                }));
            }
            start.countDown();
            int successes = 0;
            for (var task : pending) if (task.get(10, TimeUnit.SECONDS) != null) successes++;
            assertEquals(1, successes);
            assertEquals(1, accounts(game).countDocuments(new Document("reservedPlayerId", 56321)));
            assertNotNull(DatabaseHelper.createAccount("without-reservation-one"));
            assertNotNull(DatabaseHelper.createAccount("without-reservation-two"));
            assertEquals(3, accounts(game).countDocuments());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void existingPlayersAndConsoleUidCannotBeReservedByANewAccount() {
        game.getDatabase().getCollection("players").insertOne(new Document("_id", 67432));
        assertNull(DatabaseHelper.createAccountWithUid("old-uid", 67432));
        assertNull(
                DatabaseHelper.createAccountWithUid(
                        "console-uid", emu.grasscutter.GameConstants.SERVER_CONSOLE_UID));
        assertEquals(0, accounts(game).countDocuments());
        assertEquals(0, counters().countDocuments());
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failed) {
            throw failed.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> MongoCollection<T> observedCollection(
            MongoCollection<T> original,
            String write,
            boolean after,
            AtomicBoolean armed,
            Consumer<String> onWrite) {
        return (MongoCollection<T>)
                Proxy.newProxyInstance(
                        MongoCollection.class.getClassLoader(),
                        new Class<?>[] {MongoCollection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals(write)
                                    && armed.compareAndSet(true, false)) {
                                if (after) call(original, method, args);
                                onWrite.accept(write);
                            }
                            Object result = call(original, method, args);
                            if (result instanceof MongoCollection<?> collection)
                                return observedCollection(collection, write, after, armed, onWrite);
                            return result;
                        });
    }

    private Datastore observeStore(
            Datastore original,
            String collectionName,
            String write,
            boolean after,
            Consumer<String> onWrite) {
        var armed = new AtomicBoolean(true);
        var database =
                (MongoDatabase)
                        Proxy.newProxyInstance(
                                MongoDatabase.class.getClassLoader(),
                                new Class<?>[] {MongoDatabase.class},
                                (proxy, method, args) -> {
                                    Object value = call(original.getDatabase(), method, args);
                                    if (method.getName().equals("getCollection")
                                            && args[0].equals(collectionName))
                                        return observedCollection(
                                                (MongoCollection<?>) value,
                                                write,
                                                after,
                                                armed,
                                                onWrite);
                                    return value;
                                });
        return (Datastore)
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) ->
                                method.getName().equals("getDatabase")
                                        ? database
                                        : call(original, method, args));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accountWriteFailureCannotReportSuccessfulCreation(boolean after) throws Exception {
        var broken =
                observeStore(
                        game,
                        "accounts",
                        "insertOne",
                        after,
                        ignored -> {
                            throw new IllegalStateException(
                                    "Account write acknowledgement unavailable");
                        });
        gameField.set(null, broken);
        assertThrows(
                RuntimeException.class, () -> DatabaseHelper.createAccount("uncertain-account"));
        assertEquals(after ? 1 : 0, accounts(game).countDocuments());
        gameField.set(null, game);
        var retried = DatabaseHelper.createAccount("uncertain-account");
        assertEquals(after, retried == null);
        assertEquals(1, accounts(game).countDocuments());
        assertEquals("uncertain-account", accounts(game).find().first().getString("username"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCounterWriteWithUnknownAcknowledgementCannotReuseItsReservedId(boolean after)
            throws Exception {
        counter(10000);
        var broken =
                observeStore(
                        game,
                        "counters",
                        "findOneAndUpdate",
                        after,
                        ignored -> {
                            throw new IllegalStateException("Counter acknowledgement unavailable");
                        });
        gameField.set(null, broken);
        assertThrows(RuntimeException.class, () -> DatabaseManager.getNextId(Account.class));
        assertEquals(
                after ? 10001 : 10000,
                ((Number) counters().find().first().get("count")).intValue());
        gameField.set(null, game);
        assertEquals(after ? 10002 : 10001, DatabaseManager.getNextId(Account.class));
    }

    @Test
    void aCreationCannotDriftToANewDatastoreOrRunModeDuringItsWrites() throws Exception {
        var observed =
                observeStore(
                        game,
                        "accounts",
                        "createIndex",
                        false,
                        ignored -> {
                            try {
                                gameField.set(null, dispatch);
                                Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
                            } catch (IllegalAccessException e) {
                                throw new AssertionError(e);
                            }
                        });
        gameField.set(null, observed);
        var created = DatabaseHelper.createAccount("pinned-account");
        assertNotNull(created);
        assertNotNull(account(created.getId()));
        assertNotNull(counters().find().first());
        assertEquals(0, accounts(dispatch).countDocuments());
        assertEquals(0, dispatch.getDatabase().getCollection("counters").countDocuments());
    }

    @Test
    void aConflictingReservationIndexFailsWithoutDroppingAnyExistingIndex() {
        accounts(game).createIndex(ascending("reservedPlayerId"));
        List<Document> before = accounts(game).listIndexes().into(new ArrayList<>());
        assertThrows(
                RuntimeException.class,
                () -> DatabaseHelper.createAccountWithUid("conflict", 12345));
        assertEquals(before, accounts(game).listIndexes().into(new ArrayList<>()));
        assertEquals(0, accounts(game).countDocuments());
        assertEquals(0, counters().countDocuments());
    }

    @Test
    void legacyIntCountersAndHigherLongCountersKeepTheirSequence() {
        counters().insertOne(new Document("_id", "Account").append("count", 25000));
        assertEquals(25001, DatabaseManager.getNextId(Account.class));
        assertEquals(25002, DatabaseManager.getNextId(new Account()));
        assertEquals(10001, DatabaseManager.getNextId(emu.grasscutter.game.player.Player.class));
        assertEquals(2, counters().countDocuments());
    }

    @Test
    void automaticPlayerUidAllocationUsesConfirmedDistinctCounterResults() throws Exception {
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutor())) {
            assertEquals(10001, DatabaseHelper.getNextPlayerId(0));
            assertEquals(10002, DatabaseHelper.getNextPlayerId(0));
            var saved = counters().find(new Document("_id", "Player")).first();
            assertNotNull(saved);
            assertEquals(10002, ((Number) saved.get("count")).intValue());
        }
    }

    @Test
    void playerUidProbingKeepsAnExistingProfileWhenItsCounterIsBehind() {
        var before = new Document("_id", 10001).append("accountId", "legacy-account");
        var players = game.getDatabase().getCollection("players");
        players.insertOne(before);
        assertEquals(10002, DatabaseHelper.getNextPlayerId(0));
        assertEquals(before, players.find(new Document("_id", 10001)).first());
    }

    @Test
    void newPlayerCreationStillConfirmsItsGuardedProfile() throws Exception {
        var player = new emu.grasscutter.game.player.Player();
        var team = player.getClass().getDeclaredField("teamManager");
        team.setAccessible(true);
        team.set(player, new emu.grasscutter.game.player.TeamManager(player));
        DatabaseHelper.generatePlayerUid(player, 0);
        assertEquals(10001, player.getUid());
        var stored =
                game.getDatabase()
                        .getCollection("players")
                        .find(new Document("_id", player.getUid()))
                        .first();
        assertNotNull(stored);
        assertNotNull(stored.get("saveStamp"));
    }

    @Test
    void anExtendedRunOfOccupiedAccountIdsFailsWithoutReplacingThem() {
        List<Document> originals = new ArrayList<>();
        for (int id = 10001; id <= 10070; id++)
            originals.add(
                    new Document("_id", Integer.toString(id)).append("username", "legacy-" + id));
        accounts(game).insertMany(originals);
        assertThrows(
                IllegalStateException.class, () -> DatabaseHelper.createAccount("new-after-reset"));
        assertEquals(
                originals,
                accounts(game).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        assertEquals(70, accounts(game).countDocuments());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consoleCreationConfirmsInitialPermissionsAndOptionalPasswordBeforeReportingSuccess(
            boolean passwordMode) throws Exception {
        boolean previous = emu.grasscutter.config.Configuration.ACCOUNT.EXPERIMENTAL_RealPassword;
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            emu.grasscutter.config.Configuration.ACCOUNT.EXPERIMENTAL_RealPassword = passwordMode;
            new emu.grasscutter.command.commands.AccountCommand()
                    .execute(
                            null,
                            null,
                            passwordMode
                                    ? List.of("create", "command-account", "fixture-command-pass")
                                    : List.of("create", "command-account"));
            var stored = accounts(game).find(new Document("username", "command-account")).first();
            assertNotNull(stored);
            assertEquals(List.of("*"), stored.getList("permissions", String.class));
            var loaded = DatabaseHelper.getAccountByName("command-account");
            if (passwordMode) {
                assertNotNull(loaded.getPassword());
                assertTrue(loaded.verifyPassword("fixture-command-pass"));
                assertFalse(loaded.verifyPassword("wrong-fixture-pass"));
            }
        } finally {
            emu.grasscutter.config.Configuration.ACCOUNT.EXPERIMENTAL_RealPassword = previous;
        }
    }
}
