package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.IJCEPPNOCNM;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.NAALIELJILH;
import emu.grasscutter.net.proto.Client72M0SceneAvatarRawOuterClass.FKDEDKIHNDA;
import emu.grasscutter.net.proto.Client72M0SceneTeamRawOuterClass.Raw76429;
import emu.grasscutter.net.proto.Client72M0MotionRawOuterClass.Raw49847;
import emu.grasscutter.net.proto.Client72SceneHandshakeNestedOuterClass.BGDCNKAKGHI;

/** Same-player entry identities and stationary-birth position; not a complete entity/team notice. */
public final class M0AvatarIdentityProjection {
    private M0AvatarIdentityProjection() {}

    /** Proven root-list envelope only; child tag2 and complete appearance semantics remain unknown. */
    public static NAALIELJILH projectCurrentPlayerAppearance(Player player) {
        return NAALIELJILH.newBuilder()
                .addNKBMECCPKPJ(projectCurrentPlayer(player).getMPHLKPODDHK())
                .setBBHDLEGOLPI(12) // Target VisionBorn for the explicitly limited first spawn.
                .build();
    }

    public static Raw76429 projectCurrentPlayer(Player player) {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        var avatar = entity.getAvatar();
        var weapon = avatar.getWeaponNotNull();
        var position = entity.getPosition();
        var rotation = entity.getRotation();
        if (rotation.getX() != 0f || rotation.getY() != 0f || rotation.getZ() != 0f)
            throw new IllegalStateException("M0 rotation projection supports zero facing only; nonzero units remain unverified");
        var motion = Raw49847.newBuilder()
                .setHHAFHFEPLPD(BGDCNKAKGHI.newBuilder().setCJMJBINEBAG(position.getX())
                        .setALOKJBKPLGA(position.getY()).setMGICHKNKDKO(position.getZ()))
                .setHPLBLGJLIFF(BGDCNKAKGHI.getDefaultInstance())
                // Explicit M0 stationary-birth velocity; the zero rotation message remains present.
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
