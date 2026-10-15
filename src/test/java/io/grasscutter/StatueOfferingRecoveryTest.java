package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.city.StatueOfferReceipt;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interrupt actual acknowledgements, cold-load Mongo, then explicitly reconcile the same intent.
 */
class StatueOfferingRecoveryTest extends StatueOfferingPersistenceTest {
    StatueOfferReceipt pending(Player target) {
        return Objects.requireNonNull(target.getSotsManager().getCityInfo(1).getPendingOffer());
    }

    void recover(Player target, String id, boolean resume) throws Exception {
        try {
            SotSManager.class
                    .getMethod("recoverOffering", int.class, String.class, boolean.class)
                    .invoke(target.getSotsManager(), 1, id, resume);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    @SuppressWarnings("unchecked")
    List<String> inspect(Player target, int city) throws Exception {
        return (List<String>)
                SotSManager.class
                        .getMethod("inspectOffering", int.class)
                        .invoke(target.getSotsManager(), city);
    }

    Player interrupted(boolean pay, boolean partialReward) throws Exception {
        var target = offerPlayer();
        stock(target, 107001, 2);
        var saves = new AtomicInteger();
        new SotSManager(
                        target,
                        () -> {
                            DatabaseHelper.saveGameSync(target);
                            if (!pay && saves.incrementAndGet() == 1)
                                throw new IllegalStateException("After acknowledged reservation");
                        },
                        items -> {
                            if (partialReward)
                                InvestigationRewardDelivery.grant(
                                        target,
                                        items.stream().filter(x -> x.getId() == 201).toList(),
                                        ActionReason.CityLevelupReward);
                            throw new IllegalStateException("Interrupted rewards");
                        },
                        DatabaseHelper::saveGameSync)
                .levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        assertNotNull(pending(restored));
        return restored;
    }

    void completed(Player target) throws Exception {
        var restored = read();
        assertNull(restored.getSotsManager().getCityInfo(1).getPendingOffer());
        assertEquals(2, restored.getSotsManager().getCityInfo(1).getLevel());
        assertEquals(1, restored.getInventory().getItemCountById(107001));
        assertEquals(10, restored.getPrimogems());
        assertEquals(80, restored.getExp());
        assertEquals(5, restored.getInventory().getItemCountById(305));
        assertEquals(10700, restored.getProperty(PlayerProperty.PROP_MAX_STAMINA));
    }

    @Test
    void newReceiptFreezesPersistentCostAndRewardIdentitiesBeforeAnyPayment() throws Exception {
        var target = interrupted(false, false);
        var json = JsonUtils.toJson(pending(target)).getAsJsonObject();
        assertTrue(json.has("version"), "Interrupted offerings need recoverable economics");
        assertEquals(1, json.get("version").getAsInt());
        assertEquals(
                target.getInventory().getItemById(107001).getObjectId().toHexString(),
                json.get("costObjectId").getAsString());
        var material =
                java.util.stream.StreamSupport.stream(
                                json.getAsJsonArray("rewards").spliterator(), false)
                        .map(x -> x.getAsJsonObject())
                        .filter(x -> x.get("id").getAsInt() == 305)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, material.get("before").getAsInt());
        assertEquals(5, material.get("after").getAsInt());
        assertTrue(org.bson.types.ObjectId.isValid(material.get("objectId").getAsString()));
    }

    @Test
    void acknowledgedReservationResumesAfterColdLoad() throws Exception {
        var target = interrupted(false, false);
        recover(target, pending(target).getOperationId(), true);
        completed(target);
    }

    @Test
    void acknowledgedPaymentResumesWithoutChargingAgain() throws Exception {
        var target = interrupted(true, false);
        recover(target, pending(target).getOperationId(), true);
        completed(target);
    }

    @Test
    void partialCurrencyAwardIsNotRepeatedAndChangedPolicyDoesNotRewriteFrozenAwards()
            throws Exception {
        var target = interrupted(true, true);
        assertEquals(10, target.getPrimogems());
        replaceField(
                emu.grasscutter.config.ConfigContainer.Game.class,
                emu.grasscutter.Grasscutter.getConfig().server.game,
                "gameOptions",
                JsonUtils.decode(
                        "{statueRewards:{referenceMultipliers:true}}",
                        emu.grasscutter.config.ConfigContainer.GameOptions.class));
        recover(target, pending(target).getOperationId(), true);
        completed(target);
    }

    @Test
    void untouchedReservationCanBeCancelledButPaidIntentCannot() throws Exception {
        var target = interrupted(false, false);
        recover(target, pending(target).getOperationId(), false);
        var restored = read();
        assertNull(restored.getSotsManager().getCityInfo(1).getPendingOffer());
        assertEquals(2, restored.getInventory().getItemCountById(107001));
        assertEquals(0, restored.getPrimogems());
        assertEquals(1, restored.getSotsManager().getCityInfo(1).getLevel());
    }

    @Test
    void paidIntentAndWrongOperationIdCannotBeCancelledOrReplayed() throws Exception {
        var target = interrupted(true, true);
        String id = pending(target).getOperationId();
        assertThrows(IllegalStateException.class, () -> recover(target, "another-operation", true));
        assertThrows(IllegalStateException.class, () -> recover(target, id, false));
        assertEquals(10, target.getPrimogems());
        assertEquals(1, target.getInventory().getItemCountById(107001));
        assertEquals(id, pending(read()).getOperationId());
    }

    @Test
    void changedEconomicsRefuseRecoveryWithoutClearingReceiptOrWritingPlayer() throws Exception {
        var target = interrupted(true, false);
        target.setPrimogems(99);
        DatabaseHelper.saveGameSync(target);
        String before =
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", target.getUid()))
                        .first()
                        .toJson();
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(
                before,
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", target.getUid()))
                        .first()
                        .toJson());
        assertEquals(1, target.getSotsManager().getCityInfo(1).getLevel());
        assertEquals(0, target.getInventory().getItemCountById(305));
    }

    @Test
    void auditDoesNotCreateCitiesOrDecodeAndSaveAnotherPlayer() throws Exception {
        var target = interrupted(true, true);
        var before =
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", target.getUid()))
                        .first()
                        .toJson();
        var audit = inspect(target, 1);
        assertTrue(audit.stream().anyMatch(x -> x.contains(pending(target).getOperationId())));
        assertFalse(target.getCityInfoData().containsKey(8));
        assertFalse(inspect(target, 8).isEmpty());
        assertFalse(target.getCityInfoData().containsKey(8));
        assertEquals(
                before,
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", target.getUid()))
                        .first()
                        .toJson());
    }

    @Test
    void legacyReceiptWithoutEconomicProofRemainsPending() throws Exception {
        var target = offerPlayer();
        var receipt =
                new StatueOfferReceipt(
                        107001,
                        1,
                        1,
                        1,
                        0,
                        2,
                        0,
                        7,
                        10000,
                        10700,
                        List.of(new emu.grasscutter.data.common.ItemParamData(201, 10)));
        target.getSotsManager().getCityInfo(1).setPendingOffer(receipt);
        DatabaseHelper.saveGameSync(target);
        assertThrows(
                IllegalStateException.class, () -> recover(target, receipt.getOperationId(), true));
        assertEquals(receipt.getOperationId(), pending(read()).getOperationId());
    }

    @Test
    void deletedCostAcknowledgedBeforeReceiptUpdateIsNotDebitedAgain() throws Exception {
        var target = offerPlayer();
        stock(target, 107001, 1);
        new SotSManager(
                        target,
                        () -> DatabaseHelper.saveGameSync(target),
                        null,
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            throw new IllegalStateException("After acknowledged cost deletion");
                        })
                .levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        assertEquals(1, pending(restored).getState());
        assertEquals(0, restored.getInventory().getItemCountById(107001));
        recover(restored, pending(restored).getOperationId(), true);
        var done = read();
        assertNull(done.getSotsManager().getCityInfo(1).getPendingOffer());
        assertEquals(0, done.getInventory().getItemCountById(107001));
        assertEquals(10, done.getPrimogems());
        assertEquals(80, done.getExp());
        assertEquals(5, done.getInventory().getItemCountById(305));
    }

    @Test
    void materialAcknowledgementBeforePlayerWriteKeepsTheReservedOutputIdentity() throws Exception {
        var target = offerPlayer();
        stock(target, 107001, 2);
        new SotSManager(
                        target,
                        () -> DatabaseHelper.saveGameSync(target),
                        null,
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (item.getItemId() == 305)
                                throw new IllegalStateException("After material acknowledgement");
                        })
                .levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        var id = restored.getInventory().getItemById(305).getObjectId();
        assertNotNull(pending(restored));
        recover(restored, pending(restored).getOperationId(), true);
        completed(restored);
        assertEquals(id, read().getInventory().getItemById(305).getObjectId());
    }

    @Test
    void appliedReceiptOnlyClearsAndCannotBeReplayed() throws Exception {
        var target = offerPlayer();
        stock(target, 107001, 2);
        new SotSManager(
                        target,
                        () -> {
                            if (target.getSotsManager().getCityInfo(1).getPendingOffer() == null)
                                throw new IllegalStateException("Before final clear write");
                            DatabaseHelper.saveGameSync(target);
                        },
                        null,
                        DatabaseHelper::saveGameSync)
                .levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        String id = pending(restored).getOperationId();
        assertEquals(3, pending(restored).getState());
        recover(restored, id, true);
        completed(restored);
        assertThrows(IllegalStateException.class, () -> recover(restored, id, true));
        completed(restored);
    }

    @Test
    void crossingAdventureLevelDoesNotMistakeItsReducedExpForMissingReward() throws Exception {
        for (int level : new int[] {20, 21})
            put(
                    GameData.getPlayerLevelDataMap(),
                    level,
                    JsonUtils.decode(
                            "{level:" + level + ",exp:" + (level == 20 ? 100 : 200) + "}",
                            emu.grasscutter.data.excels.PlayerLevelData.class));
        var target = offerPlayer();
        target.setProperty(PlayerProperty.PROP_PLAYER_EXP, 70);
        stock(target, 107001, 2);
        new SotSManager(
                        target,
                        () -> DatabaseHelper.saveGameSync(target),
                        items -> {
                            InvestigationRewardDelivery.grant(
                                    target,
                                    items.stream().filter(x -> x.getId() == 102).toList(),
                                    ActionReason.CityLevelupReward);
                            throw new IllegalStateException("After adventure level crossing");
                        },
                        DatabaseHelper::saveGameSync)
                .levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        assertEquals(21, restored.getLevel());
        assertEquals(50, restored.getExp());
        recover(restored, pending(restored).getOperationId(), true);
        var done = read();
        assertNull(done.getSotsManager().getCityInfo(1).getPendingOffer());
        assertEquals(21, done.getLevel());
        assertEquals(50, done.getExp());
        assertEquals(10, done.getPrimogems());
        assertEquals(5, done.getInventory().getItemCountById(305));
    }

    @Test
    void liveAndStoredDisagreementRequiresReloadBeforeRecovery() throws Exception {
        var target = interrupted(true, true);
        target.setPrimogems(0);
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(10, read().getPrimogems());
        var restored = read();
        ownWorld(restored);
        recover(restored, pending(restored).getOperationId(), true);
        completed(restored);
    }

    @Test
    void restoredCountsOnADifferentCostObjectCannotMasqueradeAsUnpaid() throws Exception {
        var target = interrupted(false, false);
        var old = target.getInventory().getItemById(107001);
        assertTrue(target.getInventory().removeItem(old, old.getCount()));
        DatabaseHelper.saveGameSync(old);
        stock(target, 107001, 2);
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(2, read().getInventory().getItemCountById(107001));
        assertEquals(0, read().getPrimogems());
    }

    @Test
    void capacityIsCheckedForAllRemainingRewardsBeforeAnyNewDebit() throws Exception {
        var target = interrupted(false, false);
        var before = target.getInventory();
        var tab = new MaterialInventoryTab(before.getItems().size());
        for (var item : before.getItems().values()) tab.onAddItem(item);
        replaceField(
                Player.class,
                target,
                "inventory",
                new Inventory(target) {
                    @Override
                    public InventoryTab getInventoryTab(ItemType type) {
                        return type == ItemType.ITEM_MATERIAL ? tab : super.getInventoryTab(type);
                    }
                });
        // Preserve live stack identity in both inventory indexes, only shrink the capacity.
        target.getInventory().getItems().putAll(before.getItems());
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(2, read().getInventory().getItemCountById(107001));
        assertEquals(0, read().getPrimogems());
    }

    @Test
    void recoveringInAnotherPlayersWorldIsRejectedBeforeEconomicChanges() throws Exception {
        var target = interrupted(true, false);
        target.setWorld(player.getWorld());
        target.setScene(player.getScene());
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(0, read().getPrimogems());
        assertEquals(1, read().getInventory().getItemCountById(107001));
    }

    @Test
    void managementCommandAuditsThenResumesByTheExactOperationId() throws Exception {
        var target = interrupted(true, false);
        var command =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.StatueCommand")
                                .getConstructor()
                                .newInstance();
        command.execute(null, target, List.of("audit", "1"));
        assertNotNull(pending(target));
        command.execute(null, target, List.of("resume", "1", pending(target).getOperationId()));
        completed(target);
    }

    @Test
    void aMaterialRewardChangedToAnArtifactIsRejectedBeforeCharging() throws Exception {
        var target = interrupted(false, false);
        put(
                GameData.getItemDataMap(),
                305,
                JsonUtils.decode(
                        "{id:305,itemType:'ITEM_RELIQUARY',rankLevel:5}",
                        emu.grasscutter.data.excels.ItemData.class));
        assertThrows(
                RuntimeException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(2, target.getInventory().getItemCountById(107001));
        assertEquals(0, target.getPrimogems());
        assertEquals(2, read().getInventory().getItemCountById(107001));
    }

    @Test
    void aCostStackWithAnotherResourceDefinitionIsRejectedBeforeCharging() throws Exception {
        var target = interrupted(false, false);
        target.getInventory().getItemById(107001).setItemData(GameData.getItemDataMap().get(305));
        assertThrows(
                IllegalStateException.class,
                () -> recover(target, pending(target).getOperationId(), true));
        assertEquals(2, target.getInventory().getItemCountById(107001));
        assertEquals(0, target.getPrimogems());
    }
}
