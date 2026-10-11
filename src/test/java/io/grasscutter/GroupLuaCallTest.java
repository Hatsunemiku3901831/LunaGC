package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.script.*;

class GroupLuaCallTest {
    private AbilityTestFixture environment;
    private GroupScene scene;
    private ScriptLib library;
    private SceneGroup origin, target;
    private LuaValue context;
    private SimpleBindings bindings;
    private ScriptArgs event;

    @BeforeAll
    static void luaEngine() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        scene =
                new GroupScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), environment.player));
        library = ScriptLoader.getScriptLib();
        origin = group(100);
        target = group(200);
        scene.scripts.loaded.put(origin.id, origin);
        scene.scripts.loaded.put(target.id, target);
        library.setSceneScriptManager(scene.scripts);
        library.setCurrentGroup(origin);
        library.setCurrentEntity(environment.entity);
        event =
                new ScriptArgs(origin.id, 4, 77, 88)
                        .setUid(10001)
                        .setSourceEntityId(42)
                        .setTargetEntityId(43);
        library.setCurrentCallParams(event);
        context = library.createEventContext(event, origin);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        bindings.put("caller", context);
    }

    @AfterEach
    void cleanup() {
        library.removeCurrentGroup();
        library.removeCurrentEntity();
        library.removeCurrentCallParams();
        library.removeSceneScriptManager();
        scene.scripts.onDestroy();
        environment.close();
    }

    private SceneGroup group(int id) throws Exception {
        var value = SceneGroup.of(id);
        field(SceneGroup.class, "bindings").set(value, new SimpleBindings());
        return value;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private int lua(String text) throws ScriptException {
        return ((Number) ScriptLoader.getEngine().eval(text, bindings)).intValue();
    }

    private void function(SceneGroup group, String name, String text) throws ScriptException {
        group.getBindings().put(name, ScriptLoader.getEngine().eval(text, bindings));
    }

    private void restored() throws Exception {
        assertSame(origin, library.getCurrentGroup().orElseThrow());
        assertSame(environment.entity, library.getCurrentEntity().orElseThrow());
        assertSame(scene.scripts, library.getSceneScriptManagerOrNull());
        var slot =
                (io.netty.util.concurrent.FastThreadLocal<?>)
                        field(ScriptLib.class, "callParams").get(library);
        assertSame(event, slot.getIfExists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{0}", "{4294967295,0}", "{1,2,3}"})
    void zeroToThreeUint32ArgumentsKeepCallerAndDestinationSeparate(String values)
            throws Exception {
        var seen = new AtomicInteger();
        target.getBindings()
                .put(
                        "callable",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                seen.incrementAndGet();
                                assertEquals(200, args.arg1().get("group_id").toint());
                                assertEquals(10001, args.arg1().get("uid").toint());
                                assertEquals(0, args.arg1().get("source_entity_id").toint());
                                assertEquals(0, args.arg1().get("target_entity_id").toint());
                                assertSame(context, args.arg(2));
                                assertEquals(100, args.arg(2).get("group_id").toint());
                                assertEquals(42, args.arg(2).get("source_entity_id").toint());
                                assertSame(target, library.getCurrentGroup().orElseThrow());
                                assertTrue(library.getCurrentEntity().isEmpty());
                                assertSame(scene.scripts, library.getSceneScriptManagerOrNull());
                                return LuaValue.valueOf(-23);
                            }
                        });
        // Capture the exact arity and values with Lua as well as the real Java receiver.
        bindings.put("check", target.getBindings().get("callable"));
        function(
                target,
                "callable",
                "return function(context,prev,...) local a={...} local expected="
                        + values
                        + " assert(#a==#expected) for i,v in ipairs(expected) do assert(a[i]==v)"
                        + " end return check(context,prev,...) end");
        assertEquals(
                -23, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable'," + values + ")"));
        assertEquals(1, seen.get());
        restored();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "nil",
                "7",
                "'1'",
                "{1,2,3,4}",
                "{[2]=1}",
                "{1,k=2}",
                "{-1}",
                "{4294967296}",
                "{1.5}",
                "{'2'}",
                "{true}",
                "{0/0}",
                "{1/0}",
                "{{}}"
            })
    void rejectsMalformedParameterListsBeforeCallingOrLoading(String values) throws Exception {
        function(target, "callable", "return function() error('must not run') end");
        assertEquals(
                -1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable'," + values + ")"));
        assertEquals(0, scene.scripts.loads);
        restored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.2", "2147483648", "'200'", "nil"})
    void rejectsInvalidGroupIdentifiers(String id) throws Exception {
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller," + id + ",'callable',{})"));
        assertEquals(0, scene.scripts.loads);
    }

    @Test
    void activeOnlyDoesNotLoadAndTheOrdinaryCallLoadsItsKnownDefinition() throws Exception {
        scene.scripts.loaded.remove(target.id);
        scene.scripts.available.put(target.id, target);
        function(target, "callable", "return function(context,prev) return context.group_id end");
        assertEquals(-1, lua("return ScriptLib.ExecuteActiveGroupLua(caller,200,'callable',{})"));
        assertEquals(0, scene.scripts.loads);
        assertEquals(200, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        assertEquals(1, scene.scripts.loads);
        assertEquals(200, lua("return ScriptLib.ExecuteActiveGroupLua(caller,200,'callable',{})"));
        assertEquals(1, scene.scripts.loads);
        restored();
    }

    @Test
    void nestedCallsRestoreTheIntermediateAndThenTheOuterCaller() throws Exception {
        var third = group(300);
        scene.scripts.loaded.put(third.id, third);
        function(
                third,
                "nested",
                "return function(context,prev,p) assert(prev.group_id==200)"
                        + " assert(context.group_id==300 and context.uid==10001) return p+4 end");
        function(
                target,
                "callable",
                "return function(context,prev,p)"
                        + " local r=ScriptLib.ExecuteActiveGroupLua(context,300,'nested',{p})"
                        + " assert(ScriptLib.GetContextGroupId(context)==200)"
                        + " return r end");
        assertEquals(7, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{3})"));
        restored();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "error('expected group failure')",
                "return nil",
                "return true",
                "return '0'",
                "return 0.5",
                "return 1/0",
                "return 2147483648"
            })
    void runtimeErrorsAndInvalidReturnValuesRestoreTheOuterContext(String action) throws Exception {
        function(target, "callable", "return function() " + action + " end");
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        restored();
    }

    @Test
    void missingGroupsBindingsAndFunctionsFailAndARepairedBindingWorks() throws Exception {
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,999,'callable',{})"));
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        target.getBindings().put("callable", LuaValue.ONE);
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        function(target, "callable", "return function() return 0 end");
        assertEquals(0, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        field(SceneGroup.class, "bindings").set(target, null);
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        restored();
    }

    @Test
    void staleOriginAndAnOriginReplacedDuringForcedLoadingAreRejected() throws Exception {
        function(target, "callable", "return function() error('stale origin') end");
        scene.scripts.loaded.put(origin.id, group(origin.id));
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        assertEquals(0, scene.scripts.loads);
        scene.scripts.loaded.put(origin.id, origin);
        scene.scripts.loaded.remove(target.id);
        scene.scripts.available.put(target.id, target);
        scene.scripts.onLoad = () -> scene.scripts.loaded.remove(origin.id);
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        restored();
    }

    @Test
    void serverInitiatedCallsUseUidZeroAndNoOriginGroup() throws Exception {
        library.removeCurrentGroup();
        function(
                target,
                "callable",
                "return function(context,prev) assert(context.uid==0)"
                        + " assert(context.source_entity_id==0) return 0 end");
        assertEquals(0, lua("return ScriptLib.ExecuteGroupLua(ScriptLib,200,'callable',{})"));
        assertTrue(library.getCurrentGroup().isEmpty());
    }

    @Test
    void aDestroyedSceneAndMissingSceneContextRejectCalls() throws Exception {
        function(target, "callable", "return function() error('destroyed scene') end");
        scene.scripts.onDestroy();
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        library.removeSceneScriptManager();
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"''", "'  '", "200", "nil"})
    void invalidFunctionNamesAreRejectedBeforeLoading(String name) throws Exception {
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200," + name + ",{})"));
        assertEquals(0, scene.scripts.loads);
        restored();
    }

    @Test
    void aGroupLoadFailureIsReportedWithoutChangingTheCaller() throws Exception {
        scene.scripts.loaded.remove(target.id);
        scene.scripts.onLoad =
                () -> {
                    throw new IllegalStateException("expected load failure");
                };
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        restored();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, Integer.MAX_VALUE, 0, -1})
    void preservesSignedInt32FunctionResults(int result) throws Exception {
        function(target, "callable", "return function() return " + result + " end");
        assertEquals(result, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        restored();
    }

    @Test
    void closedHistoricalActivitiesCannotBeInvokedAsOriginOrDestination() throws Exception {
        int groupId = emu.grasscutter.game.activity.crucible.CrucibleSceneController.MAIN_GROUP;
        var closed = group(groupId);
        function(closed, "callable", "return function() error('closed activity') end");
        scene.scripts.loaded.put(groupId, closed);
        assertEquals(
                -1,
                lua(
                        "return ScriptLib.ExecuteActiveGroupLua(caller,"
                                + groupId
                                + ",'callable',{})"));
        library.setCurrentGroup(closed);
        assertEquals(-1, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        assertEquals(0, scene.scripts.loads);
        library.setCurrentGroup(origin);
        restored();
    }

    @Test
    void synchronousGroupCallsPreventAStoppedBattleFromBeingReplacedBeforeReturn()
            throws Exception {
        var player = environment.player;
        player.setUid(10001);
        player.setPeerId(1);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
        var gson = new Gson();
        var entry =
                gson.fromJson(
                        "{\"playId\":2,\"groupList\":[200],\"serverLuaCallGroupList\":[200],\"prepareTime\":20}",
                        MpPlayGroupData.class);
        var match =
                gson.fromJson(
                        "{\"id\":2,\"minPlayers\":1,\"maxPlayers\":4}", MpPlayMatchData.class);
        var battles = scene.getScenePlayBattleManager();
        assertTrue(battles.prepare(entry, match, 2, 1, List.of(player)));
        target.getBindings()
                .put(
                        "callable",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                battles.onPlayerLeaving(player);
                                assertFalse(battles.prepare(entry, match, 2, 1, List.of(player)));
                                return LuaValue.ZERO;
                            }
                        });
        assertEquals(0, lua("return ScriptLib.ExecuteGroupLua(caller,200,'callable',{})"));
        assertTrue(battles.prepare(entry, match, 2, 1, List.of(player)));
    }

    private static final class GroupScene extends AbilityTestFixture.TestScene {
        final GroupScripts scripts;

        GroupScene(AbilityTestFixture.TestWorld world) {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
            scripts = new GroupScripts(this);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class GroupScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>(), available = new HashMap<>();
        int loads;
        Runnable onLoad = () -> {};

        GroupScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public SceneGroup getGroupById(int id) {
            var group = loaded.get(id);
            if (group != null) return group;
            loads++;
            group = available.get(id);
            if (group != null) loaded.put(id, group);
            onLoad.run();
            return group;
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
