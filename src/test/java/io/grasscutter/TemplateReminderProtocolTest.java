package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ShowTemplateReminderNotifyOuterClass.ShowTemplateReminderNotify;
import emu.grasscutter.server.packet.send.PacketShowTemplateReminderNotify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

class TemplateReminderProtocolTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentNativeWireFixturesKeepSignedAndUnsignedListsSeparate(boolean packed)
            throws Exception {
        var output = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(output);
        // Native 0x1519dce80 reads uint32 at tag 4, uint32[] at tag 1, int32[] at tag 12.
        wire.writeUInt32(4, 110);
        if (packed) {
            var numbers = new ByteArrayOutputStream();
            var values = CodedOutputStream.newInstance(numbers);
            values.writeInt32NoTag(0);
            values.writeInt32NoTag(-1);
            values.writeInt32NoTag(Integer.MIN_VALUE);
            values.writeInt32NoTag(Integer.MAX_VALUE);
            values.flush();
            wire.writeByteArray(12, numbers.toByteArray());
            wire.writeByteArray(
                    1, new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f});
        } else {
            for (int value : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
                wire.writeInt32(12, value);
            wire.writeUInt32(1, -1);
        }
        wire.flush();
        var message = ShowTemplateReminderNotify.parseFrom(output.toByteArray());
        assertEquals(110, message.getTemplateReminderId());
        assertEquals(
                List.of(0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE), message.getParamListList());
        assertEquals(List.of(-1), message.getParamUidListList());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
    }

    @Test
    void sceneReminderUsesTheVerifiedCommandAndNativeParameterTag() throws Exception {
        var packet = new PacketShowTemplateReminderNotify(110, new int[] {0, 0});
        assertEquals(28686, PacketOpcodes.ShowTemplateReminderNotify);
        assertEquals(28686, packet.getOpcode());
        var raw = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(4, 12), raw.asMap().keySet());
        assertEquals(List.of(110L), raw.getField(4).getVarintList());
        assertEquals(
                List.of(ByteString.copyFrom(new byte[] {0, 0})),
                raw.getField(12).getLengthDelimitedList());
    }

    @Test
    void descriptorUsesTheNativeSignedAndUnsignedCodecTypes() {
        var fields = ShowTemplateReminderNotify.getDescriptor();
        assertEquals(Type.UINT32, fields.findFieldByNumber(1).getType());
        assertTrue(fields.findFieldByNumber(1).isRepeated());
        assertEquals(Type.UINT32, fields.findFieldByNumber(4).getType());
        assertFalse(fields.findFieldByNumber(4).isRepeated());
        assertEquals(Type.INT32, fields.findFieldByNumber(12).getType());
        assertTrue(fields.findFieldByNumber(12).isRepeated());
    }
}
