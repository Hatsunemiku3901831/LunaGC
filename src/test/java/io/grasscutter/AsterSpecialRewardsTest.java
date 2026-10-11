package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class AsterSpecialRewardsTest {
    @Test
    void confirmedSpecialReceiptIsIncludedInTheNativeActivityDetail() throws Exception {
        try (var fixture = new AsterMidTestFixture()) {
            fixture.data.setDetailJson("{\"specialRewardTaken\":true}");
            var handler = new AsterActivityHandler();
            handler.setActivityConfigItem(fixture.config);
            var info = ActivityInfo.newBuilder();
            handler.onProtoBuild(fixture.data, info);
            assertTrue(info.getAsterInfo().getIsSpecialRewardTaken());
        }
    }

    @Test
    void persistedUnconfirmedSpecialGrantBlocksOtherEventRewards() throws Exception {
        try (var fixture = new AsterMidTestFixture()) {
            fixture.data.setDetailJson("{\"pendingSpecialReward\":true}");
            var progress = AsterSchedule.progress(fixture.data);
            assertTrue(progress.hasPendingReward());
            assertThrows(IllegalStateException.class, () -> progress.beginWatcherReward(1200101));
        }
    }

    @Test
    void aReceiptCannotBeBothConfirmedAndReserved() throws Exception {
        try (var fixture = new AsterMidTestFixture()) {
            fixture.data.setDetailJson(
                    "{\"specialRewardTaken\":true,\"pendingSpecialReward\":true}");
            assertThrows(
                    IllegalArgumentException.class, () -> AsterSchedule.progress(fixture.data));
        }
    }

    static void eligible(PlayerActivityData data) {
        var json = JsonParser.parseString(data.getDetail()).getAsJsonObject();
        json.addProperty("midCollectCount", 14);
        json.addProperty("bigCollectCount", 7);
        data.setDetailJson(json.toString());
        for (int id : List.of(1200130, 1200131, 1200132)) {
            int total = GameData.getActivityWatcherDataMap().get(id).getProgress();
            data.getWatcherInfoMap()
                    .put(
                            id,
                            PlayerActivityData.WatcherInfo.of()
                                    .watcherId(id)
                                    .totalProgress(total)
                                    .curProgress(total)
                                    .build());
        }
    }

    private static int take(
            AsterMidTestFixture fixture,
            java.util.function.IntUnaryOperator validate,
            java.util.function.IntConsumer grant,
            Runnable save) {
        return AsterSpecialRewards.take(
                fixture.data, fixture.config, 20, fixture.now, validate, grant, save);
    }

    @Test
    void rewardIsGrantedOnlyAfterReservationAndConfirmedOnlyAfterTheSecondSave() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            var saves = new AtomicInteger();
            var grants = new AtomicInteger();
            assertEquals(
                    0,
                    take(
                            f,
                            reward -> {
                                assertEquals(3000000, reward);
                                assertEquals(0, saves.get());
                                return 0;
                            },
                            reward -> {
                                assertEquals(3000000, reward);
                                assertEquals(1, saves.get());
                                assertTrue(AsterSchedule.progress(f.data).pendingSpecialReward());
                                grants.incrementAndGet();
                            },
                            () -> {
                                var state = AsterSchedule.progress(f.data);
                                assertEquals(
                                        saves.incrementAndGet() == 2, state.specialRewardTaken());
                                assertEquals(saves.get() == 1, state.pendingSpecialReward());
                            }));
            assertEquals(2, saves.get());
            assertEquals(1, grants.get());
            assertEquals(
                    Retcode.RET_ASTER_SPECIAL_REWARD_HAS_TAKEN_VALUE,
                    take(
                            f,
                            id -> fail("duplicate preflight"),
                            id -> fail("duplicate grant"),
                            () -> fail("duplicate save")));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1200130, 1200131, 1200132})
    void everyOriginalConditionIsRequiredIncludingTheStoryQuest(int id) throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            f.data.getWatcherInfoMap().get(id).setCurProgress(0);
            String before = f.data.getDetail();
            assertEquals(
                    Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE,
                    take(
                            f,
                            r -> fail("condition bypass"),
                            r -> fail("condition bypass"),
                            () -> fail("condition bypass")));
            assertEquals(before, f.data.getDetail());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void missingOrMalformedConditionsCannotProduceAFreeReward(int invalid) throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            var condition = f.data.getWatcherInfoMap().get(1200131);
            if (invalid == 0) f.data.getWatcherInfoMap().remove(1200131);
            if (invalid == 1) condition.setWatcherId(1200132);
            if (invalid == 2) condition.setTotalProgress(0);
            if (invalid == 3) condition.setCurProgress(15);
            assertEquals(
                    invalid == 0
                            ? Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE
                            : Retcode.RET_SVR_ERROR_VALUE,
                    take(
                            f,
                            r -> fail("invalid condition"),
                            r -> fail("invalid condition"),
                            () -> fail("invalid condition")));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void closedDisabledAndMismatchedSchedulesCannotClaim(int invalid) throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            long now = f.now;
            if (invalid == 0) now = f.config.getBeginTime().getTime() - 1;
            if (invalid == 1) now = f.config.getEndTime().getTime();
            if (invalid == 2) f.config.setDisabled(true);
            if (invalid == 3) f.data.setScheduleId(f.data.getScheduleId() + 1);
            if (invalid == 4) f.data.setActivityId(5001);
            assertEquals(
                    Retcode.RET_ACTIVITY_CLOSE_VALUE,
                    AsterSpecialRewards.status(f.data, f.config, 20, now));
        }
    }

    @Test
    void rankGateAndConfirmedPersonalCountsCannotBeBypassedWithWatcherValues() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            assertEquals(
                    Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE,
                    AsterSpecialRewards.status(f.data, f.config, 19, f.now));
            var json = JsonParser.parseString(f.data.getDetail()).getAsJsonObject();
            json.addProperty("midCollectCount", 13);
            f.data.setDetailJson(json.toString());
            assertEquals(
                    Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE,
                    AsterSpecialRewards.status(f.data, f.config, 20, f.now));
            json.addProperty("midCollectCount", 14);
            json.addProperty("bigCollectCount", 6);
            f.data.setDetailJson(json.toString());
            assertEquals(
                    Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE,
                    AsterSpecialRewards.status(f.data, f.config, 20, f.now));
        }
    }

    @Test
    void specialRewardRemainsClaimableDuringTheOriginalRedemptionPeriod() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            long now = AsterSchedule.contentCloseTime(f.config) + 1000;
            assertTrue(f.config.isActiveAt(now));
            assertEquals(0, AsterSpecialRewards.status(f.data, f.config, 20, now));
        }
    }

    @Test
    void missingOriginalConditionMetadataFailsBeforeAnyReservation() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            GameData.getActivityWatcherDataMap().remove(1200130);
            assertEquals(
                    Retcode.RET_SVR_ERROR_VALUE,
                    take(
                            f,
                            r -> fail("missing resource"),
                            r -> fail("missing resource"),
                            () -> fail("missing resource")));
        }
    }

    @Test
    void deliveryPreflightFailureLeavesTheRewardRetryableWithoutSaving() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            String before = f.data.getDetail();
            assertEquals(
                    Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                    take(
                            f,
                            r -> Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                            r -> fail("full inventory"),
                            () -> fail("full inventory")));
            assertEquals(before, f.data.getDetail());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void failuresBeforeAndAfterGrantPreserveTheAppropriateReservation(int failure)
            throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            String original = f.data.getDetail();
            var saves = new AtomicInteger();
            var grants = new AtomicInteger();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            take(
                                    f,
                                    r -> 0,
                                    r -> {
                                        grants.incrementAndGet();
                                        if (failure == 2)
                                            throw new IllegalStateException("grant failed");
                                    },
                                    () -> {
                                        int save = saves.incrementAndGet();
                                        if (failure == 1 && save == 1 || failure == 3 && save == 2)
                                            throw new IllegalStateException("save failed");
                                    }));
            var state = AsterSchedule.progress(f.data);
            assertEquals(failure == 1 ? 0 : 1, grants.get());
            assertEquals(failure != 1, state.pendingSpecialReward());
            assertFalse(state.specialRewardTaken());
            if (failure == 1) assertEquals(original, f.data.getDetail());
            else
                assertEquals(
                        Retcode.RET_SVR_ERROR_VALUE,
                        take(
                                f,
                                r -> fail("ambiguous preflight"),
                                r -> fail("duplicate grant"),
                                () -> fail("ambiguous save")));
        }
    }

    @Test
    void overlappingClaimsDeliverOnlyOneAvatar() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1);
                var grants = new AtomicInteger();
                var calls = new ArrayList<Future<Integer>>();
                for (int i = 0; i < 2; i++)
                    calls.add(
                            executor.submit(
                                    () -> {
                                        assertTrue(start.await(5, TimeUnit.SECONDS));
                                        return take(
                                                f, r -> 0, r -> grants.incrementAndGet(), () -> {});
                                    }));
                start.countDown();
                assertEquals(
                        Set.of(0, Retcode.RET_ASTER_SPECIAL_REWARD_HAS_TAKEN_VALUE),
                        Set.of(
                                calls.get(0).get(5, TimeUnit.SECONDS),
                                calls.get(1).get(5, TimeUnit.SECONDS)));
                assertEquals(1, grants.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void oldDocumentsStartUnclaimedAndReservationsConflictWithAllOtherEconomyWrites()
            throws Exception {
        try (var f = new AsterMidTestFixture()) {
            f.data.setDetailJson("{}");
            var progress = AsterSchedule.progress(f.data);
            assertFalse(progress.specialRewardTaken());
            assertFalse(progress.pendingSpecialReward());
            progress.beginSpecialReward();
            assertThrows(IllegalStateException.class, progress::beginSpecialReward);
            assertThrows(IllegalStateException.class, () -> progress.beginWatcherReward(1200101));
            assertThrows(
                    IllegalStateException.class,
                    () -> progress.beginBigClaim(UUID.randomUUID().toString()));
            progress.finishSpecialReward();
            assertFalse(progress.hasPendingReward());
            assertTrue(progress.specialRewardTaken());
            assertThrows(IllegalStateException.class, progress::beginSpecialReward);
        }
    }

    @Test
    void anotherPendingEventRewardPreventsSpecialRewardDelivery() throws Exception {
        try (var f = new AsterMidTestFixture()) {
            eligible(f.data);
            var progress = AsterSchedule.progress(f.data);
            progress.beginWatcherReward(1200101);
            f.data.setDetail(progress);
            assertEquals(
                    Retcode.RET_SVR_ERROR_VALUE,
                    take(
                            f,
                            r -> fail("pending reward"),
                            r -> fail("pending reward"),
                            () -> fail("pending reward")));
        }
    }
}
