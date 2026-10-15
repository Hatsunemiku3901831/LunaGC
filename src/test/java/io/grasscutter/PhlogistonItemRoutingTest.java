package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

/** Resource operation 60 must use the same current recipient as an ordinary fuel-ball pickup. */
class PhlogistonItemRoutingTest {
    static List<ItemData> items;
    PhlogistonBallInvocationTest env;

    @BeforeAll
    static void resources() throws Exception {
        items =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> Set.of(2030, 2031).contains(d.getId()))
                        .toList();
        items.forEach(ItemData::onLoad);
        assertEquals(2, items.size());
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonBallInvocationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    boolean use(ItemData item, int count) {
        var params = new UseItemParams(env.env.fixture.player, item.getUseTarget());
        params.usedItemId = item.getId();
        params.count = count;
        assertEquals(1, item.getItemUseActions().size());
        return item.getItemUseActions().get(0).useItem(params);
    }

    @ParameterizedTest
    @ValueSource(floats = {0, 10, 50})
    void bothResourceItemsRefillOnlyTheRiddenSaurian(float initial) {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, initial);
        for (var item : items) {
            vehicle.setCurPhlogiston(initial);
            assertTrue(use(item, 1));
            assertEquals(Math.min(50, initial + 20), vehicle.getCurPhlogiston());
            assertEquals(50, env.fuel());
        }
    }

    @Test
    void multipleItemsScaleTheSameRecipientWithoutOverflow() {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, 10);
        assertTrue(use(items.get(0), 3));
        assertEquals(50, vehicle.getCurPhlogiston());
        assertEquals(50, env.fuel());
    }

    @Test
    void invalidVehiclePoolDoesNotBecomeATeamReward() {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, Float.NaN);
        assertFalse(use(items.get(0), 1));
        assertTrue(Float.isNaN(vehicle.getCurPhlogiston()));
        assertEquals(50, env.fuel());
    }

    @Test
    void unrelatedInvalidTeamPoolDoesNotBlockTheVehicleAward() {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, 10);
        env.env.fixture.player.setPhlogistonValue(Float.NaN);
        assertTrue(use(items.get(0), 1));
        assertEquals(30, vehicle.getCurPhlogiston());
        assertTrue(Float.isNaN(env.fuel()));
    }

    @Test
    void deadRiderStateCannotRedirectTheAwardToTheTeam() throws Exception {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, 10);
        var dead = emu.grasscutter.game.entity.GameEntity.class.getDeclaredField("isDead");
        dead.setAccessible(true);
        dead.setBoolean(vehicle, true);
        assertFalse(use(items.get(0), 1));
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(50, env.fuel());
    }

    @Test
    void disembarkingRestoresTheTeamRecipient() {
        var vehicle = env.ride(NatsaurusPossessionTest.DRILL, 10);
        emu.grasscutter.game.ability.VehicleInteraction.execute(
                env.env.fixture.player.getSession(), vehicle.getId(), 2);
        assertTrue(use(items.get(0), 1));
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, env.fuel());
    }

    @Test
    void nearbyUnriddenVehicleDoesNotRedirectFuel() {
        var vehicle = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(10);
        assertTrue(use(items.get(0), 1));
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, env.fuel());
    }

    @Test
    void ordinaryBoatDoesNotAcquireSaurianFuel() {
        var vehicle = env.ride(NatsaurusPossessionTest.BOAT, 10);
        assertTrue(use(items.get(0), 1));
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, env.fuel());
    }

    @Test
    void ambiguousRiderStateRejectsResourceUse() {
        var first = env.ride(NatsaurusPossessionTest.DRILL, 10);
        var second = env.env.environment.candle(NatsaurusPossessionTest.DRILL);
        second.getVehicleMembers().addAll(first.getVehicleMembers());
        assertFalse(use(items.get(0), 1));
        assertEquals(10, first.getCurPhlogiston());
        assertEquals(50, env.fuel());
    }

    @Test
    void absentSceneDoesNotReportSuccessfulResourceUse() {
        var player = env.env.fixture.player;
        var scene = player.getScene();
        try {
            player.setScene(null);
            assertFalse(use(items.get(0), 1));
            assertEquals(50, env.fuel());
        } finally {
            player.setScene(scene);
        }
    }
}
