package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ServerUpdateGlobalValueNotifyOuterClass.ServerUpdateGlobalValueNotify;
import emu.grasscutter.net.proto.UpdateTypeOuterClass.UpdateType;
import emu.grasscutter.server.packet.send.PacketServerUpdateGlobalValueNotify;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

class TeamGlobalValueProtocolTest {
    private static final String KEY = "AVATAR_ASTER_SCORE";

    @Test
    void independentWireFixtureMatchesAllFiveNativeReaderBranches() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        // Native 0x14e773600: tags 0x0d, 0x28, 0x60, 0x68 and 0x75.
        wire.writeFloat(1, -0.75f);
        wire.writeUInt32(5, 0x9a6d20b1);
        wire.writeEnum(12, 2);
        wire.writeUInt32(13, 0x0b000042);
        wire.writeFloat(14, 1.5f);
        wire.flush();
        var message = ServerUpdateGlobalValueNotify.parseFrom(bytes.toByteArray());
        assertEquals(-0.75f, message.getDelta());
        assertEquals(0x9a6d20b1, message.getKeyHash());
        assertEquals(UpdateType.UpdateType_SET, message.getUpdateType());
        assertEquals(0x0b000042, message.getEntityId());
        assertEquals(1.5f, message.getValue());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
        assertEquals(5, message.getDescriptorForType().getFields().size());
    }

    @Test
    void addUsesDeltaAndTheCurrentRegisteredCommand() throws Exception {
        var packet =
                new PacketServerUpdateGlobalValueNotify(42, KEY, UpdateType.UpdateType_ADD, -1.25f);
        assertEquals(20882, PacketOpcodes.ServerUpdateGlobalValueNotify);
        assertEquals(20882, packet.getOpcode());
        var fields = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(1, 5, 12, 13), fields.asMap().keySet());
        assertEquals(List.of(Float.floatToRawIntBits(-1.25f)), fields.getField(1).getFixed32List());
        assertEquals(
                List.of(Integer.toUnsignedLong(Utils.abilityHash(KEY))),
                fields.getField(5).getVarintList());
        assertEquals(List.of(1L), fields.getField(12).getVarintList());
        assertEquals(List.of(42L), fields.getField(13).getVarintList());
    }

    @Test
    void setUsesValueIncludingAnExplicitSetOperationForZero() throws Exception {
        var packet =
                new PacketServerUpdateGlobalValueNotify(42, KEY, UpdateType.UpdateType_SET, 2.5f);
        var fields = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(5, 12, 13, 14), fields.asMap().keySet());
        assertEquals(List.of(2L), fields.getField(12).getVarintList());
        assertEquals(List.of(Float.floatToRawIntBits(2.5f)), fields.getField(14).getFixed32List());
        var reset =
                ServerUpdateGlobalValueNotify.parseFrom(
                        new PacketServerUpdateGlobalValueNotify(
                                        42, KEY, UpdateType.UpdateType_SET, 0)
                                .getData());
        assertEquals(UpdateType.UpdateType_SET, reset.getUpdateType());
        assertEquals(0f, reset.getValue());
        assertEquals(0, UpdateType.UpdateType_INVALUE.getNumber());
        assertEquals(1, UpdateType.UpdateType_ADD.getNumber());
        assertEquals(2, UpdateType.UpdateType_SET.getNumber());
    }

    @Test
    void invalidPacketOperationsCannotLeaveTheServer() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PacketServerUpdateGlobalValueNotify(
                                42, KEY, UpdateType.UpdateType_INVALUE, 1));
        for (float amount :
                new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new PacketServerUpdateGlobalValueNotify(
                                    42, KEY, UpdateType.UpdateType_ADD, amount));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PacketServerUpdateGlobalValueNotify(
                                42, "", UpdateType.UpdateType_SET, 1));
    }
}
