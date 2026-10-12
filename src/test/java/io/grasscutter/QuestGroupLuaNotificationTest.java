package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.exec.ExecNotifyGroupLua;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.script.SimpleBindings;

/**
 * Real loader, player transfer, scene scheduler and queued Lua events, including original Q39403.
 */
class QuestGroupLuaNotificationTest extends QuestTestFixture {
    private static final int GROUP = 133003002;
    private final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
    private final AtomicReference<Thread> loader = new AtomicReference<>();
    private final List<ScriptArgs> events = new CopyOnWriteArrayList<>();
    private Scene target, other;
    private ObservedScripts scripts;
    private GameQuest quest;
    private boolean fail;
    private Map<Integer, GadgetData> previousGadgets;

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    private static class ObservedScripts extends SceneScriptManager {
        final List<Future<?>> callbacks = new CopyOnWriteArrayList<>();

        ObservedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current, boolean lockScene) {
            var future = super.callEvent(args, current, lockScene);
            if (future != null) callbacks.add(future);
            return future;
        }
    }

    @BeforeEach
    void pending() throws Exception {
        quest = active(1100509);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        target = player.getScene();
        target.setDontDestroyWhenEmpty(true);
        player.getWorld().getScenes().put(3, target);
        player.getTeamManager().setEntity(new EntityTeam(player));
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
        scripts = new ObservedScripts(target);
        replaceField(Scene.class, target, "scriptManager", scripts);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var group = SceneGroup.of(GROUP);
        group.setLoaded(true);
        group.setBindings(new SimpleBindings());
        group.variables = new ArrayList<>();
        group.gadgets = new HashMap<>();
        group.monsters = new HashMap<>();
        group.regions = new HashMap<>();
        group.triggers = new HashMap<>();
        group.suites = List.of(new SceneSuite());
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        group.getBindings()
                .put(
                        "condition",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                return LuaValue.TRUE;
                            }
                        });
        group.getBindings()
                .put(
                        "action",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                events.add((ScriptArgs) args.arg(2).touserdata(ScriptArgs.class));
                                return LuaValue.ZERO;
                            }
                        });
        scripts.registerLocalGroups(List.of(group));
        scripts.loadGroupFromScript(group);
        for (int type : new int[] {EventType.EVENT_QUEST_FINISH, EventType.EVENT_QUEST_START}) {
            var trigger = new SceneTrigger();
            trigger.currentGroup = group;
            trigger.setName("quest_" + type);
            trigger.setCondition("condition");
            trigger.setAction("action");
            trigger.setSource(quest.getSubQuestId() + "");
            trigger.setEvent(type);
            scripts.registerTrigger(trigger);
        }
        boolean enabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            other =
                    new Scene(
                            player.getWorld(),
                            gson.fromJson("{id:9,type:'SCENE_WORLD'}", SceneData.class));
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = enabled;
        }
        other.setDontDestroyWhenEmpty(true);
        player.getWorld().getScenes().put(9, other);
        player.setScene(other);
        player.setSceneId(9);
        other.getPlayers().add(player);
        drain();
    }

    private boolean request() {
        return new ExecNotifyGroupLua().execute(quest, null, "3", GROUP + "");
    }

    private void ready() throws Exception {
        released.countDown();
        loader.get().join(5000);
        assertFalse(loader.get().isAlive());
    }

    private void tick() throws Exception {
        target.getScheduler().runTasks();
        for (var callback : List.copyOf(scripts.callbacks)) callback.get(5, TimeUnit.SECONDS);
        drain();
    }

    private void arrived() {
        target.addPlayer(player);
    }

    private void complete() throws Exception {
        ready();
        arrived();
        target.finishLoading();
        tick();
    }

    @AfterEach
    void close() throws Exception {
        ready();
        scripts.onDestroy();
        other.getScriptManager().onDestroy();
        tick();
        if (previousGadgets != null) {
            GameData.getGadgetDataMap().clear();
            GameData.getGadgetDataMap().putAll(previousGadgets);
        }
    }

    @Test
    void waitsForScriptsPlayerArrivalAndSceneLoadingThenDeliversOnce() throws Exception {
        assertTrue(request());
        tick();
        assertTrue(events.isEmpty());
        ready();
        target.finishLoading();
        tick();
        assertTrue(events.isEmpty());
        arrived();
        tick();
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals(GROUP, event.group_id);
        assertEquals(EventType.EVENT_QUEST_FINISH, event.type);
        assertEquals(1100509, event.param1);
        assertEquals(1, event.param2);
        assertEquals("1100509", event.getEventSource());
        target.finishLoading();
        arrived();
        tick();
        assertEquals(1, events.size());
    }

    @Test
    void arrivalBeforeInitializationAndLoadingDoesNotLoseTheNotification() throws Exception {
        assertTrue(request());
        arrived();
        tick();
        assertTrue(events.isEmpty());
        ready();
        tick();
        assertTrue(events.isEmpty());
        target.finishLoading();
        tick();
        assertEquals(1, events.size());
    }

    @Test
    void readySceneUsesTheSameGuardedSchedulerPath() throws Exception {
        complete();
        assertTrue(request());
        assertTrue(events.isEmpty());
        tick();
        assertEquals(1, events.size());
    }

    @Test
    void unfinishedQuestDeliversItsOriginalStartEvent() throws Exception {
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        assertTrue(request());
        complete();
        assertEquals(1, events.size());
        assertEquals(EventType.EVENT_QUEST_START, events.get(0).type);
        assertEquals(0, events.get(0).param2);
    }

    @Test
    void savedSceneIdWithoutLiveMembershipDoesNotAllowDelivery() throws Exception {
        ready();
        target.finishLoading();
        player.setSceneId(3);
        assertTrue(request());
        tick();
        assertTrue(events.isEmpty());
        arrived();
        tick();
        assertEquals(1, events.size());
    }

    @Test
    void sameUidFromAnotherPlayerSessionDoesNotReleaseTheWaiter() throws Exception {
        ready();
        target.finishLoading();
        assertTrue(request());
        var replacement = new TestPlayer();
        replacement.setUid(player.getUid());
        replacement.server = player.server;
        replacement.setWorld(player.getWorld());
        target.addPlayer(replacement);
        tick();
        assertTrue(events.isEmpty());
        target.removePlayer(replacement);
        arrived();
        tick();
        assertEquals(1, events.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "2147483648"})
    void malformedSceneOrGroupIdsAreRejected(String value) {
        var handler = new ExecNotifyGroupLua();
        assertFalse(handler.execute(quest, null, value, GROUP + ""));
        assertFalse(handler.execute(quest, null, "3", value));
    }

    @Test
    void missingArgumentsAndWorldAreRejectedWithoutExceptions() {
        var handler = new ExecNotifyGroupLua();
        assertFalse(handler.execute(null, null, "3", GROUP + ""));
        assertFalse(handler.execute(quest, null, (String[]) null));
        assertFalse(handler.execute(quest, null, "3"));
        player.setWorld(null);
        assertFalse(request());
    }

    @Test
    void aChangedQuestPhaseCannotTurnTheQueuedFinishIntoAStartEvent() throws Exception {
        assertTrue(request());
        quest.setState(QuestState.QUEST_STATE_FAILED);
        complete();
        assertTrue(events.isEmpty());
    }

    @Test
    void aRestoredStateStillInvalidatesThePreviousQuestRevision() throws Exception {
        assertTrue(request());
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        complete();
        assertTrue(events.isEmpty());
    }

    @Test
    void aReplacedQuestObjectCannotReceiveTheOlderObjectsNotification() throws Exception {
        assertTrue(request());
        active(1100509);
        complete();
        assertTrue(events.isEmpty());
    }

    @Test
    void aChangedResourceDefinitionInvalidatesTheWaitingAction() throws Exception {
        assertTrue(request());
        quest.setConfig(gson.fromJson(gson.toJson(quest.getQuestData()), QuestData.class));
        complete();
        assertTrue(events.isEmpty());
    }

    @Test
    void leavingTheOriginalWorldDropsTheWaitingAction() throws Exception {
        assertTrue(request());
        player.setWorld(new World(player.server, player));
        ready();
        target.finishLoading();
        tick();
        assertTrue(events.isEmpty());
    }

    @Test
    void destroyedAndFailedInitializationCannotDeliver() throws Exception {
        assertTrue(request());
        fail = true;
        complete();
        assertTrue(events.isEmpty());
        assertFalse(request());
    }

    @Test
    void destroyingAnInitializedTargetDropsItsAlreadyWaitingNotification() throws Exception {
        ready();
        assertTrue(request());
        scripts.onDestroy();
        arrived();
        target.finishLoading();
        tick();
        assertTrue(events.isEmpty());
        assertFalse(request());
    }

    @Test
    void sceneRemovedBeforeArrivalCannotDeliverIntoTheDetachedInstance() throws Exception {
        assertTrue(request());
        player.getWorld().getScenes().remove(3);
        complete();
        assertTrue(events.isEmpty());
    }

    @Test
    void leavingAgainBeforeTheScheduledDeliveryDropsTheAction() throws Exception {
        assertTrue(request());
        ready();
        arrived();
        target.finishLoading();
        other.addPlayer(player);
        tick();
        assertTrue(events.isEmpty());
    }

    @Test
    void revisionIsRecheckedInsideTheAlreadyQueuedLuaCallback() throws Exception {
        complete();
        var occupied = new CountDownLatch(4);
        var releaseWorkers = new CountDownLatch(1);
        var blockers = new ArrayList<Future<?>>();
        try {
            for (int n = 0; n < 4; n++)
                blockers.add(
                        SceneScriptManager.eventExecutor.submit(
                                () -> {
                                    occupied.countDown();
                                    try {
                                        assertTrue(releaseWorkers.await(5, TimeUnit.SECONDS));
                                    } catch (InterruptedException error) {
                                        throw new AssertionError(error);
                                    }
                                }));
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            assertTrue(request());
            target.getScheduler().runTasks();
            assertFalse(scripts.callbacks.isEmpty());
            quest.setState(QuestState.QUEST_STATE_UNFINISHED);
            quest.setState(QuestState.QUEST_STATE_FINISHED);
        } finally {
            releaseWorkers.countDown();
        }
        for (var blocker : blockers) blocker.get(5, TimeUnit.SECONDS);
        tick();
        assertTrue(events.isEmpty());
    }

    @Test
    void original39403FinishNotificationRunsNativeLuaAndPersistsAllFiveSealRemovals()
            throws Exception {
        ready();
        store.getMapper().map(SceneGroupInstance.class);
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class)) {
            if (row.getMainId() == 394) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/394.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(394, main);
        GameData.getBeginCondQuestMap().clear();
        quest = active(39403);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        previousGadgets = new HashMap<>(GameData.getGadgetDataMap());
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json")),
                        GadgetData[].class)) GameData.getGadgetDataMap().put(row.getId(), row);
        replaceField(GameServer.class, player.server, "dropSystem", new DropSystem(player.server));
        int[][] seals = {
            {133007004, 7}, {133007076, 300}, {133007078, 304}, {133007079, 306}, {133007001, 1}
        };
        for (var seal : seals) {
            var group = SceneGroup.of(seal[0]);
            scripts.registerLocalGroups(List.of(group));
            scripts.loadGroupFromScript(group);
            var gadget = scripts.createGadget(group.id, 3007, group.gadgets.get(seal[1]));
            assertNotNull(gadget);
            target.getEntities().put(gadget.getId(), gadget);
        }
        var origin = SceneGroup.of(133007183).load(3);
        origin.block_id = 3007;
        scripts.getLoadedGroupSetPerBlock().put(3007, new HashSet<>());
        scripts.registerLocalGroups(List.of(origin));
        var action = (LuaValue) origin.getBindings().get("action_EVENT_QUEST_FINISH_185");
        origin.getBindings()
                .put(
                        "action_EVENT_QUEST_FINISH_185",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                var result = action.invoke(args);
                                events.add((ScriptArgs) args.arg(2).touserdata(ScriptArgs.class));
                                assertEquals(0, result.arg1().toint());
                                return result;
                            }
                        });
        var notify =
                quest.getQuestData().getFinishExec().stream()
                        .filter(exec -> exec.getType() == QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA)
                        .findFirst()
                        .orElseThrow();
        assertArrayEquals(new String[] {"3", "133007183"}, notify.getParam());
        target.finishLoading();
        assertTrue(new ExecNotifyGroupLua().execute(quest, notify, notify.getParam()));
        tick();
        assertTrue(events.isEmpty());
        arrived();
        tick();
        assertEquals(1, events.size());
        assertEquals(39403, events.get(0).param1);
        for (var seal : seals) {
            assertNull(target.getEntityByConfigId(seal[1], seal[0]));
            var cold = DatabaseHelper.loadGroupInstance(seal[0], player);
            assertNotNull(cold);
            assertTrue(cold.isScriptGadgetDestroyed(seal[1]));
        }
    }
}
