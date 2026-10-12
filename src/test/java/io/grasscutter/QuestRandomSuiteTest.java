package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.exec.ExecRefreshGroupSuiteRandom;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual initialization, random quest handler, suite transition and Mongo persistence. */
class QuestRandomSuiteTest extends QuestTestFixture {
    private static final int GROUP = 133003002;
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private final AtomicReference<Thread> loader = new AtomicReference<>();
    private Scene target;
    private SceneScriptManager scripts;
    private SceneGroup group;
    private GameQuest quest;
    private boolean fail;

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void pending() throws Exception {
        quest = active(1100509);
        target = player.getScene();
        player.setSceneId(3);
        target.getPlayers().add(player);
        player.getWorld().getScenes().put(3, target);
        store.getMapper().map(SceneGroupInstance.class);
        var meta = new SceneMeta();
        meta.config = new SceneConfig();
        meta.blocks = new HashMap<>();
        var reference =
                new SoftReference<SceneMeta>(meta) {
                    @Override
                    public SceneMeta get() {
                        loader.set(Thread.currentThread());
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("Loader barrier timed out");
                        } catch (InterruptedException error) {
                            throw new AssertionError(error);
                        }
                        if (fail)
                            throw new IllegalStateException("Controlled initialization failure");
                        return super.get();
                    }
                };
        replaceField(
                ScriptLoader.class,
                null,
                "sceneMetaCache",
                new ConcurrentHashMap<>(Map.of(3, reference)));
        scripts = new SceneScriptManager(target);
        replaceField(Scene.class, target, "scriptManager", scripts);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        group = emptyGroup(GROUP);
        scripts.registerLocalGroups(List.of(group));
        scripts.loadGroupFromScript(group);
        drain();
    }

    private SceneGroup emptyGroup(int id) {
        var group = SceneGroup.of(id);
        group.setLoaded(true);
        group.overrideScriptPath = "Scene/3/__random_suite_missing.lua";
        group.variables = new ArrayList<>();
        group.gadgets = new HashMap<>();
        group.monsters = new HashMap<>();
        group.regions = new HashMap<>();
        group.triggers = new HashMap<>();
        group.suites = List.of(new SceneSuite(), new SceneSuite(), new SceneSuite());
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        return group;
    }

    private boolean request(String choices) {
        return new ExecRefreshGroupSuiteRandom().execute(quest, null, "3", choices);
    }

    private void ready() throws Exception {
        release.countDown();
        loader.get().join(5000);
        assertFalse(loader.get().isAlive());
    }

    private void tick() throws Exception {
        target.getScheduler().runTasks();
        SceneScriptManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        drain();
    }

    private void unchanged() {
        assertEquals(0, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
        assertFalse(group.dontUnload);
    }

    @AfterEach
    void close() throws Exception {
        ready();
        scripts.onDestroy();
        tick();
    }

    @Test
    void pendingActionDoesNotApplyUntilTheRealLoaderCompletesAndTheSceneTicks() throws Exception {
        assertTrue(request(GROUP + ",2,3"));
        unchanged();
        ready();
        unchanged();
        tick();
        int chosen = scripts.getGroupInstanceById(GROUP).getActiveSuiteId();
        assertTrue(chosen == 2 || chosen == 3);
        assertTrue(group.dontUnload);
        var saved = quest.getMainQuest().getQuestGroupSuites();
        assertEquals(
                List.of(QuestGroupSuite.of().scene(3).group(GROUP).suite(chosen).build()), saved);
        tick();
        assertEquals(chosen, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(1, saved.size());
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        assertEquals(saved, player.getQuestManager().getSceneGroupSuite(3));
    }

    @Test
    void initializedActionPinsItsSelectedSuiteAndResetClearsItsSavedOverride() throws Exception {
        ready();
        assertTrue(request(GROUP + ",2"));
        assertTrue(group.dontUnload);
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(1, quest.getMainQuest().getQuestGroupSuites().size());
        assertTrue(request(GROUP + ",0"));
        assertEquals(1, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void independentDynamicVisibilitySurvivesASelectedDefaultReset() throws Exception {
        ready();
        group.dynamic_load = true;
        group.dontUnload = true;
        assertTrue(request(GROUP + ",2"));
        assertTrue(request(GROUP + ",0"));
        assertTrue(group.dontUnload);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "133003002",
                "133003002,",
                "0,1",
                "-1,1",
                "133003002,-1",
                "133003002,bad",
                "133003002,2147483648",
                "133003002,2;bad,1",
                "133003002,2;133003003",
                "133003002,2;"
            })
    void invalidWholeConfigurationDoesNotPartiallyApply(String choices) throws Exception {
        ready();
        assertFalse(request(choices));
        unchanged();
    }

    @Test
    void missingQuestSceneOrParametersAreRejected() throws Exception {
        ready();
        var handler = new ExecRefreshGroupSuiteRandom();
        assertFalse(handler.execute(null, null, "3", GROUP + ",2"));
        assertFalse(handler.execute(quest, null, (String[]) null));
        assertFalse(handler.execute(quest, null, "3"));
        assertFalse(handler.execute(quest, null, "bad", GROUP + ",2"));
        assertFalse(handler.execute(quest, null, "0", GROUP + ",2"));
        assertFalse(handler.execute(quest, null, "3", (String) null));
        unchanged();
    }

    @Test
    void stateChangeBeforeInitializationDropsTheOriginalSelectedAction() throws Exception {
        assertTrue(request(GROUP + ",2"));
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        ready();
        tick();
        unchanged();
    }

    @Test
    void restoredStateStillDropsThePreviousExecutionRevision() throws Exception {
        assertTrue(request(GROUP + ",2"));
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        ready();
        tick();
        unchanged();
    }

    @Test
    void replacingTheQuestDefinitionDropsTheOldAction() throws Exception {
        assertTrue(request(GROUP + ",2"));
        quest.setConfig(gson.fromJson(gson.toJson(quest.getQuestData()), QuestData.class));
        ready();
        tick();
        unchanged();
    }

    @Test
    void replacingTheSavedQuestObjectDropsTheOldAction() throws Exception {
        assertTrue(request(GROUP + ",2"));
        quest = active(1100509);
        ready();
        tick();
        unchanged();
    }

    @Test
    void leavingTheOriginalWorldDropsTheDeferredChoice() throws Exception {
        assertTrue(request(GROUP + ",2"));
        player.setWorld(null);
        ready();
        tick();
        unchanged();
    }

    @Test
    void allGroupsInAValidActionUseTheSharedVisibilityAndPersistenceRules() throws Exception {
        ready();
        var second = emptyGroup(GROUP + 1);
        scripts.registerLocalGroups(List.of(second));
        scripts.loadGroupFromScript(second);
        assertTrue(request(GROUP + ",2;" + second.id + ",3"));
        assertTrue(group.dontUnload);
        assertTrue(second.dontUnload);
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(3, scripts.getGroupInstanceById(second.id).getActiveSuiteId());
        assertEquals(2, quest.getMainQuest().getQuestGroupSuites().size());
    }

    @Test
    void unavailableInitializationDoesNotApplyAnAlreadyAcceptedChoice() throws Exception {
        assertTrue(request(GROUP + ",2"));
        fail = true;
        ready();
        tick();
        unchanged();
        assertFalse(request(GROUP + ",2"));
    }

    @Test
    void aClosedTargetDoesNotApplyAnAlreadyAcceptedChoice() throws Exception {
        assertTrue(request(GROUP + ",2"));
        scripts.onDestroy();
        ready();
        tick();
        unchanged();
        assertFalse(request(GROUP + ",2"));
    }

    @Test
    void removedSceneDoesNotApplyTheOldDeferredChoice() throws Exception {
        assertTrue(request(GROUP + ",2"));
        player.getWorld().getScenes().remove(3);
        ready();
        tick();
        unchanged();
    }

    @Test
    void missingGroupsAndFailedSuitesCannotCreateOverridesOrLeakVisibilityPins() throws Exception {
        ready();
        assertFalse(request("999,2"));
        assertFalse(request(GROUP + ",9999"));
        unchanged();
    }

    @Test
    void original2000502RandomChoicesSelectAnExistingNativeSuiteAndPersistIt() throws Exception {
        ready();
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class)) {
            if (row.getMainId() == 20005) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/20005.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(20005, main);
        GameData.getBeginCondQuestMap().clear();
        quest = active(2000502);
        var action =
                quest.getQuestData().getBeginExec().stream()
                        .filter(
                                exec ->
                                        exec.getType()
                                                == emu.grasscutter.game.quest.enums.QuestExec
                                                        .QUEST_EXEC_REFRESH_GROUP_SUITE_RANDOM)
                        .findFirst()
                        .orElseThrow();
        assertArrayEquals(new String[] {"3", "133001302,2,3"}, action.getParam());
        var nativeGroup = SceneGroup.of(133001302).load(3);
        assertNotNull(nativeGroup.getSuiteByIndex(2));
        assertNotNull(nativeGroup.getSuiteByIndex(3));
        scripts.registerLocalGroups(List.of(nativeGroup));
        scripts.loadGroupFromScript(nativeGroup);
        player.server.getQuestSystem().triggerExec(quest, action, action.getParam());
        drain();
        int chosen = scripts.getGroupInstanceById(nativeGroup.id).getActiveSuiteId();
        assertTrue(chosen == 2 || chosen == 3);
        assertTrue(nativeGroup.dontUnload);
        var selected = QuestGroupSuite.of().scene(3).group(nativeGroup.id).suite(chosen).build();
        assertEquals(List.of(selected), quest.getMainQuest().getQuestGroupSuites());
        var cold =
                store.find(GameMainQuest.class)
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "ownerUid", player.getUid()),
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "parentQuestId", 20005))
                        .first();
        assertNotNull(cold);
        assertEquals(List.of(selected), cold.getQuestGroupSuites());
    }
}
