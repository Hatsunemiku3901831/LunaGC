package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.EntityAvatar;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A modifier application and its client confirmations share cooldown and ammo-supply decisions.
 */
public final class AbilityCooldownInvocation {
    private static final ThreadLocal<Scope> current = new ThreadLocal<>();

    public record Scope(AbilityModifierController controller, boolean cooldownOnly) {}

    static final class Ledger {
        private final Map<Object, Set<AbilityModifierAction>> recorded = new IdentityHashMap<>();

        synchronized boolean apply(
                Object recipient, AbilityModifierAction action, BooleanSupplier body) {
            var actions =
                    recorded.computeIfAbsent(
                            recipient,
                            ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
            if (actions.contains(action)) return true;
            if (!body.getAsBoolean()) return false;
            actions.add(action);
            return true;
        }

        synchronized boolean applyPrepared(
                Object recipient, AbilityModifierAction action,
                Supplier<Optional<BooleanSupplier>> prepare) {
            var actions=recorded.computeIfAbsent(recipient,
                    ignored->Collections.newSetFromMap(new IdentityHashMap<>()));
            if(actions.contains(action)) return true;
            var body=prepare.get();
            if(body.isEmpty()) return false;
            // Persistence can throw after a mutation. Once started, this application cannot replay.
            actions.add(action);
            return body.get().getAsBoolean();
        }
    }

    private AbilityCooldownInvocation() {}

    public static boolean isCooldownAction(AbilityModifierAction action) {
        if (action == null || action.type == null) return false;
        return switch (action.type) {
            case AvatarSkillStart,
                    ModifyAvatarSkillCD,
                    AvatarShareCDSkillStart,
                    ActivitySkillShareCDStart,
                    ClearShareCD -> true;
            default -> false;
        };
    }

    public static boolean isTrackedAction(AbilityModifierAction action) {
        return isCooldownAction(action)
                || action != null && (action.type == AbilityModifierAction.Type.TPSSupplyAmmo
                        || action.type == AbilityModifierAction.Type.TPSAmmoBoxDispatch
                        || action.type == AbilityModifierAction.Type.DoActionByGlobalValue);
    }

    public static Scope capture() {
        return current.get();
    }

    private static boolean applicationCurrent() {
        var scope = current.get();
        return scope == null || scope.controller() == null || scope.controller().isApplicationCurrent();
    }

    public static void run(Scope scope, Runnable body) {
        var previous = current.get();
        if (scope == null) current.remove();
        else current.set(scope);
        try {
            body.run();
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    public static void run(AbilityModifierController controller, Runnable body) {
        run(new Scope(controller, false), body);
    }

    public static boolean identifiable(Ability ability, AbilityModifierAction action) {
        var scope = current.get();
        return scope != null
                && scope.controller() != null
                && scope.controller().getAbility() == ability
                && scope.controller().ownsCooldownCallback(action);
    }

    public static boolean record(
            Ability ability,
            AbilityModifierAction action,
            EntityAvatar target,
            BooleanSupplier body) {
        if (!applicationCurrent()) return false;
        if (!identifiable(ability, action)) return body.getAsBoolean();
        return current.get()
                .controller()
                .getCooldownLedger()
                .apply(target.getAvatar(), action, body);
    }

    /**
     * Shared groups belong to the player, so changing the recipient cannot repeat the operation.
     */
    public static boolean recordShared(
            Ability ability,
            AbilityModifierAction action,
            EntityAvatar target,
            BooleanSupplier body) {
        if (!applicationCurrent()) return false;
        if (!identifiable(ability, action)) return body.getAsBoolean();
        return current.get()
                .controller()
                .getCooldownLedger()
                .apply(target.getPlayer(), action, body);
    }

    /** A scalar dispatch decision belongs to this application, even if the current avatar changes. */
    public static boolean recordApplication(Ability ability, AbilityModifierAction action, BooleanSupplier body) {
        if (!applicationCurrent()) return false;
        if (!identifiable(ability,action)) return body.getAsBoolean();
        return current.get().controller().getCooldownLedger().apply(ability.getOwner(),action,body);
    }

    /** Reject invalid plans without consuming them; seal valid plans before their first write. */
    public static boolean recordPreparedApplication(Ability ability, AbilityModifierAction action,
                                                     Supplier<Optional<BooleanSupplier>> prepare) {
        if (!applicationCurrent()) return false;
        if(!identifiable(ability,action)) {
            var body=prepare.get();
            return body.isPresent() && body.get().getAsBoolean();
        }
        return current.get().controller().getCooldownLedger().applyPrepared(ability.getOwner(),action,prepare);
    }

    /**
     * Removal bookkeeping may traverse conditional branches, but cannot repeat unrelated effects.
     */
    public static boolean allows(AbilityModifierAction action) {
        if (!applicationCurrent()) return false;
        var scope = current.get();
        if (scope == null || !scope.cooldownOnly()) return true;
        return isCooldownAction(action)
                || action != null
                        && action.type != null
                        && (action.type == AbilityModifierAction.Type.Predicated
                                || action.type == AbilityModifierAction.Type.Randomed
                                || action.type == AbilityModifierAction.Type.DoActionByGlobalValue);
    }

    static Set<AbilityModifierAction> callbacks(AbilityModifierAction[]... groups) {
        Set<AbilityModifierAction> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var actions : groups)
            collect(actions, result, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return result.size() > 256 ? Set.of() : result;
    }

    static boolean isModifierCallback(AbilityData data, AbilityModifierAction action) {
        if (data.modifiers == null) return false;
        return data.modifiers.values().stream()
                .anyMatch(
                        modifier ->
                                callbacks(modifier.onAdded, modifier.onRemoved).contains(action));
    }

    private static void collect(
            AbilityModifierAction[] actions,
            Set<AbilityModifierAction> result,
            Set<AbilityModifierAction> path,
            int depth) {
        if (actions == null || depth > 32 || result.size() > 256) return;
        for (var action : actions) {
            if (action == null || !path.add(action)) continue;
            if (isTrackedAction(action)) result.add(action);
            // Repeated and random-queue branches may legitimately execute one object more than
            // once. Their occurrences need their own identity; do not collapse them here.
            if (action.type == AbilityModifierAction.Type.Predicated
                    || action.type == AbilityModifierAction.Type.Randomed) {
                collect(action.successActions, result, path, depth + 1);
                collect(action.failActions, result, path, depth + 1);
            }
            if (action.type == AbilityModifierAction.Type.DoActionByGlobalValue) {
                if (action.actionSteps != null)
                    for (var branch : action.actionSteps) collect(branch,result,path,depth+1);
                collect(action.defaultActions,result,path,depth+1);
            }
            path.remove(action);
        }
    }
}
