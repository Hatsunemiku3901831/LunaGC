package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

class FurnitureBatchStartProtocol71Test {
    byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
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

    DynamicMessage parse(String name, byte[] value) throws Exception {
        return DynamicMessage.parseFrom(descriptor(name), value);
    }

    Object field(Message value, int tag) {
        var field = value.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(field);
        return value.getField(field);
    }

    BasePacket packet(int ret, List<FurnitureMakeData> jobs) throws Exception {
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.send.PacketFurnitureMakeStartBatchRsp");
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        return (BasePacket) type.getConstructor(int.class, List.class).newInstance(ret, jobs);
    }

    FurnitureMakeData job() {
        return FurnitureMakeData.newBuilder()
                .setIndex(2)
                .setMakeId(38)
                .setAvatarId(10000014)
                .setBeginTime(1700000000)
                .setDurTime(43200)
                .build();
    }

    byte[] responseVector(int ret, List<FurnitureMakeData> jobs) throws Exception {
        var buffer = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(buffer);
        if (ret != 0) writer.writeInt32(4, ret);
        if (jobs != null)
            writer.writeMessage(
                    15, FurnitureMakeSlot.newBuilder().addAllFurnitureMakeDataList(jobs).build());
        writer.flush();
        return buffer.toByteArray();
    }

    @Test
    void requestHasOneRepeatedFieldAtTwelve() throws Exception {
        var d = descriptor("FurnitureMakeStartBatchReq");
        assertEquals(1, d.getFields().size());
        var f = d.findFieldByNumber(12);
        assertNotNull(f);
        assertTrue(f.isRepeated());
        assertEquals(descriptor("FurnitureMakeStartInfo"), f.getMessageType());
    }

    @Test
    void instructionSharesSingleStartSemanticsWithDifferentRecipeTag() throws Exception {
        var d = descriptor("FurnitureMakeStartInfo");
        assertEquals(2, d.getFields().size());
        assertEquals(7, d.findFieldByName("make_id").getNumber());
        assertEquals(12, d.findFieldByName("avatar_id").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(7).getType());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(12).getType());
    }

    @Test
    void instructionMatchesTheNativeWriter() throws Exception {
        var value = parse("FurnitureMakeStartInfo", bytes("3826600e"));
        assertEquals(38, field(value, 7));
        assertEquals(14, field(value, 12));
        assertArrayEquals(bytes("3826600e"), value.toByteArray());
    }

    @Test
    void defaultAvatarDoesNotInventACharacter() throws Exception {
        assertEquals(0, field(parse("FurnitureMakeStartInfo", bytes("3826")), 12));
    }

    @Test
    void repeatedJobsKeepOrderAndDuplicateRecipes() throws Exception {
        var value = parse("FurnitureMakeStartBatchReq", bytes("6202382662043827600e62023826"));
        var jobs = (List<?>) field(value, 12);
        assertEquals(3, jobs.size());
        assertEquals(38, field((Message) jobs.get(0), 7));
        assertEquals(39, field((Message) jobs.get(1), 7));
        assertEquals(14, field((Message) jobs.get(1), 12));
        assertEquals(38, field((Message) jobs.get(2), 7));
        assertArrayEquals(bytes("6202382662043827600e62023826"), value.toByteArray());
    }

    @Test
    void emptyListRemainsEmpty() throws Exception {
        assertTrue(
                ((List<?>) field(parse("FurnitureMakeStartBatchReq", new byte[0]), 12)).isEmpty());
    }

    @Test
    void allDefaultInstructionIsPreservedForBusinessValidation() throws Exception {
        var value = parse("FurnitureMakeStartBatchReq", bytes("6200"));
        assertEquals(1, ((List<?>) field(value, 12)).size());
        assertArrayEquals(bytes("6200"), value.toByteArray());
    }

    @Test
    void unsignedRecipeBitsRemainAvailableForBusinessValidation() throws Exception {
        var value = parse("FurnitureMakeStartInfo", bytes("38ffffffff0f"));
        assertEquals(-1, field(value, 7));
        assertArrayEquals(bytes("38ffffffff0f"), value.toByteArray());
    }

    @Test
    void truncatedInstructionIsRejected() throws Exception {
        var d = descriptor("FurnitureMakeStartBatchReq");
        assertThrows(
                InvalidProtocolBufferException.class,
                () -> DynamicMessage.parseFrom(d, bytes("620338")));
    }

    @Test
    void responseUsesNativeErrorFourAndQueueFifteen() throws Exception {
        var d = descriptor("FurnitureMakeStartBatchRsp");
        assertEquals(2, d.getFields().size());
        assertEquals(4, d.findFieldByName("retcode").getNumber());
        assertEquals(15, d.findFieldByName("furniture_make_slot").getNumber());
        assertEquals(FieldDescriptor.Type.INT32, d.findFieldByNumber(4).getType());
    }

    @Test
    void responseDecodesNativeQueueEnvelope() throws Exception {
        var value = parse("FurnitureMakeStartBatchRsp", responseVector(0, List.of(job())));
        var queue = (Message) field(value, 15);
        assertEquals(
                FurnitureMakeSlot.newBuilder()
                        .addFurnitureMakeDataList(job())
                        .build()
                        .toByteString(),
                queue.toByteString());
    }

    @Test
    void responseDecodesNativeError() throws Exception {
        assertEquals(1, field(parse("FurnitureMakeStartBatchRsp", bytes("2001")), 4));
    }

    @Test
    void opcodesMatchNativeGetters() throws Exception {
        assertEquals(871, PacketOpcodes.class.getField("FurnitureMakeStartBatchReq").getInt(null));
        assertEquals(9832, PacketOpcodes.class.getField("FurnitureMakeStartBatchRsp").getInt(null));
    }

    @Test
    void successPacketCarriesTheFullQueue() throws Exception {
        var p = packet(0, List.of(job()));
        assertEquals(9832, p.getOpcode());
        assertArrayEquals(responseVector(0, List.of(job())), p.getData());
    }

    @Test
    void failurePacketCannotCarrySuccessfulState() throws Exception {
        var p = packet(1, List.of(job()));
        assertArrayEquals(bytes("2001"), p.getData());
    }

    @Test
    void successfulEmptyQueueIsExplicit() throws Exception {
        assertArrayEquals(bytes("7a00"), packet(0, List.of()).getData());
    }
}
