package emu.grasscutter.game.ability;

import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

import java.util.*;

/** Distance between entity origins; attachment transforms and unknown controls stay unresolved. */
public final class AbilityDistancePredicate {
    private static final Set<String> FIELDS =
            Set.of(
                    "$type",
                    "value",
                    "ratio",
                    "logic",
                    "target",
                    "forceByCaster",
                    "NJICAELBCCB",
                    "JBOGOHPICML",
                    "__unk_Q1160719197",
                    "compareType",
                    "axisType");
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CasterOriginOwner",
                    "OriginOwner",
                    "CurLocalAvatar",
                    "Team");
    private static final Set<String> AXES = Set.of("XZ", "Y", "XYZ");
    private static final Set<String> LOGIC =
            Set.of("Greater", "GreaterOrEqual", "Equal", "Lesser", "LesserOrEqual");

    private AbilityDistancePredicate() {}

    public static boolean supported(Map<?, ?> predicate) {
        if (predicate == null
                || !"CNFMGJJEOPM".equals(predicate.get("$type"))
                || !FIELDS.containsAll(predicate.keySet())
                || !emptyAttachment(predicate, "NJICAELBCCB")
                || !emptyAttachment(predicate, "JBOGOHPICML")
                || !emptyAttachment(predicate, "__unk_Q1160719197")
                || !(predicate.containsKey("value") || predicate.containsKey("ratio")))
            return false;
        if (predicate.containsKey("target")
                && !(predicate.get("target") instanceof String target && TARGETS.contains(target)))
            return false;
        if (predicate.containsKey("forceByCaster")
                && !(predicate.get("forceByCaster") instanceof Boolean)) return false;
        if (predicate.containsKey("logic") && !LOGIC.contains(predicate.get("logic"))) return false;
        if (predicate.containsKey("axisType") && !AXES.contains(predicate.get("axisType")))
            return false;
        if (predicate.containsKey("compareType") && !AXES.contains(predicate.get("compareType")))
            return false;
        return !predicate.containsKey("axisType")
                || !predicate.containsKey("compareType")
                || Objects.equals(predicate.get("axisType"), predicate.get("compareType"));
    }

    public static boolean evaluate(
            Map<String, Object> predicate,
            Ability ability,
            GameEntity owner,
            GameEntity defaultTarget) {
        if (!supported(predicate)) return false;
        if ("CurLocalAvatar".equals(predicate.get("target"))
                && (ability == null
                        || ability.getPlayerOwner() == null
                        || ability.getPlayerOwner()
                                .getTeamManager()
                                .getActiveTeamSnapshot()
                                .isEmpty())) return false;
        var source = owner != null ? owner : ability == null ? null : ability.getOwner();
        var target = PredicateEvaluator.resolveTarget(predicate, ability, source, defaultTarget);
        var player = ability == null ? null : ability.getPlayerOwner();
        if (source == null
                || target == null
                || source.getScene() != target.getScene()
                || !AbilityExecutionContext.canTrigger(source, player)
                || !AbilityExecutionContext.canTrigger(target, player)) return false;
        var origin = AbilityBornPosition.snapshot(source.getPosition());
        var end = AbilityBornPosition.snapshot(target.getPosition());
        if (origin == null || end == null) return false;
        var properties =
                ability == null
                        ? new Object2FloatOpenHashMap<String>()
                        : AbilityActionHandler.propertiesFor(ability, source);
        Object value =
                predicate.containsKey("value") ? predicate.get("value") : predicate.get("ratio");
        if (value == null || value instanceof Boolean) return false;
        float bound;
        try {
            var dynamic = JsonUtils.decode(JsonUtils.toJson(value), DynamicFloat.class);
            var result = dynamic == null ? OptionalDouble.empty() : dynamic.tryGet(properties);
            if (result.isEmpty()) return false;
            bound = (float) result.getAsDouble();
        } catch (RuntimeException invalid) {
            return false;
        }
        if (!Float.isFinite(bound)) return false;
        String axis =
                (String)
                        predicate.getOrDefault(
                                "axisType", predicate.getOrDefault("compareType", "XZ"));
        float x = end.getX() - origin.getX(),
                y = end.getY() - origin.getY(),
                z = end.getZ() - origin.getZ();
        float distance =
                switch (axis) {
                    case "Y" -> Math.abs(y);
                    case "XYZ" -> (float) Math.sqrt(x * x + y * y + z * z);
                    default -> (float) Math.sqrt(x * x + z * z);
                };
        if (!Float.isFinite(distance)) return false;
        return switch ((String) predicate.getOrDefault("logic", "Greater")) {
            case "Greater" -> distance > bound;
            case "GreaterOrEqual" -> distance >= bound;
            case "Equal" -> distance == bound;
            case "Lesser" -> distance < bound;
            case "LesserOrEqual" -> distance <= bound;
            default -> false;
        };
    }

    private static boolean emptyAttachment(Map<?, ?> predicate, String name) {
        return !predicate.containsKey(name) || "".equals(predicate.get(name));
    }
}
