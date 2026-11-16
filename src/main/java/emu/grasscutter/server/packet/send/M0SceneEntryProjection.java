package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0SceneEntryOuterClass.*;
import emu.grasscutter.net.proto.Client72M0SharedSceneRawOuterClass.KOKHLKFCIMG;

/** Same-session identities only; this does not close the remaining scene initialization wire. */
final class M0SceneEntryProjection {
    private M0SceneEntryProjection() {}

    static NOCHHDCLKDJ project(Player player) {
        if (!player.getWorld().getEntity().getInstancedAbilities().isEmpty()
                || !player.getTeamManager().getEntity().getInstancedAbilities().isEmpty())
            throw new IllegalStateException("M0 minimal scene control requires its explicit empty MP/team ability policy");
        var result = NOCHHDCLKDJ.newBuilder()
                .setOLCJCLBMMCF(player.getTeamManager().getCurrentAvatarEntity().getId())
                .setMCCMGLDOECH(player.getEnterSceneToken())
                .setLFLOLJADPBI(JLILNBLGMIP.newBuilder()
                        .setHKLILOKPKBJ(player.getWorld().getHostPeerId())
                        .setLLHEBIHGDAE(player.getWorld().getLevelEntityId()))
                .setAEKMONHBHAA(BINIKNHCLIA.newBuilder()
                        .setEOFFDMLNFIM(KOKHLKFCIMG.getDefaultInstance())
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
