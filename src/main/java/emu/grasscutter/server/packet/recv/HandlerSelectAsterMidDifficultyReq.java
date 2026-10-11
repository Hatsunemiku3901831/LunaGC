package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SelectAsterMidDifficultyReqOuterClass.SelectAsterMidDifficultyReq;
import emu.grasscutter.net.proto.SelectAsterMidDifficultyRspOuterClass.SelectAsterMidDifficultyRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.SelectAsterMidDifficultyReq)
public final class HandlerSelectAsterMidDifficultyReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = SelectAsterMidDifficultyReq.parseFrom(payload);
        var player = session.getPlayer();
        int code =
                player.getScene() == null
                        ? Retcode.RET_ACTIVITY_CLOSE_VALUE
                        : player.getScene()
                                .getAsterMidSceneController()
                                .select(
                                        player,
                                        request.getScheduleId(),
                                        request.getGadgetEntityId(),
                                        request.getDifficultyId());
        var response = new BasePacket(PacketOpcodes.SelectAsterMidDifficultyRsp);
        response.setData(
                SelectAsterMidDifficultyRsp.newBuilder()
                        .setScheduleId(request.getScheduleId())
                        .setGadgetEntityId(request.getGadgetEntityId())
                        .setDifficultyId(request.getDifficultyId())
                        .setRetcode(code)
                        .build());
        session.send(response);
    }
}
