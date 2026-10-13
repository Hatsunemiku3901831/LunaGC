package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

class FurnitureCancelProtocol71Test {
    byte[] bytes(String value) {
        return HexFormat.of().parseHex(value);
    }

    Descriptor request() throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.net.proto.FurnitureMakeCancelReqOuterClass$FurnitureMakeCancelReq");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type, "Native cancellation request schema is missing");
        return (Descriptor) type.getMethod("getDescriptor").invoke(null);
    }

    FurnitureMakeSlot slot() {
        return FurnitureMakeSlot.newBuilder()
                .addFurnitureMakeDataList(
                        FurnitureMakeData.newBuilder()
                                .setIndex(1)
                                .setMakeId(38)
                                .setBeginTime(1700000000)
                                .setDurTime(3600))
                .build();
    }

    byte[] responseVector(int ret, int make, FurnitureMakeSlot slot) throws Exception {
        var output = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(output);
        if (ret != 0) writer.writeInt32(3, ret);
        if (slot != null) writer.writeMessage(6, slot);
        if (make != 0) writer.writeUInt32(15, make);
        writer.flush();
        return output.toByteArray();
    }

    BasePacket packet(int ret, int make, List<FurnitureMakeData> slots) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.server.packet.send.PacketFurnitureMakeCancelRsp");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type, "Native cancellation response packet is missing");
        return (BasePacket)
                type.getConstructor(int.class, int.class, List.class).newInstance(ret, make, slots);
    }

    @Test
    void requestUsesNativeIndexAndRecipeTags() throws Exception {
        var d = request();
        assertEquals(2, d.getFields().size());
        assertEquals(4, d.findFieldByName("index").getNumber());
        assertEquals(11, d.findFieldByName("make_id").getNumber());
        assertTrue(
                d.getFields().stream().allMatch(f -> f.getType() == FieldDescriptor.Type.UINT32));
    }

    @Test
    void requestDecodesNativeVector() throws Exception {
        var d = request();
        var parsed = DynamicMessage.parseFrom(d, bytes("20035826"));
        assertEquals(3, parsed.getField(d.findFieldByNumber(4)));
        assertEquals(38, parsed.getField(d.findFieldByNumber(11)));
        assertArrayEquals(bytes("20035826"), parsed.toByteArray());
    }

    @Test
    void zeroIndexDoesNotNeedAFakeNonzeroSlot() throws Exception {
        var d = request();
        var parsed = DynamicMessage.parseFrom(d, bytes("5826"));
        assertEquals(0, parsed.getField(d.findFieldByNumber(4)));
        assertEquals(38, parsed.getField(d.findFieldByNumber(11)));
        assertArrayEquals(bytes("5826"), parsed.toByteArray());
    }

    @Test
    void unsignedIndexBitsArePreserved() throws Exception {
        var d = request();
        var parsed = DynamicMessage.parseFrom(d, bytes("20ffffffff0f"));
        assertEquals(-1, parsed.getField(d.findFieldByNumber(4)));
        assertArrayEquals(bytes("20ffffffff0f"), parsed.toByteArray());
    }

    @Test
    void emptyRequestHasZeroScalars() throws Exception {
        var d = request();
        var parsed = DynamicMessage.parseFrom(d, new byte[0]);
        assertEquals(0, parsed.getField(d.findFieldByNumber(4)));
        assertEquals(0, parsed.getField(d.findFieldByNumber(11)));
    }

    @Test
    void responseUsesCurrentNativeTags() {
        var d = FurnitureMakeCancelRsp.getDescriptor();
        assertEquals(3, d.getFields().size());
        assertEquals(3, d.findFieldByName("retcode").getNumber());
        assertEquals(6, d.findFieldByName("furniture_make_slot").getNumber());
        assertEquals(15, d.findFieldByName("make_id").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByName("make_id").getType());
    }

    @Test
    void responseDecodesNativeMakeIdVector() throws Exception {
        assertEquals(38, FurnitureMakeCancelRsp.parseFrom(bytes("7826")).getMakeId());
    }

    @Test
    void responseDecodesNativeErrorVector() throws Exception {
        assertEquals(1, FurnitureMakeCancelRsp.parseFrom(bytes("1801")).getRetcode());
    }

    @Test
    void responseDecodesTheRemainingQueue() throws Exception {
        var parsed = FurnitureMakeCancelRsp.parseFrom(responseVector(0, 38, slot()));
        assertEquals(38, parsed.getMakeId());
        assertEquals(slot(), parsed.getFurnitureMakeSlot());
    }

    @Test
    void responseSerializesNativeMakeIdVector() {
        assertArrayEquals(
                bytes("7826"),
                FurnitureMakeCancelRsp.newBuilder().setMakeId(38).build().toByteArray());
    }

    @Test
    void responseSerializesNativeErrorVector() {
        assertArrayEquals(
                bytes("1801"),
                FurnitureMakeCancelRsp.newBuilder().setRetcode(1).build().toByteArray());
    }

    @Test
    void responseSerializesTheCurrentQueueVector() throws Exception {
        assertArrayEquals(
                responseVector(0, 38, slot()),
                FurnitureMakeCancelRsp.newBuilder()
                        .setMakeId(38)
                        .setFurnitureMakeSlot(slot())
                        .build()
                        .toByteArray());
    }

    @Test
    void requestAndResponseOpcodesMatchNativeGetters() {
        assertEquals(8383, PacketOpcodes.FurnitureMakeCancelReq);
        assertEquals(8571, PacketOpcodes.FurnitureMakeCancelRsp);
    }

    @Test
    void packetSuccessIncludesOnlyRemainingTasks() throws Exception {
        var packet = packet(0, 38, slot().getFurnitureMakeDataListList());
        assertEquals(8571, packet.getOpcode());
        assertArrayEquals(responseVector(0, 38, slot()), packet.getData());
    }

    @Test
    void packetFailureEchoesRecipeWithoutSuccessfulSlots() throws Exception {
        var packet = packet(Retcode.RET_SVR_ERROR_VALUE, 38, slot().getFurnitureMakeDataListList());
        var parsed = FurnitureMakeCancelRsp.parseFrom(packet.getData());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, parsed.getRetcode());
        assertEquals(38, parsed.getMakeId());
        assertFalse(parsed.hasFurnitureMakeSlot());
    }

    @Test
    void emptyRemainingQueueIsExplicitOnSuccess() throws Exception {
        var packet = packet(0, 38, List.of());
        var parsed = FurnitureMakeCancelRsp.parseFrom(packet.getData());
        assertTrue(parsed.hasFurnitureMakeSlot());
        assertEquals(0, parsed.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
    }

    @Test
    void failureWithNoQueueUsesNativeRetcode() throws Exception {
        var packet = packet(Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE, 38, null);
        assertArrayEquals(
                responseVector(Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE, 38, null),
                packet.getData());
    }
}
