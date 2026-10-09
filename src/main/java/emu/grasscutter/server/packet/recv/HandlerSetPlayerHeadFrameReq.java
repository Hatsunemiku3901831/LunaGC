package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerHeadFrameReqOuterClass.SetPlayerHeadFrameReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadFrameRsp;

@Opcodes(PacketOpcodes.SetPlayerHeadFrameReq)
public class HandlerSetPlayerHeadFrameReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = SetPlayerHeadFrameReq.parseFrom(payload);
        var player = session.getPlayer();
        int retcode = player.selectProfileFrame(req.getProfileFrameId()) ? 0 : Retcode.RET_FAIL_VALUE;
        session.send(
                new PacketSetPlayerHeadFrameRsp(player, retcode)
                        .buildHeader(PacketHead.parseFrom(header).getClientSequenceId()));
    }
}
