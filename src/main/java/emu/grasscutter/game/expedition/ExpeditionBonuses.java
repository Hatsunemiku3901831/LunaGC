package emu.grasscutter.game.expedition;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;

import java.math.BigDecimal;
import java.util.*;

/** Resource-backed active passives, frozen once per trip rather than trusted from the request. */
public final class ExpeditionBonuses {
    private ExpeditionBonuses() {}

    public static boolean bind(Player player, long guid, ExpeditionInfo info) {
        if (info == null || info.isBonusRecorded() || info.getRewardState() != 0) return false;
        var avatar = player.getAvatars().getAvatarByGuid(guid);
        var location = GameData.getExpeditionDataMap().get(info.getExpId());
        if (avatar == null
                || avatar.getTrialAvatarId() != 0
                || avatar.getSkillDepot() == null
                || location == null) return false;
        var groups = new HashSet<Integer>();
        avatar.getSkillDepot().getInherentProudSkillOpens().stream()
                .filter(p -> p.getNeedAvatarPromoteLevel() <= avatar.getPromoteLevel())
                .forEach(p -> groups.add(p.getProudSkillGroupId()));
        avatar.getSkillDepot().getQuestProudSkillGroupIds().intStream().forEach(groups::add);
        int shorten = 0, extra = 0, probability = 0;
        for (int id : avatar.getProudSkillList()) {
            var data = GameData.getProudSkillDataMap().get(id);
            if (data == null
                    || data.getId() != id
                    || !groups.contains(data.getProudSkillGroupId())
                    || data.getBreakLevel() > avatar.getPromoteLevel()
                    || data.getLifeEffectParams() == null) continue;
            var params = data.getLifeEffectParams();
            try {
                if (params.isEmpty() || Integer.parseInt(params.get(0)) != location.getCityId())
                    continue;
                if ("PROUD_EFFECT_EXPEDITION_SHORTEN_TIME".equals(data.getLifeEffectType())
                        && params.size() >= 2) {
                    int rate = rate(params.get(1));
                    if (rate >= 0 && rate < 10000) shorten = Math.max(shorten, rate);
                } else if ("PROUD_EFFECT_EXPEDITION_EXTRA_OUTPUT".equals(data.getLifeEffectType())
                        && params.size() >= 4
                        && Integer.parseInt(params.get(1)) == info.getHourTime()) {
                    int chance = Integer.parseInt(params.get(2)), rate = rate(params.get(3));
                    if (chance >= 0
                            && chance <= 10000
                            && rate >= 0
                            && rate <= 10000
                            && (rate > extra || rate == extra && chance > probability)) {
                        extra = rate;
                        probability = chance;
                    }
                }
            } catch (NumberFormatException | ArithmeticException | NullPointerException malformed) {
                /* Invalid row grants no effect. */
            }
        }
        info.setShortenRate(shorten);
        info.setExtraRate(extra);
        info.setExtraProbability(probability);
        info.setBonusRecorded(true);
        return true;
    }

    private static int rate(String text) {
        return new BigDecimal(text).multiply(BigDecimal.valueOf(10000)).intValueExact();
    }
}
