package emu.grasscutter.game.activity;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import org.bson.*;

import java.util.*;

/** Frozen intent and exact balances for one activity's currency operation. */
@Entity(useDiscriminator = false)
@Getter
public final class ActivityCoinReceipt {
    public static final int PREPARED = 1, APPLIED = 2, CANCELLED = 3;
    private String operationId, previousOperationId, documentId;
    private int uid, activityId, scheduleId, stage;
    private long createdAt;
    private boolean credit;
    @Getter(lombok.AccessLevel.NONE)
    private Map<Integer, Integer> before, after, amounts;

    private ActivityCoinReceipt() {}

    ActivityCoinReceipt(PlayerActivityData data, Map<Integer, Integer> amounts, boolean credit) {
        operationId = UUID.randomUUID().toString();
        previousOperationId =
                data.getLastCoinOperation() == null
                        ? null
                        : data.getLastCoinOperation().operationId;
        documentId = data.getId();
        uid = data.getUid();
        activityId = data.getActivityId();
        scheduleId = data.getScheduleId();
        createdAt = System.currentTimeMillis();
        stage = PREPARED;
        this.credit = credit;
        before =
                data.getActivityCoins() == null
                        ? new TreeMap<>()
                        : new TreeMap<>(data.getActivityCoins());
        this.amounts = new TreeMap<>(amounts);
        after = new TreeMap<>(before);
        for (var entry : amounts.entrySet())
            after.put(
                    entry.getKey(),
                    Math.addExact(
                            before.getOrDefault(entry.getKey(), 0),
                            credit ? entry.getValue() : -entry.getValue()));
        validate();
    }

    public Map<Integer, Integer> getBefore() {
        return Map.copyOf(before);
    }

    public Map<Integer, Integer> getAfter() {
        return Map.copyOf(after);
    }

    public Map<Integer, Integer> getAmounts() {
        return Map.copyOf(amounts);
    }

    ActivityCoinReceipt at(int stage) {
        var copy = new ActivityCoinReceipt();
        copy.operationId = operationId;
        copy.previousOperationId = previousOperationId;
        copy.documentId = documentId;
        copy.uid = uid;
        copy.activityId = activityId;
        copy.scheduleId = scheduleId;
        copy.createdAt = createdAt;
        copy.credit = credit;
        copy.stage = stage;
        copy.before = new TreeMap<>(before);
        copy.after = new TreeMap<>(after);
        copy.amounts = new TreeMap<>(amounts);
        copy.validate();
        return copy;
    }

    void validate() {
        require(
                uuid(operationId)
                        && (previousOperationId == null
                                || uuid(previousOperationId)
                                        && !previousOperationId.equals(operationId))
                        && documentId != null
                        && !documentId.isBlank()
                        && documentId.length() <= 128
                        && uid > 0
                        && activityId > 0
                        && scheduleId > 0
                        && createdAt > 0
                        && (stage == PREPARED || stage == APPLIED || stage == CANCELLED),
                "活动货币记录身份或阶段无效。");
        for (var map : Arrays.asList(before, after, amounts))
            require(
                    map != null
                            && map.size() <= 10000
                            && map.entrySet().stream()
                                    .allMatch(
                                            e ->
                                                    e.getKey() != null
                                                            && e.getKey() > 0
                                                            && e.getValue() != null
                                                            && e.getValue() >= 0),
                    "活动货币快照无效。");
        require(!amounts.isEmpty() && amounts.values().stream().allMatch(n -> n > 0), "活动货币数量无效。");
        var expected = new TreeMap<>(before);
        for (var entry : amounts.entrySet()) {
            long n =
                    before.getOrDefault(entry.getKey(), 0)
                            + (credit ? (long) entry.getValue() : -(long) entry.getValue());
            require(n >= 0 && n <= Integer.MAX_VALUE, "活动货币收支越界。");
            expected.put(entry.getKey(), (int) n);
        }
        require(expected.equals(after) && !before.equals(after), "活动货币结果与计划不符。");
    }

    void owner(PlayerActivityData data) {
        validate();
        require(
                uid == data.getUid()
                        && activityId == data.getActivityId()
                        && scheduleId == data.getScheduleId()
                        && documentId.equals(data.getId()),
                "活动货币操作归属不符。");
    }

    boolean samePlan(ActivityCoinReceipt other) {
        return other != null
                && Objects.equals(operationId, other.operationId)
                && Objects.equals(previousOperationId, other.previousOperationId)
                && Objects.equals(documentId, other.documentId)
                && uid == other.uid
                && activityId == other.activityId
                && scheduleId == other.scheduleId
                && createdAt == other.createdAt
                && credit == other.credit
                && before.equals(other.before)
                && after.equals(other.after)
                && amounts.equals(other.amounts);
    }

    static ActivityCoinReceipt from(BsonDocument row, String field) {
        if (!row.containsKey(field) || row.isNull(field)) return null;
        var raw = row.getDocument(field);
        var r = new ActivityCoinReceipt();
        r.operationId = raw.getString("operationId").getValue();
        r.previousOperationId =
                !raw.containsKey("previousOperationId") || raw.isNull("previousOperationId")
                        ? null
                        : raw.getString("previousOperationId").getValue();
        r.documentId = raw.getString("documentId").getValue();
        r.uid = raw.getInt32("uid").getValue();
        r.activityId = raw.getInt32("activityId").getValue();
        r.scheduleId = raw.getInt32("scheduleId").getValue();
        r.stage = raw.getInt32("stage").getValue();
        r.createdAt = raw.getInt64("createdAt").getValue();
        // Morphia omits primitive false values under its normal mapping settings.
        r.credit = raw.containsKey("credit") && raw.getBoolean("credit").getValue();
        r.before = amounts(raw, "before");
        r.after = amounts(raw, "after");
        r.amounts = amounts(raw, "amounts");
        r.validate();
        return r;
    }

    static Map<Integer, Integer> amounts(BsonDocument raw, String field) {
        var result = new TreeMap<Integer, Integer>();
        if (!raw.containsKey(field)) return result;
        for (var e : raw.getDocument(field).entrySet()) {
            int id = Integer.parseInt(e.getKey());
            require(
                    id > 0
                            && Integer.toString(id).equals(e.getKey())
                            && e.getValue().isInt32()
                            && e.getValue().asInt32().getValue() >= 0,
                    "活动货币存档不是有效整数映射。");
            result.put(id, e.getValue().asInt32().getValue());
        }
        return result;
    }

    private static boolean uuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
