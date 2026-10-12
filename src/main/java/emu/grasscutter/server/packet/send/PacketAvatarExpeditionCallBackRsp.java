package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionCallBackRspOuterClass.AvatarExpeditionCallBackRsp;

import java.util.Map;

public class PacketAvatarExpeditionCallBackRsp extends BasePacket {
    public PacketAvatarExpeditionCallBackRsp(Map<Long, ExpeditionInfo> expeditionInfo) {
        this(expeditionInfo, 0);
    }

    public PacketAvatarExpeditionCallBackRsp(
            Map<Long, ExpeditionInfo> expeditionInfo, int retcode) {
        super(PacketOpcodes.AvatarExpeditionCallBackRsp);

        AvatarExpeditionCallBackRsp.Builder proto = AvatarExpeditionCallBackRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        proto.setRetcode(retcode);

        this.setData(proto.build());
    }
}
