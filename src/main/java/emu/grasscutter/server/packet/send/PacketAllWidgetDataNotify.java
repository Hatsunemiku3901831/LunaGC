package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AllWidgetDataNotifyOuterClass.AllWidgetDataNotify;

import java.util.List;

public class PacketAllWidgetDataNotify extends BasePacket {

    public PacketAllWidgetDataNotify(Player player) {
        super(PacketOpcodes.AllWidgetDataNotify);

        // Slot state is authoritative; other widget subsystems retain their existing empty data.

        AllWidgetDataNotify.Builder proto =
                AllWidgetDataNotify.newBuilder()
                        .setLunchBoxData(LunchBoxDataOuterClass.LunchBoxData.newBuilder().build())
                        .addAllOneoffGatherPointDetectorDataList(List.of())
                        .addAllCoolDownGroupDataList(List.of())
                        .addAllAnchorPointList(List.of())
                        .addAllClientCollectorDataList(List.of())
                        .addAllNormalCoolDownDataList(List.of());

        proto.addAllSlotList(player.getWidgets().slots());

        AllWidgetDataNotify protoData = proto.build();

        this.setData(protoData);
    }
}
