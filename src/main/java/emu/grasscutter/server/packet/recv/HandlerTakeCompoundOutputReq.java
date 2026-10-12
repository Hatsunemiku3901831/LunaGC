package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PackageTakeCompoundOutputRsp;

import java.util.List;

@Opcodes(PacketOpcodes.TakeCompoundOutputReq)
public class HandlerTakeCompoundOutputReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq req;
        try {
            req = TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            session.getPlayer()
                    .sendPacket(
                            new PackageTakeCompoundOutputRsp(List.of(), Retcode.RET_FAIL_VALUE));
            return;
        }
        session.getPlayer().getCookingCompoundManager().handleTakeCompoundOutputReq(req);
    }
}
