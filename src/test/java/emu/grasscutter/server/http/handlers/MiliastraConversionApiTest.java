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

class MiliastraConversionApiTest {
    @TempDir Path directory;

    @Test
    void convertsTheRealNodeGraphIntoAnEditableArchiveAndBack() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String previousToken = config.accessToken;
        boolean previousLoopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "conversion-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            byte[] fixture;
            try (var stream = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
                fixture = stream.readAllBytes();
            }
            var uploaded =
                    client.send(
                            request(base + "?name=sample.gia")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(fixture))
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
            assertEquals("GIA_JSON", entry.get("type").getAsString());
            String jsonId = entry.get("id").getAsString();
            var json =
                    client.send(
                            request(base + "/" + jsonId + "/download").build(),
                            HttpResponse.BodyHandlers.ofString());
            var document = JsonParser.parseString(json.body()).getAsJsonObject();
            assertEquals("lunagc.gia.v1", document.get("format").getAsString());
            assertEquals(
                    3,
                    document
                            .getAsJsonObject("data")
                            .getAsJsonObject("graph")
                            .getAsJsonObject("graph")
                            .getAsJsonObject("inner")
                            .getAsJsonObject("graph")
                            .getAsJsonArray("nodes")
                            .size());
            var restored =
                    client.send(
                            request(base + "/" + jsonId + "/convert")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, restored.statusCode(), restored.body());
            assertEquals(
                    id,
                    JsonParser.parseString(restored.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString());
            assertEquals(
                    403,
                    client
                            .send(
                                    HttpRequest.newBuilder(URI.create(base + "/" + jsonId + "/convert"))
                                            .POST(HttpRequest.BodyPublishers.noBody())
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
        } finally {
            config.accessToken = previousToken;
            config.loopbackOnly = previousLoopback;
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer conversion-test");
    }
}
