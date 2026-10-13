package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Paired, validated callbacks from the resource's AttachActionToModifierMixin. */
final class ModifierAttachmentCallbacks {
    private final AbilityModifierController controller;
    private final GameEntity target;
    private final AbilityExecutionContext context;

    private record Group(
            AbilityModifier.AbilityModifierAction[] added,
            AbilityModifier.AbilityModifierAction[] removed) {}

    private final List<Group> groups = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(), ended = new AtomicBoolean();

    ModifierAttachmentCallbacks(AbilityModifierController controller, GameEntity target) {
        this.controller = controller;
        this.target = target;
        context = new AbilityExecutionContext(controller.getAbility(), target);
        var mixins = controller.getModifierData().modifierMixins;
        if (mixins == null) return;
        for (var mixin : mixins) {
            if (mixin == null
                    || mixin.type != AbilityMixinData.Type.AttachActionToModifierMixin
                    || mixin.addedLifecycleConfig == null && mixin.removedLifecycleConfig == null)
                continue;
            try {
                var added = actions(mixin.addedLifecycleConfig);
                var removed = actions(mixin.removedLifecycleConfig);
                if (AbilityActionSequence.callbacks(controller.getAbilityData(), added) == null
                        || AbilityActionSequence.callbacks(controller.getAbilityData(), removed)
                                == null) continue;
                groups.add(new Group(added, removed));
            } catch (RuntimeException unsupported) {
                // Keep visual or unknown callback shapes as raw resource data.
            }
        }
    }

    private static AbilityModifier.AbilityModifierAction[] actions(
            com.google.gson.JsonElement config) {
        return config == null || config.isJsonNull()
                ? null
                : emu.grasscutter.utils.JsonUtils.decode(
                        config, AbilityModifier.AbilityModifierAction[].class);
    }

    boolean available() {
        return !groups.isEmpty() && context.isCurrent();
    }

    boolean forTarget(GameEntity candidate) {
        return candidate == target;
    }

    private boolean active(BooleanSupplier parent) {
        if (!parent.getAsBoolean() || !context.isCurrent() || ended.get()) return false;
        synchronized (target.getInstancedModifiers()) {
            return target.getAbilityHpModifier().lifecycleControllers().contains(controller);
        }
    }

    void added(BooleanSupplier parent) {
        if (!available() || !active(parent) || !started.compareAndSet(false, true)) return;
        AbilityCooldownInvocation.run(
                controller,
                () -> {
                    for (var mixin : groups) {
                        if (!active(parent)) return;
                        AbilityActionSequence.run(
                                controller.getAbility(), mixin.added, target, () -> active(parent));
                    }
                });
    }

    void removed() {
        if (!started.get() || !ended.compareAndSet(false, true) || !context.isCurrent()) return;
        AbilityCooldownInvocation.run(
                controller,
                () -> {
                    for (var mixin : groups)
                        AbilityActionSequence.run(
                                controller.getAbility(), mixin.removed, target, context::isCurrent);
                });
    }
}
