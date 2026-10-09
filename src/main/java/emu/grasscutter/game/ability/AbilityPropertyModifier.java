package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.ActorProperty;
import emu.grasscutter.game.props.FightProperty;
import java.util.*;

/** Removable non-HP fight properties, refreshed with the entity's shared modifier registry. */
final class AbilityPropertyModifier {
    private final GameEntity entity;
    private final Map<FightProperty, Layer> applied = new EnumMap<>(FightProperty.class);
    private static final Map<FightProperty, FightProperty> nonExtra = nonExtraProperties();
    private record Layer(float base, float value) {}

    AbilityPropertyModifier(GameEntity entity) { this.entity = entity; }

    static boolean hasProperties(AbilityModifier data) {
        return data != null && data.properties != null && data.properties.getValues().entrySet().stream()
                .anyMatch(entry -> ActorProperty.getFightProperty(entry.getKey()) != null
                        && entry.getValue() != null
                        && (entry.getValue().isDynamic() || entry.getValue().getConstant() != 0f));
    }

    private static Map<FightProperty, FightProperty> nonExtraProperties() {
        var result = new EnumMap<FightProperty, FightProperty>(FightProperty.class);
        for (var property : FightProperty.values()) {
            String prefix = "FIGHT_PROP_NONEXTRA_";
            if (!property.name().startsWith(prefix)) continue;
            String name = property.name().substring(prefix.length());
            result.put(property, FightProperty.valueOf("FIGHT_PROP_"
                    + (name.equals("ATTACK") || name.equals("DEFENSE") ? "CUR_" : "") + name));
        }
        return result;
    }

    void reset() { applied.clear(); }

    boolean hasContribution() {
        return applied.keySet().stream().anyMatch(property -> !nonExtra.containsKey(property));
    }

    Set<FightProperty> contributedProperties() {
        return new LinkedHashSet<>(applied.keySet());
    }

    private Map<FightProperty, Double> amounts(List<AbilityModifierController> controllers,
            Map<FightProperty, Float> context, boolean extra) {
        var result = new EnumMap<FightProperty, Double>(FightProperty.class);
        for (var controller : controllers) {
            var data = controller.getModifierData();
            if (data == null || data.properties == null) continue;
            var ability = controller.getAbility();
            var values = AbilityActionHandler.propertiesFor(ability, entity);
            context.forEach((property, value) -> values.put(property.name(), value));
            // Ability constants retain precedence over globals and named fight properties.
            values.putAll(ability.getAbilitySpecials());
            for (var entry : data.properties.getValues().entrySet()) {
                var property = ActorProperty.getFightProperty(entry.getKey());
                if (property == null || property == FightProperty.FIGHT_PROP_HP_PERCENT
                        || entry.getValue() == null || entry.getKey().contains("ExtraDelta") != extra) continue;
                result.merge(property, (double) entry.getValue().get(values, 0f), Double::sum);
            }
        }
        return result;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && Math.abs(value) <= Float.MAX_VALUE;
    }

    private Map<FightProperty, Float> plan(Map<FightProperty, Float> base,
            Map<FightProperty, Double> amounts, Map<FightProperty, Float> fallback) {
        var result = new EnumMap<FightProperty, Float>(base);
        amounts.forEach((property, delta) -> {
            double value = base.getOrDefault(property, 0f) + delta;
            result.put(property, valid(value) ? (float) value : fallback.getOrDefault(property, 0f));
        });
        compound(result, base, amounts, fallback, FightProperty.FIGHT_PROP_BASE_ATTACK,
                FightProperty.FIGHT_PROP_ATTACK_PERCENT, FightProperty.FIGHT_PROP_ATTACK, FightProperty.FIGHT_PROP_CUR_ATTACK);
        compound(result, base, amounts, fallback, FightProperty.FIGHT_PROP_BASE_DEFENSE,
                FightProperty.FIGHT_PROP_DEFENSE_PERCENT, FightProperty.FIGHT_PROP_DEFENSE, FightProperty.FIGHT_PROP_CUR_DEFENSE);
        return result;
    }

    private void compound(Map<FightProperty, Float> result, Map<FightProperty, Float> base,
            Map<FightProperty, Double> amounts, Map<FightProperty, Float> fallback,
            FightProperty raw, FightProperty percent, FightProperty flat, FightProperty current) {
        double ratio = amounts.getOrDefault(percent, 0d), delta = amounts.getOrDefault(flat, 0d);
        double baseValue = base.getOrDefault(raw, 0f), previous = base.getOrDefault(current, 0f);
        double scale = 1;
        if (entity instanceof EntityMonster) {
            double compound = baseValue * (1 + (double) base.getOrDefault(percent, 0f)) + base.getOrDefault(flat, 0f);
            if (compound > 0 && Double.isFinite(compound)) scale = previous / compound;
        }
        double next = previous + (baseValue * ratio + delta) * scale;
        if (!valid(next) || next < 0 || !valid(base.getOrDefault(percent, 0f) + ratio)
                || !valid(base.getOrDefault(flat, 0f) + delta)) {
            for (var property : List.of(percent, flat, current)) result.put(property, fallback.getOrDefault(property, 0f));
        } else result.put(current, (float) next);
    }

    /** The caller holds the entity registry and fight-property locks; publish after HP also refreshes. */
    Set<FightProperty> refresh() {
        var base = new EnumMap<FightProperty, Float>(FightProperty.class);
        for (var property : FightProperty.values()) {
            float current = entity.getFightProperty(property);
            var layer = applied.get(property);
            // Subtracting a huge float can erase a small baseline even without overflow.
            float original = layer == null ? current : Float.compare(current, layer.value()) == 0
                    ? layer.base() : (float) (current - ((double) layer.value() - layer.base()));
            base.put(property, original);
        }
        var controllers = entity.getAbilityHpModifier().activeControllers();
        var regular = amounts(controllers, base, false);
        var intermediate = plan(base, regular, base);
        nonExtra.forEach((property, source) -> intermediate.put(property, intermediate.get(source)));
        var combined = new EnumMap<FightProperty, Double>(regular);
        amounts(controllers, intermediate, true).forEach((property, value) -> combined.merge(property, value, Double::sum));
        var next = plan(base, combined, intermediate);
        nonExtra.keySet().forEach(property -> next.put(property, intermediate.get(property)));
        var tracked = new LinkedHashSet<>(applied.keySet());
        tracked.addAll(combined.keySet());
        tracked.addAll(nonExtra.keySet());
        for (var pair : List.of(List.of(FightProperty.FIGHT_PROP_ATTACK, FightProperty.FIGHT_PROP_ATTACK_PERCENT, FightProperty.FIGHT_PROP_CUR_ATTACK),
                List.of(FightProperty.FIGHT_PROP_DEFENSE, FightProperty.FIGHT_PROP_DEFENSE_PERCENT, FightProperty.FIGHT_PROP_CUR_DEFENSE))) {
            if (combined.containsKey(pair.get(0)) || combined.containsKey(pair.get(1))) tracked.addAll(pair);
        }
        var changed = new LinkedHashSet<FightProperty>();
        applied.clear();
        for (var property : tracked) {
            float value = next.get(property), before = entity.getFightProperty(property);
            if (!Float.isFinite(value) || !Float.isFinite(before)) continue;
            if (Float.compare(value, before) != 0) {
                entity.setFightProperty(property, value);
                changed.add(property);
            }
            if (Float.compare(value, base.get(property)) != 0) applied.put(property, new Layer(base.get(property), value));
        }
        return changed;
    }
}
