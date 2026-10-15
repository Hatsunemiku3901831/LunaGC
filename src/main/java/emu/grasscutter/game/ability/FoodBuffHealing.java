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
    private long consumed;
    private Ability ability;

    public FoodBuffHealing(
            Player player, Avatar target, AbilityData data, AbilityModifier modifier, long start) {
        this.player = player;
        this.target = target;
        this.data = data;
        this.modifier = modifier;
        this.start = start;
        float seconds = modifier.thinkInterval == null ? 0 : modifier.thinkInterval.get();
        this.interval =
                Float.isFinite(seconds) && seconds > 0
                        ? Math.max(1, Math.round(seconds * 1000d))
                        : 0;
    }

    public static boolean supported(AbilityData data, AbilityModifier modifier) {
        return modifier != null
                && hasHealing(data, modifier)
                && supported(data, modifier.onAdded)
                && supported(data, modifier.onThinkInterval);
    }

    public static boolean hasHealing(AbilityData data, AbilityModifier modifier) {
        return modifier != null
                && (hasHealing(data, modifier.onAdded, 0)
                        || hasHealing(data, modifier.onThinkInterval, 0));
    }

    private static boolean hasHealing(
            AbilityData data, AbilityModifierAction[] actions, int depth) {
        if (actions == null) return false;
        if (depth > 32) return true; // A cyclic or oversized food graph must fail validation.
        for (var action : actions) {
            if (action == null) continue;
            if (action.type == AbilityModifierAction.Type.HealHP
                    || hasHealing(data, action.successActions, depth + 1)
                    || hasHealing(data, action.failActions, depth + 1)
                    || hasHealing(data, action.actions, depth + 1)) return true;
            if (action.randomQueue != null)
                for (var group : action.randomQueue)
                    if (group != null && hasHealing(data, group.actionQueue, depth + 1))
                        return true;
            if (action.type == AbilityModifierAction.Type.ApplyModifier
                    && data != null
                    && data.modifiers != null) {
                var child = data.modifiers.get(action.modifierName);
                if (child != null
                        && (hasHealing(data, child.onAdded, depth + 1)
                                || hasHealing(data, child.onThinkInterval, depth + 1))) return true;
            }
        }
        return false;
    }

    private static boolean supported(AbilityData data, AbilityModifierAction[] actions) {
        var callbacks = AbilityActionSequence.callbacks(data, actions);
        return callbacks != null
                && callbacks.stream()
                        .allMatch(
                                a ->
                                        Set.of(
                                                                AbilityModifierAction.Type.HealHP,
                                                                        AbilityModifierAction.Type
                                                                                .SetOverrideMapValue,
                                                                AbilityModifierAction.Type.Randomed,
                                                                        AbilityModifierAction.Type
                                                                                .Predicated,
                                                                AbilityModifierAction.Type
                                                                                .RandomAction,
                                                                        AbilityModifierAction.Type
                                                                                .Repeated)
                                                        .contains(a.type)
                                                && (a.target == null
                                                        || "Self".equals(a.target)
                                                        || "Target".equals(a.target))
                                                && a.otherTargets == null
                                                && a.hasSupportedOffStageConfig());
    }

    public Set<AbilityModifierAction> actions() {
        Set<AbilityModifierAction> actions = Collections.newSetFromMap(new IdentityHashMap<>());
        actions.addAll(AbilityActionSequence.callbacks(data, modifier.onAdded));
        actions.addAll(AbilityActionSequence.callbacks(data, modifier.onThinkInterval));
        return actions;
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
        var entity = recipient();
        if (entity == null) return false;
        apply(modifier.onAdded, entity, false);
        return true;
    }

    public void tick(long now) {
        long elapsed = now - start;
        if (interval == 0 || elapsed < 0) return;
        long due = elapsed / interval;
        if (due <= consumed) return;
        // A delayed tick never grants a burst of missed healing pulses.
        consumed = due;
        var entity = recipient();
        if (entity != null) apply(modifier.onThinkInterval, entity, true);
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
    }
}
