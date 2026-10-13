package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeReqOuterClass.FurnitureMakeReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeRsp;

@Opcodes(PacketOpcodes.FurnitureMakeReq)
public class HandlerFurnitureMakeReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        try {
            if (payload == null)
                throw new InvalidProtocolBufferException("Missing furniture query");
            FurnitureMakeReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(new PacketFurnitureMakeRsp(Retcode.RET_SVR_ERROR_VALUE));
            return;
        }
        if (session.getPlayer() == null) {
            session.send(
                    new PacketFurnitureMakeRsp(
                            Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE));
            return;
        }
        session.getPlayer().getFurnitureManager().queryStatus();
    }
}
