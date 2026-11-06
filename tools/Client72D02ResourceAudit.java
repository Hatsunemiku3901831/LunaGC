import com.google.gson.GsonBuilder;
import emu.grasscutter.game.world.GridPosition;
import emu.grasscutter.scripts.SceneIndexManager;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Query a reviewed serializer's source-equivalent grid model, not a live scene. */
public final class Client72D02ResourceAudit {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("scripts target config output required");
        var type = Client72SceneSerializeAudit.class;
        var constructor = type.getDeclaredConstructor(Path.class, Path.class, Path.class);
        constructor.setAccessible(true);
        var audit = constructor.newInstance(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        var run = type.getDeclaredMethod("run");
        run.setAccessible(true);
        var report = (Map<String, Object>) run.invoke(audit);
        var field = type.getDeclaredField("grids");
        field.setAccessible(true);
        var grids = (List<Map<GridPosition, Set<Integer>>>) field.get(audit);
        int[] widths = {20, 40, 250, 1000};
        int[] ranges = {4, 1, 1, 0}; // config vision; entity-error prevention, 500m cap
        var nearby = new TreeSet<Integer>();
        var byLayer = new TreeMap<Integer, Set<Integer>>();
        for (int layer = 0; layer < widths.length; layer++) {
            var selected = new TreeSet<Integer>();
            if (widths[layer] <= 500) {
                int x = (int) (2747.0 / widths[layer]);
                int z = (int) (-1719.0 / widths[layer]);
                int query = Math.min(ranges[layer] + 1, 500 / widths[layer] - 1);
                var entries = new ArrayList<>(grids.get(layer).entrySet());
                var index = SceneIndexManager.buildIndex(2, entries, e -> e.getKey().toPoint());
                var queried = new TreeSet<Integer>();
                SceneIndexManager.queryNeighbors(index, new double[] {x, z}, query)
                        .forEach(e -> queried.addAll(e.getValue()));
                for (var e : entries) {
                    var p = e.getKey();
                    if (Math.abs(p.getX() - x) <= query && Math.abs(p.getZ() - z) <= query)
                        selected.addAll(e.getValue());
                }
                if (!selected.equals(queried)) throw new IllegalStateException("Index and rectangle disagree");
            }
            byLayer.put(layer, selected);
            nearby.addAll(selected);
        }
        report.put("modelPosition", List.of(2747, 194, -1719));
        report.put("nearbyGroupsByLayer", byLayer);
        report.put("nearbyGroups", nearby);
        report.put("queryScope", "Reviewed inline serializer grid model; not runtime scene or trigger execution");
        Files.writeString(Path.of(args[3]), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}

/** Invoke the named Lua action only until its first real ScriptLib call. */
final class Client72D02TriggerProbe {
    public static void main(String[] args) throws Exception {
        var config = emu.grasscutter.Grasscutter.getConfig();
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (!emu.grasscutter.Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json"))
                || !Path.of(config.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data")))
            throw new IllegalStateException("Isolated config/data required");
        emu.grasscutter.scripts.ScriptLoader.init();
        var boundary = Client72ProductionSceneLoadAudit.class.getDeclaredMethod("installBoundary");
        boundary.setAccessible(true);
        boundary.invoke(null);
        var group = emu.grasscutter.scripts.data.SceneGroup.of(133003505);
        group.load(3);
        var trigger = group.triggers.get("GROUP_LOAD_505010");
        if (trigger == null || !group.suites.get(0).sceneTriggers.contains(trigger))
            throw new IllegalStateException("Initial suite trigger not bound");
        var fn = (org.luaj.vm2.LuaValue) group.getBindings().get(trigger.getAction());
        try {
            fn.call(emu.grasscutter.scripts.ScriptLoader.getScriptLibLua(), org.luaj.vm2.LuaValue.NIL);
            throw new IllegalStateException("Expected first business-call boundary");
        } catch (org.luaj.vm2.LuaError expected) {
            if (!expected.getMessage().contains("AUDIT_BUSINESS_CALL_BLOCKED: GetGadgetStateByConfigId")) throw expected;
        }
        System.out.println("initial-suite-bound; direct-action-blocked-at=GetGadgetStateByConfigId");
    }
}
