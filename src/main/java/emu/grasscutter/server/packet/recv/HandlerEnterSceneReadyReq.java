package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterSceneReadyReqOuterClass.EnterSceneReadyReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.SceneHandshakeToken;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneReadyReq)
public class HandlerEnterSceneReadyReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = EnterSceneReadyReq.parseFrom(payload);
        var player = session.getPlayer();
        SceneHandshakeToken.requireCurrent(player, (request.getMCCMGLDOECH() - 0x4C01) ^ 0xE8C3);
        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }
}
