package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MiliastraGraphTest {
    @Test
    void validatesActualEditorGraphAndRetainsUnknownExtensionData() throws Exception {
        var source = fixture();
        source.addProperty("futureExtension", "保留我");
        var preview = inspect(source);
        assertEquals("新建节点图", preview.name());
        assertEquals("server", preview.environment());
        assertEquals(3, preview.nodes());
        assertEquals(3, preview.edges());
        assertEquals(1, preview.warnings().size(), preview.warnings().toString());
        assertEquals("保留我", source.get("futureExtension").getAsString());
    }

    @Test
    void rejectsDuplicateNodesAndDanglingEdges() throws Exception {
        var source = fixture();
        source.getAsJsonArray("nodes").add(source.getAsJsonArray("nodes").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> inspect(source));
        var dangling = fixture();
        dangling
                .getAsJsonArray("edges")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("target")
                .addProperty("nodeId", "missing");
        assertThrows(IllegalArgumentException.class, () -> inspect(dangling));
    }

    @Test
    void rejectsDuplicateEdgesAndInvalidComments() throws Exception {
        var source = fixture();
        source.getAsJsonArray("edges").add(source.getAsJsonArray("edges").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> inspect(source));
        var comments = fixture();
        comments.getAsJsonArray("comments").add(JsonParser.parseString("{\"text\":\"unanchored\"}"));
        assertThrows(IllegalArgumentException.class, () -> inspect(comments));
    }

    @Test
    void warnsForUnknownAndAmbiguousDefinitionsWithoutDroppingNodes() throws Exception {
        var source = fixture();
        source.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("type", "future.node");
        source.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("type", "client.math.tan");
        var preview = inspect(source);
        assertEquals(3, preview.nodes());
        assertTrue(preview.warnings().stream().anyMatch(s -> s.contains("未收录")));
        assertTrue(preview.warnings().stream().anyMatch(s -> s.contains("歧义")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "1e999", "\"1\"", "null"})
    void rejectsInvalidCoordinates(String value) throws Exception {
        String json = fixture().toString().replaceFirst("\"x\":[^,]+", "\"x\":" + value);
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGraph.inspect(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsDuplicateJsonKeysBadUtf8AndExcessiveNesting() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGraph.readJson("{\"id\":1,\"id\":2}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGraph.readJson(new byte[] {'"', (byte) 0xFF, '"'}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraGraph.readJson(
                                ("[".repeat(66) + "0" + "]".repeat(66)).getBytes(StandardCharsets.UTF_8)));
    }

    static JsonObject fixture() throws Exception {
        return JsonParser.parseString(
                        new String(
                                MiliastraFileTest.fixture("editor-sample.server.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    static MiliastraGraph.Preview inspect(JsonObject object) {
        return MiliastraGraph.inspect(object.toString().getBytes(StandardCharsets.UTF_8));
    }
}
