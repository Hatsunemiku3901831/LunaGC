package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.world.*;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.function.Consumer;

/** Actual solo World/Scene handoff and Mongo group state, with controlled dungeon resources. */
class TowerSceneHandoffTest extends QuestTestFixture {
    private World world;
    private Scene source, cached;
    private DungeonSystem system;
    private BirthData data;
    private final List<Runnable> restoreMaps = new ArrayList<>();
    private final List<Scene> candidates = new ArrayList<>();

    static class BirthData extends DungeonData {
        boolean throwPosition;

        @Override
        public Position getStartPosition() {
            if (throwPosition) throw new IllegalStateException("Controlled birth failure");
            return new Position(1, 2, 3);
        }

        @Override
        public Position getStartRotation() {
            return new Position(0, 90, 0);
        }
    }

    private <K, V> void remember(Map<K, V> map) {
        var previous = new HashMap<>(map);
        restoreMaps.add(
                () -> {
                    map.clear();
                    map.putAll(previous);
                });
    }

    @BeforeEach
    void setupTowerWorld() throws Exception {
        remember(GameData.getSceneDataMap());
        remember(GameData.getDungeonDataMap());
        GameData.getSceneDataMap()
                .put(4, gson.fromJson("{id:4,type:'SCENE_WORLD'}", SceneData.class));
        GameData.getSceneDataMap()
                .put(9, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        data = gson.fromJson("{id:9001,sceneId:9,passCond:0}", BirthData.class);
        GameData.getDungeonDataMap().put(9001, data);
        GameData.getDungeonDataMap()
                .put(9002, gson.fromJson("{id:9002,sceneId:9,passCond:0}", BirthData.class));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        world = new World(player.server, player);
        world.getPlayers().add(player);
        player.setWorld(world);
        player.getTeamManager().setEntity(new EntityTeam(player));
        source = world.getSceneById(4);
        source.addPlayer(player);
        source.setPrevScene(77);
        source.setPrevScenePoint(88);
        cached = world.getSceneById(9);
        system = new DungeonSystem(player.server);
        replaceField(Grasscutter.class, null, "pluginManager", new PluginManager());
        store.getMapper().map(SceneGroupInstance.class);
        player.packets.clear();
    }

    @AfterEach
    void clean() throws Exception {
        for (var scene : List.copyOf(world.getScenes().values()))
            scene.getScriptManager().onDestroy();
        for (var scene : candidates) scene.getScriptManager().onDestroy();
        source.getScriptManager().onDestroy();
        cached.getScriptManager().onDestroy();
        drain();
        restoreMaps.forEach(Runnable::run);
    }

    private void event(emu.grasscutter.utils.objects.EventConsumer<PlayerTeleportEvent> listener) {
        new EventHandler<>(PlayerTeleportEvent.class).listener(listener).register(null);
    }

    private void kept() {
        assertSame(source, player.getScene());
        assertSame(source, world.getScenes().get(4));
        assertSame(cached, world.getScenes().get(9));
        assertTrue(source.getPlayers().contains(player));
        assertFalse(source.getScriptManager().isDestroyed());
        assertFalse(cached.getScriptManager().isDestroyed());
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
    }

    private SceneGroupInstance group(Scene scene) {
        var group = SceneGroup.of(19900121);
        group.setLoaded(true);
        var stage = new SceneVar();
        stage.name = "stage";
        stage.value = 0;
        group.variables = List.of(stage);
        scene.getScriptManager().registerLocalGroups(List.of(group));
        scene.getScriptManager().loadGroupFromScript(group);
        return scene.getScriptManager().getCachedGroupInstanceById(group.id);
    }

    private boolean handoff(int dungeon, List<DungeonSettleListener> listeners) {
        return system.handoffDungeon(player, dungeon, listeners);
    }

    private boolean direct(Consumer<Scene> prepare) throws Exception {
        return (boolean)
                World.class
                        .getMethod(
                                "handoffTowerDungeonScene",
                                emu.grasscutter.game.player.Player.class,
                                DungeonData.class,
                                Consumer.class)
                        .invoke(world, player, data, prepare);
    }

    private void prepare(Scene scene) {
        candidates.add(scene);
        var manager = new DungeonManager(scene, data);
        manager.setTowerDungeon(true);
        scene.setDungeonManager(manager);
    }

    @Test
    void firstEntryReplacesAnEmptyCachedDungeonBeforePublishingItsManager() {
        var observer = new TowerDungeonSettleListener();
        assertTrue(handoff(9001, List.of(observer)));
        var fresh = player.getScene();
        assertNotSame(cached, fresh);
        assertSame(fresh, world.getScenes().get(9));
        assertTrue(cached.getScriptManager().isDestroyed());
        assertTrue(fresh.getDungeonManager().isTowerDungeon());
        assertSame(data, fresh.getDungeonManager().getDungeonData());
        assertEquals(List.of(observer), fresh.getDungeonSettleListeners());
        assertEquals(4, fresh.getPrevScene());
        assertTrue(new Position(1, 2, 3).equal3d(player.getPosition()));
    }

    @Test
    void firstEntryIgnoresOldDatabaseGroupStageAndDoesNotSaveTheNewAttempt() throws Exception {
        var old = group(cached);
        old.getCachedVariables().put("stage", 2);
        old.save();
        drain();
        assertTrue(handoff(9001, List.of()));
        var fresh = group(player.getScene());
        assertEquals(0, fresh.getCachedVariables().get("stage"));
        fresh.getCachedVariables().put("stage", 1);
        fresh.save();
        player.getScene().saveGroups();
        drain();
        assertEquals(
                2,
                emu.grasscutter.database.DatabaseHelper.loadGroupInstance(19900121, player)
                        .getCachedVariables()
                        .get("stage"));
        assertEquals(1, store.getDatabase().getCollection("group_instances").countDocuments());
    }

    @Test
    void nextChamberInTheSameSceneIdCreatesFreshStateAndOneObserver() {
        assertTrue(handoff(9001, List.of(new TowerDungeonSettleListener())));
        var first = player.getScene();
        first.setPrevScene(4);
        first.setPrevScenePoint(55);
        group(first).getCachedVariables().put("stage", 2);
        var observer = new TowerDungeonSettleListener();
        assertTrue(handoff(9002, List.of(observer)));
        var second = player.getScene();
        assertNotSame(first, second);
        assertTrue(first.getScriptManager().isDestroyed());
        assertSame(second, world.getScenes().get(9));
        assertEquals(9002, second.getDungeonManager().getDungeonData().getId());
        assertEquals(List.of(observer), second.getDungeonSettleListeners());
        assertEquals(4, second.getPrevScene());
        assertEquals(55, second.getPrevScenePoint());
        assertEquals(0, group(second).getCachedVariables().get("stage"));
    }

    @Test
    void canceledInitialEntryKeepsSourceAndCachedDungeon() {
        event(PlayerTeleportEvent::cancel);
        assertFalse(handoff(9001, List.of()));
        kept();
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void canceledNextChamberKeepsItsManagerAndStage() {
        assertTrue(handoff(9001, List.of(new TowerDungeonSettleListener())));
        var first = player.getScene();
        var manager = first.getDungeonManager();
        group(first).getCachedVariables().put("stage", 2);
        event(PlayerTeleportEvent::cancel);
        assertFalse(handoff(9002, List.of()));
        assertSame(first, player.getScene());
        assertSame(manager, first.getDungeonManager());
        assertFalse(first.getScriptManager().isDestroyed());
        assertEquals(2, group(first).getCachedVariables().get("stage"));
    }

    @Test
    void mutableObserverInputIsCopiedBeforeTheTeleportCallback() {
        var first = new TowerDungeonSettleListener();
        var listeners = new ArrayList<DungeonSettleListener>(List.of(first));
        event(e -> listeners.add(new TowerDungeonSettleListener()));
        assertTrue(handoff(9001, listeners));
        assertEquals(List.of(first), player.getScene().getDungeonSettleListeners());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrNullObserversAreRejectedBeforeLeaving(boolean missing) {
        var observers =
                missing ? null : Arrays.<DungeonSettleListener>asList((DungeonSettleListener) null);
        assertFalse(handoff(9001, observers));
        kept();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing_scene",
                "wrong_scene_type",
                "missing_dungeon",
                "multiplayer",
                "extra_world_member",
                "extra_source_member",
                "occupied_target",
                "removed_source",
                "replaced_source"
            })
    void invalidContextCannotDetachThePlayer(String invalid) throws Exception {
        switch (invalid) {
            case "missing_scene" -> GameData.getSceneDataMap().remove(9);
            case "wrong_scene_type" -> GameData.getSceneDataMap()
                    .put(9, gson.fromJson("{id:9,type:'SCENE_WORLD'}", SceneData.class));
            case "missing_dungeon" -> GameData.getDungeonDataMap().remove(9001);
            case "multiplayer" -> replaceField(World.class, world, "isMultiplayer", true);
            case "extra_world_member" -> world.getPlayers()
                    .add(new emu.grasscutter.game.player.Player());
            case "extra_source_member" -> source.getPlayers()
                    .add(new emu.grasscutter.game.player.Player());
            case "occupied_target" -> cached.getPlayers()
                    .add(new emu.grasscutter.game.player.Player());
            case "removed_source" -> world.getScenes().remove(4);
            case "replaced_source" -> world.getScenes()
                    .put(4, new Scene(world, GameData.getSceneDataMap().get(4)));
        }
        assertFalse(handoff(9001, List.of()));
        assertSame(source, player.getScene());
        assertFalse(source.getScriptManager().isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"replace_dungeon", "occupy_target", "replace_source"})
    void callbackChangesAreRecheckedBeforeSceneReplacement(String change) {
        event(
                e -> {
                    switch (change) {
                        case "replace_dungeon" -> GameData.getDungeonDataMap()
                                .put(
                                        9001,
                                        gson.fromJson(
                                                "{id:9001,sceneId:9,passCond:0}", BirthData.class));
                        case "occupy_target" -> cached.getPlayers()
                                .add(new emu.grasscutter.game.player.Player());
                        case "replace_source" -> world.getScenes()
                                .put(4, new Scene(world, GameData.getSceneDataMap().get(4)));
                    }
                });
        assertFalse(handoff(9001, List.of()));
        assertSame(source, player.getScene());
        assertFalse(source.getScriptManager().isDestroyed());
    }

    @Test
    void newTargetDirectoryEntryIsPublishedWithoutAnExistingCachedScene() {
        world.getScenes().remove(9);
        assertTrue(handoff(9001, List.of()));
        assertSame(player.getScene(), world.getScenes().get(9));
        assertNotSame(cached, player.getScene());
    }

    @Test
    void preparationSeesNoJoinedPlayersAndRunsBeforeDirectoryReplacement() throws Exception {
        assertTrue(
                direct(
                        fresh -> {
                            prepare(fresh);
                            assertTrue(fresh.getPlayers().isEmpty());
                            assertSame(source, player.getScene());
                            assertSame(cached, world.getScenes().get(9));
                        }));
        assertSame(candidates.get(0), player.getScene());
    }

    @Test
    void invalidPreparedManagerClosesOnlyTheCandidate() throws Exception {
        assertFalse(
                direct(
                        fresh -> {
                            candidates.add(fresh);
                            fresh.setDungeonManager(new DungeonManager(fresh, data));
                        }));
        kept();
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).getScriptManager().isDestroyed());
    }
}
