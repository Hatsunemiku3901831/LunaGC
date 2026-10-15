package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.SceneTeamAvatarOuterClass.SceneTeamAvatar;
import emu.grasscutter.net.proto.SceneTeamUpdateNotifyOuterClass.SceneTeamUpdateNotify;

public class PacketSceneTeamUpdateNotify extends BasePacket {
    public PacketSceneTeamUpdateNotify(Player player) {
        super(PacketOpcodes.SceneTeamUpdateNotify);

        var proto = SceneTeamUpdateNotify.newBuilder().setIsInMp(player.getWorld().isMultiplayer());

        for (var p : player.getWorld().getPlayers()) {
            for (var entityAvatar : p.getTeamManager().getActiveTeam(true)) {
                var abilityInfo = AbilitySyncStateInfo.newBuilder();
                PacketPlayerEnterSceneInfoNotify.buildAvatarAbilityVars(entityAvatar, abilityInfo);
                entityAvatar.getAbilityRoots().appendOverrides(abilityInfo);
                var weapon = entityAvatar.getAvatar().getWeaponNotNull().getWeaponEntity();
                var avatarProto =
                        SceneTeamAvatar.newBuilder()
                                .setPlayerUid(p.getUid())
                                .setAvatarGuid(entityAvatar.getAvatar().getGuid())
                                .setSceneId(p.getSceneId())
                                .setEntityId(entityAvatar.getId())
                                .setSceneEntityInfo(entityAvatar.toProto())
                                .setWeaponGuid(entityAvatar.getAvatar().getWeaponNotNull().getGuid())
                                .setWeaponEntityId(entityAvatar.getWeaponEntityId())
                                .setIsPlayerCurAvatar(p.getTeamManager().getCurrentAvatarEntity() == entityAvatar)
                                .setIsOnScene(p.getTeamManager().getCurrentAvatarEntity() == entityAvatar)
                                .setAvatarAbilityInfo(abilityInfo)
                                .setWeaponAbilityInfo(weapon != null ? weapon.getAbilityRoots().toSyncState()
                                        : AbilitySyncStateInfo.getDefaultInstance())
                                .setAbilityControlBlock(entityAvatar.getAbilityControlBlock())
                                .addAllTpsWeaponList(emu.grasscutter.game.tps.TpsWeaponSystem.getSceneWeaponInfos(entityAvatar.getAvatar()));

                if (player.getWorld().isMultiplayer()) {
                    avatarProto.setAvatarInfo(entityAvatar.getAvatar().toProto());
                    avatarProto.setSceneAvatarInfo(entityAvatar.getSceneAvatarInfo()); // why mihoyo...
                }

                proto.addSceneTeamAvatarList(avatarProto);
            }
        }

        this.setData(proto);
    }
}
