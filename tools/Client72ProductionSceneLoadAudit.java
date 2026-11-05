import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.*;
import emu.grasscutter.DebugConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.script.Bindings;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

/** Actual production loading in one disposable JVM. No server, manager or database is constructed. */
public final class Client72ProductionSceneLoadAudit {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT).create();
    private static final List<Map<String, Object>> errors = new ArrayList<>(), attempts = new ArrayList<>();
    private static int currentGroup;

    public static void main(String[] args) throws Exception {
        var request = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        if (!Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json"))
                || !Path.of(config.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data")))
            throw new IllegalStateException("Production config/data must belong to isolated working directory");
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
                if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) return;
                var row = new TreeMap<String, Object>();
                row.put("group", currentGroup); row.put("message", event.getFormattedMessage());
                if (event.getThrowableProxy() != null) {
                    row.put("exception", event.getThrowableProxy().getClassName());
                    row.put("exceptionMessage", String.valueOf(event.getThrowableProxy().getMessage()));
                }
                errors.add(row);
            }
        };
        appender.setContext(Grasscutter.getLogger().getLoggerContext()); appender.start();
        Grasscutter.getLogger().addAppender(appender);
        DebugConstants.LOG_MISSING_LUA_SCRIPTS = true; // Observe the production catch; do not change its return behavior.
        Set<String> beforeThreads = threads();
        ScriptLoader.init();
        boolean guard = request.get("guardBusinessCalls").getAsBoolean();
        String originalUserdata = ScriptLoader.getScriptLibLua().getClass().getName();
        if (guard) installBoundary();
        var effectiveSources = new TreeMap<String, Object>();
        for (var path : request.getAsJsonArray("manifest")) {
            String name = path.getAsString(), source = ScriptLoader.readScript(name);
            effectiveSources.put(name, source == null ? Map.of("missing", true)
                    : Map.of("sha256", hashBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                             "bytes", source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (var item : request.getAsJsonArray("groups")) {
            currentGroup = item.getAsInt();
            int errorStart = errors.size(), attemptStart = attempts.size();
            var previousMissing = new HashMap<>(ScriptLoader.getMissingScripts());
            SceneGroup group = SceneGroup.of(currentGroup);
            String thrown = null;
            try { group.load(3); }
            catch (Exception failure) { thrown = failure.getClass().getName() + ": " + failure.getMessage(); }
            var row = snapshot(group);
            var groupErrors = new ArrayList<>(errors.subList(errorStart, errors.size()));
            var groupAttempts = new ArrayList<>(attempts.subList(attemptStart, attempts.size()));
            var groupMissing = new TreeMap<String, Integer>();
            ScriptLoader.getMissingScripts().forEach((path, count) -> {
                int delta = count - previousMissing.getOrDefault(path, 0);
                if (delta > 0) groupMissing.put(path, delta);
            });
            row.put("errors", groupErrors); row.put("businessAttempts", groupAttempts);
            row.put("missingScriptsDuringGroup", groupMissing);
            if (thrown != null) row.put("uncaughtException", thrown);
            String status = !groupAttempts.isEmpty() ? "outside-scope-business-call-blocked"
                    : thrown != null || !(boolean) row.get("allCollectionsMaterialized") ? "incomplete-production-load"
                    : !groupErrors.isEmpty() || !groupMissing.isEmpty() ? "partial-production-evaluation" : "loaded-within-audit-scope";
            row.put("status", status); groups.add(row);
            if (!groupAttempts.isEmpty()) break; // RequireFunction can swallow the guard exception: the external record controls completion.
        }
        var result = new TreeMap<String, Object>();
        result.put("schemaVersion", 1); result.put("fastRequire", Configuration.FAST_REQUIRE);
        result.put("scope", guard ? "production loader with fail-before-business-call observation boundary" : "original production userdata, reviewed minimal declaration inputs only");
        result.put("guardBusinessCalls", guard); result.put("originalUserdataClass", originalUserdata);
        result.put("serializerClass", ScriptLoader.getSerializer().getClass().getName());
        result.put("groups", groups); result.put("requestedGroups", request.getAsJsonArray("groups").size());
        result.put("processedGroups", groups.size()); result.put("missingScripts", ScriptLoader.getMissingScripts());
        result.put("businessAttempts", attempts); result.put("workingDirectoryAndDataIsolated", true);
        result.put("effectiveScriptSources", effectiveSources);
        Set<String> afterThreads = threads(); afterThreads.removeAll(beforeThreads);
        result.put("newJavaThreadNames", afterThreads);
        result.put("runtimeStatus", "no-trigger-action-or-world-acceptance"); result.put("resourceReady", false);
        result.put("legacySourceIsNative72", false);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        Grasscutter.getLogger().detachAppender(appender);
    }

    private static void installBoundary() throws Exception {
        LuaValue actual = ScriptLoader.getScriptLibLua();
        LuaValue observed = new LuaUserdata(actual.touserdata(), actual.getmetatable()) {
            @Override public LuaValue get(LuaValue key) {
                LuaValue value = actual.get(key);
                if (!value.isfunction()) return value;
                return new VarArgFunction() {
                    @Override public Varargs invoke(Varargs args) {
                        attempts.add(Map.of("group", currentGroup, "member", key.tojstring()));
                        throw new LuaError("AUDIT_BUSINESS_CALL_BLOCKED: " + key.tojstring());
                    }
                };
            }
        };
        var library = ScriptLoader.class.getDeclaredField("scriptLibLua"); library.setAccessible(true); library.set(null, observed);
        var base = ScriptLoader.class.getDeclaredField("baseGlobals"); base.setAccessible(true);
        ((Globals) base.get(null)).set("ScriptLib", observed);
    }

    private static Map<String, Object> snapshot(SceneGroup group) throws Exception {
        var row = new TreeMap<String, Object>();
        row.put("id", group.id); row.put("isLoadedFlag", group.isLoaded()); row.put("compiledScriptPresent", group.getScript() != null);
        row.put("allCollectionsMaterialized", group.monsters != null && group.npcs != null && group.gadgets != null
                && group.regions != null && group.triggers != null && group.suites != null && group.variables != null);
        var sizes = new TreeMap<String, Integer>();
        sizes.put("monsters", group.monsters == null ? -1 : group.monsters.size());
        sizes.put("npcs", group.npcs == null ? -1 : group.npcs.size());
        sizes.put("gadgets", group.gadgets == null ? -1 : group.gadgets.size());
        sizes.put("regions", group.regions == null ? -1 : group.regions.size());
        sizes.put("triggers", group.triggers == null ? -1 : group.triggers.size());
        sizes.put("suites", group.suites == null ? -1 : group.suites.size());
        sizes.put("variables", group.variables == null ? -1 : group.variables.size());
        sizes.put("garbageGadgets", group.garbages == null || group.garbages.gadgets == null ? 0 : group.garbages.gadgets.size());
        row.put("collections", sizes);
        var suiteCounts = new TreeMap<String, Integer>();
        var missing = new ArrayList<Map<String, Object>>();
        if (group.suites != null) for (int i = 0; i < group.suites.size(); i++) {
            var suite = group.suites.get(i);
            for (String kind : List.of("monsters", "gadgets", "regions", "triggers")) {
                var declared = (List<?>) SceneSuite.class.getField(kind).get(suite);
                var field = SceneSuite.class.getField("scene" + Character.toUpperCase(kind.charAt(0)) + kind.substring(1));
                var bound = (List<?>) field.get(suite);
                suiteCounts.merge(kind, bound.size(), Integer::sum);
                if (declared.size() != bound.size()) missing.add(Map.of("suite", i + 1, "collection", kind, "declared", declared.size(), "bound", bound.size()));
            }
        }
        row.put("suiteBound", suiteCounts); row.put("suiteBindingDifferences", missing);
        Bindings bindings = group.getBindings();
        var names = new TreeMap<String, String>(); var scalars = new TreeMap<String, Object>();
        var canonical = new TreeMap<String, Object>();
        if (bindings != null) for (String key : new TreeSet<>(bindings.keySet())) {
            Object value = bindings.get(key);
            if (value instanceof LuaValue lua) {
                names.put(key, lua.typename());
                canonical.put(key, luaSnapshot(lua, new IdentityHashMap<>(), key));
                if (!lua.istable() && !lua.isfunction() && !lua.isuserdata()) scalars.put(key, canonical.get(key));
            } else { names.put(key, value.getClass().getName()); canonical.put(key, String.valueOf(value)); }
        }
        var missingFunctions = new TreeSet<String>();
        if (group.triggers != null) for (var trigger : group.triggers.values())
            for (String function : Arrays.asList(trigger.getCondition(), trigger.getAction()))
                if (function != null && !function.isBlank() && !(bindings.get(function) instanceof LuaValue value && value.isfunction())) missingFunctions.add(function);
        row.put("bindingTypes", names); row.put("scalarBindings", scalars); row.put("bindingsSHA256", hash(canonical));
        row.put("missingNamedTriggerFunctions", missingFunctions);
        row.put("objectsSHA256", hash(jsonSnapshot(GSON.toJsonTree(group))));
        return row;
    }

    private static Object luaSnapshot(LuaValue value, IdentityHashMap<LuaValue, String> seen, String path) {
        if (value.istable()) {
            if (seen.containsKey(value)) return Map.of("reference", seen.get(value));
            seen.put(value, path);
            var result = new TreeMap<String, Object>();
            var keys = value.checktable().keys();
            Arrays.sort(keys, Comparator.comparing(key -> key.typename() + ":" + key.tojstring()));
            for (LuaValue key : keys) {
                String name = key.typename() + ":" + key.tojstring();
                result.put(name, luaSnapshot(value.get(key), seen, path + "/" + name));
            }
            return result;
        }
        if (value.isfunction() || value.isuserdata()) return Map.of("type", value.typename());
        return Map.of("type", value.typename(), "value", value.tojstring());
    }

    private static Object jsonSnapshot(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new TreeMap<String, Object>();
            value.getAsJsonObject().entrySet().forEach(row -> result.put(row.getKey(), jsonSnapshot(row.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new ArrayList<Object>(); value.getAsJsonArray().forEach(item -> result.add(jsonSnapshot(item))); return result;
        }
        return value;
    }

    private static String hash(Object value) throws Exception {
        // Map.of iteration order differs between JVMs; canonicalize every nested object before hashing.
        return hashBytes(GSON.toJson(jsonSnapshot(GSON.toJsonTree(value))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String hashBytes(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Set<String> threads() {
        Set<String> names = new TreeSet<>(); Thread.getAllStackTraces().keySet().forEach(thread -> names.add(thread.getName())); return names;
    }
}
