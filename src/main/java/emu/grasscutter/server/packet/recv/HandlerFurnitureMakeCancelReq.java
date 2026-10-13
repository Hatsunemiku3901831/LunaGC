package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeCancelReqOuterClass.FurnitureMakeCancelReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeCancelRsp;

@Opcodes(PacketOpcodes.FurnitureMakeCancelReq)
public final class HandlerFurnitureMakeCancelReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        FurnitureMakeCancelReq request;
        try {
            if (payload == null)
                throw new InvalidProtocolBufferException("Missing cancellation request");
            request = FurnitureMakeCancelReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(new PacketFurnitureMakeCancelRsp(Retcode.RET_SVR_ERROR_VALUE, 0, null));
            return;
        }
        session.getPlayer().getFurnitureManager().cancel(request.getIndex(), request.getMakeId());
    }
}
