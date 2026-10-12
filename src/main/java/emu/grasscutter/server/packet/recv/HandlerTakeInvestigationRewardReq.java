package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.InvestigationRewards;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeInvestigationRewardReqOuterClass.TakeInvestigationRewardReq;
import emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass.TakeInvestigationRewardRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.TakeInvestigationRewardReq)
public final class HandlerTakeInvestigationRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int id = 0, result = Retcode.RET_FAIL_VALUE;
        try {
            var request = TakeInvestigationRewardReq.parseFrom(payload);
            id = request.getId();
            result = InvestigationRewards.takeChapter(session.getPlayer(), id);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            result = Retcode.RET_FAIL_VALUE;
        }
        var response = new BasePacket(PacketOpcodes.TakeInvestigationRewardRsp);
        response.setData(
                TakeInvestigationRewardRsp.newBuilder().setId(id).setRetcode(result).build());
        session.send(response);
    }
}
