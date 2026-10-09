package emu.grasscutter.game.beyond;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;

/** Literal reference type rules and strict constant encoding for canvas exports. */
final class MiliastraCanvasTypes {
    private final Map<String, JsonObject> types = new HashMap<>();
    private final Map<Integer, JsonObject> serverEnums = new HashMap<>(),
            clientEnums = new HashMap<>();

    MiliastraCanvasTypes(JsonObject data) {
        for (var item : data.getAsJsonArray("TypesList")) {
            var row = item.getAsJsonObject();
            types.put(row.get("Expression").getAsString(), row);
        }
        for (var item : data.getAsJsonArray("EnumList")) {
            var row = item.getAsJsonObject();
            serverEnums.put(row.get("ID").getAsInt(), row);
        }
        for (var item : data.getAsJsonArray("ClientEnumList")) {
            var row = item.getAsJsonObject();
            clientEnums.put(row.get("ID").getAsInt(), row);
        }
    }

    static List<String> arguments(String type, char prefix) {
        if (!type.startsWith(prefix + "<") || !type.endsWith(">")) throw bad("类型表达式不合法：" + type);
        String body = type.substring(2, type.length() - 1);
        var result = new ArrayList<String>();
        int level = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '<') level++;
            if (c == '>' && --level < 0) throw bad("类型括号不匹配。");
            if (level > 32) throw bad("类型嵌套超限。");
            if (c == ',' && level == 0) {
                result.add(body.substring(start, i));
                start = i + 1;
            }
        }
        if (level != 0) throw bad("类型括号不匹配。");
        result.add(body.substring(start));
        return result;
    }

    static Map<String, String> bindings(String expression) {
        var result = new HashMap<String, String>();
        for (String field : arguments(expression, 'S')) {
            int colon = field.indexOf(':');
            if (colon < 1
                    || !field.substring(0, colon).matches("[A-Za-z][A-Za-z0-9_]*")
                    || result.putIfAbsent(field.substring(0, colon), field.substring(colon + 1)) != null)
                throw bad("泛型参数不合法。");
        }
        return Map.copyOf(result);
    }

    static String resolve(String expression, Map<String, String> bindings) {
        for (var field : bindings.entrySet())
            expression = expression.replace("R<" + field.getKey() + ">", field.getValue());
        if (expression.contains("R<")) throw bad("未解析的泛型：" + expression);
        return expression;
    }

    int code(String expression, boolean server) {
        if (expression.length() > 512 || expression.chars().anyMatch(Character::isWhitespace))
            throw bad("类型表达式不合法。");
        JsonObject row = types.get(expression);
        if (row != null && !expression.contains("?")) {
            JsonElement id = row.get(server ? "ID" : "ClientID");
            if (id == null || id.isJsonNull()) throw bad("此环境不支持类型：" + expression);
            return id.getAsInt();
        }
        if (expression.matches("E<[0-9]+>")) {
            int enumId = Integer.parseInt(expression.substring(2, expression.length() - 1));
            if (!(server ? serverEnums : clientEnums).containsKey(enumId))
                throw bad("参考库未收录枚举：" + expression);
            return server ? 14 : 13;
        }
        if (expression.startsWith("L<")) {
            var args = arguments(expression, 'L');
            if (args.size() != 1) throw bad("列表需要一个元素类型。");
            code(args.get(0), server);
            if (!server && args.get(0).startsWith("E<")) return 17;
        }
        if (expression.startsWith("D<")) {
            var args = arguments(expression, 'D');
            if (args.size() != 2 || !server) throw bad("字典需要服务端键和值类型。");
            code(args.get(0), true);
            code(args.get(1), true);
            return 27;
        }
        throw bad("没有可靠的类型编号：" + expression);
    }

    JsonObject itemType(String expression, boolean server) {
        var type = obj("classBase", server ? 1 : 2);
        var info = obj("type", code(expression, server));
        if (server && expression.startsWith("D<")) {
            var args = arguments(expression, 'D');
            info.addProperty("kind", 2);
            var items = obj("key", code(args.get(0), true));
            items.addProperty("value", code(args.get(1), true));
            info.add("items", items);
        }
        type.add(server ? "type_server" : "type_client", info);
        return type;
    }

    JsonObject value(String expression, JsonElement raw, boolean server) {
        int type = code(expression, server);
        JsonObject row = types.get(expression);
        int base =
                row == null
                        ? (expression.startsWith("D<") ? 10003 : expression.startsWith("L<") ? 10002 : 6)
                        : row.get("BaseTypeID").getAsInt();
        if (base == 0) throw bad("句柄类型不能作为输入常量：" + expression);
        if (raw == null || raw.isJsonNull()) throw bad("输入常量不能为 null。");
        var result = obj("class", base);
        result.addProperty("alreadySetVal", true);
        result.add("itemType", itemType(expression, server));
        JsonObject body;
        switch (base) {
            case 1, 2 -> {
                body = obj("val", integer(raw));
                result.add(base == 1 ? "bId" : "bInt", body);
            }
            case 4 -> {
                body = obj("val", coordinate(raw));
                result.add("bFloat", body);
            }
            case 5 -> {
                body = new JsonObject();
                body.addProperty("val", text(raw));
                result.add("bString", body);
            }
            case 6 -> {
                int number;
                if (expression.equals("Bol")) {
                    if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean())
                        throw bad("布尔常量需要 true 或 false。");
                    number = raw.getAsBoolean() ? 1 : 0;
                } else number = enumValue(expression, raw, server);
                result.add("bEnum", obj("val", number));
            }
            case 7 -> {
                var vector = new JsonObject();
                if (raw.isJsonArray()) {
                    var xyz = raw.getAsJsonArray();
                    if (xyz.size() != 3) throw bad("向量需要三个坐标。");
                    for (int i = 0; i < 3; i++)
                        vector.addProperty(List.of("x", "y", "z").get(i), coordinate(xyz.get(i)));
                } else if (raw.isJsonObject()
                        && raw.getAsJsonObject().keySet().equals(Set.of("x", "y", "z"))) {
                    for (String axis : List.of("x", "y", "z"))
                        vector.addProperty(axis, coordinate(raw.getAsJsonObject().get(axis)));
                } else throw bad("向量需要 [x,y,z] 或 {x,y,z}。");
                body = new JsonObject();
                body.add("val", vector);
                result.add("bVector", body);
            }
            case 10002 -> {
                if (!raw.isJsonArray()) throw bad("列表常量需要数组。");
                String elementType = arguments(expression, 'L').get(0);
                var entries = new JsonArray();
                for (var item : raw.getAsJsonArray()) entries.add(value(elementType, item, server));
                body = new JsonObject();
                body.add("entries", entries);
                result.add("bArray", body);
            }
            case 10003 -> {
                var args = arguments(expression, 'D');
                var entries = new JsonArray();
                var keys = new HashSet<String>();
                if (!raw.isJsonArray()) throw bad("字典常量需要 [[键,值],...]。");
                for (var item : raw.getAsJsonArray()) {
                    if (!item.isJsonArray() || item.getAsJsonArray().size() != 2) throw bad("字典条目需要键和值。");
                    var pair = item.getAsJsonArray();
                    var key = value(args.get(0), pair.get(0), true);
                    if (!keys.add(key.toString())) throw bad("字典键重复。");
                    var kv = new JsonObject();
                    kv.add("key", key);
                    kv.add("value", value(args.get(1), pair.get(1), true));
                    var wrapped = obj("class", 10007);
                    wrapped.addProperty("alreadySetVal", true);
                    wrapped.add("bMapPair", kv);
                    entries.add(wrapped);
                }
                body = new JsonObject();
                body.add("mapPairs", entries);
                result.add("bMap", body);
            }
            default -> throw bad("常量类型需要结构定义，不能直接编码：" + type);
        }
        return result;
    }

    boolean accepts(String expression, JsonElement value, boolean server) {
        try {
            value(expression, value, server);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private int enumValue(String expression, JsonElement raw, boolean server) {
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) return integer(raw);
        String name = text(raw);
        if (name.matches("-?[0-9]+")) {
            try {
                return Integer.parseInt(name);
            } catch (NumberFormatException e) {
                throw bad("枚举整数越界。");
            }
        }
        int id = Integer.parseInt(expression.substring(2, expression.length() - 1));
        var record = (server ? serverEnums : clientEnums).get(id);
        if (record == null) throw bad("此枚举没有文本名称映射。");
        var matches = new HashSet<Integer>();
        for (var element : record.getAsJsonArray("Items")) {
            var item = element.getAsJsonObject();
            if (name.equals(item.get("Name").getAsString())
                    || item.getAsJsonObject("Translations").entrySet().stream()
                            .anyMatch(e -> name.equals(e.getValue().getAsString())))
                matches.add(item.get("ID").getAsInt());
        }
        if (matches.size() != 1) throw bad("枚举文本未知或有歧义：" + name);
        return matches.iterator().next();
    }

    static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw bad("需要 int32 整数。");
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw bad("整数含小数或超出 int32。");
        }
    }

    static float coordinate(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw bad("需要有限数值。");
        float number = value.getAsFloat();
        if (!Float.isFinite(number)) throw bad("数值超出 float32。");
        return number;
    }

    static String text(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw bad("需要字符串。");
        return value.getAsString();
    }

    static JsonObject obj(String key, Number value) {
        var result = new JsonObject();
        result.addProperty(key, value);
        return result;
    }

    static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }
}
