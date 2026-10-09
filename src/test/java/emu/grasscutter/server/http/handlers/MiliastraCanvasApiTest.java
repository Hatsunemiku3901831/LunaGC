package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MiliastraCanvasApiTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"editor-sample.server.json", "connected-generics.server.json"})
    void archivedCanvasCanGenerateARealNodeGia(String fixture) throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "canvas-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var client = HttpClient.newHttpClient();
            byte[] bytes;
            try (var stream = getClass().getResourceAsStream("/miliastra/" + fixture)) {
                bytes = stream.readAllBytes();
            }
            var uploaded =
                    client.send(
                            request(base + "?name=canvas.json")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, uploaded.statusCode(), uploaded.body());
            String id =
                    JsonParser.parseString(uploaded.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString();
            var converted =
                    client.send(
                            request(base + "/" + id + "/convert")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, converted.statusCode(), converted.body());
            var entry =
                    JsonParser.parseString(converted.body()).getAsJsonObject().getAsJsonObject("asset");
            assertEquals("GIA", entry.get("type").getAsString());
            String resultId = entry.get("id").getAsString();
            var again =
                    client.send(
                            request(base + "/" + id + "/convert")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, again.statusCode(), again.body());
            assertEquals(
                    resultId,
                    JsonParser.parseString(again.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString());
            var projected =
                    client.send(
                            request(base + "/" + resultId + "/convert")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, projected.statusCode(), projected.body());
            assertEquals(
                    "GIA_JSON",
                    JsonParser.parseString(projected.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("type")
                            .getAsString());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer canvas-test");
    }
}
