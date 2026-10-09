package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;

/** A queued scene action belongs to the entity lifetime and caster scene that submitted it. */
final class AbilityExecutionContext {
    private final EntityLifetime owner;
    private final EntityLifetime target;
    private final Player caster;
    private final Scene casterScene;

    AbilityExecutionContext(Ability ability, GameEntity target) {
        this.owner = new EntityLifetime(ability.getOwner());
        this.target = target == null ? null : new EntityLifetime(target);
        this.caster = ability.getPlayerOwner();
        this.casterScene = owner.scene;
    }

    boolean isCurrent() {
        if (!owner.isCurrent() || (target != null && !target.isCurrent())) return false;
        // World and team entities have a dynamic scene, including during initial world setup.
        if (casterScene == null) return true;
        return (caster == null || caster.getScene() == casterScene)
                && (target == null || target.scene == null || target.scene == casterScene);
    }

    static boolean canTrigger(GameEntity entity, Player caster) {
        var lifetime = new EntityLifetime(entity);
        return lifetime.isCurrent()
                && (lifetime.scene == null || caster == null || caster.getScene() == lifetime.scene);
    }

    private static final class EntityLifetime {
        private final GameEntity entity;
        private final long version;
        private final Scene scene;
        private final int id;
        private final boolean registered;

        EntityLifetime(GameEntity entity) {
            this.entity = entity;
            this.version = entity.getAbilityLifecycleVersion();
            this.id = entity.getId();
            this.scene =
                    entity instanceof EntityWorld || entity instanceof EntityTeam ? null : entity.getScene();
            this.registered = scene != null && lookup() == entity;
        }

        private GameEntity lookup() {
            var found = scene.getEntities().get(id);
            return found == null ? scene.getWeaponEntities().get(id) : found;
        }

        boolean isCurrent() {
            if (!entity.isAbilityLifecycleCurrent(version) || entity.getId() != id) return false;
            if (scene == null) return true;
            if (entity.getScene() != scene) return false;
            var current = lookup();
            if (current == null && entity instanceof EntityAvatar avatar && avatar.isInSceneParty())
                return true;
            // Initialization can precede the entity's first registration.
            return registered ? current == entity : current == null || current == entity;
        }
    }
}
