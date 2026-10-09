package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;

public class PacketSetPlayerHeadImageRsp extends BasePacket {

    public PacketSetPlayerHeadImageRsp(Player player) {
        this(player, 0);
    }

    public PacketSetPlayerHeadImageRsp(Player player, int retcode) {
        super(PacketOpcodes.SetPlayerHeadImageRsp);

        SetPlayerHeadImageRsp proto =
                SetPlayerHeadImageRsp.newBuilder()
                        .setProfilePicture(player.getProfilePicture())
                        .setRetcode(retcode)
                        .build();

        this.setData(proto);
    }
}
