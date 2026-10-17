package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Cold-load real interrupted food uses and explicitly resolve their cost without replaying effects.
 */
class ItemUseRecoveryTest extends FoodUsePaymentPersistenceTest {
    Object recovery(Player player, Consumer<Object> save) throws Exception {
        var type = Class.forName("emu.grasscutter.game.inventory.ItemUseRecovery");
        return save == null
                ? type.getConstructor(Player.class).newInstance(player)
                : type.getConstructor(Player.class, Consumer.class).newInstance(player, save);
    }

    void resolve(Player player, String id, String action, Consumer<Object> save) throws Exception {
        var recovery = recovery(player, save);
        try {
            recovery.getClass()
                    .getMethod("resolve", String.class, String.class)
                    .invoke(recovery, id, action);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    void resolve(Player player, String id, String action) throws Exception {
        resolve(player, id, action, null);
    }

    @SuppressWarnings("unchecked")
    List<String> inspect(Player player) throws Exception {
        var recovery = recovery(player, null);
        return (List<String>) recovery.getClass().getMethod("inspect").invoke(recovery);
    }

    Player interruptedAt(int state, boolean afterWrite) throws Exception {
        assertNull(
                use(
                        new InventorySystem(
                                player.server,
                                () -> 100000,
                                value -> {
                                    boolean fail =
                                            value == target
                                                    && target.getPendingItemUse() != null
                                                    && target.getPendingItemUse().getState()
                                                            == state;
                                    if (afterWrite || !fail) DatabaseHelper.saveGameSync(value);
                                    if (fail)
                                        throw new IllegalStateException(
                                                "Interrupted at food stage " + state);
                                })));
        return read();
    }

    String operation(Player player) {
        return Objects.requireNonNull(player.getPendingItemUse()).getOperationId();
    }

    void rejectedActions() throws Exception {
        replaceField(
                emu.grasscutter.data.excels.ItemData.class,
                food.getItemData(),
                "itemUseActions",
                List.of(new ItemUseAction()));
    }

    Document playerDocument() {
        return store.getDatabase()
                .getCollection("players")
                .find(new Document("_id", 10002))
                .first();
    }

    @Test
    void cancellingAnUnspentCostRetiresItsDelayedOriginalDebit() throws Exception {
        var delayed = new DelayedItemSave(food, store, false, false);
        assertNull(use(new InventorySystem(player.server)));
        var restored = read();
        String id = operation(restored);
        assertEquals(1, restored.getInventory().getItemCountById(food.getItemId()));
        resolve(restored, id, "refund-cost");
        assertFalse(delayed.release());
        assertNull(read().getPendingItemUse());
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void aPersistedRefundIsNotRepeatedAfterThatFoodIsConsumedAgain() throws Exception {
        var restored = interruptedAt(4, false);
        String id = operation(restored);
        assertThrows(
                RuntimeException.class,
                () ->
                        resolve(
                                restored,
                                id,
                                "refund-cost",
                                value -> {
                                    DatabaseHelper.saveGameSync(value);
                                    if (value instanceof GameItem)
                                        throw new IllegalStateException(
                                                "Lost food refund acknowledgement");
                                }));
        assertTrue(restored.getInventory().removeItem(food.getItemId(), 1));
        assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        var again = read();
        resolve(again, id, "refund-cost");
        assertNull(read().getPendingItemUse());
        assertEquals(0, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void aRefundDecisionThatWasNotPersistedCannotReviveTheConsumedIdentity() throws Exception {
        var restored = interruptedAt(4, false);
        String id = operation(restored);
        assertThrows(
                RuntimeException.class,
                () ->
                        resolve(
                                restored,
                                id,
                                "refund-cost",
                                value -> {
                                    if (value == restored
                                            && restored.getPendingItemUse() != null
                                            && restored.getPendingItemUse().getState() == 5) return;
                                    DatabaseHelper.saveGameSync(value);
                                }));
        var again = read();
        assertEquals(0, again.getInventory().getItemCountById(food.getItemId()));
        assertEquals(2, again.getPendingItemUse().getState());
    }

    @Test
    void paidButUnstartedFoodCanRefundItsDeletedSourceAfterColdLoad() throws Exception {
        var restored = interruptedAt(4, false);
        assertEquals(2, restored.getPendingItemUse().getState());
        var avatarBefore = savedAvatar().toJson();
        resolve(restored, operation(restored), "refund-cost");
        var done = read();
        assertNull(done.getPendingItemUse());
        assertEquals(1, done.getInventory().getItemCountById(food.getItemId()));
        assertEquals(
                food.getObjectId(),
                done.getInventory().getItemById(food.getItemId()).getObjectId());
        assertEquals(
                avatarBefore,
                savedAvatar().toJson(),
                "Cost recovery must not infer avatar outcomes");
    }

    @Test
    void unspentReservationClearsWithoutGivingAnotherFood() throws Exception {
        var restored = interruptedAt(1, true);
        assertEquals(1, restored.getPendingItemUse().getState());
        resolve(restored, operation(restored), "refund-cost");
        assertNull(read().getPendingItemUse());
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void acknowledgedDeletionBeforePaidMarkerIsRefundedOnlyOnce() throws Exception {
        assertNull(
                use(
                        new InventorySystem(
                                player.server,
                                () -> 100000,
                                value -> {
                                    DatabaseHelper.saveGameSync(value);
                                    if (value instanceof GameItem)
                                        throw new IllegalStateException("After cost deletion");
                                })));
        var restored = read();
        assertEquals(1, restored.getPendingItemUse().getState());
        assertEquals(0, restored.getInventory().getItemCountById(food.getItemId()));
        String id = operation(restored);
        resolve(restored, id, "refund-cost");
        assertThrows(IllegalStateException.class, () -> resolve(restored, id, "refund-cost"));
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void uncertainEffectCannotRefundButCanExplicitlyKeepConsumptionWithoutReplaying()
            throws Exception {
        var actions = new ArrayList<>(food.getItemData().getItemUseActions());
        actions.add(
                new ItemUseAction() {
                    @Override
                    public boolean useItem(UseItemParams params) {
                        throw new IllegalStateException("After healing");
                    }
                });
        replaceField(
                emu.grasscutter.data.excels.ItemData.class,
                food.getItemData(),
                "itemUseActions",
                actions);
        assertNull(use(new InventorySystem(player.server)));
        var restored = read();
        assertEquals(4, restored.getPendingItemUse().getState());
        String id = operation(restored);
        String before = savedAvatar().toJson();
        assertThrows(IllegalStateException.class, () -> resolve(restored, id, "refund-cost"));
        resolve(restored, id, "settle");
        assertNull(read().getPendingItemUse());
        assertEquals(0, read().getInventory().getItemCountById(food.getItemId()));
        assertEquals(before, savedAvatar().toJson());
        assertThrows(IllegalStateException.class, () -> resolve(restored, id, "settle"));
    }

    @Test
    void interruptedRefundRecreatesTheOriginalObjectIdWithoutAnObtainEvent() throws Exception {
        rejectedActions();
        var itemWrites = new AtomicInteger();
        assertNull(
                use(
                        new InventorySystem(
                                player.server,
                                () -> 100000,
                                value -> {
                                    if (value instanceof GameItem
                                            && itemWrites.incrementAndGet() == 2)
                                        throw new IllegalStateException("Before refund write");
                                    DatabaseHelper.saveGameSync(value);
                                })));
        var restored = read();
        assertEquals(5, restored.getPendingItemUse().getState());
        assertEquals(0, restored.getInventory().getItemCountById(food.getItemId()));
        resolve(restored, operation(restored), "refund-cost");
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
        assertEquals(
                food.getObjectId(),
                read().getInventory().getItemById(food.getItemId()).getObjectId());
    }

    @Test
    void completedAndRefundedReceiptsOnlyClearTheirAlreadyConfirmedOutcome() throws Exception {
        var restored = interruptedAt(3, true);
        assertEquals(3, restored.getPendingItemUse().getState());
        var before = savedAvatar().toJson();
        resolve(restored, operation(restored), "settle");
        assertNull(read().getPendingItemUse());
        assertEquals(before, savedAvatar().toJson());
        assertEquals(0, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void refundedReceiptDoesNotGiveAnotherItemWhenCleared() throws Exception {
        rejectedActions();
        var restored = interruptedAt(6, true);
        assertEquals(6, restored.getPendingItemUse().getState());
        resolve(restored, operation(restored), "refund-cost");
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
        assertNull(read().getPendingItemUse());
    }

    @Test
    void auditProjectsOnlyTheReceiptAndDoesNotSaveOrCreateInventory() throws Exception {
        var restored = interruptedAt(4, false);
        var before = playerDocument().toJson();
        var itemsBefore = restored.getInventory().getItems().size();
        var rows = inspect(restored);
        assertTrue(rows.stream().anyMatch(x -> x.contains(operation(restored))));
        assertTrue(rows.stream().anyMatch(x -> x.contains(food.getObjectId().toHexString())));
        assertEquals(before, playerDocument().toJson());
        assertEquals(itemsBefore, restored.getInventory().getItems().size());
    }

    @Test
    void wrongIdAndLegacySelectionRecordsCannotBeClearedAsFood() throws Exception {
        var restored = interruptedAt(4, false);
        String id = operation(restored);
        assertThrows(
                IllegalStateException.class, () -> resolve(restored, "wrong-id", "refund-cost"));
        replaceField(ItemUseReceipt.class, restored.getPendingItemUse(), "kind", "");
        DatabaseHelper.saveGameSync(restored);
        assertThrows(IllegalStateException.class, () -> resolve(restored, id, "refund-cost"));
        assertEquals(id, operation(read()));
    }

    @Test
    void aDifferentStackWithTheSameFoodCountCannotMatchTheFrozenSource() throws Exception {
        var restored = interruptedAt(1, true);
        var source = restored.getInventory().getItemById(food.getItemId());
        assertTrue(restored.getInventory().removeItem(source, 1));
        DatabaseHelper.saveGameSync(source);
        var another = new GameItem(food.getItemData(), 1);
        assertTrue(restored.getInventory().addItem(another));
        DatabaseHelper.saveGameSync(another);
        assertThrows(
                IllegalStateException.class,
                () -> resolve(restored, operation(restored), "refund-cost"));
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void insufficientCapacityRejectsBeforeChangingTheRecoveryDecision() throws Exception {
        var restored = interruptedAt(4, false);
        var inventory = restored.getInventory();
        var full = new MaterialInventoryTab(inventory.getItems().size());
        inventory.getItems().values().forEach(full::onAddItem);
        inventory.getInventoryTypes().put(ItemType.ITEM_MATERIAL.getValue(), full);
        var before = playerDocument().toJson();
        assertThrows(
                IllegalStateException.class,
                () -> resolve(restored, operation(restored), "refund-cost"));
        assertEquals(before, playerDocument().toJson());
        assertEquals(0, restored.getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void failedClearAfterRefundCanBeRetriedWithoutAnotherGrant() throws Exception {
        var restored = interruptedAt(4, false);
        String id = operation(restored);
        assertThrows(
                IllegalStateException.class,
                () ->
                        resolve(
                                restored,
                                id,
                                "refund-cost",
                                value -> {
                                    if (value == restored && restored.getPendingItemUse() == null)
                                        throw new IllegalStateException("Before recovery clear");
                                    DatabaseHelper.saveGameSync(value);
                                }));
        var retry = read();
        assertEquals(6, retry.getPendingItemUse().getState());
        resolve(retry, id, "refund-cost");
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
        assertNull(read().getPendingItemUse());
    }

    @Test
    void acknowledgedRefundBeforeItsMarkerIsNotGivenTwice() throws Exception {
        var restored = interruptedAt(4, false);
        String id = operation(restored);
        assertThrows(
                IllegalStateException.class,
                () ->
                        resolve(
                                restored,
                                id,
                                "refund-cost",
                                value -> {
                                    DatabaseHelper.saveGameSync(value);
                                    if (value instanceof GameItem)
                                        throw new IllegalStateException(
                                                "After refund acknowledgement");
                                }));
        var retry = read();
        assertEquals(5, retry.getPendingItemUse().getState());
        assertEquals(1, retry.getInventory().getItemCountById(food.getItemId()));
        resolve(retry, id, "refund-cost");
        assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
        assertNull(read().getPendingItemUse());
    }

    @Test
    void liveAndStoredDisagreementCannotBeOverwrittenByRecovery() throws Exception {
        rejectedActions();
        var writes = new AtomicInteger();
        assertNull(
                use(
                        new InventorySystem(
                                player.server,
                                () -> 100000,
                                value -> {
                                    if (value instanceof GameItem && writes.incrementAndGet() == 2)
                                        throw new IllegalStateException("Refund not stored");
                                    DatabaseHelper.saveGameSync(value);
                                })));
        assertEquals(1, target.getInventory().getItemCountById(food.getItemId()));
        var before = playerDocument().toJson();
        assertThrows(
                IllegalStateException.class,
                () -> resolve(target, operation(target), "refund-cost"));
        assertEquals(before, playerDocument().toJson());
        assertEquals(0, read().getInventory().getItemCountById(food.getItemId()));
    }

    @Test
    void acceptedConsumptionCannotSwitchToRefundAfterAnUncertainClear() throws Exception {
        var restored = interruptedAt(3, true);
        replaceField(ItemUseReceipt.class, restored.getPendingItemUse(), "state", 4);
        DatabaseHelper.saveGameSync(restored);
        String id = operation(restored);
        assertThrows(
                IllegalStateException.class,
                () ->
                        resolve(
                                restored,
                                id,
                                "settle",
                                value -> {
                                    if (value == restored && restored.getPendingItemUse() == null)
                                        throw new IllegalStateException(
                                                "After decision, before clear");
                                    DatabaseHelper.saveGameSync(value);
                                }));
        var retry = read();
        assertEquals(7, retry.getPendingItemUse().getState());
        assertThrows(IllegalStateException.class, () -> resolve(retry, id, "refund-cost"));
        var before = savedAvatar().toJson();
        resolve(retry, id, "settle");
        assertEquals(0, read().getInventory().getItemCountById(food.getItemId()));
        assertEquals(before, savedAvatar().toJson());
    }

    @Test
    void aRecycledRuntimeGuidCannotDeleteAnotherInventoryEntry() throws Exception {
        var restored = interruptedAt(4, false);
        long otherGuid = restored.getInventory().getItemById(220003).getGuid();
        replaceField(ItemUseReceipt.class, restored.getPendingItemUse(), "sourceGuid", otherGuid);
        DatabaseHelper.saveGameSync(restored);
        var packets = new ArrayList<emu.grasscutter.net.packet.BasePacket>();
        var session =
                new emu.grasscutter.server.game.GameSession(player.server) {
                    @Override
                    public void send(emu.grasscutter.net.packet.BasePacket packet) {
                        packets.add(packet);
                    }
                };
        session.setPlayer(restored);
        restored.setSession(session);
        resolve(restored, operation(restored), "refund-cost");
        assertNotEquals(otherGuid, restored.getInventory().getItemById(food.getItemId()).getGuid());
        assertEquals(otherGuid, restored.getInventory().getItemById(220003).getGuid());
        assertFalse(
                packets.stream()
                        .anyMatch(
                                x ->
                                        x.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .StoreItemDelNotify));
        var packet =
                packets.stream()
                        .filter(
                                x ->
                                        x.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .PlayerStoreNotify)
                        .findFirst()
                        .orElseThrow();
        var inventory =
                emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify.parseFrom(
                        packet.getData());
        assertTrue(
                inventory.getItemListList().stream()
                        .anyMatch(x -> x.getItemId() == 220003 && x.getGuid() == otherGuid));
        assertTrue(
                inventory.getItemListList().stream()
                        .anyMatch(
                                x ->
                                        x.getItemId() == food.getItemId()
                                                && x.getMaterial().getCount() == 1));
    }

    @Test
    void realCommandDispatchUsesTargetPermissionsAndResolvesOnlyAfterAuthorization()
            throws Exception {
        var restored = interruptedAt(4, false);
        restored.getSession().setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
        String id = operation(restored);
        var command =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.ItemUseCommand")
                                .getConstructor()
                                .newInstance();
        var old = emu.grasscutter.Grasscutter.getPermissionHandler();
        var checked = new ArrayList<String>();
        try {
            emu.grasscutter.Grasscutter.setPermissionHandler(
                    new emu.grasscutter.command.PermissionHandler() {
                        public boolean EnablePermissionCommand() {
                            return true;
                        }

                        public boolean checkPermission(
                                Player sender, Player target, String own, String others) {
                            assertSame(restored, target);
                            checked.add(own);
                            checked.add(others);
                            return false;
                        }
                    });
            var map =
                    new emu.grasscutter.command.CommandMap(false)
                            .registerCommand("itemuse", command);
            map.invoke(null, restored, "itemuse refund-cost " + id);
            assertEquals(List.of("server.itemuse", "server.itemuse.others"), checked);
            assertEquals(id, operation(read()));
            emu.grasscutter.Grasscutter.setPermissionHandler(
                    new emu.grasscutter.command.DefaultPermissionHandler());
            map.invoke(null, restored, "itemuse status");
            assertEquals(id, operation(read()));
            map.invoke(null, restored, "itemuse refund-cost " + id);
            assertNull(read().getPendingItemUse());
            assertEquals(1, read().getInventory().getItemCountById(food.getItemId()));
        } finally {
            emu.grasscutter.Grasscutter.setPermissionHandler(old);
        }
    }
}
