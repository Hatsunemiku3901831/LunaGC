package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLib;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.script.LuaScriptEngineFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actual suite transitions and quest persistence, with controlled empty scene groups. */
class QuestSuitePersistenceTest extends QuestTestFixture {
    private static final int GROUP = 133003002;
    private SceneScriptManager scripts;
    private GameQuest quest;

    @BeforeEach
    void suites() throws Exception {
        quest = active(1100509);
        scripts = player.getScene().getScriptManager();
        // Invalid suite reloads must see an absent Lua file, not the real group's definition.
        replaceField(
                ScriptLoader.class, null, "engine", new LuaScriptEngineFactory().getScriptEngine());
        replaceField(ScriptLoader.class, null, "scriptLib", new ScriptLib());
        var group = SceneGroup.of(GROUP);
        group.overrideScriptPath = "Scene/3/__quest_suite_fixture_missing.lua";
        group.variables = new ArrayList<>();
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        group.suites = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            var suite = new SceneSuite();
            suite.sceneMonsters = new ArrayList<>();
            suite.sceneGadgets = new ArrayList<>();
            suite.sceneRegions = new ArrayList<>();
            suite.sceneTriggers = new ArrayList<>();
            group.suites.add(suite);
        }
        map("sceneGroupsInstances").put(GROUP, new SceneGroupInstance(group, player));
        map("cachedSceneGroupsInstances").put(GROUP, scripts.getGroupInstanceById(GROUP));
    }

    @AfterEach
    void finishScriptEvents() throws Exception {
        SceneScriptManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        drain();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Integer, T> map(String field) throws Exception {
        var member = SceneScriptManager.class.getDeclaredField(field);
        member.setAccessible(true);
        return (Map<Integer, T>) member.get(scripts);
    }

    private QuestGroupSuite row(int scene, int group, int suite) {
        return QuestGroupSuite.of().scene(scene).group(group).suite(suite).build();
    }

    private List<QuestGroupSuite> saved() {
        return quest.getMainQuest().getQuestGroupSuites();
    }

    private List<QuestGroupSuite> persisted() {
        var main =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(main);
        return main.getQuestGroupSuites();
    }

    private void apply(int suite) throws Exception {
        assertTrue(scripts.refreshGroupSuite(GROUP, suite, quest));
        drain();
    }

    private void reload() throws Exception {
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        quest = player.getQuestManager().getQuestById(1100509);
        assertNotNull(quest);
    }

    @Test
    void successfulRuntimeTransitionIsSavedBeforeRelog() throws Exception {
        apply(2);
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        assertEquals(List.of(row(3, GROUP, 2)), persisted());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), player.getQuestManager().getSceneGroupSuite(3));
    }

    @Test
    void newerSuiteReplacesItsOldWaveAndPersistsOnlyOneEntry() throws Exception {
        apply(1);
        apply(2);
        apply(3);
        assertEquals(List.of(row(3, GROUP, 3)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP, 3)), saved());
        // Use the scene entry consumer to apply only the saved latest wave after cold loading.
        var instance = scripts.getGroupInstanceById(GROUP);
        instance.setActiveSuiteId(1);
        map("localGroups").put(GROUP, instance.getLuaGroup());
        replaceField(SceneScriptManager.class, scripts, "isInit", true);
        player.getScene().loadGroupForQuest(player.getQuestManager().getSceneGroupSuite(3));
        assertEquals(3, instance.getActiveSuiteId());
    }

    @Test
    void repeatedRefreshDoesNotAppendDuplicateOverrides() throws Exception {
        apply(2);
        apply(2);
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
    }

    @Test
    void successfulDefaultResetRemovesOnlyItsOwnScopeAndPersistsCleanup() throws Exception {
        saved().add(row(3, GROUP, 2));
        saved().add(row(3, GROUP + 1, 1));
        saved().add(row(4, GROUP, 1));
        quest.save();
        drain();
        apply(0);
        assertEquals(1, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(List.of(row(3, GROUP + 1, 1), row(4, GROUP, 1)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP + 1, 1)), player.getQuestManager().getSceneGroupSuite(3));
        assertEquals(List.of(row(4, GROUP, 1)), player.getQuestManager().getSceneGroupSuite(4));
    }

    @Test
    void legacyDuplicatesCollapseWhenARealTransitionIsApplied() throws Exception {
        saved().add(row(3, GROUP, 1));
        saved().add(row(3, GROUP, 2));
        saved().add(row(3, GROUP, 2));
        apply(2);
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), saved());
    }

    @Test
    void failedMissingGroupDoesNotCreateAColdOverride() throws Exception {
        assertFalse(scripts.refreshGroupSuite(GROUP + 99, 2, quest));
        drain();
        assertTrue(saved().isEmpty());
        assertTrue(persisted().isEmpty());
    }

    @Test
    void failedResetPreservesAnExistingSavedOverride() throws Exception {
        saved().add(row(3, GROUP + 99, 2));
        quest.save();
        drain();
        assertFalse(scripts.refreshGroupSuite(GROUP + 99, 0, quest));
        drain();
        reload();
        assertEquals(List.of(row(3, GROUP + 99, 2)), saved());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4})
    void unavailableSuiteDoesNotReplaceTheSavedOrActiveWave(int suite) throws Exception {
        apply(2);
        assertFalse(scripts.refreshGroupSuite(GROUP, suite, quest));
        drain();
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), saved());
    }

    @Test
    void resetWithNoOverrideIsIdempotent() throws Exception {
        apply(0);
        apply(0);
        assertTrue(saved().isEmpty());
        reload();
        assertTrue(saved().isEmpty());
    }

    @Test
    void deferredBanRefreshKeepsThePreviousWaveUntilTransitionApplies() throws Exception {
        apply(1);
        scripts.getGroupInstanceById(GROUP).getLuaGroup().getSuiteByIndex(1).ban_refresh = true;
        assertFalse(scripts.refreshGroupSuite(GROUP, 2, quest));
        drain();
        assertEquals(1, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getTargetSuiteId());
        assertEquals(List.of(row(3, GROUP, 1)), saved());
        assertEquals(List.of(row(3, GROUP, 1)), persisted());
        apply(2);
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), saved());
    }

    @Test
    void coldChapterWithoutOverridesCanPersistItsFirstTransition() throws Exception {
        reload();
        apply(2);
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        assertEquals(List.of(row(3, GROUP, 2)), persisted());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), saved());
    }

    @Test
    void explicitLegacyNullListCanReceiveAndSaveANewOverride() throws Exception {
        var collection = store.getDatabase().getCollection("quests");
        assertEquals(
                1,
                collection
                        .updateOne(
                                new Document("ownerUid", player.getUid())
                                        .append("parentQuestId", quest.getMainQuestId()),
                                new Document("$set", new Document("questGroupSuites", null)))
                        .getMatchedCount());
        reload();
        apply(2);
        assertEquals(List.of(row(3, GROUP, 2)), saved());
        reload();
        assertEquals(List.of(row(3, GROUP, 2)), saved());
    }

    @Test
    void changingAPendingWaveRequiresThatNewWaveToBeRequestedAgain() throws Exception {
        apply(1);
        scripts.getGroupInstanceById(GROUP).getLuaGroup().getSuiteByIndex(1).ban_refresh = true;
        assertFalse(scripts.refreshGroupSuite(GROUP, 2, quest));
        assertFalse(scripts.refreshGroupSuite(GROUP, 3, quest));
        assertEquals(3, scripts.getGroupInstanceById(GROUP).getTargetSuiteId());
        assertEquals(1, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(List.of(row(3, GROUP, 1)), saved());
        apply(3);
        assertEquals(3, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        reload();
        assertEquals(List.of(row(3, GROUP, 3)), saved());
    }
}
