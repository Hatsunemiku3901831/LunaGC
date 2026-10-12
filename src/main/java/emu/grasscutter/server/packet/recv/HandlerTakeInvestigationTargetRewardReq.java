package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.InvestigationRewards;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeInvestigationTargetRewardReqOuterClass.TakeInvestigationTargetRewardReq;
import emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass.TakeInvestigationTargetRewardRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.TakeInvestigationTargetRewardReq)
public final class HandlerTakeInvestigationTargetRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int id = 0;
        int result = Retcode.RET_FAIL_VALUE;
        try {
            var request = TakeInvestigationTargetRewardReq.parseFrom(payload);
            id = request.getQuestId();
            result = InvestigationRewards.takeTarget(session.getPlayer(), id);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            result = Retcode.RET_FAIL_VALUE;
        }
        var response = new BasePacket(PacketOpcodes.TakeInvestigationTargetRewardRsp);
        response.setData(
                TakeInvestigationTargetRewardRsp.newBuilder()
                        .setQuestId(id)
                        .setRetcode(result)
                        .build());
        session.send(response);
    }
}
