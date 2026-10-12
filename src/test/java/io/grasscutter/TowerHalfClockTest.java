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
import emu.grasscutter.net.proto.DungeonChallengeBeginNotifyOuterClass.DungeonChallengeBeginNotify;
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
class TowerHalfClockTest extends QuestTestFixture {
    private static final int FIRST = 10000021,
            SECOND = 10000022,
            STATE = 233401001,
            UPPER = 233401003,
            LOWER = 233401004;
    private ClockScene scene;
    private ScriptLib library;
    private final List<Runnable> maps = new ArrayList<>();

    static class ClockScene extends Scene {
        int seconds = 100;
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
        int tideStops;

        SpyScripts(Scene scene) {
            super(scene, false);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> callEvent(
                ScriptArgs event,
                java.util.function.BooleanSupplier current,
                boolean lockScene,
                Runnable after) {
            if (current.getAsBoolean()) {
                events.add(event);
                if (after != null) after.run();
            }
            return CompletableFuture.completedFuture(null);
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
                                "{id:50,logicType:'LOGIC_AND',conds:[{condType:'DUNGEON_COND_FINISH_CHALLENGE',param:[999999,0,0]}]}",
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
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        scene.close();
        drain();
        maps.forEach(Runnable::run);
    }

    private WorldChallenge upper(int elapsed) {
        assertEquals(0, library.ActiveChallenge(1, 197, 600, UPPER, 20, 0));
        var challenge = scene.getChallenge();
        scene.seconds += elapsed;
        challenge.done();
        return challenge;
    }

    private void lower() throws Exception {
        replaceField(TeamManager.class, player.getTeamManager(), "useTemporarilyTeamIndex", 1);
        library.setCurrentGroup(scene.scripts.getLoadedGroupById(LOWER));
    }

    private int startLower(int limit) {
        return library.ActiveChallenge(2, 198, limit, LOWER, 2, 0);
    }

    private int eventTime() {
        return scene.scripts.events.stream()
                .filter(e -> e.type == EventType.EVENT_CHALLENGE_SUCCESS)
                .reduce((a, b) -> b)
                .orElseThrow()
                .param2;
    }

    private DungeonChallengeBeginNotify latestBegin() throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.DungeonChallengeBeginNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return DungeonChallengeBeginNotify.parseFrom(packet.getData());
    }

    private int lua(String text) throws Exception {
        var current = library.getCurrentGroup().orElseThrow();
        var bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        bindings.put(
                "context",
                library.createEventContext(
                        new ScriptArgs(current.id, EventType.EVENT_CHALLENGE_SUCCESS), current));
        return ((Number) ScriptLoader.getEngine().eval(text, bindings)).intValue();
    }

    @Test
    void timedTowerSuccessReportsRemainingSecondsToLua() {
        upper(33);
        assertEquals(567, eventTime());
    }

    @Test
    void ordinaryChallengeKeepsElapsedSuccessEventSemantics() {
        scene.getDungeonManager().setTowerDungeon(false);
        upper(33);
        assertEquals(33, eventTime());
    }

    @Test
    void correctLowerLimitIsNotSubtractedAgain() throws Exception {
        upper(33);
        lower();
        assertEquals(0, startLower(567));
        assertEquals(567, scene.getChallenge().getTimeLimit());
        assertEquals(List.of(2, 567), latestBegin().getParamListList());
    }

    @ParameterizedTest
    @ValueSource(ints = {27, 33, 0, 600, 233401004})
    void savedUpperRemainingBoundsIncorrectLuaLimits(int input) throws Exception {
        upper(33);
        lower();
        assertEquals(0, startLower(input));
        assertEquals(567, scene.getChallenge().getTimeLimit());
    }

    @Test
    void waitingBetweenHalvesDoesNotSpendChallengeSeconds() throws Exception {
        upper(33);
        scene.seconds += 120;
        lower();
        assertEquals(0, startLower(567));
        assertEquals(567, scene.getChallenge().getTimeLimit());
    }

    @Test
    void elapsedBeyondLimitCannotOpenAnUnlimitedLowerChallenge() throws Exception {
        var old = upper(601);
        lower();
        assertNotEquals(0, startLower(0));
        assertSame(old, scene.getChallenge());
        assertFalse(old.inProgress());
    }

    @Test
    void invalidChallengeDataCannotConsumeCarryOrInstallAChallenge() throws Exception {
        upper(33);
        lower();
        assertNotEquals(0, library.ActiveChallenge(2, 999999, 1, LOWER, 2, 0));
        assertEquals(0, startLower(33));
        assertEquals(567, scene.getChallenge().getTimeLimit());
    }

    @Test
    void guardChallengeGroupIdIsNeverTreatedAsSeconds() {
        upper(33);
        assertEquals(0, library.ActiveChallenge(2, 188, LOWER, 2, 4007, 0));
        assertEquals(0, scene.getChallenge().getTimeLimit());
        assertEquals(LOWER, scene.getChallenge().getGroup().id);
    }

    @Test
    void activeOrdinarySceneDoesNotUseUnrelatedTowerBookkeeping() {
        upper(33);
        scene.getDungeonManager().setTowerDungeon(false);
        assertEquals(0, library.ActiveChallenge(2, 198, 567, LOWER, 2, 0));
        assertEquals(567, scene.getChallenge().getTimeLimit());
    }

    @Test
    void carryCannotLeakIntoAReselectedParty() throws Exception {
        upper(33);
        assertTrue(
                player.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(
                                        List.of(
                                                player.getAvatars()
                                                        .getAvatarById(SECOND)
                                                        .getGuid()),
                                        List.of(
                                                player.getAvatars()
                                                        .getAvatarById(FIRST)
                                                        .getGuid()))));
        lower();
        assertEquals(0, startLower(400));
        assertEquals(400, scene.getChallenge().getTimeLimit());
    }

    @Test
    void carryCannotLeakAfterTheDungeonManagerIsReplaced() throws Exception {
        upper(33);
        lower();
        var replacement = new DungeonManager(scene, scene.getDungeonManager().getDungeonData());
        replacement.setTowerDungeon(true);
        scene.setDungeonManager(replacement);
        assertEquals(0, startLower(400));
        assertEquals(400, scene.getChallenge().getTimeLimit());
    }

    @Test
    void carryCannotLeakIntoAnotherChamber() throws Exception {
        upper(33);
        lower();
        replaceField(TowerData.class, player.getTowerData(), "currentLevel", 1);
        assertEquals(0, startLower(400));
        assertEquals(400, scene.getChallenge().getTimeLimit());
    }

    @Test
    void carryIsConsumedOnceWhenTheLowerChallengeStarts() throws Exception {
        upper(33);
        lower();
        assertEquals(0, startLower(567));
        scene.seconds += 10;
        scene.getChallenge().done();
        assertEquals(0, startLower(400));
        assertEquals(400, scene.getChallenge().getTimeLimit());
    }

    @Test
    void lowerSuccessReportsItsActualRemainingSeconds() throws Exception {
        upper(33);
        lower();
        assertEquals(0, startLower(567));
        scene.seconds += 20;
        scene.getChallenge().done();
        assertEquals(547, eventTime());
    }

    @Test
    void noPlayerMeansNoChallengeOrBeginNotification() {
        scene.getPlayers().clear();
        assertNotEquals(0, library.ActiveChallenge(1, 197, 600, UPPER, 20, 0));
        assertNull(scene.getChallenge());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void luaOneArgumentMirrorCallSelectsTheActualLowerParty() throws Exception {
        upper(33);
        assertEquals(0, lua("return ScriptLib.TowerMirrorTeamSetUp(context, 2)"));
        assertEquals(
                SECOND, player.getTeamManager().getCurrentAvatarEntity().getAvatar().getAvatarId());
    }

    @Test
    void invalidMirrorCallCannotStopTheCurrentMonsterTide() {
        assertNotEquals(0, library.TowerMirrorTeamSetUp(99, 0));
        assertEquals(0, scene.scripts.tideStops);
    }

    @Test
    void absentPlayerMirrorCallReportsFailure() {
        scene.getPlayers().clear();
        assertNotEquals(0, library.TowerMirrorTeamSetUp(2, 0));
        assertEquals(0, scene.scripts.tideStops);
    }

    @Test
    void increasingTimeChallengeUsesTheFifthArgumentWithoutChangingItsGroup() throws Exception {
        upper(33);
        lower();
        assertEquals(0, library.ActiveChallenge(2, 190, LOWER, 4, 33, 5));
        assertEquals(LOWER, scene.getChallenge().getGroup().id);
        assertEquals(567, scene.getChallenge().getTimeLimit());
        assertEquals(List.of(4, 567, 5), latestBegin().getParamListList());
    }

    @Test
    void trustedRemainingOverTenMinutesIsStillALowerHalfBudget() throws Exception {
        assertEquals(0, library.ActiveChallenge(1, 197, 900, UPPER, 20, 0));
        scene.seconds += 13;
        scene.getChallenge().done();
        lower();
        assertEquals(0, startLower(600));
        assertEquals(887, scene.getChallenge().getTimeLimit());
    }

    @Test
    void failedUpperHalfDoesNotCreateAValidatedCarry() throws Exception {
        assertEquals(0, library.ActiveChallenge(1, 197, 600, UPPER, 20, 0));
        scene.seconds += 33;
        scene.getChallenge().fail();
        lower();
        assertEquals(0, startLower(400));
        assertEquals(400, scene.getChallenge().getTimeLimit());
    }

    @Test
    void unknownGroupCannotReplaceTheCompletedUpperChallengeOrConsumeItsCarry() throws Exception {
        var previous = upper(33);
        lower();
        assertNotEquals(0, library.ActiveChallenge(2, 198, 33, 999999999, 2, 0));
        assertSame(previous, scene.getChallenge());
        assertEquals(0, startLower(33));
        assertEquals(567, scene.getChallenge().getTimeLimit());
    }

    @Test
    void actualResourceLowerActionUsesTheSuccessEventRemainingSeconds() throws Exception {
        upper(33);
        scene.scripts.getVariables(STATE).put("TPL_TIME", eventTime());
        lower();
        var source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "resources/Scripts/Scene/33401/scene33401_group233401004.lua"));
        int begin = source.indexOf("function action_EVENT_ANY_MONSTER_LIVE_4008");
        int end = source.indexOf("function action_EVENT_CHALLENGE_SUCCESS_4009", begin);
        assertTrue(begin >= 0 && end > begin);
        assertEquals(
                0,
                lua(
                        source.substring(begin, end)
                                + "\nreturn action_EVENT_ANY_MONSTER_LIVE_4008(context, {})"));
        assertEquals(567, scene.getChallenge().getTimeLimit());
        assertEquals(List.of(2, 567), latestBegin().getParamListList());
    }
}
