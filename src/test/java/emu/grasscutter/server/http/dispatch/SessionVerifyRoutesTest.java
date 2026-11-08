package emu.grasscutter.server.http.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.morphia.Datastore;
import dev.morphia.query.Query;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.SessionVerifyResponseJson;
import emu.grasscutter.server.http.objects.VerifySTokenResponseJson;
import io.javalin.Javalin;
import io.javalin.json.JavalinGson;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real HTTP/serialization and authenticator, with read-only in-memory account lookup, not Mongo. */
class SessionVerifyRoutesTest {
    private static final String SESSION = "/account/ma-cn-session/app/verify";
    private static final String ID = "9007199254740993";
    private final AtomicInteger lookups = new AtomicInteger();
    private final HttpClient client =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
    private String sessionToken;
    private Account account;
    private Field storeField;
    private Object previousStore;
    private ServerRunMode previousMode;
    private Javalin app;

    @BeforeEach
    void startHttpWithReadOnlyAccountFixture() throws Exception {
        Grasscutter.getConfig();
        previousMode = Grasscutter.getRunMode();
        storeField = DatabaseManager.class.getDeclaredField("gameDatastore");
        storeField.setAccessible(true);
        previousStore = storeField.get(null);
        sessionToken = "v2_" + UUID.randomUUID();
        account = new ReadOnlySessionAccount(sessionToken);
        Query<?> query =
                (Query<?>)
                        Proxy.newProxyInstance(
                                Query.class.getClassLoader(),
                                new Class<?>[] {Query.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "filter" -> proxy;
                                            case "first" -> {
                                                lookups.incrementAndGet();
                                                yield account;
                                            }
                                            default -> throw new AssertionError(
                                                    "Unexpected query operation: " + method.getName());
                                        });
        Datastore store =
                (Datastore)
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("find") && args[0] == Account.class) {
                                        return query;
                                    }
                                    throw new AssertionError(
                                            "Unexpected datastore operation: " + method.getName());
                                });
        storeField.set(null, store);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        app = Javalin.create(config -> config.jsonMapper(new JavalinGson()));
        new AuthenticationHandler().applyRoutes(app);
        app.start(0);
    }

    @AfterEach
    void restore() throws Exception {
        try {
            if (app != null) app.stop();
        } finally {
            if (storeField != null) storeField.set(null, previousStore);
            if (previousMode != null) Grasscutter.setRunModeOverride(previousMode);
        }
    }

    @Test
    void sessionVerifyUsesTheSingleTokenFromOneAuthentication() throws Exception {
        var reply = post(SESSION, nestedRequest(sessionToken));
        assertEquals(200, reply.statusCode());
        var response = json(reply);
        assertEquals(0, response.get("retcode").getAsInt());
        assertTrue(response.get("message").getAsJsonPrimitive().isString());
        var data = response.getAsJsonObject("data");
        assertTrue(data.has("token"), "Session verify must return a single data.token object");
        assertFalse(data.has("tokens"), "The session response must not use the passport token array");
        var token = data.getAsJsonObject("token");
        assertTrue(token.get("token_type").getAsJsonPrimitive().isNumber());
        assertEquals(1, token.get("token_type").getAsInt());
        assertTrue(token.get("token").getAsJsonPrimitive().isString());
        // Boolean assertions keep credentials and whole response bodies out of failure reports.
        assertTrue(sessionToken.equals(token.get("token").getAsString()), "Token must remain unchanged");
        assertTrue(sessionToken.equals(account.getSessionKey()), "Session key must remain unchanged");
        assertEquals(1, lookups.get(), "The HTTP request must authenticate exactly once");
        var user = data.getAsJsonObject("user_info");
        assertTrue(user.get("aid").getAsJsonPrimitive().isString());
        assertTrue(user.get("mid").getAsJsonPrimitive().isString());
        assertEquals(ID, user.get("aid").getAsString());
        assertEquals(ID, user.get("mid").getAsString());
        assertEquals(0, user.getAsJsonArray("links").size());
        assertTrue(data.getAsJsonObject("ext_user_info").get("birth").getAsJsonPrimitive().isString());
        assertFalse(data.has("new_token"), "The adapter must not invent token refresh behavior");
    }

    @Test
    void pureAdapterRetainsNonemptyNestedLinksAndDistinct64BitIdStringsOverHttp() throws Exception {
        var verified = new VerifySTokenResponseJson();
        verified.retcode = 0;
        verified.message = "OK";
        verified.data = new VerifySTokenResponseJson.VerifyData();
        verified.data.user_info = new VerifySTokenResponseJson.UserInfoData();
        verified.data.user_info.aid = ID;
        verified.data.user_info.mid = "9223372036854775807";
        var first = new VerifySTokenResponseJson.LinkData();
        first.thirdparty = "fixture-provider-a";
        first.union_id = "fixture-union-a";
        first.nickname = "fixture-name-a";
        var second = new VerifySTokenResponseJson.LinkData();
        second.thirdparty = "fixture-provider-b";
        second.union_id = "fixture-union-b";
        second.nickname = "fixture-name-b";
        verified.data.user_info.links = List.of(first, second);
        var verifiedToken = new VerifySTokenResponseJson.TokenData();
        verifiedToken.token_type = 1;
        verifiedToken.token = sessionToken;
        verified.data.tokens = List.of(verifiedToken);
        verified.data.ext_user_info = new VerifySTokenResponseJson.ExtUserInfoData();
        verified.data.ext_user_info.birth = "0";

        // This route exercises only the DTO's JSON projection of an already successful result.
        // Production authentication creates an empty links list; this is not a Mongo/native test.
        var adapted = new SessionVerifyResponseJson(verified);
        app.post("/fixture/session-response", ctx -> ctx.json(adapted));
        var reply = post("/fixture/session-response", "{}");
        assertEquals(200, reply.statusCode());
        var response = json(reply);
        var data = response.getAsJsonObject("data");
        assertFalse(data.has("tokens"));
        assertFalse(data.has("new_token"));
        var user = data.getAsJsonObject("user_info");
        assertTrue(user.get("aid").getAsJsonPrimitive().isString());
        assertTrue(user.get("mid").getAsJsonPrimitive().isString());
        assertEquals(ID, user.get("aid").getAsString());
        assertEquals("9223372036854775807", user.get("mid").getAsString());
        var links = user.getAsJsonArray("links");
        assertEquals(2, links.size());
        assertEquals("fixture-provider-a", links.get(0).getAsJsonObject().get("thirdparty").getAsString());
        assertEquals("fixture-union-a", links.get(0).getAsJsonObject().get("union_id").getAsString());
        assertEquals("fixture-name-a", links.get(0).getAsJsonObject().get("nickname").getAsString());
        assertEquals("fixture-provider-b", links.get(1).getAsJsonObject().get("thirdparty").getAsString());
        assertEquals("fixture-union-b", links.get(1).getAsJsonObject().get("union_id").getAsString());
        assertEquals("fixture-name-b", links.get(1).getAsJsonObject().get("nickname").getAsString());
        var token = data.getAsJsonObject("token");
        assertTrue(token.get("token_type").getAsJsonPrimitive().isNumber());
        assertEquals(1, token.get("token_type").getAsInt());
        assertTrue(token.get("token").getAsJsonPrimitive().isString());
        assertTrue(sessionToken.equals(token.get("token").getAsString()), "Token must remain unchanged");
        assertEquals(0, lookups.get(), "The pure DTO adapter must not authenticate");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/account/ma-cn-passport/token/verifySToken",
                "/hk4e_cn/account/ma-passport/token/verifySToken",
                "/hk4e_global/account/ma-passport/token/verifySToken"
            })
    void passportVerifyRetainsItsExistingTokenArray(String route) throws Exception {
        var reply = post(route, nestedRequest(sessionToken));
        assertEquals(200, reply.statusCode());
        var response = json(reply);
        assertEquals(0, response.get("retcode").getAsInt());
        var data = response.getAsJsonObject("data");
        assertFalse(data.has("token"));
        var tokens = data.getAsJsonArray("tokens");
        assertEquals(1, tokens.size());
        var token = tokens.get(0).getAsJsonObject();
        assertEquals(1, token.get("token_type").getAsInt());
        assertTrue(sessionToken.equals(token.get("token").getAsString()), "Token must remain unchanged");
        assertEquals(1, lookups.get());
    }

    @Test
    void rejectedSessionMatchesThePassportErrorAndCarriesNoToken() throws Exception {
        String request = nestedRequest("v2_" + UUID.randomUUID());
        var session = post(SESSION, request);
        var passport = post("/account/ma-cn-passport/token/verifySToken", request);
        assertEquals(200, session.statusCode());
        assertEquals(passport.statusCode(), session.statusCode());
        var response = json(session);
        assertEquals(-101, response.get("retcode").getAsInt());
        assertTrue(response.equals(json(passport)), "Session and passport must preserve the same error");
        assertNoToken(response);
        assertTrue(sessionToken.equals(account.getSessionKey()), "Rejected credentials must not replace the key");
        assertEquals(2, lookups.get());
    }

    @Test
    void missingAccountCarriesNoToken() throws Exception {
        account = null;
        var reply = post(SESSION, nestedRequest(sessionToken));
        assertEquals(200, reply.statusCode());
        var response = json(reply);
        assertEquals(-101, response.get("retcode").getAsInt());
        assertNoToken(response);
        assertEquals(1, lookups.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{", "{\"token\":{\"token_type\":\"invalid-type\"}}"})
    void invalidSessionBodyKeepsTheExistingBadRequestContract(String request) throws Exception {
        var reply = post(SESSION, request);
        assertEquals(400, reply.statusCode());
        var response = json(reply);
        assertEquals(-1, response.get("retcode").getAsInt());
        assertNoToken(response);
        assertEquals(0, lookups.get());
    }

    /** Only the pipe-only native bridge consumes these raw HTTP bodies and expected credential. */
    JsonObject nativeResponses() throws Exception {
        var success = post(SESSION, nestedRequest(sessionToken));
        assertEquals(200, success.statusCode());
        var successJson = json(success);
        assertEquals(0, successJson.get("retcode").getAsInt());
        assertTrue(successJson.getAsJsonObject("data").has("token"));
        assertEquals(1, lookups.get());

        var error = post(SESSION, nestedRequest("v2_" + UUID.randomUUID()));
        assertEquals(200, error.statusCode());
        var errorJson = json(error);
        assertEquals(-101, errorJson.get("retcode").getAsInt());
        assertNoToken(errorJson);
        assertEquals(2, lookups.get());

        var passport = post("/account/ma-cn-passport/token/verifySToken", nestedRequest(sessionToken));
        assertEquals(200, passport.statusCode());
        assertEquals(0, json(passport).get("retcode").getAsInt());
        assertTrue(json(passport).getAsJsonObject("data").has("tokens"));
        assertTrue(sessionToken.equals(account.getSessionKey()), "Session key must remain unchanged");
        assertEquals(3, lookups.get());

        var envelope = new JsonObject();
        envelope.addProperty("session_success", success.body());
        envelope.addProperty("session_error", error.body());
        envelope.addProperty("passport_success", passport.body());
        envelope.addProperty("expected_token", sessionToken);
        envelope.addProperty("lookup_count", lookups.get());
        return envelope;
    }

    private String nestedRequest(String token) {
        var nested = new JsonObject();
        nested.addProperty("token_type", 1);
        nested.addProperty("token", token);
        var request = new JsonObject();
        request.addProperty("mid", ID);
        request.add("token", nested);
        request.addProperty("refresh", true);
        return request.toString();
    }

    private HttpResponse<String> post(String route, String body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + route))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> reply) {
        return JsonParser.parseString(reply.body()).getAsJsonObject();
    }

    private static void assertNoToken(JsonObject response) {
        assertTrue(!response.has("data") || response.get("data").isJsonNull(), "Errors must not carry session data");
        assertFalse(response.has("token"));
        assertFalse(response.has("tokens"));
    }

    private static final class ReadOnlySessionAccount extends Account {
        private ReadOnlySessionAccount(String token) {
            setId(ID);
            setUsername("session-http-fixture");
            super.setSessionKey(token);
        }

        @Override
        public boolean isPasswordRequired() {
            return true;
        }

        @Override
        public void setSessionKey(String token) {
            throw new AssertionError("The session adapter must not replace the key");
        }

        @Override
        public synchronized String generateSessionKey() {
            throw new AssertionError("The session adapter must not generate a key");
        }

        @Override
        public synchronized String generateV2SessionKey() {
            throw new AssertionError("The session adapter must not generate a key");
        }
    }
}
