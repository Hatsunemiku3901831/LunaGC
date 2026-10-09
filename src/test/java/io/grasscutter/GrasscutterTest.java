package io.grasscutter;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import com.google.gson.JsonParser;
import java.io.IOException;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            // Start Grasscutter.
            Grasscutter.main(new String[] {"-test"});
        } catch (Exception exception) {
            throw new AssertionError("Grasscutter failed to start.", exception);
        }

        // Set the ports.
        GrasscutterTest.httpPort = Configuration.SERVER.http.bindPort;
        GrasscutterTest.gamePort = Configuration.SERVER.game.bindPort;
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
    public void runtimeHealthWorksInDispatchModeAndGmDiagnosticsRequireTheToken() throws IOException {
        var config = Configuration.SERVER.gm;
        var originalToken = config.accessToken;
        var originalLoopback = config.loopbackOnly;
        try {
            config.accessToken = "isolated-validation-token";
            config.loopbackOnly = false;
            try (var response = httpClient.newCall(new Request.Builder().url(http("status/server")).build()).execute()) {
                Assertions.assertEquals(200, response.code());
                var status = JsonParser.parseString(response.body().string()).getAsJsonObject().getAsJsonObject("status");
                Assertions.assertTrue(status.getAsJsonObject("health").get("healthy").getAsBoolean());
                Assertions.assertEquals(4, status.getAsJsonObject("health").getAsJsonArray("queues").size());
            }
            for (var route : new String[]{"gm/api/runtime", "gm/api/database"}) {
                try (var response = httpClient.newCall(new Request.Builder().url(http(route)).build()).execute()) {
                    Assertions.assertEquals(403, response.code());
                }
                try (var response = httpClient.newCall(new Request.Builder().url(http(route))
                        .header("Authorization", "Bearer isolated-validation-token").build()).execute()) {
                    Assertions.assertEquals(200, response.code());
                    Assertions.assertEquals("no-store", response.header("Cache-Control"));
                    var body = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    Assertions.assertEquals(0, body.get("retcode").getAsInt());
                    Assertions.assertEquals(4, route.endsWith("database") ? body.getAsJsonArray("queues").size()
                            : body.getAsJsonObject("status").getAsJsonArray("queues").size());
                }
            }
            try (var response = httpClient.newCall(new Request.Builder().url(http("gm/api/players"))
                    .header("Authorization", "Bearer isolated-validation-token").build()).execute()) {
                Assertions.assertEquals(200, response.code());
                var body = JsonParser.parseString(response.body().string()).getAsJsonObject();
                Assertions.assertTrue(body.getAsJsonArray("players").isEmpty());
            }
        } finally { config.accessToken = originalToken; config.loopbackOnly = originalLoopback; }
    }
}
