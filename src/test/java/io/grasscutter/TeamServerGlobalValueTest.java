package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;

import javax.script.SimpleBindings;

/**
 * Actual Lua calls, server broadcasts, team ability consumers and deferred scene initialization.
 */
class TeamServerGlobalValueTest {
    private static final String KEY = "SGV_Fungus_Burst_Count";
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestPlayer guest;
    private ScriptLib library;
    private SimpleBindings bindings;

    @BeforeAll
    static void initializeLua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        guest = new AbilityTestFixture.TestPlayer();
        ready(fixture.player, 10001);
        ready(guest, 10002);
        finished(true);
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(fixture.scene.getScriptManager());
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
    }

    private void ready(AbilityTestFixture.TestPlayer player, int uid) {
        player.setUid(uid);
        player.setWorld(fixture.scene.getWorld());
        player.setScene(fixture.scene);
        player.getTeamManager().setEntity(new EntityTeam(player));
        fixture.scene.getPlayers().add(player);
    }

    private void finished(boolean value) throws Exception {
        var field = Scene.class.getDeclaredField("finishedLoading");
        field.setAccessible(true);
        field.set(fixture.scene, value);
    }

    @AfterEach
    void cleanup() {
        library.removeCurrentEntity();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        fixture.scene.getScriptManager().onDestroy();
        fixture.close();
    }

    private double lua(String source) throws Exception {
        return ((Number) ScriptLoader.getEngine().eval(source, bindings)).doubleValue();
    }

    private float value() {
        return library.GetTeamServerGlobalValue(10002, KEY);
    }

    private ServerGlobalValueChangeNotify message(int index) throws Exception {
        var packet = fixture.scene.packets.get(index);
        assertEquals(1441, packet.getOpcode());
        return ServerGlobalValueChangeNotify.parseFrom(packet.getData());
    }

    @Test
    void fractionalValuesAreReadableImmediatelyAndBroadcastAsAbsoluteValues() throws Exception {
        assertEquals(
                0,
                lua(
                        "return ScriptLib.SetTeamServerGlobalValue(ScriptLib,10002,'"
                                + KEY
                                + "',1.25)"));
        assertEquals(
                0,
                lua(
                        "return ScriptLib.AddTeamServerGlobalValue(ScriptLib,10002,'"
                                + KEY
                                + "',0.5)"));
        assertEquals(
                1.75,
                lua("return ScriptLib.GetTeamServerGlobalValue(ScriptLib,10002,'" + KEY + "')"));
        assertEquals(1.75f, library.GetTeamAbilityFloatValue(10002, KEY));
        assertEquals(0f, library.GetTeamServerGlobalValue(10001, KEY));
        assertEquals(2, fixture.scene.packets.size());
        assertEquals(1.25f, message(0).getValue());
        assertEquals(1.75f, message(1).getValue());
        assertEquals(guest.getTeamManager().getEntity().getId(), message(1).getEntityId());
        assertEquals(Utils.abilityHash(KEY), message(1).getKeyHash());
        assertTrue(guest.packets.isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
        assertEquals(0, library.SetTeamServerGlobalValue(10002, KEY, 0));
        assertEquals(0f, value());
        assertEquals(0f, message(2).getValue());
    }

    @Test
    void existingCountersAreClampedWhileMissingCountersUseTheCreationValue() throws Exception {
        assertEquals(0, library.AddTeamServerGlobalValue(10002, KEY, -0.25));
        assertEquals(-0.25f, value());
        assertEquals(0, library.AddTeamServerGlobalValue(10002, KEY, -2));
        assertEquals(0f, value());
        library.SetTeamServerGlobalValue(10002, KEY, Float.MAX_VALUE);
        assertEquals(0, library.AddTeamServerGlobalValue(10002, KEY, Float.MAX_VALUE));
        assertEquals(Float.MAX_VALUE, value());
        assertEquals(Float.MAX_VALUE, message(3).getValue());
        library.SetTeamServerGlobalValue(10002, KEY, -5);
        assertEquals(-5f, value());
    }

    @Test
    void readsUseTheExistingDynamicValueTableWithoutCreatingMissingKeys() {
        var values = guest.getTeamManager().getEntity().getGlobalAbilityValues();
        assertEquals(0f, value());
        assertFalse(values.containsKey(KEY));
        values.put(KEY, 3.5f);
        assertEquals(3.5f, value());
        values.put("AVATAR_ASTER_SCORE", 7.25f);
        assertEquals(7.25f, library.GetTeamServerGlobalValue(10002, "AVATAR_ASTER_SCORE"));
        values.put(KEY, Float.NaN);
        assertEquals(0f, value());
        assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(0, library.SetTeamServerGlobalValue(10002, KEY, 2));
        assertEquals(2f, value());
    }

    @Test
    void delayedClientAbilityConfirmationsCannotOverwriteServerOwnedTeamValues() throws Exception {
        library.SetTeamServerGlobalValue(10002, KEY, 2.25);
        var manager = new AbilityManager(guest);
        for (var key :
                new AbilityString[] {
                    AbilityString.newBuilder().setHash(Utils.abilityHash(KEY)).build(),
                    AbilityString.newBuilder().setStr(KEY).build()
                }) {
            var scalar = AbilityScalarValueEntry.newBuilder().setKey(key).setFloatValue(0);
            manager.onAbilityInvoke(
                    AbilityInvokeEntry.newBuilder()
                            .setEntityId(guest.getTeamManager().getEntity().getId())
                            .setArgumentType(
                                    AbilityInvokeArgument
                                            .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                            .setAbilityData(scalar.build().toByteString())
                            .build());
        }
        assertEquals(2.25f, value());
        assertEquals(1, fixture.scene.packets.size());
        assertEquals(0, library.AddTeamServerGlobalValue(10002, KEY, 0.25));
        assertEquals(2.5f, value());
    }

    @Test
    void serverWritesRefreshAnActualTeamValueAbilityConsumer() {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"TeamServerWatcher\",\"abilityMixins\":[{\"$type\":\"AttachModifierToGlobalValueMixin\",\"globalValueKey\":\""
                                + KEY
                                + "\",\"globalValueTarget\":\"Team\",\"modifierNameSteps\":[\"Low\",\"High\"],\"valueSteps\":[0,1,2]}],\"modifiers\":{\"Low\":{\"properties\":{\"Actor_AttackDelta\":10}},\"High\":{\"properties\":{\"Actor_AttackDelta\":30}}}}",
                        AbilityData.class);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 200);
        var ability = new Ability(data, fixture.entity, guest);
        fixture.entity.getInstancedAbilities().add(ability);
        assertEquals(210f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        library.SetTeamServerGlobalValue(10002, KEY, 1.5);
        assertEquals(230f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        library.AddTeamServerGlobalValue(10002, KEY, -2);
        assertEquals(210f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        library.SetTeamServerGlobalValue(10001, KEY, 1);
        assertEquals(210f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        fixture.entity.getInstancedAbilities().remove(ability);
        assertEquals(200f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }

    @Test
    void originalLanlaluoRegionScriptSetsAndClearsOnlyTheEnteringPlayersTeam() throws Exception {
        var group = SceneGroup.of(123);
        library.setCurrentGroup(group);
        var script =
                ScriptLoader.getScript(
                        Path.of("resources/Scripts/Common/V3_0/LanlaluoCommander.lua")
                                .toAbsolutePath()
                                .toString(),
                        true);
        assertNotNull(script);
        var defs = new LuaTable();
        defs.set("curCommander", 4);
        var regions = new LuaTable();
        regions.set(1, LuaValue.valueOf(123));
        defs.set("regionList", regions);
        var config = new LuaTable();
        config.set("io_type", 0);
        var suite = new LuaTable();
        suite.set("triggers", new LuaTable());
        var suites = new LuaTable();
        suites.set(1, suite);
        bindings.put("defs", defs);
        bindings.put("init_config", config);
        bindings.put("suites", suites);
        bindings.put("triggers", new LuaTable());
        ScriptLoader.eval(script, bindings);
        var evt = new LuaTable();
        evt.set("param1", 123);
        var context = library.createEventContext(new ScriptArgs(123, 1).setUid(10002), group);
        var enter = ((LuaValue) bindings.get("action_EnterLanlaluoRegion")).checkfunction();
        var leave = ((LuaValue) bindings.get("action_LeaveLanlaluoRegion")).checkfunction();
        assertEquals(0, enter.call(context, evt).checkint());
        assertEquals(4f, library.GetTeamServerGlobalValue(10002, "SGV_LanlaluoCommander"));
        assertEquals(0f, library.GetTeamServerGlobalValue(10001, "SGV_LanlaluoCommander"));
        evt.set("param1", 999);
        enter.call(context, evt);
        assertEquals(1, fixture.scene.packets.size());
        evt.set("param1", 123);
        assertEquals(0, leave.call(context, evt).checkint());
        assertEquals(0f, library.GetTeamServerGlobalValue(10002, "SGV_LanlaluoCommander"));
        assertEquals(2, fixture.scene.packets.size());
        assertEquals(0f, message(1).getValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "nil",
                "true",
                "'10002'",
                "10002.75",
                "0",
                "-1",
                "2147483648",
                "math.huge",
                "(0/0)"
            })
    void invalidLuaUidsCannotBeCoercedIntoAnotherPlayersCounter(String uid) throws Exception {
        for (String operation : new String[] {"Set", "Add"})
            assertEquals(
                    -1,
                    lua(
                            "return ScriptLib."
                                    + operation
                                    + "TeamServerGlobalValue(ScriptLib,"
                                    + uid
                                    + ",'"
                                    + KEY
                                    + "',1)"));
        assertEquals(
                0,
                lua(
                        "return ScriptLib.GetTeamServerGlobalValue(ScriptLib,"
                                + uid
                                + ",'"
                                + KEY
                                + "')"));
        assertTrue(guest.getTeamManager().getEntity().getGlobalAbilityValues().isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nil", "true", "'0.75'", "math.huge", "-math.huge", "(0/0)", "1e100"})
    void invalidLuaAmountsDoNotChangeExistingStateOrSendPackets(String amount) throws Exception {
        library.SetTeamServerGlobalValue(10002, KEY, 2);
        for (String operation : new String[] {"Set", "Add"})
            assertEquals(
                    -1,
                    lua(
                            "return ScriptLib."
                                    + operation
                                    + "TeamServerGlobalValue(ScriptLib,10002,'"
                                    + KEY
                                    + "',"
                                    + amount
                                    + ")"));
        assertEquals(2f, value());
        assertEquals(1, fixture.scene.packets.size());
    }

    @Test
    void serverWritesRejectNonServerKeysAndJavaNonFiniteValues() throws Exception {
        for (String key :
                new String[] {
                    null, "", " ", "AVATAR_ASTER_SCORE", "sgv_counter", "prefix_SGV_key"
                }) {
            assertEquals(-1, library.SetTeamServerGlobalValue(10002, key, 1));
            assertEquals(-1, library.AddTeamServerGlobalValue(10002, key, 1));
        }
        assertEquals(-1, lua("return ScriptLib.SetTeamServerGlobalValue(ScriptLib,10002,123,1)"));
        assertEquals(0, lua("return ScriptLib.GetTeamServerGlobalValue(ScriptLib,10002,true)"));
        for (double amount :
                new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE
                }) {
            assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, amount));
            assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, amount));
        }
        assertTrue(fixture.scene.packets.isEmpty());
        assertTrue(guest.getTeamManager().getEntity().getGlobalAbilityValues().isEmpty());
    }

    @Test
    void missingTeamsAndInvalidLifetimesReturnFailureInsteadOfReportedSuccess() {
        assertEquals(-1, library.SetTeamServerGlobalValue(98765, KEY, 1));
        assertEquals(-1, library.AddTeamServerGlobalValue(98765, KEY, 1));
        assertEquals(0f, library.GetTeamServerGlobalValue(98765, KEY));
        var team = guest.getTeamManager().getEntity();
        guest.getTeamManager().setEntity(null);
        assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(0f, value());
        guest.getTeamManager().setEntity(team);
        team.invalidateAbilityLifecycle();
        assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(0f, value());
        team.activateAbilityLifecycle();
        assertEquals(0, library.SetTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(1f, value());
    }

    @Test
    void anotherPlayersTeamCannotBeSelectedByChangingTheTeamManagerReference() {
        var hostTeam = fixture.player.getTeamManager().getEntity();
        hostTeam.getGlobalAbilityValues().put(KEY, 5f);
        guest.getTeamManager().setEntity(hostTeam);
        assertEquals(0f, value());
        assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, 9));
        assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(5f, hostTeam.getGlobalAbilityValues().get(KEY));
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void departureAndAStaleSceneRosterCannotRedirectWritesToAnotherScene() {
        library.SetTeamServerGlobalValue(10002, KEY, 2);
        var team = guest.getTeamManager().getEntity();
        guest.setScene(null);
        assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(0f, value());
        guest.setScene(fixture.scene);
        guest.setWorld(null);
        assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, 1));
        guest.setWorld(fixture.scene.getWorld());
        fixture.scene.getPlayers().remove(guest);
        assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(2f, team.getGlobalAbilityValues().get(KEY));
        assertEquals(1, fixture.scene.packets.size());
    }

    @Test
    void callsWithoutSceneContextFailWithoutMutatingTheTeam() {
        library.removeSceneScriptManager();
        assertEquals(-1, library.SetTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(-1, library.AddTeamServerGlobalValue(10002, KEY, 1));
        assertEquals(0f, value());
        library.setCurrentEntity(fixture.entity);
        assertEquals(0, library.SetTeamServerGlobalValue(10002, KEY, 0.5));
        assertEquals(0.5f, value());
    }

    @Test
    void initializationPublishesQueuedAbsoluteSnapshotsInWriteOrder() throws Exception {
        finished(false);
        library.SetTeamServerGlobalValue(10002, KEY, 0.5);
        library.AddTeamServerGlobalValue(10002, KEY, 0.25);
        library.SetTeamServerGlobalValue(10002, KEY, 0);
        assertEquals(0f, value());
        assertTrue(fixture.scene.packets.isEmpty());
        fixture.scene.playerSceneInitialized(guest);
        assertTrue(fixture.scene.packets.isEmpty());
        fixture.scene.playerSceneInitialized(fixture.player);
        assertEquals(3, fixture.scene.packets.size());
        assertEquals(0.5f, message(0).getValue());
        assertEquals(0.75f, message(1).getValue());
        assertEquals(0f, message(2).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"replace", "invalidate", "uid", "id", "leave", "world", "roster"})
    void initializationDoesNotPublishAnObsoleteTeamSnapshot(String change) throws Exception {
        finished(false);
        library.SetTeamServerGlobalValue(10002, KEY, 2);
        var team = guest.getTeamManager().getEntity();
        switch (change) {
            case "replace" -> guest.getTeamManager().setEntity(new EntityTeam(guest));
            case "invalidate" -> team.invalidateAbilityLifecycle();
            case "uid" -> guest.setUid(10003);
            case "id" -> team.setId(team.getId() + 1);
            case "leave" -> guest.setScene(null);
            case "world" -> guest.setWorld(null);
            case "roster" -> fixture.scene.getPlayers().remove(guest);
        }
        fixture.scene.playerSceneInitialized(fixture.player);
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void concurrentServerCounterAdditionsPreserveEveryIncrementAndNotificationOrder()
            throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            var work = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 80; i++)
                work.add(
                        executor.submit(
                                () -> {
                                    library.setSceneScriptManager(fixture.scene.getScriptManager());
                                    try {
                                        return library.AddTeamServerGlobalValue(10002, KEY, 0.25);
                                    } finally {
                                        library.removeSceneScriptManager();
                                    }
                                }));
            for (var result : work) assertEquals(0, result.get(5, TimeUnit.SECONDS));
            assertEquals(20f, value());
            assertEquals(80, fixture.scene.packets.size());
            for (int i = 0; i < 80; i++) assertEquals((i + 1) * 0.25f, message(i).getValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentPlayersCanQueueServerNotificationsTogetherBeforeHostInitialization()
            throws Exception {
        finished(false);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var work = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 120; i++) {
                int uid = i % 2 == 0 ? 10001 : 10002;
                work.add(
                        executor.submit(
                                () -> {
                                    library.setSceneScriptManager(fixture.scene.getScriptManager());
                                    try {
                                        return library.AddTeamServerGlobalValue(uid, KEY, 0.25);
                                    } finally {
                                        library.removeSceneScriptManager();
                                    }
                                }));
            }
            for (var result : work) assertEquals(0, result.get(5, TimeUnit.SECONDS));
            assertEquals(15f, value());
            assertEquals(15f, library.GetTeamServerGlobalValue(10001, KEY));
            assertTrue(fixture.scene.packets.isEmpty());
            fixture.scene.playerSceneInitialized(fixture.player);
            assertEquals(120, fixture.scene.packets.size());
            var expected = new java.util.HashMap<Integer, Float>();
            for (int i = 0; i < 120; i++) {
                var packet = message(i);
                float next = expected.getOrDefault(packet.getEntityId(), 0f) + 0.25f;
                assertEquals(next, packet.getValue());
                expected.put(packet.getEntityId(), next);
            }
            assertEquals(
                    java.util.Map.of(
                            fixture.player.getTeamManager().getEntity().getId(), 15f,
                            guest.getTeamManager().getEntity().getId(), 15f),
                    expected);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aHostInitializationCallbackCanEnqueueMoreWorkWithoutLosingExistingCallbacks()
            throws Exception {
        finished(false);
        var calls = new ArrayList<String>();
        fixture.scene.runWhenHostInitialized(
                () -> {
                    calls.add("first");
                    fixture.scene.runWhenHostInitialized(() -> calls.add("nested"));
                });
        fixture.scene.runWhenHostInitialized(() -> calls.add("second"));
        fixture.scene.playerSceneInitialized(fixture.player);
        assertEquals(java.util.List.of("first", "second", "nested"), calls);
        fixture.scene.playerSceneInitialized(fixture.player);
        assertEquals(3, calls.size());
    }

    @Test
    void initializationFinishingBetweenCheckingAndEnqueuingRunsTheCallbackExactlyOnce() {
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) fixture.scene.getWorld(),
                        fixture.scene.getSceneData()) {
                    private final java.util.concurrent.atomic.AtomicInteger checks =
                            new java.util.concurrent.atomic.AtomicInteger();

                    @Override
                    public boolean isFinishedLoading() {
                        return checks == null || checks.getAndIncrement() != 0;
                    }
                };
        try {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            scene.runWhenHostInitialized(calls::incrementAndGet);
            assertEquals(1, calls.get());
            scene.playerSceneInitialized(fixture.player);
            assertEquals(1, calls.get());
        } finally {
            scene.getScriptManager().onDestroy();
        }
    }

    @Test
    void independentNativeGlobalValueWireFixtureMatchesTheServerBroadcast() throws Exception {
        var output = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(output);
        // Native 0x1487a1b90: key tag 0x38, entity tag 0x50, float value tag 0x5d.
        wire.writeUInt32(7, 0x9a6d20b1);
        wire.writeUInt32(10, 0x0b000042);
        wire.writeFloat(11, 2.25f);
        wire.flush();
        var sample = ServerGlobalValueChangeNotify.parseFrom(output.toByteArray());
        assertEquals(0x9a6d20b1, sample.getKeyHash());
        assertEquals(0x0b000042, sample.getEntityId());
        assertEquals(2.25f, sample.getValue());
        assertEquals(UnknownFieldSet.getDefaultInstance(), sample.getUnknownFields());
        library.SetTeamServerGlobalValue(10002, KEY, 2.25);
        var fields = UnknownFieldSet.parseFrom(fixture.scene.packets.get(0).getData());
        assertEquals(java.util.Set.of(7, 10, 11), fields.asMap().keySet());
        assertEquals(1441, PacketOpcodes.ServerGlobalValueChangeNotify);
        assertEquals(2.25f, message(0).getValue());
    }
}
