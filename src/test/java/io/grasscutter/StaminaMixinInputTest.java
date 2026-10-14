package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.mixins.CostStaminaMixin;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StaminaMixinInputTest {
    NatsaurusPossessionTest environment;

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        environment.fixture.player.getStaminaManager().setLastCostStaminaTime(0);
    }

    @AfterEach
    void close() {
        environment.close();
    }

    AbilityMixinData mixin(String value) {
        return JsonUtils.decode(
                "{$type:'CostStaminaMixin',costStaminaDelta:" + value + "}",
                AbilityMixinData.class);
    }

    boolean apply(AbilityMixinData data, GameEntity target) {
        return new CostStaminaMixin().execute(environment.ability, data, ByteString.EMPTY, target);
    }

    void unchanged() {
        assertEquals(0, environment.fixture.player.getStaminaManager().getLastCostStaminaTime());
        assertTrue(environment.fixture.player.packets.isEmpty());
    }

    @Test
    void zeroConsumptionDoesNotChangeTheSharedClockOrSendAStaminaUpdate() {
        assertTrue(apply(mixin("0"), environment.avatar));
        unchanged();
    }

    @Test
    void omittedConsumptionDoesNotInventAUnitCost() {
        apply(
                JsonUtils.decode("{$type:'CostStaminaMixin'}", AbilityMixinData.class),
                environment.avatar);
        unchanged();
    }

    @Test
    void dynamicZeroConsumptionStaysZero() {
        environment.ability.getAbilitySpecials().put("ZeroCost", 0);
        assertTrue(apply(mixin("'ZeroCost'"), environment.avatar));
        unchanged();
    }

    @Test
    void malformedExpressionDoesNotUseTheOldOnePointFallback() {
        assertFalse(apply(mixin("[1,0,'DIV']"), environment.avatar));
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void invalidConstantDoesNotTouchStaminaOrItsClock(float value) {
        var data = mixin("0");
        data.costStaminaDelta = new DynamicFloat(value);
        assertFalse(apply(data, environment.avatar));
        unchanged();
    }

    @Test
    void removedOwnerCannotAffectThePlayersStaminaClock() {
        environment.fixture.scene.removeEntity(environment.avatar);
        environment.fixture.player.packets.clear();
        assertFalse(apply(mixin("20"), environment.avatar));
        unchanged();
    }

    @Test
    void removedTargetCannotAffectThePlayersStaminaClock() {
        environment.fixture.scene.removeEntity(environment.fixture.entity);
        environment.fixture.player.packets.clear();
        assertFalse(apply(mixin("20"), environment.fixture.entity));
        unchanged();
    }

    @Test
    void disabledCostCannotAffectThePlayersStaminaClock() {
        var data = mixin("20");
        data.enable = false;
        assertFalse(apply(data, environment.avatar));
        unchanged();
    }

    @Test
    void nullTargetDoesNotResolveToAPlayerImplicitly() {
        assertFalse(apply(mixin("20"), null));
        unchanged();
    }

    @Test
    void negativeResourceDeltaRemainsAcceptedForRecovery() {
        assertTrue(apply(mixin("-100"), environment.avatar));
        assertTrue(environment.fixture.player.getStaminaManager().getLastCostStaminaTime() > 0);
    }

    @Test
    void positiveResourceDeltaRemainsAccepted() {
        assertTrue(apply(mixin("20"), environment.avatar));
        assertTrue(environment.fixture.player.getStaminaManager().getLastCostStaminaTime() > 0);
    }
}
