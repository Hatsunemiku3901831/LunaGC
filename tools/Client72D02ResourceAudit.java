import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.*;
import com.google.gson.GsonBuilder;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.GridPosition;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

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

final class Client72D02RealGridAudit {
 public static void main(String[] a) throws Exception {
  Path work=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); var c=Grasscutter.getConfig();
  if(!Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json"))||!Path.of(c.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data"))||!Path.of(c.folderStructure.cache).toAbsolutePath().normalize().equals(work.resolve("cache")))throw new IllegalStateException("isolation");
  if(Grasscutter.getPluginManager()!=null)throw new IllegalStateException("plugins present");
  var errors=new ArrayList<String>();var appender=new AppenderBase<ILoggingEvent>(){protected void append(ILoggingEvent e){if(e.getLevel().isGreaterOrEqual(Level.ERROR))synchronized(errors){errors.add(e.getFormattedMessage());}}};appender.setContext(Grasscutter.getLogger().getLoggerContext());appender.start();Grasscutter.getLogger().addAppender(appender);
  GameData.setConfigGlobalCombat(JsonUtils.loadToClass(Path.of(a[0]),ConfigGlobalCombat.class));
  var load=ResourceLoader.class.getDeclaredMethod("loadFromResource",Class.class,Path.class,it.unimi.dsi.fastutil.ints.Int2ObjectMap.class);load.setAccessible(true);load.invoke(null,GadgetData.class,Path.of(a[1]),GameData.getGadgetDataMap());
  var scenes=JsonUtils.loadToClass(Path.of(a[2]),JsonElement.class).getAsJsonArray();SceneData data=null;for(var row:scenes){if(row.getAsJsonObject().get("id").getAsInt()==3)data=JsonUtils.decode(row,SceneData.class);}if(data==null)throw new IllegalStateException("scene3 missing");
  emu.grasscutter.DebugConstants.LOG_MISSING_LUA_SCRIPTS=true;ScriptLoader.init();var boundary=Client72ProductionSceneLoadAudit.class.getDeclaredMethod("installBoundary");boundary.setAccessible(true);boundary.invoke(null);
  Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);var server=new GameServer();var worlds=GameServer.class.getDeclaredField("worlds");worlds.setAccessible(true);worlds.set(server,new HashSet<World>());var player=new Player();player.setUid(10001);var world=new World(server,player);var scene=new Scene(world,data,false);var manager=scene.getScriptManager();var ready=new CompletableFuture<Boolean>();manager.whenInitialized(ready::complete);if(!ready.get(30,TimeUnit.SECONDS))throw new IllegalStateException("scene meta init failed");
  var grids=manager.getGroupGrids();var groups=new TreeSet<>(scene.getPlayerActiveGroups(player));var counts=new ArrayList<Integer>();for(var g:grids)counts.add(g.getGrid().size());
  var field=Client72ProductionSceneLoadAudit.class.getDeclaredField("attempts");field.setAccessible(true);var attempts=(List<?>)field.get(null);
  var selected=new TreeMap<Integer,Object>();var snapshot=Client72ProductionSceneLoadAudit.class.getDeclaredMethod("snapshot",emu.grasscutter.scripts.data.SceneGroup.class);snapshot.setAccessible(true);
  for(var block:manager.getBlocks().values())for(var group:block.groups.values())if(groups.contains(group.id)||group.id==133106026){if(!group.isLoaded())throw new IllegalStateException("expected loaded group "+group.id);selected.put(group.id,snapshot.invoke(null,group));}
  if(selected.size()!=26)throw new IllegalStateException("expected 26 snapshot groups: "+selected.size());
  var result=new TreeMap<String,Object>();result.put("groupSnapshots",selected);result.put("scene",3);result.put("position",List.of(2747,194,-1719));result.put("groupIds",groups);result.put("gridCells",counts);result.put("errors",errors);result.put("businessAttempts",attempts);result.put("gadgetRows",GameData.getGadgetDataMap().size());result.put("fastRequire",c.server.fastRequire);result.put("fastRequireEffective",emu.grasscutter.config.Configuration.FAST_REQUIRE);result.put("missingScripts",ScriptLoader.getMissingScripts());result.put("resourceReady",false);result.put("scope","actual SceneScriptManager.getGroupGrids and Scene.getPlayerActiveGroups in isolated nonpersistent scene; no scene tick or trigger execution");
  Files.writeString(Path.of(a[3]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");System.out.println("groups="+groups.size()+" gridCells="+counts+" errors="+errors.size()+" attempts="+attempts.size());manager.onDestroy();Grasscutter.getLogger().detachAppender(appender);
 }
}

/** Actual selected group deserialization and initial-suite inspection, without triggering actions. */
final class Client72D02SuiteAudit {
 public static void main(String[] args) throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("output required");
  var work=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();var config=Grasscutter.getConfig();
  if(!Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json"))||!Path.of(config.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data")))throw new IllegalStateException("isolated config/data required");
  var errors=new ArrayList<String>();var appender=new AppenderBase<ILoggingEvent>(){protected void append(ILoggingEvent e){if(e.getLevel().isGreaterOrEqual(Level.ERROR))errors.add(e.getFormattedMessage());}};appender.setContext(Grasscutter.getLogger().getLoggerContext());appender.start();Grasscutter.getLogger().addAppender(appender);
  emu.grasscutter.DebugConstants.LOG_MISSING_LUA_SCRIPTS=true;ScriptLoader.init();var boundary=Client72ProductionSceneLoadAudit.class.getDeclaredMethod("installBoundary");boundary.setAccessible(true);boundary.invoke(null);
  int[] ids={133003022,133003024,133003025,133003032,133003105,133003128,133003275,133003288,133003289,133003313,133003317,133003332,133003333,133003426,133003429,133003504,133003505,133003601,133003785,133003801,133003802,133003803,133003804,133003807,133003901};
  var rows=new ArrayList<Map<String,Object>>();var start=new Position(2747,194,-1719);
  for(int id:ids){
   var group=emu.grasscutter.scripts.data.SceneGroup.of(id);group.load(3);
   if(group.init_config==null||group.suites==null)throw new IllegalStateException("missing group data "+id);
   var row=new TreeMap<String,Object>();row.put("id",id);row.put("randSuite",group.init_config.rand_suite);row.put("ioType",group.init_config.io_type);row.put("configuredSuite",group.init_config.suite);var suites=new ArrayList<Map<String,Object>>();
   for(int i=0;i<group.suites.size();i++){
    var suite=group.suites.get(i);var sr=new TreeMap<String,Object>();sr.put("index",i+1);sr.put("randomWeight",suite.rand_weight);
    boolean randomized=group.init_config.io_type!=1&&group.init_config.rand_suite&&group.suites.size()>1;
    // findInitSuiteIndex(0) returns the zero-based selected index as a one-based suite ID.
    boolean possible=randomized ? i+1<group.suites.size()&&group.suites.get(i+1).rand_weight>0 : group.init_config.suite==i+1;
    sr.put("initialCandidate",possible);
    var triggers=new ArrayList<Map<String,Object>>();for(var t:suite.sceneTriggers){var tr=new TreeMap<String,Object>();tr.put("name",t.getName());tr.put("event",t.getEvent());tr.put("condition",t.getCondition());tr.put("action",t.getAction());triggers.add(tr);}sr.put("triggers",triggers);
    var regions=new ArrayList<Map<String,Object>>();for(var region:suite.sceneRegions){var rr=new TreeMap<String,Object>();rr.put("id",region.config_id);rr.put("shape",region.shape);try{rr.put("containsStart",region.contains(start));}catch(RuntimeException e){rr.put("containsStart","unknown:"+e.getClass().getSimpleName());}regions.add(rr);}sr.put("regions",regions);sr.put("gadgets",suite.gadgets);sr.put("monsters",suite.monsters);suites.add(sr);
   }
   row.put("suites",suites);rows.add(row);
  }
  var field=Client72ProductionSceneLoadAudit.class.getDeclaredField("attempts");field.setAccessible(true);var attempts=(List<?>)field.get(null);
  var result=new TreeMap<String,Object>();result.put("groups",rows);result.put("errors",errors);result.put("businessAttempts",attempts);result.put("fastRequire",emu.grasscutter.config.Configuration.FAST_REQUIRE);result.put("scope","direct group load and 3D region contains at fixed start; no event dispatch, persisted suites, or dynamic quest groups");result.put("resourceReady",false);
  Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");if(!errors.isEmpty()||!attempts.isEmpty())throw new IllegalStateException("group load error or business attempt");
  Grasscutter.getLogger().detachAppender(appender);System.out.println("groups="+rows.size()+" errors="+errors.size()+" attempts="+attempts.size());
 }
}
