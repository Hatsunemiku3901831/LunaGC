package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

class StatueFreeRecoveryTest {
    StatueHealingTest env;
    ConfigContainer.GameOptions previous;

    @BeforeEach
    void setup() throws Exception {
        env = new StatueHealingTest();
        env.setup();
        previous = Grasscutter.getConfig().server.game.gameOptions;
        configure("{freeHealing:true}");
    }

    @AfterEach
    void close() {
        Grasscutter.getConfig().server.game.gameOptions = previous;
        env.close();
    }

    void configure(String recovery) {
        Grasscutter.getConfig().server.game.gameOptions =
                JsonUtils.decode(
                        "{statueRecovery:" + recovery + "}", ConfigContainer.GameOptions.class);
    }

    void heal() {
        env.manager.checkAndHealAvatar(env.env.caster);
    }

    @Test
    void emptyPoolDoesNotRestrictFreeRecoveryAndDisplayIsFilled() {
        env.prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 0);
        heal();
        assertEquals(100, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
    }

    @Test
    void smallConfiguredCapacityDoesNotCapFreeHealing() {
        env.prop(PlayerProperty.PROP_MAX_SPRING_VOLUME, 100);
        env.prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 100);
        heal();
        assertEquals(100, env.hp());
        assertEquals(100, env.manager.getCurrentVolume());
    }

    @Test
    void highHealthRecoveryIsNotCappedBySpringBudget() {
        env.health(300000, 250000);
        heal();
        assertEquals(300000, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
    }

    @Test
    void playerRecoveryPercentageStillControlsTheTarget() {
        env.prop(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 35);
        heal();
        assertEquals(35, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
    }

    @Test
    void disabledAutoRecoveryStillPreventsHealing() {
        env.prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        heal();
        assertEquals(10, env.hp());
        assertEquals(0, env.healCalls);
    }

    @Test
    void freeRefillKeepsCapacityAndPlayerSettings() {
        env.prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 0);
        env.prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        env.prop(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 35);
        env.manager.refillSpringVolume();
        assertEquals(10000, env.manager.getCurrentVolume());
        assertEquals(10000, env.manager.getMaxVolume());
        assertFalse(env.manager.getIsAutoRecoveryEnabled());
        assertEquals(35, env.manager.getAutoRecoveryPercentage());
    }

    @Test
    void partialFreeHealReportsOnlyTheAppliedHealthDelta() throws Exception {
        env.partial = true;
        heal();
        assertEquals(55, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
        var packet =
                env.env.fixture.player.packets.stream()
                        .filter(
                                x ->
                                        x.getOpcode()
                                                == PacketOpcodes.EntityFightPropChangeReasonNotify)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                45, EntityFightPropChangeReasonNotify.parseFrom(packet.getData()).getPropDelta());
    }

    @Test
    void freeHealFailureDoesNotSpendOrLeaveTheReentryGuardStuck() {
        env.throwsHeal = true;
        assertDoesNotThrow(this::heal);
        assertEquals(10, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
        env.throwsHeal = false;
        heal();
        assertEquals(100, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
        assertEquals(2, env.healCalls);
    }

    @Test
    void postHealNotificationFailureDoesNotChargeTheFreePool() {
        env.throwsAfterHeal = true;
        assertDoesNotThrow(this::heal);
        assertEquals(100, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
    }

    @Test
    void reentrantFreeHealAppliesOnlyOnce() {
        env.reenter = true;
        heal();
        assertEquals(100, env.hp());
        assertEquals(10000, env.manager.getCurrentVolume());
        assertEquals(1, env.healCalls);
    }

    @Test
    void livePolicyChangeRestoresBudgetedRecovery() {
        heal();
        assertEquals(10000, env.manager.getCurrentVolume());
        env.health(100, 10);
        configure("{freeHealing:false}");
        heal();
        assertEquals(100, env.hp());
        assertEquals(1000, env.manager.getCurrentVolume());
    }

    @Test
    void omittedOptionsKeepBudgetedRecovery() {
        configure("{}");
        heal();
        assertEquals(100, env.hp());
        assertEquals(1000, env.manager.getCurrentVolume());
    }

    @Test
    void legacyNullOptionsKeepBudgetedRecovery() {
        configure("null");
        heal();
        assertEquals(100, env.hp());
        assertEquals(1000, env.manager.getCurrentVolume());
    }
}
