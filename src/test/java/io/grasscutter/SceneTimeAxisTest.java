package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javax.script.SimpleBindings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

class SceneTimeAxisTest {
    private AbilityTestFixture fixture;
    private SceneScriptManager manager;
    private final List<SceneTimeAxis> created = new ArrayList<>();
    private final BlockingQueue<Observed> events = new LinkedBlockingQueue<>();

    private record Observed(int group, int index, String source, String luaSource, long nanos) {}

    @BeforeAll
    static void initialize() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        manager = fixture.scene.getScriptManager();
    }

    @SuppressWarnings("unchecked")
    private Map<Object, SceneTimeAxis> axes() throws Exception {
        Field field = SceneScriptManager.class.getDeclaredField("timeAxis");
        field.setAccessible(true);
        return (Map<Object, SceneTimeAxis>) field.get(manager);
    }

    @AfterEach
    void cleanup() throws Exception {
        // Also clean replaced legacy timers when a regression assertion fails.
        created.addAll(axes().values());
        created.forEach(SceneTimeAxis::stop);
        manager.onDestroy();
        var lib = ScriptLoader.getScriptLib();
        lib.removeCurrentGroup();
        lib.removeSceneScriptManager();
        fixture.close();
    }

    private SceneGroup group(int id) {
        var group = SceneGroup.of(id);
        var bindings = new SimpleBindings();
        bindings.put(
                "capture",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        var event = args.arg(2);
                        events.add(
                                new Observed(
                                        id,
                                        event.get("param1").toint(),
                                        event.get("source").tojstring(),
                                        event.get("source_name").tojstring(),
                                        System.nanoTime()));
                        return LuaValue.ZERO;
                    }
                });
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        group.setBindings(bindings);
        manager.registerLocalGroups(List.of(group));
        var trigger = new SceneTrigger();
        trigger.currentGroup = group;
        trigger.setName("timeline_" + id);
        trigger.setEvent(EventType.EVENT_TIME_AXIS_PASS);
        trigger.setSource("");
        trigger.setCondition("");
        trigger.setAction("capture");
        trigger.setTrigger_count(0);
        manager.registerTrigger(trigger);
        return group;
    }

    private int lua(SceneGroup group, String expression) throws Exception {
        var lib = ScriptLoader.getScriptLib();
        lib.setSceneScriptManager(manager);
        lib.setCurrentGroup(group);
        var value = ScriptLoader.getEngine().eval("return " + expression, group.getBindings());
        created.addAll(axes().values());
        return ((Number) value).intValue();
    }

    private SceneTimeAxis axis(int group, String id, int seconds, boolean loop) {
        var axis = new SceneTimeAxis(manager, group, id, seconds, loop);
        created.add(axis);
        manager.initTimeAxis(axis);
        return axis;
    }

    @Test
    void legacyConstructorUsesSecondsAndProducesLuaCompatibleEvent() throws Exception {
        group(501);
        var axis = axis(501, "seconds", 1, false);
        long start = System.nanoTime();
        axis.start();
        assertNull(events.poll(200, TimeUnit.MILLISECONDS));
        var event = events.poll(2, TimeUnit.SECONDS);
        assertNotNull(event);
        assertTrue(event.nanos() - start >= TimeUnit.MILLISECONDS.toNanos(900));
        assertEquals(new Observed(501, 1, "seconds", "seconds", event.nanos()), event);
    }

    @Test
    void allFractionalPointsKeepTheirOriginalIndexes() throws Exception {
        var group = group(501);
        long start = System.nanoTime();
        assertEquals(0, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'multi',{0.04,0.08,0.12},false)"));
        for (int index = 1; index <= 3; index++) {
            var event = events.poll(2, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(index, event.index());
            assertEquals("multi", event.luaSource());
            assertTrue(event.nanos() - start >= TimeUnit.MILLISECONDS.toNanos(index * 40 - 10));
        }
        assertNull(events.poll(120, TimeUnit.MILLISECONDS));
    }

    @Test
    void aLeadingIntegerDoesNotTruncateLaterFractionalPoints() throws Exception {
        var group = group(501);
        long start = System.nanoTime();
        assertEquals(0, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'mixed',{0,0.04,0.08},false)"));
        for (int index = 1; index <= 3; index++) {
            var event = events.poll(2, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(index, event.index());
            assertTrue(event.nanos() - start >= TimeUnit.MILLISECONDS.toNanos((index - 1) * 40 - 10));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 65536})
    void laterFractionsArePreservedForEveryLeadingInteger(int first) throws Exception {
        var group = group(501);
        assertEquals(
                0,
                lua(
                        group,
                        "ScriptLib.InitTimeAxis(ScriptLib,'coercion',{"
                                + first
                                + ","
                                + (first + 2.125)
                                + ","
                                + (first + 2.25)
                                + "},false)"));
        var field = SceneTimeAxis.class.getDeclaredField("offsets");
        field.setAccessible(true);
        long initial = first * 1_000_000_000L;
        assertArrayEquals(
                new long[] {initial, initial + 2_125_000_000L, initial + 2_250_000_000L},
                (long[]) field.get(axes().values().iterator().next()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{0,'0.1'}",
                "{0,false}",
                "{0,nil,0.1}",
                "{[0]=0,[1]=0.1}",
                "{[1]=0,[2]=0.1,note='x'}",
                "nil"
            })
    void malformedLuaArraysAreRejectedBeforeReplacingAValidAxis(String points) throws Exception {
        var group = group(501);
        var original = axis(501, "valid", 5, false);
        assertEquals(-1, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'valid'," + points + ",false)"));
        assertEquals(List.of(original), new ArrayList<>(axes().values()));
    }

    @Test
    void javaArrayCallersKeepBothExistingSignatures() throws Exception {
        var group = group(501);
        lua(group, "0");
        var lib = ScriptLoader.getScriptLib();
        assertEquals(0, lib.InitTimeAxis("integers", new int[] {1, 2}, false));
        assertEquals(0, lib.InitTimeAxis("decimals", new double[] {1.125, 2.25}, false));
        assertEquals(2, axes().size());
        created.addAll(axes().values());
    }

    @Test
    void loopingSequenceRestartsAfterItsLastPoint() throws Exception {
        var group = group(501);
        assertEquals(0, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'loop',{0.03,0.06},true)"));
        for (int index : new int[] {1, 2, 1, 2}) {
            var event = events.poll(2, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(index, event.index());
        }
        assertEquals(0, lua(group, "ScriptLib.EndTimeAxis(ScriptLib,'loop')"));
        assertTrue(axes().isEmpty());
    }

    @Test
    void sameIdentifierInDifferentGroupsDoesNotReplaceAnotherGroup() throws Exception {
        var first = group(501);
        var second = group(502);
        lua(first, "ScriptLib.InitTimeAxis(ScriptLib,'same',{0.3},false)");
        lua(second, "ScriptLib.InitTimeAxis(ScriptLib,'same',{0.05},false)");
        assertEquals(2, axes().size());
        assertEquals(502, events.poll(2, TimeUnit.SECONDS).group());
        assertEquals(501, events.poll(2, TimeUnit.SECONDS).group());
    }

    @Test
    void endingOneGroupLeavesTheOtherGroupWithTheSameIdentifier() throws Exception {
        var first = group(501);
        var second = group(502);
        lua(first, "ScriptLib.InitTimeAxis(ScriptLib,'same',{0.2},false)");
        lua(second, "ScriptLib.InitTimeAxis(ScriptLib,'same',{0.05},false)");
        lua(first, "ScriptLib.EndTimeAxis(ScriptLib,'same')");
        assertEquals(1, axes().size());
        var event = events.poll(2, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(502, event.group());
        assertNull(events.poll(250, TimeUnit.MILLISECONDS));
    }

    @Test
    void replacementCancelsTheOldAxisBeforeItCanStart() throws Exception {
        group(501);
        var old = axis(501, "replace", 0, false);
        axis(501, "replace", 5, false);
        assertDoesNotThrow(old::start);
        assertNull(events.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void endRemovesTheAxisAndARepeatedStartIsHarmless() throws Exception {
        group(501);
        var old = axis(501, "end", 0, false);
        manager.stopTimeAxis("end");
        assertTrue(axes().isEmpty());
        assertDoesNotThrow(old::start);
        assertNull(events.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void oneShotCompletionReleasesTheRegisteredAxis() throws Exception {
        var group = group(501);
        lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'once',{0},false)");
        assertNotNull(events.poll(2, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!axes().isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(axes().isEmpty());
    }

    @Test
    void groupUnloadCancelsOnlyThatGroupsAxes() throws Exception {
        var first = group(501);
        group(502);
        var old = axis(501, "first", 0, false);
        axis(502, "second", 5, false);
        manager.unregisterGroup(first);
        assertEquals(1, axes().size());
        assertDoesNotThrow(old::start);
        assertNull(events.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void sceneDestructionCancelsAxesAndRejectsLateInitialization() throws Exception {
        var group = group(501);
        var old = axis(501, "destroy", 0, false);
        manager.onDestroy();
        assertTrue(axes().isEmpty());
        assertDoesNotThrow(old::start);
        assertEquals(-1, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'late',{0},false)"));
        assertNull(events.poll(100, TimeUnit.MILLISECONDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{-1}", "{2,1}", "{0/0}", "{1/0}"})
    void invalidPointsReturnFailureAndKeepTheExistingAxis(String points) throws Exception {
        var group = group(501);
        var original = axis(501, "valid", 5, false);
        assertEquals(-1, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'valid'," + points + ",false)"));
        assertEquals(List.of(original), new ArrayList<>(axes().values()));
    }

    @Test
    void zeroPeriodLoopIsRejectedWithoutLeavingARegisteredAxis() throws Exception {
        var group = group(501);
        assertEquals(-1, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'zero',{0},true)"));
        assertTrue(axes().isEmpty());
    }

    @Test
    void zeroAndDuplicatePointsKeepAllEventsInOrder() throws Exception {
        var group = group(501);
        assertEquals(0, lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'equal',{0,0,0.04,0.04},false)"));
        for (int index = 1; index <= 4; index++) {
            var event = events.poll(2, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(index, event.index());
        }
    }

    @Test
    void constructingAxesDoesNotCreateDedicatedTimerThreads() {
        long before =
                Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith("Timer-"))
                        .count();
        for (int i = 0; i < 20; i++) {
            var axis = new SceneTimeAxis(manager, 501, "idle" + i, 1, false);
            created.add(axis);
        }
        long after =
                Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith("Timer-"))
                        .count();
        assertEquals(before, after);
    }

    @Test
    void cancellationDropsAnEventAlreadyQueuedForLuaExecution() throws Exception {
        group(501);
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var executor = (ThreadPoolExecutor) SceneScriptManager.eventExecutor;
        for (int i = 0; i < 4; i++)
            executor.submit(
                    () -> {
                        entered.countDown();
                        try {
                            release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var axis = axis(501, "queued", 0, false);
            axis.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (executor.getQueue().isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
            assertFalse(executor.getQueue().isEmpty());
            manager.stopTimeAxis("queued");
            release.countDown();
            assertNull(events.poll(300, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void actualResourceCallbackReceivesBothAbsolutePointsAndTheLuaSourceName() throws Exception {
        var group = group(220142003);
        var calls = new LinkedBlockingQueue<List<Integer>>();
        var library = new LuaTable();
        library.set(
                "ChangeToTargetLevelTag",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        calls.add(List.of(args.arg(2).toint()));
                        return LuaValue.ZERO;
                    }
                });
        library.set(
                "SetGadgetStateByConfigId",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        calls.add(List.of(args.arg(2).toint(), args.arg(3).toint()));
                        return LuaValue.ZERO;
                    }
                });
        library.set(
                "GetGroupVariableValue",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        return LuaValue.ZERO;
                    }
                });
        library.set(
                "SetWorktopOptionsByGroupId",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        var options = args.arg(4).checktable();
                        var recorded = new ArrayList<Integer>();
                        recorded.add(args.arg(2).toint());
                        recorded.add(args.arg(3).toint());
                        for (int i = 1; i <= options.length(); i++) recorded.add(options.get(i).toint());
                        calls.add(recorded);
                        return LuaValue.ZERO;
                    }
                });
        var states = new LuaTable();
        states.set("GearStart", ScriptGadgetState.GearStart);
        group.getBindings().put("ScriptLib", library);
        group.getBindings().put("GadgetState", states);
        String source =
                Files.readString(Path.of("resources/Scripts/Scene/20142/scene20142_group220142003.lua"));
        int start = source.indexOf("function action_EVENT_TIME_AXIS_PASS_3004(");
        assertTrue(start >= 0);
        int end = source.indexOf("\nfunction ", start + 1);
        assertTrue(end > start);
        ScriptLoader.getEngine()
                .eval(
                        source.substring(start, end)
                                + "\nfunction resourceAction(context,evt) capture(context,evt); "
                                + "return action_EVENT_TIME_AXIS_PASS_3004(context,evt) end",
                        group.getBindings());
        manager.getTriggersByEvent(EventType.EVENT_TIME_AXIS_PASS).stream()
                .filter(trigger -> trigger.currentGroup == group)
                .forEach(trigger -> trigger.setAction("resourceAction"));
        assertTrue(
                source.replaceAll("\\s+", "").contains("InitTimeAxis(context,\"temp\",{2,7},false)"));
        var axis = new SceneTimeAxis(manager, group.id, "temp", new double[] {2, 7}, false);
        created.add(axis);
        manager.initTimeAxis(axis);
        long started = System.nanoTime();
        axis.start();
        var first = events.poll(4, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals(1, first.index());
        assertEquals("temp", first.luaSource());
        assertTrue(first.nanos() - started >= TimeUnit.MILLISECONDS.toNanos(1900));
        assertEquals(List.of(26), calls.poll(1, TimeUnit.SECONDS));
        assertEquals(List.of(3002, ScriptGadgetState.GearStart), calls.poll(1, TimeUnit.SECONDS));
        var second = events.poll(6, TimeUnit.SECONDS);
        assertNotNull(second);
        assertEquals(2, second.index());
        assertTrue(second.nanos() - started >= TimeUnit.MILLISECONDS.toNanos(6900));
        assertEquals(List.of(0, 3001, 608, 617, 618, 619), calls.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void slowLuaCallbackPreservesPointOrderWithoutQueuingLaterEvents() throws Exception {
        var group = group(501);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var capture = (LuaValue) group.getBindings().get("capture");
        group
                .getBindings()
                .put(
                        "capture",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                if (args.arg(2).get("param1").toint() == 1) {
                                    entered.countDown();
                                    try {
                                        release.await(3, TimeUnit.SECONDS);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                return capture.invoke(args);
                            }
                        });
        try {
            lua(group, "ScriptLib.InitTimeAxis(ScriptLib,'slow',{0,0.01,0.02},false)");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertNull(events.poll(100, TimeUnit.MILLISECONDS));
            release.countDown();
            for (int index = 1; index <= 3; index++) {
                var event = events.poll(2, TimeUnit.SECONDS);
                assertNotNull(event);
                assertEquals(index, event.index());
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancelingLongDelaysReleasesScheduledTasksAndThreadsAreSharedDaemons() throws Exception {
        var field = SceneTimeAxis.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        var scheduler = (ScheduledThreadPoolExecutor) field.get(null);
        int before = scheduler.getQueue().size();
        for (int i = 0; i < 20; i++) axis(501, "long" + i, 3600, false).start();
        assertEquals(before + 20, scheduler.getQueue().size());
        assertTrue(scheduler.getRemoveOnCancelPolicy());
        var workers =
                Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> thread.getName().startsWith("Scene-time-axis-"))
                        .toList();
        assertFalse(workers.isEmpty());
        assertTrue(workers.size() <= 2);
        assertTrue(workers.stream().allMatch(Thread::isDaemon));
        manager.onDestroy();
        assertEquals(before, scheduler.getQueue().size());
        assertTrue(axes().isEmpty());
    }
}
