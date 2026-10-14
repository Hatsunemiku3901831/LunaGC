package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.SetWidgetSlotReq)
public class HandlerSetWidgetSlotReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetWidgetSlotReqOuterClass.SetWidgetSlotReq req =
                SetWidgetSlotReqOuterClass.SetWidgetSlotReq.parseFrom(payload);

        var result =
                session.getPlayer()
                        .getWidgets()
                        .setSlot(req.getMaterialId(), req.getOpValue(), req.getTagListValueList());
        var response = new BasePacket(PacketOpcodes.SetWidgetSlotRsp);
        response.setData(
                SetWidgetSlotRspOuterClass.SetWidgetSlotRsp.newBuilder()
                        .setMaterialId(req.getMaterialId())
                        .setOpValue(req.getOpValue())
                        .addAllTagListValue(result.tags())
                        .setRetcode(result.retcode()));
        session.send(response);
    }
}
