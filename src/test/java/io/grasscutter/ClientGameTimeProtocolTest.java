package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** 7.1 native getters, request writer and response reader; not the recovered old descriptors. */
class ClientGameTimeProtocolTest {
    byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    void requestMatchesCurrentNativeWriter() throws Exception {
        var raw = bytes("28ac02382d6001");
        var req = ClientSetGameTimeReq.parseFrom(raw);
        assertEquals(300, req.getGameTime());
        assertEquals(45, req.getClientGameTime());
        assertTrue(req.getIsForceSet());
        assertArrayEquals(raw, req.toByteArray());
    }

    @Test
    void responseMatchesCurrentNativeReader() throws Exception {
        var raw = bytes("108101202d30ac023a04ac02ad02");
        var rsp = ClientSetGameTimeRsp.parseFrom(raw);
        assertEquals(129, rsp.getRetcode());
        assertEquals(300, rsp.getGameTime());
        assertEquals(45, rsp.getClientGameTime());
        assertEquals(List.of(300, 301), rsp.getGameTimeLockedQuestListList());
        assertArrayEquals(raw, rsp.toByteArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3a04ac02ad02", "38ac0238ad02"})
    void lockedQuestListAcceptsBothNativeWireForms(String hex) throws Exception {
        assertEquals(
                List.of(300, 301),
                ClientSetGameTimeRsp.parseFrom(bytes(hex)).getGameTimeLockedQuestListList());
    }

    @Test
    void unsignedTimesRetainAllThirtyTwoBits() throws Exception {
        var req = ClientSetGameTimeReq.parseFrom(bytes("28ffffffff0f38ffffffff0f"));
        assertEquals(4294967295L, Integer.toUnsignedLong(req.getGameTime()));
        assertEquals(4294967295L, Integer.toUnsignedLong(req.getClientGameTime()));
    }

    @Test
    void opcodesMatchCurrentLiteralGetters() {
        assertEquals(9122, PacketOpcodes.ClientSetGameTimeReq);
        assertEquals(25217, PacketOpcodes.ClientSetGameTimeRsp);
    }
}
