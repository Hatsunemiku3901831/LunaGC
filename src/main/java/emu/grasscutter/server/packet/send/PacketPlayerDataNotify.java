package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.Client72PlayerDataNestedOuterClass.Client72PlayerPropValue;
import emu.grasscutter.net.proto.PlayerDataNotifyOuterClass.PlayerDataNotify;

public class PacketPlayerDataNotify extends BasePacket {

    public PacketPlayerDataNotify(Player player) {
        super(PacketOpcodes.PlayerDataNotify, 2);

        int levelProperty = PlayerProperty.PROP_PLAYER_LEVEL.getId();
        var level = Client72PlayerPropValue.newBuilder()
                .setPropertyType(levelProperty)
                .setInt64Value(player.getLevel());
        var data =
                PlayerDataNotify.newBuilder()
                        .setNickname(player.getNickname())
                        .setServerTime(System.currentTimeMillis())
                        .putPropMap(levelProperty, level.build());
        this.setData(data.build());
    }
}
