package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.Configuration;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class MiliastraApiTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test
    void authenticatedRawImportListsAndExportsIdenticalBytes() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var config = Configuration.SERVER.gm;
        String token = config.accessToken;
        boolean loopback = config.loopbackOnly;
        try (var app = Javalin.create(c -> c.jsonMapper(new io.javalin.json.JavalinGson()))) {
            config.accessToken = "miliastra-test";
            config.loopbackOnly = true;
            new GmHandler(directory).applyRoutes(app);
            app.start(0);
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port() + "/gm/api/miliastra";
            var empty =
                    ByteBuffer.allocate(24)
                            .putInt(20)
                            .putInt(1)
                            .putInt(0x326)
                            .putInt(3)
                            .putInt(0)
                            .putInt(0x679)
                            .array();
            assertEquals(
                    403,
                    client
                            .send(
                                    HttpRequest.newBuilder(URI.create(base)).GET().build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            var upload =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "?name=empty.gia"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(empty))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, upload.statusCode(), upload.body());
            String id =
                    JsonParser.parseString(upload.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString();
            var listing =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "?q=empty"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listing.statusCode());
            assertEquals("no-store", listing.headers().firstValue("Cache-Control").orElse(""));
            assertTrue(
                    JsonParser.parseString(listing.body()).getAsJsonObject().get("total").getAsInt() >= 1);
            var export =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + id + "/download"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, export.statusCode());
            assertArrayEquals(empty, export.body());
            for (var request :
                    new HttpRequest[] {
                        HttpRequest.newBuilder(URI.create(base + "?name=unauthorized.gia"))
                                .POST(HttpRequest.BodyPublishers.ofByteArray(empty))
                                .build(),
                        HttpRequest.newBuilder(URI.create(base + "/" + id + "/download")).build()
                    }) {
                assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            // Read the actual request stream: importing a >1 MiB opaque field must bypass
            // Javalin's default small JSON-body limit without increasing every GM route's limit.
            var payload = new java.io.ByteArrayOutputStream();
            var protobuf = com.google.protobuf.CodedOutputStream.newInstance(payload);
            protobuf.writeByteArray(99, new byte[2 * 1024 * 1024]);
            protobuf.flush();
            byte[] opaque = payload.toByteArray();
            byte[] large =
                    ByteBuffer.allocate(opaque.length + 24)
                            .putInt(opaque.length + 20)
                            .putInt(1)
                            .putInt(0x326)
                            .putInt(3)
                            .putInt(opaque.length)
                            .put(opaque)
                            .putInt(0x679)
                            .array();
            var largeUpload =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "?name=large.gia"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(large))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(201, largeUpload.statusCode(), largeUpload.body());
            String largeId =
                    JsonParser.parseString(largeUpload.body())
                            .getAsJsonObject()
                            .getAsJsonObject("asset")
                            .get("id")
                            .getAsString();
            var largeExport =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + largeId + "/download"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertArrayEquals(large, largeExport.body());
            var invalid =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "?name=broken.gia"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[24]))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            var missing =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/" + "0".repeat(64) + "/download"))
                                    .header("Authorization", "Bearer miliastra-test")
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
        } finally {
            config.accessToken = token;
            config.loopbackOnly = loopback;
        }
    }
}
