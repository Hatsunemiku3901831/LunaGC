package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** The client supplies animator timing; one confirmed instance owns its server state callbacks. */
final class AnimationModifierCallbacks {
    private final AbilityModifierController controller;
    private final GameEntity target;
    private final AbilityExecutionContext context;
    private final Set<AbilityModifierController> parents;
    private final boolean root;
    private final Set<AbilityModifierAction> callbacks =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final AtomicBoolean started = new AtomicBoolean(), ended = new AtomicBoolean();

    private AnimationModifierCallbacks(
            AbilityModifierController controller,
            GameEntity target,
            boolean root,
            Set<AbilityModifierController> parents) {
        this.controller = controller;
        this.target = target;
        this.root = root;
        this.parents = parents;
        context = new AbilityExecutionContext(controller.getAbility(), target);
        collect(controller.getModifierData().onAdded, 0);
        collect(controller.getModifierData().onRemoved, 0);
    }

    static AnimationModifierCallbacks create(
            AbilityModifierController controller, GameEntity target) {
        var ability = controller.getAbility();
        boolean root = references(ability.getData().abilityMixins, controller.getName());
        var defaults = ability.getData().modifiers.get("Default");
        root |= defaults != null && references(defaults.modifierMixins, controller.getName());
        var parents =
                Collections.newSetFromMap(
                        new IdentityHashMap<AbilityModifierController, Boolean>());
        synchronized (target.getInstancedModifiers()) {
            for (var parent : target.getAbilityHpModifier().lifecycleControllers())
                if (parent != controller
                        && parent.getAbility() == ability
                        && references(
                                parent.getModifierData().modifierMixins, controller.getName()))
                    parents.add(parent);
        }
        return root || !parents.isEmpty()
                ? new AnimationModifierCallbacks(controller, target, root, parents)
                : null;
    }

    private static boolean window(String state, Float start, Float end) {
        float first = start == null ? 0 : start, last = end == null ? 1 : end;
        return state != null
                && !state.isBlank()
                && Float.isFinite(first)
                && Float.isFinite(last)
                && first >= 0
                && last <= 1
                && first < last;
    }

    private static boolean references(AbilityMixinData[] mixins, String name) {
        if (mixins == null) return false;
        for (var mixin : mixins) {
            if (mixin == null
                    || !mixin.enable
                    || mixin.modifierName == null
                    || !mixin.modifierName.isJsonPrimitive()
                    || !mixin.modifierName.getAsJsonPrimitive().isString()
                    || !name.equals(mixin.modifierName.getAsString())
                    || mixin.target != null && !Set.of("Self", "Target").contains(mixin.target))
                continue;
            if (mixin.type == AbilityMixinData.Type.AttachToStateIDMixin
                    && mixin.stateIDs != null
                    && !mixin.stateIDs.isEmpty()
                    && mixin.stateIDs.stream().allMatch(s -> s != null && !s.isBlank()))
                return true;
            if (mixin.type == AbilityMixinData.Type.AttachToNormalizedTimeMixin
                    && window(mixin.stateID, mixin.normalizeStart, mixin.normalizeEnd)) return true;
            if (mixin.type == AbilityMixinData.Type.AttachToMultiNormalizedTimeMixin
                    && mixin.normalizedStateIDList != null
                    && !mixin.normalizedStateIDList.isEmpty()
                    && mixin.normalizedStateIDList.stream()
                            .allMatch(
                                    s ->
                                            s != null
                                                    && window(
                                                            s.stateID,
                                                            s.normalizeStart,
                                                            s.normalizeEnd))) return true;
        }
        return false;
    }

    boolean current() {
        return context.isCurrent();
    }

    private boolean active() {
        if (!current() || ended.get()) return false;
        synchronized (target.getInstancedModifiers()) {
            var live = target.getAbilityHpModifier().lifecycleControllers();
            return live.contains(controller) && (root || parents.stream().anyMatch(live::contains));
        }
    }

    boolean owns(AbilityModifierAction action) {
        return current() && callbacks.contains(action);
    }

    static boolean isCallback(AbilityData data, AbilityModifierAction action) {
        if (data.modifiers == null) return false;
        for (var entry : data.modifiers.entrySet()) {
            boolean timing =
                    references(data.abilityMixins, entry.getKey())
                            || data.modifiers.values().stream()
                                    .anyMatch(
                                            parent ->
                                                    references(
                                                            parent.modifierMixins, entry.getKey()));
            if (!timing) continue;
            var owned =
                    Collections.newSetFromMap(
                            new IdentityHashMap<AbilityModifierAction, Boolean>());
            collect(entry.getValue().onAdded, 0, owned);
            collect(entry.getValue().onRemoved, 0, owned);
            if (owned.contains(action)) return true;
        }
        return false;
    }

    private void collect(AbilityModifierAction[] values, int depth) {
        collect(values, depth, callbacks);
    }

    private static void collect(
            AbilityModifierAction[] values, int depth, Set<AbilityModifierAction> callbacks) {
        if (values == null || depth >= 32) return;
        for (var value : values) {
            if (value == null
                    || !AbilityManager.isAllowedInServerOwnedChain(value.type)
                    || !callbacks.add(value)) continue;
            collect(value.successActions, depth + 1, callbacks);
            collect(value.failActions, depth + 1, callbacks);
            collect(value.actions, depth + 1, callbacks);
        }
    }

    void added() {
        if (!active() || !started.compareAndSet(false, true)) return;
        run(controller.getModifierData().onAdded, this::active);
    }

    private void run(AbilityModifierAction[] actions, BooleanSupplier guard) {
        BindingModifierCallbacks.withInitializationGuard(
                guard,
                () ->
                        AbilityCooldownInvocation.run(
                                controller,
                                () ->
                                        AbilityManager.runServerOwned(
                                                () ->
                                                        controller
                                                                .getAbility()
                                                                .getManager()
                                                                .executeCallbackActionsNow(
                                                                        controller.getAbility(),
                                                                        actions,
                                                                        ByteString.EMPTY,
                                                                        target,
                                                                        target))));
    }

    void removed() {
        if (!started.get() || !ended.compareAndSet(false, true) || !current()) return;
        run(controller.getModifierData().onRemoved, context::isCurrent);
    }

    void parentRemoved(AbilityModifierController parent) {
        if (root || !parents.contains(parent) || ended.get()) return;
        synchronized (target.getInstancedModifiers()) {
            var live = target.getAbilityHpModifier().lifecycleControllers();
            if (parents.stream().anyMatch(p -> p != parent && live.contains(p))) return;
        }
        controller.getAbility().removeModifierInstance(target, controller, true);
    }

    static void parentRemoved(GameEntity target, AbilityModifierController parent) {
        List<AbilityModifierController> live;
        synchronized (target.getInstancedModifiers()) {
            live = List.copyOf(target.getAbilityHpModifier().lifecycleControllers());
        }
        for (var child : live) child.animationParentRemoved(parent);
    }
}
