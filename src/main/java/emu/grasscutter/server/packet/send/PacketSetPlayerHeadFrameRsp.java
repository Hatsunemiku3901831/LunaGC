package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerHeadFrameRspOuterClass.SetPlayerHeadFrameRsp;

public class PacketSetPlayerHeadFrameRsp extends BasePacket {
    public PacketSetPlayerHeadFrameRsp(Player player, int retcode) {
        super(PacketOpcodes.SetPlayerHeadFrameRsp);
        setData(
                SetPlayerHeadFrameRsp.newBuilder()
                        .setRetcode(retcode)
                        .setProfilePicture(player.getProfilePicture()));
    }
}
