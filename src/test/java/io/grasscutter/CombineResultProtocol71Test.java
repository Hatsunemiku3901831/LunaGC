package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.proto.CombineReqOuterClass.CombineReq;
import emu.grasscutter.net.proto.CombineRspOuterClass.CombineRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketCombineRsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Native CombineRsp parser and receiver multiplication, independent of generated field names. */
class CombineResultProtocol71Test {
    private PacketCombineRsp result(int units, int total) {
        return new PacketCombineRsp(
                CombineReq.newBuilder()
                        .setCombineId(11001)
                        .setCombineCount(units)
                        .setAvatarGuid(123)
                        .build(),
                List.of(ItemParam.newBuilder().setItemId(112002).setCount(units * 3).build()),
                List.of(ItemParam.newBuilder().setItemId(112003).setCount(total).build()),
                List.of(),
                List.of(),
                List.of());
    }

    private Map<Integer, List<ItemParam>> items(byte[] bytes) throws Exception {
        var input = CodedInputStream.newInstance(bytes);
        var items = new HashMap<Integer, List<ItemParam>>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if ((tag & 7) == 2)
                items.computeIfAbsent(tag >>> 3, k -> new ArrayList<>())
                        .add(ItemParam.parseFrom(input.readByteArray()));
            else input.skipField(tag);
        }
        return items;
    }

    @Test
    void requestScalarsKeepVerifiedNativeTags() throws Exception {
        var request = CombineReq.parseFrom(new byte[] {0x08, 100, 0x38, 42, 0x78, 3});
        assertEquals(100, request.getCombineId());
        assertEquals(42, request.getAvatarGuid());
        assertEquals(3, request.getCombineCount());
    }

    @Test
    void responseScalarsKeepVerifiedNativeTags() throws Exception {
        var data = result(3, 6).getData();
        var input = CodedInputStream.newInstance(data);
        var fields = new HashMap<Integer, Long>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if ((tag & 7) == 0) fields.put(tag >>> 3, input.readUInt64());
            else input.skipField(tag);
        }
        assertEquals(11001L, fields.get(14));
        assertEquals(3L, fields.get(15));
        assertEquals(123L, fields.get(4));
    }

    @Test
    void primaryItemsUseNativeFieldSeven() throws Exception {
        var fields = items(result(1, 2).getData());
        assertTrue(fields.containsKey(7));
        assertEquals(112003, fields.get(7).get(0).getItemId());
        assertTrue(fields.keySet().stream().allMatch(f -> f < 50000));
    }

    @Test
    void nativePerUnitMultiplicationMatchesActualTotal() throws Exception {
        var fields = items(result(3, 6).getData());
        assertEquals(
                2,
                fields.getOrDefault(7, List.of(ItemParam.getDefaultInstance())).get(0).getCount());
        assertEquals(6, fields.get(7).get(0).getCount() * 3);
    }

    @Test
    void unevenTotalUsesNativeAbsolutePrimaryList() throws Exception {
        var fields = items(result(3, 5).getData());
        assertEquals(
                5,
                fields.getOrDefault(6, List.of(ItemParam.getDefaultInstance())).get(0).getCount());
        assertFalse(fields.containsKey(7));
    }

    @Test
    void consumedItemsKeepActualTotalOnFieldTwelve() throws Exception {
        var fields = items(result(3, 6).getData());
        assertEquals(112002, fields.get(12).get(0).getItemId());
        assertEquals(9, fields.get(12).get(0).getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 9, 11})
    void everyRecoveredListExistsAtItsNativeTag(int tag) {
        var descriptor = CombineRsp.getDescriptor();
        var field = descriptor.findFieldByNumber(tag);
        assertNotNull(field);
        assertTrue(field.isRepeated());
        assertEquals(Descriptors.FieldDescriptor.JavaType.MESSAGE, field.getJavaType());
        assertEquals(ItemParam.getDescriptor().getFullName(), field.getMessageType().getFullName());
    }

    @Test
    void responseDescriptorContainsNoSyntheticTags() {
        assertTrue(
                CombineRsp.getDescriptor().getFields().stream()
                        .allMatch(f -> f.getNumber() < 50000));
    }

    @Test
    void errorPacketUsesRetcodeFieldTwoAndNoItems() throws Exception {
        var bytes = new PacketCombineRsp(Retcode.RET_FAIL_VALUE).getData();
        assertEquals(2, CodedInputStream.newInstance(bytes).readTag() >>> 3);
        assertTrue(items(bytes).isEmpty());
        assertNotEquals(0, CombineRsp.parseFrom(bytes).getRetcode());
    }
}
