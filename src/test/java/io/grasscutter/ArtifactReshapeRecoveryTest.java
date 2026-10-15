package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reliquary.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;

/** Current reshape packets, real stored payment snapshots and explicit GM recovery. */
class ArtifactReshapeRecoveryTest extends ArtifactReshapeTest {
    ArtifactReshapeCandidate intent(ObjectId item) {
        return actual.getArtifactTransmuterData().getReshapeCandidates().get(item.toHexString());
    }

    String operation(ObjectId item) {
        var json = JsonUtils.toJson(intent(item)).getAsJsonObject();
        var id = json.get("operationId");
        return id == null || id.isJsonNull() ? "legacy:" + item.toHexString() : id.getAsString();
    }

    GameItem current(ObjectId item) {
        return artifacts().stream()
                .filter(x -> x.getObjectId().equals(item))
                .findFirst()
                .orElseThrow();
    }

    void interrupt(String boundary) throws Exception {
        var owner = actual;
        replaceField(
                Player.class,
                owner,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        owner,
                        () -> System.currentTimeMillis() / 1000L,
                        () -> {
                            boolean reserved =
                                    owner
                                            .getArtifactTransmuterData()
                                            .getReshapeCandidates()
                                            .values()
                                            .stream()
                                            .anyMatch(
                                                    x ->
                                                            x.getPhase()
                                                                    == ArtifactReshapeCandidate
                                                                            .Phase.RESERVED);
                            boolean ready =
                                    owner
                                            .getArtifactTransmuterData()
                                            .getReshapeCandidates()
                                            .values()
                                            .stream()
                                            .anyMatch(
                                                    x ->
                                                            x.getPhase()
                                                                    == ArtifactReshapeCandidate
                                                                            .Phase.READY);
                            if (boundary.equals("readyBefore") && ready)
                                throw new IllegalStateException("injected ready write failure");
                            DatabaseHelper.saveGameSync(owner);
                            if (boundary.equals("reserve") && reserved
                                    || boundary.equals("readyAfter") && ready)
                                throw new IllegalStateException(
                                        "injected reshape acknowledgement " + boundary);
                        },
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (boundary.equals("payment") && item.getItemId() == 105006
                                    || boundary.equals("selection")
                                            && item.getItemType() == ItemType.ITEM_RELIQUARY)
                                throw new IllegalStateException(
                                        "injected reshape item acknowledgement " + boundary);
                        }));
    }

    ObjectId interruptedRoll(String boundary) throws Exception {
        var item = relic(1);
        var id = item.getObjectId();
        interrupt(boundary);
        assertNotEquals(0, roll(item).getRetcode());
        reload();
        return id;
    }

    void recover(ObjectId item, boolean resume) {
        actual.getArtifactTransmuter().reconcile(operation(item), resume);
    }

    ArtifactTransmuterData storedState() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        return store.find(Player.class)
                .field("id")
                .equal(actual.getUid())
                .first()
                .getArtifactTransmuterData();
    }

    @Test
    void reshapeRecoveryCapturesTheActualDustIdentityAndBeforeCountBeforeAnyPayment()
            throws Exception {
        var dust = actual.getInventory().getItemById(105006).getObjectId().toHexString();
        var id = interruptedRoll("reserve");
        var receipt = JsonUtils.toJson(intent(id)).getAsJsonObject();
        assertNotNull(receipt.get("operationId"));
        assertDoesNotThrow(() -> UUID.fromString(receipt.get("operationId").getAsString()));
        assertEquals(105006, receipt.get("materialId").getAsInt());
        assertEquals(dust, receipt.get("materialObjectId").getAsString());
        assertEquals(100, receipt.get("materialBefore").getAsInt());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
        assertTrue(actual.getArtifactTransmuter().inspect().toString().contains(operation(id)));
    }

    @Test
    void reshapeRecoveryCanCancelAnUnpaidReservationAndReleaseTheArtifact() throws Exception {
        var id = interruptedRoll("reserve");
        var operation = operation(id);
        assertFalse(current(id).isDestroyable());
        recover(id, false);
        assertFalse(storedState().getReshapeCandidates().containsKey(id.toHexString()));
        assertEquals(100, actual.getInventory().getItemCountById(105006));
        assertEquals(0, actual.getArtifactTransmuterData().getReshapeProgress());
        assertTrue(current(id).isDestroyable());
        assertThrows(
                IllegalStateException.class,
                () -> actual.getArtifactTransmuter().reconcile(operation, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "payment", "readyBefore", "readyAfter"})
    void reshapeRecoveryFinishesPaymentWithTheReservedCandidateAndOneProgressIncrement(
            String boundary) throws Exception {
        var id = interruptedRoll(boundary);
        var result = List.copyOf(intent(id).getResult());
        var original = List.copyOf(intent(id).getOriginal());
        recover(id, true);
        assertEquals(ArtifactReshapeCandidate.Phase.READY, intent(id).getPhase());
        assertEquals(result, intent(id).getResult());
        assertEquals(original, current(id).getAppendPropIdList());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
        assertEquals(result, candidate(current(id)).getAppendPropIdListList());
        assertEquals(
                ArtifactReshapeCandidate.Phase.READY,
                storedState().getReshapeCandidates().get(id.toHexString()).getPhase());
        recover(id, true);
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
        assertEquals(0, choose(current(id), true).getRetcode());
        assertEquals(result, current(id).getAppendPropIdList());
    }

    @Test
    void reshapeRecoveryNeverCancelsAnAlreadyPaidReservationOrRefundsItsDust() throws Exception {
        var id = interruptedRoll("payment");
        assertTrue(actual.getArtifactTransmuter().inspect().toString().contains(operation(id)));
        assertThrows(IllegalStateException.class, () -> recover(id, false));
        assertNotNull(intent(id));
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        recover(id, true);
        assertEquals(99, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void reshapeRecoveryRecognizesACompletelySpentDustStackByItsPersistedIdentity()
            throws Exception {
        assertTrue(actual.getInventory().removeItem(actual.getInventory().getItemById(105006), 99));
        var id = interruptedRoll("payment");
        assertNull(actual.getInventory().getItemById(105006));
        recover(id, true);
        assertEquals(0, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
        assertEquals(ArtifactReshapeCandidate.Phase.READY, intent(id).getPhase());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reshapeRecoveryResumesOnlyTheAlreadyRecordedKeepOrAdoptDecision(boolean adopt)
            throws Exception {
        var item = relic(1);
        var id = item.getObjectId();
        assertEquals(0, roll(item).getRetcode());
        var expected = List.copyOf(adopt ? intent(id).getResult() : intent(id).getOriginal());
        interrupt("selection");
        assertNotEquals(0, choose(item, adopt).getRetcode());
        reload();
        assertThrows(IllegalStateException.class, () -> recover(id, false));
        var operation = operation(id);
        recover(id, true);
        assertFalse(storedState().getReshapeCandidates().containsKey(id.toHexString()));
        assertEquals(expected, current(id).getAppendPropIdList());
        assertTrue(current(id).isDestroyable());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertThrows(
                IllegalStateException.class,
                () -> actual.getArtifactTransmuter().reconcile(operation, true));
    }

    @Test
    void reshapeRecoveryRefreshesAnOlderReadyCandidateWithoutRecheckingItsOldDustBalance()
            throws Exception {
        var first = relic(1);
        assertEquals(0, roll(first).getRetcode());
        var id = first.getObjectId();
        var second = relic(3);
        assertEquals(0, roll(second).getRetcode());
        assertNotEquals(operation(id), operation(second.getObjectId()));
        recover(id, true);
        assertEquals(2, dustState().getCandidateListCount());
        assertEquals(97, actual.getInventory().getItemCountById(105006));
        assertEquals(3, actual.getArtifactTransmuterData().getReshapeProgress());
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "readyBefore", "readyAfter"})
    void reshapeRecoveryCanBeInterruptedAgainWithoutLosingItsCandidateOrChargingTwice(
            String boundary) throws Exception {
        var id = interruptedRoll("reserve");
        var expected = List.copyOf(intent(id).getResult());
        interrupt(boundary);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        reload();
        recover(id, true);
        assertEquals(expected, intent(id).getResult());
        assertEquals(ArtifactReshapeCandidate.Phase.READY, intent(id).getPhase());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reshapeRecoveryCancellationClearFailureKeepsTheHoldUntilTheActualStorageIsReloaded(
            boolean committed) throws Exception {
        var id = interruptedRoll("reserve");
        var operation = operation(id);
        var owner = actual;
        replaceField(
                Player.class,
                owner,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> {
                            boolean clearing =
                                    !owner.getArtifactTransmuterData()
                                            .getReshapeCandidates()
                                            .containsKey(id.toHexString());
                            if (!clearing || committed) DatabaseHelper.saveGameSync(owner);
                            if (clearing)
                                throw new IllegalStateException(
                                        "injected reshape cancellation clear acknowledgement");
                        },
                        DatabaseHelper::saveGameSync));
        assertThrows(IllegalStateException.class, () -> recover(id, false));
        assertNotNull(intent(id));
        assertFalse(current(id).isDestroyable());
        reload();
        if (committed)
            assertThrows(
                    IllegalStateException.class,
                    () -> actual.getArtifactTransmuter().reconcile(operation, false));
        else recover(id, false);
        assertNull(intent(id));
        assertTrue(current(id).isDestroyable());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reshapeRecoveryRejectsChangedDustAndMemoryStorageDisagreementBeforeMutation(
            boolean persisted) throws Exception {
        var id = interruptedRoll("payment");
        var dust = actual.getInventory().getItemById(105006);
        dust.setCount(98);
        if (persisted) DatabaseHelper.saveGameSync(dust);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertEquals(ArtifactReshapeCandidate.Phase.RESERVED, intent(id).getPhase());
        assertEquals(0, actual.getArtifactTransmuterData().getReshapeProgress());
        dust.setCount(99);
        DatabaseHelper.saveGameSync(dust);
        recover(id, true);
        assertEquals(99, actual.getInventory().getItemCountById(105006));
    }

    void legacy(ObjectId item) throws Exception {
        var json = JsonUtils.toJson(intent(item)).getAsJsonObject();
        for (String field :
                List.of("operationId", "materialId", "materialBefore", "materialObjectId"))
            json.remove(field);
        var next = new HashMap<>(actual.getArtifactTransmuterData().getReshapeCandidates());
        next.put(item.toHexString(), JsonUtils.decode(json, ArtifactReshapeCandidate.class));
        actual.getArtifactTransmuterData().setReshapeCandidates(next);
        DatabaseHelper.saveGameSync(actual);
        reload();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "ADOPTING", "KEEPING"})
    void reshapeRecoveryCanRefreshOrFinishALegacyPaidCandidateByItsExplicitPersistentId(
            String phase) throws Exception {
        var item = relic(1);
        var id = item.getObjectId();
        assertEquals(0, roll(item).getRetcode());
        var expected =
                List.copyOf(
                        phase.equals("ADOPTING")
                                ? intent(id).getResult()
                                : intent(id).getOriginal());
        if (!phase.equals("READY")) {
            interrupt("selection");
            assertNotEquals(0, choose(item, phase.equals("ADOPTING")).getRetcode());
            reload();
        }
        legacy(id);
        assertEquals("legacy:" + id.toHexString(), operation(id));
        recover(id, true);
        assertEquals(expected, current(id).getAppendPropIdList());
        if (phase.equals("READY")) assertNotNull(intent(id));
        else assertNull(intent(id));
        assertEquals(99, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void reshapeRecoveryNeverInfersMissingLegacyPaymentMetadata() throws Exception {
        var id = interruptedRoll("payment");
        legacy(id);
        assertTrue(actual.getArtifactTransmuter().inspect().toString().contains("旧重塑记录缺少付款快照"));
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertThrows(IllegalStateException.class, () -> recover(id, false));
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertNotNull(intent(id));
        assertFalse(current(id).isDestroyable());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reshapeRecoveryDoesNotAdvanceAnAlreadySavedProgressAgainAndRejectsUnpaidProgress(
            boolean paid) throws Exception {
        var id = interruptedRoll(paid ? "payment" : "reserve");
        actual.getArtifactTransmuterData().setReshapeProgress(intent(id).getProgressAfter());
        DatabaseHelper.saveGameSync(actual);
        reload();
        if (paid) {
            recover(id, true);
            assertEquals(ArtifactReshapeCandidate.Phase.READY, intent(id).getPhase());
        } else {
            assertThrows(IllegalStateException.class, () -> recover(id, true));
            assertEquals(ArtifactReshapeCandidate.Phase.RESERVED, intent(id).getPhase());
        }
        assertEquals(paid ? 99 : 100, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
    }

    @Test
    void reshapeRecoveryCancellationLeavesAnotherReadyCandidateAndItsProgressUntouched()
            throws Exception {
        var first = relic(1);
        var firstId = first.getObjectId();
        assertEquals(0, roll(first).getRetcode());
        var expected = List.copyOf(intent(firstId).getResult());
        var second = relic(3);
        var secondId = second.getObjectId();
        interrupt("reserve");
        assertNotEquals(0, roll(second).getRetcode());
        reload();
        recover(secondId, false);
        assertNull(intent(secondId));
        assertEquals(expected, intent(firstId).getResult());
        assertEquals(1, dustState().getCandidateListCount());
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertTrue(current(secondId).isDestroyable());
        assertFalse(current(firstId).isDestroyable());
    }

    @Test
    void reshapeRecoveryRejectsAnArtifactChangedSinceReservationWithoutOverwritingIt()
            throws Exception {
        var id = interruptedRoll("payment");
        var item = current(id);
        var original = List.copyOf(item.getAppendPropIdList());
        Collections.swap(item.getAppendPropIdList(), 0, 1);
        DatabaseHelper.saveGameSync(item);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertNotEquals(original, item.getAppendPropIdList());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(original);
        DatabaseHelper.saveGameSync(item);
        recover(id, true);
        assertEquals(original, item.getAppendPropIdList());
    }

    @Test
    void reshapeRecoveryConcurrentResumesShareTheSamePaymentAndReadyCandidate() throws Exception {
        var id = interruptedRoll("reserve");
        var expected = List.copyOf(intent(id).getResult());
        var operation = operation(id);
        var manager = actual.getArtifactTransmuter();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first =
                    executor.submit(
                            () -> {
                                start.await();
                                manager.reconcile(operation, true);
                                return true;
                            });
            var second =
                    executor.submit(
                            () -> {
                                start.await();
                                manager.reconcile(operation, true);
                                return true;
                            });
            start.countDown();
            assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(expected, intent(id).getResult());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertEquals(1, actual.getArtifactTransmuterData().getReshapeProgress());
        assertEquals(1, dustState().getCandidateListCount());
    }

    @Test
    void reshapeRecoveryIsRoutedFromTheExistingGmCommandWithAnExplicitOperation() throws Exception {
        var id = interruptedRoll("reserve");
        var operation = operation(id);
        var messages = new ArrayList<String>();
        replaceField(
                emu.grasscutter.Grasscutter.class,
                null,
                "pluginManager",
                new emu.grasscutter.plugin.PluginManager());
        emu.grasscutter.Grasscutter.getPluginManager()
                .registerListener(
                        new emu.grasscutter.server.event.EventHandler<>(
                                        emu.grasscutter.server.event.game
                                                .ReceiveCommandFeedbackEvent.class)
                                .listener(
                                        event -> {
                                            messages.add(event.getMessage());
                                            event.cancel();
                                        }));
        var command = new emu.grasscutter.command.commands.ArtifactTransmuterCommand();
        command.execute(null, actual, List.of("status"));
        assertTrue(messages.toString().contains(operation));
        command.execute(null, actual, List.of("resume", "another-operation"));
        assertEquals(100, actual.getInventory().getItemCountById(105006));
        command.execute(null, actual, List.of("resume", operation));
        assertEquals(ArtifactReshapeCandidate.Phase.READY, intent(id).getPhase());
        assertTrue(messages.toString().contains("已处理 uid=10002"));
    }

    @Test
    void reshapeRecoverySavedStateLookupDoesNotQueueWritesFromASeparatePlayerCopy()
            throws Exception {
        var id = interruptedRoll("reserve");
        actual.getArtifactTransmuterData().setCycleEndTime(0xffff_ffffL);
        actual.getArtifactTransmuterData().setReshapeProgress(0xffff_ffffL);
        actual.getArtifactTransmuterData().setProgressUnits(1500);
        actual.getArtifactTransmuterData().setDefinedCounts(Map.of(15001, 1));
        intent(id).setProgressBefore(0xffff_fffeL);
        intent(id).setProgressAfter(0xffff_ffffL);
        DatabaseHelper.saveGameSync(actual);
        var type = Class.forName("emu.grasscutter.game.reliquary.ArtifactRecoveryStore");
        var constructor = type.getDeclaredConstructor(Player.class);
        constructor.setAccessible(true);
        var reader = constructor.newInstance(actual);
        var lookup = type.getDeclaredMethod("savedState");
        lookup.setAccessible(true);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        long before = DatabaseHelper.getEventExecutor().snapshot().completed();
        var saved = (ArtifactTransmuterData) lookup.invoke(reader);
        assertEquals(JsonUtils.toJson(actual.getArtifactTransmuterData()), JsonUtils.toJson(saved));
        assertEquals(
                operation(id),
                JsonUtils.toJson(saved.getReshapeCandidates().get(id.toHexString()))
                        .getAsJsonObject()
                        .get("operationId")
                        .getAsString());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(
                before,
                DatabaseHelper.getEventExecutor().snapshot().completed(),
                "Reading economic state must not start Player.onLoad migrations or queue a stale"
                        + " player save");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingResultResource",
                "corruptResult",
                "missingItemResource",
                "missingDustResource"
            })
    void reshapeRecoveryUnpaidCancellationDoesNotRequireResourcesForAnAbandonedResult(
            String boundary) throws Exception {
        var id = interruptedRoll("reserve");
        var item = current(id);
        var dust = actual.getInventory().getItemById(105006);
        switch (boundary) {
            case "missingResultResource" -> put(
                    emu.grasscutter.data.GameData.getReliquaryAffixDataMap(),
                    intent(id).getResult().get(0),
                    null);
            case "corruptResult" -> {
                intent(id).setResult(List.of());
                DatabaseHelper.saveGameSync(actual);
            }
            case "missingItemResource" -> {
                put(emu.grasscutter.data.GameData.getItemDataMap(), item.getItemId(), null);
                item.setItemData(null);
            }
            case "missingDustResource" -> {
                put(emu.grasscutter.data.GameData.getItemDataMap(), 105006, null);
                dust.setItemData(null);
            }
        }
        recover(id, false);
        assertNull(intent(id));
        assertFalse(item.isReshapePending());
        assertEquals(1, item.getCount());
        assertEquals(100, dust.getCount());
        assertFalse(storedState().getReshapeCandidates().containsKey(id.toHexString()));
    }
}
