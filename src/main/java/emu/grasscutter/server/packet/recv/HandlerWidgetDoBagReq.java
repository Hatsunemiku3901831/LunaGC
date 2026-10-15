package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.WidgetDoBagReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.WidgetDoBagReq)
public class HandlerWidgetDoBagReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WidgetDoBagReqOuterClass.WidgetDoBagReq req;
        try {
            req = WidgetDoBagReqOuterClass.WidgetDoBagReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(
                    new PacketWidgetDoBagRsp(
                            Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber(), 0));
            return;
        }
        int code = session.getPlayer().getWidgets().placements().useBag(req);
        session.send(new PacketWidgetDoBagRsp(code, req.getMaterialId()));
    }
}
