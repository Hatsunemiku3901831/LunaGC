package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.beyond.MiliastraFile;
import io.javalin.Javalin;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraGenerationApiTest {
    @TempDir Path directory;

    @Test
    void authenticatedGenerationValidatesInputAndPublishesDownloadableArchives() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String previousToken = config.accessToken;
        boolean previousLoopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "generation-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            for (String route : new String[] {"/palette", "/generate/model", "/generate/image"}) {
                var request = HttpRequest.newBuilder(URI.create(base + route));
                if (!route.equals("/palette")) request.POST(HttpRequest.BodyPublishers.ofString("{}"));
                assertEquals(
                        403, client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            var palette =
                    client.send(request(base + "/palette").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, palette.statusCode());
            assertEquals(
                    21,
                    JsonParser.parseString(palette.body()).getAsJsonObject().getAsJsonArray("blocks").size());
            byte[] model =
                    (" ".repeat(1100000) + "{\"blocks\":[{\"templateId\":1}]}")
                            .getBytes(StandardCharsets.UTF_8);
            var generated = post(client, base + "/generate/model?name=api.gia", model);
            assertEquals(201, generated.statusCode(), generated.body());
            var metadata = JsonParser.parseString(generated.body()).getAsJsonObject();
            String id = metadata.getAsJsonObject("asset").get("id").getAsString();
            var download =
                    client.send(
                            request(base + "/" + id + "/download").build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, download.statusCode());
            assertEquals(1, MiliastraFile.inspect(download.body(), "api.gia").assetCount());
            var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xFFFFFFFF);
            var png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            var pixels = post(client, base + "/generate/image?name=pixels.gia", png.toByteArray());
            assertEquals(201, pixels.statusCode(), pixels.body());
            assertEquals(
                    57600, JsonParser.parseString(pixels.body()).getAsJsonObject().get("count").getAsInt());
            for (String params :
                    new String[] {
                        "width=no",
                        "firstGuid=0",
                        "unit=NaN",
                        "height=-1",
                        "keepAspect=maybe",
                        "alphaThreshold=256",
                        "plane=ZX"
                    }) {
                assertEquals(
                        400,
                        post(client, base + "/generate/image?" + params, png.toByteArray()).statusCode(),
                        params);
            }
            assertEquals(400, post(client, base + "/generate/image", new byte[0]).statusCode());
            assertEquals(
                    400,
                    post(client, base + "/generate/model", "{}".getBytes(StandardCharsets.UTF_8))
                            .statusCode());
            assertEquals(
                    413, post(client, base + "/generate/model", new byte[8 * 1024 * 1024 + 1]).statusCode());
            assertEquals(
                    413, post(client, base + "/generate/image", new byte[16 * 1024 * 1024 + 1]).statusCode());
            var listing = client.send(request(base).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    2, JsonParser.parseString(listing.body()).getAsJsonObject().get("total").getAsInt());
        } finally {
            config.accessToken = previousToken;
            config.loopbackOnly = previousLoopback;
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer generation-test");
    }

    private static HttpResponse<String> post(HttpClient client, String url, byte[] bytes)
            throws Exception {
        return client.send(
                request(url).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
