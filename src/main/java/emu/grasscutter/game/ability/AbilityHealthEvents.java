package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;

/** Payload-independent health callbacks share the live ability and modifier registries. */
public final class AbilityHealthEvents {
    private record Binding(
            Ability ability,
            AbilityMixinData mixin,
            AbilityModifierController controller,
            AbilityExecutionContext context) {}

    private final GameEntity entity;
    private final Map<Ability, Set<AbilityMixinData>> claimed = new WeakHashMap<>();
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    public AbilityHealthEvents(GameEntity entity) {
        this.entity = entity;
    }

    public synchronized void clear() {
        claimed.clear();
    }

    private static AbilityModifierAction[] actions(AbilityMixinData mixin) {
        return mixin.actions != null && mixin.actions.length > 0
                ? mixin.actions
                : mixin.actionQueue;
    }

    private static boolean supported(Ability ability, AbilityMixinData mixin) {
        return mixin != null
                && mixin.type == AbilityMixinData.Type.DoActionByEventMixin
                && "HPDown".equals(mixin.onEvent)
                && Set.of("Both", "Server")
                        .contains(mixin.eventExecutionType == null ? "" : mixin.eventExecutionType)
                && AbilityActionSequence.supportedPredicates(mixin.predicates, 0)
                && AbilityActionSequence.callbacks(ability.getData(), actions(mixin)) != null;
    }

    private List<Binding> bindings() {
        var result = new ArrayList<Binding>();
        var abilities = new ArrayList<Ability>();
        synchronized (entity.getInstancedAbilities()) {
            abilities.addAll(entity.getInstancedAbilities());
        }
        for (var ability : abilities) {
            if (ability == null || ability.getData().abilityMixins == null) continue;
            for (var mixin : ability.getData().abilityMixins)
                if (supported(ability, mixin))
                    result.add(
                            new Binding(
                                    ability,
                                    mixin,
                                    null,
                                    new AbilityExecutionContext(ability, entity)));
        }
        List<AbilityModifierController> controllers;
        synchronized (entity.getInstancedModifiers()) {
            controllers = new ArrayList<>(entity.getAbilityHpModifier().activeControllers());
        }
        controllers.sort(Comparator.comparing(AbilityModifierController::getName));
        for (var controller : controllers) {
            var mixins = controller.getModifierData().modifierMixins;
            if (mixins == null) continue;
            for (var mixin : mixins)
                if (supported(controller.getAbility(), mixin))
                    result.add(
                            new Binding(
                                    controller.getAbility(),
                                    mixin,
                                    controller,
                                    new AbilityExecutionContext(controller.getAbility(), entity)));
        }
        return result;
    }

    private boolean current(Binding binding) {
        if (!binding.context.isCurrent()) return false;
        if (binding.controller == null)
            return entity.getInstancedAbilities().contains(binding.ability);
        synchronized (entity.getInstancedModifiers()) {
            return entity.getAbilityHpModifier().activeControllers().contains(binding.controller);
        }
    }

    private synchronized void claim(Binding binding) {
        claimed.computeIfAbsent(
                        binding.ability,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(binding.mixin);
    }

    /** Server-owned callbacks, including late confirmations of removed modifier instances. */
    public boolean owns(Ability ability, AbilityMixinData mixin) {
        synchronized (this) {
            var previous = claimed.get(ability);
            if (previous != null && previous.contains(mixin)) return true;
        }
        for (var binding : bindings())
            if (binding.ability == ability && binding.mixin == mixin && current(binding)) {
                claim(binding);
                return true;
            }
        return false;
    }

    public void changed(float previous, float next) {
        if (!entity.isAlive()
                || !Float.isFinite(previous)
                || !Float.isFinite(next)
                || next >= previous) return;
        int before = depth.get();
        if (before >= 32) return;
        depth.set(before + 1);
        try {
            AbilityActionBudget.run(
                    () -> {
                        for (var binding : bindings()) {
                            if (!current(binding)) continue;
                            claim(binding);
                            if (!PredicateEvaluator.all(
                                    binding.mixin.predicates,
                                    binding.ability,
                                    binding.ability.getOwner(),
                                    entity,
                                    null)) continue;
                            AbilityManager.runServerOwned(
                                    () ->
                                            AbilityActionSequence.run(
                                                    binding.ability,
                                                    actions(binding.mixin),
                                                    entity,
                                                    () -> current(binding)));
                        }
                        return true;
                    });
        } finally {
            if (before == 0) depth.remove();
            else depth.set(before);
        }
    }

    /** Arm a resource-defined, cooldown-gated Limbo guard before the first lethal hit. */
    public void prepareDamage() {
        if (AbilityCombatState.read(entity).limbo()) return;
        var seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<AbilityModifierController, Boolean>());
        for (var binding : bindings()) {
            var controller = binding.controller;
            if (controller == null || !seen.add(controller) || !current(binding)) continue;
            var pulse = controller.getModifierData().onThinkInterval;
            if (AbilityActionSequence.callbacks(binding.ability.getData(), pulse) == null
                    || pulse == null) continue;
            for (var action : pulse) {
                if (action == null
                        || action.type != AbilityModifierAction.Type.AttachModifier
                        || action.target != null
                                && !Set.of("Self", "Target", "Applier").contains(action.target))
                    continue;
                var data = binding.ability.getData().modifiers.get(action.modifierName);
                if (data == null || data.state != AbilityModifier.State.Limbo) continue;
                AbilityManager.runServerOwned(
                        () ->
                                AbilityActionSequence.run(
                                        binding.ability,
                                        new AbilityModifierAction[] {action},
                                        entity,
                                        () -> current(binding)));
            }
        }
    }
}
