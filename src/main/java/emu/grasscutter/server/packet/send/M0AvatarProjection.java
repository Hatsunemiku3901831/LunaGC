package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.Client72AvatarWireOuterClass.*;

/** Target-evidenced fields sourced from the same live avatar, item and team instances. */
final class M0AvatarProjection {
    private M0AvatarProjection() {}

    static ILEPMBEJKIA project(Player player) {
        var result = ILEPMBEJKIA.newBuilder();
        for (var avatar : player.getAvatars().getAvatars().values()) {
            var row = ONGOHFDHHNI.newBuilder()
                    .setNLNJDCDLHHE(avatar.getAvatarId())
                    .setFBLKPHOCBIK(avatar.getGuid())
                    .setDFENDOGMEAD(avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0 ? 1 : 2)
                    .setBLBHOJMCOJN(avatar.getSkillDepotId());
            putProperty(row, 1001, avatar.getExp());
            putProperty(row, 1002, avatar.getPromoteLevel());
            putProperty(row, 4001, avatar.getLevel());
            avatar.getEquips().values().forEach(item -> row.addAODNDDBPMFB(item.getGuid()));
            for (var property : new FightProperty[] {FightProperty.FIGHT_PROP_BASE_HP,
                    FightProperty.FIGHT_PROP_BASE_ATTACK, FightProperty.FIGHT_PROP_BASE_DEFENSE,
                    FightProperty.FIGHT_PROP_CUR_HP, FightProperty.FIGHT_PROP_MAX_HP,
                    FightProperty.FIGHT_PROP_CUR_ATTACK, FightProperty.FIGHT_PROP_CUR_DEFENSE}) {
                row.putADJINCFKMML(property.getId(), avatar.getFightProperty(property));
            }
            result.addJGECGMPNGBD(row);
        }
        var manager = player.getTeamManager();
        manager.getTeams().forEach((id, team) -> {
            var row = CFCDAEBPLOG.newBuilder().setMGIDEJKCAKD(team.getName());
            for (int avatarId : team.getAvatars()) {
                Avatar avatar = player.getAvatars().getAvatarById(avatarId);
                row.addPIDCNPCLBCE(avatar.getGuid());
            }
            result.putCNCCIILAJNJ(id, row.build());
        });
        result.setOCIKDBJPOAJ(manager.getCurrentTeamId());
        return result.build();
    }

    private static void putProperty(ONGOHFDHHNI.Builder avatar, int id, long value) {
        avatar.putIKIFIBAEDGD(id, CODAEKFNEEE.newBuilder()
                .setNOEEBKJAFAG(id).setBNJKCHONGGEInt64(value).build());
    }
}
