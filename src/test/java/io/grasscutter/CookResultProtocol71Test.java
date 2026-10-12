package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass.CookRecipeData;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PlayerCookRspOuterClass.PlayerCookRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketPlayerCookRsp;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Verified native parser slots: extra field 10, main field 11, recipe field 13. */
class CookResultProtocol71Test {
    private PacketPlayerCookRsp packet(List<ItemParamData> main, List<ItemParamData> extra) {
        return assertDoesNotThrow(
                () ->
                        (PacketPlayerCookRsp)
                                PacketPlayerCookRsp.class
                                        .getConstructor(
                                                List.class,
                                                List.class,
                                                int.class,
                                                int.class,
                                                int.class,
                                                int.class)
                                        .newInstance(main, extra, 3, 2, 1001, 5));
    }

    @Test
    void primaryAndExtraUseTheirVerifiedNativeTags() throws Exception {
        var bytes =
                packet(List.of(new ItemParamData(108013, 2)), List.of(new ItemParamData(108011, 1)))
                        .getData();
        var stream = CodedInputStream.newInstance(bytes);
        var itemTags = new LinkedHashMap<Integer, ItemParam>();
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (tag == 0x52 || tag == 0x5a)
                itemTags.put(tag, ItemParam.parseFrom(stream.readByteArray()));
            else stream.skipField(tag);
        }
        assertEquals(Set.of(0x52, 0x5a), itemTags.keySet());
        assertEquals(108011, itemTags.get(0x52).getItemId());
        assertEquals(1, itemTags.get(0x52).getCount());
        assertEquals(108013, itemTags.get(0x5a).getItemId());
        assertEquals(2, itemTags.get(0x5a).getCount());
    }

    @Test
    void identicalItemIdsRemainSeparateWithoutDoubleCounting() throws Exception {
        var result =
                PlayerCookRsp.parseFrom(
                        packet(
                                        List.of(new ItemParamData(108013, 2)),
                                        List.of(new ItemParamData(108013, 1)))
                                .getData());
        assertEquals(2, result.getItemList(0).getCount());
        assertEquals(1, result.getExtralItemList(0).getCount());
        assertEquals(3, result.getItemList(0).getCount() + result.getExtralItemList(0).getCount());
    }

    @Test
    void noExtraProducesNoFieldTen() throws Exception {
        var result =
                PlayerCookRsp.parseFrom(
                        packet(List.of(new ItemParamData(108013, 2)), List.of()).getData());
        assertEquals(0, result.getExtralItemListCount());
        assertEquals(1, result.getItemListCount());
    }

    @Test
    void recipeAndQualityCountersKeepTheirNativeFields() throws Exception {
        var bytes = packet(List.of(), List.of()).getData();
        var fields = new HashMap<Integer, Integer>();
        var stream = CodedInputStream.newInstance(bytes);
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (tag == 0x6a) {
                var recipe = CookRecipeData.parseFrom(stream.readByteArray());
                assertEquals(1001, recipe.getRecipeId());
                assertEquals(5, recipe.getProficiency());
            } else if (tag == 0x30 || tag == 0x40) fields.put(tag, stream.readUInt32());
            else stream.skipField(tag);
        }
        assertEquals(Map.of(0x30, 3, 0x40, 2), fields);
    }

    @Test
    void existingConstructorKeepsPrimaryOnlyCompatibility() throws Exception {
        var packet = new PacketPlayerCookRsp(List.<GameItem>of(), 3, 1, 1001, 5);
        var result = PlayerCookRsp.parseFrom(packet.getData());
        assertEquals(0, result.getRetcode());
        assertEquals(0, result.getExtralItemListCount());
        assertEquals(1001, result.getRecipeData().getRecipeId());
    }

    @Test
    void errorPacketDoesNotExposeAnyProducts() throws Exception {
        var result = PlayerCookRsp.parseFrom(new PacketPlayerCookRsp(Retcode.RET_FAIL).getData());
        assertNotEquals(0, result.getRetcode());
        assertEquals(0, result.getItemListCount());
        assertEquals(0, result.getExtralItemListCount());
    }
}
