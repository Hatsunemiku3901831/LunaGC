package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Historical descriptor diagnostics; these coordinates are not CN72 evidence. */
class UnmigratedSelectionDescriptorTest {
    private BasePacket notification() throws Exception {
        // The implementation was absent when this regression was introduced.
        return (BasePacket)
                Class.forName("emu.grasscutter.server.packet.send.PacketAvatarSelectionDataNotify")
                        .getConstructor()
                        .newInstance();
    }

    private Descriptors.Descriptor descriptor() throws Exception {
        var type =
                Class.forName(
                        "emu.grasscutter.net.proto.AvatarSelectionDataNotifyOuterClass$AvatarSelectionDataNotify");
        return ((Message) type.getMethod("getDefaultInstance").invoke(null)).getDescriptorForType();
    }

    @Test
    void unverifiedHistoricalNotificationCannotRemainAnActiveLoginSend() throws Exception {
        var packet = notification();
        assertTrue(PacketOpcodes.AvatarSelectionDataNotify < 0);
        assertTrue(packet.getOpcode() < 0);
        assertArrayEquals(new byte[0], packet.getData());
        var source = Files.readString(Path.of("src/main/java/emu/grasscutter/game/player/Player.java"));
        assertFalse(source.contains("new PacketAvatarSelectionDataNotify("));
    }

    @Test
    void bothNativeListFieldsArePreservedWithoutInventedFields() throws Exception {
        var descriptor = descriptor();
        assertEquals(
                List.of(1, 15),
                descriptor.getFields().stream()
                        .map(Descriptors.FieldDescriptor::getNumber)
                        .sorted()
                        .toList());
        for (var field : descriptor.getFields()) {
            assertTrue(field.isRepeated());
            assertEquals(Descriptors.FieldDescriptor.Type.UINT32, field.getType());
        }
    }

    @Test
    void packedAndUnpackedNativeSamplesDecodeToTheSameLists() throws Exception {
        var descriptor = descriptor();
        // Native reader 0x148d24d40 accepts 0x08/0x0a for object+0x20,
        // and 0x78/0x7a for object+0x18. Consumer 0x14c265e20 initializes both caches.
        var packed =
                DynamicMessage.parseFrom(descriptor, new byte[] {0x0a, 2, 7, 43, 0x7a, 2, 73, 96});
        var unpacked =
                DynamicMessage.parseFrom(
                        descriptor, new byte[] {0x08, 7, 0x08, 43, 0x78, 73, 0x78, 96});
        assertEquals(packed, unpacked);
        assertEquals(List.of(7, 43), packed.getField(descriptor.findFieldByNumber(1)));
        assertEquals(List.of(73, 96), packed.getField(descriptor.findFieldByNumber(15)));
    }
}
