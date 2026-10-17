package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/** Real item saves and deletions through the ordinary inventory persistence entry points. */
class ItemSaveFenceMongoTest {
    static final int ITEM = 104003, UID = 74441;
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    MongoClient client;
    Datastore store, previous;
    ItemData original, definition;
    Player player;
    ObjectId id;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        previous = DatabaseManager.getGameDatastore();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_item_fence_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(GameItem.class);
        setStore(store);
        original = GameData.getItemDataMap().get(ITEM);
        definition =
                new Gson()
                        .fromJson(
                                "{id:104003,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_EXP_FRUIT',stackLimit:99999}",
                                ItemData.class);
        definition.onLoad();
        GameData.getItemDataMap().put(ITEM, definition);
        player = new Player();
        player.setUid(UID);
    }

    static void setStore(Datastore ds) throws Exception {
        var f = DatabaseManager.class.getDeclaredField("gameDatastore");
        f.setAccessible(true);
        f.set(null, ds);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client == null) return;
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        setStore(previous);
        if (original == null) GameData.getItemDataMap().remove(ITEM);
        else GameData.getItemDataMap().put(ITEM, original);
        store.getDatabase().drop();
        client.close();
    }

    GameItem fresh() {
        var item = new GameItem(definition, 10);
        item.setOwner(player);
        return item;
    }

    GameItem reserved() {
        var item = new GameItem(definition, 10);
        item.restorePersistenceId(id);
        item.setOwner(player);
        return item;
    }

    GameItem seed() {
        var item = fresh();
        DatabaseHelper.saveGameSync(item);
        id = item.getObjectId();
        return item;
    }

    GameItem read() {
        return store.find(GameItem.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("id", id))
                .first();
    }

    Document row() {
        return store.getDatabase().getCollection("items").find(new Document("_id", id)).first();
    }

    @Test
    void anOldCopyCannotRollBackACommittedStack() {
        seed();
        var old = read();
        var current = read();
        current.setCount(25);
        DatabaseHelper.saveGameSync(current);
        var saved = row();
        old.setCount(2);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(old));
        assertEquals(saved, row());
    }

    @Test
    void queuedOldSavesCannotRollBackANewerResult() throws Exception {
        seed();
        var old = read();
        var current = read();
        long failures = DatabaseHelper.getEventExecutorItem().snapshot().failed();
        Document saved;
        try (var held = new Held()) {
            old.save();
            current.setCount(24);
            current.setLevel(70);
            DatabaseHelper.saveGameSync(current);
            saved = row();
        }
        assertEquals(saved, row());
        assertEquals(failures + 1, DatabaseHelper.getEventExecutorItem().snapshot().failed());
    }

    @Test
    void aStaleDeleteCannotEraseANewerResult() throws Exception {
        seed();
        var old = read();
        var current = read();
        current.setCount(25);
        DatabaseHelper.saveGameSync(current);
        var saved = row();
        DatabaseHelper.deleteItem(old);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(saved, row());
    }

    @Test
    void aConsumedItemLeavesATerminalRecordOutsideTheInventory() {
        var item = seed();
        item.setCount(0);
        DatabaseHelper.saveGameSync(item);
        assertNotNull(row());
        assertEquals(true, row().getBoolean("deleted"));
        assertEquals(0, row().getInteger("count", 0));
        assertTrue(DatabaseHelper.getInventoryItems(player).isEmpty());
    }

    @Test
    void aLoadedCopyCannotRecreateAConsumedItem() {
        var current = seed();
        var old = read();
        current.setCount(0);
        DatabaseHelper.saveGameSync(current);
        var removed = row();
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(old));
        assertEquals(removed, row());
    }

    @Test
    void aFreshReservedCopyCannotOverwriteALiveOrConsumedIdentity() {
        var current = seed();
        var duplicate = reserved();
        var saved = row();
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(duplicate));
        assertEquals(saved, row());
        current.setCount(0);
        DatabaseHelper.saveGameSync(current);
        var removed = row();
        var another = reserved();
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(another));
        assertEquals(removed, row());
    }

    @Test
    void aLegacyReadDoesNotMigrateUntilAConditionalWrite() {
        var item = fresh();
        item.ensurePersistenceId();
        id = item.getObjectId();
        var legacy = store.getMapper().toDocument(item);
        legacy.remove("saveStamp");
        store.getDatabase().getCollection("items").insertOne(legacy);
        var old = read();
        var current = read();
        assertEquals(legacy, row());
        current.setCount(20);
        DatabaseHelper.saveGameSync(current);
        var saved = row();
        assertNotNull(saved.get("saveStamp"));
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(old));
        assertEquals(saved, row());
    }

    @Test
    void oneLiveInstanceKeepsItsLatestStateAcrossQueuedAndSyncSaves() throws Exception {
        var item = seed();
        try (var held = new Held()) {
            item.save();
            item.setCount(20);
            DatabaseHelper.saveGameSync(item);
            item.setLevel(3);
        }
        assertEquals(20, read().getCount());
        assertEquals(3, read().getLevel());
    }

    @Test
    void itemIdentityCannotChangeAfterLoading() {
        seed();
        var item = read();
        var saved = row();
        item.setItemId(ITEM + 1);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(saved, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainReplacementsAreRecognizedOrRetiredBeforeAnotherResult(boolean after)
            throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, after);
        item.setCount(20);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        item.setCount(30);
        DatabaseHelper.saveGameSync(item);
        assertEquals(30, read().getCount());
        assertFalse(fault.release());
        assertEquals(30, read().getCount());
    }

    @Test
    void resolvingAnUnsentWriteChangesOnlyMetadataUntilTheNextBusinessSave() throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, false);
        item.setCount(20);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        var before = row();
        item.getSaveGuard().resolvePending(store);
        var after = row();
        assertNotEquals(before.remove("saveStamp"), after.remove("saveStamp"));
        assertEquals(before, after);
        assertEquals(10, after.getInteger("count"));
        assertFalse(fault.release());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainInsertsCannotRecreateAnItemAfterItsTerminalSave(boolean after) throws Exception {
        var item = fresh();
        id = item.ensurePersistenceId();
        var fault = new DelayedItemSave(item, store, true, after);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        item.setCount(0);
        DatabaseHelper.saveGameSync(item);
        var removed = row();
        assertTrue(removed.getBoolean("deleted"));
        assertFalse(fault.release());
        assertEquals(removed, row());
        assertTrue(DatabaseHelper.getInventoryItems(player).isEmpty());
    }

    @Test
    void aLateCommittedDeleteRemainsTerminalDuringRecovery() throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, false);
        item.setCount(0);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        fault.commitBeforeNextCheck();
        item.setCount(10);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        assertTrue(row().getBoolean("deleted"));
        assertTrue(item.isDeleted());
        assertEquals(0, item.getCount());
    }

    @Test
    void aCancelledUnknownDeleteCannotArriveAfterANewStackResult() throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, false);
        item.setCount(0);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        item.setCount(35);
        DatabaseHelper.saveGameSync(item);
        assertFalse(fault.release());
        assertEquals(35, read().getCount());
        assertFalse(read().isDeleted());
    }

    @Test
    void lostDeleteAcknowledgementsCanBeConfirmedIdempotently() throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, true);
        item.setCount(0);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        var removed = row();
        DatabaseHelper.saveGameSync(item);
        DatabaseHelper.deleteItem(item);
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(removed, row());
        assertFalse(fault.release());
    }

    @Test
    void anUnknownInsertCannotAdoptAnotherWritersRow() throws Exception {
        var item = fresh();
        id = item.ensurePersistenceId();
        new DelayedItemSave(item, store, true, false);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        var foreign = store.getMapper().toDocument(item);
        foreign.remove("saveStamp");
        store.getDatabase().getCollection("items").insertOne(foreign);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(foreign, row());
    }

    @Test
    void aLoadedCopyCannotSwitchItsDatastore() throws Exception {
        seed();
        var item = read();
        var other = Morphia.createDatastore(client, store.getDatabase().getName() + "_other");
        other.getMapper().map(GameItem.class);
        try {
            setStore(other);
            assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
            assertEquals(0, other.getDatabase().getCollection("items").countDocuments());
        } finally {
            setStore(store);
            other.getDatabase().drop();
        }
    }

    @Test
    void ownerIdentityCannotBeChangedAfterLoading() {
        seed();
        var item = read();
        var saved = row();
        var other = new Player();
        other.setUid(UID + 1);
        item.setOwner(other);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(saved, row());
    }

    @ParameterizedTest
    @ValueSource(strings = {"revision", "token", "extra", "deleted"})
    void invalidStoredMetadataIsNotAcceptedOnLoad(String bad) {
        seed();
        var raw = row();
        var stamp = raw.get("saveStamp", Document.class);
        switch (bad) {
            case "revision" -> stamp.put("revision", 1);
            case "token" -> stamp.put("token", "not-a-token");
            case "extra" -> stamp.put("extra", 1);
            default -> raw.put("deleted", "false");
        }
        store.getDatabase().getCollection("items").replaceOne(new Document("_id", id), raw);
        assertThrows(RuntimeException.class, this::read);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnknownMetadataBarrierIsResolvedBeforeAnyNewBusinessWrite(boolean after)
            throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, false);
        item.setCount(20);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        fault.failBarrierAfter = after;
        item.setCount(30);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(10, read().getCount());
        DatabaseHelper.saveGameSync(item);
        assertEquals(30, read().getCount());
        assertFalse(fault.release());
    }

    @Test
    void revisionOverflowCannotPartiallyWriteAnItem() {
        seed();
        var raw = row();
        raw.get("saveStamp", Document.class).put("revision", Long.MAX_VALUE);
        store.getDatabase().getCollection("items").replaceOne(new Document("_id", id), raw);
        var item = read();
        item.setCount(20);
        assertThrows(ArithmeticException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(raw, row());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void metadataConfirmationKeepsBusinessValuesEvenWhenItsAcknowledgementIsLost(boolean after)
            throws Exception {
        var item = seed();
        var fault = new DelayedItemSave(item, store, false, false);
        fault.failBarrierAfter = after;
        fault.triggered = true; // This case injects only the metadata confirmation failure.
        assertThrows(RuntimeException.class, () -> item.getSaveGuard().confirmStoredVersion(store));
        assertEquals(10, read().getCount());
        item.getSaveGuard().resolvePending(store);
        assertEquals(10, read().getCount());
        item.setCount(12);
        DatabaseHelper.saveGameSync(item);
        assertEquals(12, read().getCount());
    }

    private GameItem learningIntent() {
        definition =
                new Gson()
                        .fromJson(
                                "{id:104003,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CONSUME',stackLimit:99999,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'ITEM_USE_UNLOCK_COMBINE',useParam:['200']}]}",
                                ItemData.class);
        definition.onLoad();
        GameData.getItemDataMap().put(ITEM, definition);
        var item = seed();
        player.setPendingItemUse(
                ItemUseReceipt.recipe(
                        item,
                        emu.grasscutter.game.props.ItemUseOp.ITEM_USE_UNLOCK_COMBINE.getValue(),
                        200,
                        null));
        DatabaseHelper.saveGameSync(player);
        return item;
    }

    @Test
    void aRecipeDebitProofSurvivesRefillAndFinalConsumption() {
        var item = learningIntent();
        var receipt = player.getPendingItemUse();
        item.getSaveGuard().authorizeRecipeDebit(player, receipt);
        item.setCount(9);
        DatabaseHelper.saveGameSync(item);
        assertEquals(receipt.getOperationId(), row().getString("recipeUseOperationId"));
        item.setCount(15);
        DatabaseHelper.saveGameSync(item);
        item.setCount(0);
        DatabaseHelper.saveGameSync(item);
        assertTrue(row().getBoolean("deleted"));
        assertEquals(receipt.getOperationId(), read().getRecipeUseOperationId());
        assertTrue(DatabaseHelper.getInventoryItems(player).isEmpty());
    }

    @Test
    void anUnacknowledgedRecipeIntentCannotWriteADebitProof() {
        var item = learningIntent();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", UID),
                        new Document("$unset", new Document("pendingItemUse", "")));
        var before = row();
        item.getSaveGuard().authorizeRecipeDebit(player, player.getPendingItemUse());
        item.setCount(9);
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveGameSync(item));
        assertEquals(before, row());
    }

    @Test
    void cancellingAnUnpaidRecipeRetiresItsHeldDebitAndProofTogether() throws Exception {
        var item = learningIntent();
        var delayed = new DelayedItemSave(item, store, false, false);
        item.getSaveGuard().authorizeRecipeDebit(player, player.getPendingItemUse());
        item.setCount(9);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        var restored = read();
        assertEquals(10, restored.getCount());
        restored.getSaveGuard().confirmStoredVersion(store);
        player.setPendingItemUse(null);
        DatabaseHelper.saveGameSync(player);
        assertFalse(delayed.release());
        assertEquals(10, read().getCount());
        assertNull(read().getRecipeUseOperationId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnknownRecipeDebitKeepsItsProofThroughRetry(boolean after) throws Exception {
        var item = learningIntent();
        String operation = player.getPendingItemUse().getOperationId();
        var delayed = new DelayedItemSave(item, store, false, after);
        item.getSaveGuard().authorizeRecipeDebit(player, player.getPendingItemUse());
        item.setCount(9);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(item));
        DatabaseHelper.saveGameSync(item);
        assertEquals(9, read().getCount());
        assertEquals(operation, read().getRecipeUseOperationId());
        assertFalse(delayed.release());
    }

    @Test
    void anOldCopyCannotEraseTheRecipeDebitProof() {
        var item = learningIntent();
        var old = read();
        item.getSaveGuard().authorizeRecipeDebit(player, player.getPendingItemUse());
        item.setCount(9);
        DatabaseHelper.saveGameSync(item);
        var saved = row();
        old.setCount(10);
        assertThrows(RuntimeException.class, () -> DatabaseHelper.saveGameSync(old));
        assertEquals(saved, row());
    }

    static final class Held implements AutoCloseable {
        final CountDownLatch release = new CountDownLatch(1);

        Held() throws Exception {
            var queue = DatabaseHelper.getEventExecutorItem();
            var entered = new CountDownLatch(queue.getCorePoolSize());
            for (int i = 0; i < queue.getCorePoolSize(); i++)
                queue.execute(
                        () -> {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        public void close() throws Exception {
            release.countDown();
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        }
    }
}
