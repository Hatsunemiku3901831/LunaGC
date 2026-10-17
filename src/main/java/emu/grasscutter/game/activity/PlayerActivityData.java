package emu.grasscutter.game.activity;

import dev.morphia.Datastore;
import dev.morphia.annotations.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.ActivityWatcherData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.activity.salesman.SalesmanSchedule;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ActivityWatcherInfoOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketActivityUpdateWatcherNotify;
import emu.grasscutter.utils.JsonUtils;

import lombok.*;
import lombok.experimental.FieldDefaults;

import org.bson.Document;

import java.util.*;

@Entity("activities")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder(builderMethodName = "of")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class PlayerActivityData {
    @Id String id;
    int uid;
    int activityId;
    int scheduleId;
    @Builder.Default Map<Integer, WatcherInfo> watcherInfoMap = new HashMap<>();
    @Builder.Default Map<Integer, Integer> activityCoins = new HashMap<>();
    /** the detail data of each type of activity (Json format) */
    String detail;

    @Transient Player player;
    @Transient ActivityHandler activityHandler;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile boolean unconfirmedActivityCoins;

    @Setter(AccessLevel.NONE)
    ActivitySaveStamp saveStamp;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    ActivitySaveGuard saveGuard;

    @Setter(AccessLevel.NONE)
    ActivityCoinReceipt pendingCoinOperation;

    @Setter(AccessLevel.NONE)
    ActivityCoinReceipt lastCoinOperation;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    ActivityCoinTransaction coinTransaction;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Thread coinWriteThread;

    @Setter(AccessLevel.NONE)
    ActivityWatcherReceipt pendingWatcherReward;

    @Setter(AccessLevel.NONE)
    ActivityWatcherReceipt lastWatcherReward;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    ActivityWatcherRewards watcherRewards;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Thread watcherWriteThread;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Thread watcherGrantThread;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    ActivityWatcherReceipt.Line watcherGrantLine;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    boolean watcherCoinConsumed;

    @PostLoad
    private void onLoad(Document row, Datastore datastore) {
        saveGuard = new ActivitySaveGuard(this, row, datastore);
        var raw =
                row.toBsonDocument(
                        Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
        pendingCoinOperation = ActivityCoinReceipt.from(raw, "pendingCoinOperation");
        lastCoinOperation = ActivityCoinReceipt.from(raw, "lastCoinOperation");
        pendingWatcherReward = ActivityWatcherReceipt.from(raw, "pendingWatcherReward");
        lastWatcherReward = ActivityWatcherReceipt.from(raw, "lastWatcherReward");
        if (pendingWatcherReward != null) {
            pendingWatcherReward.owner(this);
            ActivityWatcherReceipt.require(
                    pendingWatcherReward.getStage() == ActivityWatcherReceipt.READY
                            || pendingWatcherReward.getStage() == ActivityWatcherReceipt.STARTED,
                    "活动领奖待处理阶段无效。");
        }
        if (lastWatcherReward != null) {
            lastWatcherReward.owner(this);
            ActivityWatcherReceipt.require(
                    lastWatcherReward.getStage() == ActivityWatcherReceipt.COMPLETED
                            || lastWatcherReward.getStage() == ActivityWatcherReceipt.CANCELLED,
                    "活动领奖完成阶段无效。");
        }
        if (pendingCoinOperation != null) {
            pendingCoinOperation.owner(this);
            ActivityCoinReceipt.require(
                    pendingCoinOperation.getStage() != ActivityCoinReceipt.CANCELLED,
                    "待处理货币记录已经取消。");
        }
        if (lastCoinOperation != null) {
            lastCoinOperation.owner(this);
            ActivityCoinReceipt.require(
                    lastCoinOperation.getStage() != ActivityCoinReceipt.PREPARED,
                    "已完成货币记录仍处于准备阶段。");
        }
    }

    public synchronized ActivitySaveGuard getSaveGuard() {
        if (saveGuard == null) saveGuard = new ActivitySaveGuard(this, null, null);
        return saveGuard;
    }

    void setSaveStamp(ActivitySaveStamp stamp) {
        saveStamp = stamp;
    }

    synchronized void inheritSaveIdentity(PlayerActivityData previous) {
        if (saveGuard != null)
            throw new IllegalStateException("Replay identity already initialized");
        saveGuard = previous.getSaveGuard().forReplay(this);
    }

    public boolean hasUnconfirmedActivityCoins() {
        return hasUnconfirmedCoinMutation()
                || pendingWatcherReward != null && watcherGrantThread != Thread.currentThread();
    }

    boolean hasUnconfirmedCoinMutation() {
        return unconfirmedActivityCoins || pendingCoinOperation != null || coinWriteThread != null;
    }

    void setPendingWatcherReward(ActivityWatcherReceipt r) {
        pendingWatcherReward = r;
    }

    void setLastWatcherReward(ActivityWatcherReceipt r) {
        lastWatcherReward = r;
    }

    public synchronized ActivityWatcherRewards getWatcherRewards() {
        if (watcherRewards == null) watcherRewards = new ActivityWatcherRewards(this);
        return watcherRewards;
    }

    boolean isWatcherWrite() {
        return watcherWriteThread == Thread.currentThread();
    }

    void saveWatcherState() {
        if (watcherWriteThread != null)
            throw new IllegalStateException("Reentrant watcher reward save");
        watcherWriteThread = Thread.currentThread();
        try {
            DatabaseHelper.saveGameSync(this);
        } finally {
            watcherWriteThread = null;
        }
    }

    void beginWatcherGrant(ActivityWatcherReceipt.Line line) {
        if (watcherGrantThread != null)
            throw new IllegalStateException("Reentrant watcher reward delivery");
        watcherGrantThread = Thread.currentThread();
        watcherGrantLine = line;
        watcherCoinConsumed = false;
    }

    void endWatcherGrant() {
        watcherGrantThread = null;
        watcherGrantLine = null;
        watcherCoinConsumed = false;
    }

    boolean allowsWatcherCredit(int id, long count) {
        return pendingWatcherReward == null
                || watcherGrantThread == Thread.currentThread()
                        && watcherGrantLine != null
                        && watcherGrantLine.getCoinOperationId() != null
                        && !watcherCoinConsumed
                        && watcherGrantLine.getItemId() == id
                        && watcherGrantLine.getCount() == count;
    }

    String claimWatcherCoin(Map<Integer, Integer> amounts, boolean credit) {
        if (pendingWatcherReward == null) return null;
        ActivityWatcherReceipt.require(
                credit
                        && watcherGrantThread == Thread.currentThread()
                        && watcherGrantLine != null
                        && !watcherCoinConsumed
                        && watcherGrantLine.getCoinOperationId() != null
                        && amounts.equals(
                                Map.of(watcherGrantLine.getItemId(), watcherGrantLine.getCount())),
                "活动领奖货币关联不符。");
        watcherCoinConsumed = true;
        return watcherGrantLine.getCoinOperationId();
    }

    void blockUnconfirmedActivityCoins() {
        unconfirmedActivityCoins = true;
    }

    void confirmActivityCoins() {
        unconfirmedActivityCoins = false;
    }

    void setPendingCoinOperation(ActivityCoinReceipt receipt) {
        pendingCoinOperation = receipt;
    }

    void setLastCoinOperation(ActivityCoinReceipt receipt) {
        lastCoinOperation = receipt;
    }

    boolean isCoinWrite() {
        return coinWriteThread == Thread.currentThread();
    }

    void saveCoinState() {
        if (coinWriteThread != null)
            throw new IllegalStateException("Reentrant activity coin save");
        coinWriteThread = Thread.currentThread();
        try {
            DatabaseHelper.saveGameSync(this);
        } finally {
            coinWriteThread = null;
        }
    }

    public synchronized ActivityCoinTransaction getCoinTransaction() {
        if (coinTransaction == null) coinTransaction = new ActivityCoinTransaction(this);
        return coinTransaction;
    }

    public PlayerActivityData(
            String id,
            int uid,
            int activityId,
            int scheduleId,
            Map<Integer, WatcherInfo> watcherInfoMap,
            String detail,
            Player player,
            ActivityHandler activityHandler) {
        this.id = id;
        this.uid = uid;
        this.activityId = activityId;
        this.scheduleId = scheduleId;
        this.watcherInfoMap = watcherInfoMap;
        this.activityCoins = new HashMap<>();
        this.detail = detail;
        this.player = player;
        this.activityHandler = activityHandler;
    }

    public static PlayerActivityData getByPlayer(Player player, int activityId) {
        return DatabaseHelper.getPlayerActivityData(player.getUid(), activityId);
    }

    public void save() {
        // Acknowledged event documents must not race queued writes when the replay reuses their ID.
        if (activityId == SalesmanSchedule.ACTIVITY_ID
                || activityId == emu.grasscutter.game.activity.aster.AsterSchedule.ACTIVITY_ID
                || ActivityCurrencies.belongsTo(activityId)) saveSync();
        else DatabaseHelper.savePlayerActivityData(this);
    }

    public void saveSync() {
        if (hasUnconfirmedActivityCoins())
            throw new IllegalStateException("Activity coin write requires reconciliation");
        DatabaseHelper.saveGameSync(this);
    }

    public synchronized void addWatcherProgress(int watcherId) {
        addWatcherProgress(watcherId, 1);
    }

    public synchronized void addWatcherProgress(int watcherId, int delta) {
        if (watcherInfoMap == null) return;
        var watcherInfo = watcherInfoMap.get(watcherId);
        if (watcherInfo == null) {
            return;
        }

        if (!watcherInfo.advance(delta)) {
            return;
        }
        save();
        getPlayer().sendPacket(new PacketActivityUpdateWatcherNotify(activityId, watcherInfo));
    }

    public List<ActivityWatcherInfoOuterClass.ActivityWatcherInfo> getAllWatcherInfoList() {
        return watcherInfoMap == null
                ? List.of()
                : watcherInfoMap.values().stream().map(WatcherInfo::toProto).toList();
    }

    public void setDetail(Object detail) {
        this.detail = JsonUtils.encode(detail);
    }

    /**
     * Restore an already serialized detail after an uncommitted change, without double encoding.
     */
    public void setDetailJson(String detail) {
        this.detail = detail;
    }

    public int takeWatcherReward(int watcherId) {
        if (player == null) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        synchronized (player.getInventory()) {
            if (activityId == emu.grasscutter.game.activity.aster.AsterSchedule.ACTIVITY_ID)
                return emu.grasscutter.game.activity.aster.AsterWatcherRewards.take(
                        this, watcherId);
            return getWatcherRewards().take(watcherId);
        }
    }

    @Entity
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Builder(builderMethodName = "of")
    public static class WatcherInfo {
        int watcherId;
        int totalProgress;
        int curProgress;
        boolean isTakenReward;

        /**
         * @return True when the progress of this watcher has reached the total progress.
         */
        public boolean isFinished() {
            return this.curProgress >= this.totalProgress;
        }

        public boolean advance(int delta) {
            if (delta <= 0 || isFinished()) return false;
            curProgress = (int) Math.min((long) curProgress + delta, totalProgress);
            return true;
        }

        public static WatcherInfo init(ActivityWatcher watcher) {
            var watcherData = watcher.getActivityWatcherData();
            var progress = watcherData != null ? watcherData.getProgress() : 0;

            return WatcherInfo.of()
                    .watcherId(watcher.getWatcherId())
                    .totalProgress(progress)
                    .isTakenReward(false)
                    .build();
        }

        public ActivityWatcherData getMetadata() {
            return GameData.getActivityWatcherDataMap().get(watcherId);
        }

        public ActivityWatcherInfoOuterClass.ActivityWatcherInfo toProto() {
            return ActivityWatcherInfoOuterClass.ActivityWatcherInfo.newBuilder()
                    .setWatcherId(watcherId)
                    .setCurProgress(curProgress)
                    .setTotalProgress(totalProgress)
                    .setIsTakenReward(isTakenReward)
                    .build();
        }
    }
}
