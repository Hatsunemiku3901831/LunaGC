package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.data.excels.DailyTaskLevelData;
import emu.grasscutter.data.excels.DailyTaskRewardData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.monster.MonsterCurveData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import com.google.gson.JsonParser;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.ScriptLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Real daily login and Mongo cold read, using a dedicated test JVM and database. */
final class D02DailyRuntimeMongoTest {
    private static final class BoundaryPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
    }
    private static MongoClient client;
    private static Datastore previous, store;
    private static Map<Integer, DailyTaskData> daily;
    private static Map<Integer, DailyTaskLevelData> levels;
    private static Map<Integer, DailyTaskRewardData> rewards;
    private static ConfigGlobalCombat combat;
    private static Map<Integer, MonsterData> monsters;
    private static Map<Integer, MonsterCurveData> curves;
    private static Map<Integer, GadgetData> gadgets;

    @BeforeAll
    static void setup() throws Exception {
        var work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        assertEquals(work.resolve("config.json"), Grasscutter.configFile.getCanonicalFile().toPath());
        assertEquals(work.resolve("data"), Path.of(config.folderStructure.data).toAbsolutePath().normalize());
        assertEquals("mongodb://127.0.0.1:27027", System.getenv("LUNAGC_TEST_MONGO_URI"));
        var monsterExcel = Path.of(System.getProperty("d02.monsterExcel"));
        assertEquals("b0efcdb6bca52eca8c2b89fe2e43f8f5a65bc1596fd250e7ddc375acd92989cc", sha(monsterExcel.resolve("MonsterExcelConfigData.json")));
        assertEquals("3014983fd6292b612c040fb4106d6bfe10aef2da0ceffcba01bd10872b832f84", sha(monsterExcel.resolve("MonsterCurveExcelConfigData.json")));
        assertEquals("5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411", sha(monsterExcel.resolve("GadgetExcelConfigData.json")));
        assertEquals("44e9de619028ac8fa2f329b565486de6df020d9aae9f2ec61703023e68687048", sha(Path.of(System.getProperty("d02.resources"), "ExcelBinOutput/DailyTaskExcelConfigData.json")));
        assertEquals("df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0", sha(Path.of(System.getProperty("d02.resources"), "BinOutput/Common/ConfigGlobalCombat.json")));
        String database = System.getProperty("d02.dailyDatabase");
        assertTrue(database.startsWith("lunagc_d02_daily_"));
        client = MongoClients.create(System.getenv("LUNAGC_TEST_MONGO_URI"));
        store = Morphia.createDatastore(client, database);
        store.getMapper().map(DailyTaskManager.class, DailyTask.class);
        previous = DatabaseManager.getGameDatastore();
        setStore(store);
        daily = new HashMap<>(GameData.getDailyTaskDataMap());
        levels = new HashMap<>(GameData.getDailyTaskLevelDataMap());
        rewards = new HashMap<>(GameData.getDailyTaskRewardDataMap());
        combat = GameData.getConfigGlobalCombat();
        monsters = new HashMap<>(GameData.getMonsterDataMap());
        curves = new HashMap<>(GameData.getMonsterCurveDataMap());
        gadgets = new HashMap<>(GameData.getGadgetDataMap());
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(Path.of(System.getProperty("d02.resources"), "BinOutput/Common/ConfigGlobalCombat.json"), ConfigGlobalCombat.class));
        assertNull(ScriptLoader.getEngine());
        ScriptLoader.init();
        load(DailyTaskData.class, "DailyTaskExcelConfigData.json", GameData.getDailyTaskDataMap());
        load(DailyTaskLevelData.class, "DailyTaskLevelExcelConfigData.json", GameData.getDailyTaskLevelDataMap());
        load(DailyTaskRewardData.class, "DailyTaskRewardExcelConfigData.json", GameData.getDailyTaskRewardDataMap());
        loadFrom(Path.of(System.getProperty("d02.monsterExcel")), MonsterData.class, "MonsterExcelConfigData.json", GameData.getMonsterDataMap());
        loadFrom(Path.of(System.getProperty("d02.monsterExcel")), MonsterCurveData.class, "MonsterCurveExcelConfigData.json", GameData.getMonsterCurveDataMap());
        loadFrom(monsterExcel, GadgetData.class, "GadgetExcelConfigData.json", GameData.getGadgetDataMap());
    }

    private static void load(Class<?> type, String file, Int2ObjectMap<?> map) throws Exception {
        loadFrom(Path.of(System.getProperty("d02.resources"), "ExcelBinOutput"), type, file, map);
    }

    private static void loadFrom(Path directory, Class<?> type, String file, Int2ObjectMap<?> map) throws Exception {
        var method = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        method.setAccessible(true);
        method.invoke(null, type, directory.resolve(file), map);
    }

    private static void setStore(Datastore value) throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, value);
    }

    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    @AfterAll
    static void restore() throws Exception {
        if (daily != null) { GameData.getDailyTaskDataMap().clear(); GameData.getDailyTaskDataMap().putAll(daily); }
        if (levels != null) { GameData.getDailyTaskLevelDataMap().clear(); GameData.getDailyTaskLevelDataMap().putAll(levels); }
        if (rewards != null) { GameData.getDailyTaskRewardDataMap().clear(); GameData.getDailyTaskRewardDataMap().putAll(rewards); }
        GameData.setConfigGlobalCombat(combat);
        if (monsters != null) { GameData.getMonsterDataMap().clear(); GameData.getMonsterDataMap().putAll(monsters); }
        if (curves != null) { GameData.getMonsterCurveDataMap().clear(); GameData.getMonsterCurveDataMap().putAll(curves); }
        if (gadgets != null) { GameData.getGadgetDataMap().clear(); GameData.getGadgetDataMap().putAll(gadgets); }
        setStore(previous);
        if (client != null) client.close(); // Preserve the unique test database as evidence.
    }

    @Test
    void levelOneLoginPersistsAndSameDayColdReloadDoesNotReroll() throws Exception {
        var player = new BoundaryPlayer();
        player.setUid(10001);
        // Controlled level-one input: a network session is deliberately absent.
        player.getProperties().put(PlayerProperty.PROP_PLAYER_LEVEL.getId(), 1);
        assertEquals(1, player.getLevel());
        player.loadDailyTaskManager();
        var manager = player.getDailyTaskManager();
        assertEquals(0, manager.loadActiveGroups(null));
        if (manager.getDailyTasks().isEmpty()) {
            // A separately initialized scene represents Done arriving before daily generation.
            realSceneAfterLogin(player, manager, 0);
        }
        manager.onPlayerLogin();
        assertEquals(1, player.packets.size());
        assertEquals(4, manager.getDailyTasks().size());
        List<Integer> ids = manager.getDailyTasks().stream().map(DailyTask::getTaskId).toList();
        var groups = manager.getActiveGroupIds();
        assertEquals(4, groups.size());
        assertNotNull(store.getDatabase().getCollection("dailytasks").find().first());
        var coldPlayer = new BoundaryPlayer();
        coldPlayer.setUid(player.getUid());
        coldPlayer.getProperties().put(PlayerProperty.PROP_PLAYER_LEVEL.getId(), 1);
        coldPlayer.loadDailyTaskManager();
        var reloaded = coldPlayer.getDailyTaskManager();
        assertNotSame(manager, reloaded);
        assertEquals(ids, reloaded.getDailyTasks().stream().map(DailyTask::getTaskId).toList());
        reloaded.onPlayerLogin();
        assertEquals(ids, reloaded.getDailyTasks().stream().map(DailyTask::getTaskId).toList());
        assertEquals(groups, reloaded.getActiveGroupIds());
        System.out.println("D02_DAILY actualIds=" + ids + " groups=" + groups + " day=" + reloaded.getLastGenerationDate());
        realSceneAfterLogin(coldPlayer, reloaded, groups.size());
    }

    private void realSceneAfterLogin(Player player, DailyTaskManager manager, int expected) throws Exception {
        var config = Grasscutter.getConfig();
        boolean priorEnabled = config.server.game.enableScriptInBigWorld;
        var override = Grasscutter.class.getDeclaredField("runModeOverride");
        override.setAccessible(true);
        var priorMode = (Grasscutter.ServerRunMode) override.get(null);
        Scene scene = null;
        try {
            config.server.game.enableScriptInBigWorld = true;
            Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
            var server = new GameServer();
            var worlds = GameServer.class.getDeclaredField("worlds");
            worlds.setAccessible(true);
            worlds.set(server, new HashSet<World>());
            var world = new World(server, player);
            var data = JsonUtils.decode(JsonParser.parseString("{\"id\":3,\"type\":\"SCENE_WORLD\"}"), SceneData.class);
            scene = new Scene(world, data, false);
            var ready = new CompletableFuture<Boolean>();
            scene.getScriptManager().whenInitialized(ready::complete);
            assertTrue(ready.get(30, TimeUnit.SECONDS), "scene3 initialization failed");
            int count = manager.loadActiveGroups(scene);
            System.out.println("D02_DAILY sceneActive=" + count + " expected=" + expected);
            assertEquals(expected, count);
            for (int groupId : manager.getActiveGroupIds()) {
                var instance = scene.getScriptManager().getGroupInstanceById(groupId);
                assertNotNull(instance, "missing instance " + groupId);
                var suite = instance.getLuaGroup().getSuiteByIndex(instance.getActiveSuiteId());
                assertNotNull(suite, "missing active suite " + groupId);
                assertEquals(suite.monsters.size(), suite.sceneMonsters.size(), "unresolved monster config " + groupId);
                assertEquals(suite.gadgets.size(), suite.sceneGadgets.size(), "unresolved gadget config " + groupId);
                System.out.println("D02_DAILY group=" + groupId + " suite=" + instance.getActiveSuiteId()
                        + " npcs=" + instance.getLuaGroup().npcs.keySet()
                        + " triggers=" + suite.sceneTriggers.stream().map(t -> t.getName() + ":" + t.getEvent()).toList());
                for (var monster : suite.sceneMonsters) {
                    var entity = assertInstanceOf(EntityMonster.class, scene.getEntityByConfigId(monster.config_id, groupId), "monster config " + monster.config_id + " group " + groupId);
                    assertEquals(monster.monster_id, entity.getMonsterData().getId());
                    System.out.println("D02_DAILY monster group=" + groupId + " config=" + monster.config_id + " id=" + entity.getMonsterData().getId());
                }
                for (var gadget : suite.sceneGadgets) {
                    var entity = assertInstanceOf(EntityGadget.class, scene.getEntityByConfigId(gadget.config_id, groupId), "gadget config " + gadget.config_id + " group " + groupId);
                    assertEquals(gadget.gadget_id, entity.getGadgetData().getId());
                    System.out.println("D02_DAILY gadget group=" + groupId + " config=" + gadget.config_id + " id=" + entity.getGadgetData().getId());
                }
            }
        } finally {
            if (scene != null) scene.getScriptManager().onDestroy();
            config.server.game.enableScriptInBigWorld = priorEnabled;
            Grasscutter.setRunModeOverride(priorMode);
        }
    }
}
