package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.expedition.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

@Opcodes(PacketOpcodes.AvatarExpeditionGetRewardReq)
public class HandlerAvatarExpeditionGetRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        AvatarExpeditionGetRewardReq req;
        try {
            req = AvatarExpeditionGetRewardReq.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            session.send(
                    new PacketAvatarExpeditionGetRewardRsp(
                            player.getExpeditionInfo(),
                            java.util.List.of(),
                            Retcode.RET_FAIL_VALUE));
            return;
        }
        var result =
                ExpeditionClaims.take(
                        player,
                        session.getServer().getExpeditionSystem(),
                        req.getAvatarGuid(),
                        req.getIsClaimAll(),
                        Utils.getCurrentSeconds());
        var response =
                new PacketAvatarExpeditionGetRewardRsp(
                        player.getExpeditionInfo(), result.rewards(), result.retcode());
        if (header != null && header.length > 0) {
            try {
                response.buildHeader(PacketHead.parseFrom(header).getClientSequenceId());
            } catch (com.google.protobuf.InvalidProtocolBufferException ignored) {
            }
        }
        session.send(response);
        if (result.retcode() == 0)
            session.send(new PacketAvatarExpeditionDataNotify(player.getExpeditionInfo()));
    }
}
