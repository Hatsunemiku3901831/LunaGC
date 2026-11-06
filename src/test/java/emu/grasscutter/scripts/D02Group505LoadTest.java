package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.World;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Real Lua bridge and scene state, using a non-persistent scene and isolated data directory. */
final class D02Group505LoadTest {
    private Scene scene;
    private SceneScriptManager manager;
    private EntityGadget otherGroupGadget;
    private static ConfigGlobalCombat previousCombat;
    private static Map<Integer, DropTableData> previousDrops;
    private static Map<Integer, GadgetData> previousGadgets;
    private static ServerRunMode previousRunModeOverride;
    private static boolean previousScriptEnabled;

    @BeforeAll
    static void initializeLoader() throws Exception {
        previousCombat = GameData.getConfigGlobalCombat();
        previousDrops = new HashMap<>(GameData.getDropTableDataMap());
        previousGadgets = new HashMap<>(GameData.getGadgetDataMap());
        previousScriptEnabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        var runMode = Grasscutter.class.getDeclaredField("runModeOverride");
        runMode.setAccessible(true);
        previousRunModeOverride = (ServerRunMode) runMode.get(null);
        assertNull(ScriptLoader.getEngine(), "Run in a fresh isolated test JVM");
        ScriptLoader.init();
    }

    @AfterAll
    static void restoreGlobalState() {
        GameData.setConfigGlobalCombat(previousCombat);
        GameData.getDropTableDataMap().clear();
        GameData.getDropTableDataMap().putAll(previousDrops);
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(previousGadgets);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = previousScriptEnabled;
        Grasscutter.setRunModeOverride(previousRunModeOverride);
        // ScriptLoader and its caches are process-wide; the isolated JVM owns their lifetime.
    }

    @BeforeEach
    void setup() throws Exception {
        var resourceRoot = Path.of(System.getProperty("d02.resources"));
        var gadgetFile = Path.of(System.getProperty("d02.gadgetTable"));
        assertEquals("8df671f4adc438e6277f3d716d80b64904ebc35715305b4779ec089fa744d3ba",
                sha(resourceRoot.resolve("Scripts/Scene/3/scene3_group133003505.lua")));
        assertEquals("5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411",
                sha(gadgetFile));
        var combatFile = resourceRoot.resolve("BinOutput/Common/ConfigGlobalCombat.json");
        assertEquals("df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0",
                sha(combatFile));
        var work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        assertEquals(work.resolve("config.json"), Grasscutter.configFile.getCanonicalFile().toPath());
        assertEquals(work.resolve("data"), Path.of(config.folderStructure.data).toAbsolutePath().normalize());
        assertEquals(resourceRoot.resolve("Scripts").toString(), config.folderStructure.scripts);

        var rows = JsonUtils.loadToClass(gadgetFile, JsonElement.class).getAsJsonArray();
        for (var element : rows) {
            int id = element.getAsJsonObject().get("id").getAsInt();
            if (id == 70360041 || id == 70211131) {
                var data = JsonUtils.decode(element, GadgetData.class);
                GameData.getGadgetDataMap().put(id, data);
            }
        }
        assertTrue(GameData.getGadgetDataMap().containsKey(70360041));
        assertTrue(GameData.getGadgetDataMap().containsKey(70211131));
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(combatFile, ConfigGlobalCombat.class));
        var dropFile = resourceRoot.resolve("Server/DropTableExcelConfigData.json");
        assertEquals("25baed72a5fe12705dcf870d0c705819f6ca7cc7f7694563fc015fd65634f940",
                sha(dropFile));
        for (var row : JsonUtils.loadToList(dropFile, DropTableData.class))
            GameData.getDropTableDataMap().put(row.getId(), row);
        assertFalse(GameData.getDropTableDataMap().containsKey(0));
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        config.server.game.enableScriptInBigWorld = false;
        var server = new GameServer();
        var worlds = GameServer.class.getDeclaredField("worlds");
        worlds.setAccessible(true);
        worlds.set(server, new HashSet<World>());
        var drops = GameServer.class.getDeclaredField("dropSystem");
        drops.setAccessible(true);
        drops.set(server, new DropSystem(server));
        var owner = new Player();
        owner.setUid(10001);
        var world = new World(server, owner);
        var data = JsonUtils.decode(com.google.gson.JsonParser.parseString(
                "{\"id\":3,\"type\":\"SCENE_WORLD\"}"), SceneData.class);
        scene = new Scene(world, data, false);
        manager = scene.getScriptManager();
        var group = SceneGroup.of(133003505);
        group.block_id = 3003;
        manager.loadGroupFromScript(group);
        assertNotNull(group.suites);
        var instance = manager.getGroupInstanceById(group.id);
        assertNotNull(instance);
        assertTrue(group.suites.get(0).sceneTriggers.stream()
                .anyMatch(trigger -> trigger.getName().equals("GROUP_LOAD_505010")));
        manager.addGroupSuite(instance, group.suites.get(0));
        for (int id : new int[] {505001, 505002, 505003, 505004}) assertNotNull(gadget(id));
        // Counterfactual entity: same config ID in a different group, not a claim about shipped Lua.
        otherGroupGadget = new EntityGadget(scene, 70360041, new Position(0, 0, 0));
        otherGroupGadget.setConfigId(505001);
        otherGroupGadget.setGroupId(133003506);
        otherGroupGadget.setState(ScriptGadgetState.GearStart);
        scene.addEntity(otherGroupGadget);
    }

    @AfterEach
    void cleanup() {
        if (manager != null) manager.onDestroy();
    }

    @Test
    void groupLoadUsesRealGadgetStateToOpenOnlyMatchingChestAndRemoveDoor() throws Exception {
        gadget(505001).setState(ScriptGadgetState.GearStart);
        gadget(505002).setState(ScriptGadgetState.Default);
        assertEquals(ScriptGadgetState.ChestLocked, gadget(505003).getState());
        assertEquals(ScriptGadgetState.ChestLocked, gadget(505004).getState());
        assertSame(otherGroupGadget, scene.getEntityByConfigId(505001, 133003506));
        assertEquals(ScriptGadgetState.GearStart, otherGroupGadget.getState());
        manager.callEvent(new ScriptArgs(133003505, EventType.EVENT_GROUP_LOAD, 133003505))
                .get(10, TimeUnit.SECONDS);
        assertNull(gadget(505001));
        assertNotNull(gadget(505002));
        assertEquals(ScriptGadgetState.Default, gadget(505003).getState());
        assertEquals(ScriptGadgetState.ChestLocked, gadget(505004).getState());
        assertSame(otherGroupGadget, scene.getEntityByConfigId(505001, 133003506));
        assertEquals(ScriptGadgetState.GearStart, otherGroupGadget.getState());
        var group = manager.getGroupInstanceById(133003505);
        assertTrue(group.isScriptGadgetDestroyed(505001));
        assertFalse(group.isScriptGadgetDestroyed(505002));
        manager.callEvent(new ScriptArgs(133003505, EventType.EVENT_GROUP_LOAD, 133003505))
                .get(10, TimeUnit.SECONDS);
        assertNull(gadget(505001));
        assertNotNull(gadget(505002));
        assertEquals(ScriptGadgetState.Default, gadget(505003).getState());
        assertEquals(ScriptGadgetState.ChestLocked, gadget(505004).getState());
    }

    @Test
    void missingDoorTakesMinusOneBranchAndRecordsRemoval() throws Exception {
        scene.removeEntity(gadget(505001));
        gadget(505002).setState(ScriptGadgetState.Default);
        manager.callEvent(new ScriptArgs(133003505, EventType.EVENT_GROUP_LOAD, 133003505))
                .get(10, TimeUnit.SECONDS);
        assertEquals(ScriptGadgetState.Default, gadget(505003).getState());
        assertEquals(ScriptGadgetState.ChestLocked, gadget(505004).getState());
        assertTrue(manager.getGroupInstanceById(133003505).isScriptGadgetDestroyed(505001));
    }

    private EntityGadget gadget(int configId) {
        return (EntityGadget) scene.getEntityByConfigId(configId, 133003505);
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
