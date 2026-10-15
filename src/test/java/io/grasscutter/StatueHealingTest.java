package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;

class StatueHealingTest {
    PhlogistonMutationTest env;
    SotSManager manager;
    boolean refuse, throwsHeal, throwsAfterHeal, partial, reenter;
    int healCalls;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        manager =
                new SotSManager(env.fixture.player, () -> {}, rewards -> {}, item -> {}) {
                    @Override
                    protected boolean recoverAvatar(EntityAvatar entity, int amount) {
                        healCalls++;
                        if (refuse) return false;
                        if (throwsHeal)
                            throw new IllegalStateException("Injected statue heal failure");
                        if (reenter) {
                            reenter = false;
                            manager.checkAndHealAvatar(env.caster);
                        }
                        boolean result =
                                env.fixture
                                        .player
                                        .getTeamManager()
                                        .healAvatar(
                                                entity.getAvatar(),
                                                0,
                                                partial ? amount / 2 : amount);
                        if (throwsAfterHeal)
                            throw new IllegalStateException(
                                    "Injected post-heal notification failure");
                        return result;
                    }
                };
        var data = Avatar.class.getDeclaredField("avatarData");
        data.setAccessible(true);
        data.set(
                env.caster.getAvatar(),
                JsonUtils.decode("{id:10000103,name:'StatueTest'}", AvatarData.class));
        prop(PlayerProperty.PROP_MAX_SPRING_VOLUME, 10000);
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 10000);
        prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 1);
        prop(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 100);
        health(100, 10);
        env.fixture.player.setSpringLastUsed(System.currentTimeMillis() / 1000);
        env.fixture.player.packets.clear();
    }

    @AfterEach
    void close() {
        env.close();
    }

    void prop(PlayerProperty property, int value) {
        env.fixture.player.getProperties().put(property.getId(), value);
    }

    void health(float max, float current) {
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, max);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, current);
    }

    float hp() {
        return env.caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    @Test
    void disabledRecoveryDoesNotHealOrSpend() {
        prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        manager.checkAndHealAvatar(env.caster);
        assertEquals(10, hp());
        assertEquals(10000, manager.getCurrentVolume());
        assertEquals(0, healCalls);
    }

    @Test
    void highHealthDoesNotOverflowThePercentageCalculation() {
        health(300000, 250000);
        prop(PlayerProperty.PROP_MAX_SPRING_VOLUME, 8500000);
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 8500000);
        manager.checkAndHealAvatar(env.caster);
        assertEquals(300000, hp());
        assertEquals(3500000, manager.getCurrentVolume());
    }

    @Test
    void refusedHealingDoesNotConsumeSpringVolume() {
        refuse = true;
        manager.checkAndHealAvatar(env.caster);
        assertEquals(10000, manager.getCurrentVolume());
        assertEquals(10, hp());
    }

    @Test
    void thrownHealingReturnsTheReservedVolume() {
        throwsHeal = true;
        assertDoesNotThrow(() -> manager.checkAndHealAvatar(env.caster));
        assertEquals(10000, manager.getCurrentVolume());
        assertEquals(10, hp());
    }

    @Test
    void partialHealingChargesOnlyTheConfirmedHealthDelta() throws Exception {
        partial = true;
        manager.checkAndHealAvatar(env.caster);
        assertEquals(55, hp());
        assertEquals(5500, manager.getCurrentVolume());
        var packet =
                env.fixture.player.packets.stream()
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
    void reentrantHealingCannotChargeOrApplyTwice() {
        reenter = true;
        manager.checkAndHealAvatar(env.caster);
        assertEquals(100, hp());
        assertEquals(1000, manager.getCurrentVolume());
        assertEquals(1, healCalls);
    }

    @Test
    void staleTeamMemberDoesNotConsumeVolume() {
        env.fixture.player.getTeamManager().getActiveTeam().clear();
        manager.checkAndHealAvatar(env.caster);
        assertEquals(10000, manager.getCurrentVolume());
        assertEquals(0, healCalls);
    }

    @Test
    void deadAvatarDoesNotPayForARefusedOrdinaryHeal() {
        health(100, 0);
        manager.checkAndHealAvatar(env.caster);
        assertEquals(10000, manager.getCurrentVolume());
        assertEquals(0, healCalls);
    }

    @Test
    void availableVolumeLimitsTheHealInHundredthsOfHealth() {
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 1250);
        manager.checkAndHealAvatar(env.caster);
        assertEquals(22.5, hp());
        assertEquals(0, manager.getCurrentVolume());
    }

    @Test
    void refillHonoursCurrentCapacityAndRecoverySettings() {
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 1000);
        prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        prop(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 35);
        long before = System.currentTimeMillis() / 1000;
        env.fixture.player.setSpringLastUsed(before - 30);
        manager.refillSpringVolume();
        assertEquals(10000, manager.getMaxVolume());
        assertEquals(1200, manager.getCurrentVolume());
        assertFalse(manager.getIsAutoRecoveryEnabled());
        assertEquals(35, manager.getAutoRecoveryPercentage());
    }

    @Test
    void longOfflineRefillCannotOverflowOrExceedTheCapacity() {
        prop(PlayerProperty.PROP_MAX_SPRING_VOLUME, 8500000);
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 0);
        env.fixture.player.setSpringLastUsed(System.currentTimeMillis() / 1000 - 7200);
        manager.refillSpringVolume();
        assertEquals(8500000, manager.getCurrentVolume());
    }

    @Test
    void subIntervalTimeIsRetainedAcrossRefills() {
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 1000);
        long before = System.currentTimeMillis() / 1000;
        env.fixture.player.setSpringLastUsed(before - 20);
        manager.refillSpringVolume();
        assertEquals(1100, manager.getCurrentVolume());
        assertEquals(before - 5, manager.getLastUsed());
        manager.refillSpringVolume();
        assertEquals(1100, manager.getCurrentVolume());
    }

    @Test
    void backwardClockDoesNotRemoveVolumeOrMintARefill() {
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 1000);
        long future = System.currentTimeMillis() / 1000 + 3600;
        env.fixture.player.setSpringLastUsed(future);
        manager.refillSpringVolume();
        assertEquals(1000, manager.getCurrentVolume());
        assertEquals(future, manager.getLastUsed());
    }

    @Test
    void notificationFailureAfterHealingCannotRefundAnAppliedHeal() {
        throwsAfterHeal = true;
        assertDoesNotThrow(() -> manager.checkAndHealAvatar(env.caster));
        assertEquals(100, hp());
        assertEquals(1000, manager.getCurrentVolume());
    }

    @Test
    void missingLegacySpringStateIsInitializedWithoutChangingPreferences() {
        prop(PlayerProperty.PROP_MAX_SPRING_VOLUME, 0);
        prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 0);
        prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        prop(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 35);
        env.fixture.player.setSpringLastUsed(0);
        manager.refillSpringVolume();
        assertEquals(8500000, manager.getMaxVolume());
        assertEquals(8500000, manager.getCurrentVolume());
        assertTrue(manager.getLastUsed() > 0);
        assertFalse(manager.getIsAutoRecoveryEnabled());
        assertEquals(35, manager.getAutoRecoveryPercentage());
    }
}
