package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded server stacks; oldest-slot refresh follows gcsim's MIT-licensed stacks/refresh.go. */
final class AbilityModifierStacking {
    private static final String REFRESH = "MultipleRefreshNoRemove";
    private static final Set<String> OPTION_FIELDS =
            Set.of(
                    "abilitySpecialName",
                    "PFEHHABPHDB",
                    "maxModifierNumForMultipleType",
                    "KAANKELFPLA",
                    "enableMixedUnique");

    private record Limit(int fixed, String special) {
        int resolve(Ability ability) {
            double value =
                    special.isEmpty()
                            ? fixed
                            : ability.getAbilitySpecials().getOrDefault(special, 0f);
            return validLimit(value) ? (int) value : 0;
        }
    }

    private AbilityModifierStacking() {}

    static boolean supported(AbilityModifier data) {
        if (AbilityPartyAura.listener(data)) return true;
        if (REFRESH.equals(data.stacking)) return limit(data.stackingOption) != null;
        return (data.stacking == null || Set.of("Unique", "Multiple").contains(data.stacking))
                && (data.stackingOption == null || data.stackingOption.isEmpty());
    }

    private static boolean validLimit(double value) {
        return Double.isFinite(value) && value >= 1 && value <= 2000 && value == Math.rint(value);
    }

    private static Object field(Map<String, Object> options, String name, String alternate) {
        if (options.containsKey(name)
                && options.containsKey(alternate)
                && !Objects.equals(options.get(name), options.get(alternate))) return null;
        return options.getOrDefault(name, options.get(alternate));
    }

    private static Limit limit(Map<String, Object> options) {
        if (options == null
                || !OPTION_FIELDS.containsAll(options.keySet())
                || options.containsKey("enableMixedUnique")
                        && !Boolean.FALSE.equals(options.get("enableMixedUnique"))) return null;
        Object specialValue = field(options, "abilitySpecialName", "PFEHHABPHDB");
        boolean hasSpecial =
                options.containsKey("abilitySpecialName") || options.containsKey("PFEHHABPHDB");
        if (hasSpecial && !(specialValue instanceof String)) return null;
        String special = hasSpecial ? (String) specialValue : "";
        Object fixedValue = field(options, "maxModifierNumForMultipleType", "KAANKELFPLA");
        boolean hasFixed =
                options.containsKey("maxModifierNumForMultipleType")
                        || options.containsKey("KAANKELFPLA");
        if (hasFixed && !(fixedValue instanceof Number)) return null;
        double fixed = hasFixed ? ((Number) fixedValue).doubleValue() : 0;
        if (!special.isEmpty())
            return fixed == 0 || validLimit(fixed) ? new Limit((int) fixed, special) : null;
        return validLimit(fixed) ? new Limit((int) fixed, "") : null;
    }

    static void apply(
            Ability ability, AbilityModifier data, GameEntity target, BooleanSupplier current) {
        // The parent may live on another entity; never evaluate its registry guard under this lock.
        if (!current.getAsBoolean()) return;
        AbilityModifierController created = null;
        var retired = new ArrayList<AbilityModifierController>();
        synchronized (target.getInstancedModifiers()) {
            if (AbilityPartyAura.window(ability, data, target)) {
                retired.addAll(
                        target.getAbilityHpModifier().activeControllers().stream()
                                .filter(
                                        c ->
                                                c.getAbility() == ability
                                                        && c.getModifierData() == data)
                                .toList());
                for (var previous : retired) ability.retireAuraModifierLocked(target, previous);
            }
            if (AbilityPartyAura.listener(data)) {
                var existing =
                        target.getAbilityHpModifier().activeControllers().stream()
                                .filter(
                                        c ->
                                                c.getAbility() == ability
                                                        && c.getModifierData() == data
                                                        && c.hasServerCallbacks()
                                                        && c.attachmentCurrent())
                                .findFirst()
                                .orElse(null);
                if (existing != null) {
                    existing.refreshServerApplication();
                    target.getAbilityModifierDuration().restartLifetime(existing);
                    target.getAbilityPeriodicHealing().restartLifetime(existing);
                    created = existing;
                }
            }
            if (created == null && REFRESH.equals(data.stacking)) {
                var limit = limit(data.stackingOption);
                int cap = limit == null ? 0 : limit.resolve(ability);
                if (cap <= 0) return;
                var active =
                        target.getAbilityHpModifier().activeControllers().stream()
                                .filter(
                                        c ->
                                                c.getAbility() == ability
                                                        && c.getModifierData() == data)
                                .toList();
                if (active.size() >= cap) {
                    // Native instances retain client ownership until their timing is established.
                    if (active.stream().anyMatch(c -> !c.hasServerCallbacks())) return;
                    var oldest =
                            active.stream()
                                    .min(
                                            Comparator.comparingLong(
                                                    AbilityModifierController
                                                            ::serverApplicationOrder))
                                    .orElseThrow();
                    oldest.refreshServerApplication();
                    target.getAbilityModifierDuration().restartLifetime(oldest);
                    target.getAbilityPeriodicHealing().restartLifetime(oldest);
                    return;
                }
            }
            if (created == null) {
                created = new AbilityModifierController(ability, ability.getData(), data);
                if (!created.prepareServerCallbacks(target)) return;
                ability.registerModifier(target, created);
            }
        }
        for (var previous : retired) previous.runServerOnRemoved();
        AbilityPartyAura.linkCurrent(target, created);
        created.runServerOnAdded(current);
    }
}
