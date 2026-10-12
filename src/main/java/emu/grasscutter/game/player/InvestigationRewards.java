package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;
import java.util.function.Consumer;

public final class InvestigationRewards {
    private InvestigationRewards() {}

    public static int takeTarget(
            Player player, int id, Runnable save, Consumer<List<ItemParamData>> grant) {
        if (player == null || id <= 0) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        synchronized (player.getInventory()) {
            var target = GameData.getInvestigationTargetDataMap().get(id);
            if (target == null
                    || target.getId() != id
                    || target.isDisuse()
                    || target.getProgress() <= 0) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            var chapter = GameData.getInvestigationDataMap().get(target.getInvestigationId());
            if (!InvestigationHandbook.open(player, chapter))
                return Retcode.RET_OPEN_STATE_NOT_OPEN_VALUE;
            var progress = player.getPlayerProgress().getInvestigation();
            synchronized (progress) {
                int state = progress.getTargetRewardState(id);
                if (state == 2) return Retcode.RET_REWARD_HAS_TAKEN_VALUE;
                if (state != 0) return Retcode.RET_SVR_ERROR_VALUE;
                if (progress.get(id) < target.getProgress()) return Retcode.RET_FAIL_VALUE;
                var reward = GameData.getRewardDataMap().get(target.getRewardId());
                if (reward == null
                        || reward.getId() != target.getRewardId()
                        || reward.getRewardItemList() == null)
                    return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                var items =
                        reward.getRewardItemList().stream()
                                .map(
                                        item ->
                                                item == null
                                                        ? null
                                                        : new ItemParamData(
                                                                item.getId(), item.getCount()))
                                .toList();
                int preflight = InvestigationRewardDelivery.validate(player, items);
                if (preflight != 0) return preflight;
                progress.setTargetRewardState(id, 1);
                try {
                    save.run();
                } catch (RuntimeException failure) {
                    // No grant has been attempted; a subsequent attempt can reserve again.
                    progress.setTargetRewardState(id, 0);
                    throw failure;
                }
                grant.accept(items);
                progress.setTargetRewardState(id, 2);
                try {
                    save.run();
                } catch (RuntimeException failure) {
                    progress.setTargetRewardState(id, 1);
                    throw failure;
                }
                return 0;
            }
        }
    }

    public static int takeTarget(Player player, int id) {
        if (player == null) return Retcode.RET_SVR_ERROR_VALUE;
        try {
            InvestigationHandbook.refreshAndNotify(player);
            int result =
                    takeTarget(
                            player,
                            id,
                            () -> DatabaseHelper.saveGameSync(player),
                            items -> InvestigationRewardDelivery.grant(player, items));
            if (result == 0 || result == Retcode.RET_REWARD_HAS_TAKEN_VALUE)
                InvestigationHandbook.refreshAndNotify(player);
            return result;
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .error(
                            "Handbook reward needs reconciliation: UID {}, target {}",
                            player.getUid(),
                            id,
                            failure);
            return Retcode.RET_SVR_ERROR_VALUE;
        }
    }
}
