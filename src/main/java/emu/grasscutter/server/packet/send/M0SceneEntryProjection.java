package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0SceneEntryOuterClass.*;

/** Same-session identities only; this does not close the remaining scene initialization wire. */
final class M0SceneEntryProjection {
    private M0SceneEntryProjection() {}

    static NOCHHDCLKDJ project(Player player) {
        var result = NOCHHDCLKDJ.newBuilder()
                .setOLCJCLBMMCF(player.getTeamManager().getCurrentAvatarEntity().getId())
                .setMCCMGLDOECH(player.getEnterSceneToken())
                .setLFLOLJADPBI(JLILNBLGMIP.newBuilder()
                        .setHKLILOKPKBJ(player.getWorld().getHostPeerId())
                        .setLLHEBIHGDAE(player.getWorld().getLevelEntityId()))
                .setAEKMONHBHAA(BINIKNHCLIA.newBuilder()
                        .setJNBCPNHJCNI(player.getTeamManager().getEntity().getId()));
        for (var entity : player.getTeamManager().getActiveTeam()) {
            var avatar = entity.getAvatar();
            var weapon = avatar.getWeaponNotNull();
            result.addDIGAMAKGCPA(CKMLFOFDEHN.newBuilder()
                    .setOKMEHFHNFCG(avatar.getGuid())
                    .setFDDDFIAINCE(entity.getId())
                    .setNPKKCHABEJH(weapon.getGuid())
                    .setIEDJNHILJBF(entity.getWeaponEntityId()));
        }
        return result.build();
    }
}
