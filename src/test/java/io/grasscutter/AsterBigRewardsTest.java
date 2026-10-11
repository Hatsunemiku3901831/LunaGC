package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

class AsterBigRewardsTest {
    private AsterBigTestFixture resources;
    private String token;

    @BeforeEach
    void setup() throws Exception {
        resources = new AsterBigTestFixture();
        token = resources.offer();
    }

    @AfterEach
    void cleanup() {
        resources.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void originalNineWorldLevelDropsAndLaterWorldLevelCap(int level) {
        var entry = GameData.getMpPlayGroupDataMap().get(2);
        assertEquals(413124, entry.getRewardConfigId());
        assertEquals(40, entry.getResinCost());
        var reward = AsterBigRewards.reward(level);
        assertNotNull(reward);
        assertEquals(level, reward.worldLevel());
        assertEquals(210020000 + 10 * Math.min(8, level), reward.dropId());
        assertEquals(200, reward.adventureExp());
        assertEquals(level == 0 ? 20 : level < 5 ? 30 : 40, reward.companionshipExp());
        assertEquals(
                new int[] {14, 20, 26, 32, 38, 44, 50, 50, 50}[Math.min(level, 8)],
                reward.essence());
    }

    @Test
    void archivedGsonFieldsPreserveFrozenPersonalDropsAndSeparateCurrency() {
        var restored = AsterSchedule.progress(resources.host);
        assertTrue(restored.bigReward().rewards().get(10002).worldLevel() == 8);
        assertEquals(Set.of(10001, 10002), restored.bigReward().remaining());
        assertEquals(0, AsterSchedule.progress(resources.guest).bigCredit());
        var effects = new ArrayList<String>();
        assertEquals(
                0,
                take(
                        resources.guest,
                        () -> {
                            effects.add("debit40");
                            return true;
                        },
                        r -> {
                            assertEquals(8, r.worldLevel());
                            assertEquals(50, r.essence());
                            effects.add("grant");
                        },
                        () -> effects.add("host"),
                        () -> effects.add("guest")));
        assertEquals(List.of("host", "guest", "debit40", "grant", "guest", "host"), effects);
        var guest = AsterSchedule.progress(resources.guest);
        assertEquals(50, guest.bigCredit());
        assertEquals(1, guest.bigCollectCount());
        assertEquals(0, guest.credit());
        assertEquals(0, guest.midCollectCount());
        assertTrue(guest.hasBigClaim(token));
        assertFalse(guest.hasPendingReward());
        assertEquals(Set.of(1200132), resources.guest.getWatcherInfoMap().keySet());
        assertEquals(1, resources.guest.getWatcherInfoMap().get(1200132).getCurProgress());
        assertEquals(Set.of(10001), AsterSchedule.progress(resources.host).bigReward().remaining());
        assertNotEquals(
                0,
                take(
                        resources.guest,
                        () -> fail("duplicate debit"),
                        r -> fail("duplicate grant"),
                        () -> fail("duplicate save"),
                        () -> fail("duplicate save")));
    }

    @Test
    void selfClaimUsesOneReservationAndOneReceiptWrite() {
        var effects = new ArrayList<String>();
        assertEquals(
                0,
                take(
                        resources.host,
                        () -> {
                            effects.add("debit");
                            return true;
                        },
                        r -> effects.add("grant"),
                        () -> effects.add("host"),
                        () -> fail("same document")));
        assertEquals(List.of("host", "debit", "grant", "host"), effects);
        var result = AsterSchedule.progress(resources.host);
        assertEquals(14, result.bigCredit());
        assertEquals(1, result.bigCollectCount());
        assertEquals(Set.of(10002), result.bigReward().remaining());
    }

    @Test
    void insufficientResinCancelsBothAcknowledgedReservationsForRetry() {
        assertEquals(
                Retcode.RET_RESIN_NOT_ENOUGH_VALUE,
                take(resources.guest, () -> false, r -> fail("grant"), () -> {}, () -> {}));
        assertTrue(AsterSchedule.progress(resources.host).bigReward().canClaim(10002));
        assertFalse(AsterSchedule.progress(resources.guest).hasPendingReward());
        assertEquals(0, take(resources.guest, () -> true, r -> {}, () -> {}, () -> {}));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void closedChapterWrongRankScheduleTokenLateUidAndUnavailableItemsHaveNoSideEffects(
            int invalid) {
        var claimant = resources.guest;
        int rank = 20;
        long now = resources.now;
        String selected = token;
        if (invalid == 0) now = resources.config.getBeginTime().getTime();
        if (invalid == 1) rank = 19;
        if (invalid == 2) claimant.setScheduleId(claimant.getScheduleId() + 1);
        if (invalid == 3) selected = UUID.randomUUID().toString();
        if (invalid == 4) claimant = resources.data(10003);
        if (invalid == 5) now = AsterSchedule.contentCloseTime(resources.config);
        if (invalid == 6) GameData.getItemDataMap().remove(110);
        assertNotEquals(
                0,
                AsterBigRewards.takeShared(
                        resources.host,
                        claimant,
                        resources.config,
                        rank,
                        now,
                        selected,
                        () -> fail("debit"),
                        r -> fail("grant"),
                        () -> fail("save"),
                        () -> fail("save")));
        assertEquals(0, AsterSchedule.progress(resources.host).bigCredit());
    }

    @Test
    void legacyDocumentsDefaultToNoLargeRewardAndMalformedProofsAreRejected() {
        resources.guest.setDetailJson("{}");
        var legacy = AsterSchedule.progress(resources.guest);
        assertNull(legacy.bigReward());
        assertEquals(0, legacy.bigCredit());
        assertFalse(legacy.hasPendingReward());
        for (String invalid :
                List.of(
                        "{\"bigCredit\":-1}",
                        "{\"bigClaims\":[\"invalid\"]}",
                        "{\"bigReward\":{\"token\":\"invalid\"}}",
                        "{\"pendingBigClaim\":null}")) {
            resources.guest.setDetailJson(invalid);
            assertThrows(
                    IllegalArgumentException.class, () -> AsterSchedule.progress(resources.guest));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void overflowingEssenceOrCollectionCountCannotDebit(int mode) {
        resources.guest.setDetailJson(
                mode == 0 ? "{\"bigCredit\":2147483647}" : "{\"bigCollectCount\":2147483647}");
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                take(
                        resources.guest,
                        () -> fail("debit"),
                        r -> fail("grant"),
                        () -> fail("save"),
                        () -> fail("save")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void everyWriteAndDeliveryFailureKeepsAReplayBarrier(int failure) {
        var hostWrites = new AtomicInteger();
        var guestWrites = new AtomicInteger();
        var debit = new AtomicInteger();
        var grant = new AtomicInteger();
        Runnable hostSave =
                () -> {
                    int n = hostWrites.incrementAndGet();
                    if (failure == 0 && n == 1 || failure == 5 && n == 2 || failure == 6 && n == 2)
                        throw new IllegalStateException("host write");
                };
        Runnable guestSave =
                () -> {
                    int n = guestWrites.incrementAndGet();
                    if (failure == 1 && n == 1 || failure == 4 && n == 2)
                        throw new IllegalStateException("guest write");
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                resources.guest,
                                () -> {
                                    debit.incrementAndGet();
                                    if (failure == 2) throw new IllegalStateException("debit");
                                    return failure != 6;
                                },
                                r -> {
                                    grant.incrementAndGet();
                                    if (failure == 3) throw new IllegalStateException("grant");
                                },
                                hostSave,
                                guestSave));
        if (failure == 0) {
            assertEquals(0, debit.get());
            assertEquals(0, grant.get());
            assertTrue(AsterSchedule.progress(resources.host).bigReward().canClaim(10002));
        } else {
            var host = AsterSchedule.progress(resources.host);
            var guest = AsterSchedule.progress(resources.guest);
            assertEquals(Set.of(10002), host.bigReward().reserved());
            assertThrows(
                    IllegalStateException.class,
                    () -> host.openBigReward(Map.of(10001, AsterBigRewards.reward(0))));
            if (failure == 5) {
                assertTrue(guest.hasBigClaim(token));
                assertEquals(50, guest.bigCredit());
            } else {
                assertTrue(guest.hasPendingReward());
                assertEquals(0, guest.bigCredit());
            }
            assertNotEquals(
                    0,
                    take(
                            resources.guest,
                            () -> fail("repeat debit"),
                            r -> fail("repeat grant"),
                            () -> fail("repeat save"),
                            () -> fail("repeat save")));
        }
        assertEquals(failure < 2 ? 0 : 1, debit.get());
        assertEquals(failure < 3 || failure == 6 ? 0 : 1, grant.get());
    }

    @Test
    void twoConcurrentClaimsGrantOnceWithoutBlockingTheHostsOwnReward() throws Exception {
        var grants = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(3);
        try {
            Callable<Integer> call =
                    () ->
                            take(
                                    resources.guest,
                                    () -> true,
                                    r -> grants.incrementAndGet(),
                                    () -> {},
                                    () -> {});
            var a = pool.submit(call);
            var b = pool.submit(call);
            assertEquals(
                    1,
                    (a.get(5, TimeUnit.SECONDS) == 0 ? 1 : 0)
                            + (b.get(5, TimeUnit.SECONDS) == 0 ? 1 : 0));
            assertEquals(
                    0,
                    take(
                            resources.host,
                            () -> true,
                            r -> grants.incrementAndGet(),
                            () -> {},
                            () -> fail()));
            assertEquals(2, grants.get());
            assertTrue(AsterSchedule.progress(resources.host).bigReward().remaining().isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }

    private int take(
            PlayerActivityData claimant,
            BooleanSupplier debit,
            Consumer<AsterBigRewards.Reward> grant,
            Runnable saveHost,
            Runnable saveGuest) {
        return AsterBigRewards.takeShared(
                resources.host,
                claimant,
                resources.config,
                20,
                resources.now,
                token,
                debit,
                grant,
                saveHost,
                saveGuest);
    }
}
