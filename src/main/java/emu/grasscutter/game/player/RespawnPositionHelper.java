package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.world.Position;

/** Nearest usable waypoint in the player's current scene, without changing exploration progress. */
public final class RespawnPositionHelper {
    private RespawnPositionHelper() {}

    public static Position find(Player player) {
        Position death = player.getPosition().clone();
        int sceneId = player.getSceneId();
        Position best = null;
        int bestId = Integer.MAX_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Integer id : player.getUnlockedScenePoints(sceneId)) {
            if (id == null || player.isScenePointForceLocked(sceneId, id)) continue;
            var entry = GameData.getScenePointEntryById(sceneId, id);
            if (entry == null || entry.getSceneId() != sceneId) continue;
            var point = entry.getPointData();
            if (point == null
                    || point.getId() != id
                    || !isWaypoint(point.getType())
                    || point.getTranSceneId() != 0 && point.getTranSceneId() != sceneId) continue;
            Position destination = point.getTranPos();
            if (!finite(destination)) destination = point.getPos();
            if (!finite(destination)) continue;
            double x = (double) destination.getX() - death.getX();
            double y = (double) destination.getY() - death.getY();
            double z = (double) destination.getZ() - death.getZ();
            double distance = x * x + y * y + z * z;
            if (distance < bestDistance || distance == bestDistance && id < bestId) {
                best = destination.clone();
                bestId = id;
                bestDistance = distance;
            }
        }
        return best == null ? death : best;
    }

    private static boolean isWaypoint(String type) {
        return "TransPointNormal".equals(type)
                || "SceneTransPoint".equals(type)
                || "TransPoint".equals(type);
    }

    private static boolean finite(Position point) {
        return point != null
                && Float.isFinite(point.getX())
                && Float.isFinite(point.getY())
                && Float.isFinite(point.getZ());
    }
}
