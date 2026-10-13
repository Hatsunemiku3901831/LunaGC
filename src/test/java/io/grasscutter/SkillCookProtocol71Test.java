package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

class SkillCookProtocol71Test {
    Descriptor descriptor(String name) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type, "Missing native skill cooking schema " + name);
        return (Descriptor) type.getMethod("getDescriptor").invoke(null);
    }

    DynamicMessage parse(String name, String hex) throws Exception {
        return DynamicMessage.parseFrom(descriptor(name), HexFormat.of().parseHex(hex));
    }

    Object field(Message value, int tag) {
        var f = value.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(f);
        return value.getField(f);
    }

    @Test
    void quotaNotificationKeepsRefreshSixAndUsedCountThirteen() throws Exception {
        var d = descriptor("AvatarEscoffierSkillCookDataNotify");
        assertEquals(2, d.getFields().size());
        assertEquals(6, d.findFieldByName("last_refresh_time").getNumber());
        assertEquals(13, d.findFieldByName("finished_weekly_cook_num").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(6).getType());
        assertEquals(FieldDescriptor.Type.UINT32, d.findFieldByNumber(13).getType());
    }

    @Test
    void quotaNotificationDecodesIndependentTimeAndCount() throws Exception {
        var value = parse("AvatarEscoffierSkillCookDataNotify", "3080e2cfaa06680a");
        assertEquals(1700000000, field(value, 6));
        assertEquals(10, field(value, 13));
        assertArrayEquals(HexFormat.of().parseHex("3080e2cfaa06680a"), value.toByteArray());
    }

    @Test
    void missingWeeklyCountDefaultsToZero() throws Exception {
        var value = parse("AvatarEscoffierSkillCookDataNotify", "3080e2cfaa06");
        assertEquals(0, field(value, 13));
        assertEquals(1700000000, field(value, 6));
    }

    @Test
    void quotaTimestampPreservesUnsignedBits() throws Exception {
        var value = parse("AvatarEscoffierSkillCookDataNotify", "30ffffffff0f6801");
        assertEquals(-1, field(value, 6));
        assertEquals(1, field(value, 13));
    }

    @Test
    void quotaNotificationPreservesUnknownFields() throws Exception {
        var value = parse("AvatarEscoffierSkillCookDataNotify", "6803980607");
        assertTrue(value.getUnknownFields().hasField(99));
        assertEquals(3, field(value, 13));
    }

    @Test
    void resultContainsKnownItemsTimeCountAndErrorFields() throws Exception {
        var d = descriptor("AvatarEscoffierSkillCookRsp");
        assertEquals(4, d.getFields().size());
        var items = d.findFieldByNumber(4);
        assertTrue(items.isRepeated());
        assertEquals(ItemParam.getDescriptor(), items.getMessageType());
        assertEquals(5, d.findFieldByName("finished_weekly_cook_num").getNumber());
        assertEquals(13, d.findFieldByName("last_refresh_time").getNumber());
        assertEquals(12, d.findFieldByName("retcode").getNumber());
        assertEquals(FieldDescriptor.Type.INT32, d.findFieldByNumber(12).getType());
    }

    @Test
    void resultRawVectorRetainsTwoRewardsAndQuota() throws Exception {
        var output = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(output);
        writer.writeMessage(4, ItemParam.newBuilder().setItemId(108824).setCount(2).build());
        writer.writeMessage(4, ItemParam.newBuilder().setItemId(108822).setCount(1).build());
        writer.writeUInt32(5, 4);
        writer.writeUInt32(13, 1700000000);
        writer.flush();
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookRsp"), output.toByteArray());
        assertEquals(2, ((List<?>) field(value, 4)).size());
        assertEquals(4, field(value, 5));
        assertEquals(1700000000, field(value, 13));
        assertEquals(0, field(value, 12));
        assertArrayEquals(output.toByteArray(), value.toByteArray());
    }

    @Test
    void negativeErrorsRetainSignedRepresentation() throws Exception {
        var value = parse("AvatarEscoffierSkillCookRsp", "60ffffffffffffffffff01");
        assertEquals(-1, field(value, 12));
        assertTrue(((List<?>) field(value, 4)).isEmpty());
    }

    @Test
    void resultPreservesForwardCompatibleFields() throws Exception {
        var value = parse("AvatarEscoffierSkillCookRsp", "2802980607");
        assertTrue(value.getUnknownFields().hasField(99));
        assertEquals(2, field(value, 5));
    }

    BasePacket packet(String name, Class<?>[] parameters, Object... values) throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.server.packet.send." + name);
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(type);
        return (BasePacket) type.getConstructor(parameters).newInstance(values);
    }

    @Test
    void successfulResultCarriesConfirmedItemAndQuota() throws Exception {
        var packet =
                packet(
                        "PacketAvatarEscoffierSkillCookRsp",
                        new Class<?>[] {int.class, List.class, int.class, int.class},
                        0,
                        List.of(ItemParam.newBuilder().setItemId(108824).setCount(2).build()),
                        1700000000,
                        1);
        assertEquals(1040, packet.getOpcode());
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookRsp"), packet.getData());
        assertEquals(1, ((List<?>) field(value, 4)).size());
        assertEquals(1700000000, field(value, 13));
        assertEquals(1, field(value, 5));
        assertEquals(0, field(value, 12));
    }

    @Test
    void failedResultExcludesProductsButKeepsConfirmedQuota() throws Exception {
        var packet =
                packet(
                        "PacketAvatarEscoffierSkillCookRsp",
                        new Class<?>[] {int.class, List.class, int.class, int.class},
                        1,
                        List.of(ItemParam.newBuilder().setItemId(108824).setCount(2).build()),
                        1700000000,
                        10);
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookRsp"), packet.getData());
        assertTrue(((List<?>) field(value, 4)).isEmpty());
        assertEquals(1, field(value, 12));
        assertEquals(10, field(value, 5));
        assertEquals(1700000000, field(value, 13));
    }

    @Test
    void zeroQuotaDoesNotInventFood() throws Exception {
        var packet =
                packet(
                        "PacketAvatarEscoffierSkillCookRsp",
                        new Class<?>[] {int.class, List.class, int.class, int.class},
                        0,
                        null,
                        1700000000,
                        0);
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookRsp"), packet.getData());
        assertTrue(((List<?>) field(value, 4)).isEmpty());
        assertEquals(0, field(value, 5));
    }

    @Test
    void notificationPacketUsesCurrentOpcodeAndStateTags() throws Exception {
        var packet =
                packet(
                        "PacketAvatarEscoffierSkillCookDataNotify",
                        new Class<?>[] {int.class, int.class},
                        1700000000,
                        10);
        assertEquals(7204, packet.getOpcode());
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookDataNotify"), packet.getData());
        assertEquals(1700000000, field(value, 6));
        assertEquals(10, field(value, 13));
    }

    @Test
    void notificationDoesNotReplaceZeroUsedCountWithTheLimit() throws Exception {
        var packet =
                packet(
                        "PacketAvatarEscoffierSkillCookDataNotify",
                        new Class<?>[] {int.class, int.class},
                        1700000000,
                        0);
        var value =
                DynamicMessage.parseFrom(
                        descriptor("AvatarEscoffierSkillCookDataNotify"), packet.getData());
        assertEquals(0, field(value, 13));
    }

    @Test
    void existingCookingAndSkillCookingResponsesDoNotCollide() {
        assertNotEquals(PacketOpcodes.PlayerCookRsp, PacketOpcodes.AvatarEscoffierSkillCookRsp);
        assertNotEquals(
                PacketOpcodes.AvatarEscoffierSkillCookDataNotify,
                PacketOpcodes.AvatarEscoffierSkillCookRsp);
    }
}
