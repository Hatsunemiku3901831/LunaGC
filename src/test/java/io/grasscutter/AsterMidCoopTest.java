package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoNotifyOuterClass.GadgetGeneralRewardInfoNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoOuterClass.GadgetGeneralRewardInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

class AsterMidCoopTest {
    private AsterMidTestFixture fixture;
    private PlayerActivityData guest;
    private int camp;
    private String token;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterMidTestFixture();
        guest = guest(10002);
        var progress = AsterSchedule.progress(fixture.data);
        camp = emu.grasscutter.data.GameData.getAsterMidDataMap().get(1).getCampVec().get(0);
        assertTrue(progress.offerMidReward(camp, 1, Set.of(10001, 10002)));
        fixture.data.setDetail(progress);
        token = progress.midReward(camp).token();
    }

    @AfterEach
    void restore() {
        fixture.close();
    }

    private PlayerActivityData guest(int uid) {
        var watchers = new HashMap<Integer, PlayerActivityData.WatcherInfo>();
        fixture.data
                .getWatcherInfoMap()
                .forEach(
                        (id, value) ->
                                watchers.put(
                                        id,
                                        PlayerActivityData.WatcherInfo.of()
                                                .watcherId(id)
                                                .totalProgress(value.getTotalProgress())
                                                .build()));
        var data =
                PlayerActivityData.of()
                        .uid(uid)
                        .activityId(2001)
                        .scheduleId(fixture.config.getScheduleId())
                        .watcherInfoMap(watchers)
                        .detail("{}")
                        .build();
        var progress = AsterSchedule.progress(data);
        progress.startMidBatch(2);
        data.setDetail(progress);
        return data;
    }

    private AsterMidRewardState.View offer() {
        return AsterSchedule.progress(fixture.data).midReward(camp);
    }

    private int take(
            PlayerActivityData data,
            long now,
            BooleanSupplier debit,
            Consumer<AsterMidRewards.Reward> grant,
            Runnable saveHost,
            Runnable saveGuest) {
        return AsterMidRewards.takeShared(
                fixture.data,
                data,
                fixture.config,
                20,
                now,
                camp,
                token,
                debit,
                grant,
                saveHost,
                saveGuest);
    }

    @Test
    void guestsHaveIndependentResinCurrencyMissionsAndTheirOwnCampBatch() {
        assertEquals(
                0,
                take(
                        guest,
                        fixture.now,
                        () -> true,
                        r -> assertEquals(15, r.energy()),
                        () -> {},
                        () -> {}));
        var personal = AsterSchedule.progress(guest);
        assertEquals(2, personal.midRefreshId());
        assertEquals(15, personal.credit());
        assertEquals(1, personal.midCollectCount());
        assertTrue(personal.midFinishedCamps().isEmpty());
        assertEquals(1, guest.getWatcherInfoMap().get(1200110).getCurProgress());
        assertEquals(0, AsterSchedule.progress(fixture.data).midCollectCount());
        assertTrue(AsterSchedule.progress(fixture.data).midCampAvailable(camp));
        assertEquals(Set.of(10001, 10002), offer().qualified());
        assertEquals(Set.of(10001), offer().remaining());
        assertEquals(fixture.now / 1000 + 10, offer().deadTime());
        assertNotEquals(
                0,
                take(
                        guest,
                        fixture.now + 1000,
                        () -> fail("duplicate debit"),
                        r -> fail("duplicate grant"),
                        () -> fail(),
                        () -> fail()));
        assertEquals(
                0,
                take(
                        fixture.data,
                        fixture.now + 8000,
                        () -> true,
                        r -> {},
                        () -> {},
                        () -> fail("host must be saved once per phase")));
        var host = AsterSchedule.progress(fixture.data);
        assertEquals(15, host.credit());
        assertEquals(1, host.midCollectCount());
        assertTrue(host.midFinishedCamps().isEmpty(), "A claim itself does not destroy the camp");
        assertTrue(host.finishMidReward(camp, fixture.now + 8000, true));
        fixture.data.setDetail(host);
        assertTrue(AsterSchedule.progress(fixture.data).midFinishedCamps().contains(camp));
    }

    @Test
    void previewAndInsufficientResinDoNotStartTheTimerAndExpiryIsPersistent() {
        assertEquals(0, offer().deadTime());
        assertEquals(
                Retcode.RET_RESIN_NOT_ENOUGH_VALUE,
                take(guest, fixture.now, () -> false, r -> fail(), () -> {}, () -> {}));
        assertEquals(0, offer().deadTime());
        assertTrue(offer().reserved().isEmpty());
        assertEquals(0, AsterSchedule.progress(guest).pendingMidClaim());
        assertEquals(0, take(guest, fixture.now + 60_000, () -> true, r -> {}, () -> {}, () -> {}));
        long deadline = offer().deadTime() * 1000;
        fixture.data.setDetailJson(fixture.data.getDetail());
        assertTrue(offer().canClaim(10001, deadline - 1));
        assertFalse(offer().canClaim(10001, deadline));
        assertNotEquals(
                0,
                take(
                        fixture.data,
                        deadline,
                        () -> fail(),
                        r -> fail(),
                        () -> fail(),
                        () -> fail()));
        var host = AsterSchedule.progress(fixture.data);
        assertTrue(host.finishMidReward(camp, deadline, true));
        assertEquals(0, host.midCollectCount());
        assertEquals(1, AsterSchedule.progress(guest).midCollectCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void uncertainWritesAndPartialRewardsKeepReservationsAndNeverReplay(int failureStage) {
        var hostSaves = new AtomicInteger();
        var guestSaves = new AtomicInteger();
        var debits = new AtomicInteger();
        var grants = new AtomicInteger();
        Runnable saveHost =
                () -> {
                    int n = hostSaves.incrementAndGet();
                    if (failureStage == 0 && n == 1 || failureStage == 4 && n == 2)
                        throw new IllegalStateException("uncertain host write");
                };
        Runnable saveGuest =
                () -> {
                    int n = guestSaves.incrementAndGet();
                    if (failureStage == 1 && n == 1 || failureStage == 3 && n == 2)
                        throw new IllegalStateException("uncertain guest write");
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                guest,
                                fixture.now,
                                () -> {
                                    debits.incrementAndGet();
                                    return true;
                                },
                                r -> {
                                    grants.incrementAndGet();
                                    if (failureStage == 2)
                                        throw new IllegalStateException("partial grant");
                                },
                                saveHost,
                                saveGuest));
        assertEquals(failureStage < 2 ? 0 : 1, debits.get());
        assertEquals(failureStage < 2 ? 0 : 1, grants.get());
        assertEquals(failureStage == 0 ? Set.of() : Set.of(10002), offer().reserved());
        assertEquals(
                failureStage == 0 || failureStage == 4 ? 0 : camp,
                AsterSchedule.progress(guest).pendingMidClaim());
        assertEquals(failureStage == 4 ? 1 : 0, AsterSchedule.progress(guest).midCollectCount());
        // Both documents are decoded again, exactly as after a restart.
        fixture.data.setDetail(JsonUtils.decode(fixture.data.getDetail(), AsterProgress.class));
        guest.setDetail(JsonUtils.decode(guest.getDetail(), AsterProgress.class));
        if (failureStage != 0) {
            assertNotEquals(
                    0,
                    take(
                            guest,
                            fixture.now + 1000,
                            () -> fail("replayed debit"),
                            r -> fail("replayed grant"),
                            () -> fail(),
                            () -> fail()));
            assertFalse(
                    AsterSchedule.progress(fixture.data)
                            .finishMidReward(camp, fixture.now + 30_000, true));
        }
    }

    @Test
    void lateJoinersOldSchedulesAndWrongTokensNeverPayOrProgress() {
        assertNotEquals(
                0,
                take(
                        guest(10003),
                        fixture.now,
                        () -> fail(),
                        r -> fail(),
                        () -> fail(),
                        () -> fail()));
        guest.setScheduleId(guest.getScheduleId() + 1);
        assertEquals(
                Retcode.RET_ACTIVITY_CLOSE_VALUE,
                take(guest, fixture.now, () -> fail(), r -> fail(), () -> fail(), () -> fail()));
        guest.setScheduleId(fixture.config.getScheduleId());
        token = UUID.randomUUID().toString();
        assertNotEquals(
                0, take(guest, fixture.now, () -> fail(), r -> fail(), () -> fail(), () -> fail()));
        token = offer().token();
        var personal = AsterSchedule.progress(guest);
        personal.beginWatcherReward(1200110);
        guest.setDetail(personal);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                take(guest, fixture.now, () -> fail(), r -> fail(), () -> fail(), () -> fail()));
    }

    @Test
    void anUnstartedGuestDocumentCanClaimAndMalformedPersistedStateIsRejected() {
        guest.setDetailJson("{}");
        assertEquals(0, take(guest, fixture.now, () -> true, r -> {}, () -> {}, () -> {}));
        assertEquals(0, AsterSchedule.progress(guest).midRefreshId());
        guest.setDetailJson(guest.getDetail().replace(token, "invalid-token"));
        assertThrows(IllegalArgumentException.class, () -> AsterSchedule.progress(guest));
    }

    @Test
    void legacyOffersMigrateWithoutAddingLatePlayersOrDroppingUncertainClaims() {
        var progress = AsterSchedule.progress(fixture.data);
        int other = emu.grasscutter.data.GameData.getAsterMidDataMap().get(1).getCampVec().get(1);
        assertTrue(progress.offerMidReward(other, 1));
        progress.beginMidClaim(other);
        assertFalse(progress.upgradeMidReward(other, 10001));
        progress.cancelMidClaim();
        assertTrue(progress.upgradeMidReward(other, 10001));
        assertEquals(Set.of(10001), progress.midReward(other).qualified());
        fixture.data.setDetail(progress);
        var restored = AsterSchedule.progress(fixture.data);
        assertFalse(restored.upgradeMidReward(other, 10002));
        assertEquals(progress.midReward(other), restored.midReward(other));
        assertThrows(IllegalStateException.class, () -> restored.beginMidClaim(other));
    }

    @Test
    void newBatchReusesCampWithANewRewardTokenAndKeepsPersonalReceipts() {
        String old = token;
        var host = AsterSchedule.progress(fixture.data);
        for (int id : emu.grasscutter.data.GameData.getAsterMidDataMap().get(1).getCampVec()) {
            if (id != camp) assertTrue(host.offerMidReward(id, 1, Set.of(10002)));
            camp = id;
            token = host.midReward(id).token();
            fixture.data.setDetail(host);
            assertEquals(0, take(guest, fixture.now, () -> true, r -> {}, () -> {}, () -> {}));
            host = AsterSchedule.progress(fixture.data);
            assertTrue(host.finishMidReward(id, fixture.now + 10_000, true));
        }
        assertTrue(host.midBatchFinished());
        host.startMidBatch(1);
        camp = emu.grasscutter.data.GameData.getAsterMidDataMap().get(1).getCampVec().get(0);
        assertTrue(host.offerMidReward(camp, 1, Set.of(10002)));
        fixture.data.setDetail(host);
        token = host.midReward(camp).token();
        assertNotEquals(old, token);
        assertEquals(0, take(guest, fixture.now + 11_000, () -> true, r -> {}, () -> {}, () -> {}));
        assertEquals(5, AsterSchedule.progress(guest).midCollectCount());
        assertTrue(guest.getWatcherInfoMap().get(1200110).isFinished());
        token = old;
        assertNotEquals(
                0,
                take(
                        guest,
                        fixture.now + 12_000,
                        () -> fail(),
                        r -> fail(),
                        () -> fail(),
                        () -> fail()));
    }

    @Test
    void racingAndReentrantClaimsDebitOnlyOnce() throws Exception {
        var debits = new AtomicInteger();
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> claim =
                    () -> {
                        start.await();
                        return take(
                                guest,
                                fixture.now,
                                () -> {
                                    debits.incrementAndGet();
                                    return true;
                                },
                                r ->
                                        assertNotEquals(
                                                0,
                                                take(
                                                        guest,
                                                        fixture.now,
                                                        () -> fail(),
                                                        x -> fail(),
                                                        () -> fail(),
                                                        () -> fail())),
                                () -> {},
                                () -> {});
                    };
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            start.countDown();
            assertEquals(
                    1,
                    List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                            .stream()
                            .filter(code -> code == 0)
                            .count());
            assertEquals(1, debits.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void currentClientRewardUpdateUsesEntityTenAndRewardThirteen() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(10, 12345);
        wire.writeMessage(
                13,
                GadgetGeneralRewardInfo.newBuilder()
                        .setResin(20)
                        .setDeadTime(1800000000)
                        .addQualifyUidList(10001)
                        .addQualifyUidList(10002)
                        .addRemainUidList(10001)
                        .build());
        wire.flush();
        var parsed = GadgetGeneralRewardInfoNotify.parseFrom(bytes.toByteArray());
        assertEquals(2019, PacketOpcodes.GadgetGeneralRewardInfoNotify);
        assertEquals(12345, parsed.getEntityId());
        assertEquals(List.of(10001), parsed.getGeneralRewardInfo().getRemainUidListList());
        assertEquals(List.of(10001, 10002), parsed.getGeneralRewardInfo().getQualifyUidListList());
        assertEquals(1800000000, parsed.getGeneralRewardInfo().getDeadTime());
    }
}
