package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.FurnitureMakeStartReqOuterClass.FurnitureMakeStartReq;
import emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeFurnitureMakeReqOuterClass.TakeFurnitureMakeReq;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.server.packet.send.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

class FurnitureProtocol71Test {
    private byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private FurnitureMakeData data() {
        return FurnitureMakeData.newBuilder()
                .setIndex(1)
                .setMakeId(38)
                .setAvatarId(10000014)
                .setDurTime(3600)
                .setBeginTime(1700000000)
                .setAccelerateTime(60)
                .build();
    }

    private ItemParam output() {
        return ItemParam.newBuilder().setItemId(360101).setCount(1).build();
    }

    private FieldDescriptor field(Message message, int number) {
        var field = message.getDescriptorForType().findFieldByNumber(number);
        assertNotNull(field, "native field " + number);
        return field;
    }

    private byte[] startVector(int ret, boolean withSlot) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(bytes);
        if (ret != 0) writer.writeInt32(8, ret);
        if (withSlot)
            writer.writeMessage(
                    14, FurnitureMakeSlot.newBuilder().addFurnitureMakeDataList(data()).build());
        writer.flush();
        return bytes.toByteArray();
    }

    @Test
    void makeDataMatchesNativeScalarAndFixed32Tags() {
        var d = FurnitureMakeData.getDescriptor();
        assertEquals(6, d.getFields().size());
        assertEquals(13, d.findFieldByName("avatar_id").getNumber());
        assertEquals(6, d.findFieldByName("make_id").getNumber());
        assertEquals(5, d.findFieldByName("index").getNumber());
        assertEquals(3, d.findFieldByName("dur_time").getNumber());
        assertEquals(15, d.findFieldByName("begin_time").getNumber());
        assertEquals(4, d.findFieldByName("accelerate_time").getNumber());
        assertEquals(FieldDescriptor.Type.FIXED32, d.findFieldByName("begin_time").getType());
        assertEquals(FieldDescriptor.Type.FIXED32, d.findFieldByName("accelerate_time").getType());
    }

    @Test
    void fixedTimesPreserveUnsignedBits() throws Exception {
        var parsed = FurnitureMakeData.parseFrom(bytes("253c0000007dffffffff"));
        assertEquals(60, parsed.getAccelerateTime());
        assertEquals(-1, parsed.getBeginTime());
        assertArrayEquals(bytes("253c0000007dffffffff"), parsed.toByteArray());
    }

    @Test
    void slotListMatchesNativeFieldSeven() {
        var f = FurnitureMakeSlot.getDescriptor().findFieldByName("furniture_make_data_list");
        assertEquals(7, f.getNumber());
        assertTrue(f.isRepeated());
        assertEquals(FurnitureMakeData.getDescriptor(), f.getMessageType());
    }

    @Test
    void startRequestMatchesNativeMakeAndAvatarTags() throws Exception {
        var req = FurnitureMakeStartReq.parseFrom(bytes("30266001"));
        assertEquals(38, req.getMakeId());
        assertEquals(1, req.getAvatarId());
        assertArrayEquals(bytes("30266001"), req.toByteArray());
        assertEquals(22221, PacketOpcodes.FurnitureMakeStartReq);
    }

    @Test
    void takeRequestMatchesNativeFields() throws Exception {
        var req = TakeFurnitureMakeReq.parseFrom(bytes("180150016826"));
        assertEquals(1, req.getIndex());
        assertTrue(req.getIsFastFinish());
        assertEquals(38, req.getMakeId());
        assertArrayEquals(bytes("180150016826"), req.toByteArray());
        assertEquals(4563, PacketOpcodes.TakeFurnitureMakeReq);
    }

    @Test
    void startResponseUsesTheStageOneNativeResponseOpcode() {
        assertEquals(21056, PacketOpcodes.FurnitureMakeStartRsp);
        assertEquals(21056, new PacketFurnitureMakeStartRsp(0, List.of()).getOpcode());
    }

    @Test
    void startResponseUsesNativeErrorEightAndSlotFourteen() {
        var d = FurnitureMakeStartRsp.getDescriptor();
        assertEquals(8, d.findFieldByName("retcode").getNumber());
        assertEquals(14, d.findFieldByName("furniture_make_slot").getNumber());
    }

    @Test
    void nativeStartSlotVectorIsReadable() throws Exception {
        var reply = FurnitureMakeStartRsp.parseFrom(startVector(0, true));
        assertTrue(reply.hasFurnitureMakeSlot());
        assertEquals(List.of(data()), reply.getFurnitureMakeSlot().getFurnitureMakeDataListList());
    }

    @Test
    void successStartPacketHasTheNativeSlotVector() throws Exception {
        assertArrayEquals(
                startVector(0, true),
                new PacketFurnitureMakeStartRsp(0, List.of(data())).getData());
    }

    @Test
    void startFailureHasTheNativeSignedErrorTag() throws Exception {
        int ret = Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE;
        assertArrayEquals(
                startVector(ret, false), new PacketFurnitureMakeStartRsp(ret, null).getData());
    }

    @Test
    void nativeNegativeStartErrorIsReadable() throws Exception {
        assertEquals(
                -1, FurnitureMakeStartRsp.parseFrom(bytes("40ffffffffffffffffff01")).getRetcode());
    }

    @Test
    void takeFieldsUseNativeTagsWithoutPlaceholders() {
        var d = TakeFurnitureMakeRsp.getDescriptor();
        assertEquals(5, d.getFields().size());
        assertEquals(2, d.findFieldByName("make_id").getNumber());
        assertEquals(7, d.findFieldByName("retcode").getNumber());
        assertEquals(5, d.findFieldByName("furniture_make_slot").getNumber());
        assertNotNull(d.findFieldByNumber(12));
        assertNotNull(d.findFieldByNumber(15));
        assertNull(d.findFieldByNumber(50000));
        assertNull(d.findFieldByNumber(50001));
    }

    @Test
    void nativePrimaryOutputVectorIsReadable() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(bytes);
        writer.writeUInt32(2, 38);
        writer.writeMessage(12, output());
        writer.flush();
        var reply = TakeFurnitureMakeRsp.parseFrom(bytes.toByteArray());
        assertEquals(List.of(output()), reply.getField(field(reply, 12)));
    }

    @Test
    void nativeSecondaryOutputVectorIsReadable() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(bytes);
        writer.writeMessage(15, output());
        writer.flush();
        var reply = TakeFurnitureMakeRsp.parseFrom(bytes.toByteArray());
        assertEquals(List.of(output()), reply.getField(field(reply, 15)));
    }

    @Test
    void actualTakePacketIncludesPrimaryOutputs() throws Exception {
        var packet = new PacketTakeFurnitureMakeRsp(0, 38, List.of(output()), List.of(data()));
        var reply = TakeFurnitureMakeRsp.parseFrom(packet.getData());
        assertEquals(29002, packet.getOpcode());
        assertEquals(38, reply.getMakeId());
        assertEquals(List.of(output()), reply.getField(field(reply, 12)));
        assertEquals(List.of(data()), reply.getFurnitureMakeSlot().getFurnitureMakeDataListList());
    }

    @Test
    void actualTakePacketUsesTheNativeExactOutputBytes() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(bytes);
        writer.writeUInt32(2, 38);
        writer.writeMessage(12, output());
        writer.flush();
        assertArrayEquals(
                bytes.toByteArray(),
                new PacketTakeFurnitureMakeRsp(0, 38, List.of(output()), null).getData());
    }

    @Test
    void takeFailureCarriesNoRewardLists() throws Exception {
        var reply =
                TakeFurnitureMakeRsp.parseFrom(
                        new PacketTakeFurnitureMakeRsp(
                                        Retcode.RET_FURNITURE_MAKE_UNFINISH_VALUE, 38, null, null)
                                .getData());
        assertEquals(Retcode.RET_FURNITURE_MAKE_UNFINISH_VALUE, reply.getRetcode());
        assertEquals(List.of(), reply.getField(field(reply, 12)));
        assertEquals(List.of(), reply.getField(field(reply, 15)));
    }
}
