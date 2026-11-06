package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.JsonElement;
import emu.grasscutter.DebugConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.GatherData;
import emu.grasscutter.data.excels.monster.MonsterCurveData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Fixed-position production group selection/load, no persistence or network. */
final class D02BirthInitialEntitiesTest {
    private static Map<Integer, GadgetData> gadgets;
    private static Map<Integer, MonsterData> monsters;
    private static Map<Integer, MonsterCurveData> curves;
    private static Map<Integer, DropTableData> drops;
    private static Map<Integer, GatherData> gather;
    private static ConfigGlobalCombat combat;
    private static Grasscutter.ServerRunMode runMode;
    private static boolean scripts, missingLog;
    private static final List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());
    private static AppenderBase<ILoggingEvent> appender;

    @BeforeAll
    static void setup() throws Exception {
        var root = Path.of(System.getProperty("d02.resources"));
        var excel = Path.of(System.getProperty("d02.excel"));
        var work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        assertEquals(work.resolve("config.json"), Grasscutter.configFile.getCanonicalFile().toPath());
        assertEquals(work.resolve("data"), Path.of(config.folderStructure.data).toAbsolutePath().normalize());
        assertEquals(work.resolve("cache"), Path.of(config.folderStructure.cache).toAbsolutePath().normalize());
        assertNull(Grasscutter.getPluginManager());
        requireHash(excel.resolve("GadgetExcelConfigData.json"), "5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411");
        requireHash(excel.resolve("MonsterExcelConfigData.json"), "b0efcdb6bca52eca8c2b89fe2e43f8f5a65bc1596fd250e7ddc375acd92989cc");
        requireHash(excel.resolve("MonsterCurveExcelConfigData.json"), "3014983fd6292b612c040fb4106d6bfe10aef2da0ceffcba01bd10872b832f84");
        requireHash(excel.resolve("SceneExcelConfigData.json"), "5a906393d1de46a6d397ae169cdcfb76a9ee77fc1c9e38dd3aec752656978550");
        requireHash(excel.resolve("GatherExcelConfigData.json"), "c805f2ebde4bdd37de5ecd612e8ddf4e30f193f78876b35fdd30790bbb81cbb0");
        requireHash(root.resolve("BinOutput/Common/ConfigGlobalCombat.json"), "df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0");
        requireHash(root.resolve("Server/DropTableExcelConfigData.json"), "25baed72a5fe12705dcf870d0c705819f6ca7cc7f7694563fc015fd65634f940");
        requireHash(root.resolve("Scripts/Scene/3/scene3.lua"), "d547a2ebeb1292358685d8f317bf6b5676a53327e42aa618f18ef361cd32a9f9");
        gadgets = new HashMap<>(GameData.getGadgetDataMap());
        monsters = new HashMap<>(GameData.getMonsterDataMap());
        curves = new HashMap<>(GameData.getMonsterCurveDataMap());
        drops = new HashMap<>(GameData.getDropTableDataMap());
        gather = new HashMap<>(GameData.getGatherDataMap());
        combat = GameData.getConfigGlobalCombat();
        scripts = config.server.game.enableScriptInBigWorld;
        missingLog = DebugConstants.LOG_MISSING_LUA_SCRIPTS;
        var field = Grasscutter.class.getDeclaredField("runModeOverride");
        field.setAccessible(true);
        runMode = (Grasscutter.ServerRunMode) field.get(null);
        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                if (event.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(event.getFormattedMessage());
            }
        };
        appender.setContext(Grasscutter.getLogger().getLoggerContext());
        appender.start();
        Grasscutter.getLogger().addAppender(appender);
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(root.resolve("BinOutput/Common/ConfigGlobalCombat.json"), ConfigGlobalCombat.class));
        load(GadgetData.class, excel.resolve("GadgetExcelConfigData.json"), GameData.getGadgetDataMap());
        load(MonsterData.class, excel.resolve("MonsterExcelConfigData.json"), GameData.getMonsterDataMap());
        load(MonsterCurveData.class, excel.resolve("MonsterCurveExcelConfigData.json"), GameData.getMonsterCurveDataMap());
        load(GatherData.class, excel.resolve("GatherExcelConfigData.json"), GameData.getGatherDataMap());
        load(DropTableData.class, root.resolve("Server/DropTableExcelConfigData.json"), GameData.getDropTableDataMap());
        assertNull(ScriptLoader.getEngine());
        DebugConstants.LOG_MISSING_LUA_SCRIPTS = true;
        ScriptLoader.init();
        config.server.game.enableScriptInBigWorld = true;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
    }

    private static void requireHash(Path path, String expected) throws Exception {
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))), path.toString());
    }

    private static void load(Class<?> type, Path path, Int2ObjectMap<?> map) throws Exception {
        var method = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        method.setAccessible(true);
        method.invoke(null, type, path, map);
    }

    @AfterAll
    static void cleanup() {
        if (appender != null) Grasscutter.getLogger().detachAppender(appender);
        if (gadgets != null) { GameData.getGadgetDataMap().clear(); GameData.getGadgetDataMap().putAll(gadgets); }
        if (monsters != null) { GameData.getMonsterDataMap().clear(); GameData.getMonsterDataMap().putAll(monsters); }
        if (curves != null) { GameData.getMonsterCurveDataMap().clear(); GameData.getMonsterCurveDataMap().putAll(curves); }
        if (drops != null) { GameData.getDropTableDataMap().clear(); GameData.getDropTableDataMap().putAll(drops); }
        if (gather != null) { GameData.getGatherDataMap().clear(); GameData.getGatherDataMap().putAll(gather); }
        GameData.setConfigGlobalCombat(combat);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
        DebugConstants.LOG_MISSING_LUA_SCRIPTS = missingLog;
        Grasscutter.setRunModeOverride(runMode);
    }

    static final class BoundaryScene extends Scene {
        final List<BasePacket> packets = new java.util.concurrent.CopyOnWriteArrayList<>();
        BoundaryScene(World world, SceneData data) { super(world, data, false); }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
    }

    @Test
    void birthSelectionAndActualInitialSuites() throws Exception {
        var root = Path.of(System.getProperty("d02.resources"));
        var excel = Path.of(System.getProperty("d02.excel"));
        var server = new GameServer();
        var worlds = GameServer.class.getDeclaredField("worlds"); worlds.setAccessible(true); worlds.set(server, new HashSet<World>());
        var dropSystem = GameServer.class.getDeclaredField("dropSystem"); dropSystem.setAccessible(true); dropSystem.set(server, new DropSystem(server));
        var player = new Player(); player.setUid(10001); player.getPosition().set(2747, 194, -1719);
        var team = Player.class.getDeclaredField("teamManager"); team.setAccessible(true); team.set(player, new TeamManager(player));
        var world = new World(server, player);
        var rows = JsonUtils.loadToClass(excel.resolve("SceneExcelConfigData.json"), JsonElement.class).getAsJsonArray();
        SceneData sceneData = null;
        for (var row : rows) if (row.getAsJsonObject().get("id").getAsInt() == 3) sceneData = JsonUtils.decode(row, SceneData.class);
        assertNotNull(sceneData);
        var scene = new BoundaryScene(world, sceneData);
        try {
            var manager = scene.getScriptManager();
            var ready = new CompletableFuture<Boolean>(); manager.whenInitialized(ready::complete);
            assertTrue(ready.get(30, TimeUnit.SECONDS));
            var selected = new TreeSet<>(scene.getPlayerActiveGroups(player));
            System.out.println("D02_BIRTH selected=" + selected + " gridErrors=" + errors.size());
            assertEquals(25, selected.size());
            scene.getPlayers().add(player);
            scene.checkGroups();
            System.out.println("D02_BIRTH load returned packets=" + scene.packets.size() + " errors=" + errors.size());
            var worktopEntity = assertInstanceOf(EntityGadget.class, scene.getEntityByConfigId(601002, 133003601), "missing 601002");
            var worktop = assertInstanceOf(GadgetWorktop.class, worktopEntity.getContent());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!worktop.getWorktopOptions().contains(173) && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(worktop.getWorktopOptions().contains(173), "601 create action did not expose option 173");
            System.out.println("D02_BIRTH 601 option 173 observed after real event");
            for (int id : selected) {
                var instance = manager.getGroupInstanceById(id);
                assertNotNull(instance, "missing group " + id);
                var group = instance.getLuaGroup();
                var suite = group.getSuiteByIndex(instance.getActiveSuiteId());
                assertNotNull(suite, "missing active suite " + id);
                assertEquals(suite.monsters.size(), suite.sceneMonsters.size(), "unresolved monsters " + id);
                assertEquals(suite.gadgets.size(), suite.sceneGadgets.size(), "unresolved gadgets " + id);
                System.out.println("D02_BIRTH group=" + id + " activeSuite=" + instance.getActiveSuiteId() + " npc=" + group.npcs.keySet() + " triggers=" + suite.sceneTriggers.stream().map(t -> t.getName()+":"+t.getEvent()).toList());
                for (var m : suite.sceneMonsters) {
                    var entity = assertInstanceOf(EntityMonster.class, scene.getEntityByConfigId(m.config_id, id), "monster " + id + "/" + m.config_id + "/" + m.monster_id);
                    assertEquals(m.monster_id, entity.getMonsterData().getId());
                }
                for (var g : suite.sceneGadgets) {
                    var entity = assertInstanceOf(EntityGadget.class, scene.getEntityByConfigId(g.config_id, id), "gadget " + id + "/" + g.config_id + "/" + g.gadget_id);
                    assertEquals(g.gadget_id, entity.getGadgetData().getId());
                }
            }
            assertTrue(errors.isEmpty(), "loader/trigger errors: " + errors.size() + " first=" + (errors.isEmpty() ? "none" : errors.get(0)));
        } finally { scene.getScriptManager().onDestroy(); }
    }
}
