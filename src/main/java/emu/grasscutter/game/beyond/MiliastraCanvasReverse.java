package emu.grasscutter.game.beyond;

import static emu.grasscutter.game.beyond.MiliastraCanvasTypes.*;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reference-mapped canvas projection with an original GIA backing for faithful editing. */
public final class MiliastraCanvasReverse {
    private static final String FORMAT = "lunagc.canvas-source.v1";
    private static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Node(
            int index, JsonObject nativeNode, JsonObject canvas, MiliastraCanvas.Node layout) {}

    private MiliastraCanvasReverse() {}

    public static byte[] decode(byte[] gia) {
        var result = project(gia);
        // A reverse projection must also be accepted by the forward compiler.
        MiliastraCanvas.encode(bytes(result), guid(typed(gia)));
        var backing = new JsonObject();
        backing.addProperty("format", FORMAT);
        backing.addProperty("data", Base64.getEncoder().encodeToString(gia));
        result.add("giaSource", backing);
        byte[] bytes = bytes(result);
        MiliastraGraph.inspect(bytes);
        return bytes;
    }

    static byte[] source(JsonObject canvas) {
        try {
            var backing = canvas.getAsJsonObject("giaSource");
            if (!backing.keySet().equals(Set.of("format", "data"))
                    || !FORMAT.equals(text(backing.get("format")))) throw bad("画布来源格式不匹配。");
            byte[] gia = Base64.getDecoder().decode(text(backing.get("data")));
            if (gia.length > MiliastraFile.MAX_BYTES
                    || gia.length < 24
                    || ByteBuffer.wrap(gia).getInt(4) != 1) throw bad("画布来源须为版本 1 GIA，最大 32 MiB。");
            typed(gia);
            return gia;
        } catch (ClassCastException | IllegalStateException | NullPointerException e) {
            throw bad("画布来源结构不合法。");
        }
    }

    static int sourceGuid(byte[] canvas) {
        var root = MiliastraGraph.readJson(canvas).getAsJsonObject();
        return guid(typed(source(root)));
    }

    static byte[] restore(byte[] canvas, int graphId) {
        MiliastraGraph.inspect(canvas);
        var document = MiliastraGraph.readJson(canvas).getAsJsonObject();
        byte[] original = source(document);
        var nativeDocument = typed(original);
        if (graphId != guid(nativeDocument)) throw bad("来源画布还原须沿用原图 GUID；生成副本后可使用复制并重新编号。");
        var baseline = MiliastraGraph.readJson(bytes(project(original))).getAsJsonObject();
        document.remove("giaSource");
        if (document.equals(baseline)) return original;
        if (!text(document.get("environment")).equals(text(baseline.get("environment"))))
            throw bad("来源画布不能直接更改图环境，请另建画布。");
        byte[] compiled = MiliastraCanvas.encode(bytes(document), graphId);
        var compiledDocument = typed(compiled);
        merge(nativeDocument, compiledDocument, document, baseline);
        return MiliastraNodeJson.encode(bytes(nativeDocument));
    }

    private static JsonObject project(byte[] gia) {
        if (gia.length < 24 || ByteBuffer.wrap(gia).getInt(4) != 1) throw bad("反向画布仅支持版本 1 GIA。");
        var typed = typed(gia);
        guid(typed);
        var unit = unit(typed);
        var graph = graph(typed);
        int which = integer(unit.get("which"));
        String environment =
                switch (which) {
                    case 9 -> "server";
                    case 22 -> "server:status";
                    case 23 -> "server:class";
                    case 46 -> "server:item";
                    case 10 -> "client:boolean";
                    case 47 -> "client:integer";
                    case 11 -> skillEnvironment(graph);
                    default -> throw bad("复合图、结构定义或未知类型尚无可靠画布映射。");
                };
        boolean server = environment.startsWith("server");
        var nodes = new LinkedHashMap<Integer, Node>();
        var comments = new JsonArray();
        for (var element : array(graph, "nodes")) {
            var nativeNode = element.getAsJsonObject();
            int index = number(nativeNode, "nodeIndex");
            if (index < 0 || nodes.containsKey(index)) throw bad("节点索引无效或重复：" + index);
            var node = match(nativeNode, server, environment);
            nodes.put(index, node);
            if (nativeNode.has("comments"))
                comments.add(
                        comment(
                                nativeNode.getAsJsonObject("comments"),
                                "gia-node-comment-" + index,
                                nodeId(index)));
        }
        int commentIndex = 0;
        for (var element : array(graph, "comments"))
            comments.add(comment(element.getAsJsonObject(), "gia-comment-" + commentIndex++, null));
        var edges = new JsonArray();
        var knownEdges = new HashSet<String>();
        for (var node : nodes.values()) {
            var local = new HashSet<String>();
            for (var element : array(node.nativeNode(), "pins")) {
                var pin = element.getAsJsonObject();
                int kind = kind(pin), ordinal = ordinal(pin);
                var owner = port(node.layout(), kind, ordinal);
                for (var connectionElement : array(pin, "connects")) {
                    var connection = connectionElement.getAsJsonObject();
                    var other = nodes.get(number(connection, "id"));
                    if (other == null) throw bad("连接引用不存在的节点：" + number(connection, "id"));
                    var target = object(connection, "connect");
                    if (connection.has("connect2") && !sameIndex(target, object(connection, "connect2")))
                        throw bad("连接的双重索引不一致。");
                    int otherKind = number(target, "kind"), otherOrdinal = number(target, "index");
                    int expected =
                            switch (kind) {
                                case 1 -> 2;
                                case 2 -> 1;
                                case 3 -> 4;
                                case 4 -> 3;
                                default -> 0;
                            };
                    if (otherKind != expected) throw bad("连接引脚方向不匹配。");
                    var peer = port(other.layout(), otherKind, otherOrdinal);
                    boolean outgoing = kind == 2 || kind == 4;
                    String from = outgoing ? nodeId(node.index()) : nodeId(other.index());
                    String to = outgoing ? nodeId(other.index()) : nodeId(node.index());
                    String out = outgoing ? owner.id : peer.id, in = outgoing ? peer.id : owner.id;
                    String key = from + "/" + out + ">" + to + "/" + in;
                    if (!local.add(kind + ":" + ordinal + ":" + key)) throw bad("同一引脚中有重复连接，不能无损投影。");
                    if (knownEdges.add(key)) {
                        var edge = new JsonObject();
                        edge.addProperty("id", "gia-edge-" + edges.size());
                        edge.add("source", endpoint(from, out));
                        edge.add("target", endpoint(to, in));
                        edges.add(edge);
                    }
                }
            }
        }
        var result = obj("schemaVersion", 2);
        String name = string(unit, "name");
        if (name.isBlank()) name = string(graph, "name");
        result.addProperty("name", name.isBlank() ? "未命名节点图" : name);
        result.addProperty("environment", environment);
        var canvasNodes = new JsonArray();
        for (var node : nodes.values()) canvasNodes.add(node.canvas());
        result.add("nodes", canvasNodes);
        result.add("edges", edges);
        result.add("comments", comments);
        MiliastraGraph.inspect(bytes(result));
        return result;
    }

    private static String skillEnvironment(JsonObject graph) {
        for (String environment :
                List.of(
                        "client:role-skill",
                        "client:creation-skill",
                        "client:creation-state",
                        "client:creation-state-decision")) {
            boolean usable = true;
            for (var element : array(graph, "nodes")) {
                try {
                    match(element.getAsJsonObject(), false, environment);
                } catch (IllegalArgumentException e) {
                    usable = false;
                    break;
                }
            }
            if (usable) return environment;
        }
        throw bad("技能图中没有共同可用的编辑器环境。");
    }

    private static Node match(JsonObject nativeNode, boolean server, String environment) {
        var generic = object(nativeNode, "genericId");
        if (number(generic, "class") != 0 && number(generic, "class") != 10001)
            throw bad("自定义或复合节点尚无编辑器模板映射。");
        int index = number(nativeNode, "nodeIndex"), id = number(generic, "nodeId");
        var matches = new ArrayList<Node>();
        for (String type : MiliastraCanvas.templates(id, server)) {
            var raw = new JsonObject();
            raw.addProperty("id", nodeId(index));
            raw.addProperty("type", type);
            var position = new JsonObject();
            position.addProperty("x", real(nativeNode, "x"));
            position.addProperty("y", real(nativeNode, "y"));
            raw.add("position", position);
            var data = new JsonObject();
            raw.add("data", data);
            try {
                if (type.equals("flow.branch.multi")) {
                    for (var element : array(nativeNode, "pins")) {
                        var pin = element.getAsJsonObject();
                        if (kind(pin) == 3 && ordinal(pin) == 1 && pin.has("value")) {
                            String expression = number(pin, "type") == 8 ? "L<Int>" : "L<Str>";
                            JsonElement values = value(expression, object(pin, "value"), server, 0);
                            var labels = new JsonArray();
                            if (values != null) for (var v : values.getAsJsonArray()) labels.add(v.getAsString());
                            data.add("branchFlowOutLabels", labels);
                        }
                    }
                }
                var layout = new MiliastraCanvas.Node(raw, index, server, environment);
                var pins = new LinkedHashMap<String, JsonObject>();
                for (var element : array(nativeNode, "pins")) {
                    var pin = element.getAsJsonObject();
                    int k = kind(pin), p = ordinal(pin);
                    if (k < 1 || k > 4 || p < 0 || pins.putIfAbsent(k + ":" + p, pin) != null)
                        throw bad("引脚索引无效、重复或暂不支持。");
                    if (pin.has("i2") && !sameIndex(object(pin, "i1"), object(pin, "i2")))
                        throw bad("引脚双重索引不一致。");
                    port(layout, k, p);
                }
                layout.options.removeIf(option -> !matches(option, layout, pins, nativeNode, server));
                if (layout.options.isEmpty()) continue;
                var overrides = new JsonObject();
                var types = new JsonObject();
                for (var p : layout.ports.values()) {
                    if (p.index < 0 || p.kind < 3 || p.id.startsWith("$")) continue;
                    String expression = common(layout.options, p);
                    var pin = pins.get(p.kind + ":" + p.index);
                    if (expression == null) {
                        if (pin != null) throw bad("原图的具体类型仍有歧义。");
                        continue;
                    }
                    try {
                        MiliastraCanvas.types().code(expression, server);
                    } catch (IllegalArgumentException unsupported) {
                        if (pin != null) throw unsupported;
                        continue;
                    }
                    types.addProperty(p.id, expression);
                    if (pin != null && pin.has("value")) {
                        var constant = value(expression, object(pin, "value"), server, 0);
                        if (constant != null) {
                            if (p.kind != 3) throw bad("输出引脚的值不是可编辑的输入常量。");
                            overrides.add(p.id, constant);
                        }
                    }
                }
                if (overrides.size() != 0) data.add("overrides", overrides);
                if (types.size() != 0) data.add("giaTypes", types);
                matches.add(new Node(index, nativeNode, raw, layout));
            } catch (IllegalArgumentException unsupported) {
                /* Other templates may match; never publish a partial node. */
            }
        }
        if (matches.size() != 1) throw bad("节点 " + index + "（模板 " + id + "）没有唯一、完整的引脚／常量映射。");
        return matches.get(0);
    }

    private static boolean matches(
            MiliastraCanvas.Option option,
            MiliastraCanvas.Node node,
            Map<String, JsonObject> pins,
            JsonObject nativeNode,
            boolean server) {
        try {
            if (nativeNode.has("concreteId")
                    && !Objects.equals(option.concrete(), number(object(nativeNode, "concreteId"), "nodeId")))
                return false;
            for (var p : node.ports.values()) {
                var pin = pins.get(p.kind + ":" + p.index);
                if (p.kind < 3 || p.index < 0 || pin == null) continue;
                String expression = option.type(p);
                if (number(pin, "type") != 0
                        && MiliastraCanvas.types().code(expression, server) != number(pin, "type"))
                    return false;
                if (pin.has("value")) {
                    var raw = object(pin, "value");
                    if (raw.has("bConcreteValue")
                            && option.reflect(p) != null
                            && number(object(raw, "bConcreteValue"), "indexOfConcrete") != option.reflect(p))
                        return false;
                    value(expression, raw, server, 0);
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String common(List<MiliastraCanvas.Option> options, MiliastraCanvas.Port port) {
        String type = options.get(0).type(port);
        return options.stream().allMatch(o -> o.type(port).equals(type)) ? type : null;
    }

    private static JsonElement value(
            String expression, JsonObject encoded, boolean server, int depth) {
        if (depth > 32) throw bad("常量嵌套超限。");
        int base = number(encoded, "class");
        if (base == 0) return null;
        if (base == 10000)
            return value(
                    expression, object(object(encoded, "bConcreteValue"), "value"), server, depth + 1);
        JsonElement result;
        switch (base) {
            case 1, 2 -> result =
                    new JsonPrimitive(number(object(encoded, base == 1 ? "bId" : "bInt"), "val"));
            case 4 -> result = new JsonPrimitive(real(object(encoded, "bFloat"), "val"));
            case 5 -> result = new JsonPrimitive(string(object(encoded, "bString"), "val"));
            case 6 -> {
                int val = number(object(encoded, "bEnum"), "val");
                if (expression.equals("Bol")) {
                    if (val != 0 && val != 1) throw bad("布尔常量不是 0 或 1。");
                    result = new JsonPrimitive(val == 1);
                } else result = new JsonPrimitive(val);
            }
            case 7 -> {
                var vector = object(object(encoded, "bVector"), "val");
                var xyz = new JsonArray();
                for (String axis : List.of("x", "y", "z")) xyz.add(real(vector, axis));
                result = xyz;
            }
            case 10002 -> {
                String element = arguments(expression, 'L').get(0);
                var values = new JsonArray();
                for (var item : array(object(encoded, "bArray"), "entries")) {
                    var decoded = value(element, item.getAsJsonObject(), server, depth + 1);
                    if (decoded == null) throw bad("列表存在未设定的元素。");
                    values.add(decoded);
                }
                result = values;
            }
            case 10003 -> {
                var types = arguments(expression, 'D');
                var pairs = new JsonArray();
                for (var item : array(object(encoded, "bMap"), "mapPairs")) {
                    var pair = object(item.getAsJsonObject(), "bMapPair");
                    var row = new JsonArray();
                    for (int i = 0; i < 2; i++) {
                        var decoded =
                                value(types.get(i), object(pair, i == 0 ? "key" : "value"), server, depth + 1);
                        if (decoded == null) throw bad("字典存在未设定的键或值。");
                        row.add(decoded);
                    }
                    pairs.add(row);
                }
                result = pairs;
            }
            default -> throw bad("常量类型没有可靠的画布表示：" + base);
        }
        var canonical = MiliastraCanvas.types().value(expression, result, server);
        if (number(canonical, "class") != base) throw bad("常量载体与声明类型不一致。");
        return result;
    }

    private static void merge(
            JsonObject original, JsonObject compiled, JsonObject canvas, JsonObject baseline) {
        var oldGraph = graph(original);
        var newGraph = graph(compiled);
        var oldNodes = new HashMap<String, JsonObject>();
        var oldViews = new HashMap<String, JsonObject>();
        var reserved = new HashSet<Integer>();
        for (var e : array(oldGraph, "nodes")) {
            var n = e.getAsJsonObject();
            int id = number(n, "nodeIndex");
            oldNodes.put(nodeId(id), n);
            reserved.add(id);
        }
        for (var e : baseline.getAsJsonArray("nodes")) {
            var n = e.getAsJsonObject();
            oldViews.put(text(n.get("id")), n);
        }
        var actual = new LinkedHashMap<String, Integer>();
        int next = 1;
        for (var e : canvas.getAsJsonArray("nodes")) {
            String id = text(e.getAsJsonObject().get("id"));
            if (oldNodes.containsKey(id)) actual.put(id, number(oldNodes.get(id), "nodeIndex"));
            else {
                while (reserved.contains(next)) {
                    if (next == Integer.MAX_VALUE) throw bad("节点索引已耗尽。");
                    next++;
                }
                actual.put(id, next);
                reserved.add(next);
            }
        }
        for (var e : array(oldGraph, "compositePins"))
            if (!actual.containsValue(number(e.getAsJsonObject(), "innerNodeId")))
                throw bad("删除的节点仍被复合引脚引用，请使用节点结构 JSON 编辑。");
        var layouts = new HashMap<String, MiliastraCanvas.Node>();
        boolean server = text(canvas.get("environment")).startsWith("server");
        for (var e : canvas.getAsJsonArray("nodes")) {
            var n = e.getAsJsonObject();
            String id = text(n.get("id"));
            layouts.put(
                    id, new MiliastraCanvas.Node(n, actual.get(id), server, text(canvas.get("environment"))));
        }
        var connections = connections(canvas, actual, layouts);
        var attached = new HashMap<String, JsonObject>();
        var occupied = new HashSet<String>();
        for (var element : canvas.getAsJsonArray("nodes")) {
            var view = element.getAsJsonObject();
            if (view.has("label")) occupied.add(text(view.get("id")));
        }
        var freeComments = new JsonArray();
        int freeOrdinal = 0;
        for (var element : canvas.getAsJsonArray("comments")) {
            var raw = element.getAsJsonObject();
            if (raw.has("nodeId") && occupied.add(text(raw.get("nodeId")))) {
                attached.put(text(raw.get("nodeId")), raw);
            } else {
                var fresh = array(newGraph, "comments").get(freeOrdinal++);
                freeComments.add(keepUnknown(savedComment(raw, oldNodes, oldGraph), fresh));
            }
        }
        var mergedNodes = new JsonArray();
        int ordinal = 0;
        for (var element : canvas.getAsJsonArray("nodes")) {
            var view = element.getAsJsonObject();
            String id = text(view.get("id"));
            var generated = newGraph.getAsJsonArray("nodes").get(ordinal++).getAsJsonObject();
            var old = oldNodes.get(id);
            var oldView = oldViews.get(id);
            boolean sameType = oldView != null && oldView.get("type").equals(view.get("type"));
            if (old != null
                    && !sameType
                    && (hasUnknown(old)
                            || !Set.of("nodeIndex", "genericId", "concreteId", "x", "y", "pins", "comments")
                                    .containsAll(old.keySet()))) throw bad("改变含未知参数的节点类型须使用节点结构 JSON。");
            var node = sameType ? old.deepCopy() : generated.deepCopy();
            node.addProperty("nodeIndex", actual.get(id));
            if (sameType && old.has("concreteId") && generated.has("concreteId")) {
                var property = old.getAsJsonObject("concreteId").deepCopy();
                replace(property, "nodeId", generated.getAsJsonObject("concreteId").get("nodeId"));
                node.add("concreteId", property);
            } else replace(node, "concreteId", generated.get("concreteId"));
            if (!sameType) replace(node, "genericId", generated.get("genericId"));
            for (String key : List.of("x", "y")) replace(node, key, generated.get(key));
            boolean same =
                    sameType
                            && oldView.get("data").equals(view.has("data") ? view.get("data") : new JsonObject());
            var pins = new LinkedHashMap<String, JsonObject>();
            if (sameType)
                for (var p : array(old, "pins")) {
                    var pin = p.getAsJsonObject();
                    pins.put(pinKey(pin), pin.deepCopy());
                }
            for (var p : array(generated, "pins")) {
                var pin = p.getAsJsonObject();
                String key = pinKey(pin);
                var saved = pins.get(key);
                if (saved == null) {
                    if (!same || connections.containsKey(id + ":" + key)) pins.put(key, pin.deepCopy());
                } else if (!same) {
                    replace(saved, "type", pin.get("type"));
                    replace(
                            saved,
                            "value",
                            pin.has("value") ? keepUnknown(saved.get("value"), pin.get("value")) : null);
                }
            }
            var finalPins = new JsonArray();
            for (var entry : pins.entrySet()) {
                var pin = entry.getValue();
                int kind = kind(pin);
                var desired = connections.getOrDefault(id + ":" + entry.getKey(), new JsonArray());
                if (kind == 2
                        || kind == 3
                        || (pin.has("connects") && !pin.getAsJsonArray("connects").isEmpty())) {
                    var previous = new HashMap<String, JsonObject>();
                    for (var c : array(pin, "connects")) {
                        var raw = c.getAsJsonObject();
                        previous.put(connectionKey(raw), raw);
                    }
                    var merged = new JsonArray();
                    for (var c : desired) {
                        var raw = c.getAsJsonObject();
                        merged.add(keepUnknown(previous.get(connectionKey(raw)), raw));
                    }
                    replace(pin, "connects", merged.isEmpty() ? null : merged);
                } else pin.remove("connects");
                finalPins.add(pin);
            }
            node.add("pins", finalPins);
            replace(
                    node,
                    "comments",
                    generated.has("comments")
                            ? keepUnknown(
                                    attached.containsKey(id)
                                            ? savedComment(attached.get(id), oldNodes, oldGraph)
                                            : null,
                                    generated.get("comments"))
                            : null);
            mergedNodes.add(node);
        }
        oldGraph.add("nodes", mergedNodes);
        if (!canvas.get("name").equals(baseline.get("name"))) {
            oldGraph.addProperty("name", text(canvas.get("name")));
            unit(original).addProperty("name", text(canvas.get("name")));
        }
        oldGraph.add("comments", freeComments);
    }

    private static JsonElement savedComment(
            JsonObject raw, Map<String, JsonObject> oldNodes, JsonObject oldGraph) {
        if (!raw.has("id")) return null;
        String id = text(raw.get("id"));
        if (id.matches("gia-comment-[0-9]+")) {
            int index = Integer.parseInt(id.substring(12));
            if (index < array(oldGraph, "comments").size()) return array(oldGraph, "comments").get(index);
        } else if (id.matches("gia-node-comment-[0-9]+")) {
            var node = oldNodes.get("gia-node-" + id.substring(17));
            if (node != null) return node.get("comments");
        }
        return null;
    }

    private static boolean hasUnknown(JsonElement value) {
        if (value.isJsonObject()) {
            if (value.getAsJsonObject().has("$unknown")) return true;
            for (var entry : value.getAsJsonObject().entrySet())
                if (hasUnknown(entry.getValue())) return true;
        } else if (value.isJsonArray())
            for (var entry : value.getAsJsonArray()) if (hasUnknown(entry)) return true;
        return false;
    }

    private static Map<String, JsonArray> connections(
            JsonObject canvas, Map<String, Integer> actual, Map<String, MiliastraCanvas.Node> nodes) {
        var result = new HashMap<String, JsonArray>();
        for (var element : canvas.getAsJsonArray("edges")) {
            var edge = element.getAsJsonObject();
            JsonObject a = object(edge, "source"), b = object(edge, "target");
            String from = text(a.get("nodeId")), to = text(b.get("nodeId"));
            var out = nodes.get(from).port(text(a.get("portId")));
            var in = nodes.get(to).port(text(b.get("portId")));
            addConnection(
                    result, from + ":" + out.kind + ":" + out.index, actual.get(to), in.kind, in.index);
            addConnection(
                    result, to + ":" + in.kind + ":" + in.index, actual.get(from), out.kind, out.index);
        }
        return result;
    }

    private static void addConnection(
            Map<String, JsonArray> result, String key, int node, int kind, int index) {
        var pin = obj("kind", kind);
        pin.addProperty("index", index);
        var row = obj("id", node);
        row.add("connect", pin);
        row.add("connect2", pin.deepCopy());
        result.computeIfAbsent(key, ignored -> new JsonArray()).add(row);
    }

    private static JsonElement keepUnknown(JsonElement old, JsonElement fresh) {
        if (fresh == null) return null;
        if (fresh.isJsonArray() && old != null && old.isJsonArray()) {
            var result = new JsonArray();
            var previous = old.getAsJsonArray();
            for (int i = 0; i < fresh.getAsJsonArray().size(); i++)
                result.add(
                        keepUnknown(
                                i < previous.size() ? previous.get(i) : null, fresh.getAsJsonArray().get(i)));
            return result;
        }
        if (!fresh.isJsonObject()) return fresh.deepCopy();
        var result = fresh.getAsJsonObject().deepCopy();
        if (old != null && old.isJsonObject()) {
            var previous = old.getAsJsonObject();
            if (previous.has("$unknown") && !result.has("$unknown"))
                result.add("$unknown", previous.get("$unknown"));
            for (var entry : List.copyOf(result.entrySet()))
                if (!entry.getKey().equals("$unknown"))
                    result.add(entry.getKey(), keepUnknown(previous.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }

    private static void replace(JsonObject target, String key, JsonElement value) {
        if (value == null) target.remove(key);
        else target.add(key, value.deepCopy());
    }

    private static JsonObject comment(JsonObject original, String id, String node) {
        var result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("text", string(original, "content"));
        if (node != null) result.addProperty("nodeId", node);
        if (node == null || original.has("x") || original.has("y")) {
            var position = new JsonObject();
            position.addProperty("x", real(original, "x"));
            position.addProperty("y", real(original, "y"));
            result.add("position", position);
        }
        return result;
    }

    private static JsonObject endpoint(String node, String port) {
        var e = new JsonObject();
        e.addProperty("nodeId", node);
        e.addProperty("portId", port);
        return e;
    }

    private static MiliastraCanvas.Port port(MiliastraCanvas.Node node, int kind, int index) {
        var found =
                node.ports.values().stream().filter(p -> p.kind == kind && p.index == index).toList();
        if (found.size() != 1) throw bad("引脚 " + kind + ":" + index + " 没有唯一编辑器端口。");
        return found.get(0);
    }

    private static String nodeId(int id) {
        return "gia-node-" + id;
    }

    private static String pinKey(JsonObject pin) {
        return kind(pin) + ":" + ordinal(pin);
    }

    private static String connectionKey(JsonObject c) {
        var pin = object(c, "connect");
        return number(c, "id") + ":" + number(pin, "kind") + ":" + number(pin, "index");
    }

    private static int kind(JsonObject pin) {
        return number(object(pin, "i1"), "kind");
    }

    private static int ordinal(JsonObject pin) {
        return number(object(pin, "i1"), "index");
    }

    private static boolean sameIndex(JsonObject a, JsonObject b) {
        return number(a, "kind") == number(b, "kind") && number(a, "index") == number(b, "index");
    }

    private static JsonObject typed(byte[] gia) {
        return MiliastraGraph.readJson(MiliastraNodeJson.decode(gia), 2000000).getAsJsonObject();
    }

    private static JsonObject unit(JsonObject document) {
        return document.getAsJsonObject("data").getAsJsonObject("graph");
    }

    private static JsonObject graph(JsonObject document) {
        return object(object(object(unit(document), "graph"), "inner"), "graph");
    }

    private static int guid(JsonObject document) {
        int id = number(object(unit(document), "id"), "id");
        if (id < 1) throw bad("原图 GUID 须为正 int32。");
        return id;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private static int number(JsonObject parent, String key) {
        return parent.has(key) ? integer(parent.get(key)) : 0;
    }

    private static float real(JsonObject parent, String key) {
        return parent.has(key) ? coordinate(parent.get(key)) : 0;
    }

    private static String string(JsonObject parent, String key) {
        return parent.has(key) ? text(parent.get(key)) : "";
    }

    private static byte[] bytes(JsonObject value) {
        return JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }
}
