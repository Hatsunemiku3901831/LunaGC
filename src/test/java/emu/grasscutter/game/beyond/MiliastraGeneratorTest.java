package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraGeneratorTest {
    @TempDir Path directory;

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] image(BufferedImage image) throws Exception {
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return bytes.toByteArray();
    }

    private static MiliastraGenerator.ImageOptions options(String plane) {
        return new MiliastraGenerator.ImageOptions(
                2, 2, false, .5f, 128, plane, new MiliastraGenerator.Vector(10, 20, 30));
    }

    private static MiliastraAssetStore store(Path path) throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        return new MiliastraAssetStore(path);
    }

    @Test
    void modelPreservesTransformsAndExplicitIds() throws Exception {
        var blocks =
                MiliastraGenerator.model(
                        json(
                                "{\"blocks\":[{\"templateId\":101,\"guid\":42,\"name\":\"方块\",\"static\":false,"
                                        + "\"position\":{\"x\":1.5,\"y\":-2,\"z\":3},\"rotation\":{\"x\":0,\"y\":90,\"z\":0},"
                                        + "\"scale\":{\"x\":2,\"y\":3,\"z\":4}}]}"));
        var block = blocks.get(0);
        assertEquals(101, block.templateId());
        assertEquals(42, block.guid());
        assertFalse(block.isStatic());
        assertEquals(90, block.rotation().y());
        assertEquals(new MiliastraGenerator.Vector(2, 3, 4), block.scale());
        var generated =
                store(directory)
                        .generateModel(
                                "实体.gia",
                                json("{\"blocks\":[{\"templateId\":101,\"guid\":42,\"name\":\"方块\"}]}"),
                                null);
        assertEquals(0, generated.automaticCount());
        assertEquals(42, generated.asset().binary().assets().get(0).guid());
        assertEquals("方块", generated.asset().name());
    }

    @Test
    void rejectsMalformedTypesUnknownFieldsAndInvalidTransforms() {
        for (String invalid :
                List.of(
                        "{}",
                        "[]",
                        "{\"blocks\":null}",
                        "{\"blocks\":[]}",
                        "{\"blocks\":[{\"templateId\":1.2}]}",
                        "{\"blocks\":[{\"templateId\":2147483648}]}",
                        "{\"blocks\":[{\"templateId\":\"1\"}]}",
                        "{\"blocks\":[{\"templateId\":1,\"guid\":0}]}",
                        "{\"blocks\":[{\"templateId\":1,\"static\":1}]}",
                        "{\"blocks\":[{\"templateId\":1,\"name\":null}]}",
                        "{\"blocks\":[{\"templateId\":1,\"ability\":{}}]}",
                        "{\"blocks\":[{\"templateId\":1,\"position\":{\"x\":1e40,\"y\":0,\"z\":0}}]}",
                        "{\"blocks\":[{\"templateId\":1,\"scale\":{\"x\":0,\"y\":1,\"z\":1}}]}",
                        "{\"blocks\":[{\"templateId\":1,\"templateId\":2}]}",
                        "{\"blocks\":[{\"templateId\":1,\"position\":{\"x\":0}}]}")) {
            assertThrows(
                    IllegalArgumentException.class, () -> MiliastraGenerator.model(json(invalid)), invalid);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGenerator.model(new byte[8 * 1024 * 1024 + 1]));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraGenerator.model(new byte[] {(byte) 0xFF}));
    }

    @Test
    void modelSupportsOneHundredThousandMinimalEntities() {
        String input = "{\"blocks\":[" + "{\"templateId\":1},".repeat(99999) + "{\"templateId\":1}]}";
        assertEquals(100000, MiliastraGenerator.model(json(input)).size());
    }

    @Test
    void pixelOriginsRemainCorrectAcrossAllThreePlanes() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        int color = rgb(MiliastraGenerator.palette().get(0));
        image.setRGB(1, 0, color); // Top right -> grid (1,1), other pixels transparent.
        for (String plane : List.of("XY", "XZ", "YZ")) {
            var blocks = MiliastraGenerator.image(image(image), options(plane));
            assertEquals(1, blocks.size());
            assertEquals("Pixel_1_1", blocks.get(0).name());
            var expected =
                    switch (plane) {
                        case "XY" -> new MiliastraGenerator.Vector(10.5f, 20.5f, 30);
                        case "XZ" -> new MiliastraGenerator.Vector(10.5f, 20, 30.5f);
                        default -> new MiliastraGenerator.Vector(10, 20.5f, 30.5f);
                    };
            assertEquals(expected, blocks.get(0).position());
        }
    }

    @Test
    void paletteColorsAndNonUniformDimensionsAreNormalized() throws Exception {
        assertEquals(21, MiliastraGenerator.palette().size());
        for (var palette : MiliastraGenerator.palette()) {
            var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, rgb(palette));
            var block =
                    MiliastraGenerator.image(
                                    image(image),
                                    new MiliastraGenerator.ImageOptions(
                                            1, 1, true, .1f, 128, "XY", new MiliastraGenerator.Vector(0, 0, 0)))
                            .get(0);
            assertEquals(palette.id(), block.templateId(), palette.name());
            assertEquals(palette.scale().x() / palette.size() * .1f, block.scale().x(), 1e-6);
            assertEquals(palette.scale().y() / palette.size() * .1f, block.scale().y(), 1e-6);
            assertEquals(palette.scale().z() / palette.size() * .1f, block.scale().z(), 1e-6);
        }
    }

    @Test
    void tinyAspectFitsStayNonZeroAndEmptyOrDamagedImagesAreRejected() throws Exception {
        var image = new BufferedImage(1, 1000, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 1000; y++) image.setRGB(0, y, 0xFFFFFFFF);
        assertEquals(
                240,
                MiliastraGenerator.image(image(image), MiliastraGenerator.ImageOptions.defaults()).size());
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraGenerator.image(new byte[0], options("XY")));
        var transparent = image(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraGenerator.image(transparent, options("XY")));
        byte[] png = image(image);
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraGenerator.image(java.util.Arrays.copyOf(png, 24), options("XY")));
        for (var bad :
                List.of(
                        new MiliastraGenerator.ImageOptions(
                                1000, 1000, true, .1f, 128, "XY", options("XY").origin()),
                        new MiliastraGenerator.ImageOptions(1, 1, true, 0, 128, "XY", options("XY").origin()),
                        new MiliastraGenerator.ImageOptions(1, 1, true, .1f, 256, "XY", options("XY").origin()),
                        new MiliastraGenerator.ImageOptions(
                                1, 1, true, .1f, 128, "ZX", options("XY").origin()))) {
            assertThrows(IllegalArgumentException.class, () -> MiliastraGenerator.image(png, bad));
        }
    }

    @Test
    void generatesDefault240PixelOutputAndAllocatesAfterEveryHiddenGuid() throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        var first =
                store(directory)
                        .generateImage(
                                "像素画.gia", image(image), MiliastraGenerator.ImageOptions.defaults(), null);
        assertEquals(57600, first.count());
        assertEquals(57600, first.asset().binary().assetCount());
        assertEquals(100, first.asset().binary().assets().size());
        var reopened = store(directory);
        var second =
                reopened.generateModel("next.gia", json("{\"blocks\":[{\"templateId\":1}]}"), null);
        assertEquals(first.firstAutomaticGuid() + 57600, second.firstAutomaticGuid());
        long hiddenGuid = first.firstAutomaticGuid() + 57599L;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reopened.generateModel(
                                "collision.gia",
                                json("{\"blocks\":[{\"templateId\":1,\"guid\":" + hiddenGuid + "}]}"),
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reopened.generateModel(
                                "manual.gia", json("{\"blocks\":[{\"templateId\":1}]}"), (int) hiddenGuid));
        assertEquals(2, reopened.list("", "", 0, 100).total());
        assertTrue(
                reopened
                        .importFile(
                                "overlap.gia",
                                MiliastraGenerator.encode(
                                        MiliastraGenerator.model(json("{\"blocks\":[{\"templateId\":2}]}")),
                                        (int) hiddenGuid))
                        .asset()
                        .warnings()
                        .stream()
                        .anyMatch(w -> w.contains("GUID 重合")));
    }

    @Test
    void concurrentCallsOnOneStoreGetDifferentRanges() throws Exception {
        var store = store(directory);
        var executor = Executors.newFixedThreadPool(2);
        try {
            byte[] input = json("{\"blocks\":[{\"templateId\":1}]}");
            var a = executor.submit(() -> store.generateModel("a.gia", input, null));
            var b = executor.submit(() -> store.generateModel("b.gia", input, null));
            assertNotEquals(a.get().firstAutomaticGuid(), b.get().firstAutomaticGuid());
            assertEquals(2, store.list("", "", 0, 100).total());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsDuplicatesMixedIdCollisionsAndOverflowBeforeWriting() throws Exception {
        var store = store(directory);
        byte[] duplicates =
                json("{\"blocks\":[{\"templateId\":1,\"guid\":1},{\"templateId\":2,\"guid\":1}]}");
        assertThrows(
                IllegalArgumentException.class, () -> store.generateModel("dup.gia", duplicates, null));
        byte[] mixed = json("{\"blocks\":[{\"templateId\":1,\"guid\":1078000000},{\"templateId\":1}]}");
        assertThrows(
                IllegalArgumentException.class, () -> store.generateModel("mixed.gia", mixed, 1078000000));
        assertEquals(1078000001, store.generateModel("mixed.gia", mixed, null).firstAutomaticGuid());
        byte[] two = json("{\"blocks\":[{\"templateId\":1},{\"templateId\":1}]}");
        assertThrows(
                IllegalArgumentException.class,
                () -> store.generateModel("overflow.gia", two, Integer.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class, () -> store.generateModel("invalid.json", two, null));
        assertEquals(1, store.list("", "", 0, 100).total());
    }

    @Test
    void repeatedGuidBeyondPreviewIsStillRejected() throws Exception {
        var blocks =
                MiliastraGenerator.model(
                        json("{\"blocks\":[" + "{\"templateId\":1},".repeat(101) + "{\"templateId\":1}]}"));
        byte[] valid = MiliastraGenerator.encode(blocks, 1000);
        var input = com.google.protobuf.CodedInputStream.newInstance(valid, 20, valid.length - 24);
        var payload =
                MiliastraFileTest.message(
                        out -> {
                            byte[] initial = null;
                            int index = 0;
                            while (input.readTag() != 0) {
                                byte[] asset = input.readByteArray();
                                if (initial == null) initial = asset;
                                out.writeByteArray(1, ++index == 102 ? initial : asset);
                            }
                        });
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraFile.inspect(MiliastraFileTest.container(3, payload), "dup.gia"));
    }

    private static int rgb(MiliastraGenerator.Palette color) {
        return 0xFF000000
                | color.color().get(0) << 16
                | color.color().get(1) << 8
                | color.color().get(2);
    }
}
