package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Scene;

import java.util.*;

/**
 * Client-created Ineffa skill objects, verified against the current gadget and ability resources.
 */
public final class IneffaSummons {
    private static final int AVATAR = 10000116;
    private static final int RELAY = 41116005;
    private static final Set<Integer> FAMILY = Set.of(RELAY, 41116006, 41116007, 41116008);

    private IneffaSummons() {}

    public static boolean clientCreates(GameEntity source, int gadgetId) {
        return source instanceof EntityAvatar avatar
                && avatar.getAvatar().getAvatarId() == AVATAR
                && ClientSkillObjects.clientCreates(source, gadgetId);
    }

    /** Called while the scene owns registration/removal; sender identity scopes co-op families. */
    public static void created(Scene scene, EntityClientGadget created) {
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
        for (var entity : stale) ClientSkillObjects.retire(scene, entity);
    }

    public static void destroying(Scene scene, EntityClientGadget removed) {
        if (!FAMILY.contains(removed.getGadgetId())) return;
        ClientSkillObjects.purge(scene, removed.getOwner(), FAMILY);
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
        for (var entity : assists) ClientSkillObjects.retire(scene, entity);
    }
}
