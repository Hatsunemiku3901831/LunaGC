package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Legacy global-value initialization and cleanup stay with their created controller. */
final class BindingModifierCallbacks {
    private static final ThreadLocal<BooleanSupplier> initializationGuard = new ThreadLocal<>();

    static BooleanSupplier captureGuard() {
        return initializationGuard.get();
    }

    static boolean current() {
        var guard = captureGuard();
        return guard == null || guard.getAsBoolean();
    }

    static void withInitializationGuard(BooleanSupplier guard, Runnable body) {
        var previous = captureGuard();
        initializationGuard.set(() -> (previous == null || previous.getAsBoolean()) && guard.getAsBoolean());
        try { body.run(); }
        finally { if (previous == null) initializationGuard.remove(); else initializationGuard.set(previous); }
    }

    static void withoutInitializationGuard(Runnable body) {
        var previous = captureGuard();
        initializationGuard.remove();
        try {
            body.run();
        } finally {
            if (previous != null) initializationGuard.set(previous);
        }
    }

    private final AbilityModifierController controller;
    private final GameEntity target;
    private final AbilityExecutionContext context;
    private final AtomicBoolean started = new AtomicBoolean(), ended = new AtomicBoolean();

    BindingModifierCallbacks(AbilityModifierController controller, GameEntity target) {
        this.controller = controller;
        this.target = target;
        context = new AbilityExecutionContext(controller.getAbility(), target);
    }

    boolean available() {
        return context.isCurrent();
    }

    private boolean active(BooleanSupplier parent) {
        if (!parent.getAsBoolean() || !context.isCurrent() || ended.get()) return false;
        synchronized (target.getInstancedModifiers()) {
            return target.getAbilityHpModifier().lifecycleControllers().contains(controller);
        }
    }

    void added(BooleanSupplier parent) {
        if (!active(parent) || !started.compareAndSet(false, true)) return;
        var previous = captureGuard();
        initializationGuard.set(
                () -> (previous == null || previous.getAsBoolean()) && active(parent));
        try {
            AbilityCooldownInvocation.run(
                    controller,
                    () ->
                            AbilityManager.runServerOwned(
                                    () -> AbilityActionBudget.run(() -> initialize(parent))));
        } finally {
            if (previous == null) initializationGuard.remove();
            else initializationGuard.set(previous);
        }
    }

    private boolean initialize(BooleanSupplier parent) {
        var actions = controller.getModifierData().onAdded;
        if (actions == null) return true;
        for (var action : actions) {
            if (!active(parent)) break;
            if (action == null || !AbilityManager.isAllowedInServerOwnedChain(action.type))
                continue;
            controller
                    .getAbility()
                    .getManager()
                    .executeCallbackActionsNow(
                            controller.getAbility(),
                            new AbilityModifierAction[] {action},
                            ByteString.EMPTY,
                            target,
                            target);
        }
        return true;
    }

    void removed() {
        if (!ended.compareAndSet(false, true) || !started.get() || !context.isCurrent()) return;
        AbilityCooldownInvocation.run(
                controller,
                () ->
                        AbilityModifierDuration.runRemovalActions(
                                controller, target, context::isCurrent));
    }
}
