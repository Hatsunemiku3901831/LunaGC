package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllReqOuterClass.TakeFurnitureMakeAllReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeFurnitureMakeAllRsp;

@Opcodes(PacketOpcodes.TakeFurnitureMakeAllReq)
public final class HandlerTakeFurnitureMakeAllReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TakeFurnitureMakeAllReq request;
        try {
            if (payload == null) throw new InvalidProtocolBufferException("Missing batch claim");
            request = TakeFurnitureMakeAllReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(
                    new PacketTakeFurnitureMakeAllRsp(Retcode.RET_SVR_ERROR_VALUE, null, null));
            return;
        }
        session.getPlayer().getFurnitureManager().takeAll(request.getTakeInfoListList());
    }
}
