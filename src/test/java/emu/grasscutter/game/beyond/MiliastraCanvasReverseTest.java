package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCanvasReverseTest {
    @TempDir Path directory;
    static final int GUID = 0x40000011;

    static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    static byte[] bytes(JsonObject value) {
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    static JsonObject node(String id, String type, String data) {
        var n = json("{\"position\":{\"x\":10,\"y\":20}}");
        n.addProperty("id", id);
        n.addProperty("type", type);
        n.add("data", json(data));
        return n;
    }

    static JsonObject canvas(String environment, JsonObject... nodes) {
        var c = json("{\"schemaVersion\":2,\"name\":\"反向转换验证\",\"edges\":[],\"comments\":[]}");
        c.addProperty("environment", environment);
        var rows = new JsonArray();
        for (var n : nodes) rows.add(n);
        c.add("nodes", rows);
        return c;
    }

    static JsonObject reversed(byte[] gia) {
        return MiliastraGraph.readJson(MiliastraCanvasReverse.decode(gia)).getAsJsonObject();
    }

    static JsonObject typed(byte[] gia) {
        return MiliastraGraph.readJson(MiliastraNodeJson.decode(gia), 2000000).getAsJsonObject();
    }

    static JsonObject nativeGraph(JsonObject typed) {
        return typed
                .getAsJsonObject("data")
                .getAsJsonObject("graph")
                .getAsJsonObject("graph")
                .getAsJsonObject("inner")
                .getAsJsonObject("graph");
    }

    static JsonObject nativeNode(JsonObject typed, int index) {
        return nativeGraph(typed).getAsJsonArray("nodes").get(index).getAsJsonObject();
    }

    static JsonObject canvasNode(JsonObject c, int index) {
        return c.getAsJsonArray("nodes").get(index).getAsJsonObject();
    }

    static JsonObject edge(String id, String from, String out, String to, String input) {
        var e = new JsonObject();
        e.addProperty("id", id);
        var a = new JsonObject();
        a.addProperty("nodeId", from);
        a.addProperty("portId", out);
        e.add("source", a);
        var b = new JsonObject();
        b.addProperty("nodeId", to);
        b.addProperty("portId", input);
        e.add("target", b);
        return e;
    }

    static String unknown() {
        return Base64.getEncoder()
                .encodeToString(
                        UnknownFieldSet.newBuilder()
                                .addField(777, UnknownFieldSet.Field.newBuilder().addVarint(99).build())
                                .build()
                                .toByteArray());
    }

    static MiliastraNodeProtocol.Root root(byte[] gia) throws Exception {
        return MiliastraNodeProtocol.Root.parseFrom(Arrays.copyOfRange(gia, 20, gia.length - 4));
    }

    static JsonObject firstOverride(JsonObject c) {
        return canvasNode(c, 0).getAsJsonObject("data").getAsJsonObject("overrides");
    }

    @Test
    void realSampleProducesAllNodesAndConnectionsAndRestoresExactBytes() throws Exception {
        byte[] source;
        try (var input = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
            source = input.readAllBytes();
        }
        var c = reversed(source);
        assertEquals(3, c.getAsJsonArray("nodes").size());
        assertEquals(3, c.getAsJsonArray("edges").size());
        assertEquals("event.graphVariableChanged", canvasNode(c, 0).get("type").getAsString());
        assertEquals("action.printString", canvasNode(c, 1).get("type").getAsString());
        assertArrayEquals(source, MiliastraCanvas.encode(bytes(c), 1024346482));
        assertTrue(c.has("giaSource"));
    }

    @Test
    void concreteGenericTypesAndConnectedFallbackConstantsStayEditable() throws Exception {
        var c =
                canvas(
                        "server",
                        node("a", "math.add", "{\"giaTypes\":{\"a\":\"Int\"},\"overrides\":{\"a\":1,\"b\":2}}"),
                        node(
                                "b", "math.add", "{\"giaTypes\":{\"a\":\"Int\"},\"overrides\":{\"a\":8,\"b\":3}}"));
        c.getAsJsonArray("edges").add(edge("data", "a", "result", "b", "a"));
        byte[] source = MiliastraCanvas.encode(bytes(c), GUID);
        var reversed = reversed(source);
        assertEquals(1, reversed.getAsJsonArray("edges").size());
        assertEquals(
                "Int",
                canvasNode(reversed, 0)
                        .getAsJsonObject("data")
                        .getAsJsonObject("giaTypes")
                        .get("a")
                        .getAsString());
        firstOverride(reversed).addProperty("b", 7);
        var updated =
                root(MiliastraCanvas.encode(bytes(reversed), GUID))
                        .getGraph()
                        .getGraph()
                        .getInner()
                        .getGraph();
        assertEquals(200, updated.getNodes(0).getConcreteId().getNodeId());
        assertTrue(
                updated.getNodes(0).getPinsList().stream()
                        .anyMatch(
                                p ->
                                        p.getI1().getIndex() == 1
                                                && p.getValue().getBConcreteValue().getValue().getBInt().getVal() == 7));
        assertTrue(
                updated.getNodes(1).getPinsList().stream()
                        .anyMatch(
                                p ->
                                        p.getI1().getKindValue() == 3
                                                && p.getI1().getIndex() == 0
                                                && p.getValue().getBConcreteValue().getValue().getBInt().getVal() == 8
                                                && p.getConnectsCount() == 1));
    }

    @Test
    void dictionaryAndZeroConstantsHaveRealCanvasValues() {
        var c =
                canvas(
                        "server",
                        node(
                                "dict",
                                "action.dict.setOrAdd",
                                "{\"giaTypes\":{\"dict\":\"D<Str,Int>\"},\"overrides\":{\"dict\":[[\"zero\",0]],\"key\":\"next\",\"value\":4}}"));
        var reversed = reversed(MiliastraCanvas.encode(bytes(c), GUID));
        assertEquals(json("{\"x\":[[\"zero\",0]]}").get("x"), firstOverride(reversed).get("dict"));
        firstOverride(reversed).addProperty("value", 0);
        var reprojected = reversed(MiliastraCanvas.encode(bytes(reversed), GUID));
        assertEquals(0, firstOverride(reprojected).get("value").getAsInt());
    }

    @Test
    void clientNumericCodesResolveToClientTypes() throws Exception {
        var c =
                canvas(
                        "client:role-skill",
                        node("sin", "client.math.sin", "{\"overrides\":{\"radian\":1.25}}"));
        var source = MiliastraCanvas.encode(bytes(c), GUID);
        var reversed = reversed(source);
        assertEquals("client.math.sin", canvasNode(reversed, 0).get("type").getAsString());
        firstOverride(reversed).addProperty("radian", 2.5);
        var result =
                root(MiliastraCanvas.encode(bytes(reversed), GUID))
                        .getGraph()
                        .getGraph()
                        .getInner()
                        .getGraph()
                        .getNodes(0);
        assertEquals(35, result.getConcreteId().getNodeId());
        assertTrue(
                result.getPinsList().stream()
                        .anyMatch(p -> p.getType() == 7 && p.getValue().getBFloat().getVal() == 2.5f));
    }

    @Test
    void dynamicStringAndIntegerBranchLabelsAndFlowEdgesRoundTrip() {
        for (String literal : List.of("\"red\"", "3")) {
            var c =
                    canvas(
                            "server",
                            node(
                                    "branch",
                                    "flow.branch.multi",
                                    "{\"overrides\":{\"controlExpr\":"
                                            + literal
                                            + "},\"branchFlowOutLabels\":["
                                            + (literal.equals("3") ? "\"3\"" : literal)
                                            + "]}"),
                            node("print", "action.printString", "{}"));
            c.getAsJsonArray("edges").add(edge("flow", "branch", "branch1", "print", "flowIn"));
            byte[] source = MiliastraCanvas.encode(bytes(c), GUID);
            var result = reversed(source);
            assertEquals(
                    1,
                    canvasNode(result, 0)
                            .getAsJsonObject("data")
                            .getAsJsonArray("branchFlowOutLabels")
                            .size());
            assertEquals(
                    "branch1",
                    result
                            .getAsJsonArray("edges")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("source")
                            .get("portId")
                            .getAsString());
            assertArrayEquals(source, MiliastraCanvas.encode(bytes(result), GUID));
        }
    }

    @Test
    void editedCoordinatesRetainSparseIndicesMetadataVariablesAndUnknownFields() {
        var c =
                canvas(
                        "server", node("print", "action.printString", "{\"overrides\":{\"text\":\"before\"}}"));
        var t = typed(MiliastraCanvas.encode(bytes(c), GUID));
        var n = nativeNode(t, 0);
        n.addProperty("nodeIndex", 77);
        n.addProperty("$unknown", unknown());
        var nativeGraph = nativeGraph(t);
        nativeGraph.addProperty("$unknown", unknown());
        var variables = new JsonArray();
        variables.add(
                json("{\"name\":\"retained\",\"type\":3,\"values\":{\"class\":2,\"bInt\":{\"val\":19}}}"));
        nativeGraph.add("graphValues", variables);
        t.getAsJsonObject("data").addProperty("filePath", "original/path");
        t.getAsJsonObject("data").addProperty("$unknown", unknown());
        byte[] source = MiliastraNodeJson.encode(bytes(t));
        var reversed = reversed(source);
        canvasNode(reversed, 0).getAsJsonObject("position").addProperty("x", 123);
        var updated = typed(MiliastraCanvas.encode(bytes(reversed), GUID));
        assertEquals(77, nativeNode(updated, 0).get("nodeIndex").getAsInt());
        assertEquals(123, nativeNode(updated, 0).get("x").getAsInt());
        assertEquals(
                t.getAsJsonObject("data").get("$unknown"), updated.getAsJsonObject("data").get("$unknown"));
        assertEquals(n.get("$unknown"), nativeNode(updated, 0).get("$unknown"));
        assertEquals(nativeGraph.get("graphValues"), nativeGraph(updated).get("graphValues"));
        assertEquals("original/path", updated.getAsJsonObject("data").get("filePath").getAsString());
    }

    @Test
    void addingAndReorderingNodesPreservesExistingIndicesAndRewritesConnections() throws Exception {
        var c = canvas("server", node("print", "action.printString", "{}"));
        var t = typed(MiliastraCanvas.encode(bytes(c), GUID));
        nativeNode(t, 0).addProperty("nodeIndex", 92);
        var reversed = reversed(MiliastraNodeJson.encode(bytes(t)));
        var old = canvasNode(reversed, 0);
        var nodes = new JsonArray();
        nodes.add(node("new", "action.printString", "{\"overrides\":{\"text\":\"new\"}}"));
        nodes.add(old);
        reversed.add("nodes", nodes);
        reversed.getAsJsonArray("edges").add(edge("flow", "new", "flowOut", "gia-node-92", "flowIn"));
        var result =
                root(MiliastraCanvas.encode(bytes(reversed), GUID))
                        .getGraph()
                        .getGraph()
                        .getInner()
                        .getGraph();
        assertEquals(1, result.getNodes(0).getNodeIndex());
        assertEquals(92, result.getNodes(1).getNodeIndex());
        assertEquals(
                92,
                result.getNodes(0).getPinsList().stream()
                        .filter(p -> p.getI1().getKindValue() == 2)
                        .findFirst()
                        .orElseThrow()
                        .getConnects(0)
                        .getId());
    }

    @Test
    void unknownPinAndNestedConstantFieldsRemainAfterConstantEdits() {
        var c =
                canvas(
                        "server", node("print", "action.printString", "{\"overrides\":{\"text\":\"before\"}}"));
        var t = typed(MiliastraCanvas.encode(bytes(c), GUID));
        var pin = nativeNode(t, 0).getAsJsonArray("pins").get(0).getAsJsonObject();
        pin.addProperty("$unknown", unknown());
        pin.getAsJsonObject("value").addProperty("$unknown", unknown());
        pin.getAsJsonObject("value").getAsJsonObject("bString").addProperty("$unknown", unknown());
        var reversed = reversed(MiliastraNodeJson.encode(bytes(t)));
        firstOverride(reversed).addProperty("text", "after");
        var result =
                nativeNode(typed(MiliastraCanvas.encode(bytes(reversed), GUID)), 0)
                        .getAsJsonArray("pins")
                        .get(0)
                        .getAsJsonObject();
        assertEquals(pin.get("$unknown"), result.get("$unknown"));
        assertEquals(
                pin.getAsJsonObject("value").get("$unknown"),
                result.getAsJsonObject("value").get("$unknown"));
        assertEquals(
                pin.getAsJsonObject("value").getAsJsonObject("bString").get("$unknown"),
                result.getAsJsonObject("value").getAsJsonObject("bString").get("$unknown"));
        assertEquals(
                "after",
                result.getAsJsonObject("value").getAsJsonObject("bString").get("val").getAsString());
    }

    @Test
    void booleanVectorAndListConstantsRemainEditableIncludingNestedUnknowns() {
        for (String expression : List.of("Bol", "Vec", "L<Int>")) {
            String constant =
                    expression.equals("Bol") ? "false" : expression.equals("Vec") ? "[0,1.25,-2]" : "[0,2]";
            var c =
                    canvas(
                            "server",
                            node(
                                    "set",
                                    "action.setLocalVariable",
                                    "{\"giaTypes\":{\"value\":\""
                                            + expression
                                            + "\"},\"overrides\":{\"value\":"
                                            + constant
                                            + "}}"));
            var original = typed(MiliastraCanvas.encode(bytes(c), GUID));
            if (expression.equals("L<Int>")) {
                var value =
                        nativeNode(original, 0)
                                .getAsJsonArray("pins")
                                .get(1)
                                .getAsJsonObject()
                                .getAsJsonObject("value");
                var items =
                        value
                                .getAsJsonObject("bConcreteValue")
                                .getAsJsonObject("value")
                                .getAsJsonObject("bArray")
                                .getAsJsonArray("entries");
                items.get(0).getAsJsonObject().addProperty("$unknown", unknown());
                items.get(0).getAsJsonObject().getAsJsonObject("bInt").addProperty("$unknown", unknown());
            }
            byte[] source = MiliastraNodeJson.encode(bytes(original));
            var c2 = reversed(source);
            assertEquals(JsonParser.parseString(constant), firstOverride(c2).get("value"));
            assertArrayEquals(source, MiliastraCanvas.encode(bytes(c2), GUID));
            firstOverride(c2)
                    .add(
                            "value",
                            JsonParser.parseString(
                                    expression.equals("Bol")
                                            ? "true"
                                            : expression.equals("Vec") ? "[3,0,4]" : "[9,2,3]"));
            byte[] edited = MiliastraCanvas.encode(bytes(c2), GUID);
            assertEquals(firstOverride(c2).get("value"), firstOverride(reversed(edited)).get("value"));
            if (expression.equals("L<Int>")) {
                var value =
                        nativeNode(typed(edited), 0)
                                .getAsJsonArray("pins")
                                .get(1)
                                .getAsJsonObject()
                                .getAsJsonObject("value");
                var items =
                        value
                                .getAsJsonObject("bConcreteValue")
                                .getAsJsonObject("value")
                                .getAsJsonObject("bArray")
                                .getAsJsonArray("entries");
                assertEquals(unknown(), items.get(0).getAsJsonObject().get("$unknown").getAsString());
                assertEquals(
                        unknown(),
                        items.get(0).getAsJsonObject().getAsJsonObject("bInt").get("$unknown").getAsString());
                assertFalse(items.get(2).getAsJsonObject().has("$unknown"));
            }
        }
    }

    @Test
    void changingNodeTypeClearsObsoletePinsAndRejectsNestedUnknownParameters() {
        var t =
                typed(
                        MiliastraCanvas.encode(
                                bytes(
                                        canvas(
                                                "server",
                                                node("print", "action.printString", "{\"overrides\":{\"text\":\"old\"}}"))),
                                GUID));
        var c = reversed(MiliastraNodeJson.encode(bytes(t)));
        canvasNode(c, 0).addProperty("type", "action.finishLevel");
        canvasNode(c, 0).add("data", new JsonObject());
        var updated = nativeNode(typed(MiliastraCanvas.encode(bytes(c), GUID)), 0);
        assertFalse(updated.toString().contains("old"));
        nativeNode(t, 0).getAsJsonObject("genericId").addProperty("$unknown", unknown());
        var c2 = reversed(MiliastraNodeJson.encode(bytes(t)));
        canvasNode(c2, 0).addProperty("type", "action.finishLevel");
        canvasNode(c2, 0).add("data", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(c2), GUID));
    }

    @Test
    void unnamedNativeGraphGetsCanvasDisplayNameWithoutChangingNativeNames() {
        var t =
                typed(
                        MiliastraCanvas.encode(
                                bytes(canvas("server", node("print", "action.printString", "{}"))), GUID));
        t.getAsJsonObject("data").getAsJsonObject("graph").remove("name");
        nativeGraph(t).remove("name");
        byte[] source = MiliastraNodeJson.encode(bytes(t));
        var c = reversed(source);
        assertEquals("未命名节点图", c.get("name").getAsString());
        assertArrayEquals(source, MiliastraCanvas.encode(bytes(c), GUID));
        canvasNode(c, 0).getAsJsonObject("position").addProperty("x", 11);
        var updated = typed(MiliastraCanvas.encode(bytes(c), GUID));
        assertFalse(nativeGraph(updated).has("name"));
        assertFalse(updated.getAsJsonObject("data").getAsJsonObject("graph").has("name"));
    }

    @Test
    void coordinateEditRetainsPropertyUnknownsAndDistinctNativeNames() {
        var t =
                typed(
                        MiliastraCanvas.encode(
                                bytes(canvas("server", node("print", "action.printString", "{}"))), GUID));
        nativeNode(t, 0).getAsJsonObject("genericId").addProperty("$unknown", unknown());
        nativeNode(t, 0).getAsJsonObject("concreteId").addProperty("$unknown", unknown());
        nativeNode(t, 0).getAsJsonObject("genericId").remove("class");
        nativeNode(t, 0).getAsJsonObject("genericId").addProperty("kind", 987654);
        nativeNode(t, 0).getAsJsonObject("concreteId").remove("type");
        nativeGraph(t).addProperty("name", "inner graph name");
        var c = reversed(MiliastraNodeJson.encode(bytes(t)));
        canvasNode(c, 0).getAsJsonObject("position").addProperty("x", 500);
        var updated = typed(MiliastraCanvas.encode(bytes(c), GUID));
        assertEquals("inner graph name", nativeGraph(updated).get("name").getAsString());
        assertEquals(nativeNode(t, 0).get("genericId"), nativeNode(updated, 0).get("genericId"));
        assertEquals(nativeNode(t, 0).get("concreteId"), nativeNode(updated, 0).get("concreteId"));
    }

    @Test
    void mirroredDataAndFlowConnectionsAreDeduplicatedAndDeletedTogether() {
        for (boolean flow : List.of(true, false)) {
            var c =
                    canvas(
                            "server",
                            node("a", flow ? "action.printString" : "math.add", "{}"),
                            node("b", flow ? "action.printString" : "math.add", "{}"));
            c.getAsJsonArray("edges")
                    .add(edge("e", "a", flow ? "flowOut" : "result", "b", flow ? "flowIn" : "a"));
            var t = typed(MiliastraCanvas.encode(bytes(c), GUID));
            var mirror =
                    json(
                            "{\"i1\":{\"kind\":"
                                    + (flow ? 1 : 4)
                                    + "},\"i2\":{\"kind\":"
                                    + (flow ? 1 : 4)
                                    + "},\"connects\":[{\"id\":"
                                    + (flow ? 1 : 2)
                                    + ",\"connect\":{\"kind\":"
                                    + (flow ? 2 : 3)
                                    + "},\"connect2\":{\"kind\":"
                                    + (flow ? 2 : 3)
                                    + "}}]}");
            mirror.addProperty("$unknown", unknown());
            var nativePins = nativeNode(t, flow ? 1 : 0).getAsJsonArray("pins");
            boolean reused = false;
            for (var existing : nativePins) {
                var pin = existing.getAsJsonObject();
                if (!flow && pin.getAsJsonObject("i1").get("kind").getAsInt() == 4) {
                    pin.add("connects", mirror.get("connects"));
                    pin.add("$unknown", mirror.get("$unknown"));
                    reused = true;
                }
            }
            if (!reused) nativePins.add(mirror);
            byte[] source = MiliastraNodeJson.encode(bytes(t));
            var reversed = reversed(source);
            assertEquals(1, reversed.getAsJsonArray("edges").size());
            assertArrayEquals(source, MiliastraCanvas.encode(bytes(reversed), GUID));
            reversed.add("edges", new JsonArray());
            var updated = typed(MiliastraCanvas.encode(bytes(reversed), GUID));
            for (var n : nativeGraph(updated).getAsJsonArray("nodes"))
                for (var p : n.getAsJsonObject().getAsJsonArray("pins"))
                    assertFalse(p.getAsJsonObject().has("connects"));
            assertEquals(
                    unknown(),
                    java.util.stream.StreamSupport.stream(
                                    nativeNode(updated, flow ? 1 : 0).getAsJsonArray("pins").spliterator(), false)
                            .map(JsonElement::getAsJsonObject)
                            .filter(p -> p.has("$unknown"))
                            .findFirst()
                            .orElseThrow()
                            .get("$unknown")
                            .getAsString());
        }
    }

    @Test
    void multipleAttachedCommentsAndFreeCommentsDoNotDisappearOrDuplicate() {
        var c = canvas("server", node("print", "action.printString", "{}"));
        c.getAsJsonArray("comments").add(json("{\"nodeId\":\"print\",\"text\":\"original attached\"}"));
        c.getAsJsonArray("comments").add(json("{\"text\":\"free\",\"position\":{\"x\":80,\"y\":90}}"));
        var reversed = reversed(MiliastraCanvas.encode(bytes(c), GUID));
        String id = canvasNode(reversed, 0).get("id").getAsString();
        var comments = new JsonArray();
        comments.add(reversed.getAsJsonArray("comments").get(0));
        comments.add(json("{\"nodeId\":\"" + id + "\",\"text\":\"second attached\"}"));
        comments.add(reversed.getAsJsonArray("comments").get(1));
        reversed.add("comments", comments);
        var result = nativeGraph(typed(MiliastraCanvas.encode(bytes(reversed), GUID)));
        assertEquals(2, result.getAsJsonArray("comments").size());
        Set<String> contents = new HashSet<>();
        for (var comment : result.getAsJsonArray("comments"))
            contents.add(comment.getAsJsonObject().get("content").getAsString());
        assertEquals(Set.of("free", "second attached"), contents);
    }

    @Test
    void serverGraphVariantsUseVerifiedWhichAndGraphTypeFields() throws Exception {
        for (String environment : List.of("server:status", "server:class", "server:item")) {
            var c = canvas(environment, node("print", "action.printString", "{}"));
            byte[] source = MiliastraCanvas.encode(bytes(c), GUID);
            var reversed = reversed(source);
            assertEquals(environment, reversed.get("environment").getAsString());
            canvasNode(reversed, 0).getAsJsonObject("position").addProperty("y", 500);
            var result = root(MiliastraCanvas.encode(bytes(reversed), GUID)).getGraph();
            assertEquals(
                    environment.equals("server:status") ? 22 : environment.equals("server:class") ? 23 : 46,
                    result.getWhichValue());
            assertEquals(
                    environment.equals("server:status")
                            ? 20003
                            : environment.equals("server:class") ? 20004 : 20005,
                    result.getGraph().getInner().getGraph().getId().getTypeValue());
        }
    }

    @Test
    void backingGuidAndEnvironmentCannotBeChangedAccidentally() {
        var c =
                reversed(
                        MiliastraCanvas.encode(
                                bytes(canvas("server", node("print", "action.printString", "{}"))), GUID));
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(c), GUID + 1));
        c.addProperty("environment", "client:role-skill");
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(c), GUID));
        c.getAsJsonObject("giaSource").addProperty("data", "corrupted");
        assertThrows(IllegalArgumentException.class, () -> MiliastraGraph.inspect(bytes(c)));
    }

    @Test
    void unsupportedTemplatesInvalidIndicesAndDirectionAreDiagnosed() {
        var t =
                typed(
                        MiliastraCanvas.encode(
                                bytes(canvas("server", node("print", "action.printString", "{}"))), GUID));
        var originalId = nativeNode(t, 0).getAsJsonObject("genericId").get("nodeId").deepCopy();
        nativeNode(t, 0).getAsJsonObject("genericId").addProperty("nodeId", 99999999);
        assertThrows(
                IllegalArgumentException.class, () -> reversed(MiliastraNodeJson.encode(bytes(t))));
        nativeNode(t, 0).getAsJsonObject("genericId").add("nodeId", originalId);
        nativeNode(t, 0)
                .getAsJsonArray("pins")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("i1")
                .addProperty("index", 500);
        assertThrows(
                IllegalArgumentException.class, () -> reversed(MiliastraNodeJson.encode(bytes(t))));
    }

    @Test
    void storeDeduplicationRestartAndTypedSourcesPreserveTheOriginal() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] gia =
                MiliastraCanvas.encode(
                        bytes(canvas("server", node("print", "action.printString", "{}"))), GUID);
        var original = store.importFile("sample.gia", gia).asset();
        var canvas = store.convertCanvasSource(original.id());
        assertEquals("GRAPH_JSON", canvas.asset().type());
        assertTrue(store.convertCanvasSource(original.id()).duplicate());
        assertEquals(
                canvas.asset().id(),
                new MiliastraAssetStore(directory).convertCanvasSource(original.id()).asset().id());
        assertEquals(original.id(), store.convertNode(canvas.asset().id()).asset().id());
        var typed = store.importFile("typed.gia.json", MiliastraNodeJson.decode(gia)).asset();
        assertEquals(canvas.asset().id(), store.convertCanvasSource(typed.id()).asset().id());
        assertArrayEquals(gia, store.download(original.id()).bytes());
    }
}
