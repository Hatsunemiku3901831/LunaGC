package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCanvasReverseApiTest {
    @TempDir Path directory;

    @Test
    void realGiaCanReturnToEditableCanvasAndRestoreTheOriginal() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "reverse-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var client = HttpClient.newHttpClient();
            byte[] bytes;
            try (var input = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
                bytes = input.readAllBytes();
            }
            var uploaded =
                    client.send(
                            request(base + "?name=sample.gia")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, uploaded.statusCode());
            String original = id(uploaded.body());
            var reversed =
                    client.send(
                            request(base + "/" + original + "/canvas")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, reversed.statusCode(), reversed.body());
            assertEquals(
                    "GRAPH_JSON",
                    JsonParser.parseString(reversed.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("type")
                            .getAsString());
            String canvas = id(reversed.body());
            var restored =
                    client.send(
                            request(base + "/" + canvas + "/convert")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, restored.statusCode(), restored.body());
            assertEquals(original, id(restored.body()));
            var download =
                    client.send(
                            request(base + "/" + original + "/download").GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertArrayEquals(bytes, download.body());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }

    private static String id(String body) {
        return JsonParser.parseString(body)
                .getAsJsonObject()
                .getAsJsonObject("asset")
                .get("id")
                .getAsString();
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer reverse-test");
    }
}
