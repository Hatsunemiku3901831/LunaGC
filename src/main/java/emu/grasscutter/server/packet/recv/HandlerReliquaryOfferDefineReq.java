package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryOfferDefineReqOuterClass.ReliquaryOfferDefineReq;
import emu.grasscutter.net.proto.ReliquaryOfferDefineRspOuterClass.ReliquaryOfferDefineRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferDefineRsp;

@Opcodes(PacketOpcodes.ReliquaryOfferDefineReq)
public final class HandlerReliquaryOfferDefineReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int sequence = 0;
        ReliquaryOfferDefineReq request;
        try {
            if (header != null && header.length > 0)
                sequence = PacketHead.parseFrom(header).getClientSequenceId();
            request = ReliquaryOfferDefineReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(
                    new PacketReliquaryOfferDefineRsp(
                            ReliquaryOfferDefineRsp.newBuilder()
                                    .setRetcode(Retcode.RET_FAIL_VALUE)
                                    .build(),
                            sequence));
            return;
        }
        session.send(
                new PacketReliquaryOfferDefineRsp(
                        session.getPlayer().getArtifactTransmuter().define(request), sequence));
    }
}
