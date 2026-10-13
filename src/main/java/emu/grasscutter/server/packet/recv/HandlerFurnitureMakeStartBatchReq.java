package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeStartBatchReqOuterClass.FurnitureMakeStartBatchReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeStartBatchRsp;

@Opcodes(PacketOpcodes.FurnitureMakeStartBatchReq)
public final class HandlerFurnitureMakeStartBatchReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        FurnitureMakeStartBatchReq request;
        try {
            if (payload == null)
                throw new InvalidProtocolBufferException("Missing furniture batch start");
            request = FurnitureMakeStartBatchReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(new PacketFurnitureMakeStartBatchRsp(Retcode.RET_SVR_ERROR_VALUE, null));
            return;
        }
        if (session.getPlayer() == null) {
            session.send(
                    new PacketFurnitureMakeStartBatchRsp(
                            Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, null));
            return;
        }
        session.getPlayer().getFurnitureManager().startBatch(request.getMakeInfoListList());
    }
}
