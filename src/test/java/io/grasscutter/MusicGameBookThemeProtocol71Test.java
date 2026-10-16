package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.FieldDescriptor.Type;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.MusicGameThemeUnlockNotifyOuterClass.MusicGameThemeUnlockNotify;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

/** Independent bytes from native 0x14880a030 and literal opcode getter 0x14880a2b0. */
class MusicGameBookThemeProtocol71Test {
    @Test
    void theCurrentNotificationHasOneUnsignedWordAtFieldSix() {
        assertEquals(22198, PacketOpcodes.MusicGameThemeUnlockNotify);
        var descriptor = MusicGameThemeUnlockNotify.getDescriptor();
        assertEquals(1, descriptor.getFields().size());
        var id = descriptor.findFieldByName("theme_id");
        assertEquals(6, id.getNumber());
        assertEquals(Type.UINT32, id.getType());
        assertFalse(id.isRepeated());
    }

    @Test
    void nativeWordEncodingKeepsAllThirtyTwoBits() throws Exception {
        var bytes = HexFormat.of().parseHex("30ffffffff0f");
        var parsed = MusicGameThemeUnlockNotify.parseFrom(bytes);
        assertEquals(-1, parsed.getThemeId());
        assertArrayEquals(bytes, parsed.toByteArray());
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void futureFieldsRemainAvailableAfterAReadAndWrite() throws Exception {
        var bytes = HexFormat.of().parseHex("30017811");
        var parsed = MusicGameThemeUnlockNotify.parseFrom(bytes);
        assertEquals(1, parsed.getThemeId());
        assertEquals(17L, parsed.getUnknownFields().getField(15).getVarintList().get(0));
        assertArrayEquals(bytes, parsed.toByteArray());
    }
}
