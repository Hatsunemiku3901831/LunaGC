package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;

import org.junit.jupiter.api.Test;

import java.util.*;

class FurnitureHelpProtocol71Test {
    Descriptor descriptor(String name) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type, "Missing native schema " + name);
        return (Descriptor) type.getMethod("getDescriptor").invoke(null);
    }

    DynamicMessage parse(String name, String bytes) throws Exception {
        return DynamicMessage.parseFrom(descriptor(name), HexFormat.of().parseHex(bytes));
    }

    Object field(Message message, int tag) {
        var f = message.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(f);
        return message.getField(f);
    }

    @Test
    void assistanceEntryUsesCurrentNativeTags() throws Exception {
        var d = descriptor("FurnitureMakeHelpData");
        assertEquals(2, d.getFields().size());
        assertNotNull(d.findFieldByNumber(3));
        assertNotNull(d.findFieldByNumber(9));
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(3).getType());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(9).getType());
    }

    @Test
    void assistanceEntryDecodesRawNativeScalarVector() throws Exception {
        var m = parse("FurnitureMakeHelpData", "18914e4802");
        assertEquals(10001, field(m, 3));
        assertEquals(2, field(m, 9));
        assertArrayEquals(HexFormat.of().parseHex("18914e4802"), m.toByteArray());
    }

    @Test
    void assistanceEntryPreservesUnknownFields() throws Exception {
        var m = parse("FurnitureMakeHelpData", "18914e4802980607");
        assertEquals(10001, field(m, 3));
        assertTrue(m.getUnknownFields().hasField(99));
    }

    @Test
    void assistanceEntryPreservesUnsignedBits() throws Exception {
        assertEquals(-1, field(parse("FurnitureMakeHelpData", "18ffffffff0f"), 3));
    }

    @Test
    void helpResponseUsesListThreeAndErrorFour() throws Exception {
        var d = descriptor("FurnitureMakeHelpRsp");
        assertEquals(2, d.getFields().size());
        var list = d.findFieldByNumber(3);
        assertNotNull(list);
        assertTrue(list.isRepeated());
        assertEquals(descriptor("FurnitureMakeHelpData"), list.getMessageType());
        assertEquals(FieldDescriptor.Type.INT32, d.findFieldByNumber(4).getType());
    }

    @Test
    void helpResponseRetainsEntryOrderAndSignedErrors() throws Exception {
        var m = parse("FurnitureMakeHelpRsp", "1a0318914e1a05480218924e20ffffffffffffffffff01");
        var list = (List<?>) field(m, 3);
        assertEquals(2, list.size());
        assertEquals(10001, field((Message) list.get(0), 3));
        assertEquals(10002, field((Message) list.get(1), 3));
        assertEquals(-1, field(m, 4));
    }

    @Test
    void helpNotificationUsesDetailsEightAndSlotsTwelve() throws Exception {
        var d = descriptor("FurnitureMakeBeHelpedNotify");
        assertEquals(2, d.getFields().size());
        assertEquals(
                descriptor("FurnitureMakeBeHelpedData"), d.findFieldByNumber(8).getMessageType());
        assertEquals(descriptor("FurnitureMakeSlot"), d.findFieldByNumber(12).getMessageType());
    }

    @Test
    void helpedProfileAndTimeKeepConfirmedCurrentTags() throws Exception {
        var d = descriptor("FurnitureMakeBeHelpedData");
        assertEquals(5, d.getFields().size());
        assertEquals(FieldDescriptor.Type.FIXED32, d.findFieldByName("time").getType());
        assertEquals(4, d.findFieldByName("time").getNumber());
        assertEquals(5, d.findFieldByName("player_name").getNumber());
        assertEquals(6, d.findFieldByName("uid").getNumber());
        assertEquals(9, d.findFieldByName("icon").getNumber());
        assertEquals(13, d.findFieldByName("profile_picture").getNumber());
    }

    @Test
    void helpNotificationDecodesProfileAndEmptySlot() throws Exception {
        var m = parse("FurnitureMakeBeHelpedNotify", "420b25010000002a014130914e6200");
        var detail = (Message) field(m, 8);
        assertEquals(1, field(detail, 4));
        assertEquals("A", field(detail, 5));
        assertEquals(10001, field(detail, 6));
        assertTrue(m.hasField(descriptor("FurnitureMakeBeHelpedNotify").findFieldByNumber(12)));
    }

    BasePacket response(int ret, List<?> entries) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.server.packet.send.PacketFurnitureMakeHelpRsp");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        var values = new ArrayList<>();
        if (entries != null)
            for (var entry : entries) values.add(typed("FurnitureMakeHelpData", (Message) entry));
        return (BasePacket)
                type.getConstructor(int.class, List.class)
                        .newInstance(ret, entries == null ? null : values);
    }

    Object typed(String name, Message value) throws Exception {
        var type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
        return type.getMethod("parseFrom", byte[].class).invoke(null, value.toByteArray());
    }

    @Test
    void successfulHelpResponseSerializesOnlyConfirmedEntries() throws Exception {
        var entry = parse("FurnitureMakeHelpData", "18914e4802");
        var packet = response(0, List.of(entry));
        assertEquals(23475, packet.getOpcode());
        var m = DynamicMessage.parseFrom(descriptor("FurnitureMakeHelpRsp"), packet.getData());
        assertEquals(1, ((List<?>) field(m, 3)).size());
        assertEquals(0, field(m, 4));
    }

    @Test
    void failedHelpResponseDoesNotInventHistory() throws Exception {
        var packet = response(9756, List.of(parse("FurnitureMakeHelpData", "18914e4802")));
        var m = DynamicMessage.parseFrom(descriptor("FurnitureMakeHelpRsp"), packet.getData());
        assertEquals(9756, field(m, 4));
        assertTrue(((List<?>) field(m, 3)).isEmpty());
    }

    @Test
    void emptySuccessfulHelpResponseHasNoEntries() throws Exception {
        var packet = response(0, null);
        assertTrue(
                ((List<?>)
                                field(
                                        DynamicMessage.parseFrom(
                                                descriptor("FurnitureMakeHelpRsp"),
                                                packet.getData()),
                                        3))
                        .isEmpty());
    }

    @Test
    void notificationPacketCarriesProfileAndUpdatedQueue() throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.send.PacketFurnitureMakeBeHelpedNotify");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        var detail = parse("FurnitureMakeBeHelpedData", "25010000002a014130914e");
        var slot =
                FurnitureMakeSlot.newBuilder()
                        .addFurnitureMakeDataList(
                                FurnitureMakeData.newBuilder()
                                        .setMakeId(38)
                                        .setIndex(2)
                                        .setAccelerateTime(14400))
                        .build();
        var packet =
                (BasePacket)
                        type.getConstructor(
                                        Class.forName(
                                                "emu.grasscutter.net.proto.FurnitureMakeBeHelpedDataOuterClass$FurnitureMakeBeHelpedData"),
                                        FurnitureMakeSlot.class)
                                .newInstance(typed("FurnitureMakeBeHelpedData", detail), slot);
        assertEquals(9270, packet.getOpcode());
        var value =
                DynamicMessage.parseFrom(
                        descriptor("FurnitureMakeBeHelpedNotify"), packet.getData());
        assertEquals(detail, field(value, 8));
        assertArrayEquals(slot.toByteArray(), ((Message) field(value, 12)).toByteArray());
    }

    @Test
    void nullNotificationDetailsCannotSendInvalidSuccess() throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.send.PacketFurnitureMakeBeHelpedNotify");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        var ctor =
                type.getConstructor(
                        Class.forName(
                                "emu.grasscutter.net.proto.FurnitureMakeBeHelpedDataOuterClass$FurnitureMakeBeHelpedData"),
                        FurnitureMakeSlot.class);
        assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> ctor.newInstance(null, FurnitureMakeSlot.getDefaultInstance()));
    }

    @Test
    void opcodesRemainDistinctFromOtherFurnitureResponses() {
        var values =
                List.of(
                        PacketOpcodes.FurnitureMakeHelpRsp,
                        PacketOpcodes.FurnitureMakeBeHelpedNotify,
                        PacketOpcodes.FurnitureMakeRsp,
                        PacketOpcodes.FurnitureMakeStartRsp,
                        PacketOpcodes.TakeFurnitureMakeRsp,
                        PacketOpcodes.TakeFurnitureMakeAllRsp,
                        PacketOpcodes.FurnitureMakeStartBatchRsp);
        assertEquals(values.size(), new HashSet<>(values).size());
    }
}
