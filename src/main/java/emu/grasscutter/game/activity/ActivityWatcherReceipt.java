package emu.grasscutter.game.activity;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import org.bson.*;
import org.bson.types.ObjectId;

import java.util.*;

/** Frozen original quantities and equipment instances; cursor advances only after delivery ACK. */
@Entity(useDiscriminator = false)
@Getter
public final class ActivityWatcherReceipt {
    public static final int READY = 1, STARTED = 2, COMPLETED = 3, CANCELLED = 4;
    public static final int STACK = 1, EQUIPMENT = 2, VIRTUAL = 3, COSMETIC = 4;
    private String operationId, previousOperationId, documentId;
    private int uid, activityId, scheduleId, watcherId, rewardId, cursor, stage;
    private long createdAt;

    @Getter(lombok.AccessLevel.NONE)
    private List<Line> lines;

    private ActivityWatcherReceipt() {}

    ActivityWatcherReceipt(PlayerActivityData data, int watcher, int reward, List<Line> lines) {
        operationId = UUID.randomUUID().toString();
        previousOperationId =
                data.getLastWatcherReward() == null
                        ? null
                        : data.getLastWatcherReward().operationId;
        documentId = data.getId();
        uid = data.getUid();
        activityId = data.getActivityId();
        scheduleId = data.getScheduleId();
        watcherId = watcher;
        rewardId = reward;
        createdAt = System.currentTimeMillis();
        stage = READY;
        this.lines = List.copyOf(lines);
        validate();
    }

    public List<Line> getLines() {
        return List.copyOf(lines);
    }

    ActivityWatcherReceipt at(int cursor, int stage) {
        var copy = new ActivityWatcherReceipt();
        copy.operationId = operationId;
        copy.previousOperationId = previousOperationId;
        copy.documentId = documentId;
        copy.uid = uid;
        copy.activityId = activityId;
        copy.scheduleId = scheduleId;
        copy.watcherId = watcherId;
        copy.rewardId = rewardId;
        copy.createdAt = createdAt;
        copy.lines = lines;
        copy.cursor = cursor;
        copy.stage = stage;
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
                        && watcherId > 0
                        && rewardId > 0
                        && createdAt > 0,
                "活动领奖身份无效。");
        require(
                lines != null
                        && !lines.isEmpty()
                        && lines.size() <= 2000
                        && cursor >= 0
                        && cursor <= lines.size()
                        && (stage == READY
                                || stage == STARTED && cursor < lines.size()
                                || stage == COMPLETED && cursor == lines.size()
                                || stage == CANCELLED && cursor == 0),
                "活动领奖阶段无效。");
        long bytes = 0;
        var ids = new HashSet<String>();
        for (var line : lines) {
            line.validate();
            bytes += line.template.length();
            if (line.objectId != null) require(ids.add(line.objectId), "活动奖励持久 ID 重复。");
        }
        require(bytes <= 4 * 1024 * 1024, "活动奖励计划过大。");
    }

    void owner(PlayerActivityData data) {
        validate();
        require(
                uid == data.getUid()
                        && activityId == data.getActivityId()
                        && scheduleId == data.getScheduleId()
                        && documentId.equals(data.getId()),
                "活动领奖归属不符。");
    }

    boolean samePlan(ActivityWatcherReceipt other) {
        return other != null
                && Objects.equals(operationId, other.operationId)
                && Objects.equals(previousOperationId, other.previousOperationId)
                && Objects.equals(documentId, other.documentId)
                && uid == other.uid
                && activityId == other.activityId
                && scheduleId == other.scheduleId
                && watcherId == other.watcherId
                && rewardId == other.rewardId
                && createdAt == other.createdAt
                && lines.size() == other.lines.size()
                && java.util.stream.IntStream.range(0, lines.size())
                        .allMatch(i -> lines.get(i).same(other.lines.get(i)));
    }

    @Entity(useDiscriminator = false)
    @Getter
    public static final class Line {
        private int itemId, count, kind, before, after;
        private String objectId, template, definition, coinOperationId;

        private Line() {}

        Line(
                int item,
                int count,
                int kind,
                int before,
                int after,
                String objectId,
                String template,
                String definition,
                String coinOperationId) {
            itemId = item;
            this.count = count;
            this.kind = kind;
            this.before = before;
            this.after = after;
            this.objectId = objectId;
            this.template = template;
            this.definition = definition;
            this.coinOperationId = coinOperationId;
            validate();
        }

        void validate() {
            require(
                    itemId > 0
                            && count > 0
                            && before >= 0
                            && after >= 0
                            && template != null
                            && template.length() <= 32768
                            && definition != null
                            && definition.matches("[0-9a-f]{64}"),
                    "活动奖励行无效。");
            require(
                    kind >= STACK
                            && kind <= COSMETIC
                            && ((kind == STACK || kind == VIRTUAL) && (long) before + count == after
                                    || kind == EQUIPMENT && count == 1 && before == 0 && after == 1
                                    || kind == COSMETIC && count == 1 && before <= 1 && after == 1),
                    "活动奖励数量与计划不符。");
            var doc = BsonDocument.parse(template);
            require(
                    doc.getInt32("itemId").getValue() == itemId
                            && doc.getInt32("count").getValue() == count
                            && (!doc.containsKey("ownerId")
                                    || doc.getInt32("ownerId").getValue() == 0),
                    "活动奖励模板身份不符。");
            if (kind == STACK || kind == EQUIPMENT)
                require(
                        objectId != null
                                && ObjectId.isValid(objectId)
                                && doc.getObjectId("_id").getValue().toHexString().equals(objectId),
                        "活动奖励模板持久 ID 不符。");
            else require(objectId == null && !doc.containsKey("_id"), "虚拟奖励不能引用库存持久 ID。");
            require(
                    coinOperationId == null || kind == VIRTUAL && uuid(coinOperationId),
                    "活动货币关联操作无效。");
        }

        boolean same(Line other) {
            return other != null
                    && itemId == other.itemId
                    && count == other.count
                    && kind == other.kind
                    && before == other.before
                    && after == other.after
                    && Objects.equals(objectId, other.objectId)
                    && Objects.equals(template, other.template)
                    && Objects.equals(definition, other.definition)
                    && Objects.equals(coinOperationId, other.coinOperationId);
        }
    }

    static ActivityWatcherReceipt from(BsonDocument row, String field) {
        if (!row.containsKey(field) || row.isNull(field)) return null;
        var raw = row.getDocument(field);
        var r = new ActivityWatcherReceipt();
        r.operationId = raw.getString("operationId").getValue();
        r.previousOperationId = string(raw, "previousOperationId");
        r.documentId = raw.getString("documentId").getValue();
        r.uid = raw.getInt32("uid").getValue();
        r.activityId = raw.getInt32("activityId").getValue();
        r.scheduleId = raw.getInt32("scheduleId").getValue();
        r.watcherId = raw.getInt32("watcherId").getValue();
        r.rewardId = raw.getInt32("rewardId").getValue();
        r.createdAt = raw.getInt64("createdAt").getValue();
        r.cursor = integer(raw, "cursor");
        r.stage = raw.getInt32("stage").getValue();
        r.lines = new ArrayList<>();
        for (var value : raw.getArray("lines")) {
            var line = value.asDocument();
            r.lines.add(
                    new Line(
                            line.getInt32("itemId").getValue(),
                            line.getInt32("count").getValue(),
                            line.getInt32("kind").getValue(),
                            integer(line, "before"),
                            line.getInt32("after").getValue(),
                            string(line, "objectId"),
                            line.getString("template").getValue(),
                            line.getString("definition").getValue(),
                            string(line, "coinOperationId")));
        }
        r.validate();
        return r;
    }

    private static int integer(BsonDocument row, String key) {
        return row.containsKey(key) ? row.getInt32(key).getValue() : 0;
    }

    private static String string(BsonDocument row, String key) {
        return !row.containsKey(key) || row.isNull(key) ? null : row.getString(key).getValue();
    }

    private static boolean uuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
