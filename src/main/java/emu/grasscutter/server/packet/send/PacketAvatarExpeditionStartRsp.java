package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PacketAvatarExpeditionStartRsp extends BasePacket {
    public PacketAvatarExpeditionStartRsp(Map<Long, ExpeditionInfo> expeditionInfo) {
        this(expeditionInfo, 0);
    }

    public PacketAvatarExpeditionStartRsp(Map<Long, ExpeditionInfo> expeditionInfo, int retcode) {
        this(expeditionInfo, retcode, List.of());
    }

    public PacketAvatarExpeditionStartRsp(
            Map<Long, ExpeditionInfo> expeditionInfo,
            int retcode,
            Collection<_AvatarExpeditionBasicInfo> started) {
        super(PacketOpcodes.AvatarExpeditionStartRsp);

        AvatarExpeditionStartRsp.Builder proto = AvatarExpeditionStartRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        proto.setRetcode(retcode);
        proto.addAllExpeditionBasicInfoList(started);

        this.setData(proto.build());
    }
}
