import com.google.gson.*;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.serializer.LuaSerializer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import org.luaj.vm2.*;

/** Direct production serializer only. Never initializes ScriptLoader or a scene manager. */
public final class Client72SceneSerializeAudit {
    private final LuaSerializer serializer = new LuaSerializer();
    private final Object evaluator;
    private final Method environment, evaluate;
    private final Map<String, Integer> counts = new TreeMap<>(), ignoredFields = new TreeMap<>();
    private final Map<String, Integer> unboundDeclaredFields = new TreeMap<>();
    private final Map<String, Map<String, Object>> unboundExamples = new TreeMap<>();
    private final List<Map<String, Object>> issues = new ArrayList<>();
    private final Map<Integer, GadgetData> gadgetData = new HashMap<>();
    private final JsonArray vision;
    private final List<Map<GridPosition, Set<Integer>>> grids = new ArrayList<>();
    private final Map<Integer, Integer> regionShapes = new TreeMap<>();
    private int groupId;
    private String stage = "metadata";

    private Client72SceneSerializeAudit(Path scripts, Path target, Path config) throws Exception {
        // Reuse the reviewed inline LuaJ evaluator without changing its public behavior.
        var constructor = Client72SceneScriptAudit.class.getDeclaredConstructor(Path.class, Path.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        evaluator = constructor.newInstance(scripts, target, true, false);
        environment = Client72SceneScriptAudit.class.getDeclaredMethod("environment");
        evaluate = Client72SceneScriptAudit.class.getDeclaredMethod("evaluate", Globals.class, String.class);
        environment.setAccessible(true); evaluate.setAccessible(true);
        vision = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("server")
                .getAsJsonObject("game").getAsJsonArray("visionOptions");
        if (vision.size() != 6) throw new IllegalArgumentException("Production grid path requires six vision options");
        for (var option : vision) {
            if (option.getAsJsonObject().get("gridWidth").getAsInt() <= 0)
                throw new IllegalArgumentException("Positive grid width required");
            grids.add(new HashMap<>());
        }
        var gson = new Gson();
        try (var reader = Files.newBufferedReader(target.resolve("ExcelBinOutput/GadgetExcelConfigData.json"))) {
            for (var row : JsonParser.parseReader(reader).getAsJsonArray()) {
                var data = gson.fromJson(row, GadgetData.class);
                gadgetData.put(data.getId(), data);
                inc(data.getVisionLevel() == null ? "targetGadgetRowsWithoutVisionLevel" : "targetGadgetRowsWithVisionLevel");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("scripts target config output required");
        var audit = new Client72SceneSerializeAudit(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        Files.writeString(Path.of(args[3]), new GsonBuilder().setPrettyPrinting().create().toJson(audit.run()) + "\n");
    }

    private Map<String, Object> run() throws Exception {
        Globals root = read("Scene/3/scene3.lua");
        object(SceneConfig.class, root.get("scene_config"), "scene_config");
        var blockIds = list(Integer.class, root.get("blocks"), "blocks");
        var blocks = list(SceneBlock.class, root.get("block_rects"), "block_rects");
        if (blocks.size() != blockIds.size()) throw new IllegalArgumentException("Block count mismatch");
        SceneIndexManager.buildIndex(2, blocks, SceneBlock::toRectangle);
        inc("blockRectangleIndexesBuilt");
        Map<Integer, SceneGroup> groups = new TreeMap<>();
        for (int i = 0; i < blockIds.size(); i++) {
            var block = blocks.get(i); block.id = blockIds.get(i);
            Globals env = read("Scene/3/scene3_block" + block.id + ".lua");
            var declared = list(SceneGroup.class, env.get("groups"), "groups");
            SceneIndexManager.buildIndex(3, declared, g -> g.pos.toPoint());
            inc("groupPositionIndexesBuilt");
            for (var group : declared) {
                group.block_id = block.id;
                if (groups.putIfAbsent(group.id, group) != null) issue("duplicate-group", "groups", group.id);
            }
        }
        for (var group : groups.values()) {
            if (group.dynamic_load) { inc("dynamicGroupsSkipped"); continue; }
            groupId = group.id;
            try {
                stage = "top-level";
                Globals env = read("Scene/3/scene3_group" + group.id + ".lua");
                inc("evaluatedGroups");
                load(group, env);
                inc("serializedGroups");
                int rejected = counts.getOrDefault("rejectedGridInputs", 0);
                index(group);
                inc(counts.getOrDefault("rejectedGridInputs", 0) == rejected ? "gridInputGroupsCompleted" : "gridInputGroupsPartial");
            } catch (Exception error) {
                Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
                issues.add(Map.of("kind", "group-failure", "group", groupId, "path", stage,
                        "value", cause.getClass().getName() + ": " + cause.getMessage(),
                        "frames", Arrays.stream(cause.getStackTrace()).map(StackTraceElement::toString).toList()));
            }
        }
        var layers = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 6; i++) {
            var grid = grids.get(i);
            SceneIndexManager.buildIndex(2, grid.keySet(), GridPosition::toPoint);
            var canonical = new TreeMap<String, Set<Integer>>();
            grid.forEach((key, value) -> canonical.put(key.toString(), new TreeSet<>(value)));
            byte[] raw = new Gson().toJson(canonical).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            layers.add(Map.of("layer", i, "cells", grid.size(), "groupMemberships", grid.values().stream().mapToInt(Set::size).sum(),
                    "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))));
        }
        var field = Client72SceneScriptAudit.class.getDeclaredField("files"); field.setAccessible(true);
        var result = new TreeMap<String, Object>();
        result.put("schemaVersion", 1); result.put("counts", counts); result.put("issues", issues);
        result.put("ignoredSourceFields", ignoredFields); result.put("regionShapeCounts", regionShapes);
        result.put("unboundDeclaredSourceFields", unboundDeclaredFields);
        result.put("unboundDeclaredFieldExamples", unboundExamples);
        result.put("gridLayers", layers); result.put("files", field.get(evaluator));
        result.put("declaredGroups", groups.size()); result.put("blocks", blocks.size());
        result.put("visionOptions", vision); result.put("requireMode", "inline");
        result.put("legacySourceIsNative72", false); result.put("resourceReady", false);
        result.put("runtimeStatus", "not-tested");
        result.put("scope", "direct production LuaSerializer, SceneSuite.init, RTree construction and source-equivalent grid-input model");
        result.put("limitations", List.of("No ScriptLoader.init, SceneGroup.load, SceneScriptManager, GameData registry, events, cache, server or database",
                "Grid algorithm transcribed from reviewed source, not a live getGroupGrids invocation",
                "GadgetData directly Gson-deserialized from fixed target table; absent visionLevel uses existing zero fallback, unknown columns never renamed",
                "All declared group positions indexed; only non-dynamic group bodies serialized",
                "No trigger, action, ScriptLib business, region contains, entity spawn, network or client acceptance",
                "Unknown source fields counted, not assigned new meanings; current constants are not independent CN72 enum evidence"));
        return result;
    }

    private void load(SceneGroup g, Globals env) throws Exception {
        stage = "serialize-collections";
        g.monsters = keyed(list(SceneMonster.class, env.get("monsters"), "monsters"), m -> m.config_id, "monsters");
        g.npcs = keyed(list(SceneNPC.class, env.get("npcs"), "npcs"), m -> m.config_id, "npcs");
        g.gadgets = keyed(list(SceneGadget.class, env.get("gadgets"), "gadgets"), m -> m.config_id, "gadgets");
        g.regions = keyed(list(SceneRegion.class, env.get("regions"), "regions"), r -> r.config_id, "regions");
        g.triggers = keyed(list(SceneTrigger.class, env.get("triggers"), "triggers"), SceneTrigger::getName, "triggers");
        g.suites = list(SceneSuite.class, env.get("suites"), "suites");
        g.variables = list(SceneVar.class, env.get("variables"), "variables");
        g.init_config = object(SceneInitConfig.class, env.get("init_config"), "init_config");
        g.monsters.values().forEach(m -> m.group = g); g.npcs.values().forEach(n -> n.group = g);
        g.gadgets.values().forEach(m -> m.group = g); g.regions.values().forEach(r -> r.group = g);
        g.triggers.values().forEach(t -> t.currentGroup = g);
        if (!env.get("garbages").isnil()) {
            g.garbages = new SceneGarbage();
            var value = env.get("garbages").checktable().get("gadgets");
            if (!value.isnil()) {
                g.garbages.gadgets = list(SceneGadget.class, value, "garbages.gadgets");
                g.garbages.gadgets.forEach(m -> m.group = g);
            }
            for (var key : env.get("garbages").checktable().keys())
                if (!key.tojstring().equals("gadgets")) ignoredFields.merge("SceneGroup.garbages." + key.tojstring(), 1, Integer::sum);
        }
        for (int i = 0; i < g.suites.size(); i++) {
            var suite = g.suites.get(i);
            stage = "suites[" + (i + 1) + "].init";
            suite.init(g); inc("initializedSuites");
            suiteRefs("monsters", suite.monsters.size(), suite.sceneMonsters.size());
            suiteRefs("gadgets", suite.gadgets.size(), suite.sceneGadgets.size());
            suiteRefs("regions", suite.regions.size(), suite.sceneRegions.size());
            suiteRefs("triggers", suite.triggers.size(), suite.sceneTriggers.size());
        }
    }

    private void index(SceneGroup g) {
        stage = "grid-input-model";
        Set<Integer> levels = new TreeSet<>();
        for (var m : g.monsters.values()) { grid("monsters", m.vision_level, m.pos); levels.add(m.vision_level); }
        for (var gadget : g.gadgets.values()) {
            int level = 0;
            var data = gadgetData.get(gadget.gadget_id);
            if (data == null) issue("missing-target-gadget", "gadgets", gadget.gadget_id);
            else if (data.getVisionLevel() != null) {
                boolean matched = false;
                for (int i = 0; i < vision.size(); i++) if (vision.get(i).getAsJsonObject().get("name").getAsString().equals(data.getVisionLevel())) {
                    level = i; matched = true; break;
                }
                if (!matched) issue("unmatched-target-vision-name", "gadgets", data.getVisionLevel());
            } else inc("gadgetGridInputsUsingAbsentTargetVisionFallback");
            level = Math.max(level, gadget.vision_level);
            grid("gadgets", level, gadget.pos); levels.add(level);
        }
        for (var n : g.npcs.values()) grid("npcs", n.vision_level, n.pos);
        for (var r : g.regions.values()) {
            regionShapes.merge(r.shape, 1, Integer::sum);
            if (r.shape < 0 || r.shape > 4) issue("unknown-region-shape", "regions", r.shape);
            grid("regions", 0, r.pos);
        }
        if (g.garbages != null && g.garbages.gadgets != null) for (var gadget : g.garbages.gadgets) {
            if (!gadgetData.containsKey(gadget.gadget_id)) issue("missing-target-gadget", "garbages.gadgets", gadget.gadget_id);
            grid("garbages.gadgets", gadget.vision_level, gadget.pos);
        }
        int max = -1;
        for (int level : levels) {
            if (level < 0 || level >= 6) continue; // Already reported by grid; cannot select an invalid layer.
            if (max == -1 || vision.get(max).getAsJsonObject().get("visionRange").getAsInt()
                    < vision.get(level).getAsJsonObject().get("visionRange").getAsInt()) max = level;
        }
        grid("group", max == -1 ? 0 : max, g.pos);
    }

    private void grid(String section, int level, Position pos) {
        if (level < 0 || level >= 6) { issue("invalid-vision-layer", section, level); inc("rejectedGridInputs"); return; }
        if (pos == null || !Float.isFinite(pos.getX()) || !Float.isFinite(pos.getY()) || !Float.isFinite(pos.getZ())) {
            issue("invalid-grid-position", section, pos == null ? "null" : "non-finite"); inc("rejectedGridInputs"); return;
        }
        int width = vision.get(level).getAsJsonObject().get("gridWidth").getAsInt();
        var cell = new GridPosition((int) (pos.getX() / width), (int) (pos.getZ() / width), width);
        grids.get(level).computeIfAbsent(cell, key -> new TreeSet<>()).add(groupId);
        inc("gridInputs." + section);
    }

    private Globals read(String relative) throws Exception {
        Globals env = (Globals) environment.invoke(evaluator);
        evaluate.invoke(evaluator, env, relative);
        return env;
    }

    private <T> T object(Class<T> type, LuaValue raw, String path) throws Exception {
        T value = serializer.toObject(type, raw.isnil() ? null : raw.checktable());
        if (!raw.isnil()) compare(raw, value, type, path);
        inc("objects." + type.getSimpleName());
        return value;
    }

    private <T> List<T> list(Class<T> type, LuaValue raw, String path) throws Exception {
        List<T> values = serializer.toList(type, raw.isnil() ? null : raw.checktable());
        if (!raw.isnil()) compare(raw, values, List.class, path);
        counts.merge("rows." + path, values.size(), Integer::sum);
        return values;
    }

    // Observe silent coercion/dropped fields on actual returned objects; do not replace the serializer.
    private void compare(LuaValue raw, Object actual, Class<?> type, String path) throws Exception {
        if (actual == null) { issue("serialized-null", path, raw.typename()); return; }
        if (actual instanceof List<?> list) {
            var keys = raw.checktable().keys();
            if (keys.length != list.size()) { issue("serialized-list-size", path, keys.length + "->" + list.size()); return; }
            for (int i = 0; i < keys.length; i++) {
                Object item = list.get(i);
                compare(raw.get(keys[i]), item, item == null ? Object.class : item.getClass(), path + "[" + keys[i].tojstring() + "]");
            }
        } else if (raw.istable()) {
            Map<String, Field> fields = new HashMap<>();
            for (Class<?> cls = type; cls != Object.class; cls = cls.getSuperclass()) for (Field field : cls.getDeclaredFields())
                if (!Modifier.isStatic(field.getModifiers())) fields.putIfAbsent(field.getName(), field);
            Set<String> setters = new HashSet<>();
            for (Method method : type.getMethods()) setters.add(method.getName());
            for (LuaValue key : raw.checktable().keys()) {
                String name = key.tojstring(); Field field = fields.get(name);
                if (field == null || !setters.contains(serializer.getSetterName(name))) {
                    if (field != null) {
                        String fieldName = type.getSimpleName() + "." + name;
                        unboundDeclaredFields.merge(fieldName, 1, Integer::sum);
                        field.setAccessible(true);
                        unboundExamples.putIfAbsent(fieldName, Map.of("group", groupId, "path", path + "." + name,
                                "source", raw.get(key).tojstring(), "actual", String.valueOf(field.get(actual)),
                                "expectedSetter", serializer.getSetterName(name)));
                    }
                    ignoredFields.merge(type.getSimpleName() + "." + name, 1, Integer::sum); continue;
                }
                field.setAccessible(true);
                Object value = field.get(actual);
                if (value instanceof List<?> list && field.getGenericType() instanceof ParameterizedType parameterized
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> expected) {
                    for (int i = 0; i < list.size(); i++) if (!expected.isInstance(list.get(i)))
                        issue("list-element-type", path + "." + name + "[" + (i + 1) + "]", expected.getName() + "<-" + list.get(i).getClass().getName());
                }
                compare(raw.get(key), value, field.getType(), path + "." + name);
            }
        } else if (actual instanceof Number number) {
            if (raw.type() != LuaValue.TNUMBER) issue("numeric-coercion", path, raw.typename() + "->" + number);
            else {
                double expected = (type == float.class || type == Float.class) ? (double) (float) raw.todouble() : raw.todouble();
                if (Double.compare(expected, number.doubleValue()) != 0) issue("numeric-value-changed", path, raw.tojstring() + "->" + number);
            }
        } else if (actual instanceof Boolean bool) {
            if (!raw.isboolean() || bool != raw.toboolean()) issue("boolean-coercion", path, raw.typename() + "->" + bool);
        } else if (actual instanceof String text) {
            if (raw.type() != LuaValue.TSTRING || !text.equals(raw.tojstring())) issue("string-coercion", path, raw.typename() + "->" + text);
        } else issue("unsupported-serialized-value", path, type.getName());
    }

    private <K, T> Map<K, T> keyed(List<T> rows, Function<T, K> key, String path) {
        Map<K, T> result = new LinkedHashMap<>();
        for (T row : rows) if (result.putIfAbsent(key.apply(row), row) != null) issue("duplicate-config-key", path, key.apply(row));
        return result;
    }
    private void suiteRefs(String path, int declared, int bound) {
        if (declared != bound) issue("suite-reference-dropped", path, declared + "->" + bound);
        counts.merge("suiteBound." + path, bound, Integer::sum);
    }
    private void inc(String key) { counts.merge(key, 1, Integer::sum); }
    private void issue(String kind, String path, Object value) {
        issues.add(Map.of("kind", kind, "group", groupId, "path", path, "value", String.valueOf(value)));
    }
}
