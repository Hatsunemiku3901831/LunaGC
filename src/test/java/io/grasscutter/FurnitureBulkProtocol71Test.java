package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

class FurnitureBulkProtocol71Test {
    byte[] bytes(String value) {
        return HexFormat.of().parseHex(value);
    }

    Descriptor descriptor(String name) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type, "Missing native schema " + name);
        return (Descriptor) type.getMethod("getDescriptor").invoke(null);
    }

    DynamicMessage parse(String name, byte[] data) throws Exception {
        return DynamicMessage.parseFrom(descriptor(name), data);
    }

    Object field(Message value, int tag) {
        var descriptor = value.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(descriptor);
        return value.getField(descriptor);
    }

    ItemParam product() {
        return ItemParam.newBuilder().setItemId(360101).setCount(2).build();
    }

    FurnitureMakeSlot slot() {
        return FurnitureMakeSlot.newBuilder()
                .addFurnitureMakeDataList(
                        FurnitureMakeData.newBuilder()
                                .setIndex(2)
                                .setMakeId(38)
                                .setBeginTime(1700000000)
                                .setDurTime(3600))
                .build();
    }

    byte[] responseVector(int ret, List<ItemParam> products, FurnitureMakeSlot slot)
            throws Exception {
        var output = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(output);
        for (var product : products) writer.writeMessage(8, product);
        if (ret != 0) writer.writeInt32(9, ret);
        if (slot != null) writer.writeMessage(15, slot);
        writer.flush();
        return output.toByteArray();
    }

    BasePacket packet(int ret, List<ItemParam> products, List<FurnitureMakeData> slots)
            throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.send.PacketTakeFurnitureMakeAllRsp");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        return (BasePacket)
                type.getConstructor(int.class, List.class, List.class)
                        .newInstance(ret, products, slots);
    }

    @Test
    void requestCarriesNativeRepeatedInstructionsAtNine() throws Exception {
        var d = descriptor("TakeFurnitureMakeAllReq");
        assertEquals(1, d.getFields().size());
        var f = d.findFieldByNumber(9);
        assertNotNull(f);
        assertTrue(f.isRepeated());
        assertEquals(descriptor("FurnitureMakeTakeInfo"), f.getMessageType());
    }

    @Test
    void instructionUsesNativeIndexFastAndRecipeTags() throws Exception {
        var d = descriptor("FurnitureMakeTakeInfo");
        assertEquals(3, d.getFields().size());
        assertEquals(2, d.findFieldByName("index").getNumber());
        assertEquals(4, d.findFieldByName("is_fast_finish").getNumber());
        assertEquals(10, d.findFieldByName("make_id").getNumber());
        assertEquals(FieldDescriptor.Type.BOOL, d.findFieldByNumber(4).getType());
    }

    @Test
    void instructionDecodesNormalAndFastValues() throws Exception {
        var value = parse("FurnitureMakeTakeInfo", bytes("100320015026"));
        assertEquals(3, field(value, 2));
        assertEquals(true, field(value, 4));
        assertEquals(38, field(value, 10));
        assertArrayEquals(bytes("100320015026"), value.toByteArray());
    }

    @Test
    void zeroIndexAndNormalFinishUseDefaults() throws Exception {
        var value = parse("FurnitureMakeTakeInfo", bytes("5026"));
        assertEquals(0, field(value, 2));
        assertEquals(false, field(value, 4));
        assertEquals(38, field(value, 10));
    }

    @Test
    void instructionPreservesUnsignedIndexBits() throws Exception {
        var value = parse("FurnitureMakeTakeInfo", bytes("10ffffffff0f"));
        assertEquals(-1, field(value, 2));
        assertArrayEquals(bytes("10ffffffff0f"), value.toByteArray());
    }

    @Test
    void requestRetainsMixedInstructionsAndTheirOrder() throws Exception {
        var value = parse("TakeFurnitureMakeAllReq", bytes("4a0250264a06100120015027"));
        var instructions = (List<?>) field(value, 9);
        assertEquals(2, instructions.size());
        assertEquals(false, field((Message) instructions.get(0), 4));
        assertEquals(true, field((Message) instructions.get(1), 4));
        assertEquals(39, field((Message) instructions.get(1), 10));
        assertArrayEquals(bytes("4a0250264a06100120015027"), value.toByteArray());
    }

    @Test
    void emptyRequestDoesNotInventInstructions() throws Exception {
        assertTrue(((List<?>) field(parse("TakeFurnitureMakeAllReq", new byte[0]), 9)).isEmpty());
    }

    @Test
    void responseCarriesNativeRewardAndStateFields() throws Exception {
        var d = descriptor("TakeFurnitureMakeAllRsp");
        assertEquals(5, d.getFields().size());
        assertEquals(8, d.findFieldByName("output_item_list").getNumber());
        assertEquals(9, d.findFieldByName("retcode").getNumber());
        assertEquals(15, d.findFieldByName("furniture_make_slot").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(14).getType());
        assertTrue(d.findFieldByNumber(14).isPacked());
    }

    @Test
    void primaryRewardListIsDecodedFromNativeEight() throws Exception {
        var value = parse("TakeFurnitureMakeAllRsp", responseVector(0, List.of(product()), null));
        var list = (List<?>) field(value, 8);
        assertEquals(1, list.size());
        assertEquals(360101, field((Message) list.get(0), 1));
        assertEquals(2, field((Message) list.get(0), 2));
    }

    @Test
    void markedSecondaryRewardsRemainSeparate() throws Exception {
        var output = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(output);
        writer.writeMessage(11, product());
        writer.flush();
        var value = parse("TakeFurnitureMakeAllRsp", output.toByteArray());
        assertTrue(((List<?>) field(value, 8)).isEmpty());
        assertEquals(1, ((List<?>) field(value, 11)).size());
    }

    @Test
    void opaqueScalarListAcceptsNativePackedAndUnpackedForms() throws Exception {
        var packed = parse("TakeFurnitureMakeAllRsp", bytes("72020003"));
        var unpacked = parse("TakeFurnitureMakeAllRsp", bytes("70007003"));
        assertEquals(List.of(0, 3), field(packed, 14));
        assertEquals(field(packed, 14), field(unpacked, 14));
        assertArrayEquals(bytes("72020003"), unpacked.toByteArray());
    }

    @Test
    void failureUsesNativeInt32FieldNine() throws Exception {
        assertEquals(1, field(parse("TakeFurnitureMakeAllRsp", bytes("4801")), 9));
    }

    @Test
    void nativeRemainingQueueIsDecodedAtFifteen() throws Exception {
        var value = parse("TakeFurnitureMakeAllRsp", responseVector(0, List.of(), slot()));
        var remaining = (Message) field(value, 15);
        assertEquals(slot().toByteString(), remaining.toByteString());
    }

    @Test
    void opcodeGettersMatchNativeTypes() {
        assertEquals(478, PacketOpcodes.TakeFurnitureMakeAllReq);
        assertEquals(27862, PacketOpcodes.TakeFurnitureMakeAllRsp);
    }

    @Test
    void successfulPacketContainsConfirmedRewardsAndRemainingQueue() throws Exception {
        var packet = packet(0, List.of(product()), slot().getFurnitureMakeDataListList());
        assertEquals(27862, packet.getOpcode());
        assertArrayEquals(responseVector(0, List.of(product()), slot()), packet.getData());
    }

    @Test
    void failurePacketCannotLeakSuccessfulRewardsOrState() throws Exception {
        var packet = packet(1, List.of(product()), slot().getFurnitureMakeDataListList());
        var value = parse("TakeFurnitureMakeAllRsp", packet.getData());
        assertEquals(1, field(value, 9));
        assertTrue(((List<?>) field(value, 8)).isEmpty());
        assertFalse(value.hasField(value.getDescriptorForType().findFieldByNumber(15)));
    }

    @Test
    void emptySuccessfulQueueIsExplicit() throws Exception {
        var value = parse("TakeFurnitureMakeAllRsp", packet(0, List.of(), List.of()).getData());
        assertTrue(value.hasField(value.getDescriptorForType().findFieldByNumber(15)));
    }
}
