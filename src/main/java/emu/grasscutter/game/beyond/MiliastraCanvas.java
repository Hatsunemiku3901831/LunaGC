package emu.grasscutter.game.beyond;

import static emu.grasscutter.game.beyond.MiliastraCanvasTypes.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Canvas-to-GIA compiler. Unmapped semantics fail with a diagnostic instead of disappearing. */
public final class MiliastraCanvas {
    private static final JsonObject DATA = resource("node-protocol.json").getAsJsonObject("data");
    private static final MiliastraCanvasTypes TYPES = new MiliastraCanvasTypes(DATA);
    private static final Map<String, JsonObject> CATALOG = new HashMap<>();
    private static final Set<String> AMBIGUOUS = new HashSet<>();
    private static final List<JsonObject> RECORDS = new ArrayList<>();
    private static final Map<String, JsonObject> GRAPH = new HashMap<>();
    private static final Map<String, List<String>> REVERSE = new HashMap<>();

    static {
        for (var element : resource("node-catalog.json").getAsJsonArray("nodes")) {
            var row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            if (CATALOG.putIfAbsent(id, row) != null) AMBIGUOUS.add(id);
        }
        for (var element : DATA.getAsJsonArray("NodesList")) RECORDS.add(element.getAsJsonObject());
        for (var element : DATA.getAsJsonArray("GraphConstList")) {
            var row = element.getAsJsonObject();
            GRAPH.put(row.get("Name").getAsString(), row);
        }
        for (var definition : CATALOG.values()) {
            if (AMBIGUOUS.contains(text(definition.get("id")))) continue;
            for (boolean server : List.of(true, false)) {
                var record = recordFor(definition, server);
                if (record != null)
                    REVERSE
                            .computeIfAbsent(
                                    server + ":" + record.get("ID").getAsInt(), ignored -> new ArrayList<>())
                            .add(text(definition.get("id")));
            }
        }
    }

    private MiliastraCanvas() {}

    public static boolean server(byte[] bytes) {
        MiliastraGraph.inspect(bytes);
        var document = MiliastraGraph.readJson(bytes).getAsJsonObject();
        return !document.has("environment") || text(document.get("environment")).startsWith("server");
    }

    public static byte[] encode(byte[] bytes, int graphId) {
        try {
            if (MiliastraGraph.readJson(bytes).getAsJsonObject().has("giaSource"))
                return MiliastraCanvasReverse.restore(bytes, graphId);
            return compile(bytes, graphId);
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw bad("画布属性的结构或类型不合法。");
        }
    }

    private static byte[] compile(byte[] bytes, int graphId) {
        MiliastraGraph.inspect(bytes);
        if (graphId <= 0) throw bad("图 GUID 必须为正 int32。");
        var document = MiliastraGraph.readJson(bytes).getAsJsonObject();
        keys(
                document,
                Set.of(
                        "schemaVersion",
                        "name",
                        "environment",
                        "nodes",
                        "edges",
                        "comments",
                        "createdAt",
                        "updatedAt",
                        "executionIntervalSeconds"));
        if (document.has("executionIntervalSeconds"))
            throw bad("executionIntervalSeconds 的游戏字段尚未核验，请使用节点结构 JSON 保留该字段。");
        String environment = document.has("environment") ? text(document.get("environment")) : "server";
        boolean server = environment.startsWith("server");
        String mode =
                server
                        ? "server"
                        : environment.equals("client:boolean")
                                ? "bool"
                                : environment.equals("client:integer") ? "int" : "skill";
        var graphConst = GRAPH.get(mode).deepCopy();
        if (environment.startsWith("server:")) {
            int variant =
                    switch (environment) {
                        case "server:status" -> 22;
                        case "server:class" -> 23;
                        case "server:item" -> 46;
                        default -> throw bad("未知服务端图类型。");
                    };
            graphConst.addProperty("Which", variant);
            graphConst.addProperty("GraphType", variant == 22 ? 20003 : variant == 23 ? 20004 : 20005);
        }
        var nodes = new LinkedHashMap<String, Node>();
        for (var element : document.getAsJsonArray("nodes")) {
            var raw = element.getAsJsonObject();
            try {
                var node = new Node(raw, nodes.size() + 1, server, environment);
                nodes.put(node.id, node);
            } catch (IllegalArgumentException e) {
                throw bad("节点 " + text(raw.get("id")) + "：" + e.getMessage());
            }
        }
        var edges = new ArrayList<Edge>();
        for (var element : document.getAsJsonArray("edges")) {
            var raw = element.getAsJsonObject();
            keys(raw, Set.of("id", "source", "target"));
            var a = raw.getAsJsonObject("source");
            var b = raw.getAsJsonObject("target");
            keys(a, Set.of("nodeId", "portId"));
            keys(b, Set.of("nodeId", "portId"));
            var from = nodes.get(text(a.get("nodeId")));
            var to = nodes.get(text(b.get("nodeId")));
            Port out = from.port(text(a.get("portId"))), in = to.port(text(b.get("portId")));
            if (!((out.kind == 2 && in.kind == 1) || (out.kind == 4 && in.kind == 3)))
                throw bad("连接端口方向不匹配：" + text(raw.get("id")));
            if (in.kind == 3 && !in.connections.isEmpty())
                throw bad("数据输入不能连接多个来源：" + to.id + "/" + in.id);
            var edge = new Edge(from, out, to, in);
            edges.add(edge);
            (out.kind == 2 ? out : in).connections.add(edge);
        }
        solve(nodes.values(), edges);
        var encodedNodes = new JsonArray();
        for (var node : nodes.values()) encodedNodes.add(node.encode(graphConst, server));
        var comments = new JsonArray();
        if (document.has("comments"))
            for (var element : document.getAsJsonArray("comments")) {
                var raw = element.getAsJsonObject();
                keys(raw, Set.of("id", "nodeId", "position", "text", "pinned", "collapsed"));
                var comment = new JsonObject();
                comment.addProperty("content", text(raw.get("text")));
                if (raw.has("position")) {
                    var pos = raw.getAsJsonObject("position");
                    keys(pos, Set.of("x", "y"));
                    comment.addProperty("x", coordinate(pos.get("x")));
                    comment.addProperty("y", coordinate(pos.get("y")));
                }
                if (raw.has("nodeId")) {
                    var node = nodes.get(text(raw.get("nodeId")));
                    var existing = node.encoded.getAsJsonObject("comments");
                    if (existing == null) node.encoded.add("comments", comment);
                    else {
                        if (!raw.has("position")) {
                            var position = node.raw.getAsJsonObject("position");
                            comment.addProperty("x", coordinate(position.get("x")));
                            comment.addProperty("y", coordinate(position.get("y")));
                        }
                        comments.add(comment);
                    }
                } else comments.add(comment);
            }
        String name = text(document.get("name"));
        var graph = new JsonObject();
        graph.addProperty("name", name);
        graph.add("nodes", encodedNodes);
        graph.add("comments", comments);
        var id = obj("class", graphConst.get("GraphClass").getAsInt());
        id.addProperty("type", graphConst.get("GraphType").getAsInt());
        id.addProperty("kind", graphConst.get("GraphKind").getAsInt());
        id.addProperty("id", Integer.toString(graphId));
        graph.add("id", id);
        var inner = new JsonObject();
        inner.add("graph", graph);
        var wrapper = new JsonObject();
        wrapper.add("inner", inner);
        var unit = new JsonObject();
        unit.add("graph", wrapper);
        unit.addProperty("name", name);
        unit.addProperty("which", graphConst.get("Which").getAsInt());
        var unitId = obj("class", graphConst.get("Class").getAsInt());
        unitId.addProperty("type", graphConst.get("Type").getAsInt());
        unitId.addProperty("id", graphId);
        unit.add("id", unitId);
        var root = new JsonObject();
        root.add("graph", unit);
        root.addProperty("gameVersion", DATA.get("GameVersion").getAsString());
        var typed = new JsonObject();
        typed.addProperty("format", "lunagc.gia.v1");
        typed.addProperty("version", 1);
        typed.add("data", root);
        return MiliastraNodeJson.encode(typed.toString().getBytes(StandardCharsets.UTF_8));
    }

    record Option(
            Integer concrete,
            List<String> inputs,
            List<String> outputs,
            List<Integer> inReflect,
            List<Integer> outReflect) {
        String type(Port port) {
            return (port.kind == 3 ? inputs : outputs).get(port.index);
        }

        Integer reflect(Port port) {
            return (port.kind == 3 ? inReflect : outReflect).get(port.index);
        }
    }

    static final class Port {
        final String id, hint;
        final int kind, index;
        final JsonElement defaultValue;
        final List<Edge> connections = new ArrayList<>();
        JsonElement value;
        String explicitType;
        boolean used;

        Port(String id, int kind, int index, String hint, JsonElement defaultValue) {
            this.id = id;
            this.kind = kind;
            this.index = index;
            this.hint = hint;
            this.defaultValue = defaultValue;
        }
    }

    private record Edge(Node from, Port out, Node to, Port in) {}

    static final class Node {
        final String id, type;
        final int index;
        final JsonObject raw, definition, record;
        final Map<String, Port> ports = new LinkedHashMap<>();
        final List<Option> options = new ArrayList<>();
        Option selected;
        JsonObject encoded;

        Node(JsonObject raw, int index, boolean server, String environment) {
            this.raw = raw;
            this.index = index;
            id = text(raw.get("id"));
            type = text(raw.get("type"));
            keys(raw, Set.of("id", "type", "position", "label", "data"));
            keys(raw.getAsJsonObject("position"), Set.of("x", "y"));
            if (AMBIGUOUS.contains(type)) throw bad("节点目录有歧义：" + type);
            definition = CATALOG.get(type);
            if (definition == null) throw bad("未收录节点：" + type);
            boolean available = false;
            for (var e : definition.getAsJsonArray("environments"))
                available |=
                        e.getAsString().equals(environment)
                                || (server && e.getAsString().equals("server"))
                                || (environment.equals("client") && e.getAsString().startsWith("client:"));
            if (!server && type.equals("event.graphStart")) available = true;
            if (type.equals("flow.graphEndBoolean") && environment.equals("client:boolean"))
                available = true;
            if (type.equals("flow.graphEndInteger") && environment.equals("client:integer"))
                available = true;
            if (!available) throw bad("节点不适用于环境 " + environment + "：" + type);
            record = recordFor(definition, server);
            if (record == null) throw bad("参考协议没有唯一模板映射：" + type);
            var data = raw.has("data") ? raw.getAsJsonObject("data") : new JsonObject();
            keys(
                    data,
                    Set.of(
                            "overrides",
                            "controls",
                            "sequenceFlowOutCount",
                            "branchFlowOutLabels",
                            "giaTypes",
                            "giaPortMap"));
            if (data.has("controls") && !data.getAsJsonObject("controls").entrySet().isEmpty())
                throw bad("界面 controls 尚无协议映射。");
            if (data.has("sequenceFlowOutCount")) throw bad("顺序执行节点的协议映射尚未核验。");
            if (data.has("branchFlowOutLabels") && !type.equals("flow.branch.multi"))
                throw bad("分支标签仅适用于多路分支。");
            var definitions = definition.getAsJsonArray("ports");
            int[] counts = new int[5];
            for (var element : definitions) counts[kind(element.getAsJsonObject())]++;
            int[] offset = new int[5];
            var custom = data.has("giaPortMap") ? data.getAsJsonObject("giaPortMap") : new JsonObject();
            for (var element : definitions) {
                var p = element.getAsJsonObject();
                int kind = kind(p);
                int ordinal = offset[kind]++;
                String portId = text(p.get("id")),
                        hint = p.has("valueType") ? text(p.get("valueType")) : "";
                int pin = ordinal;
                if (kind >= 3) {
                    var protocol = record.getAsJsonArray(kind == 3 ? "Inputs" : "Outputs");
                    if (custom.has(portId)) pin = integer(custom.get(portId));
                    else if (type.equals("flow.branch.multi") && kind == 3 && portId.equals("controlExpr"))
                        pin = 0;
                    else if (counts[kind] != protocol.size()) {
                        var possible = new ArrayList<Integer>();
                        for (int i = 0; i < protocol.size(); i++)
                            if (hintMatches(hint, protocol.get(i).getAsString())) possible.add(i);
                        pin = possible.size() == 1 ? possible.get(0) : -1;
                    }
                    if (pin < -1 || pin >= protocol.size()) throw bad("giaPortMap 引脚越界：" + portId);
                    if (custom.has(portId) && pin < 0) throw bad("giaPortMap 引脚编号必须为非负整数：" + portId);
                } else if (custom.has(portId)) throw bad("流程引脚不接受 giaPortMap。");
                if (ports.putIfAbsent(portId, new Port(portId, kind, pin, hint, p.get("defaultValue")))
                        != null) throw bad("端口重复。");
            }
            var occupied = new HashSet<String>();
            for (var p : ports.values())
                if (p.index >= 0 && !occupied.add(p.kind + ":" + p.index))
                    throw bad("端口映射重合，请明确 giaPortMap：" + type);
            for (var key : custom.keySet())
                if (!ports.containsKey(key)) throw bad("giaPortMap 引用了未知端口：" + key);
            JsonArray labels =
                    data.has("branchFlowOutLabels")
                            ? data.getAsJsonArray("branchFlowOutLabels")
                            : new JsonArray();
            if (labels.size() > 10) throw bad("分支最多十个标签。");
            if (type.equals("flow.branch.multi")) {
                for (int i = 0; i < labels.size(); i++) {
                    text(labels.get(i));
                    ports.put("branch" + (i + 1), new Port("branch" + (i + 1), 2, counts[2] + i, "", null));
                }
                var branch = new Port("$branches", 3, 1, "list", null);
                if (!labels.isEmpty()) branch.value = labels;
                ports.put(branch.id, branch);
                if (ports.get("controlExpr").index != 0) throw bad("多路分支的控制表达式固定使用输入 0，输入 1 存放分支标签。");
            }
            if (data.has("overrides"))
                for (var entry : data.getAsJsonObject("overrides").entrySet()) {
                    var p = port(entry.getKey());
                    if (p.kind != 3 || p.id.startsWith("$")) throw bad("常量仅可覆盖数据输入：" + p.id);
                    p.value = entry.getValue();
                    p.used = true;
                }
            if (data.has("giaTypes"))
                for (var entry : data.getAsJsonObject("giaTypes").entrySet()) {
                    var p = port(entry.getKey());
                    if (p.kind < 3 || p.id.startsWith("$")) throw bad("giaTypes 仅指定数据端口类型。");
                    p.explicitType = text(entry.getValue());
                    TYPES.code(p.explicitType, server);
                    p.used = true;
                }
            if (record.get("Type").getAsString().equals("Simple")) {
                Integer concrete =
                        server
                                ? record.get("ID").getAsInt()
                                : record.has("ConcreteID") ? record.get("ConcreteID").getAsInt() : null;
                var in = strings(record.getAsJsonArray("Inputs"));
                var out = strings(record.getAsJsonArray("Outputs"));
                options.add(
                        new Option(
                                concrete,
                                in,
                                out,
                                Collections.nCopies(in.size(), null),
                                Collections.nCopies(out.size(), null)));
            } else
                for (var element : record.getAsJsonArray("TypeMappings")) {
                    var mapping = element.getAsJsonObject();
                    var bindings = bindings(text(mapping.get("Type")));
                    options.add(
                            new Option(
                                    mapping.get("ConcreteId").getAsInt(),
                                    resolved(record.getAsJsonArray("Inputs"), bindings),
                                    resolved(record.getAsJsonArray("Outputs"), bindings),
                                    integers(mapping.getAsJsonArray("InputsIndexOfConcrete")),
                                    integers(mapping.getAsJsonArray("OutputsIndexOfConcrete"))));
                }
            options.removeIf(
                    option ->
                            ports.values().stream()
                                    .anyMatch(
                                            p ->
                                                    p.kind >= 3
                                                            && p.index >= 0
                                                            && ((p.explicitType != null && !p.explicitType.equals(option.type(p)))
                                                                    || (p.value != null
                                                                            && !p.id.equals("$branches")
                                                                            && !TYPES.accepts(option.type(p), p.value, server)))));
            if (options.isEmpty()) throw bad("输入常量或 giaTypes 与所有具体类型均不匹配：" + type);
        }

        Port port(String name) {
            var port = ports.get(name);
            if (port == null) throw bad("未知端口：" + id + "/" + name);
            if (port.index < 0) throw bad("端口编号有歧义，请通过 data.giaPortMap 明确：" + id + "/" + name);
            port.used = true;
            return port;
        }

        JsonObject encode(JsonObject constants, boolean server) {
            encoded = obj("nodeIndex", index);
            encoded.add("genericId", property(record.get("ID").getAsInt(), constants));
            if (selected != null && selected.concrete != null)
                encoded.add("concreteId", property(selected.concrete, constants));
            else if (record.get("Type").getAsString().equals("Simple") && options.get(0).concrete != null)
                encoded.add("concreteId", property(options.get(0).concrete, constants));
            var position = raw.getAsJsonObject("position");
            encoded.addProperty("x", coordinate(position.get("x")));
            encoded.addProperty("y", coordinate(position.get("y")));
            if (raw.has("label")) {
                var comment = new JsonObject();
                comment.addProperty("content", text(raw.get("label")));
                encoded.add("comments", comment);
            }
            var pins = new JsonArray();
            for (var p : ports.values()) {
                if (p.kind == 1 || (p.kind == 2 && p.connections.isEmpty()) || p.index < 0) continue;
                var pin = new JsonObject();
                pin.add("i1", pinIndex(p.kind, p.index));
                pin.add("i2", pinIndex(p.kind, p.index));
                if (p.kind >= 3) {
                    String expression = selected != null ? selected.type(p) : commonType(options, p);
                    if (expression == null) {
                        if (p.used || p.value != null) throw bad("请通过 data.giaTypes 明确泛型：" + id + "/" + p.id);
                        else continue;
                    }
                    if (p.kind == 4 && !p.used && selected == null) continue;
                    pin.addProperty("type", TYPES.code(expression, server));
                    JsonElement constant =
                            p.value != null ? p.value : p.connections.isEmpty() ? p.defaultValue : null;
                    if (p.id.equals("$branches") && constant != null && expression.equals("L<Int>")) {
                        var values = new JsonArray();
                        for (var label : constant.getAsJsonArray()) {
                            try {
                                values.add(Integer.parseInt(text(label)));
                            } catch (NumberFormatException e) {
                                throw bad("整数分支标签需要 int32：" + text(label));
                            }
                        }
                        constant = values;
                    }
                    JsonObject value =
                            constant != null ? TYPES.value(expression, constant, server) : new JsonObject();
                    Integer concrete = selected == null ? null : selected.reflect(p);
                    boolean wrapped =
                            concrete != null || expression.startsWith("L<") || expression.startsWith("D<");
                    if (wrapped) {
                        var body = obj("indexOfConcrete", concrete == null ? 0 : concrete);
                        body.add("value", value);
                        if (expression.startsWith("D<")) {
                            var kv = new JsonObject();
                            var pair = arguments(expression, 'D');
                            kv.addProperty("key", TYPES.code(pair.get(0), server));
                            kv.addProperty("value", TYPES.code(pair.get(1), server));
                            var wrapper = obj("class", 10003);
                            wrapper.add("mapPair", kv);
                            var inner = new JsonObject();
                            inner.add("wrapper", wrapper);
                            var structures = obj("class", 1);
                            structures.add("inner", inner);
                            body.add("structs", structures);
                        }
                        value = obj("class", 10000);
                        value.addProperty("alreadySetVal", true);
                        value.add("bConcreteValue", body);
                    }
                    if (constant != null || wrapped) pin.add("value", value);
                }
                if (!p.connections.isEmpty()) {
                    var connections = new JsonArray();
                    for (var e : p.connections) {
                        var connection = obj("id", p.kind == 2 ? e.to.index : e.from.index);
                        var target = p.kind == 2 ? pinIndex(1, e.in.index) : pinIndex(4, e.out.index);
                        connection.add("connect", target);
                        connection.add("connect2", target.deepCopy());
                        connections.add(connection);
                    }
                    pin.add("connects", connections);
                }
                pins.add(pin);
            }
            encoded.add("pins", pins);
            return encoded;
        }
    }

    static MiliastraCanvasTypes types() {
        return TYPES;
    }

    static List<String> templates(int generic, boolean server) {
        return List.copyOf(REVERSE.getOrDefault(server + ":" + generic, List.of()));
    }

    private static JsonObject recordFor(JsonObject definition, boolean server) {
        String range = server ? "Server" : "Client";
        var records =
                RECORDS.stream()
                        .filter(
                                r ->
                                        r.get("Range").getAsString().equals(range)
                                                && r.get("ID").equals(definition.get("officialID")))
                        .toList();
        if (records.size() != 1)
            records =
                    RECORDS.stream()
                            .filter(
                                    r ->
                                            r.get("Range").getAsString().equals(range)
                                                    && r.getAsJsonObject("Translations").has("en")
                                                    && r.getAsJsonObject("Translations")
                                                            .get("en")
                                                            .getAsString()
                                                            .equals(definition.get("displayNameEN").getAsString()))
                            .toList();
        return records.size() == 1 ? records.get(0) : null;
    }

    private record Choice(Option option, int score) {}

    private static void solve(Collection<Node> nodes, List<Edge> edges) {
        var ordered = new ArrayList<>(nodes);
        var indexes = new IdentityHashMap<Node, Integer>();
        var domains = new ArrayList<List<Choice>>();
        for (var node : ordered) {
            indexes.put(node, indexes.size());
            var choices = new ArrayList<Choice>();
            for (var option : node.options) {
                int score = 0;
                for (var p : node.ports.values())
                    if (p.kind >= 3
                            && p.index >= 0
                            && hintMatches(p.hint, option.type(p))
                            && !p.hint.equals("any")
                            && !p.hint.equals("list")
                            && !p.hint.equals("enum")) score++;
                choices.add(new Choice(option, score));
            }
            domains.add(choices);
        }
        var links = new ArrayList<MiliastraTypeConstraints.Link<Choice>>();
        for (var e : edges)
            if (e.out.kind == 4)
                links.add(
                        new MiliastraTypeConstraints.Link<>(
                                indexes.get(e.from),
                                indexes.get(e.to),
                                choice -> choice.option.type(e.out),
                                choice -> choice.option.type(e.in)));
        var solutions = MiliastraTypeConstraints.solve(domains, links, Choice::score);
        for (int i = 0; i < ordered.size(); i++) {
            var node = ordered.get(i);
            node.options.clear();
            for (var choice : solutions.get(i)) node.options.add(choice.option);
            node.selected = node.options.size() == 1 ? node.options.get(0) : null;
        }
        for (var e : edges)
            if (e.out.kind == 4) {
                String a =
                        e.from.selected == null
                                ? commonType(e.from.options, e.out)
                                : e.from.selected.type(e.out);
                String b =
                        e.to.selected == null ? commonType(e.to.options, e.in) : e.to.selected.type(e.in);
                if (a == null || b == null || !a.equals(b))
                    throw bad("连接的泛型仍有歧义，请明确 data.giaTypes：" + e.from.id + " → " + e.to.id);
            }
    }

    private static String commonType(List<Option> options, Port port) {
        String result = options.get(0).type(port);
        return options.stream().allMatch(o -> o.type(port).equals(result)) ? result : null;
    }

    private static boolean hintMatches(String hint, String type) {
        if (type.contains("R<") || hint.equals("any")) return true;
        return switch (hint) {
            case "bool" -> type.equals("Bol");
            case "int" -> type.equals("Int");
            case "float" -> type.equals("Flt");
            case "string" -> type.equals("Str");
            case "vector3" -> type.equals("Vec");
            case "entity" -> type.equals("Ety");
            case "guid" -> type.equals("Gid");
            case "camp" -> type.equals("Fct");
            case "configId" -> type.equals("Cfg");
            case "componentId" -> type.equals("Pfb");
            case "list" -> type.startsWith("L<");
            case "enum" -> type.startsWith("E<");
            default -> false;
        };
    }

    private static JsonObject property(int id, JsonObject gc) {
        var result = obj("nodeId", id);
        result.addProperty("class", gc.get("NodeClass").getAsInt());
        result.addProperty("type", gc.get("NodeType").getAsInt());
        result.addProperty("kind", gc.get("NodeKind").getAsInt());
        return result;
    }

    private static JsonObject pinIndex(int kind, int index) {
        var result = obj("kind", kind);
        result.addProperty("index", index);
        return result;
    }

    private static int kind(JsonObject p) {
        return switch (text(p.get("kind"))) {
            case "flow-in" -> 1;
            case "flow-out" -> 2;
            case "data-in" -> 3;
            case "data-out" -> 4;
            default -> throw bad("未知端口方向。");
        };
    }

    private static List<String> strings(JsonArray array) {
        var result = new ArrayList<String>();
        for (var value : array) result.add(text(value));
        return List.copyOf(result);
    }

    private static List<String> resolved(JsonArray array, Map<String, String> bindings) {
        return strings(array).stream().map(s -> resolve(s, bindings)).toList();
    }

    private static List<Integer> integers(JsonArray array) {
        var result = new ArrayList<Integer>();
        for (var value : array) result.add(value.isJsonNull() ? null : integer(value));
        return Collections.unmodifiableList(result);
    }

    private static void keys(JsonObject value, Set<String> allowed) {
        for (var key : value.keySet()) if (!allowed.contains(key)) throw bad("属性没有协议映射：" + key);
    }

    private static JsonObject resource(String name) {
        try (var stream = MiliastraCanvas.class.getResourceAsStream("/miliastra/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing canvas metadata: " + name);
            return MiliastraGraph.readJson(stream.readAllBytes(), 2000000).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read canvas metadata", e);
        }
    }
}
