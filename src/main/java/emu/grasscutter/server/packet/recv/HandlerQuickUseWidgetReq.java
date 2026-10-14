package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuickUseWidgetRspOuterClass.QuickUseWidgetRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.QuickUseWidgetReq)
public class HandlerQuickUseWidgetReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        player.getWidgets().refresh();
        int id = player.getWidgetId();
        var item = player.getInventory().getItemById(id);
        int code =
                id == 0
                        ? Retcode.RET_WIDGET_NOT_SET_QUICK_USE.getNumber()
                        : item == null || item.getCount() <= 0
                                ? Retcode.RET_ITEM_NOT_EXIST.getNumber()
                                : Retcode.RET_INVALID_QUICK_USE_WIDGET.getNumber();
        // An unimplemented effect must fail without consuming the reusable or consumable item.
        var response = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
        response.setData(QuickUseWidgetRsp.newBuilder().setMaterialId(id).setRetcode(code));
        session.send(response);
    }
}
