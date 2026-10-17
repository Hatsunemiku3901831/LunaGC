package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.BasePacket;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Independent native contract for the avatar lists used while initializing selection dialogs. */
class AvatarSelectionProtocol71Test {
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
    void initializationIsSentEvenWhenBothSelectionListsAreEmpty() throws Exception {
        var packet = notification();
        // Type 46070, getter 0x148d24b70: mov ax, 0x5290.
        assertEquals(21136, packet.getOpcode());
        assertArrayEquals(new byte[0], packet.getData());
        assertNotNull(packet.build());
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
