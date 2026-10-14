package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.PhlogistonMixinCallbacks;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rejected pool writes must not be reported as successful resource actions. */
class PhlogistonPoolAvailabilityTest {
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

    boolean action(String config) {
        return new ActionChangePhlogiston()
                .execute(
                        env.env.ability,
                        JsonUtils.decode(
                                "{$type:'ChangePhlogiston'," + config + "}",
                                AbilityModifierAction.class),
                        ByteString.EMPTY,
                        env.owner);
    }

    boolean callback(String config) {
        env.install("{$type:'ChangePhlogistonMixin'," + config + "}", true);
        return new PhlogistonMixinCallbacks()
                .apply(
                        env.ability,
                        env.mixin,
                        ByteString.EMPTY,
                        env.owner,
                        env.owner.getInstancedModifiers().get(101));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:1,costType:'Add'",
                "value:1,costType:'Add',useRatio:true",
                "value:0,costType:'Set'",
                "value:0.25,costType:'Set',useRatio:true",
                "value:1,costType:'Cost'",
                "value:0.1,costType:'Cost',useRatio:true"
            })
    void missingTeamRejectsResourceAction(String config) {
        var team = env.env.fixture.player.getTeamManager().getEntity();
        try {
            env.env.fixture.player.getTeamManager().setEntity(null);
            assertFalse(action(config));
            assertEquals(50, env.fuel());
            assertTrue(env.env.fixture.player.packets.isEmpty());
        } finally {
            env.env.fixture.player.getTeamManager().setEntity(team);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:1,costType:'Add'",
                "value:1,costType:'Add',useRatio:true",
                "value:0.25,costType:'Set',useRatio:true",
                "value:0.1,costType:'Cost',useRatio:true"
            })
    void invalidTeamValueRejectsResourceAction(String config) {
        env.env.fixture.player.setPhlogistonValue(Float.NaN);
        assertFalse(action(config));
        assertTrue(Float.isNaN(env.fuel()));
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:1,costType:'Add'",
                "value:1,costType:'Add',useRatio:true",
                "value:0.25,costType:'Set',useRatio:true",
                "value:0.1,costType:'Cost',useRatio:true"
            })
    void invalidVehicleValueRejectsResourceActionWithoutChargingTeam(String config) {
        var vehicle = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(Float.POSITIVE_INFINITY);
        env.owner = vehicle;
        env.env.ability = new Ability(env.env.ability.getData(), vehicle, env.env.fixture.player);
        assertFalse(action(config));
        assertEquals(50, env.fuel());
        assertEquals(Float.POSITIVE_INFINITY, vehicle.getCurPhlogiston());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "speed:1",
                "speed:0.1,useRatio:true",
                "speed:1,costType:'Add'",
                "speed:0.1,costType:'Add',useRatio:true"
            })
    void missingTeamRejectsPeriodicCallback(String config) {
        var team = env.env.fixture.player.getTeamManager().getEntity();
        try {
            env.env.fixture.player.getTeamManager().setEntity(null);
            assertFalse(callback(config));
            assertEquals(50, env.fuel());
            assertTrue(env.env.fixture.player.packets.isEmpty());
        } finally {
            env.env.fixture.player.getTeamManager().setEntity(team);
        }
    }

    @Test
    void unavailableAutomaticFallbackRejectsCallbackAndKeepsEmptyVehicle() {
        env.owner = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) env.owner).setCurPhlogiston(0);
        env.env.fixture.player.setPhlogistonValue(Float.NaN);
        assertFalse(callback("speed:0.1,useRatio:true"));
        assertTrue(Float.isNaN(env.fuel()));
        assertEquals(0, ((EntityVehicle) env.owner).getCurPhlogiston());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @Test
    void explicitVehicleDoesNotRequireAnUnrelatedHealthyTeamPool() {
        env.owner = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) env.owner).setCurPhlogiston(20);
        env.env.fixture.player.setPhlogistonValue(Float.NaN);
        assertTrue(callback("speed:0.1,interval:1,targetType:'Vehicle',useRatio:true"));
        assertEquals(18, ((EntityVehicle) env.owner).getCurPhlogiston());
        assertTrue(Float.isNaN(env.fuel()));
        assertEquals(1, env.env.fixture.player.packets.size());
    }

    @Test
    void validZeroAndFullPoolStillReportSuccessWithoutNotifications() {
        assertTrue(action("value:0,costType:'Add',useRatio:true"));
        env.env.fixture.player.setPhlogistonValue(100);
        assertTrue(action("value:1,costType:'Add',useRatio:true"));
        assertEquals(100, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
    }

    @Test
    void zeroSetOfValidPoolStillWritesAndSynchronizes() {
        assertTrue(action("value:0,costType:'Set',useRatio:true"));
        assertEquals(0, env.fuel());
        assertEquals(1, env.env.fixture.player.packets.size());
    }

    @Test
    void validInfinitePoolStillAcknowledgesBlockedCosts() {
        var infinite = new InfinitePhlogistonTest();
        infinite.env = env.env;
        infinite.install(
                env.env.fixture.player.getTeamManager().getEntity(),
                "{$type:'InfinitePhlogistonMixin'}");
        assertTrue(action("value:0.1,costType:'Cost',useRatio:true"));
        assertEquals(50, env.fuel());
        assertTrue(env.env.fixture.player.packets.isEmpty());
        env.env.fixture.player.setPhlogistonValue(Float.NaN);
        assertFalse(action("value:0.1,costType:'Cost',useRatio:true"));
    }
}
