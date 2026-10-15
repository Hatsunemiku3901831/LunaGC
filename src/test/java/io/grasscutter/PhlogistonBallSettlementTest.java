package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.ability.PhlogistonBallPickup;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.world.Position;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The scene removes an item before calling onRemoved or broadcasting its disappearance. */
class PhlogistonBallSettlementTest {
    PhlogistonBallInvocationTest env;
    int removalCalls;
    boolean reenter;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonBallInvocationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    void failingBall() {
        env.setupPickup();
        var original = env.ball;
        env.env.fixture.scene.getEntities().remove(original.getId());
        env.ball =
                new EntityItem(
                        env.env.fixture.scene,
                        env.env.fixture.player,
                        original.getItemData(),
                        new Position(1, 2, 3),
                        1,
                        true) {
                    @Override
                    public void onRemoved() {
                        removalCalls++;
                        if (reenter) {
                            assertFalse(PhlogistonBallPickup.pickup(env.env.fixture.player, this));
                        }
                        throw new IllegalStateException("injected post-removal failure");
                    }
                };
        env.env.fixture.scene.getEntities().put(env.ball.getId(), env.ball);
    }

    void claimOnce() {
        assertTrue(
                assertDoesNotThrow(
                        () -> PhlogistonBallPickup.pickup(env.env.fixture.player, env.ball)));
        assertNull(env.env.fixture.scene.getEntities().get(env.ball.getId()));
        assertFalse(PhlogistonBallPickup.pickup(env.env.fixture.player, env.ball));
        assertEquals(1, removalCalls);
    }

    @Test
    void removedHumanBallStillAwardsFuelWhenCleanupThrows() {
        failingBall();
        claimOnce();
        assertEquals(70, env.fuel());
    }

    @ParameterizedTest
    @ValueSource(floats = {0, 10, 50})
    void removedRiddenBallSettlesOnlyItsSelectedPool(float initial) {
        EntityVehicle vehicle = env.ride(NatsaurusPossessionTest.DRILL, initial);
        failingBall();
        claimOnce();
        assertEquals(Math.min(50, initial + 20), vehicle.getCurPhlogiston());
        assertEquals(50, env.fuel());
    }

    @Test
    void cleanupReentryCannotClaimBeforeTheOuterAward() {
        reenter = true;
        failingBall();
        claimOnce();
        assertEquals(70, env.fuel());
    }

    @Test
    void abilityCallbackAlsoFinishesAnAlreadyRemovedClaim() {
        failingBall();
        env.pickup();
        env.pickup();
        assertEquals(70, env.fuel());
        assertNull(env.env.fixture.scene.getEntities().get(env.ball.getId()));
        assertEquals(1, removalCalls);
    }
}
