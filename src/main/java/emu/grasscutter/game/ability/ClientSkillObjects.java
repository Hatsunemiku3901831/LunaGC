package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;

import java.util.*;

/** Known client-created skill objects; each avatar policy is verified against current resources. */
public final class ClientSkillObjects {
    private static final Map<Integer, Set<Integer>> BY_AVATAR =
            Map.of(
                    10000022, Set.of(41022001, 41022002, 41022003, 41022014),
                    10000116,
                            Set.of(
                                    41116005, 41116006, 41116007, 41116008, 41116009, 41116010,
                                    41116011));

    private ClientSkillObjects() {}

    public static boolean clientCreates(GameEntity source, int gadgetId) {
        if (!(source instanceof EntityAvatar avatar)) return false;
        var kinds = BY_AVATAR.get(avatar.getAvatar().getAvatarId());
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
