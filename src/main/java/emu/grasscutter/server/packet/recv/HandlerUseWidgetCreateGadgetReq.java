package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUseWidgetCreateGadgetRsp;

@Opcodes(PacketOpcodes.UseWidgetCreateGadgetReq)
public class HandlerUseWidgetCreateGadgetReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UseWidgetCreateGadgetReq req;
        try {
            req = UseWidgetCreateGadgetReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException invalid) {
            session.send(
                    new PacketUseWidgetCreateGadgetRsp(
                            Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber(), 0));
            return;
        }
        int code = session.getPlayer().getWidgets().placements().create(req);
        session.send(new PacketUseWidgetCreateGadgetRsp(code, req.getMaterialId()));
    }
}
