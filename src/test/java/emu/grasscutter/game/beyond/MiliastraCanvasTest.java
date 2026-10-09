package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCanvasTest {
    @TempDir Path directory;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static byte[] bytes(JsonObject value) {
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject node(String id, String type, String data) {
        var node = json("{\"position\":{\"x\":10,\"y\":20}}");
        node.addProperty("id", id);
        node.addProperty("type", type);
        node.add("data", json(data));
        return node;
    }

    private static JsonObject graph(String environment, JsonObject... nodes) {
        var graph = json("{\"schemaVersion\":2,\"name\":\"画布转换验证\",\"edges\":[],\"comments\":[]}");
        graph.addProperty("environment", environment);
        var array = new JsonArray();
        for (var node : nodes) array.add(node);
        graph.add("nodes", array);
        return graph;
    }

    private static void edge(JsonObject graph, String from, String out, String to, String in) {
        var edge = new JsonObject();
        edge.addProperty("id", "edge-" + graph.getAsJsonArray("edges").size());
        var source = new JsonObject();
        source.addProperty("nodeId", from);
        source.addProperty("portId", out);
        var target = new JsonObject();
        target.addProperty("nodeId", to);
        target.addProperty("portId", in);
        edge.add("source", source);
        edge.add("target", target);
        graph.getAsJsonArray("edges").add(edge);
    }

    private static MiliastraNodeProtocol.Root root(byte[] file) throws Exception {
        return MiliastraNodeProtocol.Root.parseFrom(Arrays.copyOfRange(file, 20, file.length - 4));
    }

    private static MiliastraNodeProtocol.NodeGraph compile(JsonObject graph) throws Exception {
        return root(MiliastraCanvas.encode(bytes(graph), 1073741824))
                .getGraph()
                .getGraph()
                .getInner()
                .getGraph();
    }

    private static MiliastraNodeProtocol.NodePin pin(
            MiliastraNodeProtocol.GraphNode node, int kind, int index) {
        return node.getPinsList().stream()
                .filter(p -> p.getI1().getKindValue() == kind && p.getI1().getIndex() == index)
                .findFirst()
                .orElseThrow();
    }

    private byte[] fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/miliastra/editor-sample.server.json")) {
            return stream.readAllBytes();
        }
    }

    @Test
    void realCanvasPreservesBothFlowAndDataConnections() throws Exception {
        var root = root(MiliastraCanvas.encode(fixture(), 1073741824));
        var graph = root.getGraph().getGraph().getInner().getGraph();
        assertEquals(3, graph.getNodesCount());
        assertEquals(9, root.getGraph().getWhichValue());
        assertEquals(351, graph.getNodes(0).getGenericId().getNodeId());
        assertEquals(2, pin(graph.getNodes(0), 2, 0).getConnects(0).getId());
        var input = pin(graph.getNodes(1), 3, 0);
        assertEquals(6, input.getType());
        assertEquals(1, input.getConnects(0).getId());
        assertEquals(4, input.getConnects(0).getConnect().getKindValue());
        assertEquals(2, input.getConnects(0).getConnect().getIndex());
        assertEquals(3, pin(graph.getNodes(1), 2, 0).getConnects(0).getId());
        assertEquals(77, graph.getNodes(2).getGenericId().getNodeId());
        assertFalse(
                graph.getNodes(0).hasConcreteId(), "Unused generic variables retain an unselected type");
    }

    @Test
    void floatingArithmeticHasCorrectConcreteIdAndWrappedValues() throws Exception {
        var graph =
                compile(graph("server", node("a", "math.add", "{\"overrides\":{\"a\":1.25,\"b\":2.5}}")));
        var node = graph.getNodes(0);
        assertEquals(200, node.getGenericId().getNodeId());
        assertEquals(201, node.getConcreteId().getNodeId());
        var input = pin(node, 3, 0);
        assertEquals(5, input.getType());
        assertEquals(10000, input.getValue().getClass_().getNumber());
        assertEquals(1, input.getValue().getBConcreteValue().getIndexOfConcrete());
        assertEquals(1.25f, input.getValue().getBConcreteValue().getValue().getBFloat().getVal());
    }

    @Test
    void explicitIntegerChoicePropagatesAcrossGenericNodes() throws Exception {
        var document =
                graph(
                        "server",
                        node("a", "math.add", "{\"giaTypes\":{\"a\":\"Int\"},\"overrides\":{\"a\":1,\"b\":2}}"),
                        node("b", "math.add", "{}"));
        edge(document, "a", "result", "b", "a");
        var graph = compile(document);
        for (var node : graph.getNodesList()) assertEquals(200, node.getConcreteId().getNodeId());
        var input = pin(graph.getNodes(1), 3, 0);
        assertEquals(3, input.getType());
        assertEquals(1, input.getConnects(0).getId());
        assertEquals(
                0, pin(graph.getNodes(0), 4, 0).getValue().getBConcreteValue().getIndexOfConcrete());
    }

    @Test
    void preferredTypesPropagateToConnectedNodesWithoutExplicitBindings() throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/miliastra/connected-generics.server.json")) {
            bytes = stream.readAllBytes();
        }
        var graph =
                root(MiliastraCanvas.encode(bytes, 1073741824)).getGraph().getGraph().getInner().getGraph();
        assertEquals(201, graph.getNodes(0).getConcreteId().getNodeId());
        assertEquals(371, graph.getNodes(1).getConcreteId().getNodeId());
        assertEquals(5, pin(graph.getNodes(1), 3, 0).getType());
        assertEquals(1, pin(graph.getNodes(1), 3, 0).getConnects(0).getId());
        assertEquals(
                3f,
                pin(graph.getNodes(1), 3, 1)
                        .getValue()
                        .getBConcreteValue()
                        .getValue()
                        .getBFloat()
                        .getVal());
    }

    @Test
    void nodeOrderDoesNotChangeTheInferredConnectedTypes() throws Exception {
        var document =
                graph(
                        "server",
                        node("eq", "math.equals", "{\"overrides\":{\"input2\":3}}"),
                        node("sum", "math.add", "{\"overrides\":{\"a\":1,\"b\":2}}"));
        edge(document, "sum", "result", "eq", "input1");
        var graph = compile(document);
        assertEquals(371, graph.getNodes(0).getConcreteId().getNodeId());
        assertEquals(201, graph.getNodes(1).getConcreteId().getNodeId());
        assertEquals(2, pin(graph.getNodes(0), 3, 0).getConnects(0).getId());
    }

    @Test
    void explicitConstraintsOverrideUiPreferencesAcrossDifferentGenericTemplates() throws Exception {
        var document =
                graph(
                        "server",
                        node("sum", "math.add", "{}"),
                        node(
                                "eq",
                                "math.equals",
                                "{\"giaTypes\":{\"input1\":\"Int\"},\"overrides\":{\"input2\":3}}"));
        edge(document, "sum", "result", "eq", "input1");
        var graph = compile(document);
        assertEquals(200, graph.getNodes(0).getConcreteId().getNodeId());
        assertEquals(370, graph.getNodes(1).getConcreteId().getNodeId());
        assertEquals(3, pin(graph.getNodes(1), 3, 0).getType());
    }

    @Test
    void genuinelyAmbiguousConstantsStillRequireAnExplicitType() {
        var document =
                graph("server", node("eq", "math.equals", "{\"overrides\":{\"input1\":1,\"input2\":2}}"));
        var error = assertThrows(IllegalArgumentException.class, () -> compile(document));
        assertTrue(error.getMessage().contains("giaTypes"));
    }

    @Test
    void conflictingDataTypesAndMultipleInputSourcesAreRejected() {
        var document =
                graph("server", node("a", "math.add", "{}"), node("b", "action.printString", "{}"));
        edge(document, "a", "result", "b", "text");
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(document), 1));
        var duplicate =
                graph(
                        "server",
                        node("a", "math.add", "{}"),
                        node("b", "math.add", "{}"),
                        node("c", "math.add", "{}"));
        edge(duplicate, "a", "result", "c", "a");
        edge(duplicate, "b", "result", "c", "a");
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(duplicate), 1));
    }

    @Test
    void clientSineUsesClientTemplateAndFloatType() throws Exception {
        var result =
                root(
                        MiliastraCanvas.encode(
                                bytes(
                                        graph(
                                                "client:role-skill",
                                                node("s", "client.math.sin", "{\"overrides\":{\"radian\":1.25}}"))),
                                1082130432));
        assertEquals(11, result.getGraph().getWhichValue());
        assertEquals(1, result.getGraph().getId().getClass_().getNumber());
        assertEquals(3, result.getGraph().getId().getTypeValue());
        var node = result.getGraph().getGraph().getInner().getGraph().getNodes(0);
        assertEquals(200094, node.getGenericId().getNodeId());
        assertEquals(20002, node.getGenericId().getTypeValue());
        assertEquals(35, node.getConcreteId().getNodeId());
        var input = pin(node, 3, 1);
        assertEquals(7, input.getType());
        assertEquals(2, input.getValue().getItemType().getClassBaseValue());
        assertEquals(7, input.getValue().getItemType().getTypeClient().getTypeValue());
    }

    @Test
    void booleanFilterUsesItsGraphTypeAndZeroConcreteId() throws Exception {
        var result =
                root(
                        MiliastraCanvas.encode(
                                bytes(
                                        graph(
                                                "client:boolean",
                                                node("end", "flow.graphEndBoolean", "{\"overrides\":{\"result\":true}}"))),
                                1082130432));
        assertEquals(10, result.getGraph().getWhichValue());
        var node = result.getGraph().getGraph().getInner().getGraph().getNodes(0);
        assertEquals(20001, node.getGenericId().getTypeValue());
        assertEquals(200000, node.getGenericId().getNodeId());
        assertTrue(node.hasConcreteId());
        assertEquals(0, node.getConcreteId().getNodeId());
        assertEquals(5, pin(node, 3, 0).getType());
        assertEquals(1, pin(node, 3, 0).getValue().getBEnum().getVal());
    }

    @Test
    void dynamicStringBranchesKeepLabelsAndFlowIndices() throws Exception {
        var document =
                graph(
                        "server",
                        node(
                                "b",
                                "flow.branch.multi",
                                "{\"overrides\":{\"controlExpr\":\"red\"},\"branchFlowOutLabels\":[\"red\",\"blue\"]}"),
                        node("p", "action.printString", "{}"));
        edge(document, "b", "branch2", "p", "flowIn");
        var node = compile(document).getNodes(0);
        assertEquals(4, node.getConcreteId().getNodeId());
        assertEquals(2, pin(node, 2, 2).getConnects(0).getId());
        var labels = pin(node, 3, 1).getValue().getBConcreteValue();
        assertEquals(1, labels.getIndexOfConcrete());
        assertEquals("red", labels.getValue().getBArray().getEntries(0).getBString().getVal());
        assertEquals("blue", labels.getValue().getBArray().getEntries(1).getBString().getVal());
    }

    @Test
    void dynamicIntegerBranchesParseLabelsWithoutTruncation() throws Exception {
        var document =
                graph(
                        "server",
                        node(
                                "b",
                                "flow.branch.multi",
                                "{\"overrides\":{\"controlExpr\":1},\"giaTypes\":{\"controlExpr\":\"Int\"},\"branchFlowOutLabels\":[\"1\",\"2\"]}"));
        var node = compile(document).getNodes(0);
        assertEquals(3, node.getConcreteId().getNodeId());
        assertEquals(
                2,
                pin(node, 3, 1)
                        .getValue()
                        .getBConcreteValue()
                        .getValue()
                        .getBArray()
                        .getEntries(1)
                        .getBInt()
                        .getVal());
        document
                .getAsJsonArray("nodes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("data")
                .getAsJsonArray("branchFlowOutLabels")
                .set(0, new JsonPrimitive("1.5"));
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(document), 1));
    }

    @Test
    void labelsAndMultipleCommentsRetainTextAndPositions() throws Exception {
        var n = node("p", "action.printString", "{}");
        n.addProperty("label", "标题");
        var document = graph("server", n);
        document.add(
                "comments",
                JsonParser.parseString(
                        "[{\"nodeId\":\"p\",\"text\":\"一\",\"position\":{\"x\":1,\"y\":2}},{\"nodeId\":\"p\",\"text\":\"二\"},{\"text\":\"三\",\"position\":{\"x\":3,\"y\":4}}]"));
        var graph = compile(document);
        assertEquals("标题", graph.getNodes(0).getComments().getContent());
        assertEquals(3, graph.getCommentsCount());
        assertEquals(1, graph.getComments(0).getX());
        assertEquals(10, graph.getComments(1).getX());
        assertEquals("二", graph.getComments(1).getContent());
    }

    @Test
    void semanticExtensionsAndInvalidDataAreNotSilentlyDropped() {
        for (String data :
                List.of(
                        "{\"controls\":{\"unknown\":1}}",
                        "{\"unknown\":1}",
                        "{\"overrides\":{\"text\":null}}",
                        "{\"giaTypes\":{\"text\":\"Int\"}}",
                        "{\"giaPortMap\":{\"unknown\":0}}",
                        "{\"giaPortMap\":{\"text\":-1}}")) {
            var graph = graph("server", node("p", "action.printString", data));
            assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(graph), 1));
        }
        var invalid = graph("server", node("p", "action.printString", "{}"));
        invalid.getAsJsonArray("nodes").get(0).getAsJsonObject().add("data", JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(invalid), 1));
        var extra = graph("server");
        extra.addProperty("executionIntervalSeconds", .3);
        assertThrows(IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(extra), 1));
    }

    @Test
    void unmappedOrUnavailableNodesPreserveTheOriginalArchive() throws Exception {
        var store = new MiliastraAssetStore(directory);
        var document = graph("client:role-skill", node("n", "client.query.getEntityType", "{}"));
        var imported = store.importFile("unknown.json", bytes(document));
        assertThrows(IllegalArgumentException.class, () -> store.convertNode(imported.asset().id()));
        assertArrayEquals(bytes(document), store.download(imported.asset().id()).bytes());
        assertEquals(1, store.list("", "", 0, 20).total());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraCanvas.encode(bytes(graph("server", node("n", "client.math.sin", "{}"))), 1));
    }

    @Test
    void positionAndGraphIdBoundsAndFutureIntegerEndMismatchAreRejected() {
        var n = node("n", "action.printString", "{}");
        n.getAsJsonObject("position").addProperty("x", 1e40);
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(graph("server", n)), 1));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCanvas.encode(bytes(graph("server")), 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraCanvas.encode(
                                bytes(
                                        graph(
                                                "client:integer",
                                                node("e", "flow.graphEndInteger", "{\"overrides\":{\"result\":1}}"))),
                                1));
    }

    @Test
    void graphAllocationAvoidsOtherAssetsAndRemainsStableAfterRestart() throws Exception {
        var first = new MiliastraAssetStore(directory.resolve("first"));
        var source = first.importFile("canvas.json", fixture());
        var result = first.convertNode(source.asset().id());
        long original = result.asset().binary().assets().get(0).guid();
        var second = new MiliastraAssetStore(directory.resolve("second"));
        second.generateModel(
                "occupied.gia",
                ("{\"blocks\":[{\"templateId\":10000001,\"guid\":" + original + "}]}")
                        .getBytes(StandardCharsets.UTF_8),
                null);
        var canvas = second.importFile("canvas.json", fixture());
        var converted = second.convertNode(canvas.asset().id());
        assertNotEquals(original, converted.asset().binary().assets().get(0).guid());
        assertTrue(second.convertNode(canvas.asset().id()).duplicate());
        var restarted = new MiliastraAssetStore(directory.resolve("second"));
        assertEquals(converted.asset().id(), restarted.convertNode(canvas.asset().id()).asset().id());
        assertEquals(3, restarted.list("", "", 0, 20).total());
    }

    @Test
    void deterministicOutputRetainsUtf8AndFloatPositions() throws Exception {
        var document =
                graph(
                        "server",
                        node("n", "action.printString", "{\"overrides\":{\"text\":\"中文与 emoji 🌙\"}}"));
        byte[] a = MiliastraCanvas.encode(bytes(document), 123456);
        byte[] b = MiliastraCanvas.encode(bytes(document), 123456);
        assertArrayEquals(a, b);
        assertEquals(
                "中文与 emoji 🌙",
                pin(root(a).getGraph().getGraph().getInner().getGraph().getNodes(0), 3, 0)
                        .getValue()
                        .getBString()
                        .getVal());
    }

    @Test
    void dictionaryNodeKeepsConstantsAndKeyValueTypeWrapper() throws Exception {
        var document =
                graph(
                        "server",
                        node(
                                "d",
                                "action.dict.setOrAdd",
                                "{\"giaTypes\":{\"dict\":\"D<Str,Int>\"},\"overrides\":{\"dict\":[[\"old\",3]],\"key\":\"new\",\"value\":4}}"));
        var node = compile(document).getNodes(0);
        assertEquals(948, node.getGenericId().getNodeId());
        var dict = pin(node, 3, 0);
        assertEquals(27, dict.getType());
        var wrapped = dict.getValue().getBConcreteValue();
        assertEquals(6, wrapped.getStructs().getInner().getWrapper().getMapPair().getKeyValue());
        assertEquals(3, wrapped.getStructs().getInner().getWrapper().getMapPair().getValueValue());
        var pair = wrapped.getValue().getBMap().getMapPairs(0).getBMapPair();
        assertEquals("old", pair.getKey().getBString().getVal());
        assertEquals(3, pair.getValue().getBInt().getVal());
    }
}
