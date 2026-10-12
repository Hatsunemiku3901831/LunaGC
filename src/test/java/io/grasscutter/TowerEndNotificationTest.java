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
class TowerEndNotificationTest extends QuestTestFixture {
    private static final int FIRST = 10000021,
            SECOND = 10000022,
            STATE = 233401001,
            UPPER = 233401003,
            LOWER = 233401004;
    private ClockScene scene;
    private ScriptLib library;
    private final List<
                    emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult
                            .DungeonEndReason>
            reasons = new CopyOnWriteArrayList<>();
    private ExitWorld world;
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
        world = new ExitWorld(player.server, player);
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
        scene.getLoadedGroups().add(scene.scripts.getLoadedGroupById(STATE));
        scene.addDungeonSettleObserver((manager1, reason) -> reasons.add(reason));
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
        group.suites = List.of(new SceneSuite());
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
        try {
            releases.forEach(CountDownLatch::countDown);
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
            flush();
        } finally {
            library.removeCurrentGroup();
            library.removeSceneScriptManager();
            scene.close();
            drain();
            maps.forEach(Runnable::run);
        }
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

    static class ExitWorld extends World {
        final java.util.concurrent.atomic.AtomicInteger transfers =
                new java.util.concurrent.atomic.AtomicInteger();
        volatile int target, delay;

        ExitWorld(
                emu.grasscutter.server.game.GameServer server,
                emu.grasscutter.game.player.Player owner) {
            super(server, owner);
        }

        @Override
        public void queueTransferPlayerToScene(
                emu.grasscutter.game.player.Player player, int id, Position pos, int ms) {
            target = id;
            delay = ms;
            transfers.incrementAndGet();
        }
    }

    private WorldChallenge active() {
        var value =
                new WorldChallenge(
                        scene,
                        scene.scripts.getLoadedGroupById(LOWER),
                        198,
                        2,
                        List.of(2, 400),
                        400,
                        2,
                        List.of());
        scene.setChallenge(value);
        value.start();
        player.packets.clear();
        return value;
    }

    private CompletableFuture<Void> end(boolean quit) {
        var result =
                CompletableFuture.runAsync(
                        () -> {
                            if (quit) scene.getDungeonManager().quitDungeon();
                            else scene.getDungeonManager().failDungeon();
                        });
        jobs.add(result);
        return result;
    }

    private emu.grasscutter.net.proto.DungeonSettleNotifyOuterClass.DungeonSettleNotify packet()
            throws Exception {
        var packets =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify)
                        .toList();
        assertEquals(1, packets.size());
        return emu.grasscutter.net.proto.DungeonSettleNotifyOuterClass.DungeonSettleNotify
                .parseFrom(packets.get(0).getData());
    }

    private void assertAborted(boolean quit) throws Exception {
        completed();
        flush();
        assertFalse(packet().getIsSuccess());
        assertEquals(9001, packet().getDungeonId());
        assertEquals(
                List.of(
                        quit
                                ? emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult
                                        .DungeonEndReason.QUIT
                                : emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult
                                        .DungeonEndReason.FAILED),
                reasons);
        assertEquals(1, player.getTowerManager().getCurrentLevel());
        assertFalse(player.getPlayerProgress().getCompletedDungeons().contains(9001));
        assertNull(player.getTowerManager().getRecordMap().get(6002));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void abortReturnsWithoutWaitingForItsLuaAndReportsLowerHalfFailure(boolean quit)
            throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        var done = end(quit);
        entered(gate);
        done.get(1, TimeUnit.SECONDS);
        assertTrue(reasons.isEmpty());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify));
        gate.release().countDown();
        assertAborted(quit);
        assertFalse(scene.getChallenge().inProgress());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aChamberCanBeAbortedBeforeItsChallengeExists(boolean quit) throws Exception {
        assertNull(scene.getChallenge());
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        gate.release().countDown();
        end(quit).get(1, TimeUnit.SECONDS);
        assertAborted(quit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedAbortDoesNotEmitAnotherEndOrAdvanceTheLevel(boolean quit) throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        gate.release().countDown();
        end(quit).get(1, TimeUnit.SECONDS);
        assertAborted(quit);
        end(quit).get(1, TimeUnit.SECONDS);
        assertAborted(quit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"world", "player", "manager", "challenge", "restart", "roster"})
    void retiredAbortCannotSettleNewPlayerState(String change) throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        var done = end(false);
        entered(gate);
        switch (change) {
            case "world" -> world.getScenes().remove(scene.getId());
            case "player" -> player.setScene(null);
            case "manager" -> scene.setDungeonManager(
                    new DungeonManager(scene, scene.getDungeonManager().getDungeonData()));
            case "challenge" -> scene.setChallenge(null);
            case "restart" -> scene.getDungeonManager().restartDungeon();
            case "roster" -> replaceField(
                    TowerData.class,
                    player.getTowerManager().getTowerData(),
                    "abyssAvatarIds",
                    List.of());
            default -> fail(change);
        }
        gate.release().countDown();
        done.get(2, TimeUnit.SECONDS);
        flush();
        assertTrue(reasons.isEmpty());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.DungeonSettleNotify));
        assertEquals(1, player.getTowerManager().getCurrentLevel());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void abortSupersedesAPendingSuccessBeforeRewardsAreRecorded(boolean quit) throws Exception {
        var value = active();
        value.done();
        flush();
        scene.scripts.getVariables(STATE).put("stage", 2);
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        scene.getDungeonManager().finishDungeon();
        entered(gate);
        var done = end(quit);
        done.get(1, TimeUnit.SECONDS);
        gate.release().countDown();
        assertAborted(quit);
    }

    @Test
    void actualLowerFailureLuaCanFinishWithOnlyOneAvailableEventWorker() throws Exception {
        var value = active();
        var occupied = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        releases.add(release);
        for (int i = 0; i < 3; i++)
            jobs.add(
                    SceneScriptManager.eventExecutor.submit(
                            () -> {
                                occupied.countDown();
                                try {
                                    release.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                }
                            }));
        assertTrue(occupied.await(2, TimeUnit.SECONDS));
        var source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "resources/Scripts/Scene/33401/scene33401_group233401004.lua"));
        int begin = source.indexOf("function action_EVENT_CHALLENGE_FAIL_4010");
        int end = source.indexOf("function condition_EVENT_MONSTER_TIDE_DIE_4011", begin);
        assertTrue(begin >= 0 && end > begin);
        var gate =
                action(
                        EventType.EVENT_CHALLENGE_FAIL,
                        source.substring(begin, end)
                                + " return action_EVENT_CHALLENGE_FAIL_4010(context, evt)");
        gate.release().countDown();
        value.fail();
        completed();
        release.countDown();
        assertAborted(false);
        assertEquals(1, scene.scripts.getGroupInstanceById(LOWER).getActiveSuiteId());
    }

    @Test
    void actualExitKeepsItsTeamsUntilTheQuitLuaHasReturned() throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        var teams = player.getTeamManager().getTemporaryTeamAvatarIds();
        var done =
                CompletableFuture.runAsync(
                        () -> player.getServer().getDungeonSystem().exitDungeon(player));
        jobs.add(done);
        entered(gate);
        done.get(1, TimeUnit.SECONDS);
        assertEquals(teams, player.getTeamManager().getTemporaryTeamAvatarIds());
        assertEquals(0, world.transfers.get());
        gate.release().countDown();
        assertAborted(true);
        assertEquals(List.of(), player.getTeamManager().getTemporaryTeamAvatarIds());
        player.getServer().getDungeonSystem().exitDungeon(player);
        assertEquals(1, world.transfers.get());
        assertEquals(3, world.target);
        assertEquals(200, world.delay);
    }

    @Test
    void retiredQuitCannotCleanTheReplacementTeamsOrTeleportIt() throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        var teams = player.getTeamManager().getTemporaryTeamAvatarIds();
        var done =
                CompletableFuture.runAsync(
                        () -> player.getServer().getDungeonSystem().exitDungeon(player));
        jobs.add(done);
        entered(gate);
        player.setScene(null);
        gate.release().countDown();
        done.get(2, TimeUnit.SECONDS);
        flush();
        assertEquals(teams, player.getTeamManager().getTemporaryTeamAvatarIds());
        assertEquals(0, world.transfers.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exitAfterAFinalizedResultCleansOnceWithoutSettlingAgain(boolean success) throws Exception {
        var value = active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        gate.release().countDown();
        if (success) {
            value.done();
            flush();
            scene.scripts.getVariables(STATE).put("stage", 2);
            scene.getDungeonManager().finishDungeon();
            completed();
            flush();
        } else {
            end(false).get(1, TimeUnit.SECONDS);
            assertAborted(false);
        }
        int count = reasons.size();
        player.getServer().getDungeonSystem().exitDungeon(player);
        flush();
        assertEquals(count, reasons.size());
        assertEquals(1, world.transfers.get());
        assertEquals(List.of(), player.getTeamManager().getTemporaryTeamAvatarIds());
        assertEquals(success, packet().getIsSuccess());
        player.getServer().getDungeonSystem().exitDungeon(player);
        assertEquals(1, world.transfers.get());
    }

    @Test
    void repeatedExitDuringQuitLuaRegistersOneReturnTransfer() throws Exception {
        active();
        var gate = action(EventType.EVENT_DUNGEON_SETTLE, "return 0");
        player.getServer().getDungeonSystem().exitDungeon(player);
        entered(gate);
        player.getServer().getDungeonSystem().exitDungeon(player);
        assertEquals(0, world.transfers.get());
        gate.release().countDown();
        assertAborted(true);
        assertEquals(1, world.transfers.get());
    }
}
