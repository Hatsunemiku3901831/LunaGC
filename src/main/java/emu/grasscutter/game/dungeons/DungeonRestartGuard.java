package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;

/** Admission for the empty 7.1 domain retry request after collecting rewards. */
public final class DungeonRestartGuard {
    private DungeonRestartGuard() {}

    public static boolean mayRestart(Player player, byte[] payload) {
        if (player == null || payload == null || payload.length != 0 || player.getWorld() == null)
            return false;
        var scene = player.getScene();
        if (scene == null
                || scene.getSceneType() != SceneType.SCENE_DUNGEON
                || scene.getPlayers().size() != 1
                || scene.getPlayers().get(0) != player) return false;
        var manager = scene.getDungeonManager();
        if (manager == null || manager.getDungeonData() == null) return false;
        var data = manager.getDungeonData();
        var type = data.getSubType();
        return data.getId() > 0
                && data.getSceneId() == scene.getId()
                && (type == DungeonSubType.DUNGEON_SUB_TALENT
                        || type == DungeonSubType.DUNGEON_SUB_WEAPON
                        || type == DungeonSubType.DUNGEON_SUB_RELIQUARY)
                && !manager.isTowerDungeon()
                && manager.isFinishedSuccessfully()
                && manager.hasClaimedDomainReward(player.getUid());
    }
}
