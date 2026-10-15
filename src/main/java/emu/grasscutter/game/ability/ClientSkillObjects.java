package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;

import java.util.*;

/**
 * Known client-created skill objects, verified against the current avatar and monster resources.
 */
public final class ClientSkillObjects {
    private static final Map<Integer, Set<Integer>> BY_AVATAR =
            Map.of(
                    10000022, Set.of(41022001, 41022002, 41022003, 41022014),
                    10000116,
                            Set.of(
                                    41116005, 41116006, 41116007, 41116008, 41116009, 41116010,
                                    41116011));
    private static final Map<Integer, Set<Integer>> BY_MONSTER_FAMILY =
            Map.of(
                    200401, Set.of(42004003, 42004004, 42004005, 42004007, 42004008, 42004012),
                    200402, Set.of(42004012),
                    200404, Set.of(42004012),
                    200405, Set.of(42004012),
                    200406, Set.of(42004012),
                    200407, Set.of(42004012));

    private ClientSkillObjects() {}

    public static boolean clientCreates(GameEntity source, int gadgetId) {
        Set<Integer> kinds;
        if (source instanceof EntityAvatar avatar)
            kinds = BY_AVATAR.get(avatar.getAvatar().getAvatarId());
        else if (source instanceof EntityMonster monster && monster.getMonsterData() != null)
            kinds = BY_MONSTER_FAMILY.get(monster.getMonsterData().getId() / 100);
        else return false;
        return kinds != null && kinds.contains(gadgetId);
    }

    /** The scene serializes registration, cleanup and appearance in that order. */
    public static void created(Scene scene, EntityClientGadget created) {
        for (var kinds : BY_AVATAR.values())
            if (kinds.contains(created.getGadgetId())) {
                purge(scene, created.getOwner(), kinds);
                return;
            }
    }

    static void purge(Scene scene, Player player, Set<Integer> kinds) {
        // Resolve before removal: nested shells may point at another retiring server object.
        var shells =
                scene.getEntities().values().stream()
                        .filter(
                                e ->
                                        e instanceof EntityGadget gadget
                                                && !gadget.isAbilityServerOwned()
                                                && kinds.contains(gadget.getGadgetId())
                                                && owned(gadget.getOwner(), player))
                        .toList();
        for (var entity : shells) retire(scene, entity);
    }

    static void retire(Scene scene, GameEntity snapshot) {
        if (scene.getEntities().get(snapshot.getId()) != snapshot) {
            if (snapshot instanceof EntityClientGadget client)
                client.getOwner().getTeamManager().getGadgets().remove(client);
            return;
        }
        if (snapshot instanceof EntityClientGadget) scene.onPlayerDestroyGadget(snapshot.getId());
        else scene.removeEntity(snapshot);
    }

    private static boolean owned(GameEntity source, Player player) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<GameEntity, Boolean>());
        for (int depth = 0; source != null && depth < 32 && seen.add(source); depth++) {
            if (source instanceof EntityAvatar avatar) return avatar.getPlayer() == player;
            if (source instanceof EntityClientGadget client) return client.getOwner() == player;
            if (source instanceof EntityGadget gadget) source = gadget.getOwner();
            else return false;
        }
        return false;
    }
}
