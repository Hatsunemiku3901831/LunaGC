package emu.grasscutter.game.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/** Native global-value dispatch reads the source controller, with half-open adjacent ranges. */
public final class AbilityGlobalValueDispatch {
    private static final AbilityModifierAction[] EMPTY = new AbilityModifierAction[0];
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CurLocalAvatar",
                    "OriginOwner",
                    "CasterOriginOwner");

    private AbilityGlobalValueDispatch() {}

    /** Native leaf confirmations cannot bypass a modifier's server-selected dispatch branch. */
    public static void collectModifierDescendants(
            AbilityModifierAction[] actions, Set<AbilityModifierAction> result) {
        collect(actions, result, Collections.newSetFromMap(new IdentityHashMap<>()), false, 0);
    }

    private static void collect(
            AbilityModifierAction[] actions,
            Set<AbilityModifierAction> result,
            Set<AbilityModifierAction> path,
            boolean nested,
            int depth) {
        if (actions == null || depth > 32) return;
        for (var action : actions) {
            if (action == null || !path.add(action)) continue;
            if (nested) result.add(action);
            boolean child =
                    nested || action.type == AbilityModifierAction.Type.DoActionByGlobalValue;
            collect(action.actions, result, path, child, depth + 1);
            collect(action.successActions, result, path, child, depth + 1);
            collect(action.failActions, result, path, child, depth + 1);
            if (action.randomQueue != null)
                for (var group : action.randomQueue)
                    if (group != null) collect(group.actionQueue, result, path, child, depth + 1);
            if (action.actionSteps != null)
                for (var branch : action.actionSteps)
                    collect(branch, result, path, child, depth + 1);
            collect(action.defaultActions, result, path, child, depth + 1);
            path.remove(action);
        }
    }

    public static boolean supported(AbilityModifierAction action) {
        if (action == null
                || action.type != AbilityModifierAction.Type.DoActionByGlobalValue
                || !action.hasSupportedOffStageConfig()
                || action.otherTargets != null
                || action.target != null && !TARGETS.contains(action.target)
                || action.valueSteps != null && action.valueSteps.length > 257
                || action.actionSteps != null && action.actionSteps.length > 256) return false;
        if (action.valueSteps == null || action.valueSteps.length < 2) return true;
        if (action.actionSteps == null
                || action.globalValueProvider == null
                || !action.globalValueProvider.isJsonObject()) return false;
        var provider = action.globalValueProvider.getAsJsonObject();
        String type = string(provider, "$type");
        if ("AFKDGJBEGHN".equals(type))
            return Set.of("$type", "name", "JJGDPDEHLKJ").containsAll(provider.keySet())
                    && staticKey(provider) != null;
        return "OOMODMGBDEP".equals(type)
                && Set.of("$type", "HNEGOANIAHN", "JLECIDOAMFG").containsAll(provider.keySet())
                && string(provider, "HNEGOANIAHN") != null
                && string(provider, "JLECIDOAMFG") != null;
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                        && !value.getAsString().isEmpty()
                ? value.getAsString()
                : null;
    }

    private static String staticKey(JsonObject provider) {
        String named = string(provider, "name"), raw = string(provider, "JJGDPDEHLKJ");
        if (named != null && raw != null && !named.equals(raw)) return null;
        return named != null ? named : raw;
    }

    private static String key(Ability ability, JsonObject provider) {
        if ("AFKDGJBEGHN".equals(string(provider, "$type"))) return staticKey(provider);
        var count =
                ability.getOwner().getGlobalAbilityValues().get(string(provider, "HNEGOANIAHN"));
        float value = count == null ? 0f : count;
        if (!Float.isFinite(value)
                || (double) value < -2147483648d
                || (double) value >= 2147483648d) return null;
        return string(provider, "JLECIDOAMFG") + (int) value;
    }

    public static Optional<AbilityModifierAction[]> choose(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        if (!supported(action)
                || ability == null
                || ability.getOwner() == null
                || target == null
                || !AbilityGlobalValues.isCurrent(ability, target)) return Optional.empty();
        var fallback = action.defaultActions == null ? EMPTY : action.defaultActions;
        if (action.valueSteps == null || action.valueSteps.length < 2) return Optional.of(fallback);
        String key = key(ability, action.globalValueProvider.getAsJsonObject());
        if (key == null) return Optional.empty();
        var value = ability.getOwner().getGlobalAbilityValues().get(key);
        if (value == null) return Optional.of(fallback);
        if (!Float.isFinite(value)) return Optional.empty();
        var properties = AbilityActionHandler.propertiesFor(ability);
        float[] bounds = new float[action.valueSteps.length];
        for (int i = 0; i < bounds.length; i++) {
            if (action.valueSteps[i] == null) return Optional.empty();
            var bound = action.valueSteps[i].tryGet(properties);
            if (bound.isEmpty()) return Optional.empty();
            bounds[i] = (float) bound.getAsDouble();
        }
        for (int i = 0; i + 1 < bounds.length; i++)
            if (value >= bounds[i] && value < bounds[i + 1]) {
                if (i >= action.actionSteps.length) return Optional.of(fallback);
                var chosen = action.actionSteps[i];
                return Optional.of(chosen == null ? EMPTY : chosen);
            }
        return Optional.of(fallback);
    }
}
