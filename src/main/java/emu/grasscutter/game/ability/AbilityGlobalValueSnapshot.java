package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** Server global values carried before the client's full ability initialization. */
public final class AbilityGlobalValueSnapshot {
    private AbilityGlobalValueSnapshot() {}

    public static void append(GameEntity entity, AbilitySyncStateInfo.Builder info) {
        if (entity == null) return;
        var scene = entity.getScene();
        int id = entity.getId();
        long lifetime = entity.getAbilityLifecycleVersion();
        if (!current(entity, scene, id, lifetime)) return;

        var live = new TreeMap<>(entity.getGlobalAbilityValues());
        var names = new HashMap<Integer, String>();
        var ambiguous = new HashSet<Integer>();
        // Retain the whole name set: removing an invalid value must not hide its hash collision.
        live.keySet().forEach(name -> identify(name, names, ambiguous));
        info.getSgvDynamicValueMapList().stream()
                .map(AbilityScalarValueEntry::getKey)
                .filter(AbilityString::hasStr)
                .forEach(key -> identify(key.getStr(), names, ambiguous));

        var merged = new LinkedHashMap<Integer, AbilityScalarValueEntry>();
        live.forEach(
                (name, value) -> {
                    int hash = Utils.abilityHash(name);
                    if (name.startsWith("SGV_")
                            && hash != 0
                            && value != null
                            && Float.isFinite(value)
                            && !ambiguous.contains(hash))
                        merged.put(
                                hash,
                                AbilityScalarValueEntry.newBuilder()
                                        .setKey(AbilityString.newBuilder().setStr(name))
                                        .setFloatValue(value)
                                        .build());
                });
        // Fuel and party-derived values are explicitly supplied by the packet builder.
        // Preserve these entries, including their unknown fields, over any stale map alias.
        for (var entry : info.getSgvDynamicValueMapList()) {
            var key = entry.getKey();
            int hash =
                    key.hasHash()
                            ? key.getHash()
                            : key.hasStr() ? Utils.abilityHash(key.getStr()) : 0;
            if (hash != 0 && Float.isFinite(entry.getFloatValue())) merged.put(hash, entry);
        }
        if (current(entity, scene, id, lifetime))
            info.clearSgvDynamicValueMap().addAllSgvDynamicValueMap(merged.values());
    }

    private static void identify(String name, Map<Integer, String> names, Set<Integer> ambiguous) {
        if (name == null || name.isBlank()) return;
        int hash = Utils.abilityHash(name);
        var old = names.putIfAbsent(hash, name);
        if (old != null && !old.equals(name)) ambiguous.add(hash);
    }

    private static boolean current(GameEntity entity, Scene scene, int id, long lifetime) {
        if (scene == null
                || entity.getScene() != scene
                || entity.getId() != id
                || !entity.isAbilityLifecycleCurrent(lifetime)) return false;
        if (entity instanceof EntityTeam team) {
            var player = team.getPlayer();
            return player != null
                    && player.getTeamManager().getEntity() == team
                    && player.getScene() == scene
                    && player.getWorld() == scene.getWorld();
        }
        if (entity instanceof EntityWorld world)
            return world.getWorld() == scene.getWorld()
                    && world.getWorld().getEntity() == world
                    && world.getWorld().getHost().getWorld() == world.getWorld();
        if (entity instanceof EntityAvatar avatar
                && (avatar.getPlayer() == null
                        || avatar.getPlayer().getScene() != scene
                        || avatar.getPlayer().getWorld() != scene.getWorld())) return false;
        if (entity instanceof EntityWeapon weapon
                && weapon.getCasterAvatar() != null
                && (weapon.getCasterAvatar().getPlayer() == null
                        || weapon.getCasterAvatar().getPlayer().getScene() != scene
                        || weapon.getCasterAvatar().getPlayer().getWorld() != scene.getWorld()))
            return false;

        var found = scene.getEntities().get(id);
        if (found == null) found = scene.getWeaponEntities().get(id);
        if (found == null) found = scene.getScenePlayBattleManager().getPlayTeamEntity(id);
        return found != null
                ? found == entity
                : entity instanceof EntityAvatar avatar && avatar.isInSceneParty();
    }
}
