package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraVoxelApiTest {
    @TempDir Path directory;

    @Test
    void generatesReferenceVoxelArrayThroughProtectedHttp() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app =
                io.javalin.Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "voxel-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            String url =
                    "http://127.0.0.1:"
                            + app.port()
                            + "/gm/api/miliastra/generate/voxel?name=voxel.gia&unit=0.25&x=10&y=20&z=30&firstGuid=1100000000";
            var client = HttpClient.newHttpClient();
            var response =
                    client.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .header("Authorization", "Bearer voxel-test")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    "[{\"x\":-2,\"y\":3,\"z\":4,\"color\":\"#877264\"}]"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode(), response.body());
            var result = JsonParser.parseString(response.body()).getAsJsonObject();
            assertEquals(1, result.get("count").getAsInt());
            assertEquals(1100000000, result.get("firstAutomaticGuid").getAsInt());
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            String id = result.getAsJsonObject("asset").get("id").getAsString();
            var download =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/download"))
                                    .header("Authorization", "Bearer voxel-test")
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, download.statusCode());
            var gia =
                    emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCollection.GIACollection.parseFrom(
                            java.util.Arrays.copyOfRange(download.body(), 20, download.body().length - 4));
            assertEquals(1100000000, gia.getAssets(0).getMeta().getAssetId());
            var position =
                    gia.getAssets(0).getEntityData().getData().getComponents(0).getTransform().getPosition();
            assertEquals(9.5f, position.getX());
            assertEquals(20.75f, position.getY());
            assertEquals(31f, position.getZ());
            for (String params :
                    new String[] {
                        "unit=0",
                        "unit=NaN",
                        "alphaThreshold=256",
                        "alphaThreshold=1.5",
                        "x=NaN",
                        "firstGuid=0",
                        "firstGuid=1100000000"
                    })
                assertEquals(
                        400,
                        client
                                .send(
                                        HttpRequest.newBuilder(URI.create(base + "/generate/voxel?" + params))
                                                .header("Authorization", "Bearer voxel-test")
                                                .POST(
                                                        HttpRequest.BodyPublishers.ofString(
                                                                "[{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#877264\"}]"))
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString())
                                .statusCode(),
                        params);
            assertEquals(
                    413,
                    client
                            .send(
                                    HttpRequest.newBuilder(URI.create(base + "/generate/voxel"))
                                            .header("Authorization", "Bearer voxel-test")
                                            .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[8 * 1024 * 1024 + 1]))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            var denied =
                    client.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .POST(HttpRequest.BodyPublishers.ofString("[]"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }
}
