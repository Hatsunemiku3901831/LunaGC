package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeQueueManipulateTypeOuterClass.ForgeQueueManipulateType;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeAllForgeOutputRspOuterClass.TakeAllForgeOutputRsp;
import emu.grasscutter.server.packet.send.PacketForgeQueueManipulateRsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

/** Byte samples follow the current native parser, independent of generated descriptors. */
class ForgeResultProtocol71Test {
    @Test
    void takeAllFieldsUseTheCurrentNativeTags() {
        var fields = TakeAllForgeOutputRsp.getDescriptor();
        assertEquals(2, fields.findFieldByName("retcode").getNumber());
        assertEquals(5, fields.findFieldByName("return_item_list").getNumber());
        assertEquals(7, fields.findFieldByName("extra_output_item_list").getNumber());
        assertEquals(9, fields.findFieldByName("fail_retcode_list").getNumber());
        assertEquals(12, fields.findFieldByName("output_item_list").getNumber());
    }

    @Test
    void nativeTakeAllSampleDecodesAllListsAndFailureCodes() throws Exception {
        // Independently constructed from reader 0x1527708d0; not a captured packet.
        byte[] sample = {
            16, 1, 42, 4, 8, 11, 16, 2, 58, 4, 8, 22, 16, 3, 74, 2, 1, 2, 98, 4, 8, 33, 16, 4
        };
        var reply = TakeAllForgeOutputRsp.parseFrom(sample);
        assertEquals(1, reply.getRetcode());
        assertEquals(11, reply.getReturnItemList(0).getItemId());
        assertEquals(2, reply.getReturnItemList(0).getCount());
        assertEquals(22, reply.getExtraOutputItemList(0).getItemId());
        assertEquals(3, reply.getExtraOutputItemList(0).getCount());
        assertEquals(List.of(1, 2), reply.getFailRetcodeListList());
        assertEquals(33, reply.getOutputItemList(0).getItemId());
        assertEquals(4, reply.getOutputItemList(0).getCount());
    }

    private static GameItem item(int id, int count, long guid) throws Exception {
        var item = new GameItem();
        item.setItemId(id);
        item.setCount(count);
        var field = GameItem.class.getDeclaredField("guid");
        field.setAccessible(true);
        field.setLong(item, guid);
        return item;
    }

    @Test
    void nativeOutputTagIsTwo() {
        assertEquals(
                2,
                ForgeQueueManipulateRsp.getDescriptor()
                        .findFieldByName("output_item_list")
                        .getNumber());
    }

    @Test
    void nativeRefundTagIsEleven() {
        assertEquals(
                11,
                ForgeQueueManipulateRsp.getDescriptor()
                        .findFieldByName("return_item_list")
                        .getNumber());
    }

    @Test
    void nativeExtraTypeAndErrorTagsRemainValid() {
        var descriptor = ForgeQueueManipulateRsp.getDescriptor();
        assertEquals(3, descriptor.findFieldByName("extra_output_item_list").getNumber());
        assertEquals(6, descriptor.findFieldByName("retcode").getNumber());
        assertEquals(9, descriptor.findFieldByName("manipulate_type").getNumber());
    }

    @Test
    void independentNativeSampleDecodesAllItemLists() throws Exception {
        byte[] nativeSample = {
            0x12, 8, 8, 11, 16, 2, 24, 1, 32, 91, 0x1a, 6, 8, 12, 16, 3, 32, 92, 0x5a, 6, 8, 13, 16,
            4, 32, 93, 0x30, 1, 0x48, 1
        };
        var result = ForgeQueueManipulateRsp.parseFrom(nativeSample);
        assertEquals(1, result.getOutputItemListCount());
        assertEquals(11, result.getOutputItemList(0).getItemId());
        assertEquals(2, result.getOutputItemList(0).getCount());
        assertEquals(91, result.getOutputItemList(0).getGuid());
        assertTrue(result.getOutputItemList(0).getIsNew());
        assertEquals(12, result.getExtraOutputItemList(0).getItemId());
        assertEquals(13, result.getReturnItemList(0).getItemId());
        assertEquals(1, result.getRetcode());
        assertEquals(1, result.getManipulateTypeValue());
    }

    @Test
    void packetPopulatesOutputRefundAndExtraWithItemHints() throws Exception {
        var packet =
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT,
                        List.of(item(11, 2, 91)),
                        List.of(item(13, 4, 93)),
                        List.of(item(12, 3, 92)));
        assertEquals(PacketOpcodes.ForgeQueueManipulateRsp, packet.getOpcode());
        var result = ForgeQueueManipulateRsp.parseFrom(packet.getData());
        assertEquals(11, result.getOutputItemList(0).getItemId());
        assertEquals(2, result.getOutputItemList(0).getCount());
        assertEquals(91, result.getOutputItemList(0).getGuid());
        assertEquals(13, result.getReturnItemList(0).getItemId());
        assertEquals(12, result.getExtraOutputItemList(0).getItemId());
    }

    @Test
    void populatedPacketUsesIndependentNativeBytes() throws Exception {
        var packet =
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SVR_ERROR,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_STOP_FORGE,
                        List.of(item(11, 2, 91)),
                        List.of(item(13, 4, 93)),
                        List.of(item(12, 3, 92)));
        assertArrayEquals(
                new byte[] {
                    0x12, 6, 8, 11, 16, 2, 32, 91, 0x1a, 6, 8, 12, 16, 3, 32, 92, 0x30, 1, 0x48, 1,
                    0x5a, 6, 8, 13, 16, 4, 32, 93
                },
                packet.getData());
    }

    @Test
    void sharedItemHintScalarTagsMatchCurrentReader() throws Exception {
        byte[] sample = {8, 11, 16, 2, 24, 1, 32, 91};
        var hint = ItemHint.parseFrom(sample);
        assertEquals(11, hint.getItemId());
        assertEquals(2, hint.getCount());
        assertTrue(hint.getIsNew());
        assertEquals(91, hint.getGuid());
        assertArrayEquals(sample, hint.toByteArray());
    }

    @Test
    void requestMatchesCurrentNativeWriter() throws Exception {
        assertArrayEquals(
                new byte[] {16, 1, 120, 3},
                ForgeQueueManipulateReq.newBuilder()
                        .setManipulateTypeValue(1)
                        .setForgeQueueId(3)
                        .build()
                        .toByteArray());
        var request = ForgeQueueManipulateReq.parseFrom(new byte[] {120, 3, 16, 1});
        assertEquals(3, request.getForgeQueueId());
        assertEquals(1, request.getManipulateTypeValue());
    }

    @Test
    void emptyFailureDoesNotAddItemLists() throws Exception {
        var packet =
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SVR_ERROR,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT,
                        List.of(),
                        List.of(),
                        List.of());
        assertArrayEquals(new byte[] {0x30, 1}, packet.getData());
    }

    @Test
    void repeatedOutputEntriesPreserveDistinctWeaponGuids() throws Exception {
        var packet =
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT,
                        List.of(item(11, 1, 91), item(11, 1, 92)),
                        List.of(),
                        List.of());
        var result = ForgeQueueManipulateRsp.parseFrom(packet.getData());
        assertEquals(2, result.getOutputItemListCount());
        assertEquals(91, result.getOutputItemList(0).getGuid());
        assertEquals(92, result.getOutputItemList(1).getGuid());
    }

    @Test
    void unknownEnumErrorCanBeEncodedWithoutThrowing() throws Exception {
        var packet =
                assertDoesNotThrow(
                        () ->
                                new PacketForgeQueueManipulateRsp(
                                        Retcode.RET_SVR_ERROR,
                                        ForgeQueueManipulateType.UNRECOGNIZED,
                                        List.of(),
                                        List.of(),
                                        List.of()));
        var result = ForgeQueueManipulateRsp.parseFrom(packet.getData());
        assertEquals(1, result.getRetcode());
        assertEquals(-1, result.getManipulateTypeValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void unsignedHintValuesRoundTripWithGuid(int value) throws Exception {
        var hint = ItemHint.newBuilder().setItemId(value).setCount(value).setGuid(-1L).build();
        var result = ItemHint.parseFrom(hint.toByteArray());
        assertEquals(value, result.getItemId());
        assertEquals(value, result.getCount());
        assertEquals(-1L, result.getGuid());
    }
}
