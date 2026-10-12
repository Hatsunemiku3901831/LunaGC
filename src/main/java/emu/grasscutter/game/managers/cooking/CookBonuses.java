package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;

import java.util.*;

/** Active life skills must belong to the selected owned avatar's currently open skill groups. */
final class CookBonuses {
    private CookBonuses() {}

    private static List<ProudSkillData> skills(Avatar avatar) {
        if (avatar == null || avatar.getTrialAvatarId() != 0 || avatar.getSkillDepot() == null)
            return List.of();
        var groups = new HashSet<Integer>();
        avatar.getSkillDepot().getInherentProudSkillOpens().stream()
                .filter(open -> open.getNeedAvatarPromoteLevel() <= avatar.getPromoteLevel())
                .forEach(open -> groups.add(open.getProudSkillGroupId()));
        avatar.getSkillDepot().getQuestProudSkillGroupIds().intStream().forEach(groups::add);
        var result = new ArrayList<ProudSkillData>();
        for (int id : avatar.getProudSkillList()) {
            var skill = GameData.getProudSkillDataMap().get(id);
            if (skill != null
                    && skill.getId() == id
                    && groups.contains(skill.getProudSkillGroupId())
                    && skill.getBreakLevel() <= avatar.getPromoteLevel()) result.add(skill);
        }
        return result;
    }

    static boolean canCook(Player player, int assistant) {
        if (assistant == 0) return true;
        var avatar = player.getAvatars().getAvatarById(assistant);
        return avatar != null
                && avatar.getTrialAvatarId() == 0
                && skills(avatar).stream()
                        .noneMatch(
                                s ->
                                        "PROUD_EFFECT_COOK_CAN_NOT_COOK"
                                                .equals(s.getLifeEffectType()));
    }

    record Extra(int chance, int quality, int count) {}

    static List<Extra> extras(Player player, int assistant, CookRecipeData recipe, int quality) {
        if (quality == 0) quality = 3;
        int category =
                switch (Objects.toString(recipe.getFoodType(), "")) {
                    case "COOK_FOOD_HEAL" -> 1;
                    case "COOK_FOOD_ATTACK" -> 2;
                    case "COOK_FOOD_FUNCTION" -> 3;
                    case "COOK_FOOD_DEFENSE" -> 4;
                    default -> 0;
                };
        if (category == 0) return List.of();
        var best = new LinkedHashMap<Integer, Extra>();
        for (var skill : skills(player.getAvatars().getAvatarById(assistant))) {
            try {
                var params = skill.getLifeEffectParams();
                if (params == null || params.size() < 3) continue;
                int chance = Integer.parseInt(params.get(0)),
                        amount = Integer.parseInt(params.get(2));
                if (chance <= 0 || chance > 10000 || amount <= 0) continue;
                boolean matches =
                        Arrays.stream(params.get(1).split(","))
                                .anyMatch(value -> Integer.parseInt(value) == category);
                if (!matches) continue;
                int target;
                if ("PROUD_EFFECT_COOK_PERFECT_MULTI_OUTPUT".equals(skill.getLifeEffectType())) {
                    if (quality != 3) continue;
                    target = 3;
                } else if ("PROUD_EFFECT_COOK_PROB_MULTI_OUTPUT"
                        .equals(skill.getLifeEffectType())) {
                    if (params.size() < 5 || Integer.parseInt(params.get(3)) != quality) continue;
                    target = Integer.parseInt(params.get(4));
                    if (target < 1 || target > 3) continue;
                } else continue;
                var extra = new Extra(chance, target, amount);
                var old = best.get(target);
                if (old == null
                        || extra.chance > old.chance
                        || extra.chance == old.chance && extra.count > old.count)
                    best.put(target, extra);
            } catch (NumberFormatException | NullPointerException invalid) {
                // Invalid resource rows grant no effect and never invent a probability.
            }
        }
        return List.copyOf(best.values());
    }
}
