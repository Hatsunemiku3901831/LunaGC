package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.ChapterData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.*;

/** Real 303 quest starts and Mongo persistence; not full login or client wire. */
final class D02Quest303StartMongoTest {
    private static MongoClient client;
    private static Datastore store, priorStore;
    private static Map<Integer, QuestData> quests;
    private static Map<Integer, MainQuestData> chapters;
    private static Map<String, List<QuestData>> conditions;
    private static Map<Integer, Integer> talks;
    private static ConfigGlobalCombat combat;
    private static Grasscutter.ServerRunMode priorMode;
    private static boolean priorScripts, priorQuesting;
    private static final List<String> errors = new CopyOnWriteArrayList<>();
    private static AppenderBase<ILoggingEvent> appender;

    static final class BoundaryPlayer extends Player {
        final GameServer server;
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();
        BoundaryPlayer(GameServer server, int uid) {
            this.server = server; setUid(uid); getProperties().put(PlayerProperty.PROP_PLAYER_LEVEL.getId(), 1);
        }
        @Override public GameServer getServer() { return server; }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
    }

    @BeforeAll
    static void setup() throws Exception {
        Path root = Path.of(System.getProperty("d02.repo"));
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        var config = Grasscutter.getConfig();
        assertEquals(work.resolve("config.json"), Grasscutter.configFile.getCanonicalFile().toPath());
        assertEquals(work.resolve("data"), Path.of(config.folderStructure.data).toAbsolutePath().normalize());
        assertEquals(work.resolve("cache"), Path.of(config.folderStructure.cache).toAbsolutePath().normalize());
        assertEquals("mongodb://127.0.0.1:27027", System.getenv("LUNAGC_TEST_MONGO_URI"));
        assertFalse(ResourceLoader.isLoadedAll(), "full resources must remain unready");
        assertNull(Grasscutter.getPluginManager());
        Path targetExcel = root.resolve("local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json");
        Path targetChapter = root.resolve("local/migration72/d02-r3-candidate/BinOutput/Quest/303.json");
        Path oldCombat = root.resolve("resources/BinOutput/Common/ConfigGlobalCombat.json");
        requireHash(targetExcel, "c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87");
        requireHash(targetChapter, "aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca");
        requireHash(root.resolve("resources/ExcelBinOutput/QuestExcelConfigData.json"), "aacb6edba8b97e3dd39fef80444bb180756fe1d6090c341cd0545ec08f0951a3");
        requireHash(root.resolve("resources/BinOutput/Quest/303.json"), "44b646a0f29a3a1a1b780d58902ee28e7d5749ba13a45e6b9b2a6d48d12ad8cc");
        requireHash(oldCombat, "df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0");
        String db = System.getProperty("d02.database"); assertTrue(db.startsWith("lunagc_d02_quest303_"));
        client = MongoClients.create(System.getenv("LUNAGC_TEST_MONGO_URI"));
        store = Morphia.createDatastore(client, db); store.getMapper().map(GameMainQuest.class);
        priorStore = DatabaseManager.getGameDatastore(); setStore(store);
        quests = new HashMap<>(GameData.getQuestDataMap()); chapters = new HashMap<>(GameData.getMainQuestDataMap());
        conditions = new HashMap<>(GameData.getBeginCondQuestMap()); talks = new HashMap<>(GameData.getQuestTalkMap());
        combat = GameData.getConfigGlobalCombat(); priorScripts = config.server.game.enableScriptInBigWorld;
        priorQuesting = config.server.game.gameOptions.questing.enabled;
        var mode = Grasscutter.class.getDeclaredField("runModeOverride"); mode.setAccessible(true); priorMode = (Grasscutter.ServerRunMode) mode.get(null);
        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent e) { if (e.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(e.getFormattedMessage()); }
        };
        appender.setContext(Grasscutter.getLogger().getLoggerContext()); appender.start(); Grasscutter.getLogger().addAppender(appender);
        GameData.getQuestDataMap().clear(); GameData.getMainQuestDataMap().clear(); GameData.getBeginCondQuestMap().clear(); GameData.getQuestTalkMap().clear();
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(oldCombat, ConfigGlobalCombat.class));
        config.server.game.enableScriptInBigWorld = true; config.server.game.gameOptions.questing.enabled = true;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
        var rows = JsonParser.parseString(Files.readString(targetExcel)).getAsJsonArray();
        var slice = new JsonArray(); for (var row : rows) if (row.getAsJsonObject().get("subId").getAsInt() == 30302) slice.add(row);
        assertEquals(1, slice.size());
        var file = work.resolve("target-excel-30302.json"); Files.writeString(file, slice.toString());
        var rejected = new Int2ObjectOpenHashMap<QuestData>();
        var load = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        load.setAccessible(true); load.invoke(null, QuestData.class, file, rejected); assertTrue(rejected.isEmpty());
        var chapter = JsonUtils.loadToClass(targetChapter, MainQuestData.class);
        GameData.getMainQuestDataMap().put(chapter.getId(), chapter); chapter.onLoad();
        assertEquals(303, chapter.getId()); assertEquals(63, chapter.getSubQuests().length);
        assertEquals(63, GameData.getQuestDataMap().size()); assertTrue(GameData.getBeginCondQuestMap().isEmpty());
        assertFalse(ChapterData.getBeginQuestChapterMap().containsKey(30302), "target ChapterExcel not loaded in this fixture");
        assertFalse(ResourceLoader.isLoadedAll());
    }

    private static void requireHash(Path path, String expected) throws Exception {
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
    }

    private static void setStore(Datastore value) throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore"); field.setAccessible(true); field.set(null, value);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (store != null) assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(20)));
        if (appender != null) Grasscutter.getLogger().detachAppender(appender);
        if (quests != null) { GameData.getQuestDataMap().clear(); GameData.getQuestDataMap().putAll(quests); }
        if (chapters != null) { GameData.getMainQuestDataMap().clear(); GameData.getMainQuestDataMap().putAll(chapters); }
        if (conditions != null) { GameData.getBeginCondQuestMap().clear(); GameData.getBeginCondQuestMap().putAll(conditions); }
        if (talks != null) { GameData.getQuestTalkMap().clear(); GameData.getQuestTalkMap().putAll(talks); }
        GameData.setConfigGlobalCombat(combat); Grasscutter.getConfig().server.game.enableScriptInBigWorld = priorScripts;
        Grasscutter.getConfig().server.game.gameOptions.questing.enabled = priorQuesting; Grasscutter.setRunModeOverride(priorMode);
        setStore(priorStore); if (client != null) client.close(); // Preserve isolated database.
    }

    private static BoundaryPlayer player(int uid) throws Exception {
        var server = new GameServer();
        var field = GameServer.class.getDeclaredField("questSystem"); field.setAccessible(true); field.set(server, new QuestSystem(server));
        var player = new BoundaryPlayer(server, uid); player.setWorld(new World(server, player)); player.getPlayerProgress().setPlayer(player);
        return player;
    }

    private static void drain() throws Exception {
        // All recovered finish conditions are COMPLETE_TALK and beginExec lists are empty;
        // the state events submitted here cannot themselves match another recovered condition.
        QuestManager.eventExecutor.submit(() -> {}).get(15, TimeUnit.SECONDS);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(20)), "quest saves did not settle");
    }

    @Test
    void direct30302StartPersistsAndUnreadyManagerDoesNotRebind() throws Exception {
        var player = player(210001);
        var started = player.getQuestManager().addQuest(30302);
        assertNotNull(started); assertEquals(QuestState.QUEST_STATE_UNFINISHED, started.getState());
        assertEquals(303, started.getMainQuestId()); assertTrue(started.getStartTime() > 0); assertTrue(started.getAcceptTime() > 0);
        assertEquals(63, started.getMainQuest().getChildQuests().size());
        assertEquals(62, started.getMainQuest().getChildQuests().values().stream().filter(q -> q.getState() == QuestState.QUEST_STATE_UNSTARTED).count());
        assertArrayEquals(new int[] {0}, started.getFinishProgressList());
        assertTrue(started.getMainQuest().getQuestGroupSuites().isEmpty());
        assertEquals(List.of("PacketFinishedParentQuestUpdateNotify", "PacketQuestListUpdateNotify"), player.packets.stream().map(p -> p.getClass().getSimpleName()).toList());
        drain(); var raw = bson(210001, "direct-bson.json");
        var cold = player(210001); var saved = DatabaseHelper.getAllQuests(cold);
        assertEquals(1, saved.size()); assertEquals(303, saved.get(0).getParentQuestId());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, saved.get(0).getChildQuestById(30302).getState());
        assertArrayEquals(new int[] {0}, saved.get(0).getChildQuestById(30302).getFinishProgressList());
        assertEquals(62, saved.get(0).getChildQuests().values().stream().filter(q -> q.getState() == QuestState.QUEST_STATE_UNSTARTED).count());
        assertTrue(saved.get(0).getQuestGroupSuites().isEmpty());
        assertFalse(ResourceLoader.isLoadedAll()); cold.getQuestManager().loadFromDatabase();
        assertTrue(cold.getQuestManager().getMainQuests().isEmpty());
        assertEquals(raw, bson(210001, "direct-bson-after-gate.json"));
        assertFalse(ResourceLoader.isLoadedAll()); assertTrue(errors.isEmpty(), "real runtime errors: " + errors);
        System.out.println("D02_303_DIRECT started=30302 childCount=63 bsonColdRead=1 unreadyManagerRebound=0 packets=" + player.packets.size());
    }

    @Test
    void progressStatueEntryStartsAndPersistsActualChapterChildren() throws Exception {
        var player = player(210002);
        var entry = player.getProgressManager().getClass().getDeclaredMethod("addStatueQuestsOnLogin");
        entry.setAccessible(true); entry.invoke(player.getProgressManager());
        var main = player.getQuestManager().getMainQuestById(303); assertNotNull(main);
        assertEquals(63, main.getChildQuests().size());
        assertEquals(63, main.getChildQuests().values().stream().filter(q -> q.getState() == QuestState.QUEST_STATE_UNFINISHED).count());
        drain(); bson(210002, "statue-bson.json");
        assertEquals(64, player.packets.size());
        assertEquals("PacketFinishedParentQuestUpdateNotify", player.packets.get(0).getClass().getSimpleName());
        assertEquals(63, player.packets.stream().filter(p -> p.getClass().getSimpleName().equals("PacketQuestListUpdateNotify")).count());
        var cold = player(210002); var saved = DatabaseHelper.getAllQuests(cold); assertEquals(1, saved.size());
        assertEquals(63, saved.get(0).getChildQuests().size());
        assertEquals(63, saved.get(0).getChildQuests().values().stream().filter(q -> q.getState() == QuestState.QUEST_STATE_UNFINISHED).count());
        assertFalse(ResourceLoader.isLoadedAll()); cold.getQuestManager().loadFromDatabase();
        assertTrue(cold.getQuestManager().getMainQuests().isEmpty()); assertFalse(ResourceLoader.isLoadedAll());
        assertTrue(errors.isEmpty(), "real runtime errors: " + errors);
        System.out.println("D02_303_STATUE childrenStarted=63 bsonColdRead=63 unreadyManagerRebound=0 packets=" + player.packets.size());
    }

    private static Document bson(int uid, String file) throws Exception {
        var matches = store.getDatabase().getCollection("quests").find(new Document("ownerUid", uid)).into(new ArrayList<>());
        assertEquals(1, matches.size()); var raw = matches.get(0); assertEquals(303, raw.getInteger("parentQuestId"));
        Files.writeString(Path.of(System.getProperty("user.dir"), file), raw.toJson() + "\n"); return raw;
    }
}
