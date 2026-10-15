package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetUpLunchBoxWidgetReqOuterClass.SetUpLunchBoxWidgetReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetUpLunchBoxWidgetRsp;

@Opcodes(PacketOpcodes.SetUpLunchBoxWidgetReq)
public class HandlerSetUpLunchBoxWidgetReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        int retcode = Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber();
        SetUpLunchBoxWidgetReq req = null;
        try {
            req = SetUpLunchBoxWidgetReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            // Return the unchanged authoritative selection on invalid input.
        }
        if (player != null && req != null && req.hasLunchBoxData())
            retcode =
                    player.getWidgets().setLunchBox(req.getLunchBoxData().getSlotMaterialMapMap());
        session.send(
                new PacketSetUpLunchBoxWidgetRsp(
                        player == null
                                ? LunchBoxData.getDefaultInstance()
                                : player.getWidgets().lunchBox(),
                        retcode));
    }
}
