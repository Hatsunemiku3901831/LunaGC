package emu.grasscutter.database;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.command.commands.AccountCommand;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Password-reset calls and failure injection touch only fresh isolated Mongo databases. */
class AccountPasswordResetMongoTest {
    private static final String NAME = "reset-fixture", PASSWORD = "new-fixture-password";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private MongoClient client;
    private Datastore game, dispatch;
    private Object oldGame, oldDispatch, oldServer;
    private ServerRunMode oldMode;
    private boolean oldPasswordMode;
    private Account account;
    private GameServer server;
    private final AtomicReference<String> fault = new AtomicReference<>("none");
    private final AtomicInteger closes = new AtomicInteger();
    private final AtomicBoolean closeSawCommittedReset = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        oldGame = field(DatabaseManager.class, "gameDatastore").get(null);
        oldDispatch = field(DatabaseManager.class, "dispatchDatastore").get(null);
        oldServer = field(Grasscutter.class, "gameServer").get(null);
        oldMode = Grasscutter.getRunMode();
        oldPasswordMode = ACCOUNT.EXPERIMENTAL_RealPassword;
        client = MongoClients.create(uri);
        MongoClient routed = wrapClient(client);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(routed, "lunagc_password_reset_game_" + suffix);
        dispatch = Morphia.createDatastore(routed, "lunagc_password_reset_dispatch_" + suffix);
        for (var store : List.of(game, dispatch))
            store.getMapper().map(Account.class, DatabaseCounter.class);
        field(DatabaseManager.class, "gameDatastore").set(null, game);
        field(DatabaseManager.class, "dispatchDatastore").set(null, dispatch);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        ACCOUNT.EXPERIMENTAL_RealPassword = true;
        server = new GameServer();
        field(GameServer.class, "players").set(server, new ConcurrentHashMap<>());
        field(Grasscutter.class, "gameServer").set(null, server);
        account =
                Objects.requireNonNull(
                        DatabaseHelper.createAccountWithUid(
                                NAME, 0, "old-fixture-password", List.of("fixture.permission")));
        account.setToken("old-login-token");
        account.setSessionKey("old-session-key");
        DatabaseHelper.saveAccountSync(account);
    }

    @AfterEach
    void close() throws Exception {
        if (client == null) return;
        try {
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        } finally {
            field(DatabaseManager.class, "gameDatastore").set(null, oldGame);
            field(DatabaseManager.class, "dispatchDatastore").set(null, oldDispatch);
            field(Grasscutter.class, "gameServer").set(null, oldServer);
            Grasscutter.setRunModeOverride(oldMode);
            ACCOUNT.EXPERIMENTAL_RealPassword = oldPasswordMode;
            game.getDatabase().drop();
            dispatch.getDatabase().drop();
            client.close();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Document row() {
        return game.getDatabase()
                .getCollection("accounts")
                .find(new Document("_id", account.getId()))
                .first();
    }

    private void command() {
        new AccountCommand().execute(null, null, List.of("resetpass", NAME, PASSWORD));
    }

    private void online() throws Exception {
        var player = new Player();
        field(Player.class, "account").set(player, account);
        field(Player.class, "accountId").set(player, account.getId());
        var session =
                new GameSession(server) {
                    @Override
                    public void close() {
                        closes.incrementAndGet();
                        var saved = row();
                        closeSawCommittedReset.set(
                                saved != null
                                        && saved.get("token") == null
                                        && saved.get("sessionKey") == null
                                        && Boolean.TRUE.equals(saved.get("passwordRequired")));
                    }
                };
        field(Player.class, "session").set(player, session);
        server.getPlayers().put(74401, player);
    }

    @Test
    void commandConfirmsPasswordBothTokenRevocationsAndAccountProtection() throws Exception {
        command();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        var stored = DatabaseHelper.getAccountById(account.getId());
        assertTrue(stored.verifyPassword(PASSWORD));
        assertFalse(stored.verifyPassword("old-fixture-password"));
        assertNull(row().get("token"));
        assertNull(row().get("sessionKey"));
        assertEquals(true, row().get("passwordRequired"));
        assertEquals(List.of("fixture.permission"), stored.getPermissions());
    }

    @Test
    void resetWorksWithoutAnInProcessGameServer() throws Exception {
        field(Grasscutter.class, "gameServer").set(null, null);
        assertDoesNotThrow(this::command);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertTrue(DatabaseHelper.getAccountById(account.getId()).verifyPassword(PASSWORD));
    }

    @Test
    void gameOnlyResetRejectsALocallyLoadedAccountInsteadOfChangingTheCodecDatabase() {
        var before = row();
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
        assertThrows(
                IllegalStateException.class,
                () ->
                        AccountPasswordResetService.resetPassword(
                                account, "fixture-wrong-authority"));
        assertEquals(before, row());
        assertEquals(0, dispatch.getDatabase().getCollection("accounts").countDocuments());
    }

    @Test
    void resetWaitsForAcceptedAccountWritesBeforeCompleting() throws Exception {
        var jobs = Executors.newSingleThreadExecutor();
        try {
            Future<?> result;
            try (var held =
                    new AccountCreationMongoTest.HeldQueue(
                            DatabaseHelper.getEventExecutorAccount())) {
                result = jobs.submit(this::command);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!DatabaseWriteBarrier.maintenanceActive()
                        && !result.isDone()
                        && System.nanoTime() < deadline) Thread.sleep(1);
                assertTrue(DatabaseWriteBarrier.maintenanceActive());
                assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
                assertEquals("old-session-key", row().getString("sessionKey"));
            }
            result.get(10, TimeUnit.SECONDS);
            assertNull(row().get("sessionKey"));
        } finally {
            jobs.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "after"})
    void aWriteFailureCannotBeReportedAsResetSuccess(String phase) {
        fault.set(phase);
        assertThrows(RuntimeException.class, this::command);
    }

    @Test
    void onlineKickOccursOnlyAfterTheResetWasConfirmed() throws Exception {
        online();
        command();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(1, closes.get());
        assertTrue(closeSawCommittedReset.get());
    }

    @Test
    void aRejectedResetDoesNotKickAnOnlineSession() throws Exception {
        online();
        fault.set("before");
        assertThrows(RuntimeException.class, this::command);
        assertEquals(0, closes.get());
    }

    @Test
    void disabledPasswordModeDoesNotModifyOrKick() throws Exception {
        online();
        ACCOUNT.EXPERIMENTAL_RealPassword = false;
        var before = row();
        command();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(before, row());
        assertEquals(0, closes.get());
    }

    @Test
    void missingAccountDoesNotCreateOrChangeAnotherAccount() throws Exception {
        var before = row();
        new AccountCommand().execute(null, null, List.of("resetpass", "missing", PASSWORD));
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(before, row());
        assertEquals(1, game.getDatabase().getCollection("accounts").countDocuments());
    }

    @Test
    void serviceKeepsTheAccountStoreCapturedBeforeWaitingForWrites() throws Exception {
        var untouched = new Document(row());
        untouched.put("username", "other-realm");
        dispatch.getDatabase().getCollection("accounts").insertOne(untouched);
        var jobs = Executors.newSingleThreadExecutor();
        try {
            Future<?> result;
            try (var held =
                    new AccountCreationMongoTest.HeldQueue(DatabaseHelper.getEventExecutor())) {
                result =
                        jobs.submit(
                                () ->
                                        AccountPasswordResetService.resetPassword(
                                                account, "captured-fixture-hash"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!DatabaseWriteBarrier.maintenanceActive() && System.nanoTime() < deadline)
                    Thread.sleep(1);
                assertTrue(DatabaseWriteBarrier.maintenanceActive());
                Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
            }
            result.get(10, TimeUnit.SECONDS);
            assertEquals("captured-fixture-hash", row().getString("password"));
            assertNull(row().get("sessionKey"));
            assertEquals(
                    untouched, dispatch.getDatabase().getCollection("accounts").find().first());
        } finally {
            jobs.shutdownNow();
        }
    }

    @Test
    void aDrainTimeoutLeavesCredentialsAndMaintenanceAdmissionUnchanged() throws Exception {
        var before = row();
        try (var held = new AccountCreationMongoTest.HeldQueue(DatabaseHelper.getEventExecutor())) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            AccountPasswordResetService.resetPassword(
                                    account, "timeout-fixture", Duration.ofMillis(30)));
            assertEquals(before, row());
            assertFalse(DatabaseWriteBarrier.maintenanceActive());
        }
        account.setEmail("still-writable@fixture.invalid");
        DatabaseHelper.saveAccountSync(account);
        assertEquals("old-login-token", row().getString("token"));
    }

    @Test
    void anAdmittedDatabaseWriterCannotWaitForItsOwnCompletion() throws Exception {
        var before = row();
        var future =
                DatabaseHelper.getEventExecutorAccount()
                        .submit(
                                () ->
                                        AccountPasswordResetService.resetPassword(
                                                account, "writer-fixture"));
        var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(before, row());
    }

    @Test
    void aStaleResetRequestCannotDiscardNewerAccountChanges() {
        var newer = DatabaseHelper.getAccountById(account.getId());
        newer.setEmail("newer@fixture.invalid");
        DatabaseHelper.saveAccountSync(newer);
        var before = row();
        assertThrows(
                IllegalStateException.class,
                () -> AccountPasswordResetService.resetPassword(account, "stale-fixture"));
        assertEquals(before, row());
        assertEquals("old-login-token", account.getToken());
        assertFalse(account.isPasswordRequired());
    }

    @Test
    void aDeletedAccountCannotBeRecreatedByPasswordReset() {
        game.getDatabase()
                .getCollection("accounts")
                .deleteOne(new Document("_id", account.getId()));
        assertThrows(
                IllegalStateException.class,
                () -> AccountPasswordResetService.resetPassword(account, "deleted-fixture"));
        assertNull(row());
    }

    @Test
    void anUnattachedAccountCannotResetAnExistingIdentity() {
        var before = row();
        var unattached = new Account();
        unattached.setId(account.getId());
        assertThrows(
                IllegalStateException.class,
                () -> AccountPasswordResetService.resetPassword(unattached, "unattached-fixture"));
        assertEquals(before, row());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void anAbsentPasswordHashCannotClearTokensOrModifyTheAccount(String hash) {
        var before = row();
        assertThrows(
                IllegalArgumentException.class,
                () -> AccountPasswordResetService.resetPassword(account, hash));
        assertEquals(before, row());
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private MongoClient wrapClient(MongoClient real) {
        return (MongoClient)
                Proxy.newProxyInstance(
                        MongoClient.class.getClassLoader(),
                        new Class<?>[] {MongoClient.class},
                        (proxy, method, args) -> {
                            Object value = call(real, method, args);
                            return value instanceof MongoDatabase db ? wrapDatabase(db) : value;
                        });
    }

    private MongoDatabase wrapDatabase(MongoDatabase real) {
        return (MongoDatabase)
                Proxy.newProxyInstance(
                        MongoDatabase.class.getClassLoader(),
                        new Class<?>[] {MongoDatabase.class},
                        (proxy, method, args) -> {
                            Object value = call(real, method, args);
                            if (value instanceof MongoDatabase database)
                                return wrapDatabase(database);
                            return value instanceof MongoCollection<?> collection
                                            && collection
                                                    .getNamespace()
                                                    .getCollectionName()
                                                    .equals("accounts")
                                    ? wrapCollection(collection)
                                    : value;
                        });
    }

    private MongoCollection<?> wrapCollection(MongoCollection<?> real) {
        return (MongoCollection<?>)
                Proxy.newProxyInstance(
                        MongoCollection.class.getClassLoader(),
                        new Class<?>[] {MongoCollection.class},
                        (proxy, method, args) -> {
                            String phase =
                                    method.getName().equals("replaceOne")
                                            ? fault.getAndSet("none")
                                            : "none";
                            if (phase.equals("before"))
                                throw new IllegalStateException("Injected before credential write");
                            Object value = call(real, method, args);
                            if (phase.equals("after"))
                                throw new IllegalStateException(
                                        "Injected lost credential acknowledgement");
                            return value instanceof MongoCollection<?> collection
                                    ? wrapCollection(collection)
                                    : value;
                        });
    }
}
