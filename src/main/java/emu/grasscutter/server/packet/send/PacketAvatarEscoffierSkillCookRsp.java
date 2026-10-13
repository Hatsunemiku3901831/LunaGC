package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarEscoffierSkillCookRspOuterClass.AvatarEscoffierSkillCookRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;

import java.util.List;

public final class PacketAvatarEscoffierSkillCookRsp extends BasePacket {
    public PacketAvatarEscoffierSkillCookRsp(
            int retcode,
            List<ItemParam> confirmed,
            int lastRefreshTime,
            int finishedWeeklyCookNum) {
        super(PacketOpcodes.AvatarEscoffierSkillCookRsp);
        var response =
                AvatarEscoffierSkillCookRsp.newBuilder()
                        .setRetcode(retcode)
                        .setLastRefreshTime(lastRefreshTime)
                        .setFinishedWeeklyCookNum(finishedWeeklyCookNum);
        if (retcode == 0 && confirmed != null) response.addAllItemList(confirmed);
        setData(response.build());
    }
}
