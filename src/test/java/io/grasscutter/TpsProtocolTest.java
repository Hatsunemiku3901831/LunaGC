package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.AvatarEnterSceneInfoOuterClass.AvatarEnterSceneInfo;
import emu.grasscutter.net.proto.AvatarInfoOuterClass.AvatarInfo;
import emu.grasscutter.net.proto.SceneAvatarInfoOuterClass.SceneAvatarInfo;
import emu.grasscutter.net.proto.SceneTeamAvatarOuterClass.SceneTeamAvatar;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify;
import emu.grasscutter.net.proto.TpsRegionalPlaySupplyInfoNotifyOuterClass.TpsRegionalPlaySupplyInfoNotify;
import emu.grasscutter.net.proto.WearTpsEquipReqOuterClass.WearTpsEquipReq;
import emu.grasscutter.net.proto.WearTpsEquipRspOuterClass.WearTpsEquipRsp;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Golden wire bytes follow the checked native parse/write tags, not Java field names. */
class TpsProtocolTest {
    private byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    void wearRequestUsesClientTagsFiveAndThirteen() throws Exception {
        var req = WearTpsEquipReq.parseFrom(bytes("28ac026a04ad02ae02"));
        assertEquals(300, req.getAvatarGuid());
        assertEquals(java.util.List.of(301L, 302L), req.getEquipGuidListList());
        assertArrayEquals(bytes("28ac026a04ad02ae02"), req.toByteArray());
        assertEquals(20756, PacketOpcodes.WearTpsEquipReq);
    }

    @Test
    void responseAndEquipNotifyMatchNativeTags() throws Exception {
        var rsp = WearTpsEquipRsp.parseFrom(bytes("38d90460ac026a02ad02"));
        assertEquals(601, rsp.getRetcode());
        assertEquals(300, rsp.getAvatarGuid());
        assertEquals(java.util.List.of(301L), rsp.getEquipGuidListList());
        var change = TpsEquipChangeNotify.parseFrom(bytes("18ac023a021803"));
        assertEquals(300, change.getAvatarGuid());
        assertEquals(3, change.getTpsWeaponList(0).getItemId());
        assertEquals(25902, PacketOpcodes.WearTpsEquipRsp);
        assertEquals(21312, PacketOpcodes.TpsEquipChangeNotify);
    }

    @Test
    void ammoPayloadKeepsUnknownFieldsAndDistinctEntryTags() throws Exception {
        var update = AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(bytes("180138014205386550ff01"));
        // Unknown fields survive; they are not interpreted as accessory ownership.
        assertEquals(1, update.getUpdateTypeValue());
        assertTrue(update.getFENKFADBDID());
        assertFalse(update.toByteString().isEmpty());
        var notify = TpsAmmunitionChangeNotify.parseFrom(bytes("3205200540e907"));
        assertEquals(5, notify.getAmmunitionList(0).getChangeCount());
        assertEquals(1001, notify.getAmmunitionList(0).getAmmunitionConfigId());
        assertEquals(24371, PacketOpcodes.TpsAmmunitionChangeNotify);
    }

    @Test
    void fullAmmunitionSupplyUsesNativeListElevenAndEntryFieldsOneAndFourteen() throws Exception {
        var wire = bytes("5a0608e90770d00f5a0308ea07");
        var supply = TpsRegionalPlaySupplyInfoNotify.parseFrom(wire);
        assertEquals(2, supply.getAmmunitionListCount());
        assertEquals(1001, supply.getAmmunitionList(0).getAmmunitionConfigId());
        assertEquals(2000, supply.getAmmunitionList(0).getTotalAmmunition());
        assertEquals(1002, supply.getAmmunitionList(1).getAmmunitionConfigId());
        assertEquals(0, supply.getAmmunitionList(1).getTotalAmmunition());
        assertArrayEquals(wire, supply.toByteArray());
        assertEquals(6579, PacketOpcodes.TpsRegionalPlaySupplyInfoNotify);
    }

    @Test
    void nestedAmmoAndSceneContainersPreserveExistingFieldNumbers() throws Exception {
        var weapon = SceneWeaponInfo.parseFrom(bytes("6207086510e9071827"));
        assertEquals(101, weapon.getAmmunitionList(0).getAmmunitionType());
        assertEquals(1001, weapon.getAmmunitionList(0).getAmmunitionConfigId());
        assertEquals(39, weapon.getAmmunitionList(0).getCurrentAmmunition());
        assertEquals(
                31, SceneAvatarInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(37, AvatarInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                382, SceneTeamAvatar.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                13, AvatarEnterSceneInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
    }
}
