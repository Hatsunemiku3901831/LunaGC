package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.exec.ExecRefreshGroupSuite;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.luaj.vm2.script.LuaScriptEngineFactory;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Actual loader completion, scene scheduler, quest lifecycle and Mongo suite persistence. */
class QuestSuiteInitializationTest extends QuestTestFixture {
    private static final int GROUP = 133003002;
    private final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
    private final AtomicReference<Thread> loader = new AtomicReference<>();
    private Scene target;
    private SceneScriptManager scripts;
    private GameQuest quest;
    private boolean fail;

    @BeforeEach
    void pendingScripts() throws Exception {
        quest = active(1100509);
        replaceField(
                ScriptLoader.class, null, "engine", new LuaScriptEngineFactory().getScriptEngine());
        replaceField(ScriptLoader.class, null, "scriptLib", new ScriptLib());
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
                            if (!released.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("Loader barrier timed out");
                        } catch (InterruptedException error) {
                            throw new AssertionError(error);
                        }
                        if (fail) throw new IllegalStateException("Controlled loader failure");
                        return super.get();
                    }
                };
        replaceField(
                ScriptLoader.class,
                null,
                "sceneMetaCache",
                new ConcurrentHashMap<>(Map.of(3, reference)));
        target =
                new Scene(
                        player.getWorld(),
                        gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class));
        scripts = target.getScriptManager();
        player.setScene(target);
        player.setSceneId(3);
        target.getPlayers().add(player);
        player.getWorld().getScenes().put(3, target);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var group = SceneGroup.of(GROUP);
        group.variables = new ArrayList<>();
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        group.suites = List.of(new SceneSuite(), new SceneSuite());
        map("localGroups").put(GROUP, group);
        map("sceneGroupsInstances").put(GROUP, new SceneGroupInstance(group, player));
        map("cachedSceneGroupsInstances").put(GROUP, scripts.getGroupInstanceById(GROUP));
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Integer, T> map(String name) throws Exception {
        var field = SceneScriptManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Integer, T>) field.get(scripts);
    }

    private boolean request(int suite) {
        return new ExecRefreshGroupSuite().execute(quest, null, "3", GROUP + "," + suite);
    }

    private void ready() throws Exception {
        released.countDown();
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
        assertFalse(scripts.getGroupInstanceById(GROUP).getLuaGroup().dontUnload);
    }

    @AfterEach
    void releaseAndDrain() throws Exception {
        ready();
        scripts.onDestroy();
        tick();
    }

    @Test
    void pendingQuestActionRunsOnTheSceneSchedulerAfterMetadataIsReady() throws Exception {
        assertTrue(request(2));
        unchanged();
        ready();
        unchanged();
        tick();
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertEquals(2, quest.getMainQuest().getQuestGroupSuites().get(0).getSuite());
        var saved =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(saved);
        assertEquals(2, saved.getQuestGroupSuites().get(0).getSuite());
        var raw =
                store.getDatabase()
                        .getCollection("quests")
                        .find(
                                new Document("ownerUid", player.getUid())
                                        .append("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(raw);
        assertFalse(
                raw.toJson().contains("executionRevision"),
                "Phase revision belongs only to this live quest object");
    }

    @Test
    void aFinishedQuestDoesNotReplayItsOldBeginAction() throws Exception {
        assertTrue(request(2));
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        ready();
        tick();
        unchanged();
    }

    @Test
    void actorLeavingTheWorldInvalidatesItsDeferredAction() throws Exception {
        assertTrue(request(2));
        player.setWorld(new World(player.server, player));
        ready();
        tick();
        unchanged();
    }

    @Test
    void replacingTheSceneInstanceInvalidatesItsDeferredAction() throws Exception {
        assertTrue(request(2));
        player.getWorld().getScenes().remove(3);
        ready();
        tick();
        unchanged();
    }

    @Test
    void replacingTheSavedQuestInstanceInvalidatesTheOldAction() throws Exception {
        assertTrue(request(2));
        player.getQuestManager().addMainQuest(quest.getQuestData());
        ready();
        tick();
        unchanged();
    }

    @Test
    void reloadingTheQuestDefinitionInvalidatesTheOldAction() throws Exception {
        assertTrue(request(2));
        quest.setConfig(gson.fromJson(gson.toJson(quest.getQuestData()), QuestData.class));
        ready();
        tick();
        unchanged();
    }

    @Test
    void loaderFailureDoesNotApplyOrPersistAQueuedSuite() throws Exception {
        assertTrue(request(2));
        fail = true;
        ready();
        tick();
        unchanged();
    }

    @Test
    void destroyingBeforeLoaderCompletionDoesNotApplyTheQueuedSuite() throws Exception {
        assertTrue(request(2));
        scripts.onDestroy();
        ready();
        tick();
        unchanged();
    }

    @Test
    void destroyingAfterReadyBeforeSchedulerTickDropsTheQueuedSuite() throws Exception {
        assertTrue(request(2));
        ready();
        scripts.onDestroy();
        tick();
        unchanged();
    }

    @Test
    void alreadyReadyActionsRunSynchronously() throws Exception {
        ready();
        assertTrue(request(2));
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
    }

    @Test
    void initializationCompletingBetweenStatusReadsDoesNotRejectTheAction() throws Exception {
        var original = scripts;
        var options = Grasscutter.getConfig().server.game;
        boolean enabled = options.enableScriptInBigWorld;
        options.enableScriptInBigWorld = false;
        SceneScriptManager observed;
        try {
            observed =
                    new SceneScriptManager(target) {
                        @Override
                        public boolean isInit() {
                            return original.isInit();
                        }

                        @Override
                        public boolean isInitAttempted() {
                            try {
                                ready();
                            } catch (Exception error) {
                                throw new AssertionError(error);
                            }
                            return original.isInitAttempted();
                        }

                        @Override
                        public boolean isDestroyed() {
                            return original.isDestroyed();
                        }

                        @Override
                        public SceneGroup getGroupById(int groupId) {
                            return original.getGroupById(groupId);
                        }

                        @Override
                        public boolean refreshGroupSuite(
                                int groupId, int suiteId, GameQuest owner) {
                            return original.refreshGroupSuite(groupId, suiteId, owner);
                        }

                        @Override
                        public void whenInitialized(Consumer<Boolean> callback) {
                            original.whenInitialized(callback);
                        }
                    };
        } finally {
            options.enableScriptInBigWorld = enabled;
        }
        replaceField(Scene.class, target, "scriptManager", observed);
        assertTrue(request(2));
        tick();
        assertEquals(2, original.getGroupInstanceById(GROUP).getActiveSuiteId());
    }

    @Test
    void terminalFailedInitializationRejectsANewAction() throws Exception {
        fail = true;
        ready();
        assertFalse(request(2));
        unchanged();
    }

    @Test
    void stateChangingBackToTheSameValueDoesNotReviveAnOldAction() throws Exception {
        assertTrue(request(2));
        quest.setState(QuestState.QUEST_STATE_UNSTARTED);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        ready();
        tick();
        unchanged();
    }

    @Test
    void genuineQuestRestartWithTheSameTimestampsInvalidatesTheOldAction() throws Exception {
        var json = gson.toJsonTree(quest.getQuestData()).getAsJsonObject();
        json.add("beginExec", JsonParser.parseString("[]"));
        json.add(
                "finishCond",
                JsonParser.parseString(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param_str:'initialization_phase',count:1}]"));
        var config = gson.fromJson(json, QuestData.class);
        config.onLoad();
        GameData.getBeginCondQuestMap().clear();
        quest.setConfig(config);
        int acceptTime = quest.getAcceptTime(), startTime = quest.getStartTime();
        assertTrue(request(2));
        quest.rewind(false);
        quest.start();
        quest.setAcceptTime(acceptTime);
        quest.setStartTime(startTime);
        ready();
        tick();
        unchanged();
    }
}
