package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryDecomposeReqOuterClass.ReliquaryDecomposeReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketReliquaryDecomposeRsp;

@Opcodes(PacketOpcodes.ReliquaryDecomposeReq)
public class HandlerReliquaryDecomposeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ReliquaryDecomposeReq req;
        try {
            req = ReliquaryDecomposeReq.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            session.send(
                    new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
            return;
        }
        session.getServer()
                .getCombineSystem()
                .decomposeReliquaries(
                        session.getPlayer(),
                        req.getConfigId(),
                        req.getTargetCount(),
                        req.getGuidListList());
    }
}
