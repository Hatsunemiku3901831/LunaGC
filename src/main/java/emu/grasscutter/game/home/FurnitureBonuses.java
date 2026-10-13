package emu.grasscutter.game.home;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;

import java.math.BigDecimal;
import java.util.*;

/** Freeze guaranteed, active material returns for the selected owned furnishing assistant. */
final class FurnitureBonuses {
    private FurnitureBonuses() {}

    static Map<Integer, Integer> materialReturns(
            Player player, int avatarId, ItemData product, Map<Integer, Integer> costs) {
        var avatar = player.getAvatars().getAvatarById(avatarId);
        if (avatar == null
                || avatar.getTrialAvatarId() != 0
                || avatar.getSkillDepot() == null
                || product.getFurnType() == null) return Map.of();
        Set<Integer> categories = new HashSet<>(), groups = new HashSet<>();
        for (int id : product.getFurnType()) {
            var type = GameData.getFurnitureTypeDataMap().get(id);
            if (type != null && type.getId() == id && type.getTypeCategoryId() > 0)
                categories.add(type.getTypeCategoryId());
        }
        if (categories.isEmpty()) return Map.of();
        avatar.getSkillDepot().getInherentProudSkillOpens().stream()
                .filter(open -> open.getNeedAvatarPromoteLevel() <= avatar.getPromoteLevel())
                .forEach(open -> groups.add(open.getProudSkillGroupId()));
        avatar.getSkillDepot().getQuestProudSkillGroupIds().intStream().forEach(groups::add);
        int rate = 0;
        for (int id : avatar.getProudSkillList()) {
            var skill = GameData.getProudSkillDataMap().get(id);
            if (skill == null
                    || skill.getId() != id
                    || !groups.contains(skill.getProudSkillGroupId())
                    || skill.getBreakLevel() > avatar.getPromoteLevel()
                    || !"PROUD_EFFECT_FURNITURE_MAKE_RETURN_MATERIAL"
                            .equals(skill.getLifeEffectType())) continue;
            try {
                var params = skill.getLifeEffectParams();
                if (params == null || params.size() < 3 || Integer.parseInt(params.get(0)) != 10000)
                    continue;
                Set<Integer> matches = new HashSet<>();
                for (var value : params.get(1).split(",", -1)) {
                    int category = Integer.parseInt(value.trim());
                    if (category <= 0)
                        throw new NumberFormatException("Invalid furnishing category");
                    matches.add(category);
                }
                if (Collections.disjoint(categories, matches)) continue;
                int value =
                        new BigDecimal(params.get(2))
                                .multiply(BigDecimal.valueOf(10000))
                                .intValueExact();
                if (value >= 0 && value <= 10000) rate = Math.max(rate, value);
            } catch (NumberFormatException | ArithmeticException | NullPointerException invalid) {
                // Invalid configurations and non-guaranteed effects grant no guaranteed return.
            }
        }
        if (rate == 0) return Map.of();
        Map<Integer, Integer> returns = new LinkedHashMap<>();
        for (var cost : costs.entrySet()) {
            int amount = (int) ((long) cost.getValue() * rate / 10000);
            if (amount > 0) returns.put(cost.getKey(), amount);
        }
        return returns;
    }
}
