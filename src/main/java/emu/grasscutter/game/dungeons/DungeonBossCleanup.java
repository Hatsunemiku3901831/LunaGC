package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.MonsterType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;

import java.util.*;

/** Scene-owned boss model cleanup, based on AstaPS's WeeklyBossModelCleanup. */
public final class DungeonBossCleanup {
    private final Scene scene;
    private final Map<Integer, RemovedBoss> removedBosses = new LinkedHashMap<>();

    private static final class RemovedBoss {
        final EntityMonster boss;
        final Map<Integer, GameEntity> owners = new LinkedHashMap<>();
        boolean modelRemoved;

        RemovedBoss(EntityMonster boss) {
            this.boss = boss;
            owners.put(boss.getId(), boss);
        }
    }

    public DungeonBossCleanup(Scene scene) {
        this.scene = scene;
    }

    private boolean available() {
        return scene.getSceneType() == SceneType.SCENE_DUNGEON
                && !scene.getScriptManager().isDestroyed();
    }

    private boolean boss(EntityMonster entity) {
        return entity != null
                && entity.getId() != 0
                && entity.getScene() == scene
                && entity.getMonsterData() != null
                && entity.getMonsterData().getType() == MonsterType.MONSTER_BOSS;
    }

    /** Retain ownership before the inline death event can remove or replace its origin. */
    public void rememberDeath(EntityMonster entity) {
        synchronized (scene) {
            if (!available() || !boss(entity) || scene.getEntityById(entity.getId()) != entity)
                return;
            var removed = new RemovedBoss(entity);
            removed.owners.putAll(ownedEntities(removed));
            removedBosses.put(entity.getId(), removed);
        }
    }

    /** Leave servant monsters for the ordinary onDeath path and its kill/quest accounting. */
    public boolean removeAfterDeath(EntityMonster entity) {
        synchronized (scene) {
            var removed = removedBosses.get(entity.getId());
            if (!available() || removed == null || removed.boss != entity) return false;
            var current = scene.getEntityById(entity.getId());
            if (current != null && current != entity) return false;
            removeOwned(removed, false);
            if (current == entity) scene.removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
            else if (!removed.modelRemoved)
                scene.broadcastPacket(
                        new PacketSceneEntityDisappearNotify(
                                entity, VisionType.VisionType_VISION_REMOVE));
            removed.modelRemoved = true;
            return true;
        }
    }

    /**
     * The successful current dungeon ends combat; removing leftovers does not manufacture kills.
     */
    public void onSettle(DungeonManager manager, DungeonEndReason reason) {
        synchronized (scene) {
            if (reason != DungeonEndReason.COMPLETED
                    || !available()
                    || scene.getDungeonManager() != manager
                    || scene.getWorld().getScenes().get(scene.getId()) != scene) return;
            for (var entity : List.copyOf(scene.getEntities().values())) {
                if (entity instanceof EntityMonster monster && boss(monster)) {
                    var previous = removedBosses.get(entity.getId());
                    if (previous == null || previous.boss != monster)
                        removedBosses.put(entity.getId(), new RemovedBoss(monster));
                }
            }
            for (var removed : removedBosses.values()) removeOwned(removed, true);
        }
    }

    /** A delayed client creation must not restore a gadget whose boss/owner has already gone. */
    public boolean removeLateGadget(EntityClientGadget gadget) {
        synchronized (scene) {
            if (!available()
                    || gadget.getScene() != scene
                    || scene.getEntityById(gadget.getId()) != gadget) return false;
            for (var removed : removedBosses.values()) {
                if (!goneOwner(removed, gadget.getOwnerEntityId())
                        && !goneOwner(removed, gadget.getOriginalOwnerEntityId())) continue;
                removed.owners.put(gadget.getId(), gadget);
                removeGadgets(List.of(gadget));
                return true;
            }
            return false;
        }
    }

    private boolean goneOwner(RemovedBoss removed, int id) {
        return id != 0 && removed.owners.containsKey(id) && scene.getEntityById(id) == null;
    }

    private void removeOwned(RemovedBoss removed, boolean settled) {
        var owned = ownedEntities(removed);
        removed.owners.putAll(owned);
        var gadgets =
                owned.values().stream()
                        .filter(EntityClientGadget.class::isInstance)
                        .map(EntityClientGadget.class::cast)
                        .toList();
        removeGadgets(gadgets);
        if (settled) {
            var monsters = owned.values().stream().filter(EntityMonster.class::isInstance).toList();
            scene.removeEntities(monsters, VisionType.VisionType_VISION_REMOVE);
            if (scene.getEntityById(removed.boss.getId()) != removed.boss)
                removed.modelRemoved = true;
        }
    }

    private void removeGadgets(List<EntityClientGadget> gadgets) {
        var current =
                gadgets.stream()
                        .filter(
                                gadget ->
                                        gadget.getScene() == scene
                                                && scene.getEntityById(gadget.getId()) == gadget)
                        .toList();
        scene.removeEntities(
                new ArrayList<GameEntity>(current), VisionType.VisionType_VISION_REMOVE);
        for (var gadget : current)
            if (gadget.getOwner() != null)
                gadget.getOwner().getTeamManager().getGadgets().remove(gadget);
    }

    private Map<Integer, GameEntity> ownedEntities(RemovedBoss removed) {
        var live = new LinkedHashMap<Integer, GameEntity>();
        for (var entity : scene.getEntities().values())
            if (entity.getScene() == scene && scene.getEntityById(entity.getId()) == entity)
                live.put(entity.getId(), entity);
        var children = new HashMap<Integer, List<GameEntity>>();
        for (var entity : live.values()) {
            if (entity instanceof EntityClientGadget gadget) {
                link(children, gadget.getOwnerEntityId(), gadget);
                link(children, gadget.getOriginalOwnerEntityId(), gadget);
            } else if (entity instanceof EntityMonster monster)
                link(children, monster.getOwnerEntityId(), monster);
        }
        var possibleOwners = new ArrayList<>(live.values());
        possibleOwners.addAll(removed.owners.values());
        for (var owner : possibleOwners)
            if (owner instanceof EntityMonster monster
                    && monster.getScene() == scene
                    && (scene.getEntityById(owner.getId()) == null
                            || scene.getEntityById(owner.getId()) == owner))
                for (var servant : monster.getSummonTagMap().values())
                    if (servant != null && live.get(servant.getId()) == servant)
                        link(children, owner.getId(), servant);
        var pending = new ArrayDeque<Integer>();
        for (var entry : removed.owners.entrySet()) {
            var current = live.get(entry.getKey());
            if (current == null && scene.getEntityById(entry.getKey()) == null
                    || current == entry.getValue()) pending.add(entry.getKey());
        }
        var seen = new HashSet<Integer>();
        var result = new LinkedHashMap<Integer, GameEntity>();
        while (!pending.isEmpty()) {
            int id = pending.removeFirst();
            if (!seen.add(id)) continue;
            var entity = live.get(id);
            if (entity instanceof EntityClientGadget || entity instanceof EntityMonster)
                result.put(id, entity);
            for (var child : children.getOrDefault(id, List.of())) pending.addLast(child.getId());
        }
        return result;
    }

    private static void link(Map<Integer, List<GameEntity>> children, int owner, GameEntity child) {
        if (owner != 0) children.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(child);
    }
}
