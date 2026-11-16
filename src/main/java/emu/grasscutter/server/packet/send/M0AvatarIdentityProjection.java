package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.IJCEPPNOCNM;
import emu.grasscutter.net.proto.Client72M0SceneAvatarRawOuterClass.FKDEDKIHNDA;
import emu.grasscutter.net.proto.Client72M0SceneTeamRawOuterClass.Raw76429;
import emu.grasscutter.net.proto.Client72M0MotionRawOuterClass.Raw49847;
import emu.grasscutter.net.proto.Client72SceneHandshakeNestedOuterClass.BGDCNKAKGHI;

/** Same-player entry identities and stationary-birth position; not a complete entity/team notice. */
public final class M0AvatarIdentityProjection {
    private M0AvatarIdentityProjection() {}

    public static Raw76429 projectCurrentPlayer(Player player) {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        var avatar = entity.getAvatar();
        var weapon = avatar.getWeaponNotNull();
        var position = entity.getPosition();
        var motion = Raw49847.newBuilder()
                .setHHAFHFEPLPD(BGDCNKAKGHI.newBuilder().setCJMJBINEBAG(position.getX())
                        .setALOKJBKPLGA(position.getY()).setMGICHKNKDKO(position.getZ()))
                // Explicit M0 stationary-birth policy; the required rotation input is still unknown.
                .setDACMFIDFIJA(BGDCNKAKGHI.newBuilder().setCJMJBINEBAG(0f)
                        .setALOKJBKPLGA(0f).setMGICHKNKDKO(0f));
        var localAvatar = FKDEDKIHNDA.newBuilder()
                .setPJAJDHBBMGB(player.getUid())
                .setFBLKPHOCBIK(avatar.getGuid());
        var sceneEntity = IJCEPPNOCNM.newBuilder()
                .setMCPCJJBMHMK(1) // Target enum31801 ProtEntityAvatar.
                .setRawMotion4(motion)
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
