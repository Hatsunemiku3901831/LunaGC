package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Current client action type 87220 stores its dynamic amount in CBOMLBFIPJM (+0x60). */
class PhlogistonRawActionControlTest {
    PhlogistonMutationTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    AbilityModifierAction config(String fields) {
        return JsonUtils.decode(
                "{$type:'ChangePhlogiston'," + fields + "}", AbilityModifierAction.class);
    }

    boolean execute(AbilityModifierAction action) {
        return new ActionChangePhlogiston()
                .execute(env.ability, action, ByteString.EMPTY, env.ability.getOwner());
    }

    float fuel() {
        return env.fixture.player.getPhlogistonValue();
    }

    @Test
    void rawAmountAndFlagsRefillAnEmptyTeam() {
        env.fixture.player.setPhlogistonValue(0);
        assertTrue(execute(config("CBOMLBFIPJM:1,DHDDHBBIHEJ:'Add',JDIMELLBMFO:true")));
        assertEquals(100, fuel());
    }

    @Test
    void rawAmountWithoutModeDefaultsToCurrentCost() {
        assertTrue(execute(config("CBOMLBFIPJM:0.1,JDIMELLBMFO:true")));
        assertEquals(45, fuel());
    }

    @Test
    void rawVehicleSetUsesItsOwnCapacity() {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(40);
        env.ability = new Ability(env.ability.getData(), vehicle, env.fixture.player);
        assertTrue(
                execute(
                        config(
                                "CBOMLBFIPJM:0.25,DHDDHBBIHEJ:'Set',JDIMELLBMFO:true,targetType:'Vehicle'")));
        assertEquals(12.5, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"value:0,CBOMLBFIPJM:1", "CBOMLBFIPJM:1,value:0"})
    void canonicalZeroWinsInBothJsonOrders(String fields) {
        assertTrue(execute(config(fields + ",costType:'Add',useRatio:true")));
        assertEquals(50, fuel());
    }

    @Test
    void canonicalModeWinsWhileReadingRawAmount() {
        assertTrue(
                execute(
                        config(
                                "CBOMLBFIPJM:0.1,costType:'Cost',DHDDHBBIHEJ:'Add',JDIMELLBMFO:true")));
        assertEquals(45, fuel());
    }

    @Test
    void canonicalFalseWinsWhileReadingRawAmount() {
        assertTrue(execute(config("CBOMLBFIPJM:1,costType:'Add',useRatio:false,JDIMELLBMFO:true")));
        assertEquals(51, fuel());
    }

    @Test
    void rawExpressionUsesFreshCurrentFuelForEveryCost() {
        var action = config("CBOMLBFIPJM:['fuel',0.1,'MUL'],JDIMELLBMFO:true");
        assertTrue(execute(action));
        assertEquals(40, fuel());
        assertTrue(execute(action));
        assertEquals(32, fuel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,0,'DIV']", "[1,'ADD']", "[1,2,'Unknown']"})
    void invalidRawExpressionIsRejected(String value) {
        assertFalse(execute(config("CBOMLBFIPJM:" + value + ",costType:'Add',useRatio:true")));
        assertEquals(50, fuel());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void invalidCanonicalExpressionCannotFallBackToRawAmount() {
        assertFalse(
                execute(config("value:[1,0,'DIV'],CBOMLBFIPJM:1,costType:'Add',useRatio:true")));
        assertEquals(50, fuel());
    }

    @Test
    void rawZeroSetEmptiesThePool() {
        assertTrue(execute(config("CBOMLBFIPJM:0,DHDDHBBIHEJ:'Set',JDIMELLBMFO:true")));
        assertEquals(0, fuel());
    }

    @Test
    void rawControlSurvivesSerialization() {
        var original = config("CBOMLBFIPJM:0.1,DHDDHBBIHEJ:'Cost',JDIMELLBMFO:true");
        var restored = JsonUtils.decode(JsonUtils.encode(original), AbilityModifierAction.class);
        assertTrue(execute(restored));
        assertEquals(45, fuel());
    }
}
