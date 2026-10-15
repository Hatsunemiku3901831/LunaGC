package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryDustReqOuterClass.ReliquaryDustReq;
import emu.grasscutter.net.proto.ReliquaryDustRspOuterClass.ReliquaryDustRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketReliquaryDustRsp;

@Opcodes(PacketOpcodes.ReliquaryDustReq)
public final class HandlerReliquaryDustReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int sequence = 0;
        ReliquaryDustReq request;
        try {
            if (header != null && header.length > 0)
                sequence = PacketHead.parseFrom(header).getClientSequenceId();
            request = ReliquaryDustReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(
                    new PacketReliquaryDustRsp(
                            ReliquaryDustRsp.newBuilder()
                                    .setRetcode(Retcode.RET_FAIL_VALUE)
                                    .build(),
                            sequence));
            return;
        }
        session.send(
                new PacketReliquaryDustRsp(
                        session.getPlayer().getArtifactTransmuter().reshape(request), sequence));
    }
}
