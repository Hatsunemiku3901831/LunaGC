package emu.grasscutter.game.activity;

import emu.grasscutter.database.DatabaseManager;

import org.bson.Document;

import java.util.concurrent.atomic.AtomicInteger;

/** Test-only fault at the real guarded Mongo write, preserving the live guard and destination. */
public final class ActivitySaveFaults {
    private ActivitySaveFaults() {}

    public static AtomicInteger failNextWrite(PlayerActivityData data, boolean committed)
            throws Exception {
        var field = ActivitySaveGuard.class.getDeclaredField("storage");
        field.setAccessible(true);
        var guard = data.getSaveGuard();
        var current = (ActivitySaveGuard.Storage) field.get(guard);
        // A cold loaded guard creates storage only when its first write is attempted.
        ActivitySaveGuard.Storage delegate =
                current == null
                        ? new ActivitySaveGuard.MongoStorage(DatabaseManager.getGameDatastore())
                        : current;
        var writes = new AtomicInteger();
        field.set(
                guard,
                new ActivitySaveGuard.Storage() {
                    public Document read(Object id) {
                        return delegate.read(id);
                    }

                    public void insert(Document value) {
                        throw new AssertionError("Existing activity must use CAS");
                    }

                    public boolean replace(Document filter, Document value) {
                        if (writes.incrementAndGet() == 1) {
                            if (committed) delegate.replace(filter, value);
                            throw new IllegalStateException("activity coin acknowledgement lost");
                        }
                        return delegate.replace(filter, value);
                    }

                    public boolean advance(Document filter, ActivitySaveStamp stamp) {
                        return delegate.advance(filter, stamp);
                    }
                });
        return writes;
    }
}
