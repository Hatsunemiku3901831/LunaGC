package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ForgeData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;

import java.math.BigDecimal;
import java.util.*;

/** Freeze active, matching ore-return passives when the assistant starts a paid queue. */
final class ForgeBonuses {
    private ForgeBonuses() {}

    static List<ItemParamData> oreReturns(
            Player player, int avatarId, ForgeData recipe, List<ItemParamData> costs, int units) {
        var avatar = player.getAvatars().getAvatarById(avatarId);
        if (avatar == null || avatar.getTrialAvatarId() != 0 || avatar.getSkillDepot() == null)
            return List.of();
        int product =
                recipe.getResultItemId() > 0 ? recipe.getResultItemId() : recipe.getShowItemId();
        var item = GameData.getItemDataMap().get(product);
        if (item == null || item.getItemType() != ItemType.ITEM_WEAPON) return List.of();
        var groups = new HashSet<Integer>();
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
                    || !"PROUD_EFFECT_FORGE_RETURN_MATERIAL".equals(skill.getLifeEffectType()))
                continue;
            try {
                var params = skill.getLifeEffectParams();
                if (params == null
                        || params.size() < 3
                        || Integer.parseInt(params.get(0)) != 10000
                        || Integer.parseInt(params.get(1)) != recipe.getForgeType()) continue;
                int value =
                        new BigDecimal(params.get(2))
                                .multiply(BigDecimal.valueOf(10000))
                                .intValueExact();
                if (value >= 0 && value <= 10000) rate = Math.max(rate, value);
            } catch (NumberFormatException | ArithmeticException | NullPointerException invalid) {
                /* Invalid or non-guaranteed configurations grant no effect. */
            }
        }
        if (rate == 0) return List.of();
        var returns = new ArrayList<ItemParamData>();
        for (var cost : costs) {
            // The boost table identifies current minerals; billets and currencies are excluded.
            var mineral = GameData.getCompoundBoostDataMap().get(cost.getId());
            var data = GameData.getItemDataMap().get(cost.getId());
            if (mineral == null
                    || mineral.getId() != cost.getId()
                    || mineral.getBoostTime() <= 0
                    || data == null
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                    || data.isUseOnGain()) continue;
            long perUnit = (long) (cost.getCount() / units) * rate / 10000;
            if (perUnit > 0) returns.add(new ItemParamData(cost.getId(), Math.toIntExact(perUnit)));
        }
        return returns;
    }
}
