package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityPlayTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SyncScenePlayTeamEntityNotifyOuterClass.SyncScenePlayTeamEntityNotify;

import java.util.Collection;

public class PacketSyncScenePlayTeamEntityNotify extends BasePacket {

    public PacketSyncScenePlayTeamEntityNotify(Player player) {
        this(player.getScene());
    }

    private PacketSyncScenePlayTeamEntityNotify(Scene scene) {
        this(scene.getId(), scene.getScenePlayBattleManager().getPlayTeamEntities());
    }

    public PacketSyncScenePlayTeamEntityNotify(int sceneId, Collection<EntityPlayTeam> entities) {
        super(PacketOpcodes.SyncScenePlayTeamEntityNotify);

        SyncScenePlayTeamEntityNotify proto =
                SyncScenePlayTeamEntityNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllEntityInfoList(
                                entities.stream().map(EntityPlayTeam::toPlayTeamProto).toList())
                        .build();

        this.setData(proto);
    }
}
