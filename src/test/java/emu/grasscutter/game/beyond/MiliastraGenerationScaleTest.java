package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraGenerationScaleTest {
    @TempDir Path directory;

    @Test
    void default240PixelOutputCanBeArchivedWithABoundedPreview() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        byte[] payload =
                MiliastraFileTest.message(
                        out -> {
                            for (int index = 0; index < 240 * 240; index++) {
                                int guid = 1078000000 + index;
                                byte[] info =
                                        MiliastraFileTest.message(
                                                i -> {
                                                    i.writeUInt32(3, 2);
                                                    i.writeUInt32(4, guid);
                                                });
                                byte[] asset =
                                        MiliastraFileTest.message(
                                                a -> {
                                                    a.writeByteArray(1, info);
                                                    a.writeString(3, "Pixel_" + guid);
                                                    a.writeUInt32(5, 3);
                                                });
                                out.writeByteArray(1, asset);
                            }
                        });
        byte[] bytes = MiliastraFileTest.container(3, payload);
        var preview = MiliastraFile.inspect(bytes, "default-pixels.gia");
        assertTrue(preview.assets().size() <= 100);
        assertEquals(57600, JsonUtils.toJson(preview).getAsJsonObject().get("assetCount").getAsInt());
        var result = new MiliastraAssetStore(directory).importFile("default-pixels.gia", bytes);
        assertArrayEquals(
                bytes, new MiliastraAssetStore(directory).download(result.asset().id()).bytes());
    }
}
