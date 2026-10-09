package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.beyond.proto.assets.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCameraTest {
    @TempDir Path directory;

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static List<MiliastraCamera.Camera> model(String text) {
        return MiliastraCamera.model(bytes(text));
    }

    static MiliastraAssetCollection.GIACollection parse(byte[] gia) throws Exception {
        return MiliastraAssetCollection.GIACollection.parseFrom(
                Arrays.copyOfRange(gia, 20, gia.length - 4));
    }

    @Test
    void allFiveTemplatesUseReferenceModesOffsetsAndMetadata() throws Exception {
        var c =
                model(
                        "{\"cameras\":[{\"preset\":\"twoPointFiveD\"},{\"preset\":\"backCamera\"},{\"preset\":\"firstPerson\"},{\"preset\":\"classic\"},{\"preset\":\"thirdPerson\"}]}");
        var root = parse(MiliastraCamera.encode(c, 1100000000));
        assertEquals(5, root.getAssetsCount());
        for (int i = 0; i < 5; i++) {
            var a = root.getAssets(i);
            var d = a.getCameraConfig().getCameraDetails(0);
            assertEquals(25, a.getMeta().getField2());
            assertEquals(0, a.getMeta().getMetaTypeValue());
            assertEquals(13, a.getTypeValue());
            assertEquals(1100000000 + i, a.getMeta().getAssetId());
            assertEquals(a.getMeta().getAssetId(), d.getCameraId());
            assertEquals(a.getName(), d.getCameraData().getName());
            assertEquals(new int[] {2, 1, 3, 0, 4}[i], d.getCameraData().getCameraMode());
        }
        assertEquals(.6f, c.get(1).data().getViewpointOffset().getX());
        assertEquals(.3f, c.get(2).data().getViewpointOffset().getY());
        assertEquals(.8f, c.get(0).data().getField14());
        assertEquals(45, c.get(4).data().getFieldOfView());
    }

    @Test
    void everyCameraFieldAndCollisionIsActuallySerialized() throws Exception {
        var cameras =
                model(
                        "{\"cameras\":[{\"guid\":42,\"name\":\"镜头 <测试>\",\"cameraMode\":2,\"defaultDistance\":12,\"minDistance\":4,\"maxDistance\":18,\"fieldOfView\":55,\"viewpointOffset\":{\"x\":-2,\"y\":3,\"z\":4},\"followRotation\":false,\"field14\":0.5,\"minHorizontalAngle\":-10,\"maxHorizontalAngle\":20,\"minPitchAngle\":-30,\"maxPitchAngle\":40,\"ignoreCollision\":true,\"horizontalAngle\":15}]}");
        var a = parse(MiliastraCamera.encode(cameras, 100)).getAssets(0);
        var d = a.getCameraConfig().getCameraDetails(0).getCameraData();
        assertEquals(42, a.getMeta().getAssetId());
        assertEquals("镜头 <测试>", d.getName());
        assertEquals(2, d.getCameraMode());
        assertEquals(12, d.getDefaultDistance());
        assertEquals(4, d.getMinDistance());
        assertEquals(18, d.getMaxDistance());
        assertEquals(55, d.getFieldOfView());
        assertEquals(-2, d.getViewpointOffset().getX());
        assertEquals(3, d.getViewpointOffset().getY());
        assertEquals(4, d.getViewpointOffset().getZ());
        assertFalse(d.getFollowRotation());
        assertEquals(.5, d.getField14());
        assertEquals(-10, d.getMinHorizontalAngle());
        assertEquals(20, d.getMaxHorizontalAngle());
        assertEquals(-30, d.getMinPitchAngle());
        assertEquals(40, d.getMaxPitchAngle());
        assertEquals(1, d.getIgnoreCollision());
        assertEquals(15, d.getHorizontalAngle());
    }

    @Test
    void referenceVariationFamiliesCoverInclusiveEndpointsWithoutDecimalDrift() {
        var c =
                model(
                        "{\"variations\":[{\"parameter\":\"horizontalAngle\",\"from\":-180,\"to\":180,\"step\":1},{\"parameter\":\"pitchAngle\",\"from\":-89,\"to\":89,\"step\":1},{\"parameter\":\"distance\",\"from\":1,\"to\":30,\"step\":0.1},{\"parameter\":\"fieldOfView\",\"from\":30,\"to\":60,\"step\":1}]}");
        assertEquals(862, c.size());
        assertEquals(-180, c.get(0).data().getMinHorizontalAngle());
        assertEquals(180, c.get(360).data().getMaxHorizontalAngle());
        assertEquals(-89, c.get(361).data().getMinPitchAngle());
        assertEquals(89, c.get(539).data().getMaxPitchAngle());
        assertEquals(1, c.get(540).data().getDefaultDistance());
        assertEquals(30, c.get(830).data().getMaxDistance());
        assertEquals("distance-1.1", c.get(541).data().getName());
        assertEquals(30, c.get(831).data().getFieldOfView());
        assertEquals(60, c.get(861).data().getFieldOfView());
    }

    @Test
    void invalidRangesTypesUnknownFieldsAndDuplicateGuidsAreRejected() {
        for (String fields :
                List.of(
                        "\"preset\":\"absent\"",
                        "\"guid\":0",
                        "\"guid\":1.5",
                        "\"cameraMode\":0",
                        "\"fieldOfView\":29",
                        "\"minDistance\":7",
                        "\"minPitchAngle\":90",
                        "\"maxHorizontalAngle\":-190",
                        "\"ignoreCollision\":1",
                        "\"field14\":2",
                        "\"fieldOfView\":\"45\"",
                        "\"viewpointOffset\":{\"w\":0}",
                        "\"viewpointOffset\":null",
                        "\"other\":1",
                        "\"defaultDistance\":1e100"))
            assertThrows(
                    IllegalArgumentException.class, () -> model("{\"cameras\":[{" + fields + "}]}"), fields);
        assertThrows(
                IllegalArgumentException.class, () -> model("{\"cameras\":[{\"guid\":42},{\"guid\":42}]}"));
        assertThrows(IllegalArgumentException.class, () -> model("{\"cameras\":[],\"other\":1}"));
        assertThrows(IllegalArgumentException.class, () -> model("{\"cameras\":[]}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> model("{\"cameras\":[{\"fieldOfView\":45,\"fieldOfView\":46}]}"));
    }

    @Test
    void invalidOrExplosiveSeriesAreRejectedBeforeLargeAllocation() {
        for (String series :
                List.of(
                        "\"parameter\":\"wrong\",\"from\":1,\"to\":2,\"step\":1",
                        "\"parameter\":\"distance\",\"from\":1,\"to\":2,\"step\":0",
                        "\"parameter\":\"distance\",\"from\":2,\"to\":1,\"step\":1",
                        "\"parameter\":\"distance\",\"from\":1,\"to\":2,\"step\":0.00000001",
                        "\"parameter\":\"distance\",\"from\":1,\"to\":1.00000001,\"step\":0.00000001",
                        "\"parameter\":\"distance\",\"from\":1,\"to\":2,\"step\":1e-100",
                        "\"parameter\":\"distance\",\"from\":1,\"to\":2,\"step\":1,\"camera\":{\"guid\":8}"))
            assertThrows(
                    IllegalArgumentException.class, () -> model("{\"variations\":[{" + series + "}]}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        model(
                                "{\"variations\":[{\"parameter\":\"horizontalAngle\",\"from\":-180,\"to\":180,\"step\":0.004,\"namePrefix\":\""
                                        + "a".repeat(4000)
                                        + "\"}]}"));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCamera.model(new byte[8 * 1024 * 1024 + 1]));
    }

    @Test
    void mixedExplicitAndAutomaticIdsRemainDistinctAndInt32Bounded() throws Exception {
        var c = model("{\"cameras\":[{\"guid\":42},{},{\"guid\":44},{}]}");
        var root = parse(MiliastraCamera.encode(c, 100));
        assertEquals(
                List.of(42, 100, 44, 101),
                root.getAssetsList().stream().map(a -> a.getMeta().getAssetId()).toList());
        assertThrows(IllegalArgumentException.class, () -> MiliastraCamera.encode(c, 42));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCamera.encode(c, Integer.MAX_VALUE));
    }

    @Test
    void generatedCamerasArchivePersistAndInteroperateWithGuidCopying() throws Exception {
        var store = new MiliastraAssetStore(directory);
        var a = store.generateCamera("camera.gia", bytes("{\"cameras\":[{},{}]}"), 1100000000);
        assertEquals(2, a.count());
        assertEquals(2, a.automaticCount());
        assertEquals(1100000000, a.firstAutomaticGuid());
        var copied = store.remap(a.asset().id(), 1110000000);
        assertEquals(2, copied.guidCount());
        var root = parse(store.download(copied.asset().id()).bytes());
        assertEquals(1110000001, root.getAssets(1).getCameraConfig().getCameraDetails(0).getCameraId());
        var restarted = new MiliastraAssetStore(directory);
        assertArrayEquals(
                store.download(a.asset().id()).bytes(), restarted.download(a.asset().id()).bytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> restarted.generateCamera("bad.gia", bytes("{\"cameras\":[{}]}"), 1100000000));
        var next = restarted.generateCamera("next.gia", bytes("{\"cameras\":[{}]}"), null);
        assertEquals(1110000002, next.firstAutomaticGuid());
    }

    @Test
    void allocationReservesSecondaryCameraAndDependencyIds() throws Exception {
        var root = parse(MiliastraCamera.encode(model("{\"cameras\":[{}]}"), 1078000000)).toBuilder();
        root.getAssetsBuilder(0)
                .addDependentAssets(
                        MiliastraAssetDefinitions.AssetMeta.newBuilder().setAssetId(1120000000));
        root.getAssetsBuilder(0)
                .getCameraConfigBuilder()
                .addCameraDetails(
                        MiliastraAssetCamera.CameraDetail.newBuilder()
                                .setCameraId(1110000000)
                                .setCameraData(MiliastraAssetCamera.CameraData.newBuilder().setName("secondary")));
        byte[] payload = root.build().toByteArray();
        byte[] gia =
                java.nio.ByteBuffer.allocate(payload.length + 24)
                        .putInt(payload.length + 20)
                        .putInt(1)
                        .putInt(0x326)
                        .putInt(3)
                        .putInt(payload.length)
                        .put(payload)
                        .putInt(0x679)
                        .array();
        var store = new MiliastraAssetStore(directory);
        store.importFile("source.gia", gia);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.generateCamera("bad.gia", bytes("{\"cameras\":[{}]}"), 1110000000));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.generateCamera("bad.gia", bytes("{\"cameras\":[{}]}"), 1120000000));
        assertEquals(
                1120000001,
                store.generateCamera("new.gia", bytes("{\"cameras\":[{}]}"), null).firstAutomaticGuid());
    }

    @Test
    void templatesAreDefensiveCopiesAndSourceIsQualified() {
        var t = MiliastraCamera.templates();
        assertEquals(5, t.getAsJsonArray("templates").size());
        assertTrue(t.get("verifiedClientVersion").isJsonNull());
        t.getAsJsonArray("templates").remove(0);
        assertEquals(5, MiliastraCamera.templates().getAsJsonArray("templates").size());
    }

    @Test
    void concurrentGenerationCannotAllocateTheSameId() throws Exception {
        var store = new MiliastraAssetStore(directory);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> store.generateCamera("a.gia", bytes("{\"cameras\":[{}]}"), null));
            var b = pool.submit(() -> store.generateCamera("b.gia", bytes("{\"cameras\":[{}]}"), null));
            assertNotEquals(a.get().firstAutomaticGuid(), b.get().firstAutomaticGuid());
        } finally {
            pool.shutdownNow();
        }
    }
}
