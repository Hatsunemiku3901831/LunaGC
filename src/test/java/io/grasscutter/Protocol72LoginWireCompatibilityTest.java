package io.grasscutter;

import static com.google.protobuf.Descriptors.FieldDescriptor.Type.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.net.proto.PingRspOuterClass.PingRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerGetPlayerTokenReq;
import emu.grasscutter.server.packet.recv.HandlerPingReq;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Target-derived, synthetic vectors; these are not captures or client acceptance. */
class Protocol72LoginWireCompatibilityTest {
    private record Field(int tag, FieldDescriptor.Type type, String name, int nativeOffset) {}

    // Independent tables from token-wire-observations.json, wire-observations.json and
    // mappings.json. Only verified business names replace the original client names.
    private static final Field[] TOKEN = {
        new Field(1, STRING, "CANLDLBEODF", 40),
        new Field(2, STRING, "account_token", 64),
        new Field(3, UINT32, "channel_id", 176),
        new Field(4, UINT32, "JFEGKNOHCMM", 172),
        new Field(5, STRING, "IDBJELLONNH", 104),
        new Field(6, STRING, "ANIBEAMGKDP", 32),
        new Field(7, UINT32, "GKODOLODOGC", 168),
        new Field(8, STRING, "BGEAPNNMOCP", 72),
        new Field(9, STRING, "account_uid", 24),
        new Field(10, UINT32, "sub_channel_id", 180),
        new Field(11, BOOL, "is_guest", 196),
        new Field(12, UINT32, "PJAJDHBBMGB", 160),
        new Field(13, STRING, "GCBLHKIJHPC", 96),
        new Field(14, STRING, "FKNBPCAPKCJ", 88),
        new Field(15, UINT32, "account_type", 164),
        new Field(83, STRING, "PIALLCNIKEE", 120),
        new Field(561, UINT32, "DCHAEKJAMBA", 192),
        new Field(623, STRING, "client_rand_key", 144),
        new Field(645, UINT32, "FGJNONFBDHO", 156),
        new Field(793, UINT32, "JIHMKEJCCGA", 184),
        new Field(884, STRING, "NGHMHALIAIO", 128),
        new Field(924, STRING, "MHNPBDMNCAA", 56),
        new Field(965, STRING, "OEDFOPBKADI", 112),
        new Field(1184, UINT32, "key_id", 152),
        new Field(1206, UINT32, "PJHOCPFACEK", 188),
        new Field(1283, STRING, "HNNICOEFLAK", 48),
        new Field(1300, STRING, "JJEGEAOIFOC", 80),
        new Field(1730, STRING, "IJANGEDLOMK", 136)
    };
    private static final Field[] HEADER = {
        new Field(3, UINT32, "client_sequence_id", 0),
        new Field(6, UINT64, "sent_ms", 0),
        new Field(7, UINT32, "POAOKGCEJPF", 0),
        new Field(8, UINT32, "KNKMLHHGPPE", 0),
        new Field(9, UINT32, "BNHLJEGKGEM", 0),
        new Field(10, UINT32, "FKKOHHCLCPH", 0)
    };
    private static final Field[] PING_REQUEST = {
        new Field(1, UINT64, "LBEAJJBNBIF", 0),
        new Field(2, UINT32, "BFNKMJBHCKJ", 0),
        new Field(3, BYTES, "IMEDCDCPICN", 0),
        new Field(5, DOUBLE, "MHCBMOHFBCK", 0),
        new Field(9, BYTES, "BAHKGFLONLL", 0),
        new Field(10, FLOAT, "HDLNEABIBJF", 0),
        new Field(13, UINT32, "OILKPHNHCHB", 0),
        new Field(14, UINT32, "AMLHAGPHMHN", 0)
    };
    private static final Field[] PING_RESPONSE = {
        new Field(1, UINT32, "AMLHAGPHMHN", 0),
        new Field(6, UINT32, "OILKPHNHCHB", 0),
        new Field(11, INT32, "LDLIEKOAFBP", 0)
    };

    // Offline target writer on synthetic object values f_<offset>, 0x12340000 + offset,
    // and true. The string and integer helpers were modeled; no client process was run.
    private static final byte[] TOKEN_NATIVE = hex(
            "0a04665f34301204665f363418b081d0910120ac81d091012a05665f313034"
            + "3204665f333238a881d091014204665f37324a04665f323450b481d091015801"
            + "60a081d091016a04665f39367204665f383878a481d091019a0505665f313230"
            + "8823c081d09101fa2605665f313434a8289c81d09101c831b881d09101"
            + "a23705665f313238e23904665f3536aa3c05665f313132804a9881d09101"
            + "b04bbc81d091019a5004665f3438a25104665f3830926c05665f313336");
    // Independently authored from the target writer and checked with research protoc.
    private static final byte[] HEAD_WIRE = hex(
            "18b424308180808010387f40800148ac0250ffffffff0f");
    private static final byte[] PING_WIRE = hex(
            "088180808010103c1a0200ff29000000000000f83f4a0241425500002040"
            + "6880e2cfaa0670ac02");

    @Test
    void descriptorsMatchEveryObservedTagTypeNameAndCardinality() {
        assertDescriptor(GetPlayerTokenReq.getDescriptor(), TOKEN);
        assertDescriptor(PacketHead.getDescriptor(), HEADER);
        assertDescriptor(PingReq.getDescriptor(), PING_REQUEST);
        assertDescriptor(PingRsp.getDescriptor(), PING_RESPONSE);
    }

    @Test
    void offlineNativeTokenFixturePopulatesAllTwentyEightFields() throws Exception {
        var request = GetPlayerTokenReq.parseFrom(TOKEN_NATIVE);
        var dynamic = DynamicMessage.parseFrom(GetPlayerTokenReq.getDescriptor(), TOKEN_NATIVE);
        for (var spec : TOKEN) {
            Object expected = switch (spec.type()) {
                case STRING -> "f_" + spec.nativeOffset();
                case UINT32 -> 0x12340000 + spec.nativeOffset();
                case BOOL -> true;
                default -> throw new AssertionError(spec);
            };
            assertEquals(expected, field(request, spec.tag()), "generated tag " + spec.tag());
            assertEquals(expected, field(dynamic, spec.tag()), "dynamic tag " + spec.tag());
        }
        assertTrue(request.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(TOKEN_NATIVE, request.toByteArray());
    }

    @Test
    void tokenCredentialsAndUnsignedBoundariesUseVerifiedTargetTags() throws Exception {
        var wire = hex("12057400e4b8ad18ffffffff0f4a047569640050ffffffff0f5801"
                + "78ffffffff0ffa2607736565642b3d3d804a04");
        var request = GetPlayerTokenReq.parseFrom(wire);
        assertEquals("t\0中", request.getAccountToken());
        assertEquals("uid\0", request.getAccountUid());
        assertEquals(4294967295L, Integer.toUnsignedLong(request.getChannelId()));
        assertEquals(4294967295L, Integer.toUnsignedLong(request.getSubChannelId()));
        assertEquals(4294967295L, Integer.toUnsignedLong(request.getAccountType()));
        assertTrue(request.getIsGuest());
        assertEquals("seed+==", request.getClientRandKey());
        assertEquals(4, request.getKeyId());
        assertArrayEquals(wire, GetPlayerTokenReq.newBuilder()
                .setAccountToken("t\0中").setChannelId(-1).setAccountUid("uid\0")
                .setSubChannelId(-1).setIsGuest(true).setAccountType(-1)
                .setClientRandKey("seed+==").setKeyId(4).build().toByteArray());
    }

    @Test
    void tokenDefaultsDuplicatesWrongWireAndUnknownTagsAreDistinct() throws Exception {
        assertArrayEquals(new byte[0], GetPlayerTokenReq.getDefaultInstance().toByteArray());
        var request = GetPlayerTokenReq.parseFrom(hex("120161120162107fc03e01c03e02fa7f020801"));
        assertEquals("b", request.getAccountToken());
        assertEquals(List.of(127L), request.getUnknownFields().getField(2).getVarintList());
        assertEquals(List.of(1L, 2L), request.getUnknownFields().getField(1000).getVarintList());
        assertEquals(ByteString.copyFrom(hex("0801")),
                request.getUnknownFields().getField(2047).getLengthDelimitedList().get(0));
        assertEquals("", GetPlayerTokenReq.parseFrom(hex("1201611200")).getAccountToken());
        assertThrows(InvalidProtocolBufferException.class, () -> GetPlayerTokenReq.parseFrom(hex("1201ff")));
    }

    @Test
    void targetPacketHeadAndPingVectorsContainAllNonzeroFields() throws Exception {
        var head = PacketHead.parseFrom(HEAD_WIRE);
        assertEquals(4660, head.getClientSequenceId());
        assertEquals(4294967297L, head.getSentMs());
        assertEquals(127, field(head, 7));
        assertEquals(128, field(head, 8));
        assertEquals(300, field(head, 9));
        assertEquals(4294967295L, Integer.toUnsignedLong((int) field(head, 10)));
        assertArrayEquals(HEAD_WIRE, head.toByteArray());

        var ping = PingReq.parseFrom(PING_WIRE);
        assertEquals(4294967297L, field(ping, 1));
        assertEquals(60, field(ping, 2));
        assertEquals(ByteString.copyFrom(hex("00ff")), field(ping, 3));
        assertEquals(1.5d, field(ping, 5));
        assertEquals(ByteString.copyFromUtf8("AB"), field(ping, 9));
        assertEquals(2.5f, field(ping, 10));
        assertEquals(1700000000, field(ping, 13));
        assertEquals(300, field(ping, 14));
        assertArrayEquals(PING_WIRE, ping.toByteArray());
    }

    @Test
    void unsigned64And32MaximumsAndRepeatedFieldsRemainSeparate() throws Exception {
        var ping = PingReq.parseFrom(hex("08ffffffffffffffffff0110ffffffff0f"
                + "68ffffffff0f70ffffffff0f"));
        assertEquals("18446744073709551615", Long.toUnsignedString((long) field(ping, 1)));
        assertEquals(4294967295L, Integer.toUnsignedLong((int) field(ping, 2)));
        assertEquals(4294967295L, Integer.toUnsignedLong((int) field(ping, 13)));
        assertEquals(4294967295L, Integer.toUnsignedLong((int) field(ping, 14)));
        var head = PacketHead.parseFrom(hex("180118021a0178800109"));
        assertEquals(2, head.getClientSequenceId());
        assertEquals(ByteString.copyFromUtf8("x"), head.getUnknownFields()
                .getField(3).getLengthDelimitedList().get(0));
        assertEquals(List.of(9L), head.getUnknownFields().getField(16).getVarintList());
        assertArrayEquals(new byte[0], PingReq.getDefaultInstance().toByteArray());
        assertArrayEquals(new byte[0], PingRsp.getDefaultInstance().toByteArray());
        assertArrayEquals(new byte[0], PacketHead.getDefaultInstance().toByteArray());
    }

    @Test
    void pingResponseUsesSignedInt32AndIndependentTargetWire() throws Exception {
        var wire = hex("08b4243080e2cfaa0658ffffffffffffffffff01");
        var response = PingRsp.parseFrom(wire);
        assertEquals(4660, field(response, 1));
        assertEquals(1700000000, field(response, 6));
        assertEquals(-1, field(response, 11));
        assertArrayEquals(wire, response.toByteArray());
        assertEquals(Integer.MIN_VALUE, field(PingRsp.parseFrom(hex("5880808080f8ffffffff01")), 11));
        assertEquals(Integer.MAX_VALUE, field(PingRsp.parseFrom(hex("58ffffffff07")), 11));
        var builder = PingRsp.newBuilder();
        builder.setField(PingRsp.getDescriptor().findFieldByNumber(11), -1);
        assertArrayEquals(hex("58ffffffffffffffffff01"), builder.build().toByteArray());
    }

    @Test
    void handlersBindVerifiedOpcodesAndEchoTheTwoSharedRawPingFields() throws Exception {
        assertEquals(24604, PacketOpcodes.GetPlayerTokenReq);
        assertEquals(26605, PacketOpcodes.PingReq);
        assertEquals(25763, PacketOpcodes.PingRsp);
        assertTrue(PacketOpcodes.LCNLHAOBBFE < 0);
        assertEquals("GetPlayerTokenReq", PacketOpcodesUtils.getOpcodeName(24604));
        assertEquals("PingReq", PacketOpcodesUtils.getOpcodeName(26605));
        assertEquals("PingRsp", PacketOpcodesUtils.getOpcodeName(25763));
        assertEquals(24604, HandlerGetPlayerTokenReq.class.getAnnotation(Opcodes.class).value());
        assertEquals(26605, HandlerPingReq.class.getAnnotation(Opcodes.class).value());

        // The shared field identities are known; their client_time/seq names are candidates.
        Grasscutter.getConfig(); // Initialize the application before Configuration's static fields.
        var session = new CapturingSession();
        new HandlerPingReq().handle(session, hex("18b424"), hex("6880e2cfaa0670ac02"));
        assertNotNull(session.sent);
        assertEquals(25763, session.sent.getOpcode());
        assertArrayEquals(hex("08ac023080e2cfaa06"), session.sent.getData());
        assertEquals(4660, PacketHead.parseFrom(session.sent.getHeader()).getClientSequenceId());
        assertEquals(1700000000, session.getClientTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "0880", "0a0378", "0880808080808080808080",
            "29000000000000f8", "55000020"})
    void malformedInputIsRejectedForAllFourMessages(String value) {
        var wire = hex(value);
        assertThrows(InvalidProtocolBufferException.class, () -> GetPlayerTokenReq.parseFrom(wire));
        assertThrows(InvalidProtocolBufferException.class, () -> PacketHead.parseFrom(wire));
        assertThrows(InvalidProtocolBufferException.class, () -> PingReq.parseFrom(wire));
        assertThrows(InvalidProtocolBufferException.class, () -> PingRsp.parseFrom(wire));
    }

    private static void assertDescriptor(Descriptor descriptor, Field[] expected) {
        assertEquals(expected.length, descriptor.getFields().size(), descriptor.getName());
        for (var spec : expected) {
            var actual = descriptor.findFieldByNumber(spec.tag());
            assertNotNull(actual, descriptor.getName() + " tag " + spec.tag());
            assertEquals(spec.type(), actual.getType(), descriptor.getName() + " tag " + spec.tag());
            assertEquals(spec.name(), actual.getName(), descriptor.getName() + " tag " + spec.tag());
            assertFalse(actual.isRepeated(), descriptor.getName() + " tag " + spec.tag());
        }
    }

    private static Object field(Message message, int tag) {
        var descriptor = message.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(descriptor, "missing tag " + tag);
        return message.getField(descriptor);
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static final class CapturingSession extends GameSession {
        private BasePacket sent;

        CapturingSession() {
            super(null);
        }

        @Override
        public void send(BasePacket packet) {
            sent = packet;
        }
    }
}
