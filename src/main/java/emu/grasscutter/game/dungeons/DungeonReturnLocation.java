package emu.grasscutter.game.dungeons;

import dev.morphia.annotations.Entity;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.ScriptLoader;

import lombok.Getter;

/** A persisted scene/position pair, independent of home return locations. */
@Entity
public final class DungeonReturnLocation {
    @Getter private int sceneId;
    private Position position;

    public DungeonReturnLocation() {}

    private DungeonReturnLocation(int sceneId, Position position) {
        this.sceneId = sceneId;
        this.position = position.clone();
    }

    public Position getPosition() {
        return position == null ? null : position.clone();
    }

    public static DungeonReturnLocation capture(Scene scene, Position position) {
        if (scene == null
                || scene.getId() <= 0
                || scene.getSceneType() != SceneType.SCENE_WORLD
                || !usable(position)) {
            return null;
        }
        return new DungeonReturnLocation(scene.getId(), position);
    }

    public static DungeonReturnLocation resolve(DungeonReturnLocation saved) {
        if (saved != null) {
            var scene = GameData.getSceneDataMap().get(saved.sceneId);
            if (scene != null && scene.getSceneType() == SceneType.SCENE_WORLD) {
                var position =
                        usable(saved.position) ? saved.position : bornPosition(saved.sceneId);
                if (usable(position)) return new DungeonReturnLocation(saved.sceneId, position);
            }
        }
        var born = bornPosition(3);
        return new DungeonReturnLocation(3, usable(born) ? born : GameConstants.START_POSITION);
    }

    private static boolean usable(Position position) {
        return position != null
                && Float.isFinite(position.getX())
                && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ())
                && !position.equal3d(Position.ZERO);
    }

    private static Position bornPosition(int sceneId) {
        try {
            var meta = ScriptLoader.getSceneMeta(sceneId);
            return meta == null || meta.config == null ? null : meta.config.born_pos;
        } catch (RuntimeException error) {
            Grasscutter.getLogger()
                    .warn("Cannot read dungeon return scene {} birth position", sceneId, error);
            return null;
        }
    }
}
