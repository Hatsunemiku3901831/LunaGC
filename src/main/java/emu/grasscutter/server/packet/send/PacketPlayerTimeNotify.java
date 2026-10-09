package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerTimeNotifyOuterClass.PlayerTimeNotify;

public class PacketPlayerTimeNotify extends BasePacket {

    public PacketPlayerTimeNotify(Player player) {
        super(PacketOpcodes.PlayerTimeNotify);

        var time = player.getPlayerTimeSnapshot();
        PlayerTimeNotify proto =
                PlayerTimeNotify.newBuilder()
                        .setIsPaused(time.paused())
                        .setPlayerTime(time.playerTime())
                        .setServerTime(System.currentTimeMillis())
                        .build();

        this.setData(proto);
    }
}
