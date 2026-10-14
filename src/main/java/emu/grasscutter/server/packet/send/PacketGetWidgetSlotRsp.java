package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;

public class PacketGetWidgetSlotRsp extends BasePacket {

    public PacketGetWidgetSlotRsp(Player player) {
        super(PacketOpcodes.GetWidgetSlotRsp);

        GetWidgetSlotRspOuterClass.GetWidgetSlotRsp.Builder proto =
                GetWidgetSlotRspOuterClass.GetWidgetSlotRsp.newBuilder();

        proto.addAllSlotList(player.getWidgets().slots());

        GetWidgetSlotRspOuterClass.GetWidgetSlotRsp protoData = proto.build();

        this.setData(protoData);
    }
}
