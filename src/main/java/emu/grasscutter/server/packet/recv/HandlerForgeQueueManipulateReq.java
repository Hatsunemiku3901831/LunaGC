package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketForgeQueueManipulateRsp;

import java.util.List;

@Opcodes(PacketOpcodes.ForgeQueueManipulateReq)
public class HandlerForgeQueueManipulateReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ForgeQueueManipulateReq req;
        try {
            req = ForgeQueueManipulateReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.getPlayer()
                    .sendPacket(
                            new PacketForgeQueueManipulateRsp(
                                    Retcode.RET_FAIL_VALUE, 0, List.of(), List.of(), List.of()));
            return;
        }
        session.getPlayer().getForgingManager().handleForgeQueueManipulateReq(req);
    }
}
