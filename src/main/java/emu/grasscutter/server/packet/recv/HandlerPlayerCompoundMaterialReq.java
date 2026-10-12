package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerCompoundMaterialRsp;

@Opcodes(PacketOpcodes.PlayerCompoundMaterialReq)
public class HandlerPlayerCompoundMaterialReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq req;
        try {
            req = PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            session.getPlayer()
                    .sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_FAIL_VALUE));
            return;
        }
        session.getPlayer().getCookingCompoundManager().handlePlayerCompoundMaterialReq(req);
    }
}
