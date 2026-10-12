package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;

import java.util.Map;

public class PacketAvatarExpeditionStartRsp extends BasePacket {
    public PacketAvatarExpeditionStartRsp(Map<Long, ExpeditionInfo> expeditionInfo) {
        this(expeditionInfo, 0);
    }

    public PacketAvatarExpeditionStartRsp(Map<Long, ExpeditionInfo> expeditionInfo, int retcode) {
        super(PacketOpcodes.AvatarExpeditionStartRsp);

        AvatarExpeditionStartRsp.Builder proto = AvatarExpeditionStartRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        proto.setRetcode(retcode);

        this.setData(proto.build());
    }
}
