package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraNodeJsonTest {
    @TempDir Path directory;

    private byte[] fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
            return input.readAllBytes();
        }
    }

    private static JsonObject document(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] json(JsonObject document) {
        return document.toString().getBytes(StandardCharsets.UTF_8);
    }

    private JsonObject sample() throws Exception {
        return document(MiliastraNodeJson.decode(fixture()));
    }

    private static JsonObject unit(JsonObject document) {
        return document.getAsJsonObject("data").getAsJsonObject("graph");
    }

    private static JsonObject graph(JsonObject document) {
        return unit(document)
                .getAsJsonObject("graph")
                .getAsJsonObject("inner")
                .getAsJsonObject("graph");
    }

    private static Message message(byte[] bytes) throws Exception {
        return DynamicMessage.parseFrom(
                MiliastraNodeProtocol.Root.getDescriptor(),
                Arrays.copyOfRange(bytes, 20, bytes.length - 4));
    }

    private static Message child(Message message, String name) {
        return (Message) message.getField(message.getDescriptorForType().findFieldByName(name));
    }

    @Test
    void realFixtureRestoresExactlyIncludingOriginalOrdering() throws Exception {
        byte[] source = fixture();
        var document = sample();
        assertEquals("lunagc.gia.v1", document.get("format").getAsString());
        assertEquals(3, graph(document).getAsJsonArray("nodes").size());
        assertArrayEquals(source, MiliastraNodeJson.encode(json(document)));
        assertEquals(3, MiliastraNodeJson.inspect(json(document)).nodes());
        assertEquals(3, MiliastraNodeJson.inspect(json(document)).edges());
        document.remove("source");
        assertEquals(message(source), message(MiliastraNodeJson.encode(json(document))));
    }

    @Test
    void nestedUnknownFieldsSurviveEditingAndOriginalSourceDoesNotOverrideEdits() throws Exception {
        var doc = sample();
        byte[] unknown =
                MiliastraFileTest.message(
                        out -> {
                            out.writeUInt64(99, Long.MIN_VALUE);
                            out.writeByteArray(101, new byte[] {(byte) 0xFF, 0, 1});
                        });
        String encoded = Base64.getEncoder().encodeToString(unknown);
        doc.getAsJsonObject("data").addProperty("$unknown", encoded);
        graph(doc).addProperty("$unknown", encoded);
        graph(doc).getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("$unknown", encoded);
        byte[] withUnknown = MiliastraNodeJson.encode(json(doc));
        var edited = document(MiliastraNodeJson.decode(withUnknown));
        unit(edited).addProperty("name", "改名后的节点图");
        graph(edited).getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("x", 123.5);
        byte[] result = MiliastraNodeJson.encode(json(edited));
        assertFalse(Arrays.equals(withUnknown, result));
        var before = message(withUnknown);
        var after = message(result);
        assertEquals(before.getUnknownFields(), after.getUnknownFields());
        var beforeGraph = child(child(child(child(before, "graph"), "graph"), "inner"), "graph");
        var afterGraph = child(child(child(child(after, "graph"), "graph"), "inner"), "graph");
        assertEquals(beforeGraph.getUnknownFields(), afterGraph.getUnknownFields());
        var nodes = beforeGraph.getDescriptorForType().findFieldByName("nodes");
        assertEquals(
                ((Message) beforeGraph.getRepeatedField(nodes, 0)).getUnknownFields(),
                ((Message) afterGraph.getRepeatedField(nodes, 0)).getUnknownFields());
        assertEquals("改名后的节点图", MiliastraFile.inspect(result, "edited.gia").name());
    }

    @Test
    void futureHeaderAndNonCanonicalUnknownVarintRoundTripWithoutByteChanges() throws Exception {
        byte[] source = fixture();
        byte[] payload = Arrays.copyOfRange(source, 20, source.length - 4);
        byte[] extra = MiliastraFileTest.message(out -> out.writeTag(99, 0));
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.write(extra);
        bytes.write(0x81);
        bytes.write(0);
        bytes.write(payload);
        byte[] original = MiliastraFileTest.container(3, bytes.toByteArray());
        ByteBuffer.wrap(original).putInt(4, -1);
        var decoded = MiliastraNodeJson.decode(original);
        assertEquals(4294967295L, document(decoded).get("version").getAsLong());
        assertArrayEquals(original, MiliastraNodeJson.encode(decoded));
    }

    @Test
    void int64StringsAndUnknownEnumsRetainExactValues() throws Exception {
        var doc = sample();
        graph(doc).getAsJsonObject("id").addProperty("id", "9223372036854775807");
        var node = graph(doc).getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.getAsJsonObject("genericId").addProperty("type", 29999);
        var encoded = MiliastraNodeJson.encode(json(doc));
        var decoded = document(MiliastraNodeJson.decode(encoded));
        assertEquals(
                "9223372036854775807", graph(decoded).getAsJsonObject("id").get("id").getAsString());
        assertEquals(
                29999,
                graph(decoded)
                        .getAsJsonArray("nodes")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("genericId")
                        .get("type")
                        .getAsInt());
        assertArrayEquals(encoded, MiliastraNodeJson.encode(json(decoded)));
    }

    @Test
    void preservesClientPinTypesAndScalarListAndMapConstants() throws Exception {
        var doc = sample();
        unit(doc).addProperty("which", 10);
        unit(doc).getAsJsonObject("id").addProperty("class", 1);
        unit(doc).getAsJsonObject("id").addProperty("type", 3);
        graph(doc).getAsJsonObject("id").addProperty("type", 20001);
        var node = graph(doc).getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.getAsJsonObject("genericId").addProperty("type", 20001);
        var pins =
                JsonParser.parseString(
                                "["
                                        + "{\"i1\":{\"kind\":3},\"type\":5,\"value\":{\"class\":6,\"alreadySetVal\":true,\"itemType\":{\"classBase\":2,\"type_client\":{\"type\":5}},\"bEnum\":{\"val\":1}}},"
                                        + "{\"i1\":{\"kind\":3,\"index\":1},\"type\":7,\"value\":{\"class\":4,\"bFloat\":{\"val\":1.25}}},"
                                        + "{\"i1\":{\"kind\":3,\"index\":2},\"type\":9,\"value\":{\"class\":5,\"bString\":{\"val\":\"输入常量\"}}},"
                                        + "{\"i1\":{\"kind\":3,\"index\":3},\"type\":10,\"value\":{\"class\":10002,\"bArray\":{\"entries\":[{\"class\":5,\"bString\":{\"val\":\"列表值\"}}]}}},"
                                        + "{\"i1\":{\"kind\":3,\"index\":4},\"type\":27,\"value\":{\"class\":10003,\"bMap\":{\"mapPairs\":[{\"class\":10007,\"bMapPair\":{\"key\":{\"class\":5,\"bString\":{\"val\":\"键\"}},\"value\":{\"class\":2,\"bInt\":{\"val\":7}}}}]}}}]")
                        .getAsJsonArray();
        node.add("pins", pins);
        var encoded = MiliastraNodeJson.encode(json(doc));
        var decoded = document(MiliastraNodeJson.decode(encoded));
        assertEquals(
                pins,
                graph(decoded).getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("pins"));
        assertEquals("client:boolean", MiliastraNodeJson.inspect(json(decoded)).environment());
        assertEquals(4, MiliastraNodeProtocol.VarType.Boolean.getNumber());
        assertEquals(5, MiliastraNodeProtocol.ClientVarType.Boolean_.getNumber());
    }

    @Test
    void optionalZeroAndClientExecutionAndCompositePinMetadataAreRetained() throws Exception {
        var doc = sample();
        var node = graph(doc).getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.add("input", JsonParser.parseString("{\"xxx\":6}"));
        var pin = node.getAsJsonArray("pins").get(0).getAsJsonObject();
        pin.add(
                "clientExecNode",
                JsonParser.parseString("{\"kind\":5,\"nodeId\":{\"id\":\"9007199254740993\"}}"));
        pin.addProperty("compositePinIndex", 0);
        var result = document(MiliastraNodeJson.decode(MiliastraNodeJson.encode(json(doc))));
        var decoded = graph(result).getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals(node.getAsJsonObject("input"), decoded.getAsJsonObject("input"));
        assertEquals(
                pin.getAsJsonObject("clientExecNode"),
                decoded.getAsJsonArray("pins").get(0).getAsJsonObject().getAsJsonObject("clientExecNode"));
        assertTrue(decoded.getAsJsonArray("pins").get(0).getAsJsonObject().has("compositePinIndex"));
    }

    @Test
    void rejectsUnknownJsonFieldsKnownFieldInjectionAndConflictingOneofs() throws Exception {
        var unknown = sample();
        graph(unknown).addProperty("invented", true);
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(unknown)));
        var injection = sample();
        byte[] known = MiliastraFileTest.message(out -> out.writeString(2, "injected-name"));
        graph(injection).addProperty("$unknown", Base64.getEncoder().encodeToString(known));
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(injection)));
        var conflicting = sample();
        unit(conflicting).add("compositeDef", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(conflicting)));
    }

    @Test
    void rejectsWrongTypesFractionsOverflowInvalidSourceAndNonFiniteValues() throws Exception {
        for (var bad :
                List.of(
                        new JsonPrimitive(1.2),
                        new JsonPrimitive("4294967296"),
                        new JsonPrimitive(true),
                        JsonNull.INSTANCE)) {
            var doc = sample();
            doc.add("version", bad);
            assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(doc)));
        }
        var wrong = sample();
        graph(wrong).getAsJsonObject("id").addProperty("id", "9223372036854775808");
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(wrong)));
        var badFloat = sample();
        graph(badFloat).getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("x", 1e40);
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(badFloat)));
        var badSource = sample();
        badSource.addProperty("source", "!!!");
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(badSource)));
        var badBase64 = sample();
        graph(badBase64).addProperty("$unknown", "!!!");
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(badBase64)));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraNodeJson.encode(new byte[] {(byte) 0xFF}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraNodeJson.encode(new byte[MiliastraFile.MAX_BYTES + 1]));
        var duplicate =
                new String(json(sample()), StandardCharsets.UTF_8)
                        .replace("\"version\":1", "\"version\":1,\"version\":2");
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraNodeJson.encode(duplicate.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEntityCollectionsEmptyContainersAndMultipleRootGraphs() throws Exception {
        byte[] entity =
                MiliastraGenerator.encode(
                        MiliastraGenerator.model(
                                "{\"blocks\":[{\"templateId\":1}]}".getBytes(StandardCharsets.UTF_8)),
                        1);
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.decode(entity));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraNodeJson.decode(MiliastraFileTest.container(3, new byte[0])));
        var doc = sample();
        unit(doc).getAsJsonObject("id").addProperty("id", 123);
        byte[] another = MiliastraNodeJson.encode(json(doc)), first = fixture();
        var stream = new java.io.ByteArrayOutputStream();
        stream.write(first, 20, first.length - 24);
        stream.write(another, 20, another.length - 24);
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraNodeJson.decode(MiliastraFileTest.container(3, stream.toByteArray())));
    }

    @Test
    void compositeAndStructureDefinitionsAreEditableWithoutDiscardingBodies() throws Exception {
        for (int which : new int[] {12, 29}) {
            var doc = sample();
            var unit = unit(doc);
            unit.remove("graph");
            unit.addProperty("which", which);
            if (which == 12)
                unit.add(
                        "compositeDef",
                        JsonParser.parseString(
                                "{\"inner\":{\"def\":{\"name\":\"复合图\",\"description\":\"描述\",\"inflows\":[{\"name\":\"执行\",\"visible\":true,\"pinIndex\":1}]}}}"));
            else
                unit.add(
                        "structureDef",
                        JsonParser.parseString(
                                "{\"def\":{\"genericField\":{\"id\":\"9007199254740993\",\"structName\":\"结构\",\"classBase\":1,\"vars\":[{\"name\":\"数值\",\"type\":3}]},\"index\":1,\"itemCount\":1}}"));
            var result = MiliastraNodeJson.encode(json(doc));
            var decoded = document(MiliastraNodeJson.decode(result));
            assertEquals(unit, unit(decoded));
            assertEquals(
                    which == 12 ? "composite" : "structure",
                    MiliastraNodeJson.inspect(json(decoded)).environment());
        }
    }

    @Test
    void excessiveEmbeddedUnknownGroupsAndNonFiniteWireFloatsAreRejected() throws Exception {
        var doc = sample();
        byte[] groups =
                MiliastraFileTest.message(
                        out -> {
                            for (int i = 0; i < 65; i++) out.writeTag(99, 3);
                            for (int i = 0; i < 65; i++) out.writeTag(99, 4);
                        });
        graph(doc).addProperty("$unknown", Base64.getEncoder().encodeToString(groups));
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.encode(json(doc)));
        var root = message(fixture()).toBuilder();
        var unit = child(root.build(), "graph").toBuilder();
        var wrapper = child(unit.build(), "graph").toBuilder();
        var inner = child(wrapper.build(), "inner").toBuilder();
        var graph = child(inner.build(), "graph").toBuilder();
        var nodes = graph.getDescriptorForType().findFieldByName("nodes");
        var node = ((Message) graph.getRepeatedField(nodes, 0)).toBuilder();
        node.setField(node.getDescriptorForType().findFieldByName("x"), Float.NaN);
        graph.setRepeatedField(nodes, 0, node.build());
        inner.setField(inner.getDescriptorForType().findFieldByName("graph"), graph.build());
        wrapper.setField(wrapper.getDescriptorForType().findFieldByName("inner"), inner.build());
        unit.setField(unit.getDescriptorForType().findFieldByName("graph"), wrapper.build());
        root.setField(root.getDescriptorForType().findFieldByName("graph"), unit.build());
        byte[] malformed = MiliastraFileTest.container(3, root.build().toByteArray());
        assertThrows(IllegalArgumentException.class, () -> MiliastraNodeJson.decode(malformed));
    }

    @Test
    void storeReopensConvertsAndChecksIdsInsideJsonArchives() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var doc = sample();
        unit(doc).getAsJsonObject("id").addProperty("id", 1500000000);
        var store = new MiliastraAssetStore(directory);
        var archive = store.importFile("edited.gia.json", json(doc));
        assertEquals("GIA_JSON", archive.asset().type());
        assertEquals(3, archive.asset().graph().nodes());
        var reopened = new MiliastraAssetStore(directory);
        var generated =
                reopened.generateModel(
                        "entity.gia",
                        "{\"blocks\":[{\"templateId\":1}]}".getBytes(StandardCharsets.UTF_8),
                        null);
        assertEquals(1500000001, generated.firstAutomaticGuid());
        var converted = reopened.convertNode(archive.asset().id());
        assertArrayEquals(
                MiliastraNodeJson.encode(json(doc)), reopened.download(converted.asset().id()).bytes());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reopened.generateModel(
                                "collision.gia",
                                "{\"blocks\":[{\"templateId\":1,\"guid\":1500000000}]}"
                                        .getBytes(StandardCharsets.UTF_8),
                                null));
        assertEquals(3, reopened.list("", "", 0, 100).total());
        assertThrows(
                IllegalArgumentException.class, () -> reopened.convertNode(generated.asset().id()));
        assertEquals(3, reopened.list("", "", 0, 100).total());
    }
}
