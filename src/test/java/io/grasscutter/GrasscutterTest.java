package io.grasscutter;

import com.google.gson.JsonParser;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer.Region;
import emu.grasscutter.config.Configuration;

import lombok.Getter;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/** Testing entrypoint for {@link Grasscutter}. */
public final class GrasscutterTest {
    @Getter private static final OkHttpClient httpClient = new OkHttpClient();

    @Getter private static int httpPort = -1;
    @Getter private static int gamePort = -1;

    /**
     * Creates an HTTP URL.
     *
     * @param route The route to use.
     * @return The URL.
     */
    public static String http(String route) {
        return "http://127.0.0.1:" + GrasscutterTest.httpPort + "/" + route;
    }

    @BeforeAll
    public static void entry() {
        try {
            Grasscutter.getConfig();
            Configuration.DISPATCH_INFO.regions =
                    List.of(new Region("os_usa", "Isolated test", "127.0.0.1", 22102));
            Configuration.SERVER.http.bindAddress = "127.0.0.1";
            Configuration.SERVER.http.bindPort = 0;
            // Start Grasscutter.
            Grasscutter.main(new String[] {"-test", "-dispatch"});
        } catch (Exception exception) {
            throw new AssertionError("Grasscutter failed to start.", exception);
        }

        // Set the ports.
        GrasscutterTest.httpPort = Grasscutter.getHttpServer().getHandle().port();
        GrasscutterTest.gamePort = Configuration.SERVER.game.bindPort;
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (Grasscutter.getHttpServer() != null) Grasscutter.getHttpServer().getHandle().stop();
        if (Grasscutter.getDispatchServer() != null) Grasscutter.getDispatchServer().stop(1000);
    }

    @Test
    @DisplayName("HTTP server check")
    public void checkHttpServer() {
        // Create a request.
        var request = new Request.Builder().url(GrasscutterTest.http("")).build();

        // Perform the request.
        try (var response = GrasscutterTest.httpClient.newCall(request).execute()) {
            // Check the response.
            Assertions.assertTrue(response.isSuccessful());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void runtimeHealthWorksInDispatchModeAndGmDiagnosticsRequireTheToken()
            throws IOException {
        var config = Configuration.SERVER.gm;
        var originalToken = config.accessToken;
        var originalLoopback = config.loopbackOnly;
        try {
            config.accessToken = "isolated-validation-token";
            config.loopbackOnly = false;
            try (var response =
                    httpClient
                            .newCall(new Request.Builder().url(http("status/server")).build())
                            .execute()) {
                Assertions.assertEquals(200, response.code());
                var status =
                        JsonParser.parseString(response.body().string())
                                .getAsJsonObject()
                                .getAsJsonObject("status");
                Assertions.assertTrue(
                        status.getAsJsonObject("health").get("healthy").getAsBoolean());
                Assertions.assertEquals(
                        4, status.getAsJsonObject("health").getAsJsonArray("queues").size());
            }
            for (var route : new String[] {"gm/api/runtime", "gm/api/database"}) {
                try (var response =
                        httpClient
                                .newCall(new Request.Builder().url(http(route)).build())
                                .execute()) {
                    Assertions.assertEquals(403, response.code());
                }
                try (var response =
                        httpClient
                                .newCall(
                                        new Request.Builder()
                                                .url(http(route))
                                                .header(
                                                        "Authorization",
                                                        "Bearer isolated-validation-token")
                                                .build())
                                .execute()) {
                    Assertions.assertEquals(200, response.code());
                    Assertions.assertEquals("no-store", response.header("Cache-Control"));
                    var body = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    Assertions.assertEquals(0, body.get("retcode").getAsInt());
                    Assertions.assertEquals(
                            4,
                            route.endsWith("database")
                                    ? body.getAsJsonArray("queues").size()
                                    : body.getAsJsonObject("status")
                                            .getAsJsonArray("queues")
                                            .size());
                }
            }
            try (var response =
                    httpClient
                            .newCall(
                                    new Request.Builder()
                                            .url(http("gm/api/players"))
                                            .header(
                                                    "Authorization",
                                                    "Bearer isolated-validation-token")
                                            .build())
                            .execute()) {
                Assertions.assertEquals(200, response.code());
                var body = JsonParser.parseString(response.body().string()).getAsJsonObject();
                Assertions.assertTrue(body.getAsJsonArray("players").isEmpty());
            }
            for (var route :
                    new String[] {"gm/api/combat?uid=10001", "gm/api/combat/reset?uid=10001"}) {
                var request = new Request.Builder().url(http(route));
                if (route.contains("/reset"))
                    request.post(
                            okhttp3.RequestBody.create(
                                    "", okhttp3.MediaType.parse("application/json")));
                try (var response = httpClient.newCall(request.build()).execute()) {
                    Assertions.assertEquals(403, response.code());
                }
                request.header("Authorization", "Bearer isolated-validation-token");
                try (var response = httpClient.newCall(request.build()).execute()) {
                    Assertions.assertEquals(404, response.code());
                }
            }
        } finally {
            config.accessToken = originalToken;
            config.loopbackOnly = originalLoopback;
        }
    }
}
