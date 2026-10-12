package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PostEnterSceneReqOuterClass.PostEnterSceneReq;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerPostEnterSceneReq;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real player save/cold load and onLogin before World construction; no native client simulation.
 */
class DungeonLoginRecoveryTest extends QuestTestFixture {
    private final List<Scene> scenes = new ArrayList<>();
    private final Map<Integer, SoftReference<SceneMeta>> metadata = new ConcurrentHashMap<>();
    private Map<Integer, SceneData> oldScenes;
    private Map<Integer, DungeonData> oldDungeons;
    private DungeonSystem system;
    private static final Position ORIGIN = new Position(123, 45, 67);
    private static final Position BORN3 = new Position(10, 20, 30);
    private static final Position BORN4 = new Position(40, 50, 60);

    private static class AdmissionProbe extends RuntimeException {}

    @Entity(value = "players", useDiscriminator = false)
    static class SavedReturnPlayer extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient GameServer server;
        @Transient boolean probe;

        public SavedReturnPlayer() {
            super();
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public GameServer getServer() {
            if (probe) throw new AdmissionProbe();
            return server;
        }

        @Override
        public void sendPacket(BasePacket packet) {}
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void dungeonResources() throws Exception {
        oldScenes = new HashMap<>(GameData.getSceneDataMap());
        oldDungeons = new HashMap<>(GameData.getDungeonDataMap());
        for (int id : new int[] {3, 4, 5, 9})
            GameData.getSceneDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",type:'"
                                            + (id == 9 ? "SCENE_DUNGEON" : "SCENE_WORLD")
                                            + "'}",
                                    SceneData.class));
        var dungeon =
                gson.fromJson(
                        "{id:9001,sceneId:9,type:'DUNGEON_PLOT',subType:'DUNGEON_SUB_NONE',passCond:0}",
                        DungeonData.class);
        GameData.getDungeonDataMap().put(9001, dungeon);
        metadata.put(3, new SoftReference<>(meta(BORN3)));
        metadata.put(4, new SoftReference<>(meta(BORN4)));
        replaceField(ScriptLoader.class, null, "sceneMetaCache", metadata);
        system = new DungeonSystem(player.server);
        store.getMapper().map(SavedReturnPlayer.class);
    }

    private SceneMeta meta(Position born) {
        var result = new SceneMeta();
        result.config = new SceneConfig();
        result.config.born_pos = born == null ? null : born.clone();
        return result;
    }

    @AfterEach
    void restoreResources() throws Exception {
        drain();
        for (var scene : scenes) scene.getScriptManager().onDestroy();
        GameData.getSceneDataMap().clear();
        GameData.getSceneDataMap().putAll(oldScenes);
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap().putAll(oldDungeons);
    }

    private SavedReturnPlayer model() {
        var model = new SavedReturnPlayer();
        model.setUid(20112);
        model.server = player.server;
        model.setSession(player.getSession());
        return model;
    }

    private SavedReturnPlayer cold() throws Exception {
        drain();
        var restored = store.find(SavedReturnPlayer.class).filter(Filters.eq("id", 20112)).first();
        assertNotNull(restored);
        restored.server = player.server;
        restored.setSession(player.getSession());
        return restored;
    }

    private SavedReturnPlayer savedOrigin(int scene, Position position) throws Exception {
        var model = model();
        model.setSceneId(9);
        model.getPosition().set(1, -450, 2);
        model.save();
        drain();
        var document = new Document("sceneId", scene);
        if (position != null)
            document.append(
                    "position",
                    new Document("x", (double) position.getX())
                            .append("y", (double) position.getY())
                            .append("z", (double) position.getZ()));
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20112),
                        new Document("$set", new Document("dungeonReturnLocation", document)));
        return cold();
    }

    private void login(SavedReturnPlayer model) {
        model.probe = true;
        try {
            var error = assertThrows(AdmissionProbe.class, model::onLogin);
            assertTrue(
                    Arrays.stream(error.getStackTrace())
                            .anyMatch(
                                    frame ->
                                            frame.getClassName().equals(World.class.getName())
                                                    && frame.getMethodName().equals("<init>")));
        } finally {
            model.probe = false;
        }
    }

    private void at(Player model, int scene, Position position) {
        assertEquals(scene, model.getSceneId());
        assertTrue(
                position.equal3d(model.getPosition()),
                "Expected " + position + ", got " + model.getPosition());
    }

    private Document storedOrigin() throws Exception {
        drain();
        var row =
                store.getDatabase()
                        .getCollection("players")
                        .find(new Document("_id", 20112))
                        .first();
        assertNotNull(row);
        return row.get("dungeonReturnLocation", Document.class);
    }

    private class TransferWorld extends World {
        boolean success = true;
        boolean throwTransfer;
        final Scene destination;

        TransferWorld(Player owner) {
            super(player.server, owner);
            destination = scene(this, 9, "SCENE_DUNGEON");
        }

        @Override
        public boolean transferPlayerToScene(Player owner, int id, DungeonData data) {
            if (throwTransfer) throw new IllegalStateException("Controlled transfer failure");
            if (!success) return false;
            owner.setScene(destination);
            owner.setSceneId(9);
            owner.getPosition().set(1, 2, 3);
            return true;
        }
    }

    private Scene scene(World world, int id, String type) {
        boolean enabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            var scene =
                    new Scene(
                            world,
                            gson.fromJson("{id:" + id + ",type:'" + type + "'}", SceneData.class));
            scenes.add(scene);
            return scene;
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = enabled;
        }
    }

    private TransferWorld source(SavedReturnPlayer model, int id, String type) {
        var world = new TransferWorld(model);
        model.setWorld(world);
        model.setScene(scene(world, id, type));
        model.setSceneId(id);
        model.getPosition().set(ORIGIN);
        return world;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void entrySavesTheWorldOriginBeforeItsPositionChanges(boolean savePrevious) throws Exception {
        var model = model();
        source(model, 4, "SCENE_WORLD");
        assertTrue(system.enterDungeon(model, 123, 9001, savePrevious));
        var origin = storedOrigin();
        assertNotNull(origin);
        assertEquals(4, origin.getInteger("sceneId"));
        var restored = cold();
        assertEquals(9, restored.getSceneId());
        login(restored);
        at(restored, 4, ORIGIN);
    }

    @Test
    void cancellationPreservesAnOlderOrigin() throws Exception {
        var model = savedOrigin(3, BORN3);
        var world = source(model, 4, "SCENE_WORLD");
        world.success = false;
        assertFalse(system.enterDungeon(model, 123, 9001, true));
        assertEquals(3, storedOrigin().getInteger("sceneId"));
    }

    @Test
    void thrownTransferPreservesAnOlderOrigin() throws Exception {
        var model = savedOrigin(3, BORN3);
        var world = source(model, 4, "SCENE_WORLD");
        world.throwTransfer = true;
        assertThrows(
                IllegalStateException.class, () -> system.enterDungeon(model, 123, 9001, true));
        assertEquals(3, storedOrigin().getInteger("sceneId"));
    }

    @Test
    void invalidWorldCoordinatesCannotReplaceAnOlderOrigin() throws Exception {
        var model = savedOrigin(3, BORN3);
        source(model, 4, "SCENE_WORLD");
        model.getPosition().set(Float.NaN, 45, 67);
        assertTrue(system.enterDungeon(model, 123, 9001, true));
        model.save();
        var restored = cold();
        login(restored);
        at(restored, 3, BORN3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCENE_DUNGEON", "SCENE_HOME_WORLD"})
    void nonWorldHandoffDoesNotReplaceItsWorldOrigin(String type) throws Exception {
        var model = savedOrigin(3, BORN3);
        source(model, 5, type);
        assertTrue(system.enterDungeon(model, 123, 9001, true));
        model.save();
        var restored = cold();
        login(restored);
        at(restored, 3, BORN3);
        assertEquals(3, storedOrigin().getInteger("sceneId"));
    }

    @Test
    void homeReturnFieldsCannotOverwriteDungeonReturn() throws Exception {
        var model = savedOrigin(4, ORIGIN);
        model.setPrevScene(5);
        model.setPrevPosForHome(new Position(500, 600, 700));
        model.save();
        var restored = cold();
        login(restored);
        at(restored, 4, ORIGIN);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldRecordsWithoutOriginUseWorldBornPosition(boolean explicitNull) throws Exception {
        var model = model();
        model.setSceneId(9);
        model.save();
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20112),
                        new Document(
                                explicitNull ? "$set" : "$unset",
                                new Document("dungeonReturnLocation", explicitNull ? null : "")));
        var restored = cold();
        login(restored);
        at(restored, 3, BORN3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 9, 999999})
    void invalidOrDungeonOriginCannotBecomeTheLoginScene(int scene) throws Exception {
        var restored = savedOrigin(scene, ORIGIN);
        login(restored);
        at(restored, 3, BORN3);
    }

    @Test
    void worldLoginKeepsItsSavedPosition() throws Exception {
        var restored = savedOrigin(4, ORIGIN);
        restored.setSceneId(3);
        restored.getPosition().set(7, 8, 9);
        login(restored);
        at(restored, 3, new Position(7, 8, 9));
    }

    @Test
    void absentPositionUsesTheOriginsBornPosition() throws Exception {
        var restored = savedOrigin(4, null);
        login(restored);
        at(restored, 4, BORN4);
    }

    @Test
    void zeroPositionUsesTheOriginsBornPosition() throws Exception {
        var restored = savedOrigin(4, new Position());
        login(restored);
        at(restored, 4, BORN4);
    }

    @Test
    void nonFinitePositionUsesTheOriginsBornPosition() throws Exception {
        var restored = savedOrigin(4, new Position(Float.NaN, Float.POSITIVE_INFINITY, 67));
        login(restored);
        at(restored, 4, BORN4);
    }

    @Test
    void unusableOriginBornPositionFallsBackToDefaultWorld() throws Exception {
        metadata.put(4, new SoftReference<>(meta(null)));
        var restored = savedOrigin(4, null);
        login(restored);
        at(restored, 3, BORN3);
    }

    @Test
    void missingDefaultBornPositionUsesTheKnownStartPosition() throws Exception {
        metadata.put(3, new SoftReference<>(meta(null)));
        var model = model();
        model.setSceneId(9);
        login(model);
        at(model, 3, GameConstants.START_POSITION);
    }

    @Test
    void savedPositionReturnedByTheOriginCannotMutateTheStoredOrigin() throws Exception {
        var model = savedOrigin(4, ORIGIN);
        var getter = Player.class.getMethod("getDungeonReturnLocation");
        var origin = getter.invoke(model);
        var position = (Position) origin.getClass().getMethod("getPosition").invoke(origin);
        position.set(1000, 2000, 3000);
        login(model);
        at(model, 4, ORIGIN);
    }

    @Test
    void recoveredWorldRunsNative35722RollbackThroughPostEnterScene() throws Exception {
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class)) {
            if (row.getMainId() == 357) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/357.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(357, main);
        GameData.getBeginCondQuestMap().clear();
        var quest = active(35722);
        var earlier = quest.getMainQuest().getChildQuestById(35721);
        earlier.setState(QuestState.QUEST_STATE_FINISHED);
        assertEquals(
                QuestContent.QUEST_CONTENT_ENTER_MY_WORLD,
                quest.getQuestData().getFailCond().get(0).getType());
        assertEquals(
                QuestExec.QUEST_EXEC_ROLLBACK_QUEST,
                quest.getQuestData().getFailExec().get(0).getType());
        var model = model();
        var nativeScene =
                Arrays.stream(
                                gson.fromJson(
                                        Files.readString(
                                                Path.of(
                                                        "resources/ExcelBinOutput/SceneExcelConfigData.json")),
                                        SceneData[].class))
                        .filter(row -> row.getId() == 20020)
                        .findFirst()
                        .orElseThrow();
        GameData.getSceneDataMap().put(nativeScene.getId(), nativeScene);
        assertEquals(
                2004, GameData.getQuestDataMap().get(35721).getFinishCond().get(0).getParam()[0]);
        model.setSceneId(20020);
        login(model);
        player.setSceneId(model.getSceneId());
        assertEquals(3, player.getSceneId());
        var session =
                new GameSession(player.server) {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public void send(BasePacket packet) {
                        player.packets.add(packet);
                    }
                };
        new HandlerPostEnterSceneReq()
                .handle(session, new byte[0], PostEnterSceneReq.getDefaultInstance().toByteArray());
        drain();
        assertEquals(QuestState.QUEST_STATE_UNSTARTED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, earlier.getState());
    }
}
