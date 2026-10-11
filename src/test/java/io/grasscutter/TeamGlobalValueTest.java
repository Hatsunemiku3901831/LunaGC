package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ServerUpdateGlobalValueNotifyOuterClass.ServerUpdateGlobalValueNotify;
import emu.grasscutter.net.proto.UpdateTypeOuterClass.UpdateType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.concurrent.*;

import javax.script.SimpleBindings;

/** Actual LuaJ calls, current team entities and owner-only packets, without a client connection. */
class TeamGlobalValueTest {
    private static final String KEY = "AVATAR_ASTER_SCORE";
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
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(fixture.scene.getScriptManager());
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
    }

    private void ready(AbilityTestFixture.TestPlayer player, int uid) {
        player.setUid(uid);
        player.setScene(fixture.scene);
        player.setWorld(fixture.scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        fixture.scene.getPlayers().add(player);
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

    private float value(int uid) {
        return library.GetTeamAbilityFloatValue(uid, KEY);
    }

    @Test
    void fractionalAsterEnergyCanBeReadAndCompletelySubmittedFromLua() throws Exception {
        assertEquals(
                0,
                lua(
                        "return ScriptLib.AddTeamEntityGlobalFloatValue(ScriptLib,{10002},'"
                                + KEY
                                + "',0.75)"));
        assertEquals(
                0,
                lua(
                        "return ScriptLib.AddTeamEntityGlobalFloatValue(ScriptLib,{10002},'"
                                + KEY
                                + "',0.75)"));
        assertEquals(
                1.5,
                lua("return ScriptLib.GetTeamAbilityFloatValue(ScriptLib,10002,'" + KEY + "')"));
        // The AsterBig submission sequence uses the floating value, then its negative to clear it.
        assertEquals(
                3,
                lua(
                        "local energy=ScriptLib.GetTeamAbilityFloatValue(ScriptLib,10002,'"
                                + KEY
                                + "') local progress=math.ceil(energy*2) "
                                + "assert(ScriptLib.AddTeamEntityGlobalFloatValue(ScriptLib,{10002},'"
                                + KEY
                                + "',-energy)==0) return progress"));
        assertEquals(0f, value(10002));
        assertEquals(0f, value(10001));
        var clear = ServerUpdateGlobalValueNotify.parseFrom(guest.packets.get(2).getData());
        assertEquals(-1.5f, clear.getDelta());
        assertEquals(UpdateType.UpdateType_ADD, clear.getUpdateType());
        assertEquals(0f, guest.getTeamManager().getEntity().getGlobalAbilityValues().get(KEY));
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void setReplacesValuesAndResetsBothSelectedTeamsToZero() throws Exception {
        assertEquals(0, library.AddTeamEntityGlobalFloatValue(new int[] {10001}, KEY, 8.5));
        assertEquals(
                0,
                lua(
                        "return ScriptLib.SetTeamEntityGlobalFloatValue(ScriptLib,{10001,10002},'"
                                + KEY
                                + "',2.25)"));
        assertEquals(2.25f, value(10001));
        assertEquals(2.25f, value(10002));
        assertEquals(
                0,
                lua(
                        "return ScriptLib.SetTeamEntityGlobalFloatValue(ScriptLib,{10001,10002},'"
                                + KEY
                                + "',0)"));
        assertEquals(0f, value(10001));
        assertEquals(0f, value(10002));
        for (var player : new AbilityTestFixture.TestPlayer[] {fixture.player, guest}) {
            var reset =
                    ServerUpdateGlobalValueNotify.parseFrom(
                            player.packets.get(player.packets.size() - 1).getData());
            assertEquals(player.getTeamManager().getEntity().getId(), reset.getEntityId());
            assertEquals(UpdateType.UpdateType_SET, reset.getUpdateType());
            assertEquals(0f, reset.getValue());
            assertEquals(0f, reset.getDelta());
        }
    }

    @Test
    void duplicateRecipientsAreUpdatedOnceAndAbsentPlayersAreSkipped() {
        assertEquals(
                0,
                library.AddTeamEntityGlobalFloatValue(new int[] {10002, 10002, 98765}, KEY, 0.5));
        assertEquals(0.5f, value(10002));
        assertEquals(1, guest.packets.size());
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void anEmptyLuaRecipientListIsAValidNoop() throws Exception {
        assertEquals(
                0,
                lua(
                        "return ScriptLib.AddTeamEntityGlobalFloatValue(ScriptLib,{},'"
                                + KEY
                                + "',1)"));
        assertTrue(guest.packets.isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "nil",
                "10002",
                "{10002.25}",
                "{'10002'}",
                "{10002,false}",
                "{[0]=10002}",
                "{[1]=10002,[3]=10001}",
                "{10002,note=1}",
                "{0}",
                "{2147483648}"
            })
    void malformedUidArraysNeverCoerceToAnotherPlayer(String recipients) throws Exception {
        for (String function : new String[] {"Add", "Set"})
            assertEquals(
                    -1,
                    lua(
                            "return ScriptLib."
                                    + function
                                    + "TeamEntityGlobalFloatValue(ScriptLib,"
                                    + recipients
                                    + ",'"
                                    + KEY
                                    + "',0.75)"));
        assertEquals(0f, value(10001));
        assertEquals(0f, value(10002));
        assertTrue(guest.packets.isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"'0.75'", "true", "nil", "math.huge", "-math.huge", "(0/0)", "1e100"})
    void invalidLuaAmountsLeaveTheExistingValueUntouched(String amount) throws Exception {
        library.SetTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 2);
        for (String function : new String[] {"Add", "Set"})
            assertEquals(
                    -1,
                    lua(
                            "return ScriptLib."
                                    + function
                                    + "TeamEntityGlobalFloatValue(ScriptLib,{10002},'"
                                    + KEY
                                    + "',"
                                    + amount
                                    + ")"));
        assertEquals(2f, value(10002));
        assertEquals(1, guest.packets.size());
    }

    @Test
    void malformedKeysAndJavaRecipientsAreRejectedBeforeAnyPlayerIsChanged() throws Exception {
        assertEquals(
                -1, lua("return ScriptLib.AddTeamEntityGlobalFloatValue(ScriptLib,{10002},123,1)"));
        assertEquals(-1, library.AddTeamEntityGlobalFloatValue(new int[] {10002, -1}, KEY, 1));
        assertEquals(-1, library.AddTeamEntityGlobalFloatValue(null, KEY, 1));
        assertEquals(-1, library.SetTeamEntityGlobalFloatValue(new int[] {10002}, " ", 1));
        assertEquals(-1, library.SetTeamEntityGlobalFloatValue(new int[] {10002}, null, 1));
        assertTrue(guest.packets.isEmpty());
    }

    @Test
    void additionsThatOverflowAreRejectedAndASetCanReplaceAnInvalidOldValue() {
        library.SetTeamEntityGlobalFloatValue(new int[] {10002}, KEY, Float.MAX_VALUE);
        assertEquals(
                -1, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, Float.MAX_VALUE));
        assertEquals(Float.MAX_VALUE, value(10002));
        assertEquals(1, guest.packets.size());
        guest.getTeamManager().getEntity().getGlobalAbilityValues().put(KEY, Float.NaN);
        assertEquals(0f, value(10002));
        assertEquals(-1, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 1));
        assertEquals(0, library.SetTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 0.5));
        assertEquals(0.5f, value(10002));
    }

    @Test
    void unavailableAndInvalidatedTeamsDoNotCrashOrReceiveValues() {
        var team = guest.getTeamManager().getEntity();
        guest.getTeamManager().setEntity(null);
        assertEquals(0, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 1));
        assertEquals(0f, value(10002));
        assertEquals(0, library.GetUidByTeamEntityId(team.getId()));
        guest.getTeamManager().setEntity(team);
        team.invalidateAbilityLifecycle();
        assertEquals(0, library.SetTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 1));
        assertEquals(0f, value(10002));
        assertTrue(guest.packets.isEmpty());
        team.activateAbilityLifecycle();
        assertEquals(0, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 0.25));
        assertEquals(0.25f, value(10002));
        assertEquals(10002, library.GetUidByTeamEntityId(team.getId()));
    }

    @Test
    void playersLeavingTheSceneAreNotSelectedEvenIfItsRosterIsStale() {
        var oldTeam = guest.getTeamManager().getEntity();
        guest.setScene(null);
        assertEquals(0, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 1));
        assertEquals(0f, value(10002));
        assertEquals(0, library.GetUidByTeamEntityId(oldTeam.getId()));
        assertFalse(oldTeam.getGlobalAbilityValues().containsKey(KEY));
        assertTrue(guest.packets.isEmpty());
    }

    @Test
    void abilityEntityContextSelectsItsSceneAndAnUnboundCallFailsCleanly() {
        library.removeSceneScriptManager();
        assertEquals(-1, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 1));
        assertEquals(0f, value(10002));
        assertEquals(0, library.GetUidByTeamEntityId(guest.getTeamManager().getEntity().getId()));
        library.withEntityContext(
                fixture.entity,
                () -> {
                    assertEquals(
                            0, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 0.75));
                    assertEquals(0.75f, value(10002));
                    return null;
                });
        assertTrue(library.getCurrentEntity().isEmpty());
        assertNull(library.getSceneScriptManagerOrNull());
    }

    @Test
    void aClientAbilityConfirmationAndLuaUseTheSameTeamValue() throws Exception {
        library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 0.75);
        var scalar =
                AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setHash(Utils.abilityHash(KEY)))
                        .setFloatValue(1.5f);
        new AbilityManager(guest)
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(guest.getTeamManager().getEntity().getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                                .setAbilityData(scalar.build().toByteString())
                                .build());
        assertEquals(1.5f, value(10002));
        assertEquals(0, library.AddTeamEntityGlobalFloatValue(new int[] {10002}, KEY, 0.25));
        assertEquals(1.75f, value(10002));
    }

    @Test
    void concurrentAddsRetainAllIncrementsAndNotifyTheirOwner() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            var work = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++)
                work.add(
                        executor.submit(
                                () -> {
                                    var local = new ScriptLib();
                                    local.setSceneScriptManager(fixture.scene.getScriptManager());
                                    try {
                                        for (int n = 0; n < 25; n++)
                                            assertEquals(
                                                    0,
                                                    local.AddTeamEntityGlobalFloatValue(
                                                            new int[] {10002}, KEY, 0.25));
                                    } finally {
                                        local.removeSceneScriptManager();
                                    }
                                }));
            for (var item : work) item.get(10, TimeUnit.SECONDS);
            assertEquals(25f, value(10002));
            assertEquals(100, guest.packets.size());
            assertTrue(fixture.player.packets.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }
}
