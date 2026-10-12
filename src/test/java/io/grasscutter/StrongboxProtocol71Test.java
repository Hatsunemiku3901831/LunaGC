package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ReliquaryDecomposeReqOuterClass.ReliquaryDecomposeReq;
import emu.grasscutter.net.proto.ReliquaryDecomposeRspOuterClass.ReliquaryDecomposeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketReliquaryDecomposeRsp;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Wire vectors from the fingerprint-checked native writer, reader and repeated-field codecs. */
class StrongboxProtocol71Test {
    private byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    void requestFieldsMatchTheNativeWireSlots() {
        var descriptor = ReliquaryDecomposeReq.getDescriptor();
        assertEquals(3, descriptor.getFields().size());
        assertEquals(2, descriptor.findFieldByName("config_id").getNumber());
        assertEquals(15, descriptor.findFieldByName("target_count").getNumber());
        var guids = descriptor.findFieldByName("guid_list");
        assertEquals(14, guids.getNumber());
        assertEquals(FieldDescriptor.Type.UINT64, guids.getType());
        assertTrue(guids.isRepeated());
        assertTrue(guids.isPacked());
    }

    @Test
    void responseFieldsMatchTheNativeWireSlots() {
        var descriptor = ReliquaryDecomposeRsp.getDescriptor();
        assertEquals(2, descriptor.getFields().size());
        var error = descriptor.findFieldByName("retcode");
        assertEquals(14, error.getNumber());
        assertEquals(FieldDescriptor.Type.INT32, error.getType());
        var guids = descriptor.findFieldByName("guid_list");
        assertEquals(8, guids.getNumber());
        assertEquals(FieldDescriptor.Type.UINT64, guids.getType());
        assertTrue(guids.isRepeated());
        assertTrue(guids.isPacked());
    }

    @Test
    void packedNativeRequestPreservesScalarsAndGuids() throws Exception {
        var request = ReliquaryDecomposeReq.parseFrom(bytes("1028720301ac027802"));
        assertEquals(40, request.getConfigId());
        assertEquals(2, request.getTargetCount());
        assertEquals(List.of(1L, 300L), request.getGuidListList());
    }

    @Test
    void unpackedRequestGuidsRemainAccepted() throws Exception {
        var request = ReliquaryDecomposeReq.parseFrom(bytes("1028700170ac027802"));
        assertEquals(40, request.getConfigId());
        assertEquals(2, request.getTargetCount());
        assertEquals(List.of(1L, 300L), request.getGuidListList());
    }

    @Test
    void generatedRequestUsesTheNativePackedCodecTag() {
        var request =
                ReliquaryDecomposeReq.newBuilder()
                        .setConfigId(40)
                        .setTargetCount(2)
                        .addAllGuidList(List.of(1L, 300L))
                        .build();
        assertArrayEquals(bytes("1028720301ac027802"), request.toByteArray());
    }

    @Test
    void packedNativeResponseGuidsAreParsed() throws Exception {
        var response = ReliquaryDecomposeRsp.parseFrom(bytes("420301ac02"));
        assertEquals(0, response.getRetcode());
        assertEquals(List.of(1L, 300L), response.getGuidListList());
    }

    @Test
    void unpackedNativeResponseGuidsAreParsed() throws Exception {
        var response = ReliquaryDecomposeRsp.parseFrom(bytes("400140ac02"));
        assertEquals(0, response.getRetcode());
        assertEquals(List.of(1L, 300L), response.getGuidListList());
    }

    @Test
    void successPacketUsesTheNativeGuidTag() {
        var packet = new PacketReliquaryDecomposeRsp(List.of(1L, 300L));
        assertEquals(20871, packet.getOpcode());
        assertArrayEquals(bytes("420301ac02"), packet.getData());
    }

    @Test
    void parameterErrorPacketUsesTheNativeRetcodeTag() {
        var packet = new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR);
        assertEquals(20871, packet.getOpcode());
        assertArrayEquals(bytes("70bb05"), packet.getData());
    }

    @Test
    void unsigned64GuidKeepsAllBits() throws Exception {
        var nativeBytes = bytes("420affffffffffffffffff01");
        var reply = ReliquaryDecomposeRsp.parseFrom(nativeBytes);
        assertEquals(List.of(-1L), reply.getGuidListList());
        assertArrayEquals(nativeBytes, new PacketReliquaryDecomposeRsp(List.of(-1L)).getData());
    }

    @Test
    void negativeRetcodeUsesSignedInt32Semantics() throws Exception {
        var reply = ReliquaryDecomposeRsp.parseFrom(bytes("70ffffffffffffffffff01"));
        assertEquals(-1, reply.getRetcode());
        assertTrue(reply.getGuidListList().isEmpty());
    }

    @Test
    void opcodesMatchTheNativeConstantGetters() {
        assertEquals(24936, PacketOpcodes.ReliquaryDecomposeReq);
        assertEquals(20871, PacketOpcodes.ReliquaryDecomposeRsp);
    }
}
