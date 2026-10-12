package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionCatalog;
import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionAllDataRspOuterClass.AvatarExpeditionAllDataRsp;

import java.util.*;
import java.util.stream.Collectors;

public class PacketAvatarExpeditionAllDataRsp extends BasePacket {
    public PacketAvatarExpeditionAllDataRsp(
            Map<Long, ExpeditionInfo> expeditionInfo, int expeditionCountLimit) {
        this(expeditionInfo, expeditionCountLimit, ExpeditionCatalog.ids());
    }

    public PacketAvatarExpeditionAllDataRsp(
            Map<Long, ExpeditionInfo> expeditionInfo,
            int expeditionCountLimit,
            List<Integer> openExpeditionList) {
        super(PacketOpcodes.AvatarExpeditionAllDataRsp);

        this.setData(
                AvatarExpeditionAllDataRsp.newBuilder()
                        .addAllOpenExpeditionList(openExpeditionList)
                        .setExpeditionCountLimit(expeditionCountLimit)
                        .putAllExpeditionInfoMap(
                                expeditionInfo.entrySet().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        e -> e.getKey(),
                                                        e -> e.getValue().toProto())))
                        .build());
    }
}
