package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetAllUnlockProfileDataRsp;

@Opcodes(PacketOpcodes.GetAllUnlockProfileDataReq)
public class HandlerGetAllUnlockProfileDataReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(
                new PacketGetAllUnlockProfileDataRsp(session.getPlayer())
                        .buildHeader(PacketHead.parseFrom(header).getClientSequenceId()));
    }
}
