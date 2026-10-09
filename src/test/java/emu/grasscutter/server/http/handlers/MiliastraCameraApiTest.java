package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import io.javalin.Javalin;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCameraApiTest {
    @TempDir Path directory;

    @Test
    void generatesCameraFromRealHttpAndProtectsRoutes() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "camera-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var response =
                    client.send(
                            HttpRequest.newBuilder(
                                            URI.create(base + "/generate/camera?name=camera.gia&firstGuid=1100000000"))
                                    .header("Authorization", "Bearer camera-test")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    "{\"cameras\":[{\"preset\":\"thirdPerson\",\"ignoreCollision\":true}]}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode(), response.body());
            var result = JsonParser.parseString(response.body()).getAsJsonObject();
            assertEquals(1, result.get("count").getAsInt());
            String id = result.getAsJsonObject("asset").get("id").getAsString();
            var download =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/download"))
                                    .header("Authorization", "Bearer camera-test")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, download.statusCode());
            assertEquals(
                    13,
                    emu.grasscutter.game.beyond.MiliastraFile.inspect(download.body(), "camera.gia")
                            .assets()
                            .get(0)
                            .type());
            var unauth =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/camera-templates")).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, unauth.statusCode());
            var templates =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/camera-templates"))
                                    .header("Authorization", "Bearer camera-test")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, templates.statusCode());
            assertEquals(
                    5,
                    JsonParser.parseString(templates.body())
                            .getAsJsonObject()
                            .getAsJsonArray("templates")
                            .size());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }
}
