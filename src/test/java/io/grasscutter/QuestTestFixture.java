package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QuestProgressUpdateNotifyOuterClass.QuestProgressUpdateNotify;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Original objectives through real content handlers, with isolated quest state and Mongo. */
abstract class QuestTestFixture {
    protected final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private com.mongodb.client.MongoClient client;
    protected Datastore store;
    protected TestPlayer player;

    static final class TestPlayer extends Player {
        GameServer server;
        ActivityManager activity;
        final TeamManager team = new TeamManager(this);
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}

        @Override
        public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value);
            return true;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_quest_progress_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(GameMainQuest.class);
        replaceField(DatabaseManager.class, null, "gameDatastore", store);
        var mode = Grasscutter.getRunMode();
        restore.add(() -> Grasscutter.setRunModeOverride(mode));
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
        boolean scripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        boolean quests = Grasscutter.getConfig().server.game.gameOptions.questing.enabled;
        restore.add(
                () -> {
                    Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
                    Grasscutter.getConfig().server.game.gameOptions.questing.enabled = quests;
                });
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        Grasscutter.getConfig().server.game.gameOptions.questing.enabled = true;
        player = new TestPlayer();
        player.setUid(10001);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        player.server = new GameServer();
        replaceField(
                GameServer.class, player.server, "questSystem", new QuestSystem(player.server));
        var oldCombat = GameData.getConfigGlobalCombat();
        restore.add(() -> GameData.setConfigGlobalCombat(oldCombat));
        GameData.setConfigGlobalCombat(
                gson.fromJson(
                        "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[]}}",
                        ConfigGlobalCombat.class));
        player.setWorld(new World(player.server, player));
        player.setSession(
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        player.packets.add(packet);
                    }
                });
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        player.setScene(
                new Scene(
                        player.getWorld(),
                        gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class)));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        player.getPlayerProgress().setPlayer(player);
        replaceField(
                ActivityManager.class,
                null,
                "configuration",
                new ActivityManager.Configuration(Map.of(), Map.of()));
        player.activity = new ActivityManager(player);
        preserve(GameData.getQuestDataMap());
        preserve(GameData.getMainQuestDataMap());
        preserve(GameData.getQuestTalkMap());
        var cache = new HashMap<String, List<QuestData>>();
        GameData.getBeginCondQuestMap()
                .forEach((key, value) -> cache.put(key, new ArrayList<>(value)));
        restore.add(
                () -> {
                    GameData.getBeginCondQuestMap().clear();
                    GameData.getBeginCondQuestMap().putAll(cache);
                });
        GameData.getQuestDataMap().clear();
        GameData.getMainQuestDataMap().clear();
        GameData.getQuestTalkMap().clear();
        var parents = Set.of(11005, 11103, 19090, 309);
        var rows =
                QuestFixtureResources.quests(
                        Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json"), parents);
        for (var row : rows)
            if (parents.contains(row.getMainId())) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        for (int id : parents) {
            var main =
                    gson.fromJson(
                            Files.readString(Path.of("resources/BinOutput/Quest", id + ".json")),
                            MainQuestData.class);
            main.onLoad();
            GameData.getMainQuestDataMap().put(id, main);
        }
        // These tests seed an active objective; automatic acceptance of later story steps is
        // separate.
        GameData.getBeginCondQuestMap().clear();
        player.packets.clear();
    }

    private <K, V> void preserve(Map<K, V> map) {
        var previous = new HashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(previous);
                });
    }

    protected void replaceField(Class<?> type, Object receiver, String name, Object value)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        var old = field.get(receiver);
        restore.add(
                () -> {
                    try {
                        field.set(receiver, old);
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                });
        field.set(receiver, value);
    }

    protected GameQuest active(int id) throws Exception {
        var meta = GameData.getQuestDataMap().get(id);
        var main = player.getQuestManager().addMainQuest(meta);
        var quest = main.getChildQuestById(id);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        main.save();
        drain();
        player.packets.clear();
        return quest;
    }

    protected void event(QuestContent type, int id) throws Exception {
        player.getQuestManager().queueEvent(type, id);
        drain();
    }

    protected void event(QuestContent type, String text, int... params) throws Exception {
        player.getQuestManager().queueEvent(type, text, params);
        drain();
    }

    // Predicate-specific cases retain the real quest lifecycle but use controlled conditions.
    protected GameQuest controlled(String finish, String fail, String logic) throws Exception {
        var json = gson.toJsonTree(GameData.getQuestDataMap().get(1100509)).getAsJsonObject();
        json.add("finishCond", gson.fromJson(finish, com.google.gson.JsonElement.class));
        json.add("failCond", gson.fromJson(fail, com.google.gson.JsonElement.class));
        json.addProperty("finishCondComb", logic);
        json.addProperty("failCondComb", "LOGIC_AND");
        var meta = gson.fromJson(json, QuestData.class);
        meta.onLoad();
        GameData.getQuestDataMap().put(meta.getId(), meta);
        GameData.getBeginCondQuestMap().clear();
        return active(meta.getId());
    }

    protected List<QuestProgressUpdateNotify> notifications() throws Exception {
        var result = new ArrayList<QuestProgressUpdateNotify>();
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.QuestProgressUpdateNotify)
                result.add(QuestProgressUpdateNotify.parseFrom(packet.getData()));
        return result;
    }

    protected void drain() throws Exception {
        for (int n = 0; n < 4; n++)
            QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
    }

    protected GameQuest stored(GameQuest quest) {
        var main =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(main);
        return main.getChildQuestById(quest.getSubQuestId());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client == null) return;
        drain();
        for (int n = restore.size() - 1; n >= 0; n--) restore.get(n).run();
        store.getDatabase().drop();
        client.close();
    }
}
