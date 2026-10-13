package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;

import java.util.*;

/** The selected skill instances must have all their charges restored. */
public final class AbilitySkillReadyPredicate {
    private AbilitySkillReadyPredicate() {}

    public static boolean evaluate(Map<String, Object> predicate, GameEntity target) {
        if (!(target instanceof EntityAvatar entity) || entity.getPlayer() == null) return false;
        Long id = unsigned(predicate.getOrDefault("skillID", 0));
        if (id == null) return false;
        var selected = new LinkedHashSet<Integer>();
        if (id != 0) selected.add(id.intValue());
        if (predicate.containsKey("skillSlot")) {
            if (!(predicate.get("skillSlot") instanceof List<?> slots)) return false;
            for (var value : slots) {
                Long slot = unsigned(value);
                if (slot == null) return false;
                int skill = AbilitySkillSlots.selectedSkill(entity, slot);
                if (skill != 0) selected.add(skill);
            }
        }
        for (int skill : selected) if (!fullyRecovered(entity, skill)) return false;
        return true;
    }

    private static Long unsigned(Object value) {
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        return Double.isFinite(result)
                        && result >= 0
                        && result <= 0xffffffffL
                        && result == Math.rint(result)
                ? (long) result
                : null;
    }

    private static boolean fullyRecovered(EntityAvatar entity, int skillId) {
        var avatar = entity.getAvatar();
        var skill = GameData.getAvatarSkillDataMap().get(skillId);
        // A missing instance does not have an incomplete charge in the native lookup.
        if (skill == null || !AbilitySkillCooldown.ownsSkill(avatar, skillId)) return true;
        var manager = entity.getPlayer().getAbilityManager();
        if (manager == null) return false;
        if (skill.getShareCDID() != 0)
            return manager.getSharedCooldown().fullyRecovered(skill.getShareCDID()).orElse(false);
        int count = avatar.getSkillInfoCharges().getOrDefault(skillId, skill.getMaxChargeNum());
        var state = manager.getSkillCooldown().state(avatar, skillId, count);
        return state != null && state.getFullCdTimeListCount() == 0;
    }
}
