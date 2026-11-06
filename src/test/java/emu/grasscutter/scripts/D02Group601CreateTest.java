package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.WorktopOptionNotifyOuterClass.WorktopOptionNotify;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Actual scene and Lua calls with an isolated, non-persistent group. */
final class D02Group601CreateTest {
    private static ConfigGlobalCombat previousCombat;
    private static Map<Integer, GadgetData> previousGadgets;
    private static ServerRunMode previousRunModeOverride;
    private static boolean previousScriptEnabled;
    private RecordingScene scene;
    private SceneScriptManager manager;
    private SceneGroup group;

    @BeforeAll
    static void initialize() throws Exception {
        previousCombat = GameData.getConfigGlobalCombat();
        previousGadgets = new HashMap<>(GameData.getGadgetDataMap());
        previousScriptEnabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        var mode = Grasscutter.class.getDeclaredField("runModeOverride");
        mode.setAccessible(true);
        previousRunModeOverride = (ServerRunMode) mode.get(null);
        assertNull(ScriptLoader.getEngine(), "Run in a fresh isolated test JVM");
        ScriptLoader.init();
    }

    @AfterAll
    static void restore() {
        GameData.setConfigGlobalCombat(previousCombat);
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(previousGadgets);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = previousScriptEnabled;
        Grasscutter.setRunModeOverride(previousRunModeOverride);
        // ScriptLoader caches remain in this test JVM and end with the process.
    }

    @BeforeEach
    void setup() throws Exception {
        var resources = Path.of(System.getProperty("d02.resources"));
        var gadgetFile = Path.of(System.getProperty("d02.gadgetTable"));
        assertEquals("f7c117fa210345e73ffab39d3225f740fcedf27f8dd9beaf4e0db2191e1abc0e",
                sha(resources.resolve("Scripts/Scene/3/scene3_group133003601.lua")));
        assertEquals("5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411", sha(gadgetFile));
        var combat = resources.resolve("BinOutput/Common/ConfigGlobalCombat.json");
        assertEquals("df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0", sha(combat));
        var work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        assertEquals(work.resolve("config.json"), Grasscutter.configFile.getCanonicalFile().toPath());
        assertEquals(work.resolve("data"), Path.of(config.folderStructure.data).toAbsolutePath().normalize());
        assertEquals(resources.resolve("Scripts").toString(), config.folderStructure.scripts);
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(combat, ConfigGlobalCombat.class));
        for (var row : JsonUtils.loadToClass(gadgetFile, JsonElement.class).getAsJsonArray()) {
            if (row.getAsJsonObject().get("id").getAsInt() == 70510013)
                GameData.getGadgetDataMap().put(70510013, JsonUtils.decode(row, GadgetData.class));
        }
        assertNotNull(GameData.getGadgetDataMap().get(70510013));
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        config.server.game.enableScriptInBigWorld = false;
        var server = new GameServer();
        var worlds = GameServer.class.getDeclaredField("worlds");
        worlds.setAccessible(true);
        worlds.set(server, new HashSet<World>());
        var owner = new Player();
        owner.setUid(10001);
        var world = new World(server, owner);
        var sceneData = JsonUtils.decode(com.google.gson.JsonParser.parseString(
                "{\"id\":3,\"type\":\"SCENE_WORLD\"}"), SceneData.class);
        scene = new RecordingScene(world, sceneData);
        manager = scene.getScriptManager();
        group = SceneGroup.of(133003601);
        group.block_id = 3003;
        manager.loadGroupFromScript(group);
        assertNotNull(manager.getGroupInstanceById(group.id));
        assertTrue(group.suites.get(0).sceneTriggers.stream()
                .anyMatch(t -> t.getName().equals("GADGET_CREATE_601005")));
    }

    @AfterEach
    void cleanup() {
        if (manager != null) manager.onDestroy();
    }

    @Test
    void creationDispatchesRealActionAndBroadcastsWorktopOption() throws Exception {
        var other = new EntityGadget(scene, 70510013, new Position(0, 0, 0));
        other.setGroupId(133003602);
        other.setConfigId(601002);
        other.buildContent();
        scene.getEntities().put(other.getId(), other); // Counterfactual other group, not shipped Lua.
        manager.addGroupSuite(manager.getGroupInstanceById(group.id), group.suites.get(0));
        assertTrue(scene.worktopBroadcast.await(10, TimeUnit.SECONDS), "onCreate event did not broadcast");
        var gadget = (EntityGadget) scene.getEntityByConfigId(601002, group.id);
        assertNotNull(gadget);
        assertEquals(ScriptGadgetState.Default, gadget.getState());
        assertEquals(java.util.Set.of(173), ((GadgetWorktop) gadget.getContent()).getWorktopOptions());
        assertTrue(((GadgetWorktop) other.getContent()).getWorktopOptions().isEmpty());
        var packet = scene.worktopPackets.get(0);
        assertEquals(799, packet.getOpcode()); // Current sender, not a verified CN 7.2 identity.
        var payload = WorktopOptionNotify.parseFrom(packet.getData());
        assertEquals(gadget.getId(), payload.getGadgetEntityId());
        assertEquals(java.util.List.of(173), payload.getOptionListList());
    }

    @Test
    void nonDefaultStateDoesNotSetOptionOrBroadcast() throws Exception {
        manager.registerTrigger(group.suites.get(0).sceneTriggers);
        var gadget = manager.createGadget(group.id, group.block_id, group.gadgets.get(601002));
        assertNotNull(gadget);
        gadget.setState(ScriptGadgetState.GearStart);
        scene.getEntities().put(gadget.getId(), gadget); // Avoid automatic onCreate in this counterfactual.
        manager.callEvent(new ScriptArgs(group.id, EventType.EVENT_GADGET_CREATE, 601002))
                .get(10, TimeUnit.SECONDS);
        assertTrue(((GadgetWorktop) gadget.getContent()).getWorktopOptions().isEmpty());
        assertTrue(scene.worktopPackets.isEmpty());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static final class RecordingScene extends Scene {
        final CountDownLatch worktopBroadcast = new CountDownLatch(1);
        final CopyOnWriteArrayList<BasePacket> worktopPackets = new CopyOnWriteArrayList<>();

        RecordingScene(World world, SceneData data) { super(world, data, false); }

        @Override public void broadcastPacket(BasePacket packet) {
            if (packet instanceof PacketWorktopOptionNotify) {
                worktopPackets.add(packet);
                worktopBroadcast.countDown();
            }
            super.broadcastPacket(packet);
        }
    }
}
