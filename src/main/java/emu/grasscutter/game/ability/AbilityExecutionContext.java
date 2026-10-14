package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;

import java.util.function.BooleanSupplier;

/** A queued scene action belongs to the entity lifetime and caster scene that submitted it. */
final class AbilityExecutionContext {
    private final EntityLifetime owner;
    private final EntityLifetime target;
    private final Ability installedRoot;
    private final Player caster;
    private final Scene casterScene;
    private final BooleanSupplier scenePlayScope;
    private final Scene scenePlayScene;

    AbilityExecutionContext(Ability ability, GameEntity target) {
        this(ability, target, false);
    }

    AbilityExecutionContext(Ability ability, GameEntity target, boolean scenePlayCall) {
        this(ability, target, scenePlayCall, false);
    }

    AbilityExecutionContext(Ability ability, GameEntity target, boolean scenePlayCall,
                            boolean requireInstalledRoot) {
        this.owner = new EntityLifetime(ability.getOwner());
        this.installedRoot = requireInstalledRoot
                && ability.getOwner().getInstancedAbilities().contains(ability)
                ? ability : null;
        this.target = target == null ? null : new EntityLifetime(target);
        this.caster = ability.getPlayerOwner();
        this.casterScene = owner.scene;
        var data = ability.getData();
        boolean scenePlayAbility =
                scenePlayCall
                        || data != null
                                && data.localIdToAction != null
                                && data.localIdToAction.values().stream()
                                        .anyMatch(
                                                action ->
                                                        action != null
                                                                && action.type
                                                                        == AbilityModifierAction
                                                                                .Type.ServerLuaCall
                                                                && action.luaCallType
                                                                        == AbilityModifierAction
                                                                                .LuaCallType
                                                                                .CurScenePlay);
        var scene = caster == null ? ability.getOwner().getScene() : caster.getScene();
        this.scenePlayScene =
                scenePlayAbility
                                || ability.getOwner() instanceof EntityPlayTeam
                                || target instanceof EntityPlayTeam
                        ? scene
                        : null;
        this.scenePlayScope =
                scenePlayAbility && scenePlayScene != null
                        ? scenePlayScene.getScenePlayBattleManager().captureAbilityScope(caster)
                        : () -> true;
    }

    void runIfCurrent(Runnable action) {
        if (scenePlayScene != null) {
            synchronized (scenePlayScene) {
                if (isCurrent()) action.run();
            }
        } else if (isCurrent()) action.run();
    }

    boolean isCurrent() {
        if (!owner.isCurrent()
                || (target != null && !target.isCurrent())
                || (installedRoot != null
                        && !installedRoot.getOwner().getInstancedAbilities().contains(installedRoot))
                || !scenePlayScope.getAsBoolean()) return false;
        // World and team entities have a dynamic scene, including during initial world setup.
        if (casterScene == null) return true;
        return (caster == null || caster.getScene() == casterScene)
                && (target == null || target.scene == null || target.scene == casterScene);
    }

    static boolean canTrigger(GameEntity entity, Player caster) {
        var lifetime = new EntityLifetime(entity);
        return lifetime.isCurrent()
                && (lifetime.scene == null
                        || caster == null
                        || caster.getScene() == lifetime.scene);
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
                    entity instanceof EntityWorld || entity instanceof EntityTeam
                            ? null
                            : entity.getScene();
            this.registered = scene != null && lookup() == entity;
        }

        private GameEntity lookup() {
            var found = scene.getEntities().get(id);
            if (found == null) found = scene.getWeaponEntities().get(id);
            return found == null ? scene.getScenePlayBattleManager().getPlayTeamEntity(id) : found;
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
