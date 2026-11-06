package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/** Production automatic 505 dispatch, no persistence, manual events or network. */
final class D02Birth505AutoEventTest {
    private static Map<Integer, GadgetData> gadgets;
    private static Map<Integer, DropTableData> drops;
    private static ConfigGlobalCombat combat;
    private static Grasscutter.ServerRunMode runMode;
    private static boolean scripts;
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
        requireHash(root.resolve("Scripts/Scene/3/scene3.lua"), "d547a2ebeb1292358685d8f317bf6b5676a53327e42aa618f18ef361cd32a9f9");
        requireHash(root.resolve("Scripts/Scene/3/scene3_block3003.lua"), "40732841be0e294fe02d80131a5436a6a9ba1ebe78eb5b8468e8a9e500ab9676");
        requireHash(root.resolve("Scripts/Scene/3/scene3_group133003505.lua"), "8df671f4adc438e6277f3d716d80b64904ebc35715305b4779ec089fa744d3ba");
        requireHash(root.resolve("BinOutput/Common/ConfigGlobalCombat.json"), "df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0");
        requireHash(root.resolve("Server/DropTableExcelConfigData.json"), "25baed72a5fe12705dcf870d0c705819f6ca7cc7f7694563fc015fd65634f940");
        requireHash(excel.resolve("GadgetExcelConfigData.json"), "5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411");
        gadgets = new HashMap<>(GameData.getGadgetDataMap());
        drops = new HashMap<>(GameData.getDropTableDataMap());
        combat = GameData.getConfigGlobalCombat();
        scripts = config.server.game.enableScriptInBigWorld;
        var mode = Grasscutter.class.getDeclaredField("runModeOverride"); mode.setAccessible(true);
        runMode = (Grasscutter.ServerRunMode) mode.get(null);
        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent e) { if (e.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(e.getFormattedMessage()); }
        };
        appender.setContext(Grasscutter.getLogger().getLoggerContext()); appender.start(); Grasscutter.getLogger().addAppender(appender);
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(root.resolve("BinOutput/Common/ConfigGlobalCombat.json"), ConfigGlobalCombat.class));
        load(GadgetData.class, excel.resolve("GadgetExcelConfigData.json"), GameData.getGadgetDataMap());
        load(DropTableData.class, root.resolve("Server/DropTableExcelConfigData.json"), GameData.getDropTableDataMap());
        assertNull(ScriptLoader.getEngine()); ScriptLoader.init();
        config.server.game.enableScriptInBigWorld = true;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
    }

    private static void requireHash(Path path, String expected) throws Exception {
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
    }

    private static void load(Class<?> type, Path path, Int2ObjectMap<?> map) throws Exception {
        var method = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        method.setAccessible(true); method.invoke(null, type, path, map);
    }

    @AfterAll
    static void cleanup() {
        if (appender != null) Grasscutter.getLogger().detachAppender(appender);
        if (gadgets != null) { GameData.getGadgetDataMap().clear(); GameData.getGadgetDataMap().putAll(gadgets); }
        if (drops != null) { GameData.getDropTableDataMap().clear(); GameData.getDropTableDataMap().putAll(drops); }
        GameData.setConfigGlobalCombat(combat); Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
        Grasscutter.setRunModeOverride(runMode);
    }

    @Test
    void automaticLoadAndCreationCompleteWithNaturalDefaultStates() throws Exception {
        var server = new GameServer();
        var worlds = GameServer.class.getDeclaredField("worlds"); worlds.setAccessible(true); worlds.set(server, new HashSet<World>());
        var dropsField = GameServer.class.getDeclaredField("dropSystem"); dropsField.setAccessible(true); dropsField.set(server, new DropSystem(server));
        var owner = new Player(); owner.setUid(10001);
        var world = new World(server, owner);
        var sceneData = JsonUtils.decode(JsonParser.parseString("{\"id\":3,\"type\":\"SCENE_WORLD\"}"), SceneData.class);
        var scene = new Scene(world, sceneData, false);
        try {
            var manager = scene.getScriptManager();
            var ready = new CompletableFuture<Boolean>(); manager.whenInitialized(ready::complete);
            assertTrue(ready.get(30, TimeUnit.SECONDS));
            var group = manager.findGroupById(133003505);
            assertNotNull(group);
            scene.onLoadGroup(List.of(group));
            var counterField = SceneScriptManager.class.getDeclaredField("triggerInvocations");
            counterField.setAccessible(true);
            @SuppressWarnings("unchecked") var counters = (Map<String, AtomicInteger>) counterField.get(manager);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((count(counters, "GROUP_LOAD_505010") < 1 || count(counters, "GADGET_CREATE_505007") < 4)
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, count(counters, "GROUP_LOAD_505010"), "automatic group load completion");
            assertEquals(4, count(counters, "GADGET_CREATE_505007"), "automatic gadget creation completions");
            for (int id : new int[] {505001, 505002}) assertEquals(ScriptGadgetState.Default, gadget(scene, id).getState());
            for (int id : new int[] {505003, 505004}) assertEquals(ScriptGadgetState.ChestLocked, gadget(scene, id).getState());
            assertTrue(errors.isEmpty(), "real event/loader errors: " + errors);
            System.out.println("D02_505_AUTO groupLoad=1 gadgetCreate=4 doors=Default chests=ChestLocked errors=0");
        } finally { scene.getScriptManager().onDestroy(); }
    }

    private static int count(Map<String, AtomicInteger> counters, String name) {
        var counter = counters.get(name); return counter == null ? 0 : counter.get();
    }

    private static EntityGadget gadget(Scene scene, int id) {
        return assertInstanceOf(EntityGadget.class, scene.getEntityByConfigId(id, 133003505), "gadget " + id);
    }
}
