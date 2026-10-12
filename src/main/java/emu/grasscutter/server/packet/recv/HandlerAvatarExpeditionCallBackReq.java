package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionCallBackReqOuterClass.AvatarExpeditionCallBackReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionCallBackRsp;

@Opcodes(PacketOpcodes.AvatarExpeditionCallBackReq)
public class HandlerAvatarExpeditionCallBackReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AvatarExpeditionCallBackReq req = AvatarExpeditionCallBackReq.parseFrom(payload);
        var player = session.getPlayer();

        synchronized (player.getInventory()) {
            for (long guid : req.getAvatarGuidList()) {
                var info = player.getExpeditionInfo(guid);
                if (info != null && info.getRewardState() == 1) {
                    session.send(
                            new PacketAvatarExpeditionCallBackRsp(
                                    player.getExpeditionInfo(), Retcode.RET_SVR_ERROR_VALUE));
                    return;
                }
            }
            for (int i = 0; i < req.getAvatarGuidCount(); i++) {
                player.removeExpeditionInfo(req.getAvatarGuid(i));
            }

            player.save();
            session.send(new PacketAvatarExpeditionCallBackRsp(player.getExpeditionInfo()));
        }
    }
}
