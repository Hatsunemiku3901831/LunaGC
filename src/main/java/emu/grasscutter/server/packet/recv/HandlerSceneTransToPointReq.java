package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneTransToPointReqOuterClass.SceneTransToPointReq;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSceneTransToPointRsp;

@Opcodes(PacketOpcodes.SceneTransToPointReq)
public class HandlerSceneTransToPointReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SceneTransToPointReq req = SceneTransToPointReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null) {
            session.send(new PacketSceneTransToPointRsp());
            return;
        }
        if (player.isScenePointForceLocked(req.getSceneId(), req.getPointId())) {
            session.send(
                    new PacketSceneTransToPointRsp(
                            req.getPointId(), req.getSceneId(), Retcode.RET_POINT_NOT_UNLOCKED));
            return;
        }

        ScenePointEntry scenePointEntry =
                GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());

        if (scenePointEntry != null
                && scenePointEntry.getSceneId() == req.getSceneId()
                && scenePointEntry.getPointData() != null
                && scenePointEntry.getPointData().getId() == req.getPointId()
                && scenePointEntry.getPointData().getTranPos() != null) {
            if (player.getCurHomeWorld() != null && player.getCurHomeWorld().isInHome(player)) {
                session.getServer()
                        .getHomeWorldMPSystem()
                        .leaveCoop(
                                player,
                                req.getSceneId(),
                                scenePointEntry.getPointData().getTranPos().clone());
                session.send(
                        new PacketSceneTransToPointRsp(player, req.getPointId(), req.getSceneId()));
                return;
            } else if (player.getWorld() != null
                    && player.getWorld()
                            .transferPlayerToScene(
                                    player,
                                    req.getSceneId(),
                                    TeleportType.WAYPOINT,
                                    scenePointEntry.getPointData().getTranPos().clone())) {
                session.send(
                        new PacketSceneTransToPointRsp(player, req.getPointId(), req.getSceneId()));
                return;
            }
        }

        session.send(new PacketSceneTransToPointRsp());
    }
}
