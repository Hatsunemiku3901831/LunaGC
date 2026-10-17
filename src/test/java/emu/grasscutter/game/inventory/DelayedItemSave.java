package emu.grasscutter.game.inventory;

import com.mongodb.MongoWriteException;

import dev.morphia.Datastore;

import org.bson.Document;
import org.bson.json.*;
import org.bson.types.ObjectId;

/** Retain the exact conditional item write and optionally deliver it after a recovery decision. */
public final class DelayedItemSave implements ItemSaveGuard.Storage {
    final ItemSaveGuard.MongoStorage delegate;
    final boolean insertion, after;
    boolean triggered, commitOnRead;
    Boolean failBarrierAfter;
    Document heldFilter, heldRow;

    public DelayedItemSave(GameItem item, Datastore store, boolean insertion, boolean after)
            throws Exception {
        this.insertion = insertion;
        this.after = after;
        delegate = new ItemSaveGuard.MongoStorage(store);
        var row = delegate.collection.find(new Document("_id", item.ensurePersistenceId())).first();
        var f = GameItem.class.getDeclaredField("saveGuard");
        f.setAccessible(true);
        f.set(item, new ItemSaveGuard(item, row, row == null ? null : store, ignored -> this));
    }

    static Document frozen(Document d) {
        return Document.parse(
                d.toJson(JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()));
    }

    public Document read(ObjectId id) {
        if (commitOnRead) {
            commitOnRead = false;
            if (!release())
                throw new IllegalStateException("Delayed item write lost its condition");
        }
        return delegate.read(id);
    }

    public void insert(Document row) {
        if (insertion && !triggered) {
            triggered = true;
            heldRow = frozen(row);
            if (after) delegate.insert(row);
            throw new IllegalStateException("Uncertain item insert");
        }
        delegate.insert(row);
    }

    public boolean replace(Document filter, Document row) {
        if (!insertion && !triggered) {
            triggered = true;
            heldFilter = frozen(filter);
            heldRow = frozen(row);
            if (after) delegate.replace(filter, row);
            throw new IllegalStateException("Uncertain item replacement");
        }
        return delegate.replace(filter, row);
    }

    public boolean advance(Document filter, ItemSaveStamp stamp) {
        if (failBarrierAfter != null) {
            boolean after = failBarrierAfter;
            failBarrierAfter = null;
            if (after) delegate.advance(filter, stamp);
            throw new IllegalStateException("Uncertain metadata barrier");
        }
        return delegate.advance(filter, stamp);
    }

    public boolean release() {
        if (!insertion) return delegate.replace(heldFilter, heldRow);
        try {
            delegate.insert(heldRow);
            return true;
        } catch (MongoWriteException duplicate) {
            if (duplicate.getError().getCode() != 11000) throw duplicate;
            return false;
        }
    }

    void commitBeforeNextCheck() {
        commitOnRead = true;
    }
}
