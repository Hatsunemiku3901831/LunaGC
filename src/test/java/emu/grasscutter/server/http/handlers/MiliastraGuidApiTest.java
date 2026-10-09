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

class MiliastraGuidApiTest {
    @TempDir Path directory;

    @Test
    void remappingCreatesAnArchivedCopyAndKeepsTheOriginal() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "guid-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var client = HttpClient.newHttpClient();
            byte[] original;
            try (var stream = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
                original = stream.readAllBytes();
            }
            var uploaded =
                    client.send(
                            request(base + "?name=original.gia")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(original))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, uploaded.statusCode(), uploaded.body());
            String id =
                    JsonParser.parseString(uploaded.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString();
            var remapped =
                    client.send(
                            request(base + "/" + id + "/remap?firstGuid=1100000000")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, remapped.statusCode(), remapped.body());
            var result = JsonParser.parseString(remapped.body()).getAsJsonObject();
            assertEquals(1, result.get("guidCount").getAsInt());
            String copy = result.getAsJsonObject("asset").get("id").getAsString();
            assertNotEquals(id, copy);
            var source =
                    client.send(
                            request(base + "/" + id + "/download").GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertArrayEquals(original, source.body());
            var repeated =
                    client.send(
                            request(base + "/" + id + "/remap?firstGuid=1100000000")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, repeated.statusCode(), repeated.body());
            assertEquals(
                    copy,
                    JsonParser.parseString(repeated.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString());
            for (String query : new String[] {"0", "abc", "2147483648", "1024346482"}) {
                var invalid =
                        client.send(
                                request(base + "/" + id + "/remap?firstGuid=" + query)
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(400, invalid.statusCode(), invalid.body());
                assertTrue(JsonParser.parseString(invalid.body()).getAsJsonObject().has("message"));
            }
            var forbidden =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/remap"))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, forbidden.statusCode());
            var missing =
                    client.send(
                            request(base + "/" + "0".repeat(64) + "/remap")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer guid-test");
    }
}
