package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto._AvatarExtraLevelUpgradeRspOuterClass._AvatarExtraLevelUpgradeRsp;

public final class PacketAvatarExtraLevelUpgradeRsp extends BasePacket {
    public PacketAvatarExtraLevelUpgradeRsp(
            long guid, int oldLevel, int currentLevel, Retcode result) {
        super(PacketOpcodes._AvatarExtraLevelUpgradeRsp);
        setData(
                _AvatarExtraLevelUpgradeRsp.newBuilder()
                        .setAvatarGuid(guid)
                        .setOldLevel(oldLevel)
                        .setCurLevel(currentLevel)
                        .setRetcode(result.getNumber()));
    }
}
