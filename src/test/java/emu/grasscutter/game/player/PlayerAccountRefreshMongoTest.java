package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.command.DefaultPermissionHandler;
import emu.grasscutter.command.PermissionHandler;
import emu.grasscutter.command.commands.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.*;
import emu.grasscutter.server.game.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Current account data and an already-cached online copy in isolated Mongo databases. */
class PlayerAccountRefreshMongoTest {
    private static final String ID = "refresh-fixture",
            NAME = "cached-user",
            KEY = "old-fixture-key";
    private MongoClient mongo;
    private Datastore game, dispatch;
    private Object oldGame, oldDispatch, oldServer;
    private ServerRunMode oldMode;
    private String[] oldDefaults;
    private PermissionHandler oldPermissionHandler;
    private Player player;
    private Account cached;
    private GameServer server;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        oldGame = field(DatabaseManager.class, "gameDatastore").get(null);
        oldDispatch = field(DatabaseManager.class, "dispatchDatastore").get(null);
        oldServer = field(Grasscutter.class, "gameServer").get(null);
        oldMode = Grasscutter.getRunMode();
        oldDefaults = ACCOUNT.defaultPermissions;
        oldPermissionHandler = Grasscutter.getPermissionHandler();
        Grasscutter.setPermissionHandler(new DefaultPermissionHandler());
        ACCOUNT.defaultPermissions = new String[0];
        mongo = MongoClients.create(uri);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(mongo, "lunagc_account_refresh_game_" + suffix);
        dispatch = Morphia.createDatastore(mongo, "lunagc_account_refresh_owner_" + suffix);
        game.getMapper().map(Account.class, Player.class);
        dispatch.getMapper().map(Account.class);
        field(DatabaseManager.class, "gameDatastore").set(null, game);
        field(DatabaseManager.class, "dispatchDatastore").set(null, dispatch);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        game.getDatabase()
                .getCollection("accounts")
                .insertOne(
                        new Document("_id", ID)
                                .append("username", NAME)
                                .append("sessionKey", KEY)
                                .append("token", "fixture-login-token")
                                .append("permissions", List.of("*")));
        cached = DatabaseHelper.getAccountById(ID);
        player =
                new Player() {
                    @Override
                    public void dropMessage(Object message) {}
                };
        field(Player.class, "accountId").set(player, ID);
        player.setAccount(cached);
        server = new GameServer();
        field(GameServer.class, "players").set(server, new ConcurrentHashMap<>());
        server.getPlayers().put(player.getUid(), player);
        field(Grasscutter.class, "gameServer").set(null, server);
    }

    @AfterEach
    void close() throws Exception {
        if (mongo == null) return;
        try {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        } finally {
            field(DatabaseManager.class, "gameDatastore").set(null, oldGame);
            field(DatabaseManager.class, "dispatchDatastore").set(null, oldDispatch);
            field(Grasscutter.class, "gameServer").set(null, oldServer);
            Grasscutter.setRunModeOverride(oldMode);
            ACCOUNT.defaultPermissions = oldDefaults;
            Grasscutter.setPermissionHandler(oldPermissionHandler);
            game.getDatabase().drop();
            dispatch.getDatabase().drop();
            mongo.close();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private void set(String key, Object value) {
        game.getDatabase()
                .getCollection("accounts")
                .updateOne(new Document("_id", ID), new Document("$set", new Document(key, value)));
    }

    private boolean permission(String node) {
        return new DefaultPermissionHandler().checkPermission(player, player, node, "");
    }

    @Test
    void repeatedSessionKeyReadsObserveConfirmedRotations() {
        assertEquals(KEY, player.getSessionKey());
        var fresh = DatabaseHelper.getAccountById(ID);
        fresh.setSessionKey("new-fixture-key");
        DatabaseHelper.saveAccountSync(fresh);
        assertEquals("new-fixture-key", player.getSessionKey());
    }

    @Test
    void handbookRejectsTheOldKeyAfterAConfirmedPasswordReset() {
        assertTrue(HandbookActions.isAuthenticated(player, KEY));
        AccountPasswordResetService.resetPassword(
                DatabaseHelper.getAccountById(ID), "fixture-hash");
        assertFalse(assertDoesNotThrow(() -> HandbookActions.isAuthenticated(player, KEY)));
        assertNull(player.getSessionKey());
    }

    @Test
    void handbookAcceptsTheCurrentKeyAndRejectsTheRotatedKey() {
        assertTrue(HandbookActions.isAuthenticated(player, KEY));
        set("sessionKey", "current-fixture-key");
        assertFalse(HandbookActions.isAuthenticated(player, KEY));
        assertTrue(HandbookActions.isAuthenticated(player, "current-fixture-key"));
    }

    @Test
    void aDeletedAccountCannotAuthenticateFromAnOnlineCache() {
        assertEquals(KEY, player.getSessionKey());
        game.getDatabase().getCollection("accounts").deleteOne(new Document("_id", ID));
        assertFalse(assertDoesNotThrow(() -> HandbookActions.isAuthenticated(player, KEY)));
        assertNull(player.getSessionKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void blankKeysCannotAuthenticateEvenWhenTheCachedAndStoredValuesMatch(String key) {
        cached.setSessionKey(key);
        set("sessionKey", key);
        assertFalse(assertDoesNotThrow(() -> HandbookActions.isAuthenticated(player, key)));
    }

    @Test
    void absentStoredKeysAreRejectedWithoutThrowing() {
        cached.setSessionKey(null);
        set("sessionKey", null);
        assertFalse(assertDoesNotThrow(() -> HandbookActions.isAuthenticated(player, "some-key")));
    }

    @Test
    void permissionRevocationIsObservedByTheNextCommandCheck() {
        assertTrue(permission("fixture.command"));
        set("permissions", List.of());
        assertFalse(permission("fixture.command"));
    }

    @Test
    void aNewPermissionDoesNotRequireRelogging() {
        cached.removePermission("*");
        set("permissions", List.of("fixture.command"));
        assertTrue(permission("fixture.command"));
    }

    @Test
    void aDeletedAccountCannotUseCachedPermissions() {
        game.getDatabase().getCollection("accounts").deleteOne(new Document("_id", ID));
        assertFalse(assertDoesNotThrow(() -> permission("fixture.command")));
    }

    @Test
    void anOnlineCachedUsernameCannotHideAnAuthoritativeRename() {
        set("username", "renamed-user");
        assertNull(server.getAccountByName(NAME));
        assertEquals(ID, server.getAccountByName("renamed-user").getId());
    }

    @Test
    void ordinaryAccountReadsKeepThePresentationCacheUntilAnExplicitRefresh() {
        assertSame(cached, player.getAccount());
        assertFalse(HandbookActions.isAuthenticated(null, KEY));
        assertFalse(HandbookActions.isAuthenticated(player, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2000000000})
    void activeBansBlockKeysAndPermissionsWithoutChangingTheStoredRow(int until) {
        set("isBanned", true);
        set("banEndTime", until);
        var before =
                game.getDatabase().getCollection("accounts").find(new Document("_id", ID)).first();
        assertFalse(HandbookActions.isAuthenticated(player, KEY));
        assertFalse(permission("fixture.command"));
        assertNull(player.getSessionKey());
        assertEquals(
                before,
                game.getDatabase().getCollection("accounts").find(new Document("_id", ID)).first());
    }

    @Test
    void expiredBansAllowAuthorizationWithoutImplicitSaveAttempts() throws Exception {
        set("isBanned", true);
        set("banEndTime", 1);
        var before =
                game.getDatabase().getCollection("accounts").find(new Document("_id", ID)).first();
        assertTrue(HandbookActions.isAuthenticated(player, KEY));
        assertTrue(permission("fixture.command"));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(3)));
        assertEquals(
                before,
                game.getDatabase().getCollection("accounts").find(new Document("_id", ID)).first());
    }

    @Test
    void anUnavailableSourceCannotFallBackToCachedCredentialsOrPermissions() throws Exception {
        field(DatabaseManager.class, "gameDatastore").set(null, null);
        assertFalse(assertDoesNotThrow(() -> HandbookActions.isAuthenticated(player, KEY)));
        assertFalse(assertDoesNotThrow(() -> permission("fixture.command")));
        assertNull(player.getSessionKey());
        assertSame(
                cached,
                player.getAccount()); // Retained for display, never as the authorization result.
    }

    @Test
    void aRefreshPublishesOneNewSnapshotToTheLinkedPlayerAndSession() {
        var session = new GameSession(server);
        session.setAccount(cached);
        session.setPlayer(player);
        set("sessionKey", "refreshed-key");
        var fresh = player.refreshAccount();
        assertNotSame(cached, fresh);
        assertSame(fresh, player.getAccount());
        assertSame(fresh, session.getAccount());
        assertEquals("refreshed-key", fresh.getSessionKey());
        assertEquals(KEY, cached.getSessionKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "ban", "unban", "language"})
    void accountEditingCommandsRefreshTheirTargetBeforeSaving(String command) throws Exception {
        var current = DatabaseHelper.getAccountById(ID);
        current.setSessionKey("owner-rotated-key");
        if (command.equals("unban")) current.setBanned(true);
        DatabaseHelper.saveAccountSync(current);
        switch (command) {
            case "permission" -> new PermissionCommand()
                    .execute(null, player, List.of("add", "fixture.added"));
            case "ban" -> new BanCommand().execute(null, player, List.of());
            case "unban" -> new UnBanCommand().execute(null, player, List.of());
            case "language" -> new LanguageCommand().execute(player, null, List.of("en-US"));
        }
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(3)));
        var saved = DatabaseHelper.getAccountById(ID);
        assertEquals("owner-rotated-key", saved.getSessionKey());
        assertNotSame(cached, player.getAccount());
        switch (command) {
            case "permission" -> assertTrue(saved.getPermissions().contains("fixture.added"));
            case "ban" -> assertTrue(saved.isBanActive());
            case "unban" -> assertFalse(saved.isBanActive());
            case "language" -> assertEquals("en", saved.getLocale().getLanguage());
        }
    }

    @Test
    void refreshingDoesNotGiveAnOldCopyTheNewSaveVersion() {
        var current = DatabaseHelper.getAccountById(ID);
        current.setSessionKey("owner-key");
        DatabaseHelper.saveAccountSync(current);
        var fresh = player.refreshAccount();
        fresh.setBanReason("new snapshot write");
        DatabaseHelper.saveAccountSync(fresh);
        cached.setBanReason("old snapshot write");
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(cached));
        assertEquals("new snapshot write", DatabaseHelper.getAccountById(ID).getBanReason());
    }
}
