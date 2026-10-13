package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarEscoffierSkillCookDataNotifyOuterClass.AvatarEscoffierSkillCookDataNotify;

public final class PacketAvatarEscoffierSkillCookDataNotify extends BasePacket {
    public PacketAvatarEscoffierSkillCookDataNotify(
            int lastRefreshTime, int finishedWeeklyCookNum) {
        super(PacketOpcodes.AvatarEscoffierSkillCookDataNotify);
        setData(
                AvatarEscoffierSkillCookDataNotify.newBuilder()
                        .setLastRefreshTime(lastRefreshTime)
                        .setFinishedWeeklyCookNum(finishedWeeklyCookNum)
                        .build());
    }
}
