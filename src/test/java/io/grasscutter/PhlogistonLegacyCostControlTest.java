package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.mixins.PhlogistonCostMixin;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

/** Explicit pool and ratio settings must work through the legacy cost entry as well. */
class PhlogistonLegacyCostControlTest {
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

    boolean cost(String config) {
        return new PhlogistonCostMixin()
                .execute(
                        env.ability,
                        JsonUtils.decode(
                                "{$type:'PhlogistonCostMixin'," + config + "}",
                                AbilityMixinData.class),
                        ByteString.EMPTY,
                        env.ability.getOwner());
    }

    EntityVehicle vehicle(float fuel) {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(fuel);
        env.ability = new Ability(env.ability.getData(), vehicle, env.fixture.player);
        env.fixture.player.packets.clear();
        return vehicle;
    }

    @Test
    void ratioUsesFreshCurrentFuelOnEveryExecution() {
        assertTrue(cost("speed:0.1,useRatio:true"));
        assertEquals(45, env.fixture.player.getPhlogistonValue());
        assertTrue(cost("speed:0.1,useRatio:true"));
        assertEquals(40.5, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void rawRatioFlagIsRecognized() {
        assertTrue(cost("speed:0.1,JDIMELLBMFO:true"));
        assertEquals(45, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void canonicalFalseOverridesRawRatioTrue() {
        assertTrue(cost("speed:1,useRatio:false,JDIMELLBMFO:true"));
        assertEquals(49, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void explicitTeamUsesTeamFuelWhileOwnerIsVehicle() {
        var vehicle = vehicle(20);
        assertTrue(cost("speed:0.1,useRatio:true,targetType:'Team'"));
        assertEquals(45, env.fixture.player.getPhlogistonValue());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @Test
    void rawTeamTargetAlsoControlsAbsoluteCosts() {
        var vehicle = vehicle(20);
        assertTrue(cost("speed:2,BBIPIGDBGFJ:'Team'"));
        assertEquals(48, env.fixture.player.getPhlogistonValue());
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @Test
    void canonicalTargetOverridesRawTarget() {
        var vehicle = vehicle(20);
        assertTrue(cost("speed:0.1,useRatio:true,targetType:'Vehicle',BBIPIGDBGFJ:'Team'"));
        assertEquals(18, vehicle.getCurPhlogiston());
        assertEquals(50, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void noTargetRetainsLegacyOwnerPoolEvenWhenEmpty() {
        var vehicle = vehicle(0);
        assertTrue(cost("speed:0.1,useRatio:true"));
        assertEquals(0, vehicle.getCurPhlogiston());
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void explicitAutoCanUseTeamAfterVehicleExhaustion() {
        var vehicle = vehicle(0);
        assertTrue(cost("speed:0.1,useRatio:true,targetType:'Auto'"));
        assertEquals(0, vehicle.getCurPhlogiston());
        assertEquals(45, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void unavailableExplicitVehicleCannotChargeTeam() {
        assertFalse(cost("speed:1,targetType:'Vehicle'"));
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void unknownTargetIsRejected() {
        assertFalse(cost("speed:1,targetType:'Unknown'"));
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void explicitTeamDoesNotReadInvalidUnselectedVehicleFuel() {
        var vehicle = vehicle(Float.NaN);
        assertTrue(cost("speed:0.1,useRatio:true,targetType:'Team'"));
        assertEquals(45, env.fixture.player.getPhlogistonValue());
        assertTrue(Float.isNaN(vehicle.getCurPhlogiston()));
    }
}
