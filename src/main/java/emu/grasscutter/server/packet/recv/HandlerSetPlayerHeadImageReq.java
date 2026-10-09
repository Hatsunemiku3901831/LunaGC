package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerHeadImageReqOuterClass.SetPlayerHeadImageReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadImageRsp;

@Opcodes(PacketOpcodes.SetPlayerHeadImageReq)
public class HandlerSetPlayerHeadImageReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerHeadImageReq req = SetPlayerHeadImageReq.parseFrom(payload);

        int id = req.getProfilePictureId();

        var player = session.getPlayer();
        int retcode =
                player.selectProfilePicture(id) ? 0 : Retcode.RET_PROFILE_PICTURE_NOT_UNLOCKED_VALUE;
        session.send(
                new PacketSetPlayerHeadImageRsp(player, retcode)
                        .buildHeader(PacketHead.parseFrom(header).getClientSequenceId()));
    }
}
