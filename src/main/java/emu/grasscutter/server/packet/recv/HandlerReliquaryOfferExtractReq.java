package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.net.proto.ReliquaryOfferExtractRspOuterClass.ReliquaryOfferExtractRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferExtractRsp;

@Opcodes(PacketOpcodes.ReliquaryOfferExtractReq)
public final class HandlerReliquaryOfferExtractReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int sequence = 0;
        ReliquaryOfferExtractReq request;
        try {
            if (header != null && header.length > 0)
                sequence = PacketHead.parseFrom(header).getClientSequenceId();
            request = ReliquaryOfferExtractReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(
                    new PacketReliquaryOfferExtractRsp(
                            ReliquaryOfferExtractRsp.newBuilder()
                                    .setRetcode(Retcode.RET_FAIL_VALUE)
                                    .build(),
                            sequence));
            return;
        }
        var response = session.getPlayer().getArtifactTransmuter().extract(request);
        session.send(new PacketReliquaryOfferExtractRsp(response, sequence));
    }
}
