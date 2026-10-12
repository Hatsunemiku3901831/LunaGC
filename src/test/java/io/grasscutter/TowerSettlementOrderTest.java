package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tower.TowerData;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

import javax.script.SimpleBindings;

/** Real challenge/factory/Lua calls with a controlled scene clock and captured queued events. */
class TowerSettlementOrderTest extends QuestTestFixture {
    private static final int FIRST = 10000021,
            SECOND = 10000022,
            STATE = 233401001,
            UPPER = 233401003,
            LOWER = 233401004;
    private ClockScene scene;
    private ScriptLib library;
    private final List<CountDownLatch> releases = new ArrayList<>();
    private final List<Future<?>> jobs = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger finishes =
            new java.util.concurrent.atomic.AtomicInteger();
    private final List<Runnable> maps = new ArrayList<>();

    static class ClockScene extends Scene {
        int seconds = 100;
        boolean settle;
        final List<Integer> stages = new CopyOnWriteArrayList<>();

        @Override
        public void triggerDungeonEvent(
                emu.grasscutter.game.dungeons.enums.DungeonPassConditionType type, int... params) {
            if (type
                    == emu.grasscutter.game.dungeons.enums.DungeonPassConditionType
                            .DUNGEON_COND_FINISH_CHALLENGE) {
                stages.add(getScriptManager().getVariables(STATE).get("stage"));
                if (!settle) return;
            }
            super.triggerDungeonEvent(type, params);
        }

        SpyScripts scripts;

        ClockScene(World world, SceneData data) {
            super(world, data, false);
            scripts = new SpyScripts(this);
        }

        @Override
        public int getSceneTimeSeconds() {
            return seconds;
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }

        void close() {
            scripts.onDestroy();
            super.getScriptManager().onDestroy();
        }
    }

    static class SpyScripts extends SceneScriptManager {
        final List<ScriptArgs> events = new ArrayList<>();
        final List<Future<?>> deliveries = new CopyOnWriteArrayList<>();
        int tideStops;

        SpyScripts(Scene scene) {
            super(scene, false);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event) {
            var result = super.callEvent(event);
            deliveries.add(result);
            return result;
        }

        @Override
        public Future<?> callEvent(
                ScriptArgs event,
                java.util.function.BooleanSupplier current,
                boolean lockScene,
                Runnable after) {
            var result = super.callEvent(event, current, lockScene, after);
            deliveries.add(result);
            return result;
        }

        @Override
        public void unloadCurrentMonsterTide() {
            tideStops++;
            super.unloadCurrentMonsterTide();
        }
    }

    static class Owned extends TowerChamberCheckpointTest.OwnedAvatar {
        Owned(int id, AvatarSkillDepotData depot) {
            super(id, depot);
        }

        @Override
        public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() {
            return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
        }

        @Override
        public Set<Integer> getProudSkillList() {
            return Set.of();
        }

        @Override
        public int getCoreProudSkillLevel() {
            return 0;
        }

        @Override
        public GameItem getWeaponNotNull() {
            return new GameItem() {
                @Override
                public long getGuid() {
                    return 77;
                }
            };
        }
    }

    private <K, V> void remember(Map<K, V> map) {
        var old = new HashMap<>(map);
        maps.add(
                () -> {
                    map.clear();
                    map.putAll(old);
                });
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void halfScene() throws Exception {
        remember(GameData.getTowerFloorDataMap());
        remember(GameData.getTowerLevelDataMap());
        remember(GameData.getDungeonDataMap());
        remember(GameData.getDungeonChallengeConfigDataMap());
        remember(GameData.getDungeonPassConfigDataMap());
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201,dungeonId:9001}",
                                TowerLevelData.class));
        GameData.getDungeonPassConfigDataMap()
                .put(
                        50,
                        gson.fromJson(
                                "{id:50,logicType:'LOGIC_AND',conds:[{condType:'DUNGEON_COND_FINISH_CHALLENGE',param:[2,0,0]}]}",
                                DungeonPassConfigData.class));
        var data = gson.fromJson("{id:9001,sceneId:9,passCond:50}", DungeonData.class);
        GameData.getDungeonDataMap().put(9001, data);
        for (int id : List.of(197, 198))
            GameData.getDungeonChallengeConfigDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:" + id + ",challengeType:'CHALLENGE_KILL_COUNT_IN_TIME'}",
                                    DungeonChallengeConfigData.class));
        GameData.getDungeonChallengeConfigDataMap()
                .put(
                        188,
                        gson.fromJson(
                                "{id:188,challengeType:'CHALLENGE_KILL_COUNT_GUARD_HP'}",
                                DungeonChallengeConfigData.class));
        GameData.getDungeonChallengeConfigDataMap()
                .put(
                        190,
                        gson.fromJson(
                                "{id:190,challengeType:'CHALLENGE_TIME_FLY'}",
                                DungeonChallengeConfigData.class));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        replaceField(
                emu.grasscutter.server.game.GameServer.class,
                player.server,
                "dungeonSystem",
                new emu.grasscutter.game.dungeons.DungeonSystem(player.server));
        replaceField(
                emu.grasscutter.server.game.GameServer.class,
                player.server,
                "towerSystem",
                new emu.grasscutter.game.tower.TowerSystem(player.server) {
                    @Override
                    public int getNextFloorId(int floor) {
                        return 0;
                    }

                    @Override
                    public Integer getLastEntranceFloor() {
                        return 6002;
                    }
                });
        var world = new World(player.server, player);
        world.getPlayers().add(player);
        player.setWorld(world);
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene =
                new ClockScene(
                        world, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        world.getScenes().put(9, scene);
        player.setScene(scene);
        player.setSceneId(9);
        var depot =
                gson.fromJson(
                        "{id:801,skills:[],energySkillData:{costElemType:'Grass',costElemVal:60},elementType:'Grass'}",
                        AvatarSkillDepotData.class);
        for (int id : List.of(FIRST, SECOND)) {
            var avatar = new Owned(id, depot);
            replaceField(emu.grasscutter.game.avatar.Avatar.class, avatar, "skillDepot", depot);
            assertTrue(player.getAvatars().addAvatar(avatar));
            avatar.setCurrentHp(100);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY, 60);
        }
        assertTrue(
                player.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(
                                        List.of(player.getAvatars().getAvatarById(FIRST).getGuid()),
                                        List.of(
                                                player.getAvatars()
                                                        .getAvatarById(SECOND)
                                                        .getGuid()))));
        replaceField(TeamManager.class, player.getTeamManager(), "useTemporarilyTeamIndex", 0);
        scene.addPlayer(player);
        var manager = new DungeonManager(scene, data);
        manager.setTowerDungeon(true);
        scene.setDungeonManager(manager);
        for (int id : List.of(STATE, UPPER, LOWER)) register(id);
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scene.scripts);
        library.setCurrentGroup(scene.scripts.getLoadedGroupById(UPPER));
        player.packets.clear();
        scene.scripts.tideStops = 0;
        scene.scripts.getVariables(STATE).put("stage", 1);
        scene.addDungeonSettleObserver(
                new emu.grasscutter.game.dungeons.TowerDungeonSettleListener());
    }

    private void register(int id) {
        var group = SceneGroup.of(id);
        group.setLoaded(true);
        group.monsters = Map.of();
        group.gadgets = Map.of();
        group.regions = Map.of();
        group.triggers = Map.of();
        var stage = new SceneVar();
        stage.name = "stage";
        stage.value = 0;
        var time = new SceneVar();
        time.name = "TPL_TIME";
        time.value = 0;
        group.variables = List.of(stage, time);
        scene.scripts.registerLocalGroups(List.of(group));
        scene.scripts.loadGroupFromScript(group);
    }

    @AfterEach
    void close() throws Exception {
        releases.forEach(CountDownLatch::countDown);
        for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        flush();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        scene.close();
        drain();
        maps.forEach(Runnable::run);
    }

    private WorldChallenge challenge(boolean legacy) {
        var trigger =
                new emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger() {
                    @Override
                    public void onFinish(WorldChallenge challenge) {
                        finishes.incrementAndGet();
                    }
                };
        var group = scene.scripts.getLoadedGroupById(LOWER);
        WorldChallenge value =
                legacy
                        ? new emu.grasscutter.game.dungeons.challenge.DungeonChallenge(
                                scene, group, 198, 2, List.of(2, 400), 400, 2, List.of(trigger))
                        : new WorldChallenge(
                                scene, group, 198, 2, List.of(2, 400), 400, 2, List.of(trigger));
        scene.setChallenge(value);
        value.start();
        player.packets.clear();
        return value;
    }

    private record Gate(CountDownLatch entered, CountDownLatch release) {}

    private Gate action(int event, String body) throws Exception {
        var group = scene.scripts.getLoadedGroupById(LOWER);
        var gate = new Gate(new CountDownLatch(1), new CountDownLatch(1));
        releases.add(gate.release());
        var bindings = new SimpleBindings();
        if (group.getBindings() != null) bindings.putAll(group.getBindings());
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        var gateName = "gate_" + event;
        bindings.put(
                gateName,
                new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                        gate.entered().countDown();
                        try {
                            if (!gate.release().await(5, TimeUnit.SECONDS))
                                throw new AssertionError("test gate timed out");
                        } catch (InterruptedException error) {
                            throw new RuntimeException(error);
                        }
                        return org.luaj.vm2.LuaValue.NIL;
                    }
                });
        replaceField(SceneGroup.class, group, "bindings", bindings);
        var name = "test_action_" + event;
        bindings.put(
                name,
                ScriptLoader.getEngine()
                        .eval(
                                "return function(context, evt) "
                                        + gateName
                                        + "(); "
                                        + body
                                        + " end",
                                bindings));
        var trigger = new SceneTrigger();
        trigger.setName("test_" + event);
        trigger.setCurrentGroup(group);
        trigger.setEvent(event);
        trigger.setSource(event == EventType.EVENT_CHALLENGE_SUCCESS ? "2" : "");
        trigger.setCondition("");
        trigger.setAction(name);
        scene.scripts.registerTrigger(trigger);
        return gate;
    }

    private Gate success() throws Exception {
        return action(
                EventType.EVENT_CHALLENGE_SUCCESS,
                "ScriptLib.SetGroupVariableValueByGroup(context, 'stage', 2, "
                        + STATE
                        + "); return 0");
    }

    private static void entered(Gate gate) throws Exception {
        assertTrue(gate.entered().await(2, TimeUnit.SECONDS), "Lua action must actually run");
    }

    private void flush() throws Exception {
        for (int i = 0; i < scene.scripts.deliveries.size(); i++)
            scene.scripts.deliveries.get(i).get(2, TimeUnit.SECONDS);
        drain();
    }

    private void completed() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify)
                && System.nanoTime() < until) Thread.sleep(5);
        assertEquals(
                1,
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successWaitsForLuaWithoutHoldingUpTheCaller(boolean legacy) throws Exception {
        var gate = success();
        var value = challenge(legacy);
        synchronized (scene) {
            value.done();
        }
        entered(gate);
        assertTrue(scene.stages.isEmpty(), "pass conditions must wait for Lua");
        assertEquals(0, finishes.get());
        gate.release().countDown();
        flush();
        assertEquals(List.of(2), scene.stages);
        assertEquals(1, finishes.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "destroy",
                "challenge",
                "manager",
                "world",
                "player",
                "group",
                "floor",
                "roster",
                "reuse",
                "restart"
            })
    void anObsoleteQueuedSuccessCannotFinishTheReplacement(String change) throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        releases.add(release);
        for (int i = 0; i < 4; i++)
            jobs.add(
                    SceneScriptManager.eventExecutor.submit(
                            () -> {
                                entered.countDown();
                                try {
                                    release.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                }
                            }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var gate = success();
        var value = challenge(false);
        value.done();
        switch (change) {
            case "destroy" -> scene.scripts.onDestroy();
            case "challenge" -> scene.setChallenge(null);
            case "manager" -> scene.setDungeonManager(
                    new DungeonManager(scene, scene.getDungeonManager().getDungeonData()));
            case "world" -> scene.getWorld().getScenes().remove(scene.getId());
            case "player" -> player.setScene(null);
            case "group" -> scene.scripts.loadGroupFromScript(SceneGroup.of(LOWER));
            case "floor" -> replaceField(
                    TowerData.class,
                    player.getTowerManager().getTowerData(),
                    "currentFloorId",
                    6003);
            case "roster" -> replaceField(
                    TowerData.class,
                    player.getTowerManager().getTowerData(),
                    "abyssAvatarIds",
                    List.of());
            case "reuse" -> value.start();
            case "restart" -> scene.getDungeonManager().restartDungeon();
            default -> fail(change);
        }
        release.countDown();
        gate.release().countDown();
        flush();
        assertTrue(scene.stages.isEmpty());
        assertEquals(0, finishes.get());
        assertEquals(1, scene.scripts.getVariables(STATE).get("stage"));
    }

    @Test
    void repeatedDoneProducesOneCompletion() throws Exception {
        var gate = success();
        var value = challenge(true);
        value.done();
        value.done();
        entered(gate);
        gate.release().countDown();
        flush();
        value.done();
        assertEquals(List.of(2), scene.stages);
        assertEquals(1, finishes.get());
    }

    @Test
    void lowerResourceActionPrecedesRealTowerRecordsAndSettlePacket() throws Exception {
        var source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "resources/Scripts/Scene/33401/scene33401_group233401004.lua"));
        int begin = source.indexOf("function action_EVENT_CHALLENGE_SUCCESS_4009");
        int end = source.indexOf("\nend", begin) + 4;
        assertTrue(begin >= 0 && end > begin);
        var gate =
                action(
                        EventType.EVENT_CHALLENGE_SUCCESS,
                        source.substring(begin, end)
                                + " return action_EVENT_CHALLENGE_SUCCESS_4009(context, evt)");
        scene.settle = true;
        var value = challenge(false);
        // Run away from the event pool: the old implementation waits for Lua during finishDungeon.
        var done = CompletableFuture.runAsync(value::done);
        jobs.add(done);
        entered(gate);
        assertTrue(scene.stages.isEmpty(), "stage=1 must not be settled");
        gate.release().countDown();
        done.get(2, TimeUnit.SECONDS);
        completed();
        assertEquals(List.of(2), scene.stages);
        assertEquals(2, player.getTowerManager().getCurrentLevel());
        assertTrue(
                player.getTowerManager()
                        .getRecordMap()
                        .get(6002)
                        .getPassedLevelMap()
                        .containsKey(6201));
    }

    @Test
    void ordinaryChallengeRetainsImmediatePassConditionDelivery() throws Exception {
        scene.getDungeonManager().setTowerDungeon(false);
        var gate = success();
        var value = challenge(false);
        value.done();
        entered(gate);
        assertEquals(List.of(1), scene.stages);
        assertEquals(1, finishes.get());
        gate.release().countDown();
        flush();
    }

    @Test
    void settleLuaReturnsBeforeRecordsAndTheFinalPacket() throws Exception {
        var success = success();
        success.release().countDown();
        var settle = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        scene.settle = true;
        challenge(false).done();
        entered(settle);
        assertEquals(1, player.getTowerManager().getCurrentLevel());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify));
        settle.release().countDown();
        completed();
        scene.getDungeonManager().finishDungeon();
        flush();
        assertEquals(2, player.getTowerManager().getCurrentLevel());
        assertEquals(
                1,
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"world", "challenge", "player", "restart"})
    void aRetiredSettleContinuationCannotChangePlayerRecords(String change) throws Exception {
        var success = success();
        success.release().countDown();
        var settle = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        scene.settle = true;
        var done = CompletableFuture.runAsync(challenge(false)::done);
        jobs.add(done);
        entered(settle);
        switch (change) {
            case "world" -> scene.getWorld().getScenes().remove(scene.getId());
            case "challenge" -> scene.setChallenge(null);
            case "player" -> player.setScene(null);
            case "restart" -> scene.getDungeonManager().restartDungeon();
            default -> fail(change);
        }
        settle.release().countDown();
        done.get(2, TimeUnit.SECONDS);
        flush();
        assertEquals(1, player.getTowerManager().getCurrentLevel());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify));
        assertFalse(player.getPlayerProgress().getCompletedDungeons().contains(9001));
    }

    @Test
    void oneAvailableEventWorkerCanFinishBothQueuedEvents() throws Exception {
        var entered = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        releases.add(release);
        for (int i = 0; i < 3; i++)
            jobs.add(
                    SceneScriptManager.eventExecutor.submit(
                            () -> {
                                entered.countDown();
                                try {
                                    release.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                }
                            }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var gate = success();
        gate.release().countDown();
        scene.settle = true;
        var done = CompletableFuture.runAsync(challenge(false)::done);
        jobs.add(done);
        done.get(1, TimeUnit.SECONDS);
        completed();
        assertEquals(2, player.getTowerManager().getCurrentLevel());
        release.countDown();
    }

    @Test
    void waitingForLuaDoesNotUseUpTheCompletedChallengesStars() throws Exception {
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201,dungeonId:9001,conds:["
                                    + "{towerCondType:'TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN',argumentList:[2,350]},"
                                    + "{towerCondType:'TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN',argumentList:[2,350]},"
                                    + "{towerCondType:'TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN',argumentList:[2,350]}]}",
                                TowerLevelData.class));
        var gate = success();
        var value = challenge(false);
        scene.seconds += 30;
        assertEquals(3, player.getTowerManager().getCurLevelStars());
        scene.settle = true;
        value.done();
        entered(gate);
        assertEquals(30, value.getFinishedTime());
        scene.seconds += 100;
        assertEquals(
                3,
                player.getTowerManager().getCurLevelStars(),
                "queueing and Lua wait are not combat time");
        gate.release().countDown();
        flush();
        assertEquals(3, player.getTowerManager().getRecordMap().get(6002).getLevelStars(6201));
    }
}
