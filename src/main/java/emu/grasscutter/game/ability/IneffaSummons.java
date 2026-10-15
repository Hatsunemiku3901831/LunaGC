package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;

import java.util.*;

/**
 * Client-created Ineffa skill objects, verified against the current gadget and ability resources.
 */
public final class IneffaSummons {
    private static final int AVATAR = 10000116;
    private static final int RELAY = 41116005;
    private static final Set<Integer> FAMILY = Set.of(RELAY, 41116006, 41116007, 41116008);
    private static final Set<Integer> CLIENT_OBJECTS =
            Set.of(RELAY, 41116006, 41116007, 41116008, 41116009, 41116010, 41116011);

    private IneffaSummons() {}

    public static boolean clientCreates(GameEntity source, int gadgetId) {
        return source instanceof EntityAvatar avatar
                && avatar.getAvatar().getAvatarId() == AVATAR
                && CLIENT_OBJECTS.contains(gadgetId);
    }

    /** Called while the scene owns registration/removal; sender identity scopes co-op families. */
    public static void created(Scene scene, EntityClientGadget created) {
        if (!CLIENT_OBJECTS.contains(created.getGadgetId())) return;
        purgeShells(scene, created.getOwner(), false);
        if (created.getGadgetId() != RELAY) return;
        var stale =
                scene.getEntities().values().stream()
                        .filter(
                                e ->
                                        e instanceof EntityClientGadget client
                                                && client != created
                                                && client.getOwner() == created.getOwner()
                                                && FAMILY.contains(client.getGadgetId()))
                        .toList();
        for (var entity : stale) retire(scene, entity);
    }

    public static void destroying(Scene scene, EntityClientGadget removed) {
        if (!FAMILY.contains(removed.getGadgetId())) return;
        purgeShells(scene, removed.getOwner(), true);
        if (removed.getGadgetId() != RELAY) return;
        // Other roots are handled by creation. Recursing into roots here could also retire a
        // replacement, whereas these assists belong to the one currently departing family.
        var assists =
                scene.getEntities().values().stream()
                        .filter(
                                e ->
                                        e instanceof EntityClientGadget client
                                                && client.getOwner() == removed.getOwner()
                                                && client.getGadgetId() != RELAY
                                                && FAMILY.contains(client.getGadgetId()))
                        .toList();
        for (var entity : assists) retire(scene, entity);
    }

    private static void purgeShells(Scene scene, Player player, boolean familyOnly) {
        var kinds = familyOnly ? FAMILY : CLIENT_OBJECTS;
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

    private static void retire(Scene scene, GameEntity snapshot) {
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
