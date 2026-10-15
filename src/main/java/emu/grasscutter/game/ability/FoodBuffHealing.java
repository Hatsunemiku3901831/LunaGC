package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;

import java.util.*;

/** Food-owned healing shares ordinary health/debt callbacks, with recipe amounts and one clock. */
public final class FoodBuffHealing {
    private final Player player;
    private final Avatar target;
    private final AbilityData data;
    private final AbilityModifier modifier;
    private final long start, interval;
    private final FoodBuffGraph graph;
    private final java.util.concurrent.atomic.AtomicBoolean ended =
            new java.util.concurrent.atomic.AtomicBoolean();
    private long consumed;
    private Ability ability;

    public FoodBuffHealing(
            Player player, Avatar target, AbilityData data, AbilityModifier modifier, long start) {
        this.player = player;
        this.target = target;
        this.data = data;
        this.modifier = modifier;
        this.start = start;
        this.graph = FoodBuffGraph.prepare(data, modifier);
        float seconds = modifier.thinkInterval == null ? 0 : modifier.thinkInterval.get();
        this.interval =
                Float.isFinite(seconds) && seconds > 0
                        ? Math.max(1, Math.round(seconds * 1000d))
                        : 0;
    }

    public static boolean supported(AbilityData data, AbilityModifier modifier) {
        return FoodBuffGraph.prepare(data, modifier) != null;
    }

    public static boolean hasHealing(AbilityData data, AbilityModifier modifier) {
        return hasHealing(data, modifier, 0, new int[1]);
    }

    private static boolean hasHealing(
            AbilityData data, AbilityModifier modifier, int depth, int[] steps) {
        if (modifier == null) return false;
        if (depth > 32 || ++steps[0] > 512) return true;
        if (hasHealing(data, modifier.onAdded, depth + 1, steps)
                || hasHealing(data, modifier.onThinkInterval, depth + 1, steps)
                || hasHealing(data, modifier.onRemoved, depth + 1, steps)) return true;
        if (modifier.modifierMixins != null)
            for (var mixin : modifier.modifierMixins) {
                if (mixin == null || mixin.type != AbilityMixinData.Type.AttachToStateIDMixin)
                    continue;
                if (data == null || data.modifiers == null || mixin.modifierName == null)
                    return true;
                try {
                    for (var name : mixin.getModifierNames()) {
                        var child = data.modifiers.get(name);
                        if (child == null || hasHealing(data, child, depth + 1, steps)) return true;
                    }
                } catch (RuntimeException malformed) {
                    return true;
                }
            }
        return false;
    }

    private static boolean hasHealing(
            AbilityData data, AbilityModifierAction[] actions, int depth, int[] steps) {
        if (actions == null) return false;
        if (depth > 32 || ++steps[0] > 512)
            return true; // Force cyclic or oversized graphs through validation.
        for (var action : actions) {
            if (++steps[0] > 512) return true;
            if (action == null) continue;
            if (action.type == AbilityModifierAction.Type.HealHP
                    || hasHealing(data, action.successActions, depth + 1, steps)
                    || hasHealing(data, action.failActions, depth + 1, steps)
                    || hasHealing(data, action.actions, depth + 1, steps)) return true;
            if (action.randomQueue != null)
                for (var group : action.randomQueue)
                    if (group != null && hasHealing(data, group.actionQueue, depth + 1, steps))
                        return true;
            if ((action.type == AbilityModifierAction.Type.ApplyModifier
                            || action.type == AbilityModifierAction.Type.AttachModifier)
                    && data != null
                    && data.modifiers != null) {
                var child = data.modifiers.get(action.modifierName);
                if (child == null || hasHealing(data, child, depth + 1, steps)) return true;
            }
        }
        return false;
    }

    public Set<AbilityModifierAction> actions() {
        return graph == null ? Set.of() : graph.claims();
    }

    public EntityAvatar recipient() {
        if (target == null || target.getPlayer() != player) return null;
        var entity = target.getAsEntity();
        return entity != null
                        && entity.isAlive()
                        && entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0
                        && entity.getScene() == player.getScene()
                        && AbilityExecutionContext.canTrigger(entity, player)
                ? entity
                : null;
    }

    public boolean initial() {
        if (graph == null) return false;
        var entity = recipient();
        if (entity == null) return false;
        apply(modifier.onAdded, entity, false);
        return true;
    }

    public void tick(long now) {
        if (ended.get()) return;
        long elapsed = now - start;
        if (interval == 0 || elapsed < 0) return;
        long due = elapsed / interval;
        if (due <= consumed) return;
        // A delayed tick never grants a burst of missed healing pulses.
        consumed = due;
        var entity = recipient();
        if (entity != null) apply(modifier.onThinkInterval, entity, true);
    }

    public void removed() {
        if (!ended.compareAndSet(false, true)) return;
        var entity = recipient();
        if (entity != null) apply(modifier.onRemoved, entity, false);
    }

    private void apply(AbilityModifierAction[] actions, EntityAvatar entity, boolean periodic) {
        if (actions == null) return;
        if (ability == null || ability.getOwner() != entity) {
            var previous = ability;
            ability = new Ability(data, entity, player);
            ability.markServerFoodHealing();
            if (previous != null)
                ability.getAbilitySpecials().putAll(previous.getAbilitySpecials());
        }
        AbilityManager.runServerOwned(
                () -> {
                    if (periodic)
                        AbilityActionSequence.runPeriodic(
                                ability, actions, entity, () -> recipient() == entity);
                    else
                        AbilityActionSequence.run(
                                ability, actions, entity, () -> recipient() == entity);
                });
        // Instant children have completed their entire callback; keeping them registered would leak
        // per meal.
        for (var bearer : player.getTeamManager().getActiveTeamSnapshot())
            for (var controller : bearer.getAbilityHpModifier().activeControllers())
                if (controller.getAbility() == ability
                        && FoodBuffGraph.instantHealing(data, controller.getModifierData()))
                    ability.unregisterModifier(bearer, controller);
    }
}
