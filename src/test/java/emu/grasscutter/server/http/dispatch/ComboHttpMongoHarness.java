package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.auth.DefaultAuthentication;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.Account;
import io.javalin.Javalin;
import io.javalin.json.JavalinGson;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bson.Document;

/** Real test-only Mongo/HTTP domain. No account, token generator, authenticator or save stubs. */
final class ComboHttpMongoHarness implements AutoCloseable {
    static final String UID = "9007199254741009";
    static final String UNKNOWN_UID = "9223372036854775807";
    static final String ROUTE = "/hk4e_cn/combo/granter/login/v2/login";
    final String sessionToken = "owned-combo-session-" + UUID.randomUUID();
    private final String initialLoginToken = "owned-combo-old-login-" + UUID.randomUUID();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private MongoClient mongo;
    private Datastore store;
    private Field storeField;
    private Object previousStore;
    private ServerRunMode previousMode;
    private AuthenticationSystem previousAuth;
    private boolean previousAutoCreate;
    private Javalin app;

    static ComboHttpMongoHarness open() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        check(uri != null && !uri.isBlank(), "LUNAGC_TEST_MONGO_URI is required; this suite never skips");
        var connection = new ConnectionString(uri);
        check(connection.getHosts().equals(List.of("127.0.0.1:27027"))
                        && connection.getCredential() == null,
                "Use the dedicated unauthenticated 127.0.0.1:27027 test Mongo only");
        var fixture = new ComboHttpMongoHarness();
        try {
            fixture.initialize(uri);
            return fixture;
        } catch (Exception | AssertionError failure) {
            fixture.close();
            throw new IllegalStateException("Combo HTTP fixture initialization failed");
        }
    }

    private void initialize(String uri) throws Exception {
        Grasscutter.getConfig();
        check(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)), "Previous database work did not finish");
        previousMode = Grasscutter.getRunMode();
        previousAuth = Grasscutter.getAuthenticationSystem();
        previousAutoCreate = ACCOUNT.autoCreate;
        storeField = DatabaseManager.class.getDeclaredField("gameDatastore");
        storeField.setAccessible(true);
        previousStore = storeField.get(null);
        mongo = MongoClients.create(uri);
        store = Morphia.createDatastore(mongo, "lunagc_combo_http_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Account.class);
        storeField.set(null, store);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        Grasscutter.setAuthenticationSystem(new DefaultAuthentication());
        ACCOUNT.autoCreate = false;
        rows().insertOne(new Document("_id", UID).append("username", "owned-combo-http")
                .append("passwordRequired", true).append("password", "unused-owned-password-fixture")
                .append("token", initialLoginToken).append("sessionKey", sessionToken)
                .append("reservedPlayerId", 170012345).append("permissions", List.of("fixture.permission")));
        app = Javalin.create(config -> config.jsonMapper(new JavalinGson()));
        new AuthenticationHandler().applyRoutes(app);
        app.start("127.0.0.1", 0);
    }

    MongoCollection<Document> rows() {
        return store.getDatabase().getCollection("accounts");
    }

    Document accountRow() {
        return rows().find(new Document("_id", UID)).first();
    }

    String url() {
        return "http://127.0.0.1:" + app.port() + ROUTE;
    }

    static String request(String uid, String token, boolean includeToken) {
        var nested = new JsonObject();
        nested.addProperty("unicode", "中文é😀");
        nested.addProperty("n", Long.MAX_VALUE);
        var inner = new JsonObject();
        inner.addProperty("uid", uid);
        if (includeToken) inner.addProperty("token", token);
        inner.addProperty("guest", false);
        inner.add("nested", nested);
        var outer = new JsonObject();
        outer.addProperty("app_id", "4");
        outer.addProperty("channel_id", "1");
        outer.addProperty("data", inner.toString());
        outer.addProperty("device", "owned-device-设备");
        // The current default receiver does not consume sign. Native PIPE cases replace this body.
        outer.addProperty("sign", "owned-shape-only");
        return outer.toString();
    }

    JsonObject exercise(String label, String rawRequest, boolean success) throws Exception {
        JsonObject outer = parse(rawRequest);
        check(outer.get("app_id").getAsJsonPrimitive().isString()
                        && outer.get("channel_id").getAsJsonPrimitive().isString()
                        && outer.get("data").getAsJsonPrimitive().isString(), "SDK outer fields must be strings");
        JsonObject inner = parse(outer.get("data").getAsString());
        String expectedUid = label.equals("unknown_uid") ? UNKNOWN_UID : UID;
        check(inner.get("uid").getAsJsonPrimitive().isString()
                        && expectedUid.equals(inner.get("uid").getAsString()), "Inner textual UID changed");
        check(inner.getAsJsonObject("nested").get("n").getAsLong() == Long.MAX_VALUE
                        && "中文é😀".equals(inner.getAsJsonObject("nested").get("unicode").getAsString()),
                "Nested owned input changed");
        Document before = accountRow();
        long count = rows().countDocuments();
        HttpResponse<String> reply;
        try {
            reply = http.send(HttpRequest.newBuilder(URI.create(url()))
                            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(rawRequest)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception failure) {
            throw new IllegalStateException("Combo HTTP transport failed");
        }
        check(reply.statusCode() == 200, "Combo route did not return HTTP 200");
        JsonObject response = parse(reply.body());
        check(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)), "Combo database work did not finish");
        Document after = accountRow();
        check(rows().countDocuments() == count && count == 1, "Combo changed account count");
        check(Objects.equals(after.get("sessionKey"), before.get("sessionKey"))
                        && sessionToken.equals(after.getString("sessionKey")), "Protected session key changed");
        check(Objects.equals(after.get("reservedPlayerId"), before.get("reservedPlayerId")), "Player ownership changed");
        var checks = new JsonObject();
        checks.addProperty("defaultAuthenticator", Grasscutter.getAuthenticationSystem() instanceof DefaultAuthentication);
        checks.addProperty("textualUidExact", true);
        checks.addProperty("nestedInputExact", true);
        checks.addProperty("accountCountUnchanged", true);
        checks.addProperty("sessionKeyUnchanged", true);
        checks.addProperty("playerOwnershipUnchanged", true);
        var frame = new JsonObject();
        frame.addProperty("schema", "combo-http-v1");
        frame.addProperty("phase", "response");
        frame.addProperty("case", label);
        frame.addProperty("http_status", reply.statusCode());
        frame.addProperty("body", reply.body());
        if (success) {
            check(response.get("retcode").getAsInt() == 0, "Combo success was rejected");
            JsonObject data = response.getAsJsonObject("data");
            check(data.get("open_id").getAsJsonPrimitive().isString()
                            && UID.equals(data.get("open_id").getAsString()), "Response textual UID changed");
            String generated = data.get("combo_token").getAsString();
            check(!generated.isBlank() && generated.equals(after.getString("token")), "HTTP token differs from real Mongo token");
            check(!generated.equals(before.getString("token")), "Successful combo did not rotate login token");
            check(data.get("data").getAsJsonPrimitive().isString()
                            && !parse(data.get("data").getAsString()).get("guest").getAsBoolean(),
                    "Combo response nested data contract changed");
            checks.addProperty("generatedTokenMatchesMongo", true);
            checks.addProperty("loginTokenRotated", true);
            checks.addProperty("openIdExact64Text", true);
            frame.addProperty("expected_combo_token", generated);
        } else {
            check(response.get("retcode").getAsInt() == -201, "Combo failure status changed");
            var data = response.getAsJsonObject("data");
            check(data == null || !data.has("combo_token") || data.get("combo_token").isJsonNull(), "Failed combo issued token");
            check(before.equals(after), "Failed combo changed account BSON");
            checks.addProperty("failureBsonUnchanged", true);
            checks.addProperty("failureHasNoToken", true);
        }
        frame.add("checks", checks);
        return frame;
    }

    static JsonObject parse(String value) {
        try {
            return JsonParser.parseString(value).getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new AssertionError("Owned HTTP/PIPE JSON shape was rejected");
        }
    }

    static void check(boolean value, String diagnostic) {
        if (!value) throw new AssertionError(diagnostic);
    }

    @Override
    public void close() throws Exception {
        boolean idle = true;
        try {
            if (app != null) app.stop();
            if (store != null) idle = DatabaseHelper.awaitIdle(Duration.ofSeconds(10));
        } finally {
            try {
                if (storeField != null) {
                    storeField.set(null, previousStore);
                    Grasscutter.setRunModeOverride(previousMode);
                    Grasscutter.setAuthenticationSystem(previousAuth);
                    ACCOUNT.autoCreate = previousAutoCreate;
                }
            } finally {
                try {
                    if (store != null) store.getDatabase().drop();
                } finally {
                    if (mongo != null) mongo.close();
                }
            }
        }
        check(idle, "Combo database work did not finish before cleanup");
    }
}
