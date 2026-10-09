package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionServerLuaCall;
import emu.grasscutter.game.activity.crucible.CrucibleSceneController;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.script.SimpleBindings;
import org.junit.jupiter.api.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

class ServerLuaCallContextTest {
    @BeforeAll
    static void initializeLua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @AfterEach
    void clearThreadContext() {
        var library = ScriptLoader.getScriptLib();
        library.removeCurrentEntity();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
    }

    private static SceneGroup install(AbilityTestFixture fixture, int id, SimpleBindings bindings)
            throws Exception {
        var group = SceneGroup.of(id);
        group.setBindings(bindings);
        var manager = fixture.scene.getScriptManager();
        manager.registerLocalGroups(List.of(group));
        var field = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var instances = (Map<Integer, SceneGroupInstance>) field.get(manager);
        instances.put(id, new SceneGroupInstance(group, fixture.player));
        return group;
    }

    private static Ability ability(AbilityTestFixture fixture) {
        fixture.player.setUid(10001);
        return new Ability(
                JsonUtils.decode("{\"abilityName\":\"Server_Lua_Test\"}", AbilityData.class),
                fixture.entity,
                fixture.player);
    }

    private static AbilityModifierAction action(String type, int parameters) {
        return JsonUtils.decode(
                "{\"$type\":\"ServerLuaCall\",\"luaCallType\":\""
                        + type
                        + "\",\"funcName\":\"callable\",\"paramNum\":"
                        + parameters
                        + ",\"param1\":11.5,\"param2\":-22.25,\"param3\":33.75,\"CallParamList\":[456]}",
                AbilityModifierAction.class);
    }

    private static boolean invoke(Ability ability, AbilityModifierAction action, GameEntity entity) {
        return new ActionServerLuaCall().execute(ability, action, ByteString.EMPTY, entity);
    }

    @Test
    void allArgumentCountsIncludeContextAndPreserveFractions() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var bindings = new SimpleBindings();
            install(f, 123, bindings);
            var values = new ArrayList<Double>();
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            var context = args.arg1();
                            assertSame(ScriptLoader.getScriptLib(), context.checkuserdata());
                            assertEquals(10001, context.get("uid").toint());
                            assertEquals(42, context.get("source_entity_id").toint());
                            assertEquals(42, context.get("target_entity_id").toint());
                            assertEquals(123, context.get("group_id").toint());
                            values.clear();
                            for (int i = 2; i <= args.narg(); i++) values.add(args.arg(i).todouble());
                            return LuaValue.ZERO;
                        }
                    });
            for (int count = 0; count <= 3; count++) {
                assertTrue(invoke(a, action("FromGroup", count), f.entity));
                assertEquals(List.of(11.5, -22.25, 33.75).subList(0, count), values);
            }
        }
    }

    @Test
    void actualLuaCanReadContextAndCallTheJavaLibrary() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var bindings = new SimpleBindings();
            install(f, 123, bindings);
            // ScriptLoader supplies these library globals when it evaluates runtime scripts.
            bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
            ScriptLoader.getEngine()
                    .eval(
                            "function callable(context,param1) "
                                    + "recorded_uid=context.uid; recorded_group=ScriptLib.GetContextGroupId(context); "
                                    + "recorded_target=context.target_entity_id; recorded_param=param1; return 0 end",
                            bindings);
            assertTrue(invoke(a, action("FromGroup", 1), f.entity));
            assertEquals(10001, ((Number) bindings.get("recorded_uid")).intValue());
            assertEquals(123, ((Number) bindings.get("recorded_group")).intValue());
            assertEquals(42, ((Number) bindings.get("recorded_target")).intValue());
            assertEquals(11.5, ((Number) bindings.get("recorded_param")).doubleValue());
        }
    }

    @Test
    void successfulCallsRestoreAllOuterContextSlots() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var library = ScriptLoader.getScriptLib();
            var outer = SceneGroup.of(999);
            var outerEntity = new AbilityTestFixture.TestEntity(f.scene);
            library.setCurrentGroup(outer);
            library.setCurrentEntity(outerEntity);
            library.setSceneScriptManager(f.scene.getScriptManager());
            var bindings = new SimpleBindings();
            var group = install(f, 123, bindings);
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertSame(group, library.getCurrentGroup().orElseThrow());
                            assertSame(f.entity, library.getCurrentEntity().orElseThrow());
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, action("FromGroup", 0), f.entity));
            assertSame(outer, library.getCurrentGroup().orElseThrow());
            assertSame(outerEntity, library.getCurrentEntity().orElseThrow());
            assertSame(f.scene.getScriptManager(), library.getSceneScriptManagerOrNull());
        }
    }

    @Test
    void specificGroupUsesTheNamedGroupAndRestoresEmptyContext() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var bindings = new SimpleBindings();
            var selected = install(f, 456, bindings);
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertSame(selected, ScriptLoader.getScriptLib().getCurrentGroup().orElseThrow());
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, action("SpecificGroup", 0), f.entity));
            assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
            assertTrue(ScriptLoader.getScriptLib().getCurrentGroup().isEmpty());
            assertNull(ScriptLoader.getScriptLib().getSceneScriptManagerOrNull());
        }
    }

    @Test
    void ownerGroupCallsUseTheAbilityOwnerInsteadOfTheTargetGroup() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var target = new AbilityTestFixture.TestEntity(f.scene);
            target.setId(43);
            target.setGroupId(456);
            var bindings = new SimpleBindings();
            var group = install(f, 123, bindings);
            var calls = new AtomicInteger();
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            assertSame(group, ScriptLoader.getScriptLib().getCurrentGroup().orElseThrow());
                            assertEquals(43, args.arg1().get("target_entity_id").toint());
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, action("OwnerFromGroup", 0), target));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void nestedCallsRestoreTheOuterSelectedGroup() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var library = ScriptLoader.getScriptLib();
            var outerBindings = new SimpleBindings();
            var outer = install(f, 123, outerBindings);
            var innerBindings = new SimpleBindings();
            var inner = install(f, 456, innerBindings);
            innerBindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertSame(inner, library.getCurrentGroup().orElseThrow());
                            return LuaValue.ZERO;
                        }
                    });
            outerBindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertTrue(ServerLuaCallContextTest.invoke(a, action("SpecificGroup", 0), f.entity));
                            assertSame(outer, library.getCurrentGroup().orElseThrow());
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, action("FromGroup", 0), f.entity));
            assertTrue(library.getCurrentGroup().isEmpty());
        }
    }

    @Test
    void missingGroupsAndMalformedActionsReturnFalseWithoutLeakingContext() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            assertFalse(invoke(a, action("FromGroup", 0), f.entity));
            var act = action("SpecificGroup", 0);
            act.callParamList = null;
            assertFalse(invoke(a, act, f.entity));
            act.callParamList = new int[0];
            assertFalse(invoke(a, act, f.entity));
            act.callParamList = new int[] {-1};
            assertFalse(invoke(a, act, f.entity));
            act.luaCallType = null;
            assertFalse(invoke(a, act, f.entity));
            assertFalse(invoke(a, null, f.entity));
            assertFalse(invoke(a, act, null));
            assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
            assertNull(ScriptLoader.getScriptLib().getSceneScriptManagerOrNull());
        }
    }

    @Test
    void invalidParameterCountsNeverRunTheFunction() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var calls = new AtomicInteger();
            var bindings = new SimpleBindings();
            install(f, 123, bindings);
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            return LuaValue.ZERO;
                        }
                    });
            assertFalse(invoke(a, action("FromGroup", -1), f.entity));
            assertFalse(invoke(a, action("FromGroup", 4), f.entity));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void gadgetFunctionsRunAndMissingBindingsDoNotClaimSuccess() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            var calls = new AtomicInteger();
            var bindings = new SimpleBindings();
            f.entity.setEntityController(new EntityController(null, bindings));
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, action("Gadget", 1), f.entity));
            assertEquals(1, calls.get());
            bindings.remove("callable");
            assertFalse(invoke(a, action("Gadget", 1), f.entity));
            assertEquals(1, calls.get());
            assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
        }
    }

    @Test
    void controllerCallsRespectTheExistingActivityLifecycle() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            var calls = new AtomicInteger();
            var bindings = new SimpleBindings();
            f.entity.setGroupId(CrucibleSceneController.MAIN_GROUP);
            f.entity.setEntityController(new EntityController(null, bindings));
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            return LuaValue.ZERO;
                        }
                    });
            assertFalse(invoke(a, action("Gadget", 0), f.entity));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void failedPredicatesPreventLuaSideEffects() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            var calls = new AtomicInteger();
            var bindings = new SimpleBindings();
            f.entity.setEntityController(new EntityController(null, bindings));
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            return LuaValue.ZERO;
                        }
                    });
            var act = action("Gadget", 0);
            act.predicates = List.of(Map.of("$type", "ByHasModifier", "modifierName", "not_installed"));
            assertTrue(invoke(a, act, f.entity));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void repeatedMissingFunctionsEmitBoundedWarningsWithoutThrowables() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            install(f, 123, new SimpleBindings());
            var logger = Grasscutter.getLogger();
            var appender = new ListAppender<ILoggingEvent>();
            appender.setContext(logger.getLoggerContext());
            appender.start();
            var level = logger.getLevel();
            logger.setLevel(Level.WARN);
            logger.addAppender(appender);
            try {
                var act = action("FromGroup", 0);
                for (int i = 0; i < 2_000; i++) {
                    act.funcName = "missing_" + i;
                    assertFalse(invoke(a, act, f.entity));
                }
                assertTrue(appender.list.size() <= 1);
                appender.list.forEach(e -> assertNull(e.getThrowableProxy()));
                assertTrue(ScriptLoader.getScriptLib().getCurrentGroup().isEmpty());
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(level);
                appender.stop();
            }
        }
    }

    @Test
    void realLuaErrorsKeepTheirThrowableAndRestoreContext() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var bindings = new SimpleBindings();
            install(f, 123, bindings);
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            throw new LuaError("actual_script_failure");
                        }
                    });
            var logger = Grasscutter.getLogger();
            var appender = new ListAppender<ILoggingEvent>();
            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.addAppender(appender);
            try {
                for (int i = 0; i < 3; i++) assertFalse(invoke(a, action("FromGroup", 0), f.entity));
                var errors = appender.list.stream().filter(e -> e.getThrowableProxy() != null).toList();
                assertEquals(3, errors.size());
                assertEquals(LuaError.class.getName(), errors.get(0).getThrowableProxy().getClassName());
                assertTrue(ScriptLoader.getScriptLib().getCurrentGroup().isEmpty());
                assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
                assertNull(ScriptLoader.getScriptLib().getSceneScriptManagerOrNull());
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    @Test
    void functionsAreResolvedAgainAfterBindingsChange() throws Exception {
        try (var f = new AbilityTestFixture()) {
            var a = ability(f);
            f.entity.setGroupId(123);
            var bindings = new SimpleBindings();
            install(f, 123, bindings);
            var act = action("FromGroup", 0);
            var calls = new AtomicInteger();
            assertFalse(invoke(a, act, f.entity));
            bindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            calls.incrementAndGet();
                            return LuaValue.ZERO;
                        }
                    });
            assertTrue(invoke(a, act, f.entity));
            bindings.remove("callable");
            assertFalse(invoke(a, act, f.entity));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void concurrentPlayersKeepUidEntityAndGroupContextsSeparate() throws Exception {
        try (var left = new AbilityTestFixture();
                var right = new AbilityTestFixture()) {
            var a = ability(left);
            var b = ability(right);
            right.player.setUid(10002);
            left.entity.setGroupId(123);
            right.entity.setGroupId(456);
            var leftBindings = new SimpleBindings();
            var rightBindings = new SimpleBindings();
            install(left, 123, leftBindings);
            install(right, 456, rightBindings);
            leftBindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertEquals(10001, args.arg1().get("uid").toint());
                            assertEquals(123, ScriptLoader.getScriptLib().GetContextGroupId());
                            return LuaValue.ZERO;
                        }
                    });
            rightBindings.put(
                    "callable",
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            assertEquals(10002, args.arg1().get("uid").toint());
                            assertEquals(456, ScriptLoader.getScriptLib().GetContextGroupId());
                            return LuaValue.ZERO;
                        }
                    });
            var executor = Executors.newFixedThreadPool(2);
            try {
                var jobs =
                        executor.invokeAll(
                                List.of(
                                        (Callable<Void>)
                                                () -> {
                                                    for (int i = 0; i < 100; i++)
                                                        assertTrue(invoke(a, action("FromGroup", 0), left.entity));
                                                    assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
                                                    return null;
                                                },
                                        () -> {
                                            for (int i = 0; i < 100; i++)
                                                assertTrue(invoke(b, action("FromGroup", 0), right.entity));
                                            assertTrue(ScriptLoader.getScriptLib().getCurrentEntity().isEmpty());
                                            return null;
                                        }));
                for (var job : jobs) job.get();
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}
