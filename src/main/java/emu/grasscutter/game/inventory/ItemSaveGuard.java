package emu.grasscutter.game.inventory;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;

import emu.grasscutter.game.player.Player;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.function.Function;

/**
 * Conditional item writes and permanent deletion evidence; old copies never adopt foreign writes.
 */
public final class ItemSaveGuard {
    interface Storage {
        Document read(ObjectId id);

        void insert(Document row);

        boolean replace(Document filter, Document row);

        boolean advance(Document filter, ItemSaveStamp stamp);
    }

    static final class MongoStorage implements Storage {
        final MongoCollection<Document> collection;

        MongoStorage(Datastore ds) {
            collection =
                    ds.getMapper()
                            .getCollection(GameItem.class)
                            .withDocumentClass(Document.class)
                            .withReadPreference(ReadPreference.primary())
                            .withReadConcern(ReadConcern.MAJORITY)
                            .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        }

        public Document read(ObjectId id) {
            return collection
                    .find(new Document("_id", id))
                    .projection(
                            new Document("ownerId", 1)
                                    .append("itemId", 1)
                                    .append("saveStamp", 1)
                                    .append("count", 1)
                                    .append("foodRefundOperationId", 1)
                                    .append("deleted", 1))
                    .first();
        }

        public void insert(Document row) {
            collection.insertOne(row);
        }

        public boolean replace(Document filter, Document row) {
            return collection.replaceOne(filter, row).getModifiedCount() == 1;
        }

        public boolean advance(Document filter, ItemSaveStamp stamp) {
            return collection
                            .updateOne(
                                    filter,
                                    new Document(
                                            "$set", new Document("saveStamp", stamp.document())))
                            .getModifiedCount()
                    == 1;
        }
    }

    private static final class Attempt {
        final ItemSaveStamp before, after;
        final Document row;
        final boolean insert, terminal, beforeDeleted;
        boolean metadataOnly;
        ItemSaveStamp barrier;

        Attempt(
                ItemSaveStamp before,
                Document row,
                boolean insert,
                boolean terminal,
                boolean beforeDeleted) {
            this.before = before;
            after = ItemSaveStamp.next(before);
            this.row = row;
            this.insert = insert;
            this.terminal = terminal;
            this.beforeDeleted = beforeDeleted;
            row.put("saveStamp", after.document());
        }
    }

    private final GameItem item;
    private final ObjectId id;
    private final int ownerId, itemId;
    private final Datastore loadedFrom;
    private final Function<Datastore, Storage> factory;
    // Fresh copies may be explicitly submitted to two stores; their revisions remain independent.
    private final Map<Datastore, Copy> copies = new IdentityHashMap<>();

    private record FoodRefund(Player player, ItemUseReceipt receipt) {}

    private FoodRefund foodRefund;

    /**
     * Only the persisted food refund phase can authorize restoring an original consumed identity.
     */
    void authorizeFoodRefund(Player player, int before, int used) {
        synchronized (item) {
            identity();
            var receipt = player.getPendingItemUse();
            if (receipt == null
                    || !"food".equals(receipt.getKind())
                    || receipt.getState() != 5
                    || player.getUid() != ownerId
                    || receipt.getSourceId() != itemId
                    || !id.toHexString().equals(receipt.getSourceObjectId())
                    || receipt.getSourceBefore() != before
                    || receipt.getUsedCount() != used
                    || before <= 0
                    || used <= 0
                    || used > before
                    || item.getCount() != before - used
                    || !FoodItemUse.isFood(item.getItemData()))
                throw new IllegalStateException("Food refund does not match its reservation");
            foodRefund = new FoodRefund(player, receipt);
        }
    }

    private void validateFoodRefund(Datastore ds, Copy state) {
        var receipt = foodRefund.receipt();
        var actor = foodRefund.player();
        String op = receipt.getOperationId();
        if (actor.getPendingItemUse() != receipt
                || receipt.getState() != 5
                || !UUID.fromString(op).toString().equals(op)
                || item.getCount() != receipt.getSourceBefore()
                || item.getItemData() == null
                || item.getItemData().getStackLimit() < item.getCount())
            throw new IllegalStateException("Food refund intent changed");
        var raw =
                ds.getMapper()
                        .getCollection(Player.class)
                        .withDocumentClass(Document.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(new Document("_id", ownerId))
                        .projection(new Document("pendingItemUse", 1))
                        .first();
        var saved = raw == null ? null : raw.get("pendingItemUse", Document.class);
        if (saved == null
                || !op.equals(saved.getString("operationId"))
                || !"food".equals(saved.getString("kind"))
                || saved.getInteger("state", 0) != 5
                || saved.getInteger("sourceId", 0) != itemId
                || !id.toHexString().equals(saved.getString("sourceObjectId"))
                || saved.getInteger("sourceBefore", 0) != receipt.getSourceBefore()
                || saved.getInteger("usedCount", 0) != receipt.getUsedCount())
            throw new IllegalStateException("Food refund intent has not been acknowledged");
        var stored = state.storage.read(id);
        if (stored == null) {
            if (state.exists || receipt.getSourceBefore() != receipt.getUsedCount())
                throw state.conflict();
            return;
        }
        if (!owner(stored)
                || stored.getInteger("count", 0)
                        != receipt.getSourceBefore() - receipt.getUsedCount()
                || op.equals(stored.getString("foodRefundOperationId")))
            throw new IllegalStateException("Food refund cost or result changed");
        if (!state.exists
                || state.deleted != terminal(stored)
                || !ItemSaveStamp.same(state.confirmed, ItemSaveStamp.from(stored)))
            throw state.conflict();
    }

    ItemSaveGuard(GameItem item, Document loaded, Datastore source) {
        this(item, loaded, source, MongoStorage::new);
    }

    ItemSaveGuard(
            GameItem item,
            Document loaded,
            Datastore source,
            Function<Datastore, Storage> factory) {
        this.item = item;
        id = item.ensurePersistenceId();
        ownerId = item.getOwnerId();
        itemId = item.getItemId();
        this.factory = factory;
        if (ownerId < 0 || itemId <= 0)
            throw new IllegalStateException("Invalid item save identity");
        loadedFrom =
                loaded == null ? null : Objects.requireNonNull(source, "Loaded item datastore");
        if (loaded != null) {
            if (!owner(loaded)) throw new IllegalStateException("Stored item identity differs");
            copies.put(source, new Copy(loaded, source));
        }
    }

    private boolean owner(Document row) {
        return id.equals(row.get("_id"))
                && ownerId == row.getInteger("ownerId", 0)
                && itemId == row.getInteger("itemId", 0);
    }

    private static boolean terminal(Document row) {
        Object value = row.get("deleted");
        if (value != null && !(value instanceof Boolean))
            throw new IllegalStateException("Invalid item deletion marker");
        return Boolean.TRUE.equals(value);
    }

    private Copy copy(Datastore ds) {
        if (loadedFrom != null && loadedFrom != ds)
            throw new IllegalStateException("Loaded item save destination changed");
        return copies.computeIfAbsent(Objects.requireNonNull(ds), key -> new Copy(null, key));
    }

    private void identity() {
        if (!id.equals(item.getObjectId())
                || ownerId != item.getOwnerId()
                || itemId != item.getItemId())
            throw new IllegalStateException("Item save identity changed");
    }

    public void save(Datastore ds) {
        write(ds, false);
    }

    public void delete(Datastore ds) {
        write(ds, true);
    }

    public void resolvePending(Datastore ds) {
        synchronized (item) {
            identity();
            copy(ds).resolvePending();
        }
    }
    /**
     * Retire any older conditional write before a recovery decision that leaves stock unchanged.
     */
    public void confirmStoredVersion(Datastore ds) {
        synchronized (item) {
            identity();
            var state = copy(ds);
            state.resolvePending();
            var row = state.storage.read(id);
            if (!state.exists
                    || row == null
                    || !owner(row)
                    || terminal(row) != state.deleted
                    || row.getInteger("count", 0) != item.getCount()
                    || !ItemSaveStamp.same(ItemSaveStamp.from(row), state.confirmed))
                throw state.conflict();
            state.pending = new Attempt(state.confirmed, row, false, state.deleted, state.deleted);
            state.pending.metadataOnly = true;
            if (!state.storage.advance(
                    state.filter(state.pending.before, state.deleted), state.pending.after))
                throw state.conflict();
            state.accept(
                    state.pending.after,
                    state.deleted,
                    row.getString("foodRefundOperationId"),
                    false);
        }
    }

    private void write(Datastore ds, boolean forceDelete) {
        synchronized (item) {
            identity();
            boolean deleting = forceDelete || item.getCount() <= 0;
            var state = copy(ds);
            state.resolvePending();
            boolean refund = !deleting && foodRefund != null;
            if (refund) validateFoodRefund(ds, state);
            if (!deleting && (state.deleted || item.isDeleted()) && !refund)
                throw new IllegalStateException("Deleted item cannot be recreated");
            if (state.deleted && !refund) {
                var stored = state.storage.read(id);
                if (stored == null
                        || !owner(stored)
                        || !terminal(stored)
                        || !ItemSaveStamp.same(ItemSaveStamp.from(stored), state.confirmed))
                    throw state.conflict();
                item.markDeleted();
                return;
            }
            var row = ds.getMapper().toDocument(item);
            if (!owner(row)) throw new IllegalStateException("Encoded item identity changed");
            row.put("deleted", deleting);
            if (refund) row.put("foodRefundOperationId", foodRefund.receipt().getOperationId());
            if (deleting) row.put("count", 0);
            state.pending =
                    new Attempt(state.confirmed, row, !state.exists, deleting, state.deleted);
            if (state.pending.insert) state.storage.insert(state.pending.row);
            else if (!state.storage.replace(
                    state.filter(state.pending.before, state.pending.beforeDeleted),
                    state.pending.row)) throw state.conflict();
            state.accept(
                    state.pending.after,
                    deleting,
                    state.pending.row.getString("foodRefundOperationId"),
                    true);
        }
    }

    private final class Copy {
        final Storage storage;
        ItemSaveStamp confirmed;
        boolean exists, deleted, conflicted;
        Attempt pending;

        Copy(Document row, Datastore ds) {
            storage = factory.apply(ds);
            exists = row != null;
            if (row != null) {
                confirmed = ItemSaveStamp.from(row);
                deleted = terminal(row);
                if (deleted && item.getCount() > 0)
                    throw new IllegalStateException("Deleted item has positive count");
            }
        }

        Document filter(ItemSaveStamp stamp, boolean wasDeleted) {
            var filter =
                    new Document("_id", id)
                            .append("ownerId", ownerId)
                            .append("itemId", itemId)
                            .append("deleted", wasDeleted ? true : new Document("$ne", true));
            if (stamp == null) filter.append("saveStamp", null);
            else {
                stamp.validate();
                filter.append("saveStamp.revision", stamp.getRevision())
                        .append("saveStamp.token", stamp.getToken());
            }
            return filter;
        }

        void resolvePending() {
            if (conflicted) throw conflict();
            if (confirmed != null) confirmed.validate();
            if (pending == null) return;
            var row = storage.read(id);
            if (recognize(row)) return;
            if (pending.insert && row == null) {
                try {
                    storage.insert(pending.row);
                    accept(
                            pending.after,
                            pending.terminal,
                            pending.row.getString("foodRefundOperationId"),
                            true);
                } catch (MongoWriteException duplicate) {
                    if (duplicate.getError() == null
                            || duplicate.getError().getCode() != 11000
                            || !recognize(storage.read(id))) throw duplicate;
                }
                return;
            }
            if (row == null
                    || !owner(row)
                    || terminal(row) != pending.beforeDeleted
                    || pending.insert
                    || !ItemSaveStamp.same(ItemSaveStamp.from(row), pending.before))
                throw conflict();
            if (pending.barrier == null) pending.barrier = ItemSaveStamp.next(pending.before);
            if (storage.advance(filter(pending.before, pending.beforeDeleted), pending.barrier))
                accept(
                        pending.barrier,
                        pending.beforeDeleted,
                        row.getString("foodRefundOperationId"),
                        false);
            else if (!recognize(storage.read(id))) throw conflict();
        }

        boolean recognize(Document row) {
            if (row == null || !owner(row)) return false;
            var stamp = ItemSaveStamp.from(row);
            if (ItemSaveStamp.same(stamp, pending.after)) {
                if (terminal(row) != pending.terminal) throw conflict();
                accept(
                        pending.after,
                        pending.terminal,
                        row.getString("foodRefundOperationId"),
                        !pending.metadataOnly);
                return true;
            }
            if (pending.barrier != null && ItemSaveStamp.same(stamp, pending.barrier)) {
                if (terminal(row) != pending.beforeDeleted) throw conflict();
                accept(
                        pending.barrier,
                        pending.beforeDeleted,
                        row.getString("foodRefundOperationId"),
                        false);
                return true;
            }
            return false;
        }

        void accept(ItemSaveStamp stamp, boolean terminal, String refund, boolean businessWrite) {
            confirmed = stamp;
            exists = true;
            deleted = terminal;
            pending = null;
            item.acceptSaveState(stamp, terminal, refund, businessWrite);
            if (businessWrite
                    && !terminal
                    && foodRefund != null
                    && Objects.equals(refund, foodRefund.receipt().getOperationId()))
                foodRefund = null;
        }

        IllegalStateException conflict() {
            conflicted = true;
            pending = null;
            return new IllegalStateException(
                    "Stale or missing item save "
                            + id
                            + " for UID "
                            + ownerId
                            + "; reload before saving");
        }
    }
}
