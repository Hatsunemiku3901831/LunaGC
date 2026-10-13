package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.ability.VehicleInteraction;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.VehicleInteractReqOuterClass;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.VehicleInteractReq)
public class HandlerVehicleInteractReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        VehicleInteractReqOuterClass.VehicleInteractReq req =
                VehicleInteractReqOuterClass.VehicleInteractReq.parseFrom(payload);
        session.send(
                VehicleInteraction.execute(session, req.getEntityId(), req.getInteractTypeValue()));
    }
}
