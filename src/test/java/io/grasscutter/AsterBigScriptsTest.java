package io.grasscutter;

import static emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.*;
import static emu.grasscutter.scripts.constants.EventType.EVENT_TIMER_EVENT;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionServerLuaCall;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.ServerUpdateGlobalValueNotifyOuterClass.ServerUpdateGlobalValueNotify;
import emu.grasscutter.net.proto.ShowTemplateReminderNotifyOuterClass.ShowTemplateReminderNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

class AsterBigScriptsTest {
    private static final int MAIN = 133001413;
    private static final int TIMER = 133001426;
    private static final String ENERGY = "AVATAR_ASTER_SCORE";
    private static JsonArray gadgetRows, abilityRows;
    private static JsonObject gadgetConfigs;
    private static AbilityModifierAction submitAction;
    private AbilityTestFixture environment;
    private BigScene scene;
    private AbilityTestFixture.TestPlayer host, guest;
    private ScenePlayBattleManager battles;
    private ScriptLib library;
    private SceneGroup main;
    private MpPlayGroupData entry;
    private MpPlayMatchData match;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void initialize() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        gadgetRows = json("ExcelBinOutput/GadgetExcelConfigData.json").getAsJsonArray();
        gadgetConfigs =
                json("BinOutput/Gadget/ConfigGadget_Scene_Meteorite.json").getAsJsonObject();
        abilityRows =
                json("BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Meteorite.json")
                        .getAsJsonArray();
        var action =
                findSubmit(
                        json(
                                "BinOutput/Ability/Temp/ChallengeAbilities/ConfigAbility_Challenge_Meteorite.json"));
        assertNotNull(action);
        submitAction = JsonUtils.decode(action.toString(), AbilityModifierAction.class);
    }

    private static JsonElement json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("resources", path)));
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        host = environment.player;
        scene =
                new BigScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), host));
        guest = new AbilityTestFixture.TestPlayer();
        ready(host, 10001, 1);
        ready(guest, 10002, 2);
        battles = scene.getScenePlayBattleManager();
        library = ScriptLoader.getScriptLib();
        for (int id : List.of(MAIN, 133001426, 133001427, 133001498)) {
            var group = SceneGroup.of(id).load(3);
            assertNotNull(group.init_config, "original group " + id);
            scene.scripts.loaded.put(id, group);
            scene.scripts.instances.put(id, new SceneGroupInstance(group, host));
        }
        main = scene.scripts.loaded.get(MAIN);
        library.setSceneScriptManager(scene.scripts);
        library.setCurrentGroup(main);
        entry = row("MpPlayGroupExcelConfigData.json", "playId", MpPlayGroupData.class);
        match = row("MpPlayMatchExcelConfigData.json", "id", MpPlayMatchData.class);
        for (int id : List.of(70370001, 70370002, 70370003, 70370004)) install(id);
        scene.packets.clear();
    }

    private void ready(AbilityTestFixture.TestPlayer player, int uid, int peer) {
        player.setUid(uid);
        player.setPeerId(peer);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
    }

    private <T> T row(String file, String key, Class<T> type) throws Exception {
        for (var raw : json("ExcelBinOutput/" + file).getAsJsonArray())
            if (raw.getAsJsonObject().get(key).getAsInt() == 2)
                return JsonUtils.decode(raw.toString(), type);
        throw new AssertionError(file);
    }

    private <K, V> void replace(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private void install(int id) {
        var raw =
                java.util.stream.StreamSupport.stream(gadgetRows.spliterator(), false)
                        .filter(v -> v.getAsJsonObject().get("id").getAsInt() == id)
                        .findFirst()
                        .orElseThrow();
        var data = JsonUtils.decode(raw.toString(), GadgetData.class);
        replace(GameData.getGadgetDataMap(), id, data);
        var name = data.getJsonName();
        replace(
                GameData.getGadgetConfigData(),
                name,
                JsonUtils.decode(gadgetConfigs.get(name).toString(), ConfigEntityGadget.class));
        var abilityRaw =
                java.util.stream.StreamSupport.stream(abilityRows.spliterator(), false)
                        .map(v -> v.getAsJsonObject().getAsJsonObject("Default"))
                        .filter(
                                v ->
                                        v != null
                                                && v.has("abilityName")
                                                && name.equals(v.get("abilityName").getAsString()))
                        .findFirst()
                        .orElseThrow();
        var ability = JsonUtils.decode(abilityRaw.toString(), AbilityData.class);
        ability.initialize();
        replace(GameData.getAbilityDataMap(), name, ability);
    }

    @AfterEach
    void cleanup() throws Exception {
        scene.scripts.onDestroy();
        scene.scripts.drain();
        library.removeCurrentGroup();
        library.removeCurrentEntity();
        library.removeCurrentCallParams();
        library.removeSceneScriptManager();
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        restore.clear();
        environment.close();
    }

    private int invoke(SceneGroup group, String function, ScriptArgs event) {
        synchronized (scene) {
            library.setCurrentGroup(group);
            library.setCurrentCallParams(event);
            try {
                var context = library.createEventContext(event, group);
                return ((LuaFunction) group.getBindings().get(function))
                        .call(context, org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(event))
                        .toint();
            } finally {
                library.setCurrentGroup(main);
                library.removeCurrentCallParams();
            }
        }
    }

    private void prepare(int difficulty, int type) {
        long now = scene.now.get();
        scene.scripts.variables.put("GM_Mode", difficulty);
        assertTrue(
                battles.prepare(
                        entry,
                        match,
                        2,
                        type,
                        type == DIRECT ? List.of(host) : List.of(host, guest)));
        scene.now.set(now + 20);
        battles.tick();
        assertEquals(READY, battles.getInfo(MAIN).getState());
        assertEquals(
                0,
                invoke(
                        main,
                        "LF_Battle_Ready",
                        new ScriptArgs(MAIN, 49, 2, 2).setParam3(READY).setUid(host.getUid())));
        assertEquals(PRESTART, battles.getInfo(MAIN).getState());
    }

    private void start() {
        prepare(1, MATCH);
        scene.now.addAndGet(10);
        battles.tick();
        assertEquals(START, battles.getInfo(MAIN).getState());
    }

    private boolean submit(AbilityTestFixture.TestPlayer player) {
        var carrier =
                battles.getPlayTeamEntities().stream()
                        .filter(entity -> entity.getPlayer() == player)
                        .findFirst()
                        .orElseThrow();
        var ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"Aster_Submit_Test\"}", AbilityData.class),
                        carrier,
                        player);
        return new ActionServerLuaCall()
                .execute(
                        ability,
                        submitAction,
                        ByteString.EMPTY,
                        player.getTeamManager().getEntity());
    }

    @Test
    void allOriginalGroupsLoadTheirMonstersSuitesAndLexicalCommonTriggers() {
        assertTrue(main.monsters.size() > 100);
        assertTrue(main.suites.size() >= 71);
        assertEquals(70900361, main.gadgets.get(413001).gadget_id);
        assertNotNull(main.gadgets.get(413124));
        for (int id : List.of(MAIN, 133001426, 133001427)) {
            var group = scene.scripts.loaded.get(id);
            assertInstanceOf(LuaFunction.class, group.getBindings().get("UpdateAsterProgress"));
            assertTrue(
                    group.getSuiteByIndex(group.init_config.suite)
                            .triggers
                            .contains("Monster_Die_Before_Leave_Scene"));
        }
        assertEquals(10, scene.scripts.loaded.get(133001427).monsters.size());
        assertTrue(scene.scripts.loaded.get(133001498).gadgets.size() > 10);
    }

    @Test
    void recoveredCommonReplacesTheSelectedCommentOnlyPlaceholder() {
        var common = ScriptLoader.readScript("Common/AsterBig.lua", false);
        assertTrue(common.contains("function UpdateAsterProgress"));
        assertTrue(
                common.contains(
                        "GetTeamAbilityFloatValue(context, context.uid, play.ratio_energy[1])"));
        assertFalse(common.contains("buff_stack//"));
    }

    @Test
    void sameCompiledGroupHasSeparateLuaBindingsAcrossWorlds() {
        var second = SceneGroup.of(MAIN).load(3);
        assertNotSame(main.getBindings(), second.getBindings());
        main.getBindings().put("world_marker", 71);
        assertNull(second.getBindings().get("world_marker"));
        assertInstanceOf(LuaFunction.class, second.getBindings().get("LF_Battle_Ready"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void originalReadyActionUsesActualDifficultyThresholdsRatiosAndPlayTeamAbility(int difficulty) {
        prepare(difficulty, MATCH);
        var info = battles.getInfo(MAIN);
        assertEquals(200 + difficulty, info.getMode());
        assertEquals(1030, info.getStartTime());
        assertEquals(600, info.getDuration());
        assertEquals(
                difficulty == 4
                        ? List.of(0, 300, 700, 1000, 1600, 2000)
                        : List.of(0, 700, 1400, 2000),
                info.getProgressStageListList());
        assertEquals(difficulty, battles.getUidValue(MAIN, host.getUid(), "difficulty"));
        assertEquals(
                difficulty == 4 ? 3 : difficulty == 3 ? 2 : 1,
                battles.getUidValue(MAIN, host.getUid(), "ratio_ptr"));
        assertEquals(2, battles.getPlayTeamEntities().size());
        assertTrue(
                battles.getPlayTeamEntities().stream()
                        .allMatch(entity -> entity.getGadgetConfigId() == 70370000 + difficulty));
        long buff = battles.getUidValue(MAIN, host.getUid(), "buff_stack");
        assertEquals(difficulty == 4 ? 1331 : 13, buff);
    }

    @Test
    void directBattleUsesOriginalSinglePlayerEasyMode() {
        prepare(0, DIRECT);
        assertEquals(204, battles.getInfo(MAIN).getMode());
        assertEquals(1, battles.getPlayTeamEntities().size());
        assertEquals(70370004, battles.getPlayTeamEntities().get(0).getGadgetConfigId());
    }

    @Test
    void buffDigitsUseLuaJCompatibleIntegerDivisionAtAllEasyStages() {
        prepare(4, MATCH);
        var function = (LuaFunction) main.getBindings().get("LF_Get_Uid_Op_Buff");
        var event = new ScriptArgs(MAIN, 50).setUid(host.getUid());
        var context = library.createEventContext(event, main);
        int[] expected = {1, 3, 3, 1};
        for (int index = 1; index <= 4; index++)
            assertEquals(
                    expected[index - 1], function.call(context, LuaValue.valueOf(index)).toint());
    }

    @ParameterizedTest
    @CsvSource({"0,0,2,2", "1,0,3,3", "0,1,2,4", "1,1,3,6"})
    void actualResourceAbilitySubmitsGuestEnergyAndReadsGuestRatios(
            int energyFlag, int progressFlag, int scored, int progressed) throws Exception {
        start();
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 1.25);
        library.SetTeamEntityGlobalFloatValue(new int[] {host.getUid()}, ENERGY, 7.5);
        library.SetTeamEntityGlobalFloatValue(
                new int[] {guest.getUid()}, "ASTER_ENERGY_RATIO", energyFlag);
        library.SetTeamEntityGlobalFloatValue(
                new int[] {guest.getUid()}, "ASTER_PROGRESS_RATIO", progressFlag);
        // Host flags differ; entity IDs must never be interpreted as the submitting player's UID.
        library.SetTeamEntityGlobalFloatValue(
                new int[] {host.getUid()}, "ASTER_ENERGY_RATIO", 1 - energyFlag);
        library.SetTeamEntityGlobalFloatValue(
                new int[] {host.getUid()}, "ASTER_PROGRESS_RATIO", 1 - progressFlag);
        guest.packets.clear();
        assertTrue(submit(guest));
        assertEquals(scored, battles.getUidValue(MAIN, guest.getUid(), ENERGY));
        assertEquals(progressed, battles.getInfo(MAIN).getProgress());
        assertEquals(0f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
        assertEquals(7.5f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(0, battles.getUidValue(MAIN, host.getUid(), ENERGY));
        assertEquals(1, guest.packets.size());
        var clear = ServerUpdateGlobalValueNotify.parseFrom(guest.packets.get(0).getData());
        assertEquals(-1.25f, clear.getDelta());
        assertEquals(guest.getTeamManager().getEntity().getId(), clear.getEntityId());
        assertTrue(submit(guest));
        assertEquals(scored, battles.getUidValue(MAIN, guest.getUid(), ENERGY));
        assertEquals(progressed, battles.getInfo(MAIN).getProgress());
    }

    @Test
    void successiveSubmissionsAccumulatePerPlayerAndSharedBattleProgress() {
        start();
        library.SetTeamEntityGlobalFloatValue(new int[] {host.getUid()}, ENERGY, 2.5);
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 4.1);
        assertTrue(submit(host));
        assertTrue(submit(guest));
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 1.1);
        assertTrue(submit(guest));
        assertEquals(3, battles.getUidValue(MAIN, host.getUid(), ENERGY));
        assertEquals(7, battles.getUidValue(MAIN, guest.getUid(), ENERGY));
        assertEquals(10, battles.getInfo(MAIN).getProgress());
    }

    @Test
    void groupReplacementRejectsOldAbilityCallBeforeClearingEnergy() {
        start();
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 5);
        scene.scripts.loaded.put(MAIN, SceneGroup.of(MAIN).load(3));
        assertFalse(submit(guest));
        assertEquals(5f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    @Test
    void stoppedBattleRejectsRetainedCarrierAndCannotClearPlayerEnergy() {
        start();
        var carrier =
                battles.getPlayTeamEntities().stream()
                        .filter(e -> e.getPlayer() == guest)
                        .findFirst()
                        .orElseThrow();
        var ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"Aster_Submit_Test\"}", AbilityData.class),
                        carrier,
                        guest);
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 5);
        scene.now.set(1630);
        battles.tick();
        assertEquals(STOP, battles.getInfo(MAIN).getState());
        assertFalse(
                new ActionServerLuaCall()
                        .execute(
                                ability,
                                submitAction,
                                ByteString.EMPTY,
                                guest.getTeamManager().getEntity()));
        assertEquals(5f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    @Test
    void countdownRoundRejectsSubmissionBeforeClearingPlayerEnergy() {
        prepare(1, MATCH);
        library.SetTeamEntityGlobalFloatValue(new int[] {guest.getUid()}, ENERGY, 5);
        assertFalse(submit(guest));
        assertEquals(5f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
        assertEquals(0, battles.getUidValue(MAIN, guest.getUid(), ENERGY));
        assertEquals(0, battles.getInfo(MAIN).getProgress());
    }

    @Test
    void originalMonsterScoringActionUsesRealKillScoresAndDifficultyRatio() {
        start();
        battles.setUidValue(MAIN, host.getUid(), "ratio_ptr", 2);
        var definition =
                main.monsters.values().stream()
                        .filter(monster -> monster.kill_score > 0)
                        .findFirst()
                        .orElseThrow();
        var event = new ScriptArgs(MAIN, 55, definition.config_id).setUid(host.getUid());
        assertEquals(0, invoke(main, "action_monster_die_before_leave_scene", event));
        assertEquals(
                definition.kill_score * 2f,
                library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(
                definition.kill_score * 2f,
                library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    private ScoringMonster scoringMonster(int id, boolean scoring) {
        var group = scene.scripts.loaded.get(id);
        scene.scripts.registerTrigger(group.triggers.get("Monster_Die_Before_Leave_Scene"));
        var definition =
                group.monsters.values().stream()
                        .filter(monster -> monster.kill_score > 0)
                        .findFirst()
                        .orElseThrow();
        if (!scoring) {
            // Exercise the original condition's zero-score branch with a controlled Lua row.
            ((LuaTable) group.getBindings().get("monsters"))
                    .get(definition.config_id)
                    .set("kill_score", 0);
            definition.kill_score = 0;
        }
        var monster = new ScoringMonster(scene);
        monster.setGroupId(id);
        monster.setConfigId(definition.config_id);
        monster.setMetaMonster(definition);
        scene.addEntity(monster);
        return monster;
    }

    @ParameterizedTest
    @ValueSource(ints = {MAIN, 133001426, 133001427})
    void actualMonsterDeathRunsOriginalScoringTriggersInAllThreeCombatGroups(int id) {
        start();
        battles.setUidValue(MAIN, host.getUid(), "ratio_ptr", 2);
        var monster = scoringMonster(id, true);
        scene.killEntity(monster);
        float expected = monster.getMetaMonster().kill_score * 2f;
        assertEquals(expected, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(expected, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
        assertNull(scene.getEntityById(monster.getId()));
        scene.killEntity(monster);
        assertEquals(expected, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
    }

    @Test
    void zeroScoreMonsterDoesNotAwardAsterEnergy() {
        start();
        scene.killEntity(scoringMonster(MAIN, false));
        assertEquals(0f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(0f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    @Test
    void countdownKillDoesNotAwardAsterEnergy() {
        prepare(1, MATCH);
        scene.killEntity(scoringMonster(MAIN, true));
        assertEquals(0f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(0f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    @Test
    void stoppedBattleDoesNotAwardAsterEnergyFromRemainingMonsters() {
        start();
        var monster = scoringMonster(MAIN, true);
        scene.now.set(1630);
        battles.tick();
        scene.killEntity(monster);
        assertEquals(0f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        assertEquals(0f, library.GetTeamAbilityFloatValue(guest.getUid(), ENERGY));
    }

    @Test
    void oldGroupMonsterCannotAwardEnergyAfterGroupReplacement() {
        start();
        var monster = scoringMonster(MAIN, true);
        scene.scripts.loaded.put(MAIN, SceneGroup.of(MAIN).load(3));
        scene.killEntity(monster);
        assertEquals(0f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
    }

    @Test
    void survivingMonsterFromPreviousBattleCannotAwardEnergyToTheNewBattle() {
        start();
        var old = scoringMonster(MAIN, true);
        scene.now.set(1630);
        battles.tick();
        prepare(1, MATCH);
        scene.now.set(1660);
        battles.tick();
        assertEquals(START, battles.getInfo(MAIN).getState());
        scene.killEntity(old);
        assertEquals(0f, library.GetTeamAbilityFloatValue(host.getUid(), ENERGY));
        var fresh = scoringMonster(MAIN, true);
        scene.killEntity(fresh);
        assertTrue(library.GetTeamAbilityFloatValue(host.getUid(), ENERGY) > 0);
    }

    private SceneGroup setTimer(int choice) {
        var group = scene.scripts.loaded.get(TIMER);
        var math = new LuaTable();
        math.set(
                "randomseed",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs values) {
                        return LuaValue.NONE;
                    }
                });
        math.set(
                "random",
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs values) {
                        return LuaValue.valueOf(choice);
                    }
                });
        // Choose both original suite alternatives deterministically without changing shared
        // globals.
        var closure = (LuaClosure) group.getBindings().get("action_timer_summon");
        var globals = closure.upValues[0].getValue();
        assertSame(ScriptLoader.getScriptLibLua(), globals.get("ScriptLib"));
        globals.set("math", math);
        scene.scripts.registerTrigger(group.triggers.get("Timer_Summon"));
        assertEquals(0, invoke(group, "LF_Set_Timer", new ScriptArgs(TIMER, 50)));
        return group;
    }

    private void timerTicks(int seconds) throws Exception {
        for (int i = 0; i < seconds; i++) {
            scene.now.incrementAndGet();
            scene.getScheduler().runTasks();
            scene.scripts.drain();
            scene.scripts.tickGroupTimers();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void originalTimedReinforcementsSpawnAtAllFiveTimesIncludingTheLastWave(int difficulty)
            throws Exception {
        prepare(difficulty, MATCH);
        scene.now.addAndGet(10);
        battles.tick();
        assertEquals(START, battles.getInfo(MAIN).getState());
        var timer = setTimer(1);
        scene.packets.clear();
        long started = scene.now.get();
        int[] times = {60, 180, 300, 420, 540};
        int[] configs = {426002, 426003, 426004, 426001, 426002};
        int previous = 0;
        for (int i = 0; i < times.length; i++) {
            timerTicks(times[i] - previous - 1);
            assertEquals(i, scene.scripts.spawned.size());
            timerTicks(1);
            assertEquals(i + 1, scene.scripts.spawned.size(), "wave at " + times[i]);
            var monster = scene.scripts.spawned.get(i);
            assertEquals(configs[i], monster.getConfigId());
            assertEquals(TIMER, monster.getGroupId());
            assertSame(monster, scene.getEntityById(monster.getId()));
            assertEquals(started + times[i], scene.scripts.spawnTimes.get(i));
            assertEquals(i + 2, battles.getUidValue(MAIN, host.getUid(), "timer_ptr"));
            // The final original suite reuses the first monster config ID.
            scene.killEntity(monster);
            previous = times[i];
        }
        var reminders =
                scene.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ShowTemplateReminderNotify)
                        .toList();
        assertEquals(5, reminders.size());
        for (var packet : reminders) {
            var reminder = ShowTemplateReminderNotify.parseFrom(packet.getData());
            assertEquals(110, reminder.getTemplateReminderId());
            assertEquals(List.of(0, 0), reminder.getParamListList());
        }
        assertTrue(
                scene.scripts
                        .getTriggersByEvent(EVENT_TIMER_EVENT)
                        .contains(timer.triggers.get("Timer_Summon")));
        assertEquals(
                0, invoke(timer, "action_timer_summon", new ScriptArgs(TIMER, EVENT_TIMER_EVENT)));
        timerTicks(30);
        assertEquals(5, scene.scripts.spawned.size());
    }

    @Test
    void originalEmptySuiteAlternativeAdvancesAllFiveWavesWithoutRemindersOrMonsters()
            throws Exception {
        start();
        setTimer(2);
        scene.packets.clear();
        timerTicks(550);
        assertEquals(6, battles.getUidValue(MAIN, host.getUid(), "timer_ptr"));
        assertTrue(scene.scripts.spawned.isEmpty());
        assertTrue(
                scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ShowTemplateReminderNotify));
    }

    @Test
    void countdownTimerCannotSpawnOrConsumeItsOriginalWave() throws Exception {
        prepare(1, MATCH);
        var timer = setTimer(1);
        assertEquals(
                0, invoke(timer, "action_timer_summon", new ScriptArgs(TIMER, EVENT_TIMER_EVENT)));
        assertTrue(scene.scripts.spawned.isEmpty());
        assertEquals(1, battles.getUidValue(MAIN, host.getUid(), "timer_ptr"));
    }

    @Test
    void oldReinforcementTimerCannotRunAfterStopOrInTheNextBattle() throws Exception {
        start();
        var timer = setTimer(1);
        scene.now.set(1630);
        battles.tick();
        assertEquals(STOP, battles.getInfo(MAIN).getState());
        assertEquals(
                0, invoke(timer, "action_timer_summon", new ScriptArgs(TIMER, EVENT_TIMER_EVENT)));
        assertTrue(scene.scripts.spawned.isEmpty());
        prepare(1, MATCH);
        scene.now.addAndGet(10);
        battles.tick();
        timerTicks(61);
        assertTrue(scene.scripts.spawned.isEmpty());
        setTimer(1);
        timerTicks(60);
        assertEquals(1, scene.scripts.spawned.size());
    }

    private static final class ScoringMonster extends EntityMonster {
        ScoringMonster(Scene scene) {
            super(
                    scene,
                    new Gson()
                            .fromJson(
                                    "{\"id\":21010101,\"type\":\"MONSTER_ORDINARY\","
                                            + "\"equips\":[],\"affix\":[]}",
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
        public void onDeath(int killer) {}

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }

    private static JsonObject findSubmit(JsonElement node) {
        if (node.isJsonObject()) {
            var object = node.getAsJsonObject();
            if (object.has("luaCallType")
                    && object.has("funcName")
                    && "CurScenePlay".equals(object.get("luaCallType").getAsString())
                    && "UpdateAsterProgress".equals(object.get("funcName").getAsString()))
                return object;
            for (var child : object.entrySet()) {
                var result = findSubmit(child.getValue());
                if (result != null) return result;
            }
        } else if (node.isJsonArray())
            for (var child : node.getAsJsonArray()) {
                var result = findSubmit(child);
                if (result != null) return result;
            }
        return null;
    }

    static final class BigScene extends AbilityTestFixture.TestScene {
        final AtomicLong now = new AtomicLong(1000);
        final BigScripts scripts;

        BigScene(AbilityTestFixture.TestWorld world) throws Exception {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_DUNGEON\"}", SceneData.class));
            scripts = new BigScripts(this);
            var field = Scene.class.getDeclaredField("scenePlayBattleManager");
            field.setAccessible(true);
            field.set(this, new ScenePlayBattleManager(this, now::get));
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    static final class BigScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>();
        final Map<Integer, SceneGroupInstance> instances = new HashMap<>();
        final List<ScoringMonster> spawned = new ArrayList<>();
        final List<Long> spawnTimes = new ArrayList<>();
        final List<Future<?>> callbacks = new CopyOnWriteArrayList<>();
        final Map<String, Integer> variables = new HashMap<>();

        BigScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public SceneGroup getGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public SceneGroupInstance getGroupInstanceById(int id) {
            return instances.get(id);
        }

        @Override
        public EntityMonster createMonster(int groupId, int blockId, SceneMonster definition) {
            var monster = new ScoringMonster(getScene());
            monster.setGroupId(groupId);
            monster.setConfigId(definition.config_id);
            monster.setMetaMonster(definition);
            spawned.add(monster);
            spawnTimes.add(((BigScene) getScene()).now.get());
            return monster;
        }

        @Override
        public Map<String, Integer> getVariables(int id) {
            return variables;
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current, boolean locked) {
            if (args.type != emu.grasscutter.scripts.constants.EventType.EVENT_TIMER_EVENT)
                return CompletableFuture.completedFuture(null);
            var result = super.callEvent(args, current, locked);
            callbacks.add(result);
            return result;
        }

        void drain() throws Exception {
            var pending = List.copyOf(callbacks);
            callbacks.removeAll(pending);
            for (var callback : pending)
                if (!callback.isCancelled()) callback.get(5, TimeUnit.SECONDS);
        }
    }
}
