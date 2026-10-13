package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Native writers/readers and codec initializers establish tags, not grouped-choice semantics. */
class UseItemProtocol71Test {
    private byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    void requestScalarTagsMatchTheNativeWriter() {
        var d = UseItemReq.getDescriptor();
        assertEquals(6, d.getFields().size());
        assertEquals(1, d.findFieldByName("is_enter_mp_dungeon_team").getNumber());
        assertEquals(3, d.findFieldByName("guid").getNumber());
        assertEquals(9, d.findFieldByName("count").getNumber());
        assertEquals(14, d.findFieldByName("target_guid").getNumber());
        assertEquals(15, d.findFieldByName("option_idx").getNumber());
    }

    @Test
    void responseScalarTagsMatchTheNativeParser() {
        var d = UseItemRsp.getDescriptor();
        assertEquals(6, d.getFields().size());
        assertEquals(1, d.findFieldByName("guid").getNumber());
        assertEquals(4, d.findFieldByName("retcode").getNumber());
        assertEquals(5, d.findFieldByName("item_id").getNumber());
        assertEquals(12, d.findFieldByName("target_guid").getNumber());
        assertEquals(14, d.findFieldByName("option_idx").getNumber());
    }

    @Test
    void choiceListsUseUInt32PackedCodecs() {
        var req = UseItemReq.getDescriptor().findFieldByName("_option_idx_list");
        var rsp = UseItemRsp.getDescriptor().findFieldByName("_option_idx_list");
        assertEquals(7, req.getNumber());
        assertEquals(9, rsp.getNumber());
        for (var field : List.of(req, rsp)) {
            assertEquals(FieldDescriptor.Type.UINT32, field.getType());
            assertTrue(field.isRepeated());
            assertTrue(field.isPacked());
        }
    }

    @Test
    void packedRequestPreservesAllFields() throws Exception {
        var req = UseItemReq.parseFrom(bytes("080118013a0302ac02480370047805"));
        assertTrue(req.getIsEnterMpDungeonTeam());
        assertEquals(1L, req.getGuid());
        assertEquals(3, req.getCount());
        assertEquals(4L, req.getTargetGuid());
        assertEquals(5, req.getOptionIdx());
        assertEquals(List.of(2, 300), req.getOptionIdxListList());
    }

    @Test
    void unpackedRequestChoicesAreAccepted() throws Exception {
        assertEquals(
                List.of(2, 300), UseItemReq.parseFrom(bytes("380238ac02")).getOptionIdxListList());
    }

    @Test
    void requestEncoderUsesTheNativeTags() {
        var req =
                UseItemReq.newBuilder()
                        .setIsEnterMpDungeonTeam(true)
                        .setGuid(1)
                        .setCount(3)
                        .setTargetGuid(4)
                        .setOptionIdx(5)
                        .addAllOptionIdxList(List.of(2, 300))
                        .build();
        assertArrayEquals(bytes("080118013a0302ac02480370047805"), req.toByteArray());
    }

    @Test
    void packedResponsePreservesAllFields() throws Exception {
        var rsp = UseItemRsp.parseFrom(bytes("0801200628074a0302ac0260047005"));
        assertEquals(1L, rsp.getGuid());
        assertEquals(6, rsp.getRetcode());
        assertEquals(7, rsp.getItemId());
        assertEquals(4L, rsp.getTargetGuid());
        assertEquals(5, rsp.getOptionIdx());
        assertEquals(List.of(2, 300), rsp.getOptionIdxListList());
    }

    @Test
    void unpackedResponseChoicesAreAccepted() throws Exception {
        assertEquals(
                List.of(2, 300), UseItemRsp.parseFrom(bytes("480248ac02")).getOptionIdxListList());
    }

    @Test
    void unsignedChoiceAndSignedFailureKeepTheirWireTypes() throws Exception {
        assertEquals(-1, UseItemReq.parseFrom(bytes("78ffffffff0f")).getOptionIdx());
        assertEquals(-1, UseItemRsp.parseFrom(bytes("20ffffffffffffffffff01")).getRetcode());
        assertEquals(-1L, UseItemRsp.parseFrom(bytes("08ffffffffffffffffff01")).getGuid());
    }

    @Test
    void opcodesMatchNativeConstantGetters() {
        assertEquals(25681, PacketOpcodes.UseItemReq);
        assertEquals(25449, PacketOpcodes.UseItemRsp);
    }
}
