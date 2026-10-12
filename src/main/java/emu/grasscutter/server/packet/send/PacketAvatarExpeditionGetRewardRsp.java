package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto._AvatarExpeditionRewardInfoOuterClass._AvatarExpeditionRewardInfo;

import java.util.*;

public class PacketAvatarExpeditionGetRewardRsp extends BasePacket {
    public PacketAvatarExpeditionGetRewardRsp(
            Map<Long, ExpeditionInfo> expeditionInfo,
            Collection<_AvatarExpeditionRewardInfo> rewards,
            int retcode) {
        super(PacketOpcodes.AvatarExpeditionGetRewardRsp);

        AvatarExpeditionGetRewardRsp.Builder proto = AvatarExpeditionGetRewardRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        proto.setRetcode(retcode).addAllExpeditionRewardList(rewards);

        this.setData(proto.build());
    }
}
