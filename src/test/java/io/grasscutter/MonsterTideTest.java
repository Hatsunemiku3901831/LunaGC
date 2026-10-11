package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.service.ScriptMonsterTideService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.script.*;

class MonsterTideTest {
    private AbilityTestFixture environment;
    private TideScene scene;
    private TideScripts scripts;
    private SceneGroup group;
    private ScriptLib library;
    private Bindings bindings;

    @BeforeAll
    static void initialize() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        scene =
                new TideScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), environment.player));
        scripts = scene.scripts;
        group = group(100);
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scripts);
        library.setCurrentGroup(group);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
    }

    @AfterEach
    void cleanup() {
        scripts.onDestroy();
        library.removeSceneScriptManager();
        library.removeCurrentGroup();
        library.removeCurrentCallParams();
        environment.close();
    }

    private SceneGroup group(int id) {
        var value = SceneGroup.of(id);
        value.monsters = new HashMap<>();
        value.setBindings(new SimpleBindings());
        for (int config = 1; config <= 8; config++) {
            var monster = new SceneMonster();
            monster.config_id = config;
            monster.monster_id = 21010101;
            monster.pos = new Position();
            monster.rot = new Position();
            monster.level = 1;
            monster.group = value;
            value.monsters.put(config, monster);
        }
        scripts.loaded.put(id, value);
        return value;
    }

    private int lua(String expression) throws ScriptException {
        return ((Number) ScriptLoader.getEngine().eval("return " + expression, bindings))
                .intValue();
    }

    private int start(int total, int min, int max) {
        return scripts.startMonsterTideInGroup(
                "1", group, new Integer[] {1, 2, 3, 4, 5, 6, 7, 8}, total, min, max);
    }

    private ScriptMonsterTideService tide() {
        return scripts.getMonsterTide(group.id, "1");
    }

    private EntityMonster monster(int config) {
        return (EntityMonster) scene.getEntityByConfigId(config, group.id);
    }

    private void die(EntityMonster monster) {
        // Real scene removal precedes the spawn-service death notification, as in EntityMonster.
        scene.removeEntity(monster, VisionType.VisionType_VISION_DIE);
        scripts.getScriptMonsterSpawnService().onMonsterDead(monster);
    }

    private long events(int type) {
        return scripts.observed.stream().filter(event -> event.type == type).count();
    }

    @Test
    void lowerThresholdRefillsToDistinctUpperLimitAndRotatesConfigIds() {
        assertEquals(0, start(0, 2, 4));
        var tide = tide();
        assertEquals(4, tide.getAliveCount());
        die(monster(1));
        die(monster(2));
        assertEquals(4, tide.getCreatedCount());
        die(monster(3));
        assertEquals(7, tide.getCreatedCount());
        assertEquals(4, tide.getAliveCount());
        assertNull(monster(1));
        assertNotNull(monster(7));
        scripts.drain();
        var create =
                scripts.observed.stream()
                        .filter(event -> event.type == EventType.EVENT_MONSTER_TIDE_CREATE)
                        .toList();
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7), create.stream().map(event -> event.param1).toList());
        assertEquals(
                List.of(1, 1, 1, 1, 2, 2, 2), create.stream().map(event -> event.param2).toList());
        assertEquals(3, events(EventType.EVENT_MONSTER_TIDE_DIE));
    }

    @Test
    void finiteTideEndsOnceAndRetainsSurvivorDeathAccounting() {
        assertEquals(0, start(6, 2, 4));
        var tide = tide();
        die(monster(1));
        die(monster(2));
        die(monster(3));
        assertEquals(6, tide.getCreatedCount());
        assertEquals(3, tide.getAliveCount());
        scripts.drain();
        assertEquals(1, events(EventType.EVENT_MONSTER_TIDE_OVER));
        var end =
                scripts.observed.stream()
                        .filter(event -> event.type == EventType.EVENT_MONSTER_TIDE_OVER)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, end.param1);
        assertEquals(3, end.param2);
        assertFalse(tide.finish(7));
        die(monster(4));
        die(monster(5));
        die(monster(6));
        assertEquals(6, tide.getDeadCount());
        assertEquals(0, tide.getAliveCount());
        assertEquals(6, tide.getCreatedCount());
        scripts.tickMonsterTides();
        assertSame(tide, tide()); // Last queued deaths must still be able to reach Lua.
        scripts.drain();
        scripts.tickMonsterTides();
        assertNull(tide());
        assertEquals(6, events(EventType.EVENT_MONSTER_TIDE_DIE));
        assertEquals(1, events(EventType.EVENT_MONSTER_TIDE_OVER));
    }

    @Test
    void partialInitialBatchCanFinishWithLiveMonsters() {
        assertEquals(0, start(2, 2, 4));
        assertEquals(2, tide().getCreatedCount());
        assertEquals(2, tide().getAliveCount());
        scripts.drain();
        assertEquals(1, events(EventType.EVENT_MONSTER_TIDE_OVER));
        assertFalse(tide().pause());
        assertFalse(tide().resume());
    }

    @Test
    void exactInitialBatchFinishesWhenLiveCountCrossesLowerThreshold() {
        assertEquals(0, start(4, 2, 4));
        scripts.drain();
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_OVER));
        die(monster(1));
        die(monster(2));
        scripts.drain();
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_OVER));
        die(monster(3));
        scripts.drain();
        assertEquals(1, events(EventType.EVENT_MONSTER_TIDE_OVER));
    }

    @Test
    void zeroTotalContinuesPastFirstBatchWithoutDuplicatingLiveConfigIds() {
        assertEquals(0, start(0, 1, 2));
        var tide = tide();
        for (int i = 0; i < 20; i++) {
            var current =
                    scene.getEntities().values().stream().map(e -> (EntityMonster) e).toList();
            current.forEach(this::die);
            assertEquals(2, tide.getAliveCount());
        }
        assertEquals(42, tide.getCreatedCount());
        assertEquals(40, tide.getDeadCount());
        assertEquals(2, scene.getEntities().size());
        scripts.drain();
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_OVER));
    }

    @Test
    void unrelatedDeathsAndRepeatedNotificationsDoNotChangeTide() {
        assertEquals(0, start(0, 1, 2));
        var tide = tide();
        var unrelated = scripts.newMonster(200, group.monsters.get(1));
        scripts.getScriptMonsterSpawnService().onMonsterDead(unrelated);
        var sameConfigWrongObject = scripts.newMonster(group.id, group.monsters.get(1));
        scripts.getScriptMonsterSpawnService().onMonsterDead(sameConfigWrongObject);
        var victim = monster(1);
        die(victim);
        scripts.getScriptMonsterSpawnService().onMonsterDead(victim);
        assertEquals(1, tide.getDeadCount());
        assertEquals(1, tide.getAliveCount());
        scripts.drain();
        assertEquals(1, events(EventType.EVENT_MONSTER_TIDE_DIE));
    }

    @Test
    void anotherSceneCannotAdvanceThisTide() {
        assertEquals(0, start(0, 1, 2));
        var other = new TestMonster(environment.scene);
        other.setGroupId(group.id);
        other.setConfigId(1);
        scripts.getScriptMonsterSpawnService().onMonsterDead(other);
        assertEquals(0, tide().getDeadCount());
    }

    @Test
    void eventsExposeTideSourceActualEntityAndMonsterTypeToLua() throws Exception {
        assertEquals(0, start(0, 1, 2));
        var first = monster(1);
        die(first);
        scripts.drain();
        bindings.put("event", org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(scripts.observed.get(2)));
        assertEquals(
                0,
                lua(
                        "(function() assert(event.source=='1' and event.source_name=='1')"
                                + " assert(event.param1==1 and event.param2==0 and event.param4==1)"
                                + " assert(event.source_eid=="
                                + first.getId()
                                + ") return 0 end)()"));
    }

    @Test
    void tideIdentifiersAreIndependentWithinAndAcrossGroups() {
        assertEquals(0, start(0, 1, 2));
        var first = tide();
        assertEquals(0, scripts.startMonsterTideInGroup("2", group, new Integer[] {3, 4}, 0, 1, 2));
        var second = scripts.getMonsterTide(group.id, "2");
        var other = group(200);
        assertEquals(0, scripts.startMonsterTideInGroup("1", other, new Integer[] {1, 2}, 0, 1, 2));
        die(monster(1));
        assertEquals(1, first.getDeadCount());
        assertEquals(0, second.getDeadCount());
        assertEquals(0, scripts.getMonsterTide(200, "1").getDeadCount());
        assertEquals(3, scripts.getScriptMonsterSpawnService().onMonsterDeadListener.size());
    }

    @Test
    void duplicateActiveTideIsRejectedWithoutChangingItsEntitiesOrListeners() {
        assertEquals(0, start(0, 1, 2));
        var first = tide();
        var entity = monster(1);
        assertEquals(-1, start(0, 1, 2));
        assertSame(first, tide());
        assertSame(entity, monster(1));
        assertEquals(1, scripts.getScriptMonsterSpawnService().onMonsterDeadListener.size());
        assertFalse(first.start());
        assertEquals(1, scripts.getScriptMonsterSpawnService().onMonsterDeadListener.size());
    }

    @Test
    void manualEndReportsReasonAndStopsRefillingWithoutDespawning() throws Exception {
        assertEquals(0, start(0, 1, 2));
        var tide = tide();
        assertEquals(0, lua("ScriptLib.EndMonsterTide(ScriptLib,100,1,9)"));
        assertEquals(2, tide.getAliveCount());
        die(monster(1));
        die(monster(2));
        assertEquals(2, tide.getCreatedCount());
        assertEquals(-1, lua("ScriptLib.EndMonsterTide(ScriptLib,100,1,9)"));
        scripts.drain();
        var event =
                scripts.observed.stream()
                        .filter(e -> e.type == EventType.EVENT_MONSTER_TIDE_OVER)
                        .findFirst()
                        .orElseThrow();
        assertEquals(9, event.param1);
        assertEquals(0, event.param2);
        assertEquals(2, events(EventType.EVENT_MONSTER_TIDE_DIE));
    }

    @Test
    void pauseTracksDeathsAndContinueImmediatelyRefillsMissingMonsters() throws Exception {
        assertEquals(0, start(0, 2, 4));
        assertEquals(0, lua("ScriptLib.PauseAutoMonsterTide(ScriptLib,100,1)"));
        assertEquals(-1, lua("ScriptLib.PauseAutoMonsterTide(ScriptLib,100,1)"));
        die(monster(1));
        die(monster(2));
        die(monster(3));
        assertEquals(1, tide().getAliveCount());
        assertEquals(4, tide().getCreatedCount());
        assertEquals(0, lua("ScriptLib.ContinueAutoMonster(ScriptLib,100,1)"));
        assertEquals(4, tide().getAliveCount());
        assertEquals(7, tide().getCreatedCount());
        assertEquals(-1, lua("ScriptLib.ContinueAutoMonster(ScriptLib,100,1)"));
    }

    @Test
    void killingTideRemovesOnlyOwnedObjectsWithoutDropsDeathsOrEndEvents() throws Exception {
        assertEquals(0, start(0, 1, 2));
        var old = tide();
        var unrelated = scripts.newMonster(group.id, group.monsters.get(3));
        scene.addEntity(unrelated);
        scripts.drain();
        assertEquals(0, lua("ScriptLib.KillMonsterTide(ScriptLib,100,1)"));
        assertEquals(-1, lua("ScriptLib.KillMonsterTide(ScriptLib,100,1)"));
        assertNull(tide());
        assertEquals(List.of(unrelated), List.copyOf(scene.getEntities().values()));
        assertEquals(0, old.getDeadCount());
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_DIE));
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_OVER));
        assertEquals(0, scripts.getScriptMonsterSpawnService().onMonsterDeadListener.size());
    }

    @Test
    void removedOwnedObjectIsNotAllowedToDeleteItsReplacement() {
        assertEquals(0, start(0, 1, 2));
        var removed = monster(1);
        scene.removeEntity(removed);
        var replacement = scripts.newMonster(group.id, group.monsters.get(1));
        replacement.setId(removed.getId());
        scene.addEntity(replacement);
        scripts.killMonsterTide(group.id, "1");
        assertSame(replacement, scene.getEntities().get(replacement.getId()));
    }

    @Test
    void pendingCallbacksFromKilledTideCannotAffectRestartWithSameIdentifier() {
        assertEquals(0, start(0, 1, 2));
        var old = tide();
        var victim = monster(1);
        die(victim);
        scripts.killMonsterTide(100, "1");
        assertEquals(0, start(0, 1, 2));
        scripts.drain();
        assertEquals(2, events(EventType.EVENT_MONSTER_TIDE_CREATE));
        assertEquals(0, events(EventType.EVENT_MONSTER_TIDE_DIE));
        assertNotSame(old, tide());
        assertEquals(0, tide().getDeadCount());
    }

    @Test
    void completedTideCannotRestartUntilFinalCallbacksHaveReturned() {
        assertEquals(0, start(1, 1, 2));
        die(monster(1));
        assertEquals(-1, start(1, 1, 2));
        scripts.drain();
        assertEquals(0, start(1, 1, 2));
    }

    @Test
    void exactGroupUnloadCleansAllItsTidesAndKeepsOtherGroups() {
        assertEquals(0, start(0, 1, 2));
        var other = group(200);
        assertEquals(0, scripts.startMonsterTideInGroup("1", other, new Integer[] {1, 2}, 0, 1, 2));
        scripts.unregisterGroup(group);
        scripts.loaded.remove(group.id);
        scripts.drain();
        assertNull(tide());
        assertNotNull(scripts.getMonsterTide(other.id, "1"));
        assertTrue(scene.getEntities().values().stream().allMatch(e -> e.getGroupId() == other.id));
    }

    @Test
    void staleGroupCleanupCannotRemoveNewDefinitionTide() {
        assertEquals(0, start(0, 1, 2));
        scripts.killMonsterTide(100, "1");
        var replacement = group(100);
        assertEquals(
                0,
                scripts.startMonsterTideInGroup("1", replacement, new Integer[] {1, 2}, 0, 1, 2));
        scripts.stopMonsterTides(group);
        assertSame(replacement, tide().getGroup());
    }

    @Test
    void reloadedGroupDropsOldCallbacksAndOldLiveMonstersOnNextTick() {
        assertEquals(0, start(0, 1, 2));
        group(100);
        scripts.drain();
        scripts.tickMonsterTides();
        assertNull(tide());
        assertTrue(scene.getEntities().isEmpty());
        assertTrue(scripts.observed.isEmpty());
    }

    @Test
    void sceneDestroyAndLegacyUnloadCleanEveryRegisteredTide() {
        assertEquals(0, start(0, 1, 2));
        assertEquals(0, scripts.startMonsterTideInGroup("2", group, new Integer[] {3, 4}, 0, 1, 2));
        scripts.unloadCurrentMonsterTide();
        assertTrue(scene.getEntities().isEmpty());
        assertNull(scripts.getScriptMonsterTideService());
        assertEquals(0, start(0, 1, 2));
        scripts.onDestroy();
        scripts.drain();
        assertTrue(scene.getEntities().isEmpty());
        assertNull(tide());
        assertEquals(-1, start(0, 1, 2));
    }

    @Test
    void creationFailureRollsBackEarlierSpawnsAndQueuedCallbacks() {
        scripts.failAt = 2;
        assertEquals(-1, start(0, 1, 3));
        scripts.drain();
        assertTrue(scene.getEntities().isEmpty());
        assertNull(tide());
        assertTrue(scripts.observed.isEmpty());
        assertEquals(0, scripts.getScriptMonsterSpawnService().onMonsterDeadListener.size());
        scripts.failAt = 0;
        assertEquals(0, start(0, 1, 3));
    }

    @Test
    void destroyedSceneRejectsReentrantSpawnsDuringMonsterCleanup() {
        assertEquals(0, start(0, 1, 2));
        var attempted = new AtomicInteger(10);
        ((TestMonster) monster(1)).onRemove =
                () ->
                        attempted.set(
                                scripts.startMonsterTideInGroup(
                                        "2", group, new Integer[] {3, 4}, 0, 1, 2));
        scripts.onDestroy();
        assertEquals(-1, attempted.get());
        assertTrue(scene.getEntities().isEmpty());
        assertNull(scripts.getMonsterTide(100, "2"));
    }

    @Test
    void closedHistoricalActivitiesRejectTidesAndDoNotReviveTheirGroups() throws Exception {
        for (int id :
                new int[] {
                    emu.grasscutter.game.activity.crucible.CrucibleSceneController.MAIN_GROUP,
                    emu.grasscutter.game.activity.aster.AsterFragments.areas()
                            .keySet()
                            .iterator()
                            .next(),
                    emu.grasscutter.game.activity.aster.AsterMidCamps.camps()
                            .values()
                            .iterator()
                            .next()
                            .groupId()
                }) {
            var historical = group(id);
            assertEquals(
                    -1,
                    scripts.startMonsterTideInGroup(
                            "1", historical, new Integer[] {1, 2}, 0, 1, 2));
        }
        assertEquals(0, scripts.creations);
    }

    @Test
    void factoryExceptionsOnInitialFillAndRefillLeaveNoLiveTideOrSurvivors() {
        scripts.throwAt = 2;
        assertEquals(-1, start(0, 1, 2));
        assertTrue(scene.getEntities().isEmpty());
        assertNull(tide());
        scripts.throwAt = 0;
        assertEquals(0, start(0, 2, 4));
        scripts.throwAt = scripts.creations + 1;
        die(monster(1));
        die(monster(2));
        assertDoesNotThrow(() -> die(monster(3)));
        scripts.tickMonsterTides();
        assertTrue(scene.getEntities().isEmpty());
        assertNull(tide());
    }

    @Test
    void refillFailureAndResumeFailureCleanSurvivors() {
        assertEquals(0, start(0, 2, 4));
        die(monster(1));
        die(monster(2));
        scripts.failAt = scripts.creations + 1;
        die(monster(3));
        assertTrue(scene.getEntities().isEmpty());
        scripts.tickMonsterTides();
        assertNull(tide());
        scripts.failAt = 0;
        assertEquals(0, start(0, 2, 4));
        assertTrue(tide().pause());
        die(monster(1));
        die(monster(2));
        die(monster(3));
        scripts.failAt = scripts.creations + 1;
        assertFalse(tide().resume());
        assertTrue(scene.getEntities().isEmpty());
    }

    @Test
    void unavailableConfigIdsRejectStartBeforeCreatingAnything() {
        var entity = scripts.newMonster(100, group.monsters.get(1));
        scene.addEntity(entity);
        assertEquals(
                -1, scripts.startMonsterTideInGroup("1", group, new Integer[] {1, 2}, 0, 1, 2));
        assertEquals(0, scripts.creations);
        assertSame(entity, monster(1));
        assertNull(tide());
    }

    @Test
    void sameConfigInAnotherGroupDoesNotBlockInitialFill() {
        scene.addEntity(scripts.newMonster(200, group.monsters.get(1)));
        assertEquals(0, start(0, 1, 2));
        assertEquals(3, scene.getEntities().size());
    }

    @Test
    void occupiedConfigIsSkippedWhenThereAreEnoughOtherDefinitions() {
        var existing = scripts.newMonster(100, group.monsters.get(1));
        scene.addEntity(existing);
        assertEquals(0, start(0, 1, 2));
        assertSame(existing, monster(1));
        assertNotNull(monster(2));
        assertNotNull(monster(3));
        scripts.killMonsterTide(100, "1");
        assertSame(existing, monster(1));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "nil",
                "1",
                "{[2]=1}",
                "{1,k=2}",
                "{0}",
                "{-1}",
                "{1,1}",
                "{1.5}",
                "{'1'}",
                "{true}",
                "{2147483648}",
                "{0/0}",
                "{1/0}"
            })
    void malformedOrdersAreRejectedBeforeLoadingOrMutation(String order) throws Exception {
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,100," + order + ",0,1,1)"));
        assertEquals(0, scripts.loads);
        assertEquals(0, scripts.creations);
        assertNull(tide());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0,1,0",
                "0,2,1",
                "0,1,3",
                "-1,1,2",
                "1.5,1,2",
                "'1',1,2",
                "2147483648,1,2",
                "0,0,2",
                "0,1.5,2",
                "0,1,'2'"
            })
    void malformedCountsAreRejectedBeforeLoadingOrMutation(String counts) throws Exception {
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,100,{1,2}," + counts + ")"));
        assertEquals(0, scripts.loads);
        assertEquals(0, scripts.creations);
        assertNull(tide());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.2", "'1'", "nil", "2147483648"})
    void allOperationsRejectInvalidGroupOrTideIdentifiers(String value) throws Exception {
        for (var name :
                List.of(
                        "KillMonsterTide",
                        "PauseAutoMonsterTide",
                        "ContinueAutoMonster",
                        "EndMonsterTide")) {
            var tail = name.equals("EndMonsterTide") ? ",0" : "";
            assertEquals(-1, lua("ScriptLib." + name + "(ScriptLib,100," + value + tail + ")"));
            assertEquals(-1, lua("ScriptLib." + name + "(ScriptLib," + value + ",1" + tail + ")"));
        }
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib," + value + ",100,{1,2},0,1,2)"));
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1," + value + ",{1,2},0,1,2)"));
        assertEquals(0, scripts.loads);
    }

    @Test
    void unknownDefinitionsAndMissingTidesReturnErrors() throws Exception {
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,100,{99},0,1,1)"));
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,200,{1},0,1,1)"));
        for (var name :
                List.of(
                        "KillMonsterTide",
                        "PauseAutoMonsterTide",
                        "ContinueAutoMonster",
                        "EndMonsterTide"))
            assertEquals(
                    -1,
                    lua(
                            "ScriptLib."
                                    + name
                                    + "(ScriptLib,100,1"
                                    + (name.equals("EndMonsterTide") ? ",0" : "")
                                    + ")"));
        assertEquals(0, scripts.creations);
    }

    @Test
    void successfulLuaCallCanLoadDestinationAndPreservesCallerContext() throws Exception {
        var destination = group(200);
        scripts.loaded.remove(200);
        scripts.available.put(200, destination);
        assertEquals(0, lua("ScriptLib.AutoMonsterTide(ScriptLib,7,200,{1,2},0,1,2)"));
        assertEquals(1, scripts.loads);
        assertSame(group, library.getCurrentGroup().orElseThrow());
        assertEquals(2, scripts.getMonsterTide(200, "7").getAliveCount());
    }

    @Test
    void staleOriginAfterDestinationLoadingCannotStartTide() throws Exception {
        var destination = group(200);
        scripts.loaded.remove(200);
        scripts.available.put(200, destination);
        scripts.onLoad = () -> group(100);
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,7,200,{1,2},0,1,2)"));
        assertEquals(0, scripts.creations);
    }

    @Test
    void staleOriginAndDestroyedSceneRejectAllLuaMutations() throws Exception {
        scripts.loaded.remove(group.id);
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,100,{1,2},0,1,2)"));
        scripts.loaded.put(group.id, group);
        scripts.onDestroy();
        assertEquals(-1, lua("ScriptLib.AutoMonsterTide(ScriptLib,1,100,{1,2},0,1,2)"));
        assertEquals(0, scripts.loads);
    }

    @Test
    void invalidEndReasonDoesNotFinishLiveTide() throws Exception {
        assertEquals(0, start(0, 1, 2));
        for (var reason : List.of("-1", "1.5", "'1'", "2147483648", "nil"))
            assertEquals(-1, lua("ScriptLib.EndMonsterTide(ScriptLib,100,1," + reason + ")"));
        assertTrue(tide().pause());
        assertFalse(tide().finish(-1));
    }

    @Test
    void listenersCanRemoveThemselvesDuringNotificationWithoutSkippingOthers() {
        var listeners = scripts.getScriptMonsterSpawnService();
        var calls = new AtomicInteger();
        emu.grasscutter.scripts.listener.ScriptMonsterListener[] self = {null};
        self[0] =
                monster -> {
                    calls.incrementAndGet();
                    listeners.removeMonsterDeadListener(self[0]);
                };
        listeners.addMonsterDeadListener(self[0]);
        listeners.addMonsterDeadListener(monster -> calls.incrementAndGet());
        listeners.onMonsterDead(scripts.newMonster(100, group.monsters.get(1)));
        assertEquals(2, calls.get());
    }

    @Test
    void realExecutorRunsLuaWithSceneLockAndPreventsConcurrentTideReplacement() throws Exception {
        scripts.realEvents = true;
        scripts.registerLocalGroups(List.of(group));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var complete = new CountDownLatch(1);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        group.getBindings()
                .put(
                        "capture",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                try {
                                    assertTrue(Thread.holdsLock(scene));
                                    assertEquals(1, args.arg(2).get("param4").toint());
                                    entered.countDown();
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (Throwable failure) {
                                    error.set(failure);
                                } finally {
                                    complete.countDown();
                                }
                                return LuaValue.ZERO;
                            }
                        });
        var trigger = new SceneTrigger();
        trigger.currentGroup = group;
        trigger.setName("tide_create");
        trigger.setEvent(EventType.EVENT_MONSTER_TIDE_CREATE);
        trigger.setSource("1");
        trigger.setCondition("");
        trigger.setAction("capture");
        trigger.setTrigger_count(0);
        scripts.registerTrigger(trigger);
        var worker = Executors.newSingleThreadExecutor();
        try {
            assertEquals(0, start(1, 1, 2));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var kill = worker.submit(() -> scripts.killMonsterTide(100, "1"));
            assertThrows(TimeoutException.class, () -> kill.get(80, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(0, kill.get(5, TimeUnit.SECONDS));
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            assertNull(error.get());
            assertNull(tide());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    private static final class TideScene extends AbilityTestFixture.TestScene {
        final TideScripts scripts;

        TideScene(AbilityTestFixture.TestWorld world) {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
            scripts = new TideScripts(this);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class TestMonster extends EntityMonster {
        Runnable onRemove = () -> {};

        TestMonster(Scene scene) {
            super(
                    scene,
                    new Gson()
                            .fromJson(
                                    "{\"id\":21010101,\"type\":\"MONSTER_ORDINARY\",\"equips\":[],\"affix\":[]}",
                                    MonsterData.class),
                    new Position(),
                    new Position(),
                    1);
        }

        @Override
        public void initAbilities() {}

        @Override
        public void recalcStats() {}

        @Override
        public void onRemoved() {
            onRemove.run();
        }

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }

    private static final class TideScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>(), available = new HashMap<>();
        final List<FutureTask<Void>> queued = new ArrayList<>();
        final List<ScriptArgs> observed = new ArrayList<>();
        int loads, creations, failAt, throwAt;
        boolean realEvents;
        Runnable onLoad = () -> {};

        TideScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public SceneGroup getGroupById(int id) {
            loads++;
            var group = loaded.get(id);
            if (group == null) {
                group = available.get(id);
                if (group != null) loaded.put(id, group);
                onLoad.run();
            }
            return group;
        }

        TestMonster newMonster(int group, SceneMonster definition) {
            var entity = new TestMonster(getScene());
            entity.setGroupId(group);
            entity.setConfigId(definition.config_id);
            entity.setMetaMonster(definition);
            return entity;
        }

        @Override
        public EntityMonster createMonster(int group, int block, SceneMonster definition) {
            creations++;
            if (creations == throwAt) throw new IllegalStateException("fixture factory failure");
            return creations == failAt ? null : newMonster(group, definition);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event, BooleanSupplier current, boolean locked) {
            if (realEvents) return super.callEvent(event, current, locked);
            assertTrue(locked);
            var task =
                    new FutureTask<Void>(
                            () -> {
                                synchronized (getScene()) {
                                    if (current.getAsBoolean()) observed.add(event);
                                }
                                return null;
                            });
            queued.add(task);
            return task;
        }

        void drain() {
            var tasks = List.copyOf(queued);
            queued.clear();
            for (var task : tasks) {
                task.run();
                if (!task.isCancelled())
                    try {
                        task.get();
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
            }
        }
    }
}
