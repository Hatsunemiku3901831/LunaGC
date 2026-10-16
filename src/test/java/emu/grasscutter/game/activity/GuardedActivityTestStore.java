package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.*;

import dev.morphia.Datastore;

import org.bson.Document;

import java.util.function.Consumer;

/** Preserve business fault callbacks while exercising real acknowledged activity CAS writes. */
public final class GuardedActivityTestStore {
    private GuardedActivityTestStore() {}

    public static void bind(
            PlayerActivityData data,
            Datastore actual,
            Datastore destination,
            boolean loaded,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        if (!actual.getDatabase().getName().startsWith("lunagc_"))
            throw new IllegalArgumentException("Expected an isolated fixture database");
        actual.getMapper().map(PlayerActivityData.class);
        if (data.getUid() == 0) data.setUid(12345);
        var storage = new ActivitySaveGuard.MongoStorage(actual);
        assertEquals(WriteConcern.MAJORITY.withJournal(true), storage.collection.getWriteConcern());
        assertEquals(ReadConcern.MAJORITY, storage.collection.getReadConcern());
        assertEquals(ReadPreference.primary(), storage.collection.getReadPreference());
        var row =
                loaded ? storage.collection.find(new Document("_id", data.getId())).first() : null;
        if (loaded && row == null)
            throw new IllegalStateException("Activity fixture was not seeded");
        var wrapper =
                new ActivitySaveGuard.Storage() {
                    public Document read(Object id) {
                        return storage.read(id);
                    }

                    public void insert(Document document) {
                        before.accept(document);
                        storage.insert(document);
                        after.accept(document);
                    }

                    public boolean replace(Document filter, Document document) {
                        before.accept(document);
                        boolean result = storage.replace(filter, document);
                        if (result) after.accept(document);
                        return result;
                    }

                    public boolean advance(Document filter, ActivitySaveStamp stamp) {
                        return storage.advance(filter, stamp);
                    }
                };
        var field = PlayerActivityData.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        field.set(data, new ActivitySaveGuard(data, row, destination, ignored -> wrapper));
    }

    public static void bind(
            PlayerActivityData data,
            Datastore actual,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        bind(data, actual, null, false, before, after);
    }
}
