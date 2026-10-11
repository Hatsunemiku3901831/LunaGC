package emu.grasscutter.game.activity.aster;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.List;
import java.util.function.*;

/** Original special-reward conditions with an acknowledged, per-replay reservation. */
public final class AsterSpecialRewards {
    public static final int REWARD_ID = 3000000, QUEST_WATCHER = 1200130;
    private static final List<Integer> CONDITIONS = List.of(QUEST_WATCHER, 1200131, 1200132);

    private AsterSpecialRewards() {}

    public static boolean resourcesAvailable() {
        var preview = GameData.getAsterPreviewDataMap().get(AsterSchedule.ACTIVITY_ID);
        var quest = GameData.getActivityWatcherDataMap().get(QUEST_WATCHER);
        return preview != null
                && preview.getActivityId() == AsterSchedule.ACTIVITY_ID
                && preview.getUnlockLevel() == 20
                && preview.getSpecialRewardId() == REWARD_ID
                && preview.getWatcherList().equals(CONDITIONS)
                && AsterMilestones.resourcesAvailable()
                && quest != null
                && quest.getId() == QUEST_WATCHER
                && !quest.isDisuse()
                && quest.getRewardID() == 0
                && quest.getProgress() == 1
                && quest.getTriggerConfig() != null
                && quest.getTriggerConfig().getWatcherTriggerType()
                        == emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_FINISH_QUEST_AND
                && quest.getTriggerConfig().getParamList().equals(List.of("4111309"));
    }

    public static int status(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        if (data == null
                || config == null
                || !config.isActiveAt(now)
                || config.getActivityId() != AsterSchedule.ACTIVITY_ID
                || data.getActivityId() != AsterSchedule.ACTIVITY_ID
                || config.getScheduleId() != data.getScheduleId())
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        if (rank < 20) return Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE;
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            if (progress.specialRewardTaken())
                return Retcode.RET_ASTER_SPECIAL_REWARD_HAS_TAKEN_VALUE;
            if (progress.hasPendingReward() || !resourcesAvailable())
                return Retcode.RET_SVR_ERROR_VALUE;
            if (data.getWatcherInfoMap() == null) return Retcode.RET_SVR_ERROR_VALUE;
            for (int id : CONDITIONS) {
                var condition = data.getWatcherInfoMap().get(id);
                var meta = GameData.getActivityWatcherDataMap().get(id);
                if (condition == null)
                    return Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE;
                if (condition.getWatcherId() != id
                        || condition.getTotalProgress() != meta.getProgress()
                        || condition.getCurProgress() < 0
                        || condition.getCurProgress() > meta.getProgress())
                    return Retcode.RET_SVR_ERROR_VALUE;
                if (!condition.isFinished())
                    return Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE;
            }
            // Manually edited watcher values cannot substitute for confirmed personal claims.
            return progress.midCollectCount() >= 14 && progress.bigCollectCount() >= 7
                    ? 0
                    : Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE;
        }
    }

    public static int take(
            PlayerActivityData data,
            ActivityConfigItem config,
            int rank,
            long now,
            IntUnaryOperator validate,
            IntConsumer grant,
            Runnable save) {
        if (data == null) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        synchronized (data) {
            int code = status(data, config, rank, now);
            if (code != 0) return code;
            code = validate.applyAsInt(REWARD_ID);
            if (code != 0) return code;
            var progress = AsterSchedule.progress(data);
            String original = data.getDetail();
            progress.beginSpecialReward();
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                data.setDetailJson(original);
                throw failure;
            }
            String reservation = data.getDetail();
            try {
                grant.accept(REWARD_ID);
                progress.finishSpecialReward();
                data.setDetail(progress);
                save.run();
            } catch (RuntimeException failure) {
                data.setDetailJson(reservation);
                Grasscutter.getLogger()
                        .error(
                                "Aster special reward needs reconciliation: UID {}, schedule {}",
                                data.getUid(),
                                data.getScheduleId(),
                                failure);
                throw failure;
            }
            return 0;
        }
    }

    public static int take(PlayerActivityData data) {
        if (data == null) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        var player = data.getPlayer();
        if (player == null
                || player.getUid() != data.getUid()
                || player.getActivityManager()
                                .getPlayerActivityDataMap()
                                .get(AsterSchedule.ACTIVITY_ID)
                        != data) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        synchronized (player.getInventory()) {
            return take(
                    data,
                    ActivityManager.getScheduleActivityConfigMap().get(data.getScheduleId()),
                    player.getLevel(),
                    System.currentTimeMillis(),
                    reward -> AsterSpecialRewardDelivery.validate(player, reward),
                    reward -> AsterSpecialRewardDelivery.grant(player, reward),
                    data::saveSync);
        }
    }
}
