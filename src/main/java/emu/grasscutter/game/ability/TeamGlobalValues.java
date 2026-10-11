package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.UpdateTypeOuterClass.UpdateType;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.server.packet.send.PacketServerUpdateGlobalValueNotify;
import emu.grasscutter.utils.Utils;

import java.util.LinkedHashSet;

/** Scene-local Lua operations on the same team values used by ability evaluation. */
public final class TeamGlobalValues {
    private TeamGlobalValues() {}

    private static Player owner(Scene scene, int uid) {
        if (scene == null || uid <= 0) return null;
        return scene.getPlayers().stream()
                .filter(
                        player ->
                                player.getUid() == uid
                                        && player.getScene() == scene
                                        && player.getWorld() == scene.getWorld())
                .findFirst()
                .orElse(null);
    }

    private static boolean current(Scene scene, Player player, EntityTeam team, long version) {
        return player.getScene() == scene
                && player.getWorld() == scene.getWorld()
                && scene.getPlayers().contains(player)
                && player.getTeamManager().getEntity() == team
                && team.isAbilityLifecycleCurrent(version);
    }

    public static float get(Scene scene, int uid, String key) {
        var player = owner(scene, uid);
        if (player == null || key == null || key.isBlank()) return 0;
        var team = player.getTeamManager().getEntity();
        if (team == null) return 0;
        synchronized (team) {
            if (!current(scene, player, team, team.getAbilityLifecycleVersion())) return 0;
            float value = team.getGlobalAbilityValues().getOrDefault(key, 0f);
            return Float.isFinite(value) ? value : 0;
        }
    }

    public static int uidByEntityId(Scene scene, int entityId) {
        if (scene == null) return 0;
        for (var player : scene.getPlayers()) {
            var team = player.getTeamManager().getEntity();
            if (team != null
                    && team.getId() == entityId
                    && current(scene, player, team, team.getAbilityLifecycleVersion()))
                return player.getUid();
        }
        return 0;
    }

    public static int add(Scene scene, int[] uids, String key, double delta) {
        if (scene == null) return -1;
        // Scene-play modifier checks acquire the scene monitor. Match callback lock order
        // before acquiring a team or modifier map, including non-script callers.
        synchronized (scene) {
            return update(scene, uids, key, delta, UpdateType.UpdateType_ADD);
        }
    }

    public static int set(Scene scene, int[] uids, String key, double value) {
        if (scene == null) return -1;
        synchronized (scene) {
            return update(scene, uids, key, value, UpdateType.UpdateType_SET);
        }
    }

    public static float getServer(Scene scene, int uid, String key) {
        var player = owner(scene, uid);
        if (player == null || key == null || key.isBlank()) return 0;
        var team = player.getTeamManager().getEntity();
        if (team == null) return 0;
        synchronized (team) {
            if (!currentServer(
                    scene, player, team, uid, team.getId(), team.getAbilityLifecycleVersion()))
                return 0;
            float value = team.getGlobalAbilityValues().getOrDefault(key, 0f);
            return Float.isFinite(value) ? value : 0;
        }
    }

    public static int setServer(Scene scene, int uid, String key, double value) {
        if (scene == null) return -1;
        synchronized (scene) {
            return updateServer(scene, uid, key, value, false);
        }
    }

    public static int addServer(Scene scene, int uid, String key, double delta) {
        if (scene == null) return -1;
        synchronized (scene) {
            return updateServer(scene, uid, key, delta, true);
        }
    }

    private static boolean currentServer(
            Scene scene, Player player, EntityTeam team, int uid, int id, long version) {
        return current(scene, player, team, version)
                && player.getUid() == uid
                && team.getPlayer() == player
                && team.getScene() == scene
                && team.getId() == id;
    }

    private static int updateServer(Scene scene, int uid, String key, double amount, boolean add) {
        float operand = (float) amount;
        if (key == null
                || !key.startsWith("SGV_")
                || Utils.abilityHash(key) == 0
                || !Float.isFinite(operand)) return -1;
        var player = owner(scene, uid);
        if (player == null) return -1;
        var team = player.getTeamManager().getEntity();
        if (team == null) return -1;
        synchronized (team) {
            long version = team.getAbilityLifecycleVersion();
            int id = team.getId();
            if (!currentServer(scene, player, team, uid, id, version)) return -1;
            Float previous = team.getGlobalAbilityValues().get(key);
            float value = operand;
            if (add && previous != null) {
                if (!Float.isFinite(previous)) return -1;
                // The original server creates a missing value directly, then bounds later
                // additions.
                value = Math.max(0, Math.min(Float.MAX_VALUE, previous + operand));
            }
            team.getGlobalAbilityValues().put(key, value);
            float snapshot = value;
            scene.runWhenHostInitialized(
                    () -> {
                        synchronized (team) {
                            if (currentServer(scene, player, team, uid, id, version))
                                scene.broadcastPacket(
                                        new PacketServerGlobalValueChangeNotify(id, key, snapshot));
                        }
                    });
            team.onAbilityValueUpdate();
        }
        return 0;
    }

    private static int update(Scene scene, int[] uids, String key, double amount, UpdateType type) {
        float operand = (float) amount;
        if (scene == null
                || uids == null
                || key == null
                || key.isBlank()
                || Utils.abilityHash(key) == 0
                || !Float.isFinite(operand)) return -1;
        var recipients = new LinkedHashSet<Integer>();
        for (int uid : uids) {
            if (uid <= 0) return -1;
            recipients.add(uid);
        }
        for (int uid : recipients) {
            var player = owner(scene, uid);
            if (player == null) continue;
            var team = player.getTeamManager().getEntity();
            if (team == null) continue;
            synchronized (team) {
                long version = team.getAbilityLifecycleVersion();
                if (!current(scene, player, team, version)) continue;
                float previous = team.getGlobalAbilityValues().getOrDefault(key, 0f);
                float value = type == UpdateType.UpdateType_ADD ? previous + operand : operand;
                if (!Float.isFinite(value)) return -1;
                team.getGlobalAbilityValues().put(key, value);
                player.sendPacket(
                        new PacketServerUpdateGlobalValueNotify(team.getId(), key, type, operand));
                team.onAbilityValueUpdate();
            }
        }
        return 0;
    }
}
