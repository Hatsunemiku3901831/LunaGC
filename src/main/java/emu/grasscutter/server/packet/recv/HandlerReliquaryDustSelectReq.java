package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryDustSelectReqOuterClass.ReliquaryDustSelectReq;
import emu.grasscutter.net.proto.ReliquaryDustSelectRspOuterClass.ReliquaryDustSelectRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketReliquaryDustSelectRsp;

@Opcodes(PacketOpcodes.ReliquaryDustSelectReq)
public final class HandlerReliquaryDustSelectReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int sequence = 0;
        ReliquaryDustSelectReq request;
        try {
            if (header != null && header.length > 0)
                sequence = PacketHead.parseFrom(header).getClientSequenceId();
            request = ReliquaryDustSelectReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(
                    new PacketReliquaryDustSelectRsp(
                            ReliquaryDustSelectRsp.newBuilder()
                                    .setRetcode(Retcode.RET_FAIL_VALUE)
                                    .build(),
                            sequence));
            return;
        }
        session.send(
                new PacketReliquaryDustSelectRsp(
                        session.getPlayer().getArtifactTransmuter().selectReshape(request),
                        sequence));
    }
}
