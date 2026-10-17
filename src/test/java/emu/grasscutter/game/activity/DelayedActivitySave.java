package emu.grasscutter.game.activity;

import dev.morphia.Datastore;

import org.bson.Document;

/** Retain the exact activity CAS and BSON candidate, then release it after a recovery decision. */
public final class DelayedActivitySave implements ActivitySaveGuard.Storage {
    private final ActivitySaveGuard.Storage delegate;
    private final int nth;
    private int replacements;
    private Document delayedFilter, delayedDocument;
    private boolean commitOnRead;

    public DelayedActivitySave(PlayerActivityData data, Datastore store, int nth) throws Exception {
        delegate = new ActivitySaveGuard.MongoStorage(store);
        this.nth = nth;
        var row =
                store.getDatabase()
                        .getCollection("activities")
                        .find(new Document("_id", data.getId()))
                        .first();
        var field = PlayerActivityData.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        field.set(data, new ActivitySaveGuard(data, row, store, ignored -> this));
    }

    public void commitBeforeNextCheck() {
        commitOnRead = true;
    }

    public boolean release() {
        return delegate.replace(delayedFilter, delayedDocument);
    }

    public Document read(Object id) {
        if (commitOnRead) {
            commitOnRead = false;
            if (!release())
                throw new IllegalStateException("Delayed activity write did not commit");
        }
        return delegate.read(id);
    }

    public void insert(Document document) {
        delegate.insert(document);
    }

    public boolean replace(Document filter, Document document) {
        if (++replacements == nth) {
            delayedFilter = filter;
            delayedDocument = document;
            throw new IllegalStateException("Uncertain delayed activity write");
        }
        return delegate.replace(filter, document);
    }

    public boolean advance(Document filter, ActivitySaveStamp stamp) {
        return delegate.advance(filter, stamp);
    }
}
