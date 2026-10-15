package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.PhlogistonMixinCallbacks;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.ability.mixins.PhlogistonCostMixin;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Raw enum exports use integer IDs for the same modes and pools as readable configurations. */
class PhlogistonNumericControlTest {
    PhlogistonMixinInvocationTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMixinInvocationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    EntityVehicle vehicle(float fuel) {
        var vehicle = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(fuel);
        env.owner = vehicle;
        env.env.ability = new Ability(env.env.ability.getData(), vehicle, env.env.fixture.player);
        return vehicle;
    }

    boolean action(String fields) {
        return new ActionChangePhlogiston()
                .execute(
                        env.env.ability,
                        JsonUtils.decode(
                                "{$type:'ChangePhlogiston'," + fields + "}",
                                AbilityModifierAction.class),
                        ByteString.EMPTY,
                        env.owner);
    }

    boolean callback(String fields) {
        env.install("{$type:'ChangePhlogistonMixin'," + fields + "}", true);
        return new PhlogistonMixinCallbacks()
                .apply(
                        env.ability,
                        env.mixin,
                        ByteString.EMPTY,
                        env.owner,
                        env.owner.getInstancedModifiers().get(101));
    }

    @ParameterizedTest
    @CsvSource({
        "costType, 0, 45",
        "costType, 1, 60",
        "costType, 2, 10",
        "DHDDHBBIHEJ, 0, 45",
        "DHDDHBBIHEJ, 1, 60",
        "DHDDHBBIHEJ, 2, 10"
    })
    void actionNumericModeUsesTheSameRatioBasis(String field, int mode, float expected) {
        assertTrue(action("value:0.1,useRatio:true," + field + ":" + mode));
        assertEquals(expected, env.fuel());
    }

    @ParameterizedTest
    @CsvSource({"costType, 0, 45", "costType, 1, 60", "DHDDHBBIHEJ, 0, 45", "DHDDHBBIHEJ, 1, 60"})
    void periodicNumericModeKeepsItsConfiguredInterval(String field, int mode, float expected) {
        assertTrue(callback("speed:0.1,interval:1,useRatio:true," + field + ":" + mode));
        assertEquals(expected, env.fuel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetType", "BBIPIGDBGFJ"})
    void periodicNumericTeamReadsTeamFuelInsteadOfVehicle(String field) {
        var vehicle = vehicle(20);
        assertTrue(callback("speed:0.1,interval:1,useRatio:true," + field + ":2"));
        assertEquals(45, env.fuel());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @ParameterizedTest
    @CsvSource({"0, 20, 18, 50", "0, 0, 0, 45", "1, 0, 0, 50", "3, 0, 0, 50"})
    void numericPoolSelectionPreservesAutomaticFallbackOnly(
            int pool, float initial, float after, float team) {
        var vehicle = vehicle(initial);
        assertTrue(callback("speed:0.1,interval:1,useRatio:true,targetType:" + pool));
        assertEquals(after, vehicle.getCurPhlogiston());
        assertEquals(team, env.fuel());
    }

    @Test
    void ordinaryNumericTeamUsesTheExplicitPool() {
        var vehicle = vehicle(20);
        assertTrue(action("value:0.1,useRatio:true,targetType:2"));
        assertEquals(45, env.fuel());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetType", "BBIPIGDBGFJ"})
    void legacyCostUsesNumericPoolAndCurrentFuelRatio(String field) {
        var vehicle = vehicle(20);
        var config =
                JsonUtils.decode(
                        "{$type:'PhlogistonCostMixin',speed:0.1,useRatio:true," + field + ":2}",
                        AbilityMixinData.class);
        assertTrue(
                new PhlogistonCostMixin()
                        .execute(env.env.ability, config, ByteString.EMPTY, env.owner));
        assertEquals(45, env.fuel());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @Test
    void canonicalNumericControlsOverrideRawValues() {
        var vehicle = vehicle(20);
        assertTrue(
                callback(
                        "speed:0.1,interval:1,useRatio:true,costType:0,DHDDHBBIHEJ:1,targetType:2,BBIPIGDBGFJ:3"));
        assertEquals(45, env.fuel());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @Test
    void canonicalFalseStillDisablesRawRatioForNumericMode() {
        assertTrue(action("value:0.1,costType:0,useRatio:false,JDIMELLBMFO:true"));
        assertEquals(49.9f, env.fuel(), 0.00001f);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "costType:9,DHDDHBBIHEJ:1",
                "costType:1.5,DHDDHBBIHEJ:1",
                "targetType:9",
                "targetType:-1",
                "targetType:2.5"
            })
    void invalidNumericControlCannotFallbackOrMutate(String fields) {
        assertFalse(action("value:0.1,useRatio:true," + fields));
        assertEquals(50, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @Test
    void numericPeriodicSetRemainsUnpaired() {
        assertFalse(callback("speed:0.1,interval:1,costType:2,useRatio:true"));
        assertEquals(50, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @Test
    void legacySubNameIsNotACurrentTimerMode() {
        assertFalse(callback("speed:0.1,interval:1,costType:'Sub',useRatio:true"));
        assertEquals(50, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }
}
