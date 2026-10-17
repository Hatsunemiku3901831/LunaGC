package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.MaPassportAuthenticator;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

class AccountSaveFenceMongoTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private MongoClient client;
    private Datastore game, dispatch;
    private Object previousGame, previousDispatch;
    private ServerRunMode previousMode;
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
        previousGame = gameField.get(null);
        previousDispatch = dispatchField.get(null);
        previousMode = Grasscutter.getRunMode();
        client = MongoClients.create(uri);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(client, "lunagc_account_save_game_" + suffix);
        dispatch = Morphia.createDatastore(client, "lunagc_account_save_dispatch_" + suffix);
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
            gameField.set(null, previousGame);
            dispatchField.set(null, previousDispatch);
            Grasscutter.setRunModeOverride(previousMode);
            game.getDatabase().drop();
            dispatch.getDatabase().drop();
            client.close();
        }
    }

    private MongoCollection<Document> accounts(Datastore store) {
        return store.getDatabase().getCollection("accounts");
    }

    private Document row(String id) {
        return accounts(game).find(new Document("_id", id)).first();
    }

    private Account seed() {
        return Objects.requireNonNull(DatabaseHelper.createAccount("save-fixture"));
    }

    private Account read(String id) {
        return DatabaseHelper.getAccountById(id);
    }

    private void flush(Account account) throws Exception {
        account.save();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
    }

    private void legacy() {
        accounts(game)
                .insertOne(
                        new Document("_id", "legacy")
                                .append("username", "legacy-player")
                                .append("password", "fixture-only")
                                .append("token", "old-token")
                                .append("sessionKey", "old-session")
                                .append("permissions", List.of("fixture.old")));
    }

    @Test
    void aStaleCopyCannotRollbackCredentialsPermissionsOrBanState() throws Exception {
        String id = seed().getId();
        var stale = read(id);
        var current = read(id);
        current.setPassword("new-fixture-password");
        current.setToken("new-token");
        current.setSessionKey("new-session");
        current.addPermission("fixture.new");
        current.setBanned(true);
        current.setBanReason("fixture");
        flush(current);
        var confirmed = row(id);
        stale.setEmail("stale@example.invalid");
        flush(stale);
        assertEquals(confirmed, row(id));
    }

    @Test
    void anAlreadyQueuedOldCopyCannotRollbackANewerSynchronousSave() throws Exception {
        String id = seed().getId();
        var stale = read(id);
        var current = read(id);
        Document confirmed;
        try (var held =
                new AccountCreationMongoTest.HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            stale.setEmail("queued@example.invalid");
            stale.save();
            current.setToken("acknowledged-token");
            DatabaseHelper.saveGameSync(current);
            confirmed = row(id);
        }
        assertEquals(confirmed, row(id));
    }

    @Test
    void aLoadedCopyCannotResurrectADeletedAccount() throws Exception {
        String id = seed().getId();
        var copy = read(id);
        DatabaseHelper.deleteAccount(copy);
        assertNull(row(id));
        flush(copy);
        assertNull(row(id));
    }

    @Test
    void anUnattachedObjectCannotReplaceAnExistingAccount() throws Exception {
        String id = seed().getId();
        var confirmed = row(id);
        var copy = new Account();
        copy.setId(id);
        copy.setUsername("forged-fixture");
        flush(copy);
        assertEquals(confirmed, row(id));
    }

    @Test
    void ordinarySaveCannotCreateAnUnreservedAccount() throws Exception {
        var fresh = new Account();
        fresh.setId("fresh");
        fresh.setUsername("fresh-fixture");
        flush(fresh);
        assertNull(row("fresh"));
    }

    @Test
    void aLoadedAccountCannotMoveIntoAnotherAccountStore() throws Exception {
        String id = seed().getId();
        var copy = read(id);
        var original = row(id);
        var elsewhere =
                new Document("_id", id)
                        .append("username", "other-fixture")
                        .append("permissions", List.of("elsewhere"));
        accounts(dispatch).insertOne(elsewhere);
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        copy.setEmail("wrong-store@example.invalid");
        flush(copy);
        assertEquals(elsewhere, accounts(dispatch).find(new Document("_id", id)).first());
        assertEquals(original, row(id));
    }

    @Test
    void theGenericGameSavePathCannotBypassAccountRevisionChecks() throws Exception {
        String id = seed().getId();
        var stale = read(id);
        var current = read(id);
        current.setToken("new-token");
        DatabaseHelper.saveGameSync(current);
        var confirmed = row(id);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(stale));
        assertEquals(confirmed, row(id));
    }

    @Test
    void twoLegacyCopiesCanOnlyPromoteTheOriginalVersionOnce() throws Exception {
        legacy();
        var current = read("legacy");
        var stale = read("legacy");
        current.setSessionKey("promoted-session");
        flush(current);
        var confirmed = row("legacy");
        stale.setToken("stale-token");
        flush(stale);
        assertEquals(confirmed, row("legacy"));
    }

    @Test
    void merelyReadingLegacyDataDoesNotWriteAMigration() {
        legacy();
        var before = row("legacy");
        assertNotNull(read("legacy"));
        assertEquals(before, row("legacy"));
    }

    @Test
    void successiveSavesOfTheSameAcknowledgedObjectRemainUsable() throws Exception {
        var current = seed();
        current.setToken("first-token");
        flush(current);
        current.setSessionKey("second-session");
        flush(current);
        assertEquals("first-token", row(current.getId()).getString("token"));
        assertEquals("second-session", row(current.getId()).getString("sessionKey"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"session", "v2-session", "login"})
    void anAuthenticationTokenIsStoredBeforeItIsReturnedEvenWithABusyQueue(String kind)
            throws Exception {
        var account = seed();
        try (var held =
                new AccountCreationMongoTest.HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            String token =
                    switch (kind) {
                        case "session" -> account.generateSessionKey();
                        case "v2-session" -> account.generateV2SessionKey();
                        default -> account.generateLoginToken();
                    };
            assertEquals(
                    token,
                    row(account.getId()).getString(kind.equals("login") ? "token" : "sessionKey"));
        }
    }

    @Test
    void changingAnAttachedObjectsIdCannotCreateAnotherAccount() throws Exception {
        var account = seed();
        var original = row(account.getId());
        account.setId("changed");
        flush(account);
        assertEquals(
                original, accounts(game).find(new Document("_id", original.get("_id"))).first());
        assertNull(row("changed"));
    }

    @Test
    void aDeletedLegacyCopyCannotReplaceANewIncarnation() throws Exception {
        legacy();
        var old = read("legacy");
        accounts(game).deleteOne(new Document("_id", "legacy"));
        var created = seed();
        var replacement = new Document(row(created.getId()));
        replacement.put("_id", "legacy");
        replacement.put("username", "recreated-fixture");
        accounts(game).insertOne(replacement);
        flush(old);
        assertEquals(replacement, row("legacy"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "mobile"})
    void passportLoginUsesOnlyTheSelectedAccountStore(String mode) {
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        var account =
                DatabaseHelper.createAccountWithUid(
                        "passport-fixture", 0, "fixture-password", List.of("fixture.permission"));
        var unrelated =
                new Document("_id", account.getId())
                        .append("username", "other-realm")
                        .append("token", "other-token");
        accounts(game).insertOne(unrelated);
        String encoded =
                Base64.getEncoder()
                        .encodeToString(
                                "passport-fixture"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LoginByPasswordResponseJson response;
        if (mode.equals("password")) {
            var request = new LoginByPasswordRequestJson();
            request.account = encoded;
            request.password =
                    Base64.getEncoder()
                            .encodeToString(
                                    "fixture-password"
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            response = MaPassportAuthenticator.appLoginByPassword(request);
        } else {
            var request = new LoginByMobileCaptchaRequestJson();
            request.account = encoded;
            response = MaPassportAuthenticator.appLoginByMobileCaptcha(request);
        }
        assertEquals(0, response.retcode);
        assertEquals(
                response.data.token.token,
                accounts(dispatch)
                        .find(new Document("_id", account.getId()))
                        .first()
                        .getString("sessionKey"));
        assertEquals(unrelated, row(account.getId()));
    }

    @Test
    void passportTokenAdoptionIsConfirmedBeforeTheResponse() throws Exception {
        var account = seed();
        var request = new VerifySTokenRequestJson();
        request.mid = account.getId();
        request.stoken = "v2_fixture-adopted";
        try (var held =
                new AccountCreationMongoTest.HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            var response = MaPassportAuthenticator.verifySToken(request);
            assertEquals(0, response.retcode);
            assertEquals(request.stoken, row(account.getId()).getString("sessionKey"));
        }
    }

    @Test
    void aMissingPassportTokenRetainsTheStoredValueWithoutWriting() {
        var account = seed();
        account.generateV2SessionKey();
        var before = row(account.getId());
        var request = new VerifySTokenRequestJson();
        request.mid = account.getId();
        assertEquals(0, MaPassportAuthenticator.verifySToken(request).retcode);
        assertEquals(before, row(account.getId()));
    }

    @Test
    void tokenVerificationDoesNotCreateAnUnknownAccount() {
        var request = new VerifySTokenRequestJson();
        request.mid = "missing";
        request.stoken = "v2_fixture-missing";
        assertNotEquals(0, MaPassportAuthenticator.verifySToken(request).retcode);
        assertEquals(0, accounts(game).countDocuments());
    }
}
