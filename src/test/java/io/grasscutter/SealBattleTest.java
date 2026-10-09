package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.seal.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SealBattleBeginNotifyOuterClass.SealBattleBeginNotify;
import emu.grasscutter.net.proto.SealBattleEndNotifyOuterClass.SealBattleEndNotify;
import emu.grasscutter.net.proto.SealBattleProgressNotifyOuterClass.SealBattleProgressNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.serializer.LuaSerializer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

class SealBattleTest {
    private Fixture fixture;
    private static final SealBattleParameters ORIGINAL =
            new SealBattleParameters(10, 600, 302001058, 0, 0, 30, 300);

    @BeforeEach
    void setup() throws Exception {
        fixture = new Fixture();
    }

    @AfterEach
    void close() {
        if (fixture != null) fixture.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void originalDifficultyScriptStartsRealLuaApiWithItsResourceParameters(int difficulty)
            throws Exception {
        var globals = original("activity2001_group302001020.lua");
        var library = new LuaTable();
        var variables = new HashMap<String, Integer>();
        var refreshes = new ArrayList<String>();
        var states = new ArrayList<Integer>();
        var rewards = new ArrayList<Integer>();
        for (var name : List.of("PrintLog")) bind(library, name, args -> LuaValue.ZERO);
        bind(
                library,
                "SetGadgetStateByConfigId",
                args -> {
                    states.add(args.arg(3).toint());
                    return LuaValue.ZERO;
                });
        bind(
                library,
                "CreateAsterMidGeneralRewardGadget",
                args -> {
                    assertEquals(20022, args.arg(2).get("config_id").toint());
                    rewards.add(args.arg(2).get("difficulty_id").toint());
                    return LuaValue.ZERO;
                });
        bind(
                library,
                "GetGroupVariableValue",
                args -> LuaValue.valueOf(variables.getOrDefault(args.arg(2).tojstring(), 0)));
        bind(
                library,
                "SetGroupVariableValue",
                args -> {
                    variables.put(args.arg(2).tojstring(), args.arg(3).toint());
                    return LuaValue.ZERO;
                });
        bind(
                library,
                "RefreshGroup",
                args -> {
                    refreshes.add(
                            args.arg(2).get("group_id").toint()
                                    + ":"
                                    + args.arg(2).get("suite").toint());
                    return LuaValue.ZERO;
                });
        var actualLibrary = new ScriptLib();
        actualLibrary.setSceneScriptManager(fixture.scene.scripts);
        actualLibrary.setCurrentGroup(fixture.group);
        bind(
                library,
                "StartSealBattle",
                args ->
                        LuaValue.valueOf(
                                actualLibrary.StartSealBattle(
                                        args.arg(2).toint(), args.arg(3).checktable())));
        globals.set("ScriptLib", library);
        fixture.scene.scripts.dispatched =
                args -> {
                    if (args.type != EventType.EVENT_SEAL_BATTLE_END) return;
                    var luaEvent = new LuaTable();
                    luaEvent.set("param1", args.param1);
                    luaEvent.set("param2", args.param2);
                    assertEquals(
                            0,
                            globals.get("action_EVENT_SEAL_BATTLE_END_20030")
                                    .call(LuaValue.NIL, luaEvent)
                                    .toint());
                };
        var event = new LuaTable();
        event.set("param1", 20021);
        event.set("param2", difficulty);
        event.set("param3", 14);
        assertEquals(
                0,
                globals.get("action_EVENT_SELECT_DIFFICULTY_20031")
                        .call(LuaValue.NIL, event)
                        .toint());
        int suite = difficulty < 5 ? 1 : 3;
        assertEquals(List.of("302001058:" + suite), refreshes);
        assertEquals(300, lastProgress().getMaxProgress());
        assertEquals(600, lastProgress().getEndTime());
        assertEquals(10, lastProgress().getSealRadius());
        var begin =
                SealBattleBeginNotify.parseFrom(
                        packets(PacketOpcodes.SealBattleBeginNotify).get(0).getData());
        assertEquals(2, begin.getBattleType());
        assertEquals(fixture.gadget.getId(), begin.getSealEntityId());
        fixture.score(fixture.monster(302001058, 300));
        fixture.scene.scripts.drain();
        assertEquals(1, variables.get("isDone"));
        assertEquals(
                List.of(difficulty),
                rewards,
                "Reward creation is only a fixture binding; activity rewards remain separate");
        assertEquals(List.of("302001058:" + suite, "302001058:5"), refreshes);
        assertEquals(List.of(201, 202), states);
        variables.put("isDone", 2);
        globals.get("action_EVENT_SELECT_DIFFICULTY_20031").call(LuaValue.NIL, event);
        assertEquals(0, lastProgress().getProgress());
        assertEquals(0, variables.get("isDone"));
        fixture.at(600);
        fixture.scene.scripts.drain();
        assertEquals(2, variables.get("isDone"));
        assertEquals(0, variables.get("isShow"));
        assertEquals(List.of(201, 202, 201, 0), states);
        assertEquals(1, rewards.size());
        actualLibrary.removeCurrentGroup();
        actualLibrary.removeSceneScriptManager();
    }

    @Test
    void actualCombatDeathScoresOnlyOnceAndSendsVictoryWithOriginalLuaEvent() throws Exception {
        assertTrue(start(ORIGINAL));
        var monster = fixture.monster(302001058, 300);
        fixture.scene.killEntity(monster, fixture.avatar.getId());
        assertEquals(300, lastProgress().getProgress());
        fixture.scene.killEntity(monster, fixture.avatar.getId());
        fixture.scene.getSealBattleManager().onMonsterKilled(monster, fixture.player);
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
        assertTrue(
                SealBattleEndNotify.parseFrom(
                                packets(PacketOpcodes.SealBattleEndNotify).get(0).getData())
                        .getIsWin());
        fixture.scene.scripts.drain();
        var event =
                fixture.scene.scripts.ran.stream()
                        .filter(e -> e.type == EventType.EVENT_SEAL_BATTLE_END)
                        .findFirst()
                        .orElseThrow();
        assertEquals(20021, event.param1);
        assertEquals(1, event.param2);
        assertEquals(fixture.gadget.getId(), event.source_eid);
    }

    @Test
    void unrelatedMonstersVisitorsAndCleanupDoNotGiveEnergy() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.scene
                .getSealBattleManager()
                .onMonsterKilled(fixture.monster(302001022, 300), fixture.player);
        var monster = fixture.monster(302001058, 50);
        var visitor = new SealPlayer();
        visitor.setScene(fixture.scene);
        fixture.scene.getSealBattleManager().onMonsterKilled(monster, visitor);
        fixture.scene.getEntities().remove(monster.getId());
        fixture.scene.getSealBattleManager().onMonsterKilled(monster, fixture.player);
        fixture.scene.killEntity(fixture.monster(302001058, 50));
        assertEquals(0, lastProgress().getProgress());
        assertTrue(packets(PacketOpcodes.SealBattleEndNotify).isEmpty());
    }

    @Test
    void originalMonsterScriptPreservesScoresAndDefaultChargeIsOnlyAFallback() throws Exception {
        var globals = original("activity2001_group302001058.lua");
        var monsters = new LuaSerializer().toList(SceneMonster.class, globals.get("monsters"));
        assertTrue(monsters.stream().anyMatch(m -> m.kill_score == 20));
        assertTrue(monsters.stream().anyMatch(m -> m.kill_score == 30));
        assertTrue(monsters.stream().anyMatch(m -> m.kill_score == 50));
        assertTrue(start(new SealBattleParameters(10, 600, 302001058, 7, 0, 30, 300)));
        fixture.score(fixture.monster(302001058, 20));
        fixture.score(fixture.monster(302001058, 0));
        assertEquals(27, lastProgress().getProgress());
    }

    @Test
    void leavingTheSphereSkipsTransitionThenDeclinesByElapsedPlayerTime() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.at(1); // Enter the radius; the transition has no charge/decline.
        fixture.score(fixture.monster(302001058, 100));
        fixture.avatar.getPosition().setX(11);
        fixture.at(2);
        assertEquals(100, lastProgress().getProgress());
        fixture.scene.scripts.drain();
        assertEquals(
                1,
                fixture.scene.scripts.ran.stream()
                        .filter(e -> e.type == EventType.EVENT_SEAL_BATTLE_PROGRESS_DECREASE)
                        .count());
        fixture.at(4);
        assertEquals(40, lastProgress().getProgress());
        fixture.at(7);
        assertEquals(0, lastProgress().getProgress());
        fixture.avatar.getPosition().setX(10);
        fixture.at(8);
        fixture.at(9);
        assertEquals(0, lastProgress().getProgress());
    }

    @Test
    void automaticChargeCanFinishAndNeverAdvancesTwiceAtTheSameSecond() throws Exception {
        assertTrue(start(new SealBattleParameters(10, 600, 302001058, 0, 100, 30, 300)));
        fixture.at(1);
        assertEquals(0, lastProgress().getProgress());
        fixture.at(3);
        assertEquals(200, lastProgress().getProgress());
        fixture.at(3);
        assertEquals(200, lastProgress().getProgress());
        fixture.at(4);
        assertEquals(300, lastProgress().getProgress());
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
    }

    @Test
    void energyAtTheExactDeadlineCannotTurnTimeoutIntoVictory() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.player.timeMs = 600_000;
        fixture.score(fixture.monster(302001058, 300));
        fixture.at(601);
        assertEquals(0, lastProgress().getProgress());
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
        assertFalse(
                SealBattleEndNotify.parseFrom(
                                packets(PacketOpcodes.SealBattleEndNotify).get(0).getData())
                        .getIsWin());
    }

    @Test
    void scenePauseAndClockIndependentOfSceneTimePreserveTheDeadline() throws Exception {
        fixture.player.timeMs = 123_000;
        assertTrue(start(ORIGINAL));
        assertEquals(723, lastProgress().getEndTime());
        fixture.score(fixture.monster(302001058, 100));
        fixture.scene.setPaused(true);
        fixture.scene.getSealBattleManager().tick();
        fixture.score(fixture.monster(302001058, 300));
        assertEquals(100, lastProgress().getProgress());
        assertTrue(packets(PacketOpcodes.SealBattleEndNotify).isEmpty());
        fixture.scene.setPaused(false);
        fixture.at(125);
        fixture.at(126);
        assertEquals(100, lastProgress().getProgress());
    }

    @Test
    void freshRoundStartsAtZeroAndWaitsForEveryOldCallback() throws Exception {
        assertTrue(start(ORIGINAL));
        assertFalse(start(ORIGINAL));
        fixture.score(fixture.monster(302001058, 300));
        assertFalse(start(ORIGINAL));
        fixture.scene.scripts
                .runLast(); // Finish callback done; the begin callback is still queued.
        assertFalse(start(ORIGINAL));
        fixture.scene.scripts.drain();
        assertTrue(start(ORIGINAL));
        assertEquals(0, lastProgress().getProgress());
        assertTrue(packets(PacketOpcodes.SealBattleEndNotify).size() == 1);
        fixture.scene.scripts.drain();
        assertEquals(
                1,
                fixture.scene.scripts.ran.stream()
                        .filter(e -> e.type == EventType.EVENT_SEAL_BATTLE_BEGIN)
                        .count());
    }

    @Test
    void removedSealAndReloadedGroupCannotApplyOldCallbacksToTheNewSeal() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.scene.removeEntity(fixture.gadget);
        fixture.scene.getSealBattleManager().onGroupUnloaded(fixture.group);
        var replacement = new SceneGroup();
        replacement.id = fixture.group.id;
        fixture.group = replacement;
        fixture.scene.scripts.loaded.put(replacement.id, replacement);
        fixture.gadget = fixture.gadget();
        assertFalse(start(ORIGINAL), "Old callbacks still own their group/config key");
        fixture.scene.scripts.drain();
        assertTrue(fixture.scene.scripts.ran.isEmpty());
        assertTrue(start(ORIGINAL));
        fixture.scene
                .getSealBattleManager()
                .onEntityRemoved(new EntityGadget(fixture.scene, 70950015, new Position()));
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
    }

    @Test
    void ownerLeavingAndSceneDestructionFinishOnlyOnce() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.scene.getSealBattleManager().onPlayerLeaving(fixture.player);
        fixture.scene.getPlayers().remove(fixture.player);
        fixture.player.setScene(null);
        fixture.scene.getSealBattleManager().tick();
        fixture.scene.getScriptManager().onDestroy();
        fixture.scene.getScriptManager().onDestroy();
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
        fixture.scene.scripts.drain();
        assertTrue(fixture.scene.scripts.ran.isEmpty());
        fixture.player.setScene(fixture.scene);
        fixture.scene.getPlayers().add(fixture.player);
        assertFalse(start(ORIGINAL));
    }

    @Test
    void ownerFiftyMetersAwayEndsBattleAndHorizontalDistanceIgnoresHeight() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.avatar.getPosition().setY(100);
        fixture.at(1);
        fixture.score(fixture.monster(302001058, 50));
        fixture.at(2);
        assertEquals(50, lastProgress().getProgress());
        fixture.avatar.getPosition().setX(50);
        fixture.at(3);
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
        assertFalse(
                SealBattleEndNotify.parseFrom(
                                packets(PacketOpcodes.SealBattleEndNotify).get(0).getData())
                        .getIsWin());
    }

    @Test
    void staleGroupObjectWithSameNumericIdCannotStopCurrentRound() throws Exception {
        assertTrue(start(ORIGINAL));
        var stale = new SceneGroup();
        stale.id = 302001058;
        fixture.scene.getSealBattleManager().onGroupUnloaded(stale);
        stale.id = fixture.group.id;
        fixture.scene.getSealBattleManager().onGroupUnloaded(stale);
        fixture.score(fixture.monster(302001058, 50));
        assertEquals(50, lastProgress().getProgress());
        assertTrue(packets(PacketOpcodes.SealBattleEndNotify).isEmpty());
    }

    @Test
    void deadPartyCannotKeepBattleAliveOrStartAnother() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.avatar.markDead();
        fixture.at(1);
        assertEquals(1, packets(PacketOpcodes.SealBattleEndNotify).size());
        fixture.scene.scripts.drain();
        assertFalse(start(ORIGINAL));
    }

    @Test
    void activeRoundSnapshotRestoresBothUiMessagesAndExcludesFinishedRound() throws Exception {
        assertTrue(start(ORIGINAL));
        fixture.score(fixture.monster(302001058, 50));
        fixture.player.packets.clear();
        fixture.scene.getSealBattleManager().sendTo(fixture.player);
        assertEquals(
                List.of(
                        PacketOpcodes.SealBattleBeginNotify,
                        PacketOpcodes.SealBattleProgressNotify),
                fixture.player.packets.stream().map(BasePacket::getOpcode).toList());
        assertEquals(
                50,
                SealBattleProgressNotify.parseFrom(fixture.player.packets.get(1).getData())
                        .getProgress());
        fixture.score(fixture.monster(302001058, 300));
        fixture.player.packets.clear();
        fixture.scene.getSealBattleManager().sendTo(fixture.player);
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void joiningPlayerWithDifferentClockReceivesTheSameRemainingBattleTime() throws Exception {
        fixture.player.timeMs = 123_000;
        assertTrue(start(ORIGINAL));
        var visitor = new SealPlayer();
        visitor.setScene(fixture.scene);
        visitor.setWorld(fixture.scene.getWorld());
        visitor.getPosition().set(0, 0, 0);
        visitor.getTeamManager().getActiveTeam().add(new SealAvatar(fixture.scene, visitor));
        visitor.timeMs = 200_000;
        fixture.scene.getPlayers().add(visitor);
        fixture.scene.getSealBattleManager().sendTo(visitor);
        var packet =
                visitor.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SealBattleProgressNotify)
                        .findFirst()
                        .orElseThrow();
        assertEquals(800, SealBattleProgressNotify.parseFrom(packet.getData()).getEndTime());
        assertEquals(723, lastProgress().getEndTime());
        visitor.packets.clear();
        visitor.timeMs = 205_000;
        fixture.at(128);
        packet =
                visitor.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SealBattleProgressNotify)
                        .findFirst()
                        .orElseThrow();
        assertEquals(800, SealBattleProgressNotify.parseFrom(packet.getData()).getEndTime());
        assertEquals(723, lastProgress().getEndTime());
    }

    @Test
    void invalidAndUnsupportedLuaCallsNeverPretendToStart() {
        var library = new ScriptLib();
        assertEquals(-1, library.StartSealBattle(20021, new LuaTable()));
        library.setSceneScriptManager(fixture.scene.scripts);
        library.setCurrentGroup(fixture.group);
        var table = new LuaTable();
        table.set(
                "battle_type",
                2); // Lua KILL_MONSTER is intentionally not implemented by this energy engine.
        assertEquals(-1, library.StartSealBattle(20021, table));
        for (var value : List.of(Double.NaN, Double.POSITIVE_INFINITY, -1d, 1.5d, 2147483648d)) {
            table.set("battle_type", 1);
            table.set("radius", LuaValue.valueOf(value));
            assertEquals(-1, library.StartSealBattle(20021, table));
        }
        assertTrue(packets(PacketOpcodes.SealBattleBeginNotify).isEmpty());
    }

    @Test
    void protocolParsesNative71TagsWithoutOldSchemaFieldNumbers() throws Exception {
        var begin =
                SealBattleBeginNotify.parseFrom(HexFormat.of().parseHex("08ac02380a68b9607002"));
        assertEquals(10, begin.getSealRadius());
        assertEquals(300, begin.getSealMaxProgress());
        assertEquals(12345, begin.getSealEntityId());
        assertEquals(2, begin.getBattleType());
        var progress =
                SealBattleProgressNotify.parseFrom(
                        HexFormat.of().parseHex("18d804280a30ac0268b9607832"));
        assertEquals(50, progress.getProgress());
        assertEquals(300, progress.getMaxProgress());
        assertEquals(600, progress.getEndTime());
        assertEquals(12345, progress.getSealEntityId());
        assertEquals(10, progress.getSealRadius());
        var end = SealBattleEndNotify.parseFrom(HexFormat.of().parseHex("30b9606001"));
        assertEquals(12345, end.getSealEntityId());
        assertTrue(end.getIsWin());
        assertEquals(21905, PacketOpcodes.SealBattleBeginNotify);
        assertEquals(24186, PacketOpcodes.SealBattleProgressNotify);
        assertEquals(750, PacketOpcodes.SealBattleEndNotify);
    }

    @Test
    void restoredScriptFixturesMatchTheirPinnedSourceHashes() throws Exception {
        var rows =
                com.google.gson.JsonParser.parseString(resource("sources.json")).getAsJsonArray();
        assertEquals(2, rows.size());
        for (var row : rows) {
            var record = row.getAsJsonObject();
            try (var stream =
                    getClass()
                            .getResourceAsStream(
                                    "/seal-battle/" + record.get("path").getAsString())) {
                assertEquals(
                        record.get("sha256").getAsString(),
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(stream.readAllBytes())));
            }
        }
    }

    private boolean start(SealBattleParameters parameters) {
        return fixture.scene
                .getSealBattleManager()
                .start(fixture.gadget, fixture.group, parameters);
    }

    private List<BasePacket> packets(int opcode) {
        var values =
                opcode == PacketOpcodes.SealBattleProgressNotify
                        ? fixture.player.packets
                        : fixture.scene.packets;
        return values.stream().filter(p -> p.getOpcode() == opcode).toList();
    }

    private SealBattleProgressNotify lastProgress() throws Exception {
        var values = packets(PacketOpcodes.SealBattleProgressNotify);
        return SealBattleProgressNotify.parseFrom(values.get(values.size() - 1).getData());
    }

    private static String resource(String name) throws Exception {
        try (var stream = SealBattleTest.class.getResourceAsStream("/seal-battle/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Globals original(String name) throws Exception {
        var globals = JsePlatform.standardGlobals();
        var events = new LuaTable();
        events.set(
                "EVENT_SEAL_BATTLE_PROGRESS_DECREASE",
                EventType.EVENT_SEAL_BATTLE_PROGRESS_DECREASE);
        events.set("EVENT_SEAL_BATTLE_END", EventType.EVENT_SEAL_BATTLE_END);
        events.set("EVENT_SELECT_DIFFICULTY", EventType.EVENT_SELECT_DIFFICULTY);
        events.set("EVENT_GENERAL_REWARD_DIE", EventType.EVENT_GENERAL_REWARD_DIE);
        events.set("EVENT_ANY_MONSTER_DIE", EventType.EVENT_ANY_MONSTER_DIE);
        events.set("EVENT_MONSTER_TIDE_OVER", EventType.EVENT_MONSTER_TIDE_OVER);
        globals.set("EventType", events);
        var states = new LuaTable();
        states.set("Default", 0);
        states.set("GearStart", 201);
        states.set("GearStop", 202);
        globals.set("GadgetState", states);
        var types = new LuaTable();
        types.set("ENERGY_CHARGE", 1);
        globals.set("SealBattleType", types);
        globals.get("package")
                .get("preload")
                .set(
                        "AsterMiddle",
                        new ZeroArgFunction() {
                            @Override
                            public LuaValue call() {
                                return LuaValue.TRUE;
                            }
                        });
        globals.load(resource(name), name).call();
        return globals;
    }

    private static void bind(LuaTable table, String name, Function<Varargs, LuaValue> action) {
        table.set(
                name,
                new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        return action.apply(args);
                    }
                });
    }

    private static final class SealPlayer extends AbilityTestFixture.TestPlayer {
        long timeMs;
        private final emu.grasscutter.game.activity.ActivityManager activity =
                new emu.grasscutter.game.activity.ActivityManager(this) {
                    @Override
                    public synchronized void refreshActivities() {}

                    @Override
                    public void triggerWatcher(
                            emu.grasscutter.game.props.WatcherTriggerType type, String... params) {}
                };

        @Override
        public emu.grasscutter.game.activity.ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public long getPlayerTimeMs() {
            return timeMs;
        }
    }

    private static final class SealAvatar extends EntityAvatar {
        SealAvatar(Scene scene, SealPlayer owner) {
            super(scene, new SkillTalentLevelTest.RecordingAvatar(owner, 10000073, depot()));
        }

        private static AvatarSkillDepotData depot() {
            var result =
                    JsonUtils.decode(
                            "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                            AvatarSkillDepotData.class);
            result.onLoad();
            return result;
        }

        @Override
        public void initAbilities() {}

        void markDead() {
            setDead(true);
        }
    }

    private static final class SealScene extends AbilityTestFixture.TestScene {
        final QueuedScripts scripts;

        SealScene(AbilityTestFixture.TestWorld world) {
            super(
                    world,
                    JsonUtils.decode(
                            "{\"id\":3,\"type\":\"SCENE_DUNGEON\"}",
                            emu.grasscutter.data.excels.scene.SceneData.class));
            scripts = new QueuedScripts(this);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class QueuedScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>();
        final List<FutureTask<Void>> queued = new ArrayList<>();
        final List<ScriptArgs> ran = new ArrayList<>();
        Consumer<ScriptArgs> dispatched = args -> {};

        QueuedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args) {
            return callEvent(args, () -> true);
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
            for (var task : List.copyOf(queued)) {
                task.run();
                try {
                    task.get();
                } catch (Exception failure) {
                    throw new AssertionError("Queued Lua callback failed", failure);
                }
            }
        }

        void runLast() {
            queued.get(queued.size() - 1).run();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AbilityTestFixture environment;
        final SealPlayer player;
        final SealScene scene;
        final SealAvatar avatar;
        SceneGroup group = new SceneGroup();
        EntityGadget gadget;

        Fixture() throws Exception {
            environment = new AbilityTestFixture();
            player = new SealPlayer();
            scene =
                    new SealScene(
                            new AbilityTestFixture.TestWorld(
                                    environment.scene.getWorld().getServer(), player));
            player.setScene(scene);
            player.setWorld(scene.getWorld());
            player.getPosition().set(0, 0, 0);
            player.getTeamManager().setEntity(new EntityTeam(player));
            scene.getPlayers().add(player);
            avatar = new SealAvatar(scene, player);
            player.getTeamManager().getActiveTeam().add(avatar);
            scene.getEntities().put(avatar.getId(), avatar);
            group.id = 302001020;
            scene.scripts.loaded.put(group.id, group);
            var monsterGroup = new SceneGroup();
            monsterGroup.id = 302001058;
            scene.scripts.loaded.put(monsterGroup.id, monsterGroup);
            gadget = gadget();
            scene.packets.clear();
        }

        EntityGadget gadget() {
            var result = new EntityGadget(scene, 70950015, new Position());
            result.setGroupId(group.id);
            result.setConfigId(20021);
            scene.getEntities().put(result.getId(), result);
            return result;
        }

        EntityMonster monster(int groupId, int score) {
            var data =
                    JsonUtils.decode(
                            "{\"id\":21010101,\"hpBase\":100,\"attackBase\":10,\"defenseBase\":5,\"propGrowCurves\":[]}",
                            MonsterData.class);
            var result =
                    new EntityMonster(scene, data, new Position(), new Position(), 1) {
                        @Override
                        public void initAbilities() {}
                        // This fixture isolates the actual Scene.killEntity path from quest/BP
                        // setup.
                        @Override
                        public void onDeath(int attackerId) {}
                    };
            var meta = new SceneMonster();
            meta.kill_score = score;
            result.setMetaMonster(meta);
            result.setGroupId(groupId);
            result.setConfigId(result.getId());
            scene.getEntities().put(result.getId(), result);
            return result;
        }

        void score(EntityMonster monster) {
            scene.getSealBattleManager().onMonsterKilled(monster, player);
        }

        void at(long seconds) {
            player.timeMs = seconds * 1000;
            scene.getSealBattleManager().tick();
        }

        @Override
        public void close() {
            scene.getSealBattleManager().close();
            scene.scripts.drain();
            environment.close();
        }
    }
}
