package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.props.*;
import emu.grasscutter.server.game.GameServer;

import io.javalin.Javalin;

import okhttp3.*;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

class DamageLogHttpTest {
    DamageLogTest environment;
    Javalin http;
    Field globalServer;
    Object originalServer;
    String originalToken;
    boolean originalLoopback;
    final OkHttpClient client = new OkHttpClient();

    @BeforeEach
    void setup() throws Exception {
        environment = new DamageLogTest();
        environment.setup();
        var player = environment.environment.fixture.player;
        var server = player.getWorld().getServer();
        var players = GameServer.class.getDeclaredField("players");
        players.setAccessible(true);
        var online = new ConcurrentHashMap<Integer, emu.grasscutter.game.player.Player>();
        online.put(player.getUid(), player);
        players.set(server, online);
        globalServer = Grasscutter.class.getDeclaredField("gameServer");
        globalServer.setAccessible(true);
        originalServer = globalServer.get(null);
        globalServer.set(null, server);
        originalToken = Configuration.SERVER.gm.accessToken;
        originalLoopback = Configuration.SERVER.gm.loopbackOnly;
        Configuration.SERVER.gm.accessToken = "damage-log-test-token";
        Configuration.SERVER.gm.loopbackOnly = true;
        http = Javalin.create();
        var routes =
                Class.forName("emu.grasscutter.server.http.handlers.GmCombat")
                        .getDeclaredMethod("applyRoutes", Javalin.class);
        routes.setAccessible(true);
        routes.invoke(null, http);
        new emu.grasscutter.server.http.HttpServer.UnhandledRequestRouter().applyRoutes(http);
        http.start("127.0.0.1", 0);
        environment.victim.damage(25, environment.environment.avatar.getId(), ElementType.Fire);
    }

    @AfterEach
    void close() throws Exception {
        if (http != null) http.stop();
        if (globalServer != null) globalServer.set(null, originalServer);
        Configuration.SERVER.gm.accessToken = originalToken;
        Configuration.SERVER.gm.loopbackOnly = originalLoopback;
        if (environment != null) environment.close();
    }

    Response call(String route, boolean reset, boolean authorized) throws Exception {
        var request = new Request.Builder().url("http://127.0.0.1:" + http.port() + route);
        if (reset) request.post(RequestBody.create("", MediaType.parse("application/json")));
        if (authorized) request.header("Authorization", "Bearer damage-log-test-token");
        return client.newCall(request.build()).execute();
    }

    @Test
    void queryRequiresTheExistingGmToken() throws Exception {
        try (var r = call("/gm/api/combat?uid=10001", false, false)) {
            assertEquals(403, r.code());
        }
    }

    @Test
    void unauthorizedResetCannotClearStatistics() throws Exception {
        try (var r = call("/gm/api/combat/reset?uid=10001", true, false)) {
            assertEquals(403, r.code());
        }
        assertEquals(
                25, environment.snapshot(environment.worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void missingAndInvalidUidDoNotSilentlyPickAnyOnlinePlayer() throws Exception {
        for (var route :
                new String[] {"/gm/api/combat", "/gm/api/combat?uid=no", "/gm/api/combat?uid=-1"})
            try (var r = call(route, false, true)) {
                assertEquals(400, r.code());
            }
    }

    @Test
    void offlineUidReturnsAnExplicitNotFound() throws Exception {
        try (var r = call("/gm/api/combat?uid=20001", false, true)) {
            assertEquals(404, r.code());
        }
    }

    @Test
    void authenticatedQueryReturnsTheSelectedWorldAndDisablesCaching() throws Exception {
        try (var r = call("/gm/api/combat?uid=10001", false, true)) {
            assertEquals(200, r.code());
            assertEquals("no-store", r.header("Cache-Control"));
            var s = JsonParser.parseString(r.body().string()).getAsJsonObject();
            assertEquals(10001, s.get("uid").getAsInt());
            assertEquals(25, s.get("totalDamage").getAsDouble());
        }
    }

    @Test
    void authenticatedResetClearsTheSelectedWorld() throws Exception {
        try (var r = call("/gm/api/combat/reset?uid=10001", true, true)) {
            assertEquals(200, r.code());
            var s = JsonParser.parseString(r.body().string()).getAsJsonObject();
            assertEquals(0, s.get("totalDamage").getAsDouble());
        }
    }

    @Test
    void resetRouteDoesNotAcceptAGetRequest() throws Exception {
        try (var r = call("/gm/api/combat/reset?uid=10001", false, true)) {
            assertEquals(404, r.code());
        }
        assertEquals(
                25, environment.snapshot(environment.worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void missingGmRouteRetainsItsNotFoundStatus() throws Exception {
        try (var response = call("/gm/api/no-such-feature", false, true)) {
            assertEquals(404, response.code());
        }
    }

    @Test
    void legacySdkFallbackStillReturnsItsExpectedEmptySuccess() throws Exception {
        try (var response = call("/legacy-sdk-no-such-feature", false, false)) {
            assertEquals(200, response.code());
            var body = JsonParser.parseString(response.body().string()).getAsJsonObject();
            assertEquals(0, body.get("retcode").getAsInt());
            assertEquals(0, body.getAsJsonObject("data").size());
        }
    }
}
