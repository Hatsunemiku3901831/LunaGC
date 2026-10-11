package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class AsterMilestonesTest {
    private AsterBigTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterBigTestFixture();
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void confirmedLargeClaimAdvancesOnlyTheClaimantsSevenClaimCondition() {
        String token = fixture.offer();
        assertEquals(
                0,
                AsterBigRewards.takeShared(
                        fixture.host,
                        fixture.guest,
                        fixture.config,
                        20,
                        fixture.now,
                        token,
                        () -> true,
                        reward -> {},
                        () -> {},
                        () -> {}));
        var condition = fixture.guest.getWatcherInfoMap().get(1200132);
        assertNotNull(condition, "The original seven large rewards condition must advance");
        assertEquals(1, condition.getCurProgress());
        assertEquals(7, condition.getTotalProgress());
        assertFalse(fixture.host.getWatcherInfoMap().containsKey(1200132));
        assertFalse(fixture.guest.getWatcherInfoMap().containsKey(1200131));
    }

    @Test
    void confirmedMiddleClaimAdvancesTheOriginalFourteenClaimCondition() {
        int camp = fixture.original.offer(0, 1);
        assertEquals(
                0,
                AsterMidRewards.take(
                        fixture.host,
                        fixture.config,
                        20,
                        fixture.now,
                        camp,
                        1,
                        () -> true,
                        reward -> {},
                        () -> {}));
        var condition = fixture.host.getWatcherInfoMap().get(1200131);
        assertNotNull(condition, "The original fourteen middle rewards condition must advance");
        assertEquals(1, condition.getCurProgress());
        assertEquals(14, condition.getTotalProgress());
        assertFalse(fixture.host.getWatcherInfoMap().containsKey(1200132));
    }

    private int large(
            String token,
            BooleanSupplier debit,
            Consumer<AsterBigRewards.Reward> grant,
            Runnable saveHost,
            Runnable saveGuest) {
        return AsterBigRewards.takeShared(
                fixture.host,
                fixture.guest,
                fixture.config,
                20,
                fixture.now,
                token,
                debit,
                grant,
                saveHost,
                saveGuest);
    }

    private int middle(
            int index,
            BooleanSupplier debit,
            Consumer<AsterMidRewards.Reward> grant,
            Runnable save) {
        return AsterMidRewards.take(
                fixture.host,
                fixture.config,
                20,
                fixture.now,
                fixture.original.offer(index, 1),
                1,
                debit,
                grant,
                save);
    }

    @Test
    void sevenLargeReceiptsFinishTheConditionAndLaterClaimsRemainCapped() {
        String last = null;
        for (int n = 1; n <= 8; n++) {
            last = fixture.offer();
            assertEquals(0, large(last, () -> true, reward -> {}, () -> {}, () -> {}));
            var w = fixture.guest.getWatcherInfoMap().get(1200132);
            assertEquals(Math.min(7, n), w.getCurProgress());
            assertEquals(n >= 7, w.isFinished());
            assertEquals(n, AsterSchedule.progress(fixture.guest).bigCollectCount());
        }
        assertNotEquals(
                0,
                large(
                        last,
                        () -> fail("No repeat debit"),
                        reward -> fail("No repeat grant"),
                        () -> fail("No repeat save"),
                        () -> fail("No repeat save")));
        assertEquals(7, fixture.guest.getWatcherInfoMap().get(1200132).getCurProgress());
    }

    @Test
    void fourteenMiddleReceiptsFinishTheConditionAndKeepTheSixExistingMissions() {
        int batchSize = GameData.getAsterMidDataMap().get(1).getCampVec().size();
        for (int n = 1; n <= 15; n++) {
            if (n > 1 && (n - 1) % batchSize == 0) {
                var progress = AsterSchedule.progress(fixture.host);
                progress.startMidBatch(progress.midRefreshId() + 1);
                fixture.host.setDetail(progress);
            }
            assertEquals(0, middle((n - 1) % batchSize, () -> true, reward -> {}, () -> {}));
            var w = fixture.host.getWatcherInfoMap().get(1200131);
            assertEquals(Math.min(14, n), w.getCurProgress());
            assertEquals(n >= 14, w.isFinished());
        }
        assertEquals(15, AsterSchedule.progress(fixture.host).midCollectCount());
        assertEquals(5, fixture.host.getWatcherInfoMap().get(1200110).getCurProgress());
        assertEquals(15, fixture.host.getWatcherInfoMap().get(1200115).getCurProgress());
        assertFalse(fixture.host.getWatcherInfoMap().containsKey(1200132));
    }

    @Test
    void sharedMiddleClaimUsesTheGuestsReceiptsRatherThanTheHostsCount() {
        for (int id = 1200110; id <= 1200115; id++)
            fixture.guest
                    .getWatcherInfoMap()
                    .put(
                            id,
                            PlayerActivityData.WatcherInfo.of()
                                    .watcherId(id)
                                    .totalProgress(
                                            GameData.getActivityWatcherDataMap()
                                                    .get(id)
                                                    .getProgress())
                                    .build());
        int camp = GameData.getAsterMidDataMap().get(1).getCampVec().get(0);
        var host = AsterSchedule.progress(fixture.host);
        assertTrue(host.offerMidReward(camp, 1, Set.of(10001, 10002)));
        fixture.host.setDetail(host);
        assertEquals(
                0,
                AsterMidRewards.takeShared(
                        fixture.host,
                        fixture.guest,
                        fixture.config,
                        20,
                        fixture.now,
                        camp,
                        host.midReward(camp).token(),
                        () -> true,
                        reward -> {},
                        () -> {},
                        () -> {}));
        assertEquals(1, fixture.guest.getWatcherInfoMap().get(1200131).getCurProgress());
        assertEquals(1, AsterSchedule.progress(fixture.guest).midCollectCount());
        assertEquals(0, AsterSchedule.progress(fixture.host).midCollectCount());
        assertFalse(fixture.host.getWatcherInfoMap().containsKey(1200131));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void insufficientResinDoesNotAdvanceOrCreateACondition(int kind) {
        int result =
                kind == 1
                        ? middle(0, () -> false, reward -> fail("No grant"), () -> {})
                        : large(
                                fixture.offer(),
                                () -> false,
                                reward -> fail("No grant"),
                                () -> {},
                                () -> {});
        assertEquals(Retcode.RET_RESIN_NOT_ENOUGH_VALUE, result);
        assertFalse(fixture.host.getWatcherInfoMap().containsKey(1200131));
        assertFalse(fixture.guest.getWatcherInfoMap().containsKey(1200132));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void largeFailuresKeepOnlyConfirmedPersonalProgress(int failure) {
        for (int i = 0; i < 3; i++)
            assertEquals(0, large(fixture.offer(), () -> true, reward -> {}, () -> {}, () -> {}));
        String token = fixture.offer();
        var hostSaves = new AtomicInteger();
        var guestSaves = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        large(
                                token,
                                () -> true,
                                reward -> {
                                    if (failure == 2)
                                        throw new IllegalStateException("Grant uncertain");
                                },
                                () -> {
                                    int n = hostSaves.incrementAndGet();
                                    if (failure == 0 && n == 1 || failure == 4 && n == 2)
                                        throw new IllegalStateException("Host save uncertain");
                                },
                                () -> {
                                    int n = guestSaves.incrementAndGet();
                                    if (failure == 1 && n == 1 || failure == 3 && n == 2)
                                        throw new IllegalStateException("Guest save uncertain");
                                }));
        assertEquals(
                failure == 4 ? 4 : 3,
                fixture.guest.getWatcherInfoMap().get(1200132).getCurProgress());
        assertEquals(failure == 4 ? 4 : 3, AsterSchedule.progress(fixture.guest).bigCollectCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void middleFailuresPreserveTheLastConfirmedCondition(int failure) {
        for (int i = 0; i < 3; i++) assertEquals(0, middle(i, () -> true, reward -> {}, () -> {}));
        var saves = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        middle(
                                3,
                                () -> true,
                                reward -> {
                                    if (failure == 2)
                                        throw new IllegalStateException("Grant uncertain");
                                },
                                () -> {
                                    int n = saves.incrementAndGet();
                                    if (failure == 1 && n == 1 || failure == 3 && n == 2)
                                        throw new IllegalStateException("Save uncertain");
                                }));
        assertEquals(3, fixture.host.getWatcherInfoMap().get(1200131).getCurProgress());
        assertEquals(3, AsterSchedule.progress(fixture.host).midCollectCount());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,1", "1,2", "1,3", "2,0", "2,1", "2,2", "2,3"})
    void resourceMismatchIsRejectedBeforeAnyDebitGrantOrReservation(int kind, int invalid) {
        int id = kind == 1 ? 1200131 : 1200132;
        var json =
                JsonParser.parseString(
                                JsonUtils.encode(GameData.getActivityWatcherDataMap().get(id)))
                        .getAsJsonObject();
        switch (invalid) {
            case 0 -> json.addProperty("progress", 1);
            case 1 -> json.addProperty("isDisuse", true);
            case 2 -> json.addProperty("id", 999999);
            case 3 -> json.getAsJsonObject("triggerConfig")
                    .getAsJsonArray("paramList")
                    .set(0, new com.google.gson.JsonPrimitive("999999"));
        }
        var meta = JsonUtils.decode(json.toString(), ActivityWatcherData.class);
        meta.onLoad();
        GameData.getActivityWatcherDataMap().put(id, meta);
        assertFalse(AsterMilestones.resourcesAvailable());
        int result =
                kind == 1
                        ? middle(
                                0,
                                () -> fail("No debit"),
                                reward -> fail("No grant"),
                                () -> fail("No save"))
                        : large(
                                fixture.offer(),
                                () -> fail("No debit"),
                                reward -> fail("No grant"),
                                () -> fail("No host save"),
                                () -> fail("No guest save"));
        assertEquals(Retcode.RET_NOT_FOUND_CONFIG_VALUE, result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void malformedPersonalWatcherCannotBeCompletedAfterSideEffects(int invalid) {
        var w =
                PlayerActivityData.WatcherInfo.of()
                        .watcherId(invalid == 0 ? 1 : 1200132)
                        .totalProgress(invalid == 1 ? 8 : 7)
                        .curProgress(invalid == 2 ? -1 : invalid == 3 ? 8 : 0)
                        .build();
        fixture.guest.getWatcherInfoMap().put(1200132, w);
        assertEquals(
                Retcode.RET_NOT_FOUND_CONFIG_VALUE,
                large(
                        fixture.offer(),
                        () -> fail("No debit"),
                        reward -> fail("No grant"),
                        () -> fail("No host save"),
                        () -> fail("No guest save")));
        assertSame(w, fixture.guest.getWatcherInfoMap().get(1200132));
    }

    @Test
    void reloadDerivesConditionsFromReceiptsAndPreservesTheQuestAndRewardFlags() {
        for (int n = 0; n < 3; n++) assertEquals(0, middle(n, () -> true, reward -> {}, () -> {}));
        var progress = AsterSchedule.progress(fixture.host);
        for (int n = 0; n < 8; n++) {
            progress.beginBigClaim(UUID.randomUUID().toString());
            progress.finishBigClaim(14);
        }
        fixture.host.setDetail(progress);
        var quest =
                PlayerActivityData.WatcherInfo.of()
                        .watcherId(1200130)
                        .totalProgress(1)
                        .curProgress(1)
                        .isTakenReward(true)
                        .build();
        fixture.host.getWatcherInfoMap().put(1200130, quest);
        fixture.host.getWatcherInfoMap().get(1200131).setCurProgress(0);
        fixture.host
                .getWatcherInfoMap()
                .put(
                        1200132,
                        PlayerActivityData.WatcherInfo.of()
                                .watcherId(1200132)
                                .totalProgress(7)
                                .isTakenReward(true)
                                .build());
        String before = fixture.host.getDetail();
        var handler = new AsterActivityHandler();
        assertTrue(handler.onLoadPlayerActivityData(fixture.host));
        assertEquals(3, fixture.host.getWatcherInfoMap().get(1200131).getCurProgress());
        assertEquals(7, fixture.host.getWatcherInfoMap().get(1200132).getCurProgress());
        assertTrue(fixture.host.getWatcherInfoMap().get(1200132).isTakenReward());
        assertSame(quest, fixture.host.getWatcherInfoMap().get(1200130));
        assertEquals(before, fixture.host.getDetail());
        assertFalse(handler.onLoadPlayerActivityData(fixture.host));
        fixture.host.setDetail(new AsterProgress());
        assertTrue(handler.onLoadPlayerActivityData(fixture.host));
        assertEquals(0, fixture.host.getWatcherInfoMap().get(1200131).getCurProgress());
        assertEquals(0, fixture.host.getWatcherInfoMap().get(1200132).getCurProgress());
    }

    public static final class AlwaysClaimWatcher extends ActivityWatcher {
        @Override
        protected boolean isMeet(String... args) {
            return true;
        }
    }

    @Test
    void genericWatcherTriggersCannotDoubleCountTheConfirmedReceiptConditions() {
        var previous = GameData.getActivityDataMap().get(2001);
        try {
            var activity =
                    JsonUtils.decode(
                            "{activityId:2001,activityType:'NEW_ACTIVITY_ASTER',"
                                    + "watcherId:[1200130,1200131,1200132],condGroupId:[]}",
                            ActivityData.class);
            activity.onLoad();
            GameData.getActivityDataMap().put(2001, activity);
            var handler = new AsterActivityHandler();
            handler.setActivityConfigItem(fixture.config);
            handler.initWatchers(
                    Map.of(
                            WatcherTriggerType.TRIGGER_TAKE_GENERAL_REWARD,
                            ConstructorAccess.get(AlwaysClaimWatcher.class)));
            handler.onLoadPlayerActivityData(fixture.guest);
            for (var watcher :
                    handler.getWatchersMap().get(WatcherTriggerType.TRIGGER_TAKE_GENERAL_REWARD)) {
                assertEquals(DefaultWatcher.class, watcher.getClass());
                watcher.trigger(fixture.guest, "70330041");
            }
            assertEquals(0, fixture.guest.getWatcherInfoMap().get(1200131).getCurProgress());
            assertEquals(0, fixture.guest.getWatcherInfoMap().get(1200132).getCurProgress());
        } finally {
            if (previous == null) GameData.getActivityDataMap().remove(2001);
            else GameData.getActivityDataMap().put(2001, previous);
        }
    }
}
