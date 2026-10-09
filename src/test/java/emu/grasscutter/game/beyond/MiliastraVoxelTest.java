package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraVoxelTest {
    @TempDir Path directory;

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] row(String color) {
        return json("[{\"x\":-2,\"y\":3,\"z\":4,\"color\":" + color + "}]");
    }

    @Test
    void matchesEveryPaletteColorAndReferenceNormalizedDimensions() {
        for (var palette : MiliastraGenerator.palette()) {
            int rgb = palette.color().get(0) << 16 | palette.color().get(1) << 8 | palette.color().get(2);
            var block =
                    MiliastraVoxel.model(
                                    row("\"#" + String.format("%06X", rgb) + "\""),
                                    new MiliastraVoxel.Options(.25f, 128, new MiliastraGenerator.Vector(10, 20, 30)))
                            .get(0);
            assertEquals(palette.id(), block.templateId(), palette.name());
            assertEquals(new MiliastraGenerator.Vector(9.5f, 20.75f, 31), block.position());
            assertEquals(palette.scale().x() / palette.size() * .25f, block.scale().x(), 1e-6);
            assertEquals(palette.scale().y() / palette.size() * .25f, block.scale().y(), 1e-6);
            assertEquals(palette.scale().z() / palette.size() * .25f, block.scale().z(), 1e-6);
            assertEquals("Block_-2_3_4", block.name());
            assertTrue(block.isStatic());
            assertEquals(0, block.guid());
        }
    }

    @Test
    void supportsRgbRgbaHexAndIntegerArraysWithExactAlphaBoundary() {
        var options = MiliastraVoxel.Options.defaults();
        var expected = MiliastraVoxel.model(row("\"#877264\""), options).get(0);
        for (String color :
                List.of("\"877264\"", "\"#87726480\"", "[135,114,100]", "[135,114,100,128]"))
            assertEquals(expected, MiliastraVoxel.model(row(color), options).get(0));
        for (String color : List.of("\"#8772647F\"", "[135,114,100,127]"))
            assertThrows(IllegalArgumentException.class, () -> MiliastraVoxel.model(row(color), options));
        assertEquals(
                1,
                MiliastraVoxel.model(
                                row("\"#87726400\""), new MiliastraVoxel.Options(.01f, 0, options.origin()))
                        .size());
    }

    @Test
    void rejectsMalformedRowsDuplicateKeysCoordinatesAndUnboundedNumbers() {
        for (String bad :
                List.of(
                        "{}",
                        "[]",
                        "[null]",
                        "[1]",
                        "[{\"x\":0,\"y\":0,\"z\":0}]",
                        "[{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#FFFFFF\",\"extra\":0}]",
                        "[{\"x\":0,\"x\":1,\"y\":0,\"z\":0,\"color\":\"#FFFFFF\"}]",
                        "[{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#FFFFFF\"},{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#00000000\"}]",
                        "[{\"x\":1.5,\"y\":0,\"z\":0,\"color\":\"#FFFFFF\"}]",
                        "[{\"x\":2147483648,\"y\":0,\"z\":0,\"color\":\"#FFFFFF\"}]",
                        "[{\"x\":\"1\",\"y\":0,\"z\":0,\"color\":\"#FFFFFF\"}]"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MiliastraVoxel.model(json(bad), MiliastraVoxel.Options.defaults()),
                    bad);
        for (String color :
                List.of(
                        "null",
                        "42",
                        "true",
                        "\"#FFFF\"",
                        "\"#GGFFFF\"",
                        "[]",
                        "[1,2]",
                        "[1,2,3,4,5]",
                        "[1,2,256]",
                        "[1,2,-1]",
                        "[1,2,3.5]",
                        "[1,2,\"3\"]"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MiliastraVoxel.model(row(color), MiliastraVoxel.Options.defaults()),
                    color);
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraVoxel.model(new byte[] {(byte) 0xFF}, MiliastraVoxel.Options.defaults()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraVoxel.model(new byte[8 * 1024 * 1024 + 1], MiliastraVoxel.Options.defaults()));
    }

    @Test
    void rejectsInvalidScaleOverflowAndFloat32PositionCollapse() {
        var zero = new MiliastraGenerator.Vector(0, 0, 0);
        for (var options :
                List.of(
                        new MiliastraVoxel.Options(0, 128, zero),
                        new MiliastraVoxel.Options(-1, 128, zero),
                        new MiliastraVoxel.Options(Float.NaN, 128, zero),
                        new MiliastraVoxel.Options(Float.POSITIVE_INFINITY, 128, zero),
                        new MiliastraVoxel.Options(.01f, -1, zero),
                        new MiliastraVoxel.Options(.01f, 256, zero),
                        new MiliastraVoxel.Options(.01f, 128, new MiliastraGenerator.Vector(Float.NaN, 0, 0)),
                        new MiliastraVoxel.Options(Float.MAX_VALUE, 128, zero)))
            assertThrows(
                    IllegalArgumentException.class, () -> MiliastraVoxel.model(row("\"#877264\""), options));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraVoxel.model(row("\"#877264\""), null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraVoxel.model(
                                row("\"#95A091\""), new MiliastraVoxel.Options(Float.MIN_VALUE, 128, zero)));
        var points =
                json(
                        "[{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#877264\"},{\"x\":1,\"y\":0,\"z\":0,\"color\":\"#877264\"}]");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraVoxel.model(
                                points,
                                new MiliastraVoxel.Options(.01f, 128, new MiliastraGenerator.Vector(1e10f, 0, 0))));
    }

    @Test
    void allocationReservesKnownCameraReferencesEvenWithoutArchivedBinary() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var gia = MiliastraCamera.encode(MiliastraCamera.model(json("{\"cameras\":[{}]}")), 1078000000);
        var root =
                emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCollection.GIACollection.parseFrom(
                        java.util.Arrays.copyOfRange(gia, 20, gia.length - 4))
                        .toBuilder();
        root.getAssetsBuilder(0)
                .addDependentAssets(
                        emu.grasscutter.game.beyond.proto.assets.MiliastraAssetDefinitions.AssetMeta
                                .newBuilder()
                                .setAssetId(1120000000));
        root.getAssetsBuilder(0)
                .getCameraConfigBuilder()
                .addCameraDetails(
                        emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCamera.CameraDetail.newBuilder()
                                .setCameraId(1110000000)
                                .setCameraData(
                                        emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCamera.CameraData
                                                .newBuilder()
                                                .setName("secondary")));
        byte[] payload = root.build().toByteArray();
        gia =
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
        store.importFile("source.camera.json", MiliastraCameraJson.decode(gia));
        for (int id : new int[] {1078000000, 1110000000, 1120000000})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            store.generateVoxel(
                                    "collision.gia", row("\"#877264\""), MiliastraVoxel.Options.defaults(), id));
        assertEquals(
                1120000001,
                store
                        .generateVoxel("next.gia", row("\"#877264\""), MiliastraVoxel.Options.defaults(), null)
                        .firstAutomaticGuid());
    }

    @Test
    void boundsInputAtOneHundredThousandAndFiltersBeforeGuidAllocation() throws Exception {
        var text = new StringBuilder("[");
        for (int i = 0; i < 100000; i++) {
            if (i > 0) text.append(',');
            text.append("{\"x\":").append(i).append(",\"y\":0,\"z\":0,\"color\":\"#877264\"}");
        }
        text.append(']');
        assertEquals(
                100000,
                MiliastraVoxel.model(json(text.toString()), MiliastraVoxel.Options.defaults()).size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MiliastraVoxel.model(
                                json(
                                        text.substring(0, text.length() - 1)
                                                + ",{\"x\":100000,\"y\":0,\"z\":0,\"color\":\"#877264\"}]"),
                                MiliastraVoxel.Options.defaults()));
        Class.forName("emu.grasscutter.Grasscutter");
        var store = new MiliastraAssetStore(directory);
        var generated =
                store.generateVoxel(
                        "voxels.gia",
                        json(
                                "[{\"x\":0,\"y\":0,\"z\":0,\"color\":\"#87726400\"},{\"x\":1,\"y\":0,\"z\":0,\"color\":\"#877264\"}]"),
                        MiliastraVoxel.Options.defaults(),
                        1100000000);
        assertEquals(1, generated.count());
        assertEquals(1, generated.automaticCount());
        assertEquals(1100000000, generated.firstAutomaticGuid());
        var reopened = new MiliastraAssetStore(directory);
        var next =
                reopened.generateVoxel(
                        "next.gia", row("\"#877264\""), MiliastraVoxel.Options.defaults(), null);
        assertEquals(1100000001, next.firstAutomaticGuid());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reopened.generateVoxel(
                                "collision.gia",
                                row("\"#877264\""),
                                MiliastraVoxel.Options.defaults(),
                                1100000000));
    }
}
