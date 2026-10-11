package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.ActivityWatcherData;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.props.WatcherTriggerType;

import java.util.List;

/** Special-reward conditions derive from confirmed personal receipts, never battle completion. */
public final class AsterMilestones {
    public static final int MIDDLE = 1200131, LARGE = 1200132;

    private AsterMilestones() {}

    private static ActivityWatcherData metadata(int id) {
        if (id != MIDDLE && id != LARGE) return null;
        var meta = GameData.getActivityWatcherDataMap().get(id);
        return meta != null
                        && meta.getId() == id
                        && !meta.isDisuse()
                        && meta.getRewardID() == 0
                        && meta.getProgress() == (id == MIDDLE ? 14 : 7)
                        && meta.getTriggerConfig() != null
                        && meta.getTriggerConfig().getWatcherTriggerType()
                                == WatcherTriggerType.TRIGGER_TAKE_GENERAL_REWARD
                        && meta.getTriggerConfig()
                                .getParamList()
                                .equals(List.of(id == MIDDLE ? "70950021" : "70330041"))
                ? meta
                : null;
    }

    public static boolean resourcesAvailable() {
        return metadata(MIDDLE) != null && metadata(LARGE) != null;
    }

    /** Caller holds the data monitor through preparation, receipt updates, save and rollback. */
    public static Update prepare(PlayerActivityData data, int id) {
        var meta = metadata(id);
        if (meta == null
                || data == null
                || data.getActivityId() != AsterSchedule.ACTIVITY_ID
                || data.getWatcherInfoMap() == null) return null;
        var previous = data.getWatcherInfoMap().get(id);
        if (previous != null
                && (previous.getWatcherId() != id
                        || previous.getTotalProgress() != meta.getProgress()
                        || previous.getCurProgress() < 0
                        || previous.getCurProgress() > meta.getProgress())) return null;
        return new Update(data, id, meta.getProgress(), previous);
    }

    public static final class Update {
        private final PlayerActivityData data;
        private final int id, total;
        private final PlayerActivityData.WatcherInfo previous;
        private PlayerActivityData.WatcherInfo installed;

        private Update(
                PlayerActivityData data,
                int id,
                int total,
                PlayerActivityData.WatcherInfo previous) {
            this.data = data;
            this.id = id;
            this.total = total;
            this.previous = previous;
        }

        public boolean apply(int confirmedCount) {
            if (confirmedCount < 0) throw new IllegalArgumentException("Invalid Aster claim count");
            int count = Math.min(total, confirmedCount);
            if (previous != null && previous.getCurProgress() == count) return false;
            installed =
                    PlayerActivityData.WatcherInfo.of()
                            .watcherId(id)
                            .totalProgress(total)
                            .curProgress(count)
                            .isTakenReward(previous != null && previous.isTakenReward())
                            .build();
            data.getWatcherInfoMap().put(id, installed);
            return true;
        }

        public void rollback() {
            if (installed == null) return;
            if (previous == null) data.getWatcherInfoMap().remove(id);
            else data.getWatcherInfoMap().put(id, previous);
        }
    }

    /** The activity loader saves any repaired view without changing currency or replay receipts. */
    public static boolean restore(PlayerActivityData data, AsterProgress progress) {
        synchronized (data) {
            var middle = prepare(data, MIDDLE);
            var large = prepare(data, LARGE);
            if (middle == null || large == null)
                throw new IllegalArgumentException("Invalid Aster special-reward conditions");
            return middle.apply(progress.midCollectCount())
                    | large.apply(progress.bigCollectCount());
        }
    }
}
