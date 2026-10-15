package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CityInfoOuterClass.CityInfo;
import emu.grasscutter.net.proto.LevelupCityReqOuterClass.LevelupCityReq;
import emu.grasscutter.net.proto.LevelupCityRspOuterClass.LevelupCityRsp;
import emu.grasscutter.server.packet.recv.HandlerLevelupCityReq;
import emu.grasscutter.server.packet.send.PacketLevelupCityRsp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** Native opcode getters and wire readers/writers, independently of generated Java codecs. */
class StatueProtocol71Test {
    @Test
    void opcodesMatchNativeLiteralGettersAndHandlerRegistration() {
        // 0x14939bb40 / 0x1492c54d0 return 8352 / 26402.
        assertEquals(8352, PacketOpcodes.LevelupCityReq);
        assertEquals(8352, HandlerLevelupCityReq.class.getAnnotation(Opcodes.class).value());
        assertEquals(26402, PacketOpcodes.LevelupCityRsp);
        assertEquals(26402, new PacketLevelupCityRsp(3, 2, 5, 7, 33, 0).getOpcode());
    }

    @Test
    void requestReadsDistinctNativeItemSceneAndAreaFields() throws Exception {
        // 0x14939bb80 writes +0x20 at tag 8, +0x18 at 0x28, +0x1c at 0x40.
        var req = LevelupCityReq.parseFrom(new byte[] {0x08, 20, 0x28, 3, 0x40, 33});
        assertEquals(20, req.getItemNum());
        assertEquals(3, req.getSceneId());
        assertEquals(33, req.getAreaId());
        assertEquals(UnknownFieldSet.getDefaultInstance(), req.getUnknownFields());
    }

    @Test
    void citySnapshotMatchesNativeThreeFieldWriter() throws Exception {
        // 0x14af83ee0 writes crystals at 0x18, level at 0x28, city at 0x38.
        var city = new CityInfoData(5);
        city.setLevel(2);
        city.setNumCrystal(7);
        assertArrayEquals(new byte[] {0x18, 7, 0x28, 2, 0x38, 5}, city.toProto().toByteArray());
        var decoded = CityInfo.parseFrom(new byte[] {0x38, 5, 0x28, 2, 0x18, 7});
        assertEquals(5, decoded.getCityId());
        assertEquals(2, decoded.getLevel());
        assertEquals(7, decoded.getCrystalNum());
        assertEquals(UnknownFieldSet.getDefaultInstance(), decoded.getUnknownFields());
    }

    @Test
    void responseMatchesNativeNestedCityAndResultFields() throws Exception {
        // Native reader 0x1492c5620: scene=8, area=0x18, city=0x2a, result=0x30.
        var packet = new PacketLevelupCityRsp(3, 2, 5, 7, 33, 0);
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(1, 3, 5), wire.asMap().keySet());
        assertEquals(List.of(3L), wire.getField(1).getVarintList());
        assertEquals(List.of(33L), wire.getField(3).getVarintList());
        assertArrayEquals(
                new byte[] {0x18, 7, 0x28, 2, 0x38, 5},
                wire.getField(5).getLengthDelimitedList().get(0).toByteArray());
        var decoded =
                LevelupCityRsp.parseFrom(
                        new byte[] {
                            0x08, 3, 0x18, 33, 0x2a, 6, 0x18, 7, 0x28, 2, 0x38, 5, 0x30, 9
                        });
        assertEquals(3, decoded.getSceneId());
        assertEquals(33, decoded.getAreaId());
        assertEquals(5, decoded.getCityInfo().getCityId());
        assertEquals(9, decoded.getRetcode());
        assertEquals(UnknownFieldSet.getDefaultInstance(), decoded.getUnknownFields());
    }

    @Test
    void nonzeroFailureCodeIsSentAtNativeResultTag() throws Exception {
        var packet = new PacketLevelupCityRsp(3, 1, 5, 0, 33, 9);
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(1, 3, 5, 6), wire.asMap().keySet());
        assertEquals(List.of(9L), wire.getField(6).getVarintList());
    }
}
