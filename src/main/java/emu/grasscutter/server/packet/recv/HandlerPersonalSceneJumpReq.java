package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PersonalSceneJumpReqOuterClass.PersonalSceneJumpReq;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPersonalSceneJumpRsp;

@Opcodes(PacketOpcodes.PersonalSceneJumpReq)
public class HandlerPersonalSceneJumpReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PersonalSceneJumpReq req = PersonalSceneJumpReq.parseFrom(payload);
        var player = session.getPlayer();
        var prevSceneId = player.getSceneId();

        // get the scene point
        ScenePointEntry scenePointEntry =
                GameData.getScenePointEntryById(prevSceneId, req.getPointId());

        if (scenePointEntry == null || scenePointEntry.getPointData().getTranPos() == null) {
            session.send(new PacketPersonalSceneJumpRsp(Retcode.RET_ENTER_SCENE_FAIL));
            return;
        }
        var point = scenePointEntry.getPointData();
        Position pos = point.getTranPos().clone();
        int sceneId = point.getTranSceneId();
        var properties = TeleportProperties.builder()
                .sceneId(sceneId)
                .teleportType(TeleportType.INTERNAL)
                .enterReason(EnterReason.PersonalScene)
                .enterType(EnterType.EnterType_ENTER_JUMP)
                .teleportTo(pos)
                .teleportRot(point.getTranRot() == null ? null : point.getTranRot().clone())
                .build();
        if (!player.getWorld().transferPlayerToScene(player, properties)) {
            session.send(new PacketPersonalSceneJumpRsp(Retcode.RET_ENTER_SCENE_FAIL));
            return;
        }
        player.getScene().setPrevScene(prevSceneId);
        session.send(new PacketPersonalSceneJumpRsp(sceneId, pos));
    }
}
