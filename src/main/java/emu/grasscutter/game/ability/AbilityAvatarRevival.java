package emu.grasscutter.game.ability;

import com.google.gson.JsonElement;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;

import java.util.*;

/** Resource revival uses current party identities and preserves explicitly resolved recipients. */
public final class AbilityAvatarRevival {
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Caster",
                    "Owner",
                    "CurLocalAvatar",
                    "OriginOwner",
                    "CasterOriginOwner",
                    "AllPlayerAvatars",
                    "CurTeamAvatars");

    private AbilityAvatarRevival() {}

    public static boolean supported(AbilityModifierAction action) {
        return action != null
                && (action.type == AbilityModifierAction.Type.ReviveAvatar
                        || action.type == AbilityModifierAction.Type.ReviveDeadAvatar)
                && (action.target == null || TARGETS.contains(action.target))
                && action.otherTargets == null
                && radius(action.range).isPresent()
                && AbilityActionSequence.supportedPredicates(action.predicates, 0)
                && AbilityRevivalSkillControl.supported(action);
    }

    public static List<EntityAvatar> recipients(
            Ability ability, AbilityModifierAction action, GameEntity target, boolean resolved) {
        if (ability == null || ability.getPlayerOwner() == null || !supported(action))
            return List.of();
        var player = ability.getPlayerOwner();
        var party = player.getTeamManager().getActiveTeamSnapshot();
        if (resolved && target instanceof EntityAvatar avatar) return List.of(avatar);
        if ("AllPlayerAvatars".equals(action.target)
                || "CurTeamAvatars".equals(action.target)
                || action.target == null
                        && action.type == AbilityModifierAction.Type.ReviveDeadAvatar)
            return List.copyOf(new LinkedHashSet<>(party));
        if (("CurLocalAvatar".equals(action.target) || "OriginOwner".equals(action.target))
                && party.isEmpty()) return List.of();
        var selected =
                resolved
                        ? target
                        : AbilityActionHandler.resolveTarget(ability, target, action.target);
        return selected instanceof EntityAvatar avatar ? List.of(avatar) : List.of();
    }

    public static boolean eligible(
            Ability ability, AbilityModifierAction action, EntityAvatar target) {
        if (ability == null || target == null || ability.getPlayerOwner() == null) return false;
        var player = ability.getPlayerOwner();
        var scene = player.getScene();
        if (scene == null
                || ability.getOwner().getScene() != scene
                || target.getScene() != scene
                || target.getPlayer() != player
                || player.getTeamManager().getActiveTeamSnapshot().stream()
                        .noneMatch(member -> member == target)
                || !AbilityGlobalValues.isCurrent(ability, target)) return false;
        var radius = radius(action.range);
        if (radius.isEmpty()) return false;
        if (radius.getAsDouble() == Double.POSITIVE_INFINITY) return true;
        var origin = ability.getOwner().getPosition();
        var position = target.getPosition();
        return AbilityGlobalPositions.finite(origin)
                && AbilityGlobalPositions.finite(position)
                && origin.computeDistance(position) <= radius.getAsDouble();
    }

    public static OptionalDouble health(
            Ability ability, AbilityModifierAction action, EntityAvatar target) {
        var caster = ability.getCasterEntity();
        var owner = caster == null ? ability.getOwner() : caster;
        var properties = AbilityActionHandler.propertiesFor(ability, owner);
        DynamicFloat[] amounts = {
            action.amount,
            action.amountByCasterMaxHPRatio,
            action.amountByCasterAttackRatio,
            action.amountByTargetCurrentHPRatio,
            action.amountByTargetMaxHPRatio
        };
        double[] factors = {
            1,
            owner.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),
            owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),
            target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),
            target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)
        };
        double total = 0;
        for (int i = 0; i < amounts.length; i++) {
            if (amounts[i] == null) return OptionalDouble.empty();
            var value = amounts[i].tryGet(properties);
            if (value.isEmpty()
                    || !Double.isFinite(value.getAsDouble())
                    || value.getAsDouble() < 0
                    || !Double.isFinite(factors[i])
                    || factors[i] < 0) return OptionalDouble.empty();
            total += value.getAsDouble() * factors[i];
        }
        float maximum = target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        return Double.isFinite(total) && total > 0 && Float.isFinite(maximum) && maximum > 0
                ? OptionalDouble.of(Math.min(maximum, total))
                : OptionalDouble.empty();
    }

    private static OptionalDouble radius(JsonElement value) {
        if (value == null || value.isJsonNull()) return OptionalDouble.of(Double.POSITIVE_INFINITY);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            return OptionalDouble.empty();
        try {
            double radius = value.getAsDouble();
            return Double.isFinite(radius) && radius > 0
                    ? OptionalDouble.of(radius)
                    : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
