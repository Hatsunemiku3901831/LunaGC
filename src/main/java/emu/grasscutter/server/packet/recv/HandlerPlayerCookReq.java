package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerCookRsp;

@Opcodes(PacketOpcodes.PlayerCookReq)
public class HandlerPlayerCookReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerCookReqOuterClass.PlayerCookReq req;
        try {
            req = PlayerCookReqOuterClass.PlayerCookReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.getPlayer().sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }
        session.getPlayer().getCookingManager().handlePlayerCookReq(req);
    }
}
