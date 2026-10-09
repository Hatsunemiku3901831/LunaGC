package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllUnlockProfileDataRspOuterClass.GetAllUnlockProfileDataRsp;

public class PacketGetAllUnlockProfileDataRsp extends BasePacket {
    public PacketGetAllUnlockProfileDataRsp(Player player) {
        super(PacketOpcodes.GetAllUnlockProfileDataRsp);
        var cosmetics = player.getProfileCosmetics();
        setData(
                GetAllUnlockProfileDataRsp.newBuilder()
                        .addAllProfilePictureList(cosmetics.unlockedPictureIds(player))
                        .addAllProfileFrameList(cosmetics.unlockedFrameIds()));
    }
}
