package emu.grasscutter.game.beyond;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Structural graph validation. Screenshot-derived node definitions only supply preview warnings.
 */
public final class MiliastraGraph {
    private static final Set<String> ENVIRONMENTS =
            Set.of(
                    "server",
                    "server:status",
                    "server:class",
                    "server:item",
                    "client",
                    "client:role-skill",
                    "client:creation-skill",
                    "client:creation-state",
                    "client:creation-state-decision",
                    "client:boolean",
                    "client:integer");
    private static final Map<String, JsonObject> CATALOG = loadCatalog();

    public record Preview(
            String name, String environment, int nodes, int edges, List<String> warnings) {}

    private MiliastraGraph() {}

    public static Preview inspect(byte[] bytes) {
        if (bytes.length > 8 * 1024 * 1024) throw invalid("节点 JSON 最大为 8 MiB。");
        var root = object(readJson(bytes), "节点图");
        if (root.has("giaSource")) MiliastraCanvasReverse.source(root);
        int schema = integer(root.get("schemaVersion"), "schemaVersion");
        if (schema != 1 && schema != 2) throw invalid("仅支持节点 JSON schemaVersion 1 或 2。");
        String name = text(root.get("name"), "节点图名称");
        String environment =
                root.has("environment") ? text(root.get("environment"), "environment") : "server";
        if (!ENVIRONMENTS.contains(environment)) throw invalid("未知的节点图环境。");
        var nodes = array(root, "nodes");
        var edges = array(root, "edges");
        if (nodes.size() > 10000 || edges.size() > 40000) throw invalid("节点或连接数量超限。");
        var types = new HashMap<String, String>();
        var warnings = new LinkedHashSet<String>();
        warnings.add("节点目录来自截图推断，仅供预览；节点执行与当前客户端兼容性尚未验证。");
        for (var element : nodes) {
            var node = object(element, "节点");
            String id = text(node.get("id"), "节点 ID");
            String type = text(node.get("type"), "节点类型");
            if (types.putIfAbsent(id, type) != null) throw invalid("节点 ID 重复：" + id);
            position(node.get("position"));
            var definition = CATALOG.get(type);
            if (definition == null) warning(warnings, "未收录节点：" + type);
            else {
                if (definition.get("ambiguous").getAsBoolean()) warning(warnings, "目录存在歧义：" + type);
                if (!environment.equals("client")
                        && !contains(definition.getAsJsonArray("environments"), environment)
                        && !(environment.startsWith("server:")
                                && contains(definition.getAsJsonArray("environments"), "server"))) {
                    warning(warnings, "目录未标记此环境可用：" + type);
                }
            }
        }
        var edgeIds = new HashSet<String>();
        var connections = new HashSet<List<String>>();
        for (var element : edges) {
            var edge = object(element, "连接");
            String id = text(edge.get("id"), "连接 ID");
            if (!edgeIds.add(id)) throw invalid("连接 ID 重复：" + id);
            var source = object(edge.get("source"), "连接来源");
            var target = object(edge.get("target"), "连接目标");
            String from = text(source.get("nodeId"), "来源节点"), to = text(target.get("nodeId"), "目标节点");
            String out = text(source.get("portId"), "来源端口"), in = text(target.get("portId"), "目标端口");
            if (!types.containsKey(from) || !types.containsKey(to)) throw invalid("连接引用了不存在的节点：" + id);
            if (!connections.add(List.of(from, out, to, in))) throw invalid("重复的节点连接：" + id);
            var a = port(types.get(from), out);
            var b = port(types.get(to), in);
            if (a == null || b == null) warning(warnings, "目录未识别连接端口：" + id);
            else {
                String ak = a.get("kind").getAsString(), bk = b.get("kind").getAsString();
                if (!(ak.equals("flow-out") && bk.equals("flow-in"))
                        && !(ak.equals("data-out") && bk.equals("data-in"))) {
                    warning(warnings, "目录中的端口方向不匹配：" + id);
                } else if (ak.equals("data-out") && a.has("valueType") && b.has("valueType")) {
                    String av = a.get("valueType").getAsString(), bv = b.get("valueType").getAsString();
                    if (!av.equals(bv)
                            && !av.equals("any")
                            && !bv.equals("any")
                            && !(b.has("accepts") && contains(b.getAsJsonArray("accepts"), av))) {
                        warning(warnings, "目录中的端口数据类型不匹配：" + id);
                    }
                }
            }
        }
        for (var element : array(root, "comments")) {
            var comment = object(element, "注释");
            if (comment.has("nodeId")) {
                if (!types.containsKey(text(comment.get("nodeId"), "注释节点"))) throw invalid("注释引用不存在的节点。");
            } else if (!comment.has("position")) throw invalid("注释需要节点或位置。");
            if (comment.has("position")) position(comment.get("position"));
            if (!comment.has("text")
                    || !comment.get("text").isJsonPrimitive()
                    || !comment.get("text").getAsJsonPrimitive().isString()) throw invalid("注释缺少文本。");
        }
        if (root.has("executionIntervalSeconds")) number(root.get("executionIntervalSeconds"), "执行间隔");
        return new Preview(name, environment, nodes.size(), edges.size(), List.copyOf(warnings));
    }

    /** Strict UTF-8, bounded nesting/value count, no duplicate keys, non-finite or lenient JSON. */
    static JsonElement readJson(byte[] bytes) {
        return readJson(bytes, 200000);
    }

    static JsonElement readJson(byte[] bytes, int maxValues) {
        var decoder = StandardCharsets.UTF_8.newDecoder();
        try (var reader =
                new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), decoder))) {
            reader.setLenient(false);
            var result = read(reader, 0, new int[] {0}, maxValues);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("JSON 文档后有多余内容。");
            return result;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw invalid("JSON 格式、编码或数值不合法。");
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] count, int maxValues)
            throws IOException {
        if (depth > 64 || ++count[0] > maxValues) throw invalid("JSON 层级或元素数量超限。");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw invalid("JSON 属性重复：" + key);
                    object.add(key, read(reader, depth + 1, count, maxValues));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, count, maxValues));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String literal = reader.nextString();
                if (literal.length() > 256) throw invalid("JSON 数值过长。");
                var value = new BigDecimal(literal);
                if (!Double.isFinite(value.doubleValue())) throw invalid("JSON 数值超出有限数范围。");
                yield new JsonPrimitive(value);
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw invalid("JSON 格式不合法。");
        };
    }

    private static Map<String, JsonObject> loadCatalog() {
        try (var stream = MiliastraGraph.class.getResourceAsStream("/miliastra/node-catalog.json")) {
            if (stream == null) throw new IllegalStateException("Missing Miliastra node catalogue");
            var rows = object(readJson(stream.readAllBytes()), "目录").getAsJsonArray("nodes");
            var result = new HashMap<String, JsonObject>();
            for (var row : rows) {
                var node = row.getAsJsonObject();
                // Ambiguous entries remain preview-only and do not decide port compatibility.
                result.putIfAbsent(node.get("id").getAsString(), node);
            }
            return Map.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load node catalogue", e);
        }
    }

    private static JsonObject port(String type, String id) {
        var definition = CATALOG.get(type);
        if (definition == null || definition.get("ambiguous").getAsBoolean()) return null;
        for (var row : definition.getAsJsonArray("ports")) {
            if (row.getAsJsonObject().get("id").getAsString().equals(id)) return row.getAsJsonObject();
        }
        return null;
    }

    private static void position(JsonElement element) {
        var position = object(element, "节点位置");
        number(position.get("x"), "x");
        number(position.get("y"), "y");
    }

    private static void number(JsonElement element, String label) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(element.getAsDouble())) throw invalid(label + " 应为有限数值。");
    }

    private static int integer(JsonElement element, String label) {
        number(element, label);
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) {
            throw invalid(label + " 应为整数。");
        }
    }

    private static String text(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw invalid(label + " 应为字符串。");
        String text = element.getAsString();
        if (text.isBlank() || text.length() > 256) throw invalid(label + " 不能为空或超过 256 字符。");
        return text;
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) throw invalid(label + " 应为对象。");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        if (!object.get(key).isJsonArray()) throw invalid(key + " 应为数组。");
        return object.getAsJsonArray(key);
    }

    private static boolean contains(JsonArray array, String value) {
        for (var element : array) if (element.getAsString().equals(value)) return true;
        return false;
    }

    private static void warning(Set<String> warnings, String message) {
        if (warnings.size() < 100) warnings.add(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
