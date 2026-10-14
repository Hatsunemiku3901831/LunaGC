package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.utils.JsonUtils;

import java.util.List;
import java.util.Map;

public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    public static boolean all(
            List<Map<String, Object>> predicates,
            Ability ability,
            GameEntity owner,
            GameEntity target,
            AbilityModifierAction action) {
        if (predicates == null || predicates.isEmpty()) return true;
        for (var pred : predicates) {
            if (pred == null) continue;
            if (!evaluate(pred, ability, owner, target, action)) return false;
        }
        return true;
    }

    public static boolean evaluate(
            Map<String, Object> pred,
            Ability ability,
            GameEntity owner,
            GameEntity target,
            AbilityModifierAction action) {
        if (pred == null) return false;
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String type)) return true;
        if ("CNFMGJJEOPM".equals(type))
            return AbilityDistancePredicate.evaluate(pred, ability, owner, target);
        if ("CBHEIAINBAI".equals(type))
            return AbilityEntityAvailability.evaluate(pred, ability, owner, target);
        GameEntity resolved = resolveTarget(pred, ability, owner, target);
        if ("ByUnlockTalentParam".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            GameEntity talentTarget = caster != null ? caster : (owner != null ? owner : resolved);
            if (pred.get("target") instanceof String
                    && !Boolean.TRUE.equals(pred.get("forceByCaster"))) {
                talentTarget = resolved;
                if ("Team".equals(pred.get("target"))
                        && ability != null
                        && ability.getPlayerOwner() != null)
                    talentTarget =
                            ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
            }
            return byUnlockTalentParam(pred, ability, talentTarget);
        }
        return switch (type) {
            case "DEOFBICNFHF" -> AbilityEntityTypePredicate.evaluate(pred, ability, resolved);
            case "ByHasModifier" -> byHasModifier(pred, ability, resolved);
            case "ByTargetGlobalValue" -> byTargetGlobalValue(pred, ability, resolved);
            case "ByTargetHPRatio" -> byTargetHPRatio(pred, ability, resolved);
                // Verified against the current 7.1 client; these are not unknown predicates.
            case "MIJGFBNEIOG" -> byTargetHpValue(pred, ability, resolved);
            case "BAEGOCNMDPJ" -> AbilitySkillReadyPredicate.evaluate(pred, resolved);
            case "LPKOBOLMDBI" -> byTargetHpDebts(pred, ability, resolved);
            case "ONJEHDDFFCH" -> byHealTags(pred, action);
            case "GKGBIPDLMMG" -> none(pred, ability, owner, target, action);
            default -> true;
        };
    }

    static GameEntity resolveTarget(
            Map<String, Object> pred, Ability ability, GameEntity owner, GameEntity defaultTarget) {
        if (Boolean.TRUE.equals(pred.get("forceByCaster")) && ability != null) {
            var caster = ability.getCasterEntity();
            if (caster != null) return caster;
            if (ability.getOwner() != null) return ability.getOwner();
        }
        Object t = pred.get("target");
        if (!(t instanceof String s)) return defaultTarget;
        return switch (s) {
            case "Self", "Target", "Applier" -> defaultTarget;
            case "Owner" -> owner != null ? owner : defaultTarget;
            case "Caster", "CasterOriginOwner", "OriginOwner" -> ability != null
                    ? ability.getCasterEntity()
                    : defaultTarget;
            case "CurLocalAvatar" -> ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity()
                    : defaultTarget;
            case "Team" -> ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getEntity()
                    : defaultTarget;
            default -> defaultTarget;
        };
    }

    private static boolean byUnlockTalentParam(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        Object tp = pred.get("talentParam");
        if (!(tp instanceof String talentParam) || talentParam.isEmpty()) return false;
        if (!(target instanceof EntityAvatar ea)) return false;
        String abilityName =
                pred.get("abilityName") instanceof String name
                        ? name
                        : ability != null ? ability.getData().abilityName : null;
        return AbilityTalentResolver.unlocked(ea.getAvatar(), abilityName, talentParam);
    }

    private static boolean byHasModifier(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        Object mn = pred.get("modifierName");
        if (!(mn instanceof String modifierName) || ability == null || target == null) return false;
        for (var states : target.getAppliedAbilityModifiers().values()) {
            if (states.containsKey(modifierName)) return true;
        }
        for (var modifier : target.getInstancedModifiersSnapshot()) {
            if (modifierName.equals(modifier.getName())) return true;
        }
        return false;
    }

    private static boolean byTargetGlobalValue(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null) return false;
        Object key = pred.get("key");
        if (!(key instanceof String k)) return false;
        float current = target.getGlobalAbilityValues().getOrDefault(k, 0f);
        float bound = readFloat(pred.get("value"), ability);
        Object cmpObj = pred.get("compareType");
        String cmp = cmpObj instanceof String s ? s : "Equal";
        return compare(current, bound, cmp);
    }

    private static boolean compare(float current, float bound, String logic) {
        return switch (logic) {
            case "MoreThan", "Greater" -> current > bound;
            case "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual" -> current >= bound;
            case "LessThan", "Lesser" -> current < bound;
            case "LessThanAndEqual", "LessAndEqual", "LessOrEqual", "LesserOrEqual" -> current
                    <= bound;
            case "NotEqual" -> current != bound;
            default -> current == bound;
        };
    }

    private static boolean byTargetHPRatio(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || !pred.containsKey("HPRatio")) return false;
        float maxHp =
                target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP);
        float curHp =
                target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        if (maxHp <= 0f) return false;
        float threshold = readFloat(pred.get("HPRatio"), ability);
        String logic = pred.get("logic") instanceof String value ? value : "Greater";
        return compare(curHp / maxHp, threshold, logic);
    }

    private static boolean byTargetHpValue(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || pred.get("HP") == null) return false;
        float current =
                target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        float bound = readFloat(pred.get("HP"), ability);
        if (!Float.isFinite(current) || !Float.isFinite(bound)) return false;
        String logic = pred.get("logic") instanceof String value ? value : "Greater";
        // The native comparison enum has these five values; invalid values return false.
        return switch (logic) {
            case "Greater", "GreaterOrEqual", "Equal", "Lesser", "LesserOrEqual" -> compare(
                    current, bound, logic);
            default -> false;
        };
    }

    private static boolean byTargetHpDebts(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || !pred.containsKey("value")) return false;
        float current =
                target.getFightProperty(
                        emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float bound = readFloat(pred.get("value"), ability);
        if (!Float.isFinite(current) || !Float.isFinite(bound)) return false;
        // The native enum defaults to 0 (Greater), including when JSON omits logic.
        String logic = pred.get("logic") instanceof String value ? value : "Greater";
        return compare(current, bound, logic);
    }

    private static boolean byHealTags(Map<String, Object> pred, AbilityModifierAction event) {
        String healTag =
                event != null && event.type == AbilityModifierAction.Type.HealHP
                        ? event.healTag
                        : AbilityHealContext.currentTag();
        if (healTag == null) return false;
        Object tags = pred.get("LAIFKEGOFJO");
        if (tags == null) tags = pred.get("__exp_healTags");
        return tags instanceof List<?> values && values.contains(healTag);
    }

    private static boolean none(
            Map<String, Object> pred,
            Ability ability,
            GameEntity owner,
            GameEntity target,
            AbilityModifierAction event) {
        if (!(pred.get("predicates") instanceof List<?> children)) return false;
        // Native evaluation stops on the first true child. This is NOR, not !all(children).
        for (var child : children) {
            if (!(child instanceof Map<?, ?>)) return false;
            @SuppressWarnings("unchecked")
            var typed = (Map<String, Object>) child;
            if (evaluate(typed, ability, owner, target, event)) return false;
        }
        return true;
    }

    private static float readFloat(Object v, Ability ability) {
        if (v instanceof Boolean b) return b ? 1f : 0f;
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof Map<?, ?> m) {
            Object inner = m.get("value");
            if (inner != null) return readFloat(inner, ability);
            Object exp = m.get("__exp_FixedValue");
            return exp != null ? readFloat(exp, ability) : 0f;
        }
        if (v == null) return 0f;
        var dynamic = JsonUtils.decode(JsonUtils.toJson(v), DynamicFloat.class);
        return ability == null
                ? dynamic.get()
                : dynamic.get(AbilityActionHandler.propertiesFor(ability), 0f);
    }
}
