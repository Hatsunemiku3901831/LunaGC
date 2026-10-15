package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.*;

import java.util.*;

/** AJPLICCNACO requires every configured feature tag on the selected entity's resource group. */
public final class AbilityFeatureTagPredicate {
    private static final Set<String> FIELDS =
            Set.of("$type", "target", "forceByCaster", "BFLILDJLGHP", "featureTagIDs");
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

    private AbilityFeatureTagPredicate() {}

    public static boolean supported(Map<?, ?> predicate) {
        if (predicate == null
                || !"AJPLICCNACO".equals(predicate.get("$type"))
                || !FIELDS.containsAll(predicate.keySet())
                || !validTags(tags(predicate))) return false;
        var tags = tags(predicate);
        if (predicate.containsKey("BFLILDJLGHP") && predicate.containsKey("featureTagIDs")) {
            // The current resource export includes 696 copies with both names. Never pick a side
            // when those aliases disagree.
            var alias = predicate.get("featureTagIDs");
            if (!validTags(alias) || ((List<?>) alias).size() != tags.size()) return false;
            for (int i = 0; i < tags.size(); i++)
                if (((Number) tags.get(i)).longValue()
                        != ((Number) ((List<?>) alias).get(i)).longValue()) return false;
        }
        return (!predicate.containsKey("target")
                        || predicate.get("target") instanceof String target
                                && TARGETS.contains(target))
                && (!predicate.containsKey("forceByCaster")
                        || predicate.get("forceByCaster") instanceof Boolean);
    }

    private static List<?> tags(Map<?, ?> predicate) {
        Object value =
                predicate.containsKey("BFLILDJLGHP")
                        ? predicate.get("BFLILDJLGHP")
                        : predicate.get("featureTagIDs");
        return value instanceof List<?> list ? list : null;
    }

    private static boolean validTags(Object value) {
        if (!(value instanceof List<?> tags) || tags.size() > 256) return false;
        for (var tag : tags) {
            if (!(tag instanceof Number number)) return false;
            double id = number.doubleValue();
            if (!Double.isFinite(id) || id < 0 || id > 0xffff_ffffL || id != Math.rint(id))
                return false;
        }
        return true;
    }

    public static boolean evaluate(
            Map<String, Object> predicate, Ability ability, GameEntity owner, GameEntity target) {
        if (!supported(predicate)) return false;
        var selected = PredicateEvaluator.resolveTarget(predicate, ability, owner, target);
        if (selected == null) return false;
        var group = GameData.getFeatureTagGroupDataMap().get(groupId(selected));
        if (group == null || group.getTagIds() == null) return false;
        for (var tag : tags(predicate)) {
            long required = ((Number) tag).longValue();
            if (Arrays.stream(group.getTagIds()).noneMatch(value -> value == required))
                return false;
        }
        return true;
    }

    private static int groupId(GameEntity entity) {
        if (entity instanceof EntityAvatar selected) {
            var avatar = selected.getAvatar();
            var data = avatar.getAvatarData();
            if (data == null) data = GameData.getAvatarDataMap().get(avatar.getAvatarId());
            if (data == null) return 0;
            var depot = avatar.getSkillDepot();
            if (depot == null)
                depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
            return depot != null && depot.getFeatureTagGroupID() != 0
                    ? depot.getFeatureTagGroupID()
                    : data.getFeatureTagGroupID();
        }
        if (entity instanceof EntityMonster monster)
            return monster.getMonsterData() == null
                    ? 0
                    : monster.getMonsterData().getFeatureTagGroupID();
        if (entity instanceof EntityVehicle vehicle) {
            var data = vehicle.getVehicleDataA();
            if (data == null) data = GameData.getVehicleDataMap().get(vehicle.getGadgetId());
            return data == null ? 0 : data.getFeatureTagGroupID();
        }
        return 0;
    }
}
