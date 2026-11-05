import com.google.gson.*;
import emu.grasscutter.game.dungeons.challenge.enums.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.scripts.constants.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.*;

/** Offline LuaJ declaration evaluation. No server, database, entity or trigger is started. */
public final class Client72SceneScriptAudit {
    private final Path scripts;
    private final boolean allStatic;
    private final boolean fastRequire;
    private final Map<String, Map<String, Object>> files = new TreeMap<>();
    private final Set<String> apiReferences = new TreeSet<>();
    private final List<Map<String, Object>> issues = new ArrayList<>();
    private final Map<String, Set<Integer>> targetIds = new TreeMap<>();
    private final Map<String, Integer> checkedIds = new TreeMap<>();
    private Set<String> currentGroupApi;
    private int evaluatedGroups;

    private Client72SceneScriptAudit(Path scripts, Path target, boolean allStatic, boolean fastRequire) throws Exception {
        this.scripts = scripts.toAbsolutePath().normalize();
        this.allStatic = allStatic;
        this.fastRequire = fastRequire;
        for (String[] pair : new String[][]{{"gadgets", "GadgetExcelConfigData.json"},
                {"monsters", "MonsterExcelConfigData.json"}, {"npcs", "NpcExcelConfigData.json"}}) {
            Set<Integer> ids = new HashSet<>();
            try (var reader = Files.newBufferedReader(target.resolve("ExcelBinOutput/" + pair[1]))) {
                for (var row : JsonParser.parseReader(reader).getAsJsonArray())
                    ids.add(row.getAsJsonObject().get("id").getAsInt());
            }
            targetIds.put(pair[0], ids);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("scripts target-source output [all-static] [fast-require] required");
        Set<String> options = new HashSet<>(Arrays.asList(args).subList(3, args.length));
        if (!Set.of("all-static", "fast-require").containsAll(options)) throw new IllegalArgumentException("Unknown evaluation option");
        var audit = new Client72SceneScriptAudit(Path.of(args[0]), Path.of(args[1]), options.contains("all-static"), options.contains("fast-require"));
        Map<String, Object> report = audit.run();
        Files.writeString(Path.of(args[2]), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private Map<String, Object> run() throws Exception {
        Globals root = environment();
        evaluate(root, "Scene/3/scene3.lua");
        var config = root.get("scene_config");
        var born = config.get("born_pos");
        double x = born.get("x").checkdouble(), z = born.get("z").checkdouble();
        LuaValue ids = root.get("blocks"), rects = root.get("block_rects");
        if (ids.length() != rects.length()) throw new IllegalArgumentException("Block/rectangle counts differ");
        List<Integer> selected = new ArrayList<>();
        Set<Integer> allBlocks = new HashSet<>();
        for (int i = 1; i <= ids.length(); i++) {
            int id = ids.get(i).checkint();
            if (!allBlocks.add(id)) throw new IllegalArgumentException("Duplicate block ID");
            var box = rects.get(i);
            if (allStatic || x >= box.get("min").get("x").checkdouble() && x <= box.get("max").get("x").checkdouble()
                    && z >= box.get("min").get("z").checkdouble() && z <= box.get("max").get("z").checkdouble())
                selected.add(id);
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("Born position has no containing block");
        Map<Integer, Map<String, Object>> groups = new TreeMap<>();
        for (int block : selected) {
            Globals env = environment();
            String path = "Scene/3/scene3_block" + block + ".lua";
            evaluate(env, path);
            var rows = env.get("groups").checktable();
            for (LuaValue key : rows.keys()) {
                var row = rows.get(key);
                int id = row.get("id").checkint();
                var info = new TreeMap<String, Object>();
                info.put("id", id);
                info.put("dynamicLoad", row.get("dynamic_load").optboolean(false));
                info.put("evaluated", false);
                info.put("directScriptLibMembers", new TreeSet<String>());
                info.put("businessType", row.get("business").istable() ? row.get("business").get("type").optint(0) : 0);
                groups.put(id, info);
            }
        }
        for (int id : groups.keySet()) {
            if (allStatic && (boolean) groups.get(id).get("dynamicLoad")) continue;
            String path = "Scene/3/scene3_group" + id + ".lua";
            currentGroupApi = new TreeSet<>();
            try {
                Globals env = environment();
                evaluate(env, path);
                evaluatedGroups++;
                groups.get(id).put("evaluated", true);
                for (String kind : targetIds.keySet()) checkEntities(path, env.get(kind), kind, kind);
                var garbage = env.get("garbages");
                if (garbage.istable()) for (String kind : targetIds.keySet())
                    checkEntities(path, garbage.get(kind), kind, "garbages." + kind);
                checkSuiteReferences(path, env);
            } catch (LuaError error) {
                issues.add(Map.of("kind", "lua-evaluation", "file", path, "message", error.getMessage()));
            }
            groups.get(id).put("directScriptLibMembers", currentGroupApi);
            currentGroupApi = null;
        }
        Set<String> available = new TreeSet<>();
        // Reflection reads current API names only; it does not instantiate ScriptLib.
        for (var method : emu.grasscutter.scripts.ScriptLib.class.getMethods()) available.add(method.getName());
        Set<String> missingApi = new TreeSet<>(apiReferences);
        missingApi.removeAll(available);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("scope", allStatic ? "legacy all-scene3 non-dynamic grid-input LuaJ declaration evaluation and selected target IDs only"
                : "legacy scene3 containing-birth-block LuaJ declaration evaluation and selected target IDs only");
        report.put("legacySourceIsNative72", false);
        report.put("runtimeStatus", "not-tested");
        report.put("resourceReady", false);
        report.put("selectedBlocks", selected);
        report.put("allStaticMode", allStatic);
        report.put("fastRequire", fastRequire);
        report.put("declaredGroups", groups.size());
        report.put("evaluatedGroups", evaluatedGroups);
        report.put("groupDeclarations", new ArrayList<>(groups.values()));
        report.put("files", files);
        report.put("checkedPositiveEntityIds", checkedIds);
        report.put("directScriptLibMemberReferences", apiReferences);
        report.put("missingDirectScriptLibMembers", missingApi);
        report.put("issues", issues);
        report.put("limitations", List.of("LuaJ top-level declarations evaluated; trigger/action functions never called",
                "current enum bindings reused, not independently recovered CN72 network enum semantics",
                "only direct adjacent compiled ScriptLib member references; dynamic/aliased calls remain open",
                allStatic ? "dynamic groups inventoried but not evaluated, matching the static grid-input filter"
                        : "containing birth block only, not all visible neighbors or complete required scripts",
                "no production SceneMeta/SceneGroup serializer, entity spawn, sockets, saves or client acceptance"));
        return report;
    }

    private Globals environment() {
        Globals g = JsePlatform.standardGlobals();
        for (Object[] row : new Object[][]{{"EntityType", EntityType.values()}, {"QuestState", QuestState.values()},
                {"ElementType", ElementType.values()}}) {
            LuaTable table = new LuaTable();
            for (Enum<?> value : (Enum<?>[]) row[1]) {
                try {
                    int number = (int) value.getClass().getMethod("getValue").invoke(value);
                    table.set(value.name(), number); table.set(value.name().toUpperCase(Locale.ROOT), number);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            }
            g.set((String) row[0], table);
        }
        for (Object[] row : new Object[][]{{"GroupKillPolicy", GroupKillPolicy.values()},
                {"SealBattleType", SealBattleType.values()}, {"FatherChallengeProperty", FatherChallengeProperty.values()},
                {"ChallengeEventMarkType", ChallengeEventMarkType.values()}, {"VisionLevelType", VisionLevelType.values()}}) {
            LuaTable table = new LuaTable();
            for (Enum<?> value : (Enum<?>[]) row[1]) {
                table.set(value.name(), value.ordinal()); table.set(value.name().toUpperCase(Locale.ROOT), value.ordinal());
            }
            g.set((String) row[0], table);
        }
        g.set("EventType", CoerceJavaToLua.coerce(new EventType()));
        g.set("GadgetState", CoerceJavaToLua.coerce(new ScriptGadgetState()));
        g.set("RegionShape", CoerceJavaToLua.coerce(new ScriptRegionShape()));
        g.set("require", new OneArgFunction() {
            public LuaValue call(LuaValue name) {
                evaluate(g, "Common/" + name.checkjstring() + ".lua");
                return LuaValue.NONE;
            }
        });
        return g;
    }

    private String source(String relative) throws java.io.IOException, java.security.NoSuchAlgorithmException {
        Path path = scripts.resolve(relative).normalize();
        if (!path.startsWith(scripts)) throw new LuaError("Require path outside script input");
        if (!Files.isRegularFile(path)) throw new LuaError("Missing script: " + relative);
        byte[] raw = Files.readAllBytes(path);
        files.put(relative, Map.of("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)), "bytes", raw.length));
        return new String(raw, StandardCharsets.UTF_8);
    }

    private void evaluate(Globals env, String relative) {
        try {
            String text = source(relative);
            // Match ScriptLoader.getScript's existing non-fast Common inlining.
            // It preserves lexical locals such as group-local defs in the same chunk.
            if ((!fastRequire || text.contains("require \"AsterMiddle\"") || text.contains("require \"AsterBig\"")) && text.contains("require")) {
                var expanded = new StringBuilder();
                for (String line : text.split("\n")) {
                    if (!line.startsWith("require") || fastRequire && !line.trim().equals("require \"AsterMiddle\"") && !line.trim().equals("require \"AsterBig\"")) {
                        expanded.append(line).append('\n');
                        continue;
                    }
                    String trimmed = line.trim();
                    String name = trimmed.substring(9, trimmed.length() - 1);
                    expanded.append(source("Common/" + name + ".lua")).append('\n');
                }
                text = expanded.toString();
            }
            Prototype prototype = env.compilePrototype(new java.io.StringReader(text), relative);
            Set<String> direct = new TreeSet<>();
            members(prototype, direct);
            apiReferences.addAll(direct);
            if (currentGroupApi != null) currentGroupApi.addAll(direct);
            new LuaClosure(prototype, env).call();
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) {
            throw new LuaError("Could not evaluate script: " + relative);
        }
    }

    private void members(Prototype p, Set<String> result) {
        for (int i = 0; i + 1 < p.code.length; i++) {
            int first = p.code[i], second = p.code[i + 1];
            if (Lua.GET_OPCODE(first) != Lua.OP_GETTABUP || !Lua.ISK(Lua.GETARG_C(first))) continue;
            if (!p.upvalues[Lua.GETARG_B(first)].name.tojstring().equals("_ENV")) continue;
            LuaValue name = p.k[Lua.INDEXK(Lua.GETARG_C(first))];
            if (!name.isstring() || !name.checkjstring().equals("ScriptLib")) continue;
            if (Lua.GET_OPCODE(second) == Lua.OP_GETTABLE && Lua.GETARG_B(second) == Lua.GETARG_A(first)
                    && Lua.ISK(Lua.GETARG_C(second))) {
                var member = p.k[Lua.INDEXK(Lua.GETARG_C(second))];
                if (member.isstring()) result.add(member.checkjstring());
            }
        }
        for (Prototype child : p.p) members(child, result);
    }

    private void checkEntities(String file, LuaValue value, String kind, String section) {
        if (value.isnil()) return; // Absent optional top-level collection.
        String field = Map.of("gadgets", "gadget_id", "monsters", "monster_id", "npcs", "npc_id").get(kind);
        for (LuaValue key : value.checktable().keys()) {
            var row = value.get(key);
            int id = row.get(field).checkint();
            if (id <= 0) continue;
            checkedIds.merge(kind, 1, Integer::sum);
            if (!targetIds.get(kind).contains(id))
                issues.add(Map.of("kind", "missing-target-entity", "file", file, "collection", kind,
                        "sourceSection", section, "field", field, "targetId", id,
                        "configId", row.get("config_id").optint(0)));
        }
    }

    private void checkSuiteReferences(String file, Globals env) {
        for (String kind : List.of("monsters", "gadgets", "regions", "triggers")) {
            Set<String> declared = new HashSet<>();
            var collection = env.get(kind);
            if (collection.istable()) for (LuaValue key : collection.checktable().keys()) {
                var row = collection.get(key);
                declared.add(row.get(kind.equals("triggers") ? "name" : "config_id").tojstring());
                if (kind.equals("triggers")) for (String field : List.of("condition", "action")) {
                    String function = row.get(field).optjstring("");
                    if (!function.isEmpty() && !env.get(function).isfunction())
                        issues.add(Map.of("kind", "missing-trigger-function", "file", file, "function", function));
                }
            }
            var suites = env.get("suites");
            if (!suites.istable()) continue;
            for (LuaValue suite : suites.checktable().keys()) {
                var members = suites.get(suite).get(kind);
                if (!members.istable()) continue;
                for (LuaValue key : members.checktable().keys()) {
                    String member = members.get(key).tojstring();
                    if (!declared.contains(member)) issues.add(Map.of("kind", "missing-suite-member", "file", file,
                            "suite", suite.tojstring(), "collection", kind, "member", member));
                }
            }
        }
    }
}
