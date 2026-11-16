package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.IJCEPPNOCNM;
import emu.grasscutter.net.proto.Client72M0SceneAvatarRawOuterClass.FKDEDKIHNDA;
import emu.grasscutter.net.proto.Client72M0SceneTeamRawOuterClass.Raw76429;

/** Same-player UID/GUID/entity relations only; this row is not a complete entity/team notification. */
public final class M0AvatarIdentityProjection {
    private M0AvatarIdentityProjection() {}

    public static Raw76429 projectCurrentPlayer(Player player) {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        var avatar = entity.getAvatar();
        var weapon = avatar.getWeaponNotNull();
        var localAvatar = FKDEDKIHNDA.newBuilder()
                .setPJAJDHBBMGB(player.getUid())
                .setFBLKPHOCBIK(avatar.getGuid());
        var sceneEntity = IJCEPPNOCNM.newBuilder()
                .setMCPCJJBMHMK(1) // Target enum31801 ProtEntityAvatar.
                .setRawAvatar10(localAvatar);
        return Raw76429.newBuilder()
                .setHCIEHNNJNCF(player.getUid())
                .setOKMEHFHNFCG(avatar.getGuid())
                .setLLHEBIHGDAE(entity.getId())
                .setNPKKCHABEJH(weapon.getGuid())
                .setIEDJNHILJBF(entity.getWeaponEntityId())
                .setJMHKHAPJADA(player.getSceneId())
                .setMPHLKPODDHK(sceneEntity)
                .build();
    }
}
