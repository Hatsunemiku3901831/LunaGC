package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.Client72SceneHandshakeNestedOuterClass.BGDCNKAKGHI;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PlayerEnterSceneNotifyOuterClass.PlayerEnterSceneNotify;
import emu.grasscutter.utils.Utils;

public class PacketPlayerEnterSceneNotify extends BasePacket {
    public PacketPlayerEnterSceneNotify(Player player) {
        super(PacketOpcodes.PlayerEnterSceneNotify);
        setData(createEntry(player, player.getSceneId(), player.getPosition(), EnterType.EnterType_ENTER_SELF));
    }

    public PacketPlayerEnterSceneNotify(
            Player player, EnterType type, EnterReason reason, int newScene, Position newPos) {
        this(player, player, type, reason, newScene, newPos);
    }

    public PacketPlayerEnterSceneNotify(Player player, TeleportProperties properties) {
        super(PacketOpcodes.PlayerEnterSceneNotify);
        setData(createEntry(player, properties.getSceneId(), properties.getTeleportTo(), properties.getEnterType()));
    }

    public PacketPlayerEnterSceneNotify(
            Player player, Player target, EnterType type, EnterReason reason, int newScene, Position newPos) {
        this(player, target, TeleportProperties.builder().enterType(type).enterReason(reason)
                .sceneId(newScene).teleportTo(newPos).build());
    }

    public PacketPlayerEnterSceneNotify(Player player, Player target, TeleportProperties properties) {
        this(player, properties);
    }

    public PacketPlayerEnterSceneNotify(Player player, World destination, TeleportProperties properties) {
        this(player, properties);
    }

    public PacketPlayerEnterSceneNotify(
            Player player, int targetUid, TeleportProperties properties, boolean other) {
        super(PacketOpcodes.PlayerEnterSceneNotify);
        var type = other ? EnterType.EnterType_ENTER_OTHER_HOME : EnterType.EnterType_ENTER_SELF_HOME;
        setData(createEntry(player, properties.getSceneId(), properties.getTeleportTo(), type));
    }

    private static PlayerEnterSceneNotify createEntry(Player player, int sceneId, Position pos, EnterType type) {
        player.setSceneLoadState(SceneLoadState.LOADING);
        player.setEnterSceneToken(Utils.randomRange(1000, 99999));
        // Only these four destinations have independent target consumer evidence.
        // Owner, world level, time, dungeon and region fields remain unnamed.
        return PlayerEnterSceneNotify.newBuilder()
                .setSceneId((sceneId - 0xC0E3) ^ 0x2D03)
                .setType(type.getNumber())
                .setEnterSceneToken((player.getEnterSceneToken() ^ 0xE011) - 0x5889)
                .setPos(BGDCNKAKGHI.newBuilder().setCJMJBINEBAG(pos.getX())
                        .setALOKJBKPLGA(pos.getY()).setMGICHKNKDKO(pos.getZ()))
                .build();
    }
}
