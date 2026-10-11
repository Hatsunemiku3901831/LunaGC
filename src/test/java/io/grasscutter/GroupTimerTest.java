package io.grasscutter;

import static emu.grasscutter.scripts.constants.EventType.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager;
import emu.grasscutter.net.proto.ShowTemplateReminderNotifyOuterClass.ShowTemplateReminderNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import javax.script.SimpleBindings;

class GroupTimerTest {
    private AbilityTestFixture environment;
    private TimerScene scene;
    private TimerScripts scripts;
    private SceneGroup group;
    private ScriptLib library;
    private SimpleBindings bindings;
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private Consumer<ScriptArgs> action = event -> {};

    @BeforeAll
    static void init() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        scene =
                new TimerScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), environment.player));
        scripts = scene.scripts;
        group = group(100);
        trigger(group, "alarm", "alarm");
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scripts);
        library.setCurrentGroup(group);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        var player = environment.player;
        player.setUid(10001);
        player.setPeerId(1);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
    }

    @AfterEach
    void cleanup() throws Exception {
        scripts.onDestroy();
        scripts.drain();
        library.removeSceneScriptManager();
        library.removeCurrentGroup();
        library.removeCurrentEntity();
        library.removeCurrentCallParams();
        environment.close();
    }

    private SceneGroup group(int id) {
        var result = SceneGroup.of(id);
        result.setBindings(new SimpleBindings());
        result.triggers = new HashMap<>();
        result.variables = new ArrayList<>();
        scripts.loaded.put(id, result);
        return result;
    }

    private void trigger(SceneGroup owner, String triggerName, String source) {
        owner.getBindings()
                .put(
                        triggerName,
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                var event = (ScriptArgs) args.arg(2).touserdata(ScriptArgs.class);
                                try {
                                    assertTrue(Thread.holdsLock(scene));
                                    assertSame(owner, library.getCurrentGroup().orElseThrow());
                                    assertEquals(source, event.source_name);
                                    events.add(owner.id + ":" + triggerName);
                                    action.accept(event);
                                } catch (Throwable error) {
                                    failure.set(error);
                                }
                                return LuaValue.ZERO;
                            }
                        });
        var trigger = new SceneTrigger();
        trigger.currentGroup = owner;
        trigger.setName(triggerName);
        trigger.setAction(triggerName);
        trigger.setCondition("");
        trigger.setEvent(EVENT_TIMER_EVENT);
        trigger.setSource(source);
        trigger.setTrigger_count(0);
        owner.triggers.put(triggerName, trigger);
        scripts.registerTrigger(trigger);
    }

    private int lua(String expression) throws Exception {
        return ((Number) ScriptLoader.getEngine().eval("return " + expression, bindings))
                .intValue();
    }

    private void tick(int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) {
            scene.getScheduler().runTasks();
            scripts.drain();
            scripts.tickGroupTimers();
        }
        assertNull(failure.get());
    }

    @Test
    void firesOnceAtTheRequestedDelay() throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 3));
        tick(2);
        assertTrue(events.isEmpty());
        tick(1);
        assertEquals(List.of("100:alarm"), events);
        tick(12);
        assertEquals(1, events.size());
    }

    @Test
    void fractionsRoundUpAndZeroRunsOnTheNextSceneTick() throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1.5));
        tick(1);
        assertTrue(events.isEmpty());
        tick(1);
        assertEquals(1, events.size());
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 0));
        assertEquals(1, events.size());
        tick(1);
        assertEquals(2, events.size());
    }

    @Test
    void actionCanRescheduleTheSameSourceWithoutItsNextTimerBeingCancelled() throws Exception {
        action =
                event -> {
                    if (events.size() < 3)
                        assertEquals(0, library.CreateGroupTimerEvent(100, "alarm", 2));
                };
        assertEquals(0, library.CreateGroupTimerEvent(100, "alarm", 2));
        tick(6);
        assertEquals(3, events.size());
        tick(8);
        assertEquals(3, events.size());
        assertTrue(
                scripts.getTriggersByEvent(EVENT_TIMER_EVENT)
                        .contains(group.triggers.get("alarm")));
    }

    @Test
    void sameSourceInDifferentGroupsAndDifferentSourcesInOneGroupAreIndependent() throws Exception {
        var other = group(200);
        trigger(other, "alarm", "alarm");
        trigger(group, "second", "second");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        assertEquals(0, scripts.createGroupTimerEvent(200, "alarm", 2));
        assertEquals(0, scripts.createGroupTimerEvent(100, "second", 3));
        tick(3);
        assertEquals(Set.of("100:alarm", "200:alarm", "100:second"), new HashSet<>(events));
    }

    @Test
    void oneSourceInvokesMultipleMatchingTriggersWithoutCreatingDuplicateTimerTasks()
            throws Exception {
        trigger(group, "second", "alarm");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        tick(3);
        assertEquals(2, events.size());
        assertEquals(Set.of("100:alarm", "100:second"), new HashSet<>(events));
    }

    @Test
    void replacementAndCancellationAffectOnlyTheirExactSource() throws Exception {
        trigger(group, "second", "second");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 3));
        assertEquals(0, scripts.createGroupTimerEvent(100, "second", 1));
        tick(1);
        assertEquals(List.of("100:second"), events);
        assertEquals(0, scripts.cancelGroupTimerEvent(100, "alarm"));
        tick(4);
        assertEquals(1, events.size());
    }

    @Test
    void cancelGroupAndSceneDestructionDiscardPendingTimers() throws Exception {
        var other = group(200);
        trigger(other, "alarm", "alarm");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        assertEquals(0, scripts.createGroupTimerEvent(200, "alarm", 1));
        scripts.cancelGroupTimers(100);
        tick(1);
        assertEquals(List.of("200:alarm"), events);
        assertEquals(0, scripts.createGroupTimerEvent(200, "alarm", 1));
        scripts.onDestroy();
        tick(2);
        assertEquals(1, events.size());
        assertEquals(-1, scripts.createGroupTimerEvent(200, "alarm", 1));
    }

    @Test
    void unloadedOrReplacedGroupDoesNotReceiveOldTimers() throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        scripts.loaded.remove(100);
        tick(2);
        assertTrue(events.isEmpty());
        var replacement = group(100);
        trigger(replacement, "new", "alarm");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        var newer = group(100);
        trigger(newer, "newer", "alarm");
        tick(2);
        assertTrue(events.isEmpty());
    }

    @Test
    void refreshGroupCancelsTimersFromItsPreviousSuite() throws Exception {
        group.monsters = new HashMap<>();
        group.gadgets = new HashMap<>();
        group.regions = new HashMap<>();
        var suite = new SceneSuite();
        suite.sceneMonsters = List.of();
        suite.sceneGadgets = List.of();
        suite.sceneRegions = List.of();
        suite.sceneTriggers = List.of();
        group.suites = List.of(suite);
        var instance = new SceneGroupInstance(group, environment.player);
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        assertEquals(1, scripts.refreshGroup(instance, 1, false));
        tick(2);
        assertTrue(events.isEmpty());
    }

    @Test
    void cancelledQueuedCallbackDoesNotReachLua() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var blockers = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 4; i++)
                blockers.add(
                        SceneScriptManager.eventExecutor.submit(
                                () -> {
                                    entered.countDown();
                                    release.await(5, TimeUnit.SECONDS);
                                    return null;
                                }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
            scene.getScheduler().runTasks();
            assertEquals(0, scripts.cancelGroupTimerEvent(100, "alarm"));
            release.countDown();
            for (var task : blockers) task.get(5, TimeUnit.SECONDS);
            scripts.drain();
            assertTrue(events.isEmpty());
            assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
            tick(1);
            assertEquals(1, events.size());
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0/0", "math.huge", "2147483648", "'1'", "nil"})
    void invalidDelaysDoNotLoadOrReplaceAnExistingTimer(String value) throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        int loads = scripts.loads;
        assertEquals(
                -1, lua("ScriptLib.CreateGroupTimerEvent(ScriptLib,200,'alarm'," + value + ")"));
        assertEquals(loads, scripts.loads);
        tick(1);
        assertEquals(1, events.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "'100'", "2147483648", "nil"})
    void invalidIdsCannotLoadOrCancelGroups(String id) throws Exception {
        int loads = scripts.loads;
        assertEquals(-1, lua("ScriptLib.CreateGroupTimerEvent(ScriptLib," + id + ",'alarm',1)"));
        assertEquals(-1, lua("ScriptLib.CancelGroupTimerEvent(ScriptLib," + id + ",'alarm')"));
        assertEquals(loads, scripts.loads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"''", "' '", "1", "nil"})
    void invalidSourcesCannotLoadGroups(String source) throws Exception {
        int loads = scripts.loads;
        assertEquals(-1, lua("ScriptLib.CreateGroupTimerEvent(ScriptLib,200," + source + ",1)"));
        assertEquals(-1, lua("ScriptLib.CancelGroupTimerEvent(ScriptLib,100," + source + ")"));
        assertEquals(loads, scripts.loads);
    }

    @Test
    void absentMatchingTriggerDoesNotReplaceAnExistingTimer() throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        assertEquals(-1, scripts.createGroupTimerEvent(100, "unknown", 1));
        tick(1);
        assertEquals(1, events.size());
    }

    @Test
    void staleLuaOriginCannotCreateOrCancelTimers() throws Exception {
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        scripts.loaded.remove(100);
        assertEquals(-1, lua("ScriptLib.CreateGroupTimerEvent(ScriptLib,200,'alarm',1)"));
        assertEquals(-1, lua("ScriptLib.CancelGroupTimerEvent(ScriptLib,100,'alarm')"));
    }

    @Test
    void oldTriggerCannotRunOnATimerCreatedByItsReplacementGroup() throws Exception {
        var replacement = group(100);
        trigger(replacement, "new", "alarm");
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        tick(1);
        assertEquals(List.of("100:new"), events);
    }

    @Test
    void reminderBroadcastsSignedParametersFromTheCurrentLuaGroup() throws Exception {
        scene.packets.clear();
        assertEquals(
                0,
                lua("ScriptLib.ShowTemplateReminder(ScriptLib,110,{0,-1,-2147483648,2147483647})"));
        assertEquals(1, scene.packets.size());
        var message = ShowTemplateReminderNotify.parseFrom(scene.packets.get(0).getData());
        assertEquals(110, message.getTemplateReminderId());
        assertEquals(
                List.of(0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE), message.getParamListList());
        assertTrue(message.getParamUidListList().isEmpty());
    }

    @Test
    void reminderAcceptsAnEmptyParameterList() throws Exception {
        scene.packets.clear();
        assertEquals(0, lua("ScriptLib.ShowTemplateReminder(ScriptLib,110,{})"));
        assertEquals(1, scene.packets.size());
        assertEquals(
                0,
                ShowTemplateReminderNotify.parseFrom(scene.packets.get(0).getData())
                        .getParamListCount());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "nil",
                "1",
                "'0'",
                "{[2]=1}",
                "{0,missing=1}",
                "{'1'}",
                "{1.5}",
                "{0/0}",
                "{math.huge}",
                "{2147483648}",
                "{-2147483649}"
            })
    void reminderRejectsMalformedParameterListsWithoutSendingPackets(String parameters)
            throws Exception {
        scene.packets.clear();
        assertEquals(-1, lua("ScriptLib.ShowTemplateReminder(ScriptLib,110," + parameters + ")"));
        assertTrue(scene.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "'110'", "2147483648", "nil"})
    void reminderRejectsInvalidIdsWithoutSendingPackets(String id) throws Exception {
        scene.packets.clear();
        assertEquals(-1, lua("ScriptLib.ShowTemplateReminder(ScriptLib," + id + ",{0,0})"));
        assertTrue(scene.packets.isEmpty());
    }

    @Test
    void reminderCannotSendFromAnUnloadedGroupOrDestroyedScene() throws Exception {
        scene.packets.clear();
        scripts.loaded.remove(100);
        assertEquals(-1, lua("ScriptLib.ShowTemplateReminder(ScriptLib,110,{0,0})"));
        scripts.loaded.put(100, group);
        scripts.onDestroy();
        assertEquals(-1, lua("ScriptLib.ShowTemplateReminder(ScriptLib,110,{0,0})"));
        assertTrue(scene.packets.isEmpty());
    }

    private MpPlayGroupData entry() {
        return new Gson()
                .fromJson(
                        "{\"playId\":2,\"groupList\":[100],\"prepareTime\":0}",
                        MpPlayGroupData.class);
    }

    private boolean prepare() {
        return scene.getScenePlayBattleManager()
                .prepare(
                        entry(),
                        new Gson()
                                .fromJson(
                                        "{\"id\":2,\"minPlayers\":1,\"maxPlayers\":4}",
                                        MpPlayMatchData.class),
                        2,
                        ScenePlayBattleManager.DIRECT,
                        List.of(environment.player));
    }

    @Test
    void oldRoundTimerCannotRunInTheNextRoundWithTheSameGroupObject() throws Exception {
        assertTrue(prepare());
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 2));
        scene.getScenePlayBattleManager().onPlayerLeaving(environment.player);
        assertTrue(prepare());
        tick(3);
        assertTrue(events.isEmpty());
    }

    @Test
    void callbackReschedulingCannotMakeAStoppedRoundReplaceableBeforeReturn() throws Exception {
        assertTrue(prepare());
        action =
                event -> {
                    scene.getScenePlayBattleManager().onPlayerLeaving(environment.player);
                    assertEquals(0, library.CreateGroupTimerEvent(100, "alarm", 2));
                    assertFalse(prepare());
                };
        assertEquals(0, scripts.createGroupTimerEvent(100, "alarm", 1));
        tick(1);
        assertEquals(1, events.size());
        assertTrue(prepare());
        tick(3);
        assertEquals(1, events.size());
    }

    private static final class TimerScene extends AbilityTestFixture.TestScene {
        final TimerScripts scripts;

        TimerScene(AbilityTestFixture.TestWorld world) {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_DUNGEON\"}", SceneData.class));
            scripts = new TimerScripts(this);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class TimerScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>();
        final List<Future<?>> callbacks = new CopyOnWriteArrayList<>();
        int loads;

        TimerScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public SceneGroup getGroupById(int id) {
            loads++;
            return loaded.get(id);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event, BooleanSupplier current) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event, BooleanSupplier current, boolean locked) {
            var callback = super.callEvent(event, current, locked);
            callbacks.add(callback);
            return callback;
        }

        void drain() throws Exception {
            var selected = List.copyOf(callbacks);
            callbacks.removeAll(selected);
            for (var callback : selected)
                if (!callback.isCancelled()) callback.get(5, TimeUnit.SECONDS);
        }
    }
}
