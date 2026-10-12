package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerTeamOuterClass;
import emu.grasscutter.net.proto.TowerTeamSelectReqOuterClass.TowerTeamSelectReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerTeamSelectRsp;

@Opcodes(PacketOpcodes.TowerTeamSelectReq)
public class HandlerTowerTeamSelectReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TowerTeamSelectReq req = TowerTeamSelectReq.parseFrom(payload);

        var towerTeams =
                req.getTowerTeamListList().stream()
                        .map(TowerTeamOuterClass.TowerTeam::getAvatarGuidListList)
                        .toList();

        var player = session.getPlayer();
        if (player == null) return;
        var success = player.getTowerManager().tryTeamSelect(req.getFloorId(), towerTeams);
        session.send(new PacketTowerTeamSelectRsp(success ? Retcode.RET_SUCC : Retcode.RET_FAIL));
    }
}
