package emu.grasscutter.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.utils.JsonUtils;

import java.util.*;

/** Missing Excel objectives may be recovered from the original client chapter. */
public final class QuestBinFallback {
    private QuestBinFallback() {}

    public static void recover(int parentId, JsonObject[] children) {
        if (parentId <= 0 || children == null) return;
        var occurrences = new HashMap<Integer, Integer>();
        for (var row : children) {
            if (row != null) occurrences.merge(integer(row.get("subId")), 1, Integer::sum);
        }
        for (var row : children) {
            if (row == null) continue;
            int id = integer(row.get("subId"));
            if (GameData.getQuestDataMap().containsKey(id)) continue;
            if (id <= 0
                    || occurrences.getOrDefault(id, 0) != 1
                    || integer(row.get("mainId")) != parentId) continue;
            try {
                // Check raw names before Gson can turn an unknown enum into a null placeholder.
                if (!conditions(row, "finishCond", QuestContent.class, true)
                        || !conditions(row, "failCond", QuestContent.class, false)
                        || !conditions(row, "acceptCond", QuestCond.class, false)
                        || !conditions(row, "beginExec", QuestExec.class, false)
                        || !conditions(row, "finishExec", QuestExec.class, false)
                        || !conditions(row, "failExec", QuestExec.class, false)
                        || !logic(row, "acceptCondComb")
                        || !logic(row, "finishCondComb")
                        || !logic(row, "failCondComb")) {
                    Grasscutter.getLogger()
                            .warn(
                                    "Incomplete or unsupported BIN quest {} in chapter {}; keeping"
                                            + " it unavailable",
                                    id,
                                    parentId);
                    continue;
                }
                var definition = JsonUtils.decode(row, QuestData.class);
                definition.onLoad();
                GameData.getQuestDataMap().put(id, definition);
            } catch (RuntimeException error) {
                Grasscutter.getLogger()
                        .warn("Could not recover BIN quest {} in chapter {}", id, parentId, error);
            }
        }
    }

    private static boolean logic(JsonObject row, String name) {
        var value = row.get(name);
        if (value == null || value.isJsonNull()) return true;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        try {
            LogicType.valueOf(value.getAsString());
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static <T extends Enum<T>> boolean conditions(
            JsonObject row, String name, Class<T> type, boolean required) {
        var list = row.get(name);
        if (list == null || list.isJsonNull()) return !required;
        if (!list.isJsonArray()) return false;
        int namedConditions = 0;
        for (var value : list.getAsJsonArray()) {
            if (value.isJsonNull()) continue;
            if (!value.isJsonObject()) return false;
            var condition = value.getAsJsonObject();
            var rawType = alias(condition, "_type", "type");
            if (rawType == null
                    || rawType.isJsonNull()
                    || (rawType.isJsonPrimitive() && rawType.getAsString().isBlank())) {
                if (!placeholder(condition)) return false;
                continue;
            }
            if (!rawType.isJsonPrimitive() || !rawType.getAsJsonPrimitive().isString())
                return false;
            final T known;
            try {
                known = Enum.valueOf(type, rawType.getAsString());
            } catch (IllegalArgumentException error) {
                return false;
            }
            if (type != QuestExec.class) alias(condition, "_param_str", "param_str");
            var params = alias(condition, "_param", "param");
            boolean none = known.name().endsWith("_NONE");
            if (params == null || params.isJsonNull()) {
                if (!none) return false;
            } else {
                if (!params.isJsonArray() || (!none && params.getAsJsonArray().isEmpty()))
                    return false;
                for (var param : params.getAsJsonArray()) {
                    if (!param.isJsonPrimitive()) return false;
                    if (type != QuestExec.class && !isInteger(param)) return false;
                }
            }
            var count = alias(condition, "_count", "count");
            if (type != QuestExec.class
                    && count != null
                    && !count.isJsonNull()
                    && (!isInteger(count) || integer(count) < 0)) return false;
            // QuestCondition.asKey uses param[0], even for an explicit NONE condition.
            if (type == QuestCond.class
                    && none
                    && (params == null || params.isJsonNull() || params.getAsJsonArray().isEmpty()))
                return false;
            namedConditions++;
        }
        return !required || namedConditions > 0;
    }

    private static boolean placeholder(JsonObject row) {
        var params = alias(row, "_param", "param");
        if (params != null && !params.isJsonNull()) {
            if (!params.isJsonArray()) return false;
            for (var param : params.getAsJsonArray())
                if (!isInteger(param) || integer(param) != 0) return false;
        }
        var text = alias(row, "_param_str", "param_str");
        if (text != null
                && !text.isJsonNull()
                && (!text.isJsonPrimitive() || !text.getAsString().isBlank())) return false;
        var count = alias(row, "_count", "count");
        return count == null || count.isJsonNull() || (isInteger(count) && integer(count) == 0);
    }

    private static JsonElement alias(JsonObject row, String canonical, String alternate) {
        var primary = row.get(canonical);
        var legacy = row.get(alternate);
        if (primary != null && legacy != null && !primary.equals(legacy))
            throw new IllegalArgumentException(
                    "Conflicting quest fields " + canonical + " and " + alternate);
        return primary != null ? primary : legacy;
    }

    private static boolean isInteger(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return false;
        try {
            Integer.parseInt(value.getAsString());
            return true;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static int integer(JsonElement value) {
        return isInteger(value) ? Integer.parseInt(value.getAsString()) : 0;
    }
}
