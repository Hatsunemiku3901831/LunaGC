package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reliquary.ArtifactTransmuter;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Recovery against acknowledged Mongo documents, using actual interrupted requests. */
class ArtifactTransmuterRecoveryTest extends ArtifactTransmuterDefineTest {
    @BeforeEach
    void extraMaterial() throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 105002)
                        .findFirst()
                        .orElseThrow();
        data.onLoad();
        put(emu.grasscutter.data.GameData.getItemDataMap(), data.getId(), data);
    }

    Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        try {
            return ArtifactTransmuter.class
                    .getMethod(method, types)
                    .invoke(actual.getArtifactTransmuter(), args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw failure;
        }
    }

    void recover(String id, boolean resume) throws Exception {
        invoke("reconcile", new Class<?>[] {String.class, boolean.class}, id, resume);
    }

    List<?> inspect() throws Exception {
        return (List<?>) invoke("inspect", new Class<?>[0]);
    }

    void interruptAt(String phase, int outputId) throws Exception {
        var owner = actual;
        replaceField(
                Player.class,
                owner,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> {
                            DatabaseHelper.saveGameSync(owner);
                            var pending = owner.getArtifactTransmuterData().getPending();
                            if (pending != null
                                    && (phase.equals("reserve") && pending.getState() == 1
                                            || phase.equals("paid") && pending.getState() == 2))
                                throw new IllegalStateException(
                                        "injected recovery boundary " + phase);
                        },
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (phase.equals("payment") && item.getItemId() != outputId
                                    || phase.equals("output") && item.getItemId() == outputId)
                                throw new IllegalStateException(
                                        "injected recovery item boundary " + phase);
                        }));
    }

    String interruptedDefinition(String phase) throws Exception {
        interruptAt(phase, -1);
        assertNotEquals(0, define(req(3)).getRetcode());
        var id = actual.getArtifactTransmuterData().getPending().getOperationId();
        reload();
        return id;
    }

    String interruptedExtraction(String phase, boolean full) throws Exception {
        stock(105003, full ? 50 : 1);
        interruptAt(phase, 105005);
        assertNotEquals(
                0,
                actual.getArtifactTransmuter()
                        .extract(
                                ReliquaryOfferExtractReq.newBuilder()
                                        .setScheduleId(700)
                                        .addItemList(
                                                ItemParam.newBuilder()
                                                        .setItemId(105003)
                                                        .setCount(full ? 50 : 1))
                                        .build())
                        .getRetcode());
        var id = actual.getArtifactTransmuterData().getPending().getOperationId();
        reload();
        return id;
    }

    @Test
    void recoveryInspectionIsReadOnlyAndCancellationClearsOnlyAnUnpaidReservation()
            throws Exception {
        var id = interruptedDefinition("reserve");
        assertTrue(inspect().toString().contains(id));
        assertNotNull(readPending());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        recover(id, false);
        assertNull(actual.getArtifactTransmuterData().getPending());
        assertNull(readPending());
        assertTrue(artifacts().isEmpty());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
    }

    Object readPending() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        return store.find(Player.class)
                .field("id")
                .equal(actual.getUid())
                .first()
                .getArtifactTransmuterData()
                .getPending();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "payment", "paid"})
    void recoveryResumesDefinitionWithTheReservedRollsAndChargesEachCostOnce(String phase)
            throws Exception {
        var id = interruptedDefinition(phase);
        var receipt = actual.getArtifactTransmuterData().getPending();
        var reserved = receipt.getOutputObjectId();
        var append = List.copyOf(receipt.getAppendPropIds());
        recover(id, true);
        assertNull(readPending());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertEquals(1, artifacts().size());
        var item = artifacts().get(0);
        assertEquals(reserved, item.getObjectId().toHexString());
        assertEquals(append, item.getAppendPropIdList());
        assertEquals(receipt.getSelectedPropIds(), item.getPurchasedAppendPropIdList());
        assertEquals(2, item.getDefinedUpgradeGuarantee());
        assertTrue(item.isLocked());
        assertEquals(1, actual.getArtifactTransmuterData().getDefinedCounts().get(15001));
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        reload();
        assertEquals(1, artifacts().size());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoveryRecognizesAnAlreadyPersistedDefinitionInsteadOfDeliveringAnother(boolean consumed)
            throws Exception {
        var owner = actual;
        replaceField(
                Player.class,
                owner,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> DatabaseHelper.saveGameSync(owner),
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (item.getItemType() == ItemType.ITEM_RELIQUARY)
                                throw new IllegalStateException(
                                        "injected persisted relic acknowledgement");
                        }));
        assertNotEquals(0, define(req(3)).getRetcode());
        var id = actual.getArtifactTransmuterData().getPending().getOperationId();
        var itemId = artifacts().get(0).getObjectId();
        if (consumed) {
            assertTrue(actual.getInventory().removeItem(artifacts().get(0), 1));
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        }
        reload();
        recover(id, true);
        assertNull(readPending());
        assertEquals(consumed ? 0 : 1, artifacts().size());
        if (!consumed) assertEquals(itemId, artifacts().get(0).getObjectId());
        else assertTrue(store.find(GameItem.class).field("id").equal(itemId).first().isDeleted());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertEquals(1, actual.getArtifactTransmuterData().getDefinedCounts().get(15001));
    }

    @Test
    void cancellingAnUnspentDefinitionRetiresItsDelayedCostWrite() throws Exception {
        var id = interruptedDefinition("reserve");
        var source = actual.getInventory().getItemById(105005);
        var delayed = new DelayedItemSave(source, store, false, false);
        int before = source.getCount();
        source.setCount(before - 2);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(source));
        reload();
        recover(id, false);
        assertFalse(delayed.release());
        assertNull(readPending());
        assertEquals(before, actual.getInventory().getItemCountById(105005));
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "paid", "output"})
    void recoveryCompletesExtractionWithoutRepeatingItsAlreadyPaidOrDeliveredParts(String phase)
            throws Exception {
        var id = interruptedExtraction(phase, true);
        recover(id, true);
        assertNull(readPending());
        assertEquals(0, actual.getInventory().getItemCountById(105003));
        assertEquals(21, actual.getInventory().getItemCountById(105005));
        assertEquals(1, actual.getArtifactTransmuterData().getExtractedCount());
        assertEquals(0, actual.getArtifactTransmuterData().getProgressUnits());
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        reload();
        assertEquals(21, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void recoveryPreservesFractionalExtractionWithoutCreatingAnElixir() throws Exception {
        var id = interruptedExtraction("payment", false);
        recover(id, true);
        assertNull(readPending());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertEquals(2000, actual.getArtifactTransmuterData().getProgressUnits());
        assertEquals(0, actual.getArtifactTransmuterData().getExtractedCount());
    }

    @Test
    void recoveryRejectsTheWrongOperationAndCannotCancelPaidMaterials() throws Exception {
        var id = interruptedDefinition("payment");
        assertThrows(IllegalStateException.class, () -> recover("another-operation", true));
        assertThrows(IllegalStateException.class, () -> recover(id, false));
        assertNotNull(readPending());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void recoveryRejectsChangedMaterialsBeforeMutatingOtherState() throws Exception {
        var id = interruptedDefinition("payment");
        var dust = actual.getInventory().getItemById(105005);
        dust.setCount(17);
        DatabaseHelper.saveGameSync(dust);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertNotNull(readPending());
        assertEquals(17, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void recoveryCommandIsAvailableToGmAndRequiresAnExplicitOperationForMutation()
            throws Exception {
        var id = interruptedDefinition("reserve");
        var messages = new ArrayList<String>();
        replaceField(
                Grasscutter.class,
                null,
                "pluginManager",
                new emu.grasscutter.plugin.PluginManager());
        Grasscutter.getPluginManager()
                .registerListener(
                        new EventHandler<>(ReceiveCommandFeedbackEvent.class)
                                .listener(
                                        event -> {
                                            messages.add(event.getMessage());
                                            event.cancel();
                                        }));
        var command =
                (CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.ArtifactTransmuterCommand")
                                .getConstructor()
                                .newInstance();
        var annotation = command.getClass().getAnnotation(Command.class);
        assertEquals("server.transmuter", annotation.permission());
        assertEquals(Command.TargetRequirement.ONLINE, annotation.targetRequirement());
        assertInstanceOf(command.getClass(), new CommandMap(true).getHandler("transmuter"));
        command.execute(null, actual, List.of("status"));
        assertTrue(messages.toString().contains(id));
        command.execute(null, actual, List.of("cancel"));
        assertNotNull(readPending());
        command.execute(null, actual, List.of("cancel", id));
        assertNull(readPending());
        assertTrue(messages.toString().contains("uid=10002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fullBag", "missingResource", "legacy"})
    void recoveryCancellationDoesNotRequireRecreatingAnUnpaidOutput(String boundary)
            throws Exception {
        var id = interruptedDefinition("reserve");
        var receipt = actual.getArtifactTransmuterData().getPending();
        switch (boundary) {
            case "fullBag" -> replaceField(
                    EquipInventoryTab.class,
                    actual.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY),
                    "maxCapacity",
                    0);
            case "missingResource" -> put(
                    emu.grasscutter.data.GameData.getItemDataMap(), receipt.getRewardId(), null);
            case "legacy" -> {
                replaceField(receipt.getClass(), receipt, "definedUpgradeGuarantee", 0);
                DatabaseHelper.saveGameSync(actual);
            }
        }
        recover(id, false);
        assertNull(readPending());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void recoveryContinuesAPartialMultiMaterialPaymentWithoutPayingItsFirstCostAgain()
            throws Exception {
        stock(105003, 1);
        stock(105002, 1);
        interruptAt("payment", 105005);
        assertNotEquals(
                0,
                actual.getArtifactTransmuter()
                        .extract(
                                ReliquaryOfferExtractReq.newBuilder()
                                        .setScheduleId(700)
                                        .addItemList(
                                                ItemParam.newBuilder()
                                                        .setItemId(105003)
                                                        .setCount(1))
                                        .addItemList(
                                                ItemParam.newBuilder()
                                                        .setItemId(105002)
                                                        .setCount(1))
                                        .build())
                        .getRetcode());
        var id = actual.getArtifactTransmuterData().getPending().getOperationId();
        reload();
        assertEquals(0, actual.getInventory().getItemCountById(105003));
        assertEquals(1, actual.getInventory().getItemCountById(105002));
        recover(id, true);
        assertNull(readPending());
        assertEquals(0, actual.getInventory().getItemCountById(105002));
        assertEquals(2500, actual.getArtifactTransmuterData().getProgressUnits());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void recoveryRejectsMemoryThatDiffersFromTheStoredMaterialCount() throws Exception {
        var id = interruptedDefinition("payment");
        actual.getInventory().getItemById(105005).setCount(17);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertNotNull(readPending());
        assertTrue(artifacts().isEmpty());
        assertEquals(
                18,
                store.find(GameItem.class)
                        .field("ownerId")
                        .equal(10002)
                        .field("itemId")
                        .equal(105005)
                        .first()
                        .getCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "output"})
    void recoveryCanItselfBeInterruptedAndResumedWithoutAnotherCostOrArtifact(String phase)
            throws Exception {
        var id = interruptedDefinition("reserve");
        var outputId = actual.getArtifactTransmuterData().getPending().getRewardId();
        interruptAt(phase, outputId);
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        reload();
        recover(id, true);
        assertNull(readPending());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertEquals(1, artifacts().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoveryClearAcknowledgementFailureDoesNotRepeatTheCompletedDelivery(boolean committed)
            throws Exception {
        var id = interruptedDefinition("reserve");
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
                                    owner.getArtifactTransmuterData().getPending() == null;
                            if (!clearing || committed) DatabaseHelper.saveGameSync(owner);
                            if (clearing)
                                throw new IllegalStateException(
                                        "injected recovery clear acknowledgement");
                        },
                        DatabaseHelper::saveGameSync));
        assertThrows(IllegalStateException.class, () -> recover(id, true));
        assertNotNull(actual.getArtifactTransmuterData().getPending());
        reload();
        if (committed) assertThrows(IllegalStateException.class, () -> recover(id, true));
        else recover(id, true);
        assertNull(readPending());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertEquals(1, artifacts().size());
        assertEquals(1, actual.getArtifactTransmuterData().getDefinedCounts().get(15001));
    }

    @Test
    void recoveryUsesTheMaterialSlotFreedByItsExactRemainingPayment() throws Exception {
        assertTrue(actual.getInventory().removeItem(actual.getInventory().getItemById(105005), 20));
        var id = interruptedExtraction("reserve", true);
        var tab = actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
        replaceField(MaterialInventoryTab.class, tab, "maxCapacity", tab.getSize());
        recover(id, true);
        assertNull(readPending());
        assertEquals(0, actual.getInventory().getItemCountById(105003));
        assertEquals(1, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void recoveryCancellationDoesNotConsumeOrUnlockAnUnpaidRelicThatWasLaterLocked()
            throws Exception {
        var data =
                items.stream()
                        .filter(
                                x ->
                                        x.getItemType() == ItemType.ITEM_RELIQUARY
                                                && x.getRankLevel() == 5)
                        .findFirst()
                        .orElseThrow();
        var item = stock(data.getId(), 1);
        item.setLevel(5);
        DatabaseHelper.saveGameSync(item);
        var itemId = item.getObjectId();
        interruptAt("reserve", 105005);
        assertNotEquals(
                0,
                actual.getArtifactTransmuter()
                        .extract(
                                ReliquaryOfferExtractReq.newBuilder()
                                        .setScheduleId(700)
                                        .addReliquaryGuidList(item.getGuid())
                                        .build())
                        .getRetcode());
        var id = actual.getArtifactTransmuterData().getPending().getOperationId();
        item.setLocked(true);
        DatabaseHelper.saveGameSync(item);
        reload();
        recover(id, false);
        assertNull(readPending());
        assertEquals(1, artifacts().size());
        assertEquals(itemId, artifacts().get(0).getObjectId());
        assertTrue(artifacts().get(0).isLocked());
    }
}
