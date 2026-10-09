package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MiliastraFileTest {
    @Test
    void readsActualEditorExportWithoutMutatingUnknownFields() throws Exception {
        byte[] source = fixture("editor-sample.gia"), before = source.clone();
        var preview = MiliastraFile.inspect(source, "editor.gia");
        assertEquals("GIA", preview.type());
        assertEquals("新建节点图", preview.name());
        assertEquals(1024346482, preview.assets().get(0).guid());
        assertEquals(357, preview.payloadBytes());
        assertArrayEquals(before, source);
        assertFalse(preview.warnings().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void permitsDocumentedEmptyContainer(int type) {
        String extension = new String[] {"gip", "gil", "gia", "gir"}[type - 1];
        assertEquals(
                extension.toUpperCase(),
                MiliastraFile.inspect(container(type, new byte[0]), "blank." + extension).type());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 12, 16, 20})
    void rejectsMalformedHeaderFooterOrLengths(int offset) {
        byte[] bytes = container(3, new byte[0]);
        ByteBuffer.wrap(bytes).putInt(offset, 0xFFFFFFFF);
        assertThrows(IllegalArgumentException.class, () -> MiliastraFile.inspect(bytes, "bad.gia"));
    }

    @Test
    void rejectsRenamedWrongContainerAndShortFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraFile.inspect(container(2, new byte[0]), "level.gia"));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraFile.inspect(new byte[23], "level.gia"));
    }

    @Test
    void preservesFutureVersionAndUnknownWireFields() throws Exception {
        byte[] bytes =
                container(
                        3,
                        message(
                                out -> {
                                    out.writeUInt32(100, 45);
                                    out.writeBytes(101, com.google.protobuf.ByteString.copyFromUtf8("opaque"));
                                }));
        ByteBuffer.wrap(bytes).putInt(4, -1);
        var preview = MiliastraFile.inspect(bytes, "future.gia");
        assertEquals(4294967295L, preview.version());
        assertTrue(preview.warnings().stream().anyMatch(s -> s.contains("4294967295")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "0affffffff7f", "80", "0b0c0c", "0e", "1aff"})
    void rejectsTruncatedOrInvalidWirePayload(String hex) {
        byte[] bytes = container(3, java.util.HexFormat.of().parseHex(hex));
        assertThrows(IllegalArgumentException.class, () -> MiliastraFile.inspect(bytes, "broken.gia"));
    }

    @Test
    void reportsUnsignedGuidAndRejectsDuplicatePrimaryAssets() throws Exception {
        byte[] info =
                message(
                        out -> {
                            out.writeUInt32(3, 2);
                            out.writeUInt32(4, 0xF0000001);
                        });
        byte[] asset =
                message(
                        out -> {
                            out.writeByteArray(1, info);
                            out.writeString(3, "生成的实体");
                            out.writeUInt32(5, 3);
                        });
        byte[] payload = message(out -> out.writeByteArray(1, asset));
        assertEquals(
                4026531841L,
                MiliastraFile.inspect(container(3, payload), "entity.gia").assets().get(0).guid());
        byte[] duplicate =
                message(
                        out -> {
                            out.writeByteArray(1, asset);
                            out.writeByteArray(1, asset);
                        });
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraFile.inspect(container(3, duplicate), "collision.gia"));
        byte[] related =
                message(
                        out -> {
                            out.writeByteArray(1, asset);
                            out.writeByteArray(2, asset);
                        });
        assertEquals(2, MiliastraFile.inspect(container(3, related), "related.gia").assets().size());
    }

    @Test
    void rejectsDeepUnknownGroupsAndOversizedMetadata() throws Exception {
        byte[] groups = new byte[130];
        Arrays.fill(groups, 0, 65, (byte) 0x0b);
        Arrays.fill(groups, 65, 130, (byte) 0x0c);
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraFile.inspect(container(3, groups), "deep.gia"));
        byte[] text = message(out -> out.writeString(3, "a".repeat(4097)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraFile.inspect(container(3, text), "long.gia"));
    }

    static byte[] fixture(String name) throws Exception {
        try (var input = MiliastraFileTest.class.getResourceAsStream("/miliastra/" + name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    static byte[] container(int type, byte[] payload) {
        return ByteBuffer.allocate(payload.length + 24)
                .putInt(payload.length + 20)
                .putInt(1)
                .putInt(0x326)
                .putInt(type)
                .putInt(payload.length)
                .put(payload)
                .putInt(0x679)
                .array();
    }

    interface Writer {
        void write(CodedOutputStream out) throws Exception;
    }

    static byte[] message(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        writer.write(out);
        out.flush();
        return bytes.toByteArray();
    }
}
