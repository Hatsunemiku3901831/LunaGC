package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketForgeStartRsp;

@Opcodes(PacketOpcodes.ForgeStartReq)
public class HandlerForgeStartReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ForgeStartReqOuterClass.ForgeStartReq req;
        try {
            req = ForgeStartReqOuterClass.ForgeStartReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.getPlayer().sendPacket(new PacketForgeStartRsp(Retcode.RET_FAIL));
            return;
        }
        session.getPlayer().getForgingManager().handleForgeStartReq(req);
    }
}
