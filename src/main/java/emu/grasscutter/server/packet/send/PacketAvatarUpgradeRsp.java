package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarUpgradeRspOuterClass.AvatarUpgradeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.Map;

public class PacketAvatarUpgradeRsp extends BasePacket {
    public PacketAvatarUpgradeRsp(Avatar avatar, Retcode result) {
        super(PacketOpcodes.AvatarUpgradeRsp);
        setData(
                AvatarUpgradeRsp.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setOldLevel(avatar.getLevel())
                        .setCurLevel(avatar.getLevel())
                        .setRetcode(result.getNumber()));
    }

    public PacketAvatarUpgradeRsp(Avatar avatar, int oldLevel, Map<Integer, Float> oldFightPropMap) {
        super(PacketOpcodes.AvatarUpgradeRsp);

        this.buildHeader(0);

        AvatarUpgradeRsp proto =
                AvatarUpgradeRsp.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setOldLevel(oldLevel)
                        .setCurLevel(avatar.getLevel())
                        // .putAllOldFightPropMap(oldFightPropMap)
                        // .putAllCurFightPropMap(avatar.getFightProperties())
                        .build();

        this.setData(proto);
    }
}
