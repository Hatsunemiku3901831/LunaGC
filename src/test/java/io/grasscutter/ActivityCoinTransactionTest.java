package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual activity writes and cold reads; no client or production database is used. */
class ActivityCoinTransactionTest extends QuestTestFixture {
    static final int ACTIVITY = 2014, COIN = 141, SCHEDULE = 2014771;
    PlayerActivityData data;
    ActivityConfigItem config;
    AtomicInteger writes;

    @BeforeEach
    void currency() throws Exception {
        config = new ActivityConfigItem();
        config.setActivityId(ACTIVITY);
        config.setScheduleId(SCHEDULE);
        config.setBeginTime(new Date(System.currentTimeMillis() - 10000));
        config.setEndTime(new Date(System.currentTimeMillis() + 86400000));
        config.onLoad();
        var handler = new DefaultActivityHandler();
        handler.setActivityConfigItem(config);
        config.setActivityHandler(handler);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(ACTIVITY, config),
                        Map.of(SCHEDULE, config),
                        Map.of(COIN, ACTIVITY)));
        data =
                PlayerActivityData.of()
                        .uid(player.getUid())
                        .activityId(ACTIVITY)
                        .scheduleId(SCHEDULE)
                        .activityCoins(new HashMap<>(Map.of(COIN, 100)))
                        .player(player)
                        .build();
        data.saveSync();
        player.activity.getPlayerActivityDataMap().put(ACTIVITY, data);
        player.packets.clear();
    }

    Document row() {
        return store.getDatabase()
                .getCollection("activities")
                .find(new Document("_id", data.getId()))
                .first();
    }

    PlayerActivityData cold() {
        return DatabaseHelper.getPlayerActivityData(player.getUid(), ACTIVITY);
    }

    int storedCoins() {
        return row().get("activityCoins", Document.class).getInteger("141");
    }

    void fault(int nth, boolean committed) throws Exception {
        writes = new AtomicInteger();
        var current = new AtomicInteger();
        GuardedActivityTestStore.bind(
                data,
                store,
                store,
                true,
                document -> {
                    int n = writes.incrementAndGet();
                    current.set(n);
                    if (n == nth && !committed)
                        throw new IllegalStateException("before activity write");
                },
                document -> {
                    if (current.get() == nth && committed)
                        throw new IllegalStateException("activity write acknowledgement lost");
                });
    }

    @Test
    void intentIsDurableBeforeTheBalanceCanChange() throws Exception {
        fault(2, false);
        assertThrows(IllegalStateException.class, () -> ActivityCurrencies.credit(player, COIN, 5));
        assertEquals(100, storedCoins());
        assertNotNull(row().get("pendingCoinOperation"));
    }

    @Test
    void lostPreparationAcknowledgementDoesNotChangeTheBalance() throws Exception {
        fault(1, true);
        assertThrows(IllegalStateException.class, () -> ActivityCurrencies.credit(player, COIN, 5));
        assertEquals(100, storedCoins());
    }

    @Test
    void aColdReadRetainsTheUnconfirmedIntentAndBlocksGenericSaves() throws Exception {
        fault(1, true);
        assertThrows(IllegalStateException.class, () -> ActivityCurrencies.credit(player, COIN, 5));
        var loaded = cold();
        assertTrue(loaded.hasUnconfirmedActivityCoins());
        assertThrows(IllegalStateException.class, loaded::saveSync);
    }

    @Test
    void aColdReadRetainsAnUnconfirmedAppliedResult() throws Exception {
        fault(2, true);
        assertThrows(
                IllegalStateException.class, () -> ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        assertEquals(93, storedCoins());
        assertTrue(cold().hasUnconfirmedActivityCoins());
    }

    @Test
    void successfulChangesLeaveAnOperationRecordAndNoPendingIntent() {
        assertTrue(ActivityCurrencies.credit(player, COIN, 5));
        assertEquals(105, storedCoins());
        assertNull(row().get("pendingCoinOperation"));
        assertNotNull(row().get("lastCoinOperation"));
    }

    String operationId() {
        return data.getPendingCoinOperation().getOperationId();
    }

    void attachCold() {
        data = cold();
        data.setPlayer(player);
        player.activity.getPlayerActivityDataMap().put(ACTIVITY, data);
    }

    void interrupted(int nth, boolean committed, boolean debit) throws Exception {
        fault(nth, committed);
        assertThrows(
                IllegalStateException.class,
                () -> {
                    if (debit) ActivityCurrencies.pay(player, Map.of(COIN, 7));
                    else ActivityCurrencies.credit(player, COIN, 5);
                });
    }

    @ParameterizedTest
    @CsvSource({
        "1,false,false",
        "1,true,false",
        "2,false,false",
        "2,true,false",
        "1,false,true",
        "1,true,true",
        "2,false,true",
        "2,true,true"
    })
    void liveRecoveryAppliesTheExactCreditOrDebitOnce(int nth, boolean committed, boolean debit)
            throws Exception {
        interrupted(nth, committed, debit);
        String id = operationId();
        var beforeAudit = row();
        data.getCoinTransaction().audit(id);
        assertEquals(beforeAudit, row());
        assertFalse(ActivityCurrencies.credit(player, COIN, 1));
        assertFalse(ActivityCurrencies.pay(player, Map.of(COIN, 1)));
        data.getCoinTransaction().resume(id);
        data.getCoinTransaction().resume(id);
        assertEquals(debit ? 93 : 105, storedCoins());
        assertNull(row().get("pendingCoinOperation"));
        assertFalse(data.hasUnconfirmedActivityCoins());
        assertEquals(id, data.getLastCoinOperation().getOperationId());
        assertTrue(ActivityCurrencies.credit(player, COIN, 1));
        assertEquals(id, data.getLastCoinOperation().getPreviousOperationId());
    }

    @ParameterizedTest
    @CsvSource({
        "1,true,false",
        "2,false,false",
        "2,true,false",
        "1,true,true",
        "2,false,true",
        "2,true,true"
    })
    void storedIntentCanBeResumedAfterColdRead(int nth, boolean committed, boolean debit)
            throws Exception {
        interrupted(nth, committed, debit);
        String id = operationId();
        attachCold();
        assertTrue(data.hasUnconfirmedActivityCoins());
        assertFalse(ActivityCurrencies.credit(player, COIN, 1));
        data.getCoinTransaction().resume(id);
        assertEquals(debit ? 93 : 105, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false"})
    void unappliedIntentsCanBeCancelledWithoutChangingMoney(int nth, boolean committed)
            throws Exception {
        interrupted(nth, committed, true);
        String id = operationId();
        data.getCoinTransaction().cancel(id);
        data.getCoinTransaction().cancel(id);
        assertEquals(100, storedCoins());
        assertEquals(ActivityCoinReceipt.CANCELLED, data.getLastCoinOperation().getStage());
        var saved = row();
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().resume(id));
        assertEquals(saved, row());
        assertTrue(ActivityCurrencies.pay(player, Map.of(COIN, 1)));
    }

    @Test
    void appliedDebitCannotBeCancelledOrRefundedByGuessing() throws Exception {
        interrupted(2, true, true);
        String id = operationId();
        attachCold();
        var saved = row();
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().cancel(id));
        assertEquals(saved, row());
        data.getCoinTransaction().resume(id);
        assertEquals(93, storedCoins());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completionWriteFailureDoesNotTurnAnAcknowledgedDebitIntoAFailedPurchase(boolean committed)
            throws Exception {
        fault(3, committed);
        assertTrue(ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        assertTrue(data.getCoinTransaction().pending().contains("完成记录"));
        String id = data.getLastCoinOperation().getOperationId();
        assertEquals(93, storedCoins());
        attachCold();
        assertEquals(!committed, data.hasUnconfirmedActivityCoins());
        data.getCoinTransaction().resume(id);
        assertEquals(93, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUncertainCleanupCanBeFollowedByAnotherOperationOnTheSameLiveObject(boolean committed)
            throws Exception {
        fault(3, committed);
        assertTrue(ActivityCurrencies.credit(player, COIN, 5));
        assertTrue(ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        assertEquals(98, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void recoveryWriteAcknowledgementsCanBeRetriedWithoutDuplicatingTheDelta(
            int nth, boolean committed) throws Exception {
        interrupted(1, true, false);
        String id = operationId();
        attachCold();
        fault(nth, committed);
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().resume(id));
        assertTrue(data.hasUnconfirmedActivityCoins());
        data.getCoinTransaction().resume(id);
        data.getCoinTransaction().resume(id);
        assertEquals(105, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationWriteAcknowledgementsCanBeRetriedWithoutDebiting(boolean committed)
            throws Exception {
        interrupted(1, true, true);
        String id = operationId();
        attachCold();
        fault(1, committed);
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().cancel(id));
        assertTrue(data.hasUnconfirmedActivityCoins());
        data.getCoinTransaction().cancel(id);
        assertEquals(100, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @Test
    void wrongIdsCannotClearOrReplaceAnIntent() throws Exception {
        interrupted(1, true, false);
        var saved = row();
        String wrong = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().audit(wrong));
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().resume(wrong));
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().cancel(wrong));
        assertEquals(saved, row());
    }

    @Test
    void foreignRecoveryCannotBeOverwrittenByTheOriginalUncertainObject() throws Exception {
        interrupted(1, true, false);
        String id = operationId();
        var foreign = cold();
        foreign.setPlayer(player);
        foreign.getCoinTransaction().resume(id);
        var saved = row();
        assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().resume(id));
        assertEquals(saved, row());
        attachCold();
        data.getCoinTransaction().resume(id);
        assertEquals(105, storedCoins());
    }

    @Test
    void disabledActivitiesRemainAuditableAndRecoverableThroughTheGmCommand() throws Exception {
        interrupted(1, true, false);
        String id = operationId();
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(Map.of(), Map.of(), Map.of(COIN, ACTIVITY)));
        player.activity.getPlayerActivityDataMap().clear();
        player.packets.clear();
        assertEquals(1, ActivityCoinTransaction.list(player).size());
        new emu.grasscutter.command.commands.ActivityCoinsCommand()
                .execute(null, player, List.of("resume", "2014", id));
        assertEquals(105, storedCoins());
        assertFalse(cold().hasUnconfirmedActivityCoins());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void aDurableIntentPreventsReplayResetAfterColdRead() throws Exception {
        interrupted(1, true, false);
        attachCold();
        config.setScheduleId(SCHEDULE + 1);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(ACTIVITY, config),
                        Map.of(SCHEDULE + 1, config),
                        Map.of(COIN, ACTIVITY)));
        var saved = row();
        assertThrows(IllegalStateException.class, () -> player.activity.refreshActivities());
        assertEquals(saved, row());
    }

    @ParameterizedTest
    @ValueSource(strings = {"after", "uid", "longCount"})
    void malformedIntentsAreRejectedWithoutMutatingStorage(String field) throws Exception {
        interrupted(1, true, false);
        var change =
                switch (field) {
                    case "after" -> new Document("pendingCoinOperation.after.141", 106);
                    case "uid" -> new Document("pendingCoinOperation.uid", player.getUid() + 1);
                    default -> new Document("pendingCoinOperation.before.141", (1L << 32) + 100);
                };
        store.getDatabase()
                .getCollection("activities")
                .updateOne(new Document("_id", data.getId()), new Document("$set", change));
        var saved = row();
        assertThrows(RuntimeException.class, this::cold);
        assertThrows(RuntimeException.class, () -> data.getCoinTransaction().audit(operationId()));
        assertEquals(saved, row());
    }

    @Test
    void duplicateActivityDocumentsCannotBeChosenForGmRecovery() throws Exception {
        interrupted(1, true, false);
        var duplicate = new Document(row());
        duplicate.put("_id", "duplicate-activity");
        duplicate.remove("pendingCoinOperation");
        duplicate.remove("lastCoinOperation");
        store.getDatabase().getCollection("activities").insertOne(duplicate);
        var saved = row();
        assertThrows(
                IllegalStateException.class, () -> ActivityCoinTransaction.find(player, ACTIVITY));
        assertEquals(saved, row());
    }

    @Test
    void operationSnapshotsCannotBeMutatedThroughGetters() {
        assertTrue(ActivityCurrencies.credit(player, COIN, 5));
        var receipt = data.getLastCoinOperation();
        assertThrows(UnsupportedOperationException.class, () -> receipt.getAfter().put(COIN, 999));
        assertThrows(UnsupportedOperationException.class, () -> receipt.getBefore().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> receipt.getAmounts().put(COIN, 999));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void aDelayedBalanceWriteCannotOvertakeRecoveryOrCancellation(boolean cancel, boolean oldWins)
            throws Exception {
        var delayed = new DelayedActivitySave(data, store, 2);
        assertThrows(
                IllegalStateException.class, () -> ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        String id = operationId();
        var audited = row();
        data.getCoinTransaction().audit(id);
        assertEquals(audited, row());
        if (oldWins) delayed.commitBeforeNextCheck();
        if (cancel && oldWins) {
            assertThrows(IllegalStateException.class, () -> data.getCoinTransaction().cancel(id));
            data.getCoinTransaction().resume(id);
        } else if (cancel) data.getCoinTransaction().cancel(id);
        else data.getCoinTransaction().resume(id);
        assertEquals(cancel && !oldWins ? 100 : 93, storedCoins());
        var saved = row();
        assertFalse(delayed.release());
        assertEquals(saved, row());
        assertFalse(cold().hasUnconfirmedActivityCoins());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aDelayedCompletionCannotEraseTheNextOperation(boolean oldWins) throws Exception {
        var delayed = new DelayedActivitySave(data, store, 3);
        assertTrue(ActivityCurrencies.credit(player, COIN, 5));
        String previous = data.getLastCoinOperation().getOperationId();
        if (oldWins) delayed.commitBeforeNextCheck();
        assertTrue(ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        var saved = row();
        assertFalse(delayed.release());
        assertEquals(saved, row());
        assertEquals(98, storedCoins());
        assertEquals(previous, cold().getLastCoinOperation().getPreviousOperationId());
    }

    @Test
    void independentRecoveryObjectsCannotBothApplyTheSameIntent() throws Exception {
        interrupted(1, true, false);
        String id = operationId();
        var copies = List.of(cold(), cold());
        for (var copy : copies) {
            var actor = new QuestTestFixture.TestPlayer();
            actor.setUid(player.getUid());
            copy.setPlayer(actor);
        }
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var copy : copies)
                futures.add(
                        pool.submit(
                                () -> {
                                    assertTrue(go.await(5, java.util.concurrent.TimeUnit.SECONDS));
                                    try {
                                        copy.getCoinTransaction().resume(id);
                                        return true;
                                    } catch (IllegalStateException conflict) {
                                        return false;
                                    }
                                }));
            go.countDown();
            assertNotEquals(
                    futures.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(105, storedCoins());
            assertFalse(cold().hasUnconfirmedActivityCoins());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void writesCannotReenterTheCurrencyOrGenericSavePaths() throws Exception {
        var entries = new AtomicInteger();
        GuardedActivityTestStore.bind(
                data,
                store,
                store,
                true,
                document -> {
                    entries.incrementAndGet();
                    assertFalse(ActivityCurrencies.credit(player, COIN, 1));
                    assertFalse(ActivityCurrencies.pay(player, Map.of(COIN, 1)));
                    assertThrows(IllegalStateException.class, data::saveSync);
                },
                ignored -> {});
        assertTrue(ActivityCurrencies.credit(player, COIN, 5));
        assertEquals(3, entries.get());
        assertEquals(105, storedCoins());
    }

    @Test
    void aPreparationThatNeverReachedStorageHasNoEconomicEffectAfterReconnect() throws Exception {
        interrupted(1, false, true);
        attachCold();
        assertFalse(data.hasUnconfirmedActivityCoins());
        assertNull(data.getPendingCoinOperation());
        assertNull(data.getLastCoinOperation());
        assertTrue(ActivityCurrencies.pay(player, Map.of(COIN, 7)));
        assertEquals(93, storedCoins());
    }
}
