package emu.grasscutter.auth;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import static org.junit.jupiter.api.Assertions.*;

import at.favre.lib.crypto.bcrypt.BCrypt;

import com.google.gson.JsonObject;
import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.server.packet.recv.HandlerGetPlayerTokenReq;
import emu.grasscutter.utils.*;

import io.javalin.http.Context;

import org.bson.Document;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

import javax.crypto.Cipher;

/** Exercise existing SDK, dispatch and game-token entry points on a post-reset account fixture. */
class PasswordResetAuthenticationTest {
    private static final String ID = "protected-fixture", PASSWORD = "new-fixture-password";
    private static final String HASH =
            BCrypt.withDefaults().hashToString(4, PASSWORD.toCharArray());
    private MongoClient client;
    private Datastore store;
    private Object oldStore;
    private ServerRunMode oldMode;
    private boolean oldAutoCreate;
    private Context context;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        oldStore = field.get(null);
        oldMode = Grasscutter.getRunMode();
        oldAutoCreate = ACCOUNT.autoCreate;
        ACCOUNT.autoCreate = false;
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_reset_auth_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Account.class);
        field.set(null, store);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        rows().insertOne(
                        new Document("_id", ID)
                                .append("username", ID)
                                .append("password", HASH)
                                .append("passwordRequired", true)
                                .append("permissions", List.of("fixture.permission")));
        context =
                (Context)
                        Proxy.newProxyInstance(
                                Context.class.getClassLoader(),
                                new Class<?>[] {Context.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "header" -> null;
                                            case "ip" -> "127.0.0.1";
                                            case "toString" -> "reset-auth-fixture";
                                            default -> throw new UnsupportedOperationException(
                                                    method.getName());
                                        });
    }

    @AfterEach
    void close() throws Exception {
        if (client == null) return;
        try {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        } finally {
            var field = DatabaseManager.class.getDeclaredField("gameDatastore");
            field.setAccessible(true);
            field.set(null, oldStore);
            Grasscutter.setRunModeOverride(oldMode);
            ACCOUNT.autoCreate = oldAutoCreate;
            store.getDatabase().drop();
            client.close();
        }
    }

    private MongoCollection<Document> rows() {
        return store.getDatabase().getCollection("accounts");
    }

    private Document row() {
        return rows().find(new Document("_id", ID)).first();
    }

    private LoginResultJson password(String value, boolean crypto, boolean experimental) {
        var data = new LoginAccountRequestJson();
        data.account = ID;
        data.password = value;
        data.is_crypto = crypto;
        var request = AuthenticationSystem.fromPasswordRequest(context, data);
        return experimental
                ? new DefaultAuthenticators.ExperimentalPasswordAuthenticator()
                        .authenticate(request)
                : new DefaultAuthenticators.PasswordAuthenticator().authenticate(request);
    }

    private ComboTokenResJson combo(String token) {
        var data = new ComboTokenReqJson.LoginTokenData();
        data.uid = ID;
        data.token = token;
        return new DefaultAuthenticators.SessionKeyAuthenticator()
                .authenticate(
                        AuthenticationSystem.fromComboTokenRequest(
                                context, new ComboTokenReqJson(), data));
    }

    private static String fixtureBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encrypted(String value) throws Exception {
        var factory = KeyFactory.getInstance("RSA");
        var privateKey =
                (RSAPrivateCrtKey)
                        factory.generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        FileUtils.readResource("/keys/auth_private-key.der")));
        var publicKey =
                factory.generatePublic(
                        new RSAPublicKeySpec(
                                privateKey.getModulus(), privateKey.getPublicExponent()));
        var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder()
                .encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-fixture-password"})
    void protectedDefaultPasswordEntryRejectsWrongOrMissingCredentials(String provided) {
        var before = row();
        var response = assertDoesNotThrow(() -> password(provided, false, false));
        assertNotEquals(0, response.retcode);
        assertEquals(before, row());
    }

    @Test
    void protectedDefaultPasswordEntryAcceptsTheNewPassword() {
        var response = password(PASSWORD, false, false);
        assertEquals(0, response.retcode);
        assertEquals(response.data.account.token, row().getString("sessionKey"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void encryptedPasswordEntryVerifiesTheDecryptedValue(boolean correct) throws Exception {
        String supplied = encrypted(correct ? PASSWORD : "wrong-fixture-password");
        var response = password(supplied, true, false);
        assertEquals(correct, response.retcode == 0);
    }

    @Test
    void invalidEncryptedInputDoesNotIssueAToken() {
        var before = row();
        assertNotEquals(0, assertDoesNotThrow(() -> password("not-rsa", true, false)).retcode);
        assertEquals(before, row());
    }

    @Test
    void unresetAccountsRetainTheExistingConveniencePasswordPath() {
        rows().updateOne(
                        new Document("_id", ID),
                        new Document("$set", new Document("passwordRequired", false)));
        assertEquals(0, password("different-fixture-password", false, false).retcode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void experimentalPasswordEntryRejectsMissingCredentialsWithoutThrowing(String provided) {
        assertNotEquals(0, assertDoesNotThrow(() -> password(provided, false, true)).retcode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"old-session-key"})
    void comboCannotAdoptAnOldOrMissingSessionAfterReset(String token) {
        var before = row();
        assertNotEquals(0, assertDoesNotThrow(() -> combo(token)).retcode);
        assertEquals(before, row());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"old-session-key"})
    void registryTokenLoginRejectsClearedKeysWithoutThrowing(String token) {
        var request = LoginTokenRequestJson.builder().uid(ID).token(token).build();
        assertNotEquals(
                0,
                assertDoesNotThrow(
                                () ->
                                        new DefaultAuthenticators.TokenAuthenticator()
                                                .authenticate(
                                                        AuthenticationSystem.fromTokenRequest(
                                                                context, request)))
                        .retcode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"old-session-key"})
    void passportCannotAdoptAnOldOrMissingSessionAfterReset(String token) {
        var before = row();
        var request = new VerifySTokenRequestJson();
        request.mid = ID;
        request.stoken = token;
        assertNotEquals(0, MaPassportAuthenticator.verifySToken(request).retcode);
        assertEquals(before, row());
    }

    @Test
    void unverifiedMobileLoginCannotBypassAResetPassword() {
        var before = row();
        var request = new LoginByMobileCaptchaRequestJson();
        request.account = fixtureBase64(ID);
        assertNotEquals(0, MaPassportAuthenticator.appLoginByMobileCaptcha(request).retcode);
        assertEquals(before, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passportPasswordEntryUsesTheNewPassword(boolean correct) {
        var request = new LoginByPasswordRequestJson();
        request.account = fixtureBase64(ID);
        request.password = fixtureBase64(correct ? PASSWORD : "old-fixture-password");
        assertEquals(correct, MaPassportAuthenticator.appLoginByPassword(request).retcode == 0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"old-login-token"})
    void gameTokenValidationRejectsRevokedValuesWithoutThrowing(String token) {
        assertNull(assertDoesNotThrow(() -> DispatchUtils.authenticate(ID, token)));
    }

    @Test
    void nativeGameTokenRequestClosesARevokedLoginBeforePlayerCreation() {
        var closes = new AtomicInteger();
        var session =
                new GameSession(null) {
                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                };
        byte[] payload =
                GetPlayerTokenReq.newBuilder()
                        .setAccountUid(ID)
                        .setAccountToken("old-login-token")
                        .build()
                        .toByteArray();
        assertDoesNotThrow(
                () -> new HandlerGetPlayerTokenReq().handle(session, new byte[0], payload));
        assertEquals(1, closes.get());
        assertNull(session.getAccount());
        assertEquals(0, store.getDatabase().getCollection("players").countDocuments());
    }

    @Test
    void internalDispatchRepliesFalseForAClearedLoginToken() {
        var server = new DispatchServer("127.0.0.1", 0);
        var reply = new AtomicReference<byte[]>();
        var socket =
                (WebSocket)
                        Proxy.newProxyInstance(
                                WebSocket.class.getClassLoader(),
                                new Class<?>[] {WebSocket.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("send")
                                            && args[0] instanceof byte[] bytes) {
                                        reply.set(bytes.clone());
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        var request = new JsonObject();
        request.addProperty("uid", ID);
        request.addProperty("token", "old-login-token");
        assertDoesNotThrow(
                () -> server.getHandlers().get(PacketIds.TokenValidateReq).accept(socket, request));
        assertNotNull(reply.get());
        var packet = server.decodeMessage(reply.get());
        assertFalse(IDispatcher.decode(packet.get("message")).get("valid").getAsBoolean());
    }

    @Test
    void newPasswordCanCompleteSessionComboAndGameValidation() {
        var login = password(PASSWORD, false, false);
        assertEquals(0, login.retcode);
        var combo = combo(login.data.account.token);
        assertEquals(0, combo.retcode);
        assertNotNull(DispatchUtils.authenticate(ID, combo.data.combo_token));
        assertEquals(combo.data.combo_token, row().getString("token"));
    }
}
