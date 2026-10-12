package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;

import java.util.*;

/** Formal group saves/loads through isolated Mongo and per-scene ephemeral group state. */
class DungeonGroupPersistenceTest extends QuestTestFixture {
    private final List<Scene> created = new ArrayList<>();

    @BeforeEach
    void mapGroups() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        store.getMapper().map(SceneGroupInstance.class);
    }

    @AfterEach
    void close() throws Exception {
        for (var scene : created) scene.getScriptManager().onDestroy();
        drain();
    }

    private SceneGroup group(int id) {
        var group = SceneGroup.of(id);
        group.setLoaded(true);
        var stage = new SceneVar();
        stage.name = "stage";
        stage.value = 0;
        group.variables = List.of(stage);
        return group;
    }

    private Scene scene(boolean persistent) throws Exception {
        var data = gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class);
        var result =
                (Scene)
                        Scene.class
                                .getConstructor(World.class, SceneData.class, boolean.class)
                                .newInstance(player.getWorld(), data, persistent);
        created.add(result);
        return result;
    }

    private SceneGroupInstance load(Scene scene, int id) {
        var group = group(id);
        scene.getScriptManager().registerLocalGroups(List.of(group));
        scene.getScriptManager().loadGroupFromScript(group);
        return scene.getScriptManager().getCachedGroupInstanceById(id);
    }

    private void saveOld(int id) throws Exception {
        var state = new SceneGroupInstance(group(id), player);
        state.getCachedVariables().put("stage", 2);
        state.getDeadEntities().add(12001);
        state.markScriptGadgetDestroyed(12002);
        state.getCachedGadgetStates().put(12002, 201);
        state.setActiveSuiteId(2);
        state.setTargetSuiteId(2);
        state.save();
        drain();
    }

    @Test
    void ordinaryGroupLoadsAndKeepsItsSavedProgress() throws Exception {
        saveOld(19900121);
        var scene =
                new Scene(
                        player.getWorld(),
                        gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        created.add(scene);
        var state = load(scene, 19900121);
        assertEquals(2, state.getCachedVariables().get("stage"));
        assertEquals(2, state.getActiveSuiteId());
        assertTrue(state.getDeadEntities().contains(12001));
        assertTrue(state.isScriptGadgetDestroyed(12002));
    }

    @Test
    void ephemeralGroupsIgnoreSavedStageAndEntityRemovals() throws Exception {
        saveOld(19900122);
        var state = load(scene(false), 19900122);
        assertEquals(0, state.getCachedVariables().get("stage"));
        assertEquals(0, state.getActiveSuiteId());
        assertEquals(0, state.getTargetSuiteId());
        assertTrue(state.getDeadEntities().isEmpty());
        assertFalse(state.isScriptGadgetDestroyed(12002));
        assertTrue(state.getCachedGadgetStates().isEmpty());
    }

    @Test
    void ephemeralGroupSaveAndSceneSaveCannotWriteOrReplaceDatabaseProgress() throws Exception {
        saveOld(19900123);
        var scene = scene(false);
        var state = load(scene, 19900123);
        state.getCachedVariables().put("stage", 1);
        state.setCached(true);
        state.save();
        scene.saveGroups();
        var fresh = load(scene, 19900124);
        fresh.save();
        scene.saveGroups();
        drain();
        var saved = DatabaseHelper.loadGroupInstance(19900123, player);
        assertNotNull(saved);
        assertEquals(2, saved.getCachedVariables().get("stage"));
        assertNull(DatabaseHelper.loadGroupInstance(19900124, player));
        assertEquals(1, store.getDatabase().getCollection("group_instances").countDocuments());
    }

    @Test
    void ephemeralStateSurvivesReloadWithinTheSameSceneAndResetsInANewScene() throws Exception {
        var first = scene(false);
        var before = load(first, 19900125);
        before.getCachedVariables().put("stage", 1);
        first.getScriptManager().loadGroupFromScript(group(19900125));
        assertSame(before, first.getScriptManager().getCachedGroupInstanceById(19900125));
        assertEquals(1, before.getCachedVariables().get("stage"));
        var other = load(scene(false), 19900125);
        assertNotSame(before, other);
        assertEquals(0, other.getCachedVariables().get("stage"));
    }

    @Test
    void persistentModeCanStillCreateAndSaveANewGroup() throws Exception {
        var scene = scene(true);
        var state = load(scene, 19900126);
        state.getCachedVariables().put("stage", 3);
        scene.saveGroups();
        drain();
        var saved = DatabaseHelper.loadGroupInstance(19900126, player);
        assertNotNull(saved);
        assertEquals(3, saved.getCachedVariables().get("stage"));
    }
}
