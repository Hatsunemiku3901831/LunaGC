package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCameraJsonApiTest {
    @TempDir Path directory;

    @Test
    void extractsAndRestoresCameraThroughAuthenticatedHttp() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app =
                io.javalin.Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "camera-json-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var generated =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/generate/camera?name=test.gia"))
                                    .header("Authorization", "Bearer camera-json-test")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    "{\"cameras\":[{\"preset\":\"thirdPerson\"}]}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, generated.statusCode());
            String id =
                    JsonParser.parseString(generated.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString();
            var extracted =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/camera"))
                                    .header("Authorization", "Bearer camera-json-test")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, extracted.statusCode(), extracted.body());
            var entry =
                    JsonParser.parseString(extracted.body()).getAsJsonObject().getAsJsonObject("asset");
            assertEquals("CAMERA_JSON", entry.get("type").getAsString());
            var restored =
                    client.send(
                            HttpRequest.newBuilder(
                                            URI.create(base + "/" + entry.get("id").getAsString() + "/convert"))
                                    .header("Authorization", "Bearer camera-json-test")
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
            var denied =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/camera"))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }
}
