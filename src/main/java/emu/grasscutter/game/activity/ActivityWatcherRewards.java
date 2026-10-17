package emu.grasscutter.game.activity;

import static emu.grasscutter.game.activity.ActivityWatcherReceipt.*;

import com.mongodb.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.bson.*;

import java.util.*;

/** Source reservation, per-line delivery acknowledgement, and recovery only from positive proof. */
public final class ActivityWatcherRewards {
    private final PlayerActivityData data;
    private int confirmedCursor, unstarted = -1;
    private boolean processing;

    ActivityWatcherRewards(PlayerActivityData data) {
        this.data = data;
        confirmedCursor =
                data.getPendingWatcherReward() == null
                        ? 0
                        : data.getPendingWatcherReward().getCursor();
    }

    private boolean active() {
        var p = data.getPlayer();
        var c = ActivityManager.configurationSnapshot().activities().get(data.getActivityId());
        return p != null
                && p.getUid() == data.getUid()
                && p.getActivityManager() != null
                && p.getActivityManager().getPlayerActivityDataMap().get(data.getActivityId())
                        == data
                && c != null
                && c.getScheduleId() == data.getScheduleId()
                && c.isActiveAt(System.currentTimeMillis());
    }

    public int take(int watcherId) {
        var player = data.getPlayer();
        if (player == null) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        synchronized (player.getInventory()) {
            synchronized (data) {
                if (processing) return Retcode.RET_SVR_ERROR_VALUE;
                if (!active()) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
                var watcher =
                        data.getWatcherInfoMap() == null
                                ? null
                                : data.getWatcherInfoMap().get(watcherId);
                if (watcher == null) return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
                if (watcher.isTakenReward()) return Retcode.RET_ACTIVITY_WATCHER_REWARD_TAKEN_VALUE;
                processing = true;
                try {
                    if (data.getPendingWatcherReward() != null) {
                        if (data.getPendingWatcherReward().getWatcherId() != watcherId)
                            return Retcode.RET_SVR_ERROR_VALUE;
                        return run(data.getPendingWatcherReward().getOperationId());
                    }
                    var meta = GameData.getActivityWatcherDataMap().get(watcherId);
                    var activity = GameData.getActivityDataMap().get(data.getActivityId());
                    if (meta == null
                            || meta.isDisuse()
                            || activity == null
                            || activity.getWatcherDataList() == null
                            || activity.getWatcherDataList().stream()
                                    .noneMatch(w -> w.getId() == watcherId))
                        return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
                    if (!watcher.isFinished())
                        return Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE;
                    if (data.hasUnconfirmedActivityCoins()) return Retcode.RET_SVR_ERROR_VALUE;
                    var reward = GameData.getRewardDataMap().get(meta.getRewardID());
                    if (reward == null) return Retcode.RET_SVR_ERROR_VALUE;
                    data.getSaveGuard().resolvePending(DatabaseManager.getGameDatastore());
                    var rows = new ArrayList<>(reward.getRewardItemList());
                    rows.addAll(reward.getAdditionalRewards());
                    var receipt =
                            new ActivityWatcherReceipt(
                                    data,
                                    watcherId,
                                    reward.getId(),
                                    ActivityWatcherDelivery.prepare(data, rows));
                    confirmedCursor = 0;
                    unstarted = -1;
                    data.setPendingWatcherReward(receipt);
                    data.saveWatcherState();
                    return deliver(receipt);
                } catch (ActivityWatcherDelivery.Rejected invalid) {
                    return invalid.code;
                } catch (RuntimeException uncertain) {
                    log(uncertain);
                    return Retcode.RET_SVR_ERROR_VALUE;
                } finally {
                    processing = false;
                }
            }
        }
    }

    private ActivityWatcherReceipt operation(String id) {
        var r =
                data.getPendingWatcherReward() != null
                        ? data.getPendingWatcherReward()
                        : data.getLastWatcherReward();
        require(r != null && Objects.equals(id, r.getOperationId()), "活动领奖操作 ID 不符或已更替。");
        r.owner(data);
        return r;
    }

    private record Stored(
            ActivityWatcherReceipt pending, ActivityWatcherReceipt last, boolean taken) {}

    private Stored stored(ActivityWatcherReceipt receipt, boolean current) {
        var ds = DatabaseManager.getGameDatastore();
        var filter =
                current
                        ? data.getSaveGuard().confirmedCondition(ds)
                        : new Document("_id", data.getId())
                                .append("uid", data.getUid())
                                .append("activityId", data.getActivityId())
                                .append("scheduleId", data.getScheduleId());
        var raw =
                ds.getMapper()
                        .getCollection(PlayerActivityData.class)
                        .withDocumentClass(BsonDocument.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(filter)
                        .projection(
                                new BsonDocument("pendingWatcherReward", new BsonInt32(1))
                                        .append("lastWatcherReward", new BsonInt32(1))
                                        .append("watcherInfoMap", new BsonInt32(1)))
                        .first();
        require(raw != null, "活动领奖存档不存在或版本已变化。");
        var pending = ActivityWatcherReceipt.from(raw, "pendingWatcherReward");
        var last = ActivityWatcherReceipt.from(raw, "lastWatcherReward");
        if (pending != null) {
            pending.owner(data);
            require(pending.getStage() == READY || pending.getStage() == STARTED, "待处理活动领奖阶段无效。");
        }
        if (last != null) {
            last.owner(data);
            require(last.getStage() == COMPLETED || last.getStage() == CANCELLED, "完成活动领奖阶段无效。");
        }
        var watchers = raw.getDocument("watcherInfoMap", new BsonDocument());
        var watcher =
                watchers.getDocument(Integer.toString(receipt.getWatcherId()), new BsonDocument());
        return new Stored(
                pending, last, watcher.getBoolean("isTakenReward", BsonBoolean.FALSE).getValue());
    }

    private boolean align(ActivityWatcherReceipt receipt) {
        var saved = stored(receipt, true);
        var watcher = data.getWatcherInfoMap().get(receipt.getWatcherId());
        require(watcher != null, "活动任务已不存在。");
        if (saved.pending() == null
                && saved.last() != null
                && receipt.getOperationId().equals(saved.last().getOperationId())) {
            require(receipt.samePlan(saved.last()), "活动领奖结果计划已改变。");
            require(saved.taken() == (saved.last().getStage() == COMPLETED), "活动领奖结果标志不符。");
            data.setPendingWatcherReward(null);
            data.setLastWatcherReward(saved.last());
            watcher.setTakenReward(saved.taken());
            return true;
        }
        require(!saved.taken(), "活动奖励标志已改变。");
        if (saved.pending() == null) {
            require(
                    receipt.getCursor() == 0
                            && receipt.getStage() == READY
                            && confirmedCursor == 0
                            && Objects.equals(
                                    receipt.getPreviousOperationId(),
                                    saved.last() == null ? null : saved.last().getOperationId()),
                    "活动领奖存档已改变。");
            data.setPendingWatcherReward(receipt);
            data.saveWatcherState();
            return false;
        }
        require(receipt.samePlan(saved.pending()), "存档有另一份活动领奖计划。");
        data.setPendingWatcherReward(saved.pending());
        if (saved.pending().getStage() == READY)
            confirmedCursor = Math.max(confirmedCursor, saved.pending().getCursor());
        return false;
    }

    private int run(String id) {
        var r = operation(id);
        if (align(r))
            return data.getLastWatcherReward().getStage() == COMPLETED
                    ? 0
                    : Retcode.RET_SVR_ERROR_VALUE;
        return deliver(data.getPendingWatcherReward());
    }

    private int deliver(ActivityWatcherReceipt receipt) {
        if (receipt.getStage() == STARTED) {
            int i = receipt.getCursor();
            if (confirmedCursor == i + 1 || unstarted == i) {
                receipt = receipt.at(confirmedCursor > i ? confirmedCursor : i, READY);
                data.setPendingWatcherReward(receipt);
                data.saveWatcherState();
            } else {
                var line = receipt.getLines().get(i);
                data.beginWatcherGrant(line);
                boolean confirmed;
                try {
                    confirmed = ActivityWatcherDelivery.reconcileStarted(data, line);
                } finally {
                    data.endWatcherGrant();
                }
                require(confirmed, "该奖励发放结果仍不明，保留计划；不能自动重发已有堆叠或普通货币。");
                confirmedCursor = i + 1;
                receipt = receipt.at(i + 1, READY);
                data.setPendingWatcherReward(receipt);
                data.saveWatcherState();
            }
        }
        while (receipt.getCursor() < receipt.getLines().size()) {
            require(active(), "活动排期未开放，保留已确认阶段；可恢复原排期后继续。");
            int i = receipt.getCursor();
            var line = receipt.getLines().get(i);
            receipt = receipt.at(i, STARTED);
            unstarted = i;
            data.setPendingWatcherReward(receipt);
            data.saveWatcherState();
            data.beginWatcherGrant(line);
            try {
                ActivityWatcherDelivery.grant(data, line, () -> unstarted = -1);
            } finally {
                data.endWatcherGrant();
            }
            confirmedCursor = i + 1;
            receipt = receipt.at(i + 1, READY);
            data.setPendingWatcherReward(receipt);
            data.saveWatcherState();
        }
        finish(receipt, COMPLETED);
        return 0;
    }

    private void finish(ActivityWatcherReceipt receipt, int stage) {
        var watcher = data.getWatcherInfoMap().get(receipt.getWatcherId());
        require(watcher != null, "活动任务已不存在。");
        var previousPending = data.getPendingWatcherReward();
        var previousLast = data.getLastWatcherReward();
        boolean previousTaken = watcher.isTakenReward();
        watcher.setTakenReward(stage == COMPLETED);
        data.setLastWatcherReward(
                receipt.at(stage == COMPLETED ? receipt.getLines().size() : 0, stage));
        data.setPendingWatcherReward(null);
        try {
            data.saveWatcherState();
        } catch (RuntimeException uncertain) {
            watcher.setTakenReward(previousTaken);
            data.setPendingWatcherReward(previousPending);
            data.setLastWatcherReward(previousLast);
            throw uncertain;
        }
    }

    public String pending() {
        synchronized (data) {
            var r = data.getPendingWatcherReward();
            if (r == null) return "活动 " + data.getActivityId() + " 无待处理领奖。";
            return "活动 "
                    + r.getActivityId()
                    + " 任务 "
                    + r.getWatcherId()
                    + " 操作 "
                    + r.getOperationId()
                    + "，已确认 "
                    + r.getCursor()
                    + "/"
                    + r.getLines().size()
                    + "，阶段 "
                    + r.getStage()
                    + "。";
        }
    }

    public String audit(String id) {
        synchronized (data) {
            var r = operation(id);
            var saved = stored(r, false);
            return pending()
                    + " 存档已领取="
                    + saved.taken()
                    + "，待处理阶段="
                    + (saved.pending() == null ? "无" : saved.pending().getStage())
                    + "；核对不改余额或库存。";
        }
    }

    public String resume(String id) {
        var p = actor();
        synchronized (p.getInventory()) {
            synchronized (data) {
                require(!processing, "活动领奖正在处理。");
                processing = true;
                try {
                    require(run(id) == 0, "活动领奖尚未完成。");
                    return "活动领奖已确认：" + id + "。";
                } finally {
                    processing = false;
                }
            }
        }
    }

    public String cancel(String id) {
        var p = actor();
        synchronized (p.getInventory()) {
            synchronized (data) {
                require(!processing, "活动领奖正在处理。");
                processing = true;
                try {
                    var r = operation(id);
                    if (align(r)) {
                        require(data.getLastWatcherReward().getStage() == CANCELLED, "已领取奖励不能取消。");
                        return "活动领奖已取消：" + id + "。";
                    }
                    r = data.getPendingWatcherReward();
                    require(
                            r.getCursor() == 0 && r.getStage() == READY && confirmedCursor == 0,
                            "已有奖励开始发放，不能取消。");
                    finish(r, CANCELLED);
                    return "活动领奖已取消：" + id + "。";
                } finally {
                    processing = false;
                }
            }
        }
    }

    private Player actor() {
        var p = data.getPlayer();
        require(p != null && p.getUid() == data.getUid(), "活动领奖玩家归属不符。");
        return p;
    }

    public static List<String> list(Player player) {
        synchronized (player.getInventory()) {
            var rows = new TreeMap<Integer, PlayerActivityData>();
            for (var stored :
                    DatabaseManager.getGameDatastore()
                            .find(PlayerActivityData.class)
                            .filter(
                                    dev.morphia.query.experimental.filters.Filters.eq(
                                            "uid", player.getUid()))
                            .iterator()
                            .toList())
                require(
                        rows.putIfAbsent(stored.getActivityId(), stored) == null,
                        "玩家存在重复活动存档，请先人工核对。");
            if (player.getActivityManager() != null)
                rows.putAll(player.getActivityManager().getPlayerActivityDataMap());
            return rows.values().stream()
                    .filter(d -> d.getPendingWatcherReward() != null)
                    .map(d -> d.getWatcherRewards().pending())
                    .toList();
        }
    }

    private void log(RuntimeException error) {
        var r = data.getPendingWatcherReward();
        Grasscutter.getLogger()
                .error(
                        "Activity watcher reward needs reconciliation: UID {}, activity {},"
                                + " operation {}",
                        data.getUid(),
                        data.getActivityId(),
                        r == null ? "none" : r.getOperationId(),
                        error);
    }
}
