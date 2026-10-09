package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol.*;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCamera.*;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetDefinitions.*;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetEntity.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraGuidTest {
    @TempDir Path directory;
    static final int OLD = 1078000001, NEXT = 1100000000;

    static Asset entity(int id) {
        return Asset.newBuilder()
                .setMeta(AssetMeta.newBuilder().setAssetId(id).setMetaTypeValue(2))
                .setTypeValue(3)
                .setName("entity-" + id)
                .setEntityData(
                        Entity.newBuilder()
                                .setTemplateId(id)
                                .setData(
                                        EntityData.newBuilder()
                                                .setEntityId(id)
                                                .setTemplateIdRef(id)
                                                .setTemplate(TemplateReference.newBuilder().setTemplateId(id))))
                .build();
    }

    static byte[] wrap(byte[] payload) {
        return ByteBuffer.allocate(payload.length + 24)
                .putInt(payload.length + 20)
                .putInt(1)
                .putInt(0x326)
                .putInt(3)
                .putInt(payload.length)
                .put(payload)
                .putInt(0x679)
                .array();
    }

    static byte[] container(Message... units) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        for (var unit : units) out.writeByteArray(1, unit.toByteArray());
        out.flush();
        return wrap(bytes.toByteArray());
    }

    static List<Asset> entities(byte[] bytes) throws Exception {
        var input = CodedInputStream.newInstance(bytes, 20, bytes.length - 24);
        var result = new ArrayList<Asset>();
        int tag;
        while ((tag = input.readTag()) != 0) {
            if (tag == 10 || tag == 18) result.add(Asset.parseFrom(input.readByteArray()));
            else input.skipField(tag);
        }
        return result;
    }

    static GraphUnit graph(boolean client, NodePin... pins) {
        return GraphUnit.newBuilder()
                .setId(GraphUnit.Id.newBuilder().setClass_Value(5).setTypeValue(client ? 3 : 0).setId(OLD))
                .setWhichValue(client ? 11 : 9)
                .setName("GUID graph")
                .setGraph(
                        NodeGraphWrapper.newBuilder()
                                .setInner(
                                        NodeGraphWrapper.InnerWrapper.newBuilder()
                                                .setGraph(
                                                        NodeGraph.newBuilder()
                                                                .setId(
                                                                        NodeGraph.Id.newBuilder()
                                                                                .setClass_Value(10000)
                                                                                .setKindValue(21001)
                                                                                .setId(OLD))
                                                                .addNodes(
                                                                        GraphNode.newBuilder()
                                                                                .setNodeIndex(OLD)
                                                                                .setGenericId(
                                                                                        NodeProperty.newBuilder()
                                                                                                .setClass_Value(10001)
                                                                                                .setKindValue(22000)
                                                                                                .setNodeId(OLD))
                                                                                .addAllPins(Arrays.asList(pins))))))
                .build();
    }

    static VarBase idValue(int id) {
        return VarBase.newBuilder()
                .setClass_Value(1)
                .setBId(IdBaseValue.newBuilder().setVal(id))
                .build();
    }

    static NodeGraph decodedGraph(byte[] bytes) throws Exception {
        return Root.parseFrom(Arrays.copyOfRange(bytes, 20, bytes.length - 4))
                .getGraph()
                .getGraph()
                .getInner()
                .getGraph();
    }

    @Test
    void entitiesKeepTemplateNumbersAndOpaqueData() throws Exception {
        var unknown =
                UnknownFieldSet.newBuilder()
                        .addField(123, UnknownFieldSet.Field.newBuilder().addVarint(OLD).build())
                        .build();
        var original =
                entity(OLD).toBuilder()
                        .mergeUnknownFields(unknown)
                        .addDependentAssets(AssetMeta.newBuilder().setAssetId(OLD + 1))
                        .setEntityData(
                                entity(OLD).getEntityData().toBuilder()
                                        .setData(
                                                entity(OLD).getEntityData().getData().toBuilder()
                                                        .addField7(ByteString.copyFromUtf8("opaque-" + OLD))))
                        .build();
        var result = MiliastraGuid.remap(container(original, entity(OLD + 1)), NEXT);
        var copy = entities(result.bytes()).get(0);
        assertEquals(NEXT, copy.getMeta().getAssetId());
        assertEquals(NEXT, copy.getEntityData().getData().getEntityId());
        assertEquals(NEXT + 1, copy.getDependentAssets(0).getAssetId());
        assertEquals(OLD, copy.getEntityData().getTemplateId());
        assertEquals(OLD, copy.getEntityData().getData().getTemplate().getTemplateId());
        assertEquals(OLD, copy.getEntityData().getData().getTemplateIdRef());
        assertEquals(
                original.getEntityData().getData().getField7List(),
                copy.getEntityData().getData().getField7List());
        assertEquals(unknown, copy.getUnknownFields());
        assertEquals(2, result.guidCount());
        assertEquals(5, result.changedFields());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void cameraDetailsAreDefinitionsWhileParametersStayTheSame() throws Exception {
        var camera =
                Asset.newBuilder()
                        .setMeta(AssetMeta.newBuilder().setAssetId(OLD))
                        .setTypeValue(13)
                        .setCameraConfig(
                                CameraConfig.newBuilder()
                                        .addCameraDetails(
                                                CameraDetail.newBuilder()
                                                        .setCameraId(OLD)
                                                        .setCameraData(
                                                                CameraData.newBuilder()
                                                                        .setName("camera")
                                                                        .setDefaultDistance(12.5f)))
                                        .addCameraDetails(CameraDetail.newBuilder().setCameraId(OLD + 2)))
                        .build();
        var result = MiliastraGuid.remap(container(camera), NEXT);
        var copy = entities(result.bytes()).get(0);
        assertEquals(NEXT, copy.getMeta().getAssetId());
        assertEquals(NEXT, copy.getCameraConfig().getCameraDetails(0).getCameraId());
        assertEquals(NEXT + 1, copy.getCameraConfig().getCameraDetails(1).getCameraId());
        assertEquals(
                camera.getCameraConfig().getCameraDetails(0).getCameraData(),
                copy.getCameraConfig().getCameraDetails(0).getCameraData());
    }

    @Test
    void graphTypedGuidConstantsChangeButIndicesBuiltinsAndIntegersDoNot() throws Exception {
        var pins =
                new NodePin[] {
                    NodePin.newBuilder().setType(2).setValue(idValue(OLD)).build(),
                    NodePin.newBuilder()
                            .setType(3)
                            .setValue(
                                    VarBase.newBuilder()
                                            .setClass_Value(2)
                                            .setBInt(IntBaseValue.newBuilder().setVal(OLD)))
                            .build(),
                    NodePin.newBuilder()
                            .setType(20)
                            .setValue(idValue(OLD))
                            .addConnects(NodeConnection.newBuilder().setId(OLD))
                            .build(),
                    NodePin.newBuilder().setType(0).setValue(idValue(OLD)).build()
                };
        var original = graph(false, pins);
        var bytes = MiliastraGuid.remap(container(original), NEXT).bytes();
        var result = decodedGraph(bytes);
        assertEquals(NEXT, result.getId().getId());
        assertEquals(NEXT, result.getNodes(0).getPins(0).getValue().getBId().getVal());
        assertEquals(OLD, result.getNodes(0).getPins(1).getValue().getBInt().getVal());
        assertEquals(OLD, result.getNodes(0).getPins(2).getValue().getBId().getVal());
        assertEquals(OLD, result.getNodes(0).getPins(2).getConnects(0).getId());
        assertEquals(OLD, result.getNodes(0).getPins(3).getValue().getBId().getVal());
        assertEquals(OLD, result.getNodes(0).getNodeIndex());
        assertEquals(
                original.getGraph().getInner().getGraph().getNodes(0).getGenericId(),
                result.getNodes(0).getGenericId());
    }

    @Test
    void clientGuidAndGuidListUseTheirOwnTypeCodes() throws Exception {
        var pins =
                new NodePin[] {
                    NodePin.newBuilder().setType(14).setValue(idValue(OLD)).build(),
                    NodePin.newBuilder()
                            .setType(15)
                            .setValue(
                                    VarBase.newBuilder()
                                            .setClass_Value(10002)
                                            .setBArray(
                                                    ArrayBaseValue.newBuilder()
                                                            .addEntries(idValue(OLD))
                                                            .addEntries(idValue(OLD + 8))))
                            .build(),
                    NodePin.newBuilder().setType(18).setValue(idValue(OLD)).build()
                };
        var result =
                decodedGraph(MiliastraGuid.remap(container(graph(true, pins)), NEXT).bytes()).getNodes(0);
        assertEquals(NEXT, result.getPins(0).getValue().getBId().getVal());
        assertEquals(NEXT, result.getPins(1).getValue().getBArray().getEntries(0).getBId().getVal());
        assertEquals(OLD + 8, result.getPins(1).getValue().getBArray().getEntries(1).getBId().getVal());
        assertEquals(OLD, result.getPins(2).getValue().getBId().getVal());
    }

    @Test
    void concreteAndDictionaryValuesRetainTypedGuidReferences() throws Exception {
        var pair =
                VarBase.newBuilder()
                        .setClass_Value(10007)
                        .setBMapPair(MapPairBaseValue.newBuilder().setKey(idValue(OLD)).setValue(idValue(OLD)))
                        .build();
        var value =
                VarBase.newBuilder()
                        .setClass_Value(10003)
                        .setItemType(
                                VarBase.ItemType.newBuilder()
                                        .setClassBaseValue(1)
                                        .setTypeServer(
                                                VarBase.ItemType.ServerType.newBuilder()
                                                        .setTypeValue(27)
                                                        .setItems(
                                                                VarBase.ItemType.PairItems.newBuilder()
                                                                        .setKeyValue(2)
                                                                        .setValueValue(20))))
                        .setBMap(MapBaseValue.newBuilder().addMapPairs(pair))
                        .build();
        var concrete =
                VarBase.newBuilder()
                        .setClass_Value(10000)
                        .setBConcreteValue(
                                ConcreteBaseValue.newBuilder().setIndexOfConcrete(OLD).setValue(idValue(OLD)))
                        .build();
        var original =
                graph(
                        false,
                        NodePin.newBuilder().setType(27).setValue(value).build(),
                        NodePin.newBuilder().setType(2).setValue(concrete).build());
        var result = decodedGraph(MiliastraGuid.remap(container(original), NEXT).bytes()).getNodes(0);
        assertEquals(
                NEXT,
                result
                        .getPins(0)
                        .getValue()
                        .getBMap()
                        .getMapPairs(0)
                        .getBMapPair()
                        .getKey()
                        .getBId()
                        .getVal());
        assertEquals(
                OLD,
                result
                        .getPins(0)
                        .getValue()
                        .getBMap()
                        .getMapPairs(0)
                        .getBMapPair()
                        .getValue()
                        .getBId()
                        .getVal());
        assertEquals(
                NEXT, result.getPins(1).getValue().getBConcreteValue().getValue().getBId().getVal());
        assertEquals(OLD, result.getPins(1).getValue().getBConcreteValue().getIndexOfConcrete());
    }

    @Test
    void mixedAssetsAndBareAccessoryReferencesAreUpdatedTogether() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeByteArray(
                1,
                graph(false, NodePin.newBuilder().setType(2).setValue(idValue(OLD + 1)).build())
                        .toByteArray());
        out.writeByteArray(2, entity(OLD + 1).toByteArray());
        out.writeByteArray(
                2,
                Asset.newBuilder()
                        .setMeta(AssetMeta.newBuilder().setAssetId(OLD + 1))
                        .setTypeValue(3)
                        .build()
                        .toByteArray());
        out.writeString(3, "path-" + OLD);
        out.flush();
        var result = MiliastraGuid.remap(wrap(bytes.toByteArray()), NEXT);
        assertEquals(
                List.of(new MiliastraGuid.Pair(OLD, NEXT), new MiliastraGuid.Pair(OLD + 1, NEXT + 1)),
                result.mapping());
        assertEquals(
                NEXT + 1, decodedGraph(result.bytes()).getNodes(0).getPins(0).getValue().getBId().getVal());
        assertEquals(NEXT + 1, entities(result.bytes()).get(2).getMeta().getAssetId());
        assertEquals(
                "path-" + OLD,
                Root.parseFrom(Arrays.copyOfRange(result.bytes(), 20, result.bytes().length - 4))
                        .getFilePath());
    }

    @Test
    void identityAndUnknownRootBytesArePreserved() throws Exception {
        var raw = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(raw);
        out.writeByteArray(1, entity(OLD).toByteArray());
        out.flush();
        byte[] opaque = new byte[] {(byte) 0xf8, 0x07, (byte) 0x81, 0};
        raw.write(opaque);
        byte[] source = wrap(raw.toByteArray());
        assertArrayEquals(source, MiliastraGuid.remap(source, Map.of((long) OLD, (long) OLD)).bytes());
        byte[] copy = MiliastraGuid.remap(source, NEXT).bytes();
        assertArrayEquals(opaque, Arrays.copyOfRange(copy, copy.length - 8, copy.length - 4));
    }

    @Test
    void mappingsAreParallelAndNeverCascade() throws Exception {
        byte[] source = container(entity(OLD), entity(OLD + 1));
        var result =
                entities(
                        MiliastraGuid.remap(
                                        source, Map.of((long) OLD, (long) OLD + 1, (long) OLD + 1, (long) OLD))
                                .bytes());
        assertEquals(OLD + 1, result.get(0).getMeta().getAssetId());
        assertEquals(OLD, result.get(1).getMeta().getAssetId());
    }

    @Test
    void malformedOrUnsupportedBodiesAndInvalidMappingsAreDiagnosed() throws Exception {
        byte[] source = container(entity(OLD));
        assertThrows(IllegalArgumentException.class, () -> MiliastraGuid.remap(source, 0));
        assertThrows(IllegalArgumentException.class, () -> MiliastraGuid.remap(source, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGuid.remap(source, Map.of((long) OLD, 2147483648L)));
        byte[] wrong = source.clone();
        ByteBuffer.wrap(wrong).putInt(4, 2);
        assertThrows(IllegalArgumentException.class, () -> MiliastraGuid.remap(wrong, NEXT));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGuid.remap(container(entity(OLD), entity(OLD)), NEXT));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGuid.remap(container(entity(OLD), entity(OLD + 1)), Integer.MAX_VALUE));
        var terrain =
                Asset.newBuilder().setMeta(AssetMeta.newBuilder().setAssetId(OLD)).setTypeValue(5).build();
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraGuid.remap(container(terrain), NEXT));
        var bad = entity(OLD).toBuilder().setEntityData(entity(OLD + 1).getEntityData()).build();
        assertThrows(IllegalArgumentException.class, () -> MiliastraGuid.remap(container(bad), NEXT));
    }

    @Test
    void collisionCheckIncludesExternalReferencesAndAssetsAfterPreview() throws Exception {
        var units = new Asset[121];
        for (int i = 0; i < units.length; i++) units[i] = entity(OLD + i);
        byte[] source = container(units);
        var result = MiliastraGuid.remap(source, NEXT);
        assertEquals(121, result.guidCount());
        assertEquals(100, result.mapping().size());
        assertEquals(
                NEXT + 120, entities(result.bytes()).get(120).getEntityData().getData().getEntityId());
        var external =
                entity(OLD).toBuilder().addDependentAssets(AssetMeta.newBuilder().setAssetId(NEXT)).build();
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraGuid.remap(container(external), NEXT));
        var store = new MiliastraAssetStore(directory);
        store.importFile("occupied.gia", source);
        var target = store.importFile("target.gia", container(entity(OLD - 1))).asset();
        assertThrows(IllegalArgumentException.class, () -> store.remap(target.id(), OLD + 120));
    }

    @Test
    void automaticAllocationReservesDependencyIdsAndPersistsAcrossRestart() throws Exception {
        var store = new MiliastraAssetStore(directory);
        var unrelated =
                entity(OLD).toBuilder()
                        .addDependentAssets(AssetMeta.newBuilder().setAssetId(NEXT + 20))
                        .build();
        var source = store.importFile("source.gia", container(unrelated)).asset();
        var result = store.remap(source.id(), null);
        assertEquals(NEXT + 21L, result.mapping().get(0).to());
        assertFalse(result.duplicate());
        var restarted = new MiliastraAssetStore(directory).remap(source.id(), null);
        assertTrue(restarted.duplicate());
        assertEquals(result.asset().id(), restarted.asset().id());
        assertEquals(2, store.list("", "", 0, 20).total());
        assertArrayEquals(container(unrelated), store.download(source.id()).bytes());
    }

    @Test
    void typedJsonSourcesCanBeCopiedAndSameInstanceConcurrencyDeduplicates() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] original;
        try (var input = getClass().getResourceAsStream("/miliastra/editor-sample.gia")) {
            original = input.readAllBytes();
        }
        var source = store.importFile("source.gia.json", MiliastraNodeJson.decode(original)).asset();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results =
                    executor.invokeAll(
                            List.<Callable<MiliastraAssetStore.Remapped>>of(
                                    () -> store.remap(source.id(), NEXT), () -> store.remap(source.id(), NEXT)));
            assertEquals(results.get(0).get().asset().id(), results.get(1).get().asset().id());
            assertNotEquals(results.get(0).get().duplicate(), results.get(1).get().duplicate());
            assertEquals("GIA", results.get(0).get().asset().type());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tooDeepValuesAndDamagedArchivesCannotProduceCopies() throws Exception {
        var value = idValue(OLD);
        for (int i = 0; i < 50; i++)
            value =
                    VarBase.newBuilder()
                            .setClass_Value(10000)
                            .setBConcreteValue(ConcreteBaseValue.newBuilder().setValue(value))
                            .build();
        byte[] deep = container(graph(false, NodePin.newBuilder().setType(2).setValue(value).build()));
        assertThrows(IllegalArgumentException.class, () -> MiliastraGuid.remap(deep, NEXT));
        var store = new MiliastraAssetStore(directory);
        var source = store.importFile("source.gia", container(entity(OLD))).asset();
        java.nio.file.Files.write(directory.resolve(source.id()).resolve("source.bin"), new byte[] {1});
        assertThrows(java.io.IOException.class, () -> store.remap(source.id(), null));
        assertEquals(1, store.list("", "", 0, 20).total());
    }

    @Test
    void misleadingArchiveNamesCannotOverrideTheRequestedStart() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] bytes = container(entity(OLD));
        var source = store.importFile("source.gia", bytes).asset();
        store.importFile(
                "source-" + source.id().substring(0, 12) + "-remapped-" + NEXT + ".gia",
                MiliastraGuid.remap(bytes, NEXT + 10).bytes());
        var result = store.remap(source.id(), NEXT);
        assertEquals(NEXT, result.mapping().get(0).to());
        assertFalse(result.duplicate());
        assertTrue(store.remap(source.id(), NEXT).duplicate());
    }

    @Test
    void aForgedCopyWithNonconsecutiveGuidsCannotMasqueradeAsTheRequestedCopy() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] bytes = container(entity(OLD), entity(OLD + 1));
        var source = store.importFile("source.gia", bytes).asset();
        var mapping = Map.of((long) OLD, (long) NEXT, (long) OLD + 1, (long) NEXT + 10);
        store.importFile(
                "source-" + source.id().substring(0, 12) + "-remapped-" + NEXT + ".gia",
                MiliastraGuid.remap(bytes, mapping).bytes());
        assertThrows(IllegalArgumentException.class, () -> store.remap(source.id(), NEXT));
        assertEquals(2, store.list("", "", 0, 20).total());
    }

    @Test
    void serverListsStructReferencesAndCustomCallsAreMappedWithTheirDeclaredRoles() throws Exception {
        var original =
                graph(
                        false,
                        NodePin.newBuilder()
                                .setType(7)
                                .setValue(
                                        VarBase.newBuilder()
                                                .setClass_Value(10002)
                                                .setBArray(ArrayBaseValue.newBuilder().addEntries(idValue(OLD))))
                                .build());
        var content =
                original.getGraph().getInner().getGraph().toBuilder()
                        .addGraphValues(GraphVariable.newBuilder().setTypeValue(2).setValues(idValue(OLD)))
                        .addGraphValues(GraphVariable.newBuilder().setTypeValue(25).setStructId(OLD))
                        .addNodes(
                                GraphNode.newBuilder()
                                        .setNodeIndex(2)
                                        .setGenericId(
                                                NodeProperty.newBuilder()
                                                        .setClass_Value(10000)
                                                        .setKindValue(21002)
                                                        .setNodeId(OLD)));
        original =
                original.toBuilder()
                        .setGraph(
                                original.getGraph().toBuilder()
                                        .setInner(original.getGraph().getInner().toBuilder().setGraph(content)))
                        .build();
        var result = decodedGraph(MiliastraGuid.remap(container(original), NEXT).bytes());
        assertEquals(
                NEXT, result.getNodes(0).getPins(0).getValue().getBArray().getEntries(0).getBId().getVal());
        assertEquals(NEXT, result.getGraphValues(0).getValues().getBId().getVal());
        assertEquals(NEXT, result.getGraphValues(1).getStructId());
        assertEquals(NEXT, result.getNodes(1).getGenericId().getNodeId());
    }
}
