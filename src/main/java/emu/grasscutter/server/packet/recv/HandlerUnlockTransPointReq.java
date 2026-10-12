package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.UnlockTransPointReq)
public class HandlerUnlockTransPointReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnlockTransPointReq req = UnlockTransPointReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null || player.isScenePointForceLocked(req.getSceneId(), req.getPointId()))
            return;
        // unlockTransPoint sends ScenePointUnlockNotify. The 7.1 client applies that notify
        // directly; UnlockTransPointRsp still has no known CmdId.
        session.getPlayer()
                .getProgressManager()
                .unlockTransPoint(req.getSceneId(), req.getPointId(), false);
    }
}
