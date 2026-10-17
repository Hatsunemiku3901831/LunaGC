package emu.grasscutter.game.activity;

import static emu.grasscutter.game.activity.ActivityCoinReceipt.*;

import com.mongodb.*;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;

import org.bson.*;

import java.util.*;

/** Durable currency intent, conditional result, and explicit reconciliation of uncertain writes. */
public final class ActivityCoinTransaction {
    private final PlayerActivityData data;
    private boolean completionUnconfirmed;

    ActivityCoinTransaction(PlayerActivityData data) {
        this.data = Objects.requireNonNull(data);
    }

    void commit(Map<Integer, Integer> amounts, boolean credit) {
        require(!data.hasUnconfirmedActivityCoins(), "活动货币仍有未确认操作。");
        data.getSaveGuard().resolvePending(DatabaseManager.getGameDatastore());
        var receipt =
                new ActivityCoinReceipt(
                        data, amounts, credit, data.claimWatcherCoin(amounts, credit));
        if (data.getLastCoinOperation() != null) data.getLastCoinOperation().owner(data);
        data.setPendingCoinOperation(receipt);
        completionUnconfirmed = false;
        try {
            data.saveCoinState(); // Persist intent before changing any balance.
            data.setActivityCoins(new TreeMap<>(receipt.getAfter()));
            data.setPendingCoinOperation(receipt.at(APPLIED));
            data.saveCoinState();
        } catch (RuntimeException uncertain) {
            data.blockUnconfirmedActivityCoins();
            Grasscutter.getLogger()
                    .error(
                            "Activity coin operation {} was not acknowledged: UID {}, activity {},"
                                    + " schedule {}",
                            receipt.getOperationId(),
                            data.getUid(),
                            data.getActivityId(),
                            data.getScheduleId(),
                            uncertain);
            throw uncertain;
        }
        // The balance write was acknowledged. A failed cleanup must not report a failed debit.
        data.setLastCoinOperation(receipt.at(APPLIED));
        data.setPendingCoinOperation(null);
        try {
            data.saveCoinState();
        } catch (RuntimeException cleanup) {
            completionUnconfirmed = true;
            Grasscutter.getLogger()
                    .warn(
                            "Activity coin operation {} balance is confirmed; completion record"
                                    + " needs reconciliation",
                            receipt.getOperationId(),
                            cleanup);
        }
    }

    public String pending() {
        synchronized (data) {
            var receipt = data.getPendingCoinOperation();
            if (receipt == null) {
                var last = data.getLastCoinOperation();
                if (completionUnconfirmed && last != null)
                    return "活动 "
                            + data.getActivityId()
                            + " 操作 "
                            + last.getOperationId()
                            + " 的余额已确认，完成记录写入仍需核对。";
                return "活动 "
                        + data.getActivityId()
                        + " 无待核对货币操作。"
                        + (last == null
                                ? ""
                                : " 最近操作 "
                                        + last.getOperationId()
                                        + "，阶段 "
                                        + last.getStage()
                                        + "。");
            }
            receipt.owner(data);
            return "活动 "
                    + data.getActivityId()
                    + "，排期 "
                    + data.getScheduleId()
                    + "，操作 "
                    + receipt.getOperationId()
                    + "，"
                    + (receipt.isCredit() ? "入账 " : "扣除 ")
                    + receipt.getAmounts()
                    + "，阶段 "
                    + receipt.getStage()
                    + "。";
        }
    }

    private ActivityCoinReceipt operation(String id) {
        var receipt = data.getPendingCoinOperation();
        if (receipt == null) receipt = data.getLastCoinOperation();
        require(
                receipt != null && Objects.equals(receipt.getOperationId(), id),
                "活动货币操作 ID 不符或记录已更替。");
        receipt.owner(data);
        var expected = receipt.getStage() == APPLIED ? receipt.getAfter() : receipt.getBefore();
        require(expected.equals(coins()), "当前活动余额与冻结记录不同。");
        if (data.getPendingCoinOperation() != null)
            require(
                    Objects.equals(
                            receipt.getPreviousOperationId(), id(data.getLastCoinOperation())),
                    "活动货币前序记录已变化。");
        return receipt;
    }

    private Map<Integer, Integer> coins() {
        return data.getActivityCoins() == null ? Map.of() : data.getActivityCoins();
    }

    private static String id(ActivityCoinReceipt receipt) {
        return receipt == null ? null : receipt.getOperationId();
    }

    private enum State {
        ABSENT,
        PREPARED,
        APPLIED,
        COMPLETED,
        CANCELLED
    }

    private record Stored(
            Map<Integer, Integer> coins, ActivityCoinReceipt pending, ActivityCoinReceipt last) {}

    private Stored read(boolean current) {
        var datastore = DatabaseManager.getGameDatastore();
        var filter =
                current
                        ? data.getSaveGuard().confirmedCondition(datastore)
                        : new Document("_id", data.getId())
                                .append("uid", data.getUid())
                                .append("activityId", data.getActivityId())
                                .append("scheduleId", data.getScheduleId());
        var row =
                datastore
                        .getMapper()
                        .getCollection(PlayerActivityData.class)
                        .withDocumentClass(BsonDocument.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(filter)
                        .projection(
                                new BsonDocument("activityCoins", new BsonInt32(1))
                                        .append("pendingCoinOperation", new BsonInt32(1))
                                        .append("lastCoinOperation", new BsonInt32(1)))
                        .first();
        require(row != null, "活动存档不存在或版本已变化，请重新核对。");
        var pending = ActivityCoinReceipt.from(row, "pendingCoinOperation");
        var last = ActivityCoinReceipt.from(row, "lastCoinOperation");
        if (pending != null) {
            pending.owner(data);
            require(pending.getStage() != CANCELLED, "待核对记录阶段不符。");
        }
        if (last != null) {
            last.owner(data);
            require(last.getStage() != PREPARED, "已完成记录阶段不符。");
        }
        return new Stored(ActivityCoinReceipt.amounts(row, "activityCoins"), pending, last);
    }

    private State checked(ActivityCoinReceipt receipt, boolean current) {
        var stored = read(current);
        if (stored.pending() != null) {
            require(
                    receipt.samePlan(stored.pending())
                            && Objects.equals(receipt.getPreviousOperationId(), id(stored.last())),
                    "存档有其他活动货币操作。");
            boolean applied = stored.pending().getStage() == APPLIED;
            require(
                    stored.coins().equals(applied ? receipt.getAfter() : receipt.getBefore()),
                    "存档活动余额与操作阶段不符。");
            return applied ? State.APPLIED : State.PREPARED;
        }
        if (Objects.equals(id(stored.last()), receipt.getOperationId())) {
            require(receipt.samePlan(stored.last()), "存档活动货币计划已变化。");
            boolean cancelled = stored.last().getStage() == CANCELLED;
            require(
                    stored.coins().equals(cancelled ? receipt.getBefore() : receipt.getAfter()),
                    "已完成活动货币余额与计划不符。");
            return cancelled ? State.CANCELLED : State.COMPLETED;
        }
        require(
                Objects.equals(receipt.getPreviousOperationId(), id(stored.last()))
                        && stored.coins().equals(receipt.getBefore()),
                "活动货币存档已变化，不能覆盖。");
        return State.ABSENT;
    }

    public String audit(String operationId) {
        synchronized (data) {
            var receipt = operation(operationId);
            var state = checked(receipt, false);
            return pending() + " 当前存档：" + state + "。核对不写入；resume 确认或继续原计划，cancel 仅取消尚未入账或扣款的计划。";
        }
    }

    private Player actor() {
        var player = data.getPlayer();
        require(player != null && player.getUid() == data.getUid(), "活动货币玩家归属不符。");
        return player;
    }

    public String resume(String operationId) {
        var player = actor();
        synchronized (player.getInventory()) {
            synchronized (data) {
                var receipt = operation(operationId);
                var state = checked(receipt, true); // Retire uncertain old writes before deciding.
                require(state != State.CANCELLED, "活动货币操作已经取消。");
                if (state != State.COMPLETED) {
                    try {
                        if (state == State.ABSENT) {
                            data.setActivityCoins(new TreeMap<>(receipt.getBefore()));
                            data.setPendingCoinOperation(receipt.at(PREPARED));
                            data.saveCoinState();
                        }
                        if (state == State.ABSENT || state == State.PREPARED) {
                            for (int coin : receipt.getAmounts().keySet())
                                require(
                                        Objects.equals(
                                                ActivityManager.configurationSnapshot()
                                                        .currencyActivities()
                                                        .get(coin),
                                                data.getActivityId()),
                                        "活动货币资源归属已变化。");
                            data.setActivityCoins(new TreeMap<>(receipt.getAfter()));
                            data.setPendingCoinOperation(receipt.at(APPLIED));
                            data.saveCoinState();
                        }
                        finish(receipt, APPLIED);
                    } catch (RuntimeException uncertain) {
                        data.blockUnconfirmedActivityCoins();
                        throw uncertain;
                    }
                } else installFinished(receipt, APPLIED);
                data.confirmActivityCoins();
                completionUnconfirmed = false;
                ActivityCurrencies.notifyRecovered(data);
                return "活动货币操作已确认：" + operationId + "。";
            }
        }
    }

    public String cancel(String operationId) {
        var player = actor();
        synchronized (player.getInventory()) {
            synchronized (data) {
                require(
                        data.getPendingWatcherReward() == null
                                || data.getPendingWatcherReward().getLines().stream()
                                        .noneMatch(
                                                line ->
                                                        Objects.equals(
                                                                line.getCoinOperationId(),
                                                                operationId)),
                        "该货币操作属于活动领奖，请使用 activityrewards 核对并继续原计划。");
                var receipt = operation(operationId);
                var state = checked(receipt, true);
                require(
                        state != State.APPLIED && state != State.COMPLETED,
                        "活动货币已经入账或扣除，不能取消，请 resume 确认。");
                if (state != State.CANCELLED) finish(receipt, CANCELLED);
                else installFinished(receipt, CANCELLED);
                data.confirmActivityCoins();
                completionUnconfirmed = false;
                ActivityCurrencies.notifyRecovered(data);
                return "活动货币操作已取消：" + operationId + "。";
            }
        }
    }

    private void installFinished(ActivityCoinReceipt receipt, int stage) {
        data.setActivityCoins(
                new TreeMap<>(stage == APPLIED ? receipt.getAfter() : receipt.getBefore()));
        data.setPendingCoinOperation(null);
        data.setLastCoinOperation(receipt.at(stage));
    }

    private void finish(ActivityCoinReceipt receipt, int stage) {
        var previousPending = data.getPendingCoinOperation();
        var previousLast = data.getLastCoinOperation();
        var previousCoins = data.getActivityCoins();
        installFinished(receipt, stage);
        try {
            data.saveCoinState();
        } catch (RuntimeException uncertain) {
            data.setPendingCoinOperation(previousPending);
            data.setLastCoinOperation(previousLast);
            data.setActivityCoins(previousCoins);
            data.blockUnconfirmedActivityCoins();
            throw uncertain;
        }
    }

    /**
     * Include disabled activities, and retain a live attempt whose intent has not reached Mongo.
     */
    public static List<String> list(Player player) {
        synchronized (player.getInventory()) {
            var rows = new TreeMap<Integer, PlayerActivityData>();
            for (var stored :
                    DatabaseManager.getGameDatastore()
                            .find(PlayerActivityData.class)
                            .filter(Filters.eq("uid", player.getUid()))
                            .iterator()
                            .toList())
                require(
                        rows.putIfAbsent(stored.getActivityId(), stored) == null,
                        "玩家存在重复活动存档，请先人工核对。");
            if (player.getActivityManager() != null)
                rows.putAll(player.getActivityManager().getPlayerActivityDataMap());
            return rows.values().stream()
                    .filter(
                            d ->
                                    d.getPendingCoinOperation() != null
                                            || d.getLastCoinOperation() != null
                                            || d.hasUnconfirmedActivityCoins())
                    .map(d -> d.getCoinTransaction().pending())
                    .toList();
        }
    }

    public static PlayerActivityData find(Player player, int activityId) {
        synchronized (player.getInventory()) {
            var rows =
                    DatabaseManager.getGameDatastore()
                            .find(PlayerActivityData.class)
                            .filter(
                                    Filters.eq("uid", player.getUid()),
                                    Filters.eq("activityId", activityId))
                            .iterator()
                            .toList();
            require(rows.size() == 1, "活动存档不存在或不唯一。");
            var live =
                    player.getActivityManager() == null
                            ? null
                            : player.getActivityManager()
                                    .getPlayerActivityDataMap()
                                    .get(activityId);
            var result = live == null ? rows.get(0) : live;
            require(
                    result.getUid() == player.getUid()
                            && Objects.equals(result.getId(), rows.get(0).getId()),
                    "内存活动与存档身份不同。");
            result.setPlayer(player);
            return result;
        }
    }
}
