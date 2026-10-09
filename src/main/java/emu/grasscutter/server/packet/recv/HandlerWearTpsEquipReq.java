package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WearTpsEquipReqOuterClass.WearTpsEquipReq;
import emu.grasscutter.net.proto.WearTpsEquipRspOuterClass.WearTpsEquipRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WearTpsEquipReq)
public class HandlerWearTpsEquipReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = WearTpsEquipReq.parseFrom(payload);
        var player = session.getPlayer();
        int result = TpsWeaponSystem.wear(player, req.getAvatarGuid(), req.getEquipGuidListList());
        var response =
                WearTpsEquipRsp.newBuilder().setAvatarGuid(req.getAvatarGuid()).setRetcode(result);
        var avatar = TpsWeaponSystem.findAvatar(player, req.getAvatarGuid());
        if (avatar != null)
            response.addAllEquipGuidList(
                    TpsWeaponSystem.getWornWeapons(avatar).stream().map(item -> item.getGuid()).toList());
        var packet = new BasePacket(PacketOpcodes.WearTpsEquipRsp);
        packet.setData(response);
        session.send(packet);
    }
}
