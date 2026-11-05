package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.DoSetPlayerBornDataNotifyOuterClass.DoSetPlayerBornDataNotify;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp;
import emu.grasscutter.server.packet.send.PacketDoSetPlayerBornDataNotify;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent B-02d native/protoc vectors, not same-schema round trips. */
class Protocol72BornCompatibilityTest {
    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    @Test
    void nativeRequestAndUnsignedBoundaryMatchGeneratedProductionSchema() throws Exception {
        var nativeRequest = hex("22064165746865725885ade204");
        var request = SetPlayerBornDataReq.parseFrom(nativeRequest);
        assertEquals(10000005, request.getAvatarId());
        assertEquals("Aether", request.getNickName());
        assertArrayEquals(nativeRequest, SetPlayerBornDataReq.newBuilder()
                .setAvatarId(10000005).setNickName("Aether").build().toByteArray());
        var maximum = SetPlayerBornDataReq.parseFrom(hex("22017858ffffffff0f"));
        assertEquals(4294967295L, Integer.toUnsignedLong(maximum.getAvatarId()));
        assertArrayEquals(hex("22017858ffffffff0f"), SetPlayerBornDataReq.newBuilder()
                .setAvatarId(-1).setNickName("x").build().toByteArray());
        assertArrayEquals(new byte[0], SetPlayerBornDataReq.getDefaultInstance().toByteArray());
    }

    @Test
    void unicodeNulAndUnknownFieldsKeepTheirWireMeaning() throws Exception {
        var unicode = hex("220be69785e4babaf09f9a80005887ade204");
        var request = SetPlayerBornDataReq.parseFrom(unicode);
        assertEquals("旅人🚀\0", request.getNickName());
        assertEquals(10000007, request.getAvatarId());
        assertArrayEquals(unicode, request.toByteArray());
        var legacy = SetPlayerBornDataReq.parseFrom(hex("1085ade204220178"));
        assertEquals(0, legacy.getAvatarId());
        assertTrue(legacy.getUnknownFields().hasField(2));
        // Unknown uint64 max, nested bytes and repeated varints are not new declared fields.
        var unknown = SetPlayerBornDataRsp.parseFrom(hex("780178840108ffffffffffffffffff011202080118011802"));
        assertEquals(132, unknown.getRetcode());
        assertEquals(2, unknown.getUnknownFields().getField(3).getVarintList().size());
        var wrongWire = SetPlayerBornDataRsp.parseFrom(hex("78747a0178"));
        assertEquals(116, wrongWire.getRetcode());
        assertTrue(wrongWire.getUnknownFields().hasField(15));
        assertEquals(0, SetPlayerBornDataRsp.parseFrom(hex("308401")).getRetcode());
    }

    @ParameterizedTest
    @CsvSource({"0,''", "1,7801", "116,7874", "132,788401", "-1,78ffffffffffffffffff01",
            "-2147483648,7880808080f8ffffffff01", "2147483647,78ffffffff07"})
    void responseErrorsReallyEncodeInt32Tag15(int retcode, String wire) throws Exception {
        var packet = new PacketSetPlayerBornDataRsp(retcode);
        assertEquals(414, packet.getOpcode());
        assertArrayEquals(hex(wire), packet.getData());
        assertEquals(retcode, SetPlayerBornDataRsp.parseFrom(hex(wire)).getRetcode());
    }

    @Test
    void emptyNotifyAndSuccessHaveDistinctVerifiedOpcodesAndFrames() {
        assertEquals(5008, PacketOpcodes.DoSetPlayerBornDataNotify);
        assertEquals(8541, PacketOpcodes.SetPlayerBornDataReq);
        assertEquals(414, PacketOpcodes.SetPlayerBornDataRsp);
        assertEquals("DoSetPlayerBornDataNotify", PacketOpcodesUtils.getOpcodeName(5008));
        assertEquals("SetPlayerBornDataReq", PacketOpcodesUtils.getOpcodeName(8541));
        assertEquals("SetPlayerBornDataRsp", PacketOpcodesUtils.getOpcodeName(414));
        assertTrue(DoSetPlayerBornDataNotify.getDescriptor().getFields().isEmpty());
        assertArrayEquals(hex("4567139000000000000089ab"), new PacketDoSetPlayerBornDataNotify().build());
        assertArrayEquals(hex("4567019e00000000000089ab"), new PacketSetPlayerBornDataRsp(0).build());
        assertArrayEquals(hex("4567019e00000000000378840189ab"), new PacketSetPlayerBornDataRsp(132).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0880", "120378", "00", "0880808080808080808080"})
    void malformedPayloadsAreRejectedByTheProductionParsers(String wire) {
        assertThrows(InvalidProtocolBufferException.class, () -> SetPlayerBornDataReq.parseFrom(hex(wire)));
        assertThrows(InvalidProtocolBufferException.class, () -> SetPlayerBornDataRsp.parseFrom(hex(wire)));
        assertThrows(InvalidProtocolBufferException.class, () -> DoSetPlayerBornDataNotify.parseFrom(hex(wire)));
    }

    @Test
    void invalidUtf8IsNotAcceptedAsANickname() {
        assertThrows(InvalidProtocolBufferException.class,
                () -> SetPlayerBornDataReq.parseFrom(hex("2201ff")));
    }
}
