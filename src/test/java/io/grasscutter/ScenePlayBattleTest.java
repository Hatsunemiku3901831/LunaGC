package io.grasscutter;

import static emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionServerLuaCall;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePlayBattleInfoListNotifyOuterClass.ScenePlayBattleInfoListNotify;
import emu.grasscutter.net.proto.ScenePlayBattleResultNotifyOuterClass.ScenePlayBattleResultNotify;
import emu.grasscutter.net.proto.ScenePlayBattleUidOpNotifyOuterClass.ScenePlayBattleUidOpNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

import javax.script.SimpleBindings;

class ScenePlayBattleTest {
    private static final int MAIN = 133001413;
    private static final List<Long> STAGES = List.of(0L, 1500L, 3000L, 4500L, 6500L);
    private AbilityTestFixture environment;
    private BattleScene scene;
    private AbilityTestFixture.TestPlayer host, guest;
    private ScenePlayBattleManager battles;
    private ScriptLib library;
    private SimpleBindings bindings;
    private MpPlayGroupData entry;
    private MpPlayMatchData match;

    @BeforeAll
    static void luaEngine() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        host = environment.player;
        scene =
                new BattleScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), host));
        guest = new AbilityTestFixture.TestPlayer();
        ready(host, 10001);
        ready(guest, 10002);
        battles = scene.getScenePlayBattleManager();
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scene.scripts);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        entry = resource("MpPlayGroupExcelConfigData.json", "playId", MpPlayGroupData.class);
        match = resource("MpPlayMatchExcelConfigData.json", "id", MpPlayMatchData.class);
        for (int id : List.of(MAIN, 133001426, 133001427, 133001498)) {
            var group = new SceneGroup();
            group.id = id;
            scene.scripts.loaded.put(id, group);
        }
        scene.packets.clear();
    }

    private static <T> T resource(String file, String idKey, Class<T> type) throws Exception {
        var array =
                JsonParser.parseString(Files.readString(Path.of("resources/ExcelBinOutput", file)))
                        .getAsJsonArray();
        for (var raw : array)
            if (raw.getAsJsonObject().get(idKey).getAsInt() == 2)
                return new Gson().fromJson(raw, type);
        throw new AssertionError("Missing Aster multiplayer resource " + file);
    }

    private void ready(AbilityTestFixture.TestPlayer player, int uid) {
        player.setUid(uid);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
    }

    @AfterEach
    void cleanup() {
        library.removeCurrentEntity();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        scene.scripts.onDestroy();
        scene.scripts.drain();
        environment.close();
    }

    private double lua(String source) throws Exception {
        return ((Number) ScriptLoader.getEngine().eval(source, bindings)).doubleValue();
    }

    private boolean prepare() {
        return battles.prepare(entry, match, 2, MATCH, List.of(host, guest));
    }

    private void at(long seconds) {
        scene.now.set(seconds);
        battles.tick();
    }

    private void readyBattle() {
        assertTrue(prepare());
        at(1020);
        scene.scripts.drain();
        assertEquals(READY, battles.getInfo(MAIN).getState());
    }

    private void startBattle() throws Exception {
        readyBattle();
        assertEquals(
                0,
                lua(
                        "return"
                            + " ScriptLib.PrestartScenePlayBattle(ScriptLib,{group_id=133001413,duration=600,start_cd=10,progress_stage={0,1500,3000,4500,6500},mode=203})"));
        at(1030);
        scene.scripts.drain();
        assertEquals(START, battles.getInfo(MAIN).getState());
        scene.scripts.ran.clear();
        scene.packets.clear();
    }

    private List<ScriptArgs> events(int type, int group) {
        return scene.scripts.ran.stream()
                .filter(e -> e.type == type && e.group_id == group)
                .toList();
    }

    private List<BasePacket> packets(int opcode) {
        return scene.packets.stream().filter(p -> p.getOpcode() == opcode).toList();
    }

    @Test
    void prepareUsesActualAsterResourceGroupsAndTwentySecondCountdown() throws Exception {
        assertTrue(prepare());
        assertEquals(List.of(MAIN), entry.getServerLuaCallGroupList());
        assertEquals(2, battles.getInfo(MAIN).getPlayId());
        assertEquals(PREPARE, battles.getInfo(133001426).getState());
        assertEquals(1020, battles.getInfo(MAIN).getPrepareEndTime());
        at(1019);
        assertEquals(PREPARE, battles.getInfo(MAIN).getState());
        at(1020);
        assertEquals(READY, battles.getInfo(MAIN).getState());
        scene.scripts.drain();
        var states = events(EventType.EVENT_SCENE_MP_PLAY_BATTLE_STATE, MAIN);
        assertEquals(List.of(PREPARE, READY), states.stream().map(e -> e.param3).toList());
        assertTrue(states.stream().allMatch(e -> e.param1 == 2 && e.param2 == 2 && e.uid == 10001));
        assertEquals(8, scene.scripts.ran.size());
        assertEquals(0, battles.getInfo(MAIN).getStartTime());
    }

    @Test
    void actualLuaReadyActionPrestartsAndSceneTickStartsAtTheBoundary() throws Exception {
        var action =
                (LuaValue)
                        ScriptLoader.getEngine()
                                .eval(
                                        "return function(context,evt) if evt.param3==3 and"
                                            + " context.group_id==133001413 then"
                                            + " assert(context.uid==10001)"
                                            + " assert(ScriptLib.PrestartScenePlayBattle(context,{group_id=0,duration=600,start_cd=10,progress_stage={0,1500,3000,4500,6500},mode=201})==0)"
                                            + " end return 0 end",
                                        bindings);
        scene.scripts.dispatched =
                args -> {
                    var group = scene.scripts.loaded.get(args.group_id);
                    library.setCurrentGroup(group);
                    try {
                        assertEquals(
                                0,
                                scene.scripts
                                        .safetyCall(
                                                "ready",
                                                action,
                                                CoerceJavaToLua.coerce(args),
                                                group)
                                        .toint());
                    } finally {
                        library.removeCurrentGroup();
                    }
                };
        assertTrue(prepare());
        at(1020);
        scene.scripts.drain();
        assertEquals(PRESTART, battles.getInfo(MAIN).getState());
        assertEquals(1030, battles.getInfo(MAIN).getStartTime());
        assertEquals(201, battles.getInfo(MAIN).getMode());
        at(1029);
        assertEquals(PRESTART, battles.getInfo(MAIN).getState());
        scene.now.set(1030);
        scene.onTick();
        scene.scripts.drain();
        assertEquals(START, battles.getInfo(MAIN).getState());
        assertEquals(
                List.of(PREPARE, READY, PRESTART, START),
                events(49, MAIN).stream().map(e -> e.param3).toList());
    }

    @Test
    void progressStagesMatchOriginalEventArgumentsIncludingSkippedStages() throws Exception {
        startBattle();
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 1499));
        scene.scripts.drain();
        assertTrue(events(50, MAIN).isEmpty());
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 1));
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 3000));
        scene.scripts.drain();
        var changes = events(50, MAIN);
        assertEquals(2, changes.size());
        assertEquals(0, changes.get(0).param1);
        assertEquals(1, changes.get(0).param2);
        assertEquals(1, changes.get(1).param1);
        assertEquals(3, changes.get(1).param2);
        assertTrue(changes.stream().allMatch(e -> e.param3 == 4));
        assertEquals(4500, battles.getInfo(MAIN).getProgress());
    }

    @Test
    void victoryIsSettledOnceWithReasonInParamThreeAndNoInventedRewards() throws Exception {
        startBattle();
        at(1055);
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 6500));
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 1));
        at(2000);
        scene.scripts.drain();
        assertEquals(STOP, battles.getInfo(MAIN).getState());
        var result = ScenePlayBattleResultNotify.parseFrom(packets(8678).get(0).getData());
        assertTrue(result.getIsWin());
        assertEquals(25, result.getCostTime());
        assertEquals(
                List.of(10001, 10002),
                result.getSettlePlayerInfoListList().stream().map(p -> p.getUid()).toList());
        assertEquals(0, result.getSettleRewardInfoListCount());
        assertEquals(1, packets(8678).size());
        assertEquals(1, events(51, MAIN).size());
        assertEquals(2, events(51, MAIN).get(0).param1);
        assertEquals(2, events(51, MAIN).get(0).param2);
        assertEquals(WIN, events(51, MAIN).get(0).param3);
        assertTrue(events(49, MAIN).isEmpty()); // AsterBig does not handle a STOP state event.
    }

    @Test
    void timeoutWinsTheBoundaryRaceAgainstEnergySubmission() throws Exception {
        startBattle();
        at(1629);
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 6000));
        scene.now.set(1630);
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 500));
        scene.scripts.drain();
        assertEquals(6000, battles.getInfo(MAIN).getProgress());
        var result = ScenePlayBattleResultNotify.parseFrom(packets(8678).get(0).getData());
        assertFalse(result.getIsWin());
        assertEquals(600, result.getCostTime());
        assertEquals(LOSE, events(51, MAIN).get(0).param3);
    }

    @Test
    void replayWaitsForTerminalCallbacksAndStartsWithFreshUidState() throws Exception {
        startBattle();
        assertEquals(0, library.SetScenePlayBattleUidValue(MAIN, 10002, "progress", 6500));
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 6500));
        assertFalse(prepare()); // Old Lua cleanup is still queued.
        scene.scripts.drain();
        assertTrue(prepare());
        assertEquals(0, library.GetScenePlayBattleUidValue(MAIN, 10002, "progress"));
        assertEquals(0, battles.getInfo(MAIN).getProgress());
        assertEquals(0, battles.getInfo(MAIN).getStartTime());
        assertEquals(0, battles.getInfo(MAIN).getDuration());
        assertEquals(0, battles.getInfo(MAIN).getProgressStageListCount());
        assertEquals(PREPARE, battles.getInfo(MAIN).getState());
    }

    @Test
    void preparationInterruptClearsWithoutInventingAResult() throws Exception {
        assertTrue(prepare());
        scene.removePlayer(guest);
        scene.scripts.drain();
        assertEquals(NONE, battles.getInfo(MAIN).getState());
        assertEquals(PREPARE, events(54, MAIN).get(0).param3);
        assertEquals(1, packets(20684).size());
        assertTrue(packets(8678).isEmpty());
    }

    @Test
    void aQueuedGroupCallbackKeepsItsOriginalRoundUntilItFinishes() throws Exception {
        startBattle();
        library.AddScenePlayBattleProgress(MAIN, 6500);
        scene.scripts.drain();
        var saved = new java.util.concurrent.atomic.AtomicReference<BooleanSupplier>();
        var future = new FutureTask<Void>(() -> {}, null);
        battles.submitCallback(
                MAIN,
                current -> {
                    saved.set(current);
                    return future;
                });
        assertTrue(saved.get().getAsBoolean());
        assertFalse(prepare());
        future.run();
        assertTrue(prepare());
        assertFalse(saved.get().getAsBoolean());
    }

    @Test
    void actualSceneEventExecutorPreventsReplayWhileAnOldGroupCallbackIsRunning() throws Exception {
        startBattle();
        library.AddScenePlayBattleProgress(MAIN, 6500);
        scene.scripts.drain();
        var scripts = new SceneScriptManager(scene);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var future =
                    scripts.callEvent(
                            new ScriptArgs(MAIN, EventType.EVENT_TIMER_EVENT),
                            () -> {
                                entered.countDown();
                                try {
                                    return release.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    return false;
                                }
                            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(prepare());
            release.countDown();
            future.get(5, TimeUnit.SECONDS);
            assertTrue(prepare());
        } finally {
            release.countDown();
            scripts.onDestroy();
        }
    }

    @Test
    void hostDepartureFromRunningBattleSendsOneInterruptAndLoss() throws Exception {
        startBattle();
        scene.removePlayer(host);
        scene.scripts.drain();
        battles.tick();
        assertEquals(STOP, battles.getInfo(MAIN).getState());
        assertEquals(START, events(54, MAIN).get(0).param3);
        assertEquals(INTERRUPT, events(51, MAIN).get(0).param3);
        assertEquals(1, packets(20684).size());
        assertEquals(1, packets(8678).size());
        assertFalse(
                ScenePlayBattleResultNotify.parseFrom(packets(8678).get(0).getData()).getIsWin());
    }

    @Test
    void aGuestCanLeaveWhileTheResourceMinimumIsStillMet() throws Exception {
        var third = new AbilityTestFixture.TestPlayer();
        ready(third, 10003);
        assertTrue(battles.prepare(entry, match, 2, MATCH, List.of(host, guest, third)));
        scene.removePlayer(third);
        at(1020);
        assertEquals(READY, battles.getInfo(MAIN).getState());
        assertTrue(packets(20684).isEmpty());
        assertEquals(-1, library.SetScenePlayBattleUidValue(MAIN, 10003, "progress", 1));
    }

    @Test
    void directBattleSupportsTheHostOnly() throws Exception {
        assertTrue(battles.prepare(entry, match, 2, DIRECT, List.of(host)));
        assertEquals(DIRECT, library.GetScenePlayBattleType(MAIN));
        assertEquals(10001, library.GetScenePlayBattleHostUid(MAIN));
    }

    @Test
    void playerReplacementWithTheSameUidCannotAdoptTheOldRound() throws Exception {
        startBattle();
        scene.getPlayers().remove(guest);
        guest.setScene(null);
        var replacement = new AbilityTestFixture.TestPlayer();
        ready(replacement, 10002);
        assertEquals(-1, library.SetScenePlayBattleUidValue(MAIN, 10002, "progress", 1));
        battles.tick();
        assertEquals(STOP, battles.getInfo(MAIN).getState());
    }

    @Test
    void unloadingAGroupSuppressesItsQueuedRoundCallbacks() throws Exception {
        assertTrue(prepare());
        var group = scene.scripts.loaded.get(MAIN);
        scene.scripts.unregisterGroup(group);
        scene.scripts.loaded.remove(MAIN);
        scene.scripts.drain();
        assertTrue(scene.scripts.ran.isEmpty());
        assertEquals(0, library.GetScenePlayBattleHostUid(MAIN));
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 1));
    }

    @Test
    void destroyingTheSceneInvalidatesQueuedReadyCallbacks() throws Exception {
        assertTrue(prepare());
        at(1020);
        scene.scripts.onDestroy();
        scene.scripts.drain();
        assertTrue(scene.scripts.ran.isEmpty());
        assertFalse(prepare());
        assertEquals(-1, library.PrestartScenePlayBattle(new LuaTable()));
    }

    @Test
    void joiningPlayerGetsOnlyTheCurrentActiveBattleAndEmptyListAfterSettlement() throws Exception {
        startBattle();
        guest.packets.clear();
        scene.playerSceneInitialized(guest);
        var list = ScenePlayBattleInfoListNotify.parseFrom(guest.packets.get(0).getData());
        assertEquals(20153, guest.packets.get(0).getOpcode());
        assertEquals(1, list.getBattleInfoListCount());
        assertEquals(START, list.getBattleInfoList(0).getState());
        library.AddScenePlayBattleProgress(MAIN, 6500);
        guest.packets.clear();
        scene.playerSceneInitialized(guest);
        assertEquals(
                0,
                ScenePlayBattleInfoListNotify.parseFrom(guest.packets.get(0).getData())
                        .getBattleInfoListCount());
    }

    @Test
    void luaUsesGroupZeroFromCurrentContextAndRejectsStaleGroupObjects() throws Exception {
        assertTrue(prepare());
        library.setCurrentGroup(scene.scripts.loaded.get(MAIN));
        assertEquals(
                0,
                lua(
                        "return"
                            + " ScriptLib.SetScenePlayBattleUidValue(ScriptLib,0,10002,'difficulty',2)"));
        assertEquals(
                2,
                lua("return ScriptLib.GetScenePlayBattleUidValue(ScriptLib,0,10002,'difficulty')"));
        assertEquals(10001, lua("return ScriptLib.GetScenePlayBattleHostUid(ScriptLib,0)"));
        scene.scripts.loaded.put(MAIN, new SceneGroup());
        assertEquals(-1, library.SetScenePlayBattleUidValue(MAIN, 10002, "difficulty", 3));
        assertEquals(0, library.GetScenePlayBattleUidValue(MAIN, 10002, "difficulty"));
    }

    @Test
    void perPlayerStatisticsRetainSignedInt64ValuesAndAreNotShared() throws Exception {
        assertTrue(prepare());
        assertEquals(0, library.SetScenePlayBattleUidValue(MAIN, 10001, "large", Long.MAX_VALUE));
        assertEquals(0, library.SetScenePlayBattleUidValue(MAIN, 10002, "large", Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, library.GetScenePlayBattleUidValue(MAIN, 10001, "large"));
        assertEquals(Long.MIN_VALUE, library.GetScenePlayBattleUidValue(MAIN, 10002, "large"));
        assertThrows(
                Exception.class,
                () ->
                        lua(
                                "return"
                                    + " ScriptLib.GetScenePlayBattleUidValue(ScriptLib,133001413,10001,'large')"));
        assertEquals(0, library.GetScenePlayBattleUidValue(MAIN, 10003, "large"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.5",
                "'2'",
                "true",
                "0/0",
                "math.huge",
                "9007199254740992",
                "-9007199254740992"
            })
    void luaRejectsStatisticsThatWouldBeSilentlyRounded(String value) throws Exception {
        assertTrue(prepare());
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.SetScenePlayBattleUidValue(ScriptLib,133001413,10002,'progress',"
                                + value
                                + ")"));
        assertEquals(0, library.GetScenePlayBattleUidValue(MAIN, 10002, "progress"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{0,1500,1500}",
                "{0,100,-1}",
                "{0,1.5,10}",
                "{0,'1500',6500}",
                "{[1]=0,[3]=6500}",
                "{0,6500,x=1}",
                "{}",
                "{0}"
            })
    void malformedThresholdsCannotLeaveReadyState(String stages) throws Exception {
        readyBattle();
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.PrestartScenePlayBattle(ScriptLib,{group_id=133001413,duration=600,start_cd=10,progress_stage="
                                + stages
                                + "})"));
        assertEquals(READY, battles.getInfo(MAIN).getState());
    }

    @Test
    void progressIsRejectedBeforeStartAndInvalidDeltasDoNotChangeTheRound() throws Exception {
        assertTrue(prepare());
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 1));
        at(1020);
        scene.scripts.drain();
        assertTrue(battles.prestart(MAIN, new ScenePlayBattleParameters(600, 10, 0, STAGES)));
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 1));
        at(1030);
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, -1));
        assertEquals(-1, library.AddScenePlayBattleProgress(MAIN, 0x1_0000_0000L));
        assertEquals(
                -1, lua("return ScriptLib.AddScenePlayBattleProgress(ScriptLib,133001413,0.75)"));
        assertEquals(0, battles.getInfo(MAIN).getProgress());
    }

    @Test
    void unsignedProgressSaturatesAtTheProtocolMaximum() throws Exception {
        readyBattle();
        assertTrue(
                battles.prestart(
                        MAIN,
                        new ScenePlayBattleParameters(
                                600, 0, 0, List.of(0L, 0xffff_fffeL, 0xffff_ffffL))));
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 0xffff_fffeL));
        assertEquals(0, library.AddScenePlayBattleProgress(MAIN, 6500));
        assertEquals(0xffff_ffffL, Integer.toUnsignedLong(battles.getInfo(MAIN).getProgress()));
        assertEquals(STOP, battles.getInfo(MAIN).getState());
    }

    @Test
    void invalidPreparationHasNoPacketsOrPartialRegistration() throws Exception {
        assertFalse(battles.prepare(entry, match, 2, MATCH, List.of(host)));
        assertFalse(battles.prepare(entry, match, 2, MATCH, List.of(host, guest, guest)));
        guest.setWorld(environment.scene.getWorld());
        assertFalse(prepare());
        assertTrue(scene.packets.isEmpty());
        assertEquals(0, battles.getInfo(MAIN).getPlayId());
    }

    @Test
    void countdownRejectsOverflowAndDoesNotRewindAfterClockMovesBackwards() throws Exception {
        scene.now.set(0xffff_fff8L);
        assertFalse(prepare());
        scene.now.set(1000);
        assertTrue(prepare());
        at(1020);
        assertFalse(
                battles.prestart(MAIN, new ScenePlayBattleParameters(0xffff_ffffL, 10, 0, STAGES)));
        at(1000);
        assertEquals(READY, battles.getInfo(MAIN).getState());
        assertTrue(battles.prestart(MAIN, new ScenePlayBattleParameters(600, 10, 0, STAGES)));
        assertEquals(1030, battles.getInfo(MAIN).getStartTime());
    }

    @Test
    void uidOperationUsesRealGadgetAndRejectsMissingOrUnqualifiedTargets() throws Exception {
        assertTrue(prepare());
        var gadget = new EntityGadget(scene, 70310006, new Position());
        gadget.setGroupId(MAIN);
        gadget.setConfigId(413002);
        scene.getEntities().put(gadget.getId(), gadget);
        assertEquals(
                0,
                lua(
                        "return"
                            + " ScriptLib.ScenePlayBattleUidOp(ScriptLib,133001413,413002,{10002,10002},6,'random_buff_aster',{17,0},{42},9,30)"));
        var message = ScenePlayBattleUidOpNotify.parseFrom(packets(6526).get(0).getData());
        assertEquals(gadget.getId(), message.getEntityId());
        assertEquals(List.of(10002), message.getUidListList());
        assertEquals(List.of(17, 0), message.getParamListList());
        assertEquals(List.of(42), message.getParamTargetListList());
        assertEquals(9, message.getParamIndex());
        assertEquals(30, message.getParamDuration());
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.ScenePlayBattleUidOp(ScriptLib,133001413,413002,{10003},6,'random_buff_aster',{},{},6,0)"));
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.ScenePlayBattleUidOp(ScriptLib,133001413,413002,{10002.5},6,'random_buff_aster',{},{},6,0)"));
        scene.getEntities().remove(gadget.getId());
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.ScenePlayBattleUidOp(ScriptLib,133001413,413002,{10002},6,'random_buff_aster',{},{},6,0)"));
        assertEquals(1, packets(6526).size());
    }

    private Ability luaAbility(AbilityTestFixture.TestPlayer player) {
        var entity = new AbilityTestFixture.TestEntity(scene);
        entity.setId(20000 + player.getUid());
        entity.setGroupId(999);
        scene.getEntities().put(entity.getId(), entity);
        return new Ability(
                JsonUtils.decode("{\"abilityName\":\"Scene_Play_Lua_Test\"}", AbilityData.class),
                entity,
                player);
    }

    private AbilityModifierAction sceneLuaAction(int parameters) {
        return JsonUtils.decode(
                "{\"$type\":\"ServerLuaCall\",\"luaCallType\":\"CurScenePlay\","
                        + "\"funcName\":\"callable\",\"paramNum\":"
                        + parameters
                        + ",\"param1\":11.5,\"param2\":-2.25,\"param3\":3}",
                AbilityModifierAction.class);
    }

    private boolean sceneLua(Ability ability, AbilityModifierAction action) {
        return new ActionServerLuaCall()
                .execute(
                        ability,
                        action,
                        ByteString.EMPTY,
                        ability.getPlayerOwner().getTeamManager().getEntity());
    }

    private SimpleBindings battleBindings(int groupId) {
        var result = new SimpleBindings();
        result.put("ScriptLib", ScriptLoader.getScriptLibLua());
        scene.scripts.loaded.get(groupId).setBindings(result);
        return result;
    }

    private void serverLuaGroups(int... groups) {
        var raw = new Gson().toJsonTree(entry).getAsJsonObject();
        var ids = new JsonArray();
        for (int id : groups) ids.add(id);
        raw.add("serverLuaCallGroupList", ids);
        entry = new Gson().fromJson(raw, MpPlayGroupData.class);
    }

    @Test
    void scenePlayLuaUsesTheActualResourceControlGroupAndGuestContext() throws Exception {
        assertTrue(prepare());
        var a = luaAbility(guest);
        var b = battleBindings(MAIN);
        ScriptLoader.getEngine()
                .eval(
                        "function callable(context,p1) recorded_uid=context.uid;"
                            + " recorded_source=context.source_entity_id;"
                            + " recorded_target=context.target_entity_id;"
                            + " recorded_group=context.group_id; return"
                            + " ScriptLib.SetScenePlayBattleUidValue(context,0,context.uid,'slc',p1)"
                            + " end",
                        b);
        var action = sceneLuaAction(1);
        action.param1 = new DynamicFloat(25);
        assertTrue(sceneLua(a, action));
        assertEquals(10002, ((Number) b.get("recorded_uid")).intValue());
        assertEquals(MAIN, ((Number) b.get("recorded_group")).intValue());
        assertEquals(a.getOwner().getId(), ((Number) b.get("recorded_source")).intValue());
        assertEquals(
                guest.getTeamManager().getEntity().getId(),
                ((Number) b.get("recorded_target")).intValue());
        assertEquals(25, battles.getUidValue(MAIN, 10002, "slc"));
        assertEquals(0, battles.getUidValue(MAIN, 10001, "slc"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void scenePlayLuaRetainsTheConfiguredNumberAndValuesOfParameters(int count) {
        assertTrue(prepare());
        var b = battleBindings(MAIN);
        b.put(
                "callable",
                new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        assertEquals(count + 1, args.narg());
                        if (count > 0) assertEquals(11.5, args.arg(2).todouble());
                        if (count > 1) assertEquals(-2.25, args.arg(3).todouble());
                        if (count > 2) assertEquals(3, args.arg(4).todouble());
                        return LuaValue.ZERO;
                    }
                });
        assertTrue(sceneLua(luaAbility(host), sceneLuaAction(count)));
    }

    @Test
    void actualAsterAbilityActionReachesUpdateAsterProgress() throws Exception {
        var root =
                JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/ChallengeAbilities/ConfigAbility_Challenge_Meteorite.json")));
        var raw = findScenePlayAction(root);
        assertNotNull(raw);
        assertEquals("Team", raw.get("target").getAsString());
        assertEquals("UpdateAsterProgress", raw.get("funcName").getAsString());
        var action = JsonUtils.decode(raw.toString(), AbilityModifierAction.class);
        var b = battleBindings(MAIN);
        ScriptLoader.getEngine()
                .eval(
                        "function UpdateAsterProgress(context) "
                                + "recorded_uid=context.uid; return 0 end",
                        b);
        assertTrue(prepare());
        assertTrue(sceneLua(luaAbility(guest), action));
        assertEquals(10002, ((Number) b.get("recorded_uid")).intValue());
    }

    private static JsonObject findScenePlayAction(JsonElement node) {
        if (node.isJsonObject()) {
            var object = node.getAsJsonObject();
            if (object.has("luaCallType")
                    && "CurScenePlay".equals(object.get("luaCallType").getAsString()))
                return object;
            for (var child : object.entrySet()) {
                var found = findScenePlayAction(child.getValue());
                if (found != null) return found;
            }
        } else if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) {
                var found = findScenePlayAction(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void controlGroupsAreDeduplicatedSortedAndDoNotUseTheOrdinaryGroupList() {
        serverLuaGroups(133001427, MAIN, 133001427);
        var called = new ArrayList<Integer>();
        for (int group : List.of(MAIN, 133001426, 133001427, 133001498)) {
            var b = battleBindings(group);
            b.put(
                    "callable",
                    new org.luaj.vm2.lib.VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            called.add(group);
                            return LuaValue.ZERO;
                        }
                    });
        }
        assertTrue(prepare());
        assertTrue(sceneLua(luaAbility(host), sceneLuaAction(0)));
        assertEquals(List.of(MAIN, 133001427), called);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1", "nil", "'0'", "0/0", "error('fixture failure')"})
    void scenePlayLuaFailuresRestoreContextAndStillCallTheRemainingControlGroup(String status)
            throws Exception {
        serverLuaGroups(MAIN, 133001427);
        var first = battleBindings(MAIN);
        var second = battleBindings(133001427);
        ScriptLoader.getEngine()
                .eval("function callable(context) return " + status + " end", first);
        ScriptLoader.getEngine()
                .eval("function callable(context) reached=true; return 0 end", second);
        assertTrue(prepare());
        var library = ScriptLoader.getScriptLib();
        var outer = new SceneGroup();
        outer.id = 999;
        var outerEntity = environment.entity;
        library.setCurrentGroup(outer);
        library.setCurrentEntity(outerEntity);
        library.setSceneScriptManager(environment.scene.getScriptManager());
        assertFalse(sceneLua(luaAbility(host), sceneLuaAction(0)));
        assertTrue(((LuaValue) second.get("reached")).toboolean());
        assertSame(outer, library.getCurrentGroup().orElseThrow());
        assertSame(outerEntity, library.getCurrentEntity().orElseThrow());
        assertSame(environment.scene.getScriptManager(), library.getSceneScriptManagerOrNull());
    }

    @Test
    void emptyMissingAndReplacedControlGroupsDoNotFallBackToEntityOrOtherGroups() {
        var a = luaAbility(host);
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        serverLuaGroups();
        assertTrue(prepare());
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        battles.close();
    }

    @Test
    void aReplacedControlGroupAndAReplacedPlayerCannotInheritTheRound() {
        assertTrue(prepare());
        var a = luaAbility(guest);
        var b = battleBindings(MAIN);
        b.put(
                "callable",
                new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        fail("stale request");
                        return LuaValue.ZERO;
                    }
                });
        var original = scene.scripts.loaded.get(MAIN);
        var replacement = new SceneGroup();
        replacement.id = MAIN;
        replacement.setBindings(b);
        scene.scripts.loaded.put(MAIN, replacement);
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        scene.scripts.loaded.put(MAIN, original);
        var other = new AbilityTestFixture.TestPlayer();
        ready(other, guest.getUid());
        scene.getPlayers().remove(guest);
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        assertFalse(sceneLua(luaAbility(other), sceneLuaAction(0)));
    }

    @Test
    void aLateFunctionDefinitionRepairsAMissAndAnUnlistedPlayerCannotCallIt() throws Exception {
        assertTrue(prepare());
        var a = luaAbility(host);
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        var b = battleBindings(MAIN);
        ScriptLoader.getEngine().eval("function callable(context) return 0 end", b);
        assertTrue(sceneLua(a, sceneLuaAction(0)));
        var outsider = new AbilityTestFixture.TestPlayer();
        ready(outsider, 10003);
        assertFalse(sceneLua(luaAbility(outsider), sceneLuaAction(0)));
    }

    @Test
    void unregisteredReplacedInvalidatedAndForeignEntitiesAreRejected() {
        assertTrue(prepare());
        var a = luaAbility(host);
        var id = a.getOwner().getId();
        scene.getEntities().remove(id);
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        scene.getEntities().put(id, new AbilityTestFixture.TestEntity(scene));
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        scene.getEntities().put(id, a.getOwner());
        a.getOwner().invalidateAbilityLifecycle();
        assertFalse(sceneLua(a, sceneLuaAction(0)));
        var foreign =
                new Ability(
                        JsonUtils.decode("{\"abilityName\":\"Foreign\"}", AbilityData.class),
                        environment.entity,
                        host);
        assertFalse(sceneLua(foreign, sceneLuaAction(0)));
    }

    @Test
    void lateProgressCallsDoNotRunAfterTheDeadline() throws Exception {
        startBattle();
        var b = battleBindings(MAIN);
        b.put(
                "callable",
                new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        fail("expired battle");
                        return LuaValue.ZERO;
                    }
                });
        scene.now.set(1630);
        assertFalse(sceneLua(luaAbility(host), sceneLuaAction(0)));
        assertEquals(STOP, battles.getInfo(MAIN).getState());
    }

    @Test
    void oneCallCannotFinishAndReplaceItsOwnRoundEvenAfterEventsDrain() throws Exception {
        startBattle();
        var b = battleBindings(MAIN);
        b.put(
                "callable",
                new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        assertTrue(battles.addProgress(MAIN, 6500));
                        scene.scripts.drain();
                        assertFalse(prepare());
                        return LuaValue.ZERO;
                    }
                });
        assertTrue(sceneLua(luaAbility(host), sceneLuaAction(0)));
        assertTrue(prepare());
    }

    @Test
    void replacingTheSourceDuringTheFirstCallSkipsTheRemainingGroup() {
        serverLuaGroups(MAIN, 133001427);
        assertTrue(prepare());
        var a = luaAbility(host);
        battleBindings(MAIN)
                .put(
                        "callable",
                        new org.luaj.vm2.lib.VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                a.getOwner().invalidateAbilityLifecycle();
                                return LuaValue.ZERO;
                            }
                        });
        battleBindings(133001427)
                .put(
                        "callable",
                        new org.luaj.vm2.lib.VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                fail("replaced source");
                                return LuaValue.ZERO;
                            }
                        });
        assertFalse(sceneLua(a, sceneLuaAction(0)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queuedScenePlayActionsCannotAdoptARoundThatStartsAfterSubmission(boolean previouslyActive)
            throws Exception {
        if (previouslyActive) startBattle();
        var a = luaAbility(host);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        battleBindings(MAIN)
                .put(
                        "callable",
                        new org.luaj.vm2.lib.VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                calls.incrementAndGet();
                                return LuaValue.ZERO;
                            }
                        });
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var held = new ArrayList<Future<?>>();
        try {
            for (int n = 0; n < 4; n++)
                held.add(
                        AbilityManager.eventExecutor.submit(
                                () -> {
                                    entered.countDown();
                                    try {
                                        assertTrue(release.await(10, TimeUnit.SECONDS));
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            host.getAbilityManager()
                    .executeAction(
                            a,
                            sceneLuaAction(0),
                            ByteString.EMPTY,
                            host.getTeamManager().getEntity());
            if (previouslyActive) {
                assertTrue(battles.addProgress(MAIN, 6500));
                scene.scripts.drain();
            }
            assertTrue(prepare());
        } finally {
            release.countDown();
        }
        for (var task : held) task.get(5, TimeUnit.SECONDS);
        var pool = (ThreadPoolExecutor) AbilityManager.eventExecutor;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((!pool.getQueue().isEmpty() || pool.getActiveCount() != 0)
                && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(pool.getQueue().isEmpty());
        assertEquals(0, pool.getActiveCount());
        assertEquals(0, calls.get());
        assertEquals(PREPARE, battles.getInfo(MAIN).getState());
    }

    @Test
    void ordinaryTeamFollowsItsOwnerSceneAndAReplacedTeamIsRejected() {
        var team = host.getTeamManager().getEntity();
        assertSame(scene, team.getScene());
        host.setScene(environment.scene);
        assertSame(environment.scene, team.getScene());
        host.setScene(scene);
        assertTrue(prepare());
        var a = luaAbility(host);
        host.getTeamManager().setEntity(new EntityTeam(host));
        assertFalse(
                new ActionServerLuaCall().execute(a, sceneLuaAction(0), ByteString.EMPTY, team));
    }

    @Test
    void aCurrentScenePlayActionRunsThroughTheRealAbilityExecutor() throws Exception {
        assertTrue(prepare());
        var reached = new CountDownLatch(1);
        battleBindings(MAIN)
                .put(
                        "callable",
                        new org.luaj.vm2.lib.VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                reached.countDown();
                                return LuaValue.ZERO;
                            }
                        });
        var a = luaAbility(guest);
        guest.getAbilityManager()
                .executeAction(
                        a, sceneLuaAction(0), ByteString.EMPTY, guest.getTeamManager().getEntity());
        assertTrue(reached.await(5, TimeUnit.SECONDS));
        // Wait for the callback to release the scene monitor before fixture teardown.
        synchronized (scene) {
            assertEquals(PREPARE, battles.getInfo(MAIN).getState());
        }
    }

    @Test
    void deferredAbilityStartKeepsItsOriginalBattleScope() throws Exception {
        startBattle();
        var owner = luaAbility(host).getOwner();
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"Deferred_Scene_Play\",\"onAbilityStart\":["
                                + "{\"$type\":\"ServerLuaCall\",\"luaCallType\":\"CurScenePlay\","
                                + "\"funcName\":\"callable\",\"target\":\"Team\"}]}",
                        AbilityData.class);
        var a = new Ability(data, owner, host);
        var begin = Ability.class.getDeclaredMethod("prepareTriggerInitialization");
        begin.setAccessible(true);
        begin.invoke(a);
        owner.getInstancedAbilities().add(a);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        battleBindings(MAIN)
                .put(
                        "callable",
                        new org.luaj.vm2.lib.VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                calls.incrementAndGet();
                                return LuaValue.ZERO;
                            }
                        });
        assertTrue(host.getAbilityManager().triggerAbility(owner, data.abilityName));
        assertEquals(0, calls.get());
        assertTrue(battles.addProgress(MAIN, 6500));
        scene.scripts.drain();
        assertTrue(prepare());
        assertTrue(a.getTriggerInitialization().complete(null));
        assertEquals(0, calls.get());
    }

    @Test
    void anAbilityScopeRejectsUidChangesDepartureAndSceneShutdown() {
        assertTrue(prepare());
        var scope = battles.captureAbilityScope(guest);
        assertTrue(scope.getAsBoolean());
        guest.setUid(10003);
        assertFalse(scope.getAsBoolean());
        guest.setUid(10002);
        scene.getPlayers().remove(guest);
        assertFalse(scope.getAsBoolean());
        scene.getPlayers().add(guest);
        assertTrue(scope.getAsBoolean());
        battles.close();
        assertFalse(scope.getAsBoolean());
    }

    private static final class BattleScene extends AbilityTestFixture.TestScene {
        final AtomicLong now = new AtomicLong(1000);
        final QueuedScripts scripts;

        BattleScene(AbilityTestFixture.TestWorld world) throws Exception {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_DUNGEON\"}", SceneData.class));
            scripts = new QueuedScripts(this);
            var field = Scene.class.getDeclaredField("scenePlayBattleManager");
            field.setAccessible(true);
            field.set(this, new ScenePlayBattleManager(this, now::get));
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class QueuedScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new LinkedHashMap<>();
        final List<FutureTask<Void>> queued = new ArrayList<>();
        final List<ScriptArgs> ran = new ArrayList<>();
        Consumer<ScriptArgs> dispatched = event -> {};

        QueuedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current) {
            var task =
                    new FutureTask<Void>(
                            () -> {
                                if (current.getAsBoolean()) {
                                    ran.add(args);
                                    dispatched.accept(args);
                                }
                            },
                            null);
            queued.add(task);
            return task;
        }

        void drain() {
            while (queued.stream().anyMatch(task -> !task.isDone()))
                for (var task : List.copyOf(queued)) {
                    task.run();
                    try {
                        task.get();
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                }
        }
    }
}
