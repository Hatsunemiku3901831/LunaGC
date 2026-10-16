package emu.grasscutter.game.activity;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.function.Function;

/** Conditional whole-document saves; an old activity copy must never adopt a foreign revision. */
public final class ActivitySaveGuard {
    interface Storage {
        Document read(Object id);

        void insert(Document document);

        boolean replace(Document filter, Document document);

        boolean advance(Document filter, ActivitySaveStamp stamp);
    }

    static final class MongoStorage implements Storage {
        final MongoCollection<Document> collection;

        MongoStorage(Datastore datastore) {
            collection =
                    datastore
                            .getMapper()
                            .getCollection(PlayerActivityData.class)
                            .withDocumentClass(Document.class)
                            .withReadPreference(ReadPreference.primary())
                            .withReadConcern(ReadConcern.MAJORITY)
                            .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        }

        public Document read(Object id) {
            return collection
                    .find(new Document("_id", id))
                    .projection(
                            new Document("uid", 1)
                                    .append("activityId", 1)
                                    .append("scheduleId", 1)
                                    .append("saveStamp", 1))
                    .first();
        }

        public void insert(Document document) {
            collection.insertOne(document);
        }

        public boolean replace(Document filter, Document document) {
            return collection.replaceOne(filter, document).getModifiedCount() == 1;
        }

        public boolean advance(Document filter, ActivitySaveStamp stamp) {
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
        final ActivitySaveStamp before, after;
        final int beforeSchedule, afterSchedule;
        final Document document;
        final boolean insert;
        ActivitySaveStamp barrier;

        Attempt(ActivitySaveStamp before, int beforeSchedule, Document document, boolean insert) {
            this.before = before;
            this.after = ActivitySaveStamp.next(before);
            this.beforeSchedule = beforeSchedule;
            this.afterSchedule = document.getInteger("scheduleId", 0);
            this.document = document;
            this.insert = insert;
            document.put("saveStamp", after.document());
        }
    }

    private final PlayerActivityData data;
    private final String id;
    private final Object bsonId;
    private final int uid, activityId;
    private final Function<Datastore, Storage> factory;
    private ActivitySaveStamp confirmed;
    private int storedSchedule;
    private boolean exists, conflicted;
    private Datastore destination;
    private Storage storage;
    private Attempt pending;

    ActivitySaveGuard(PlayerActivityData data, Document loaded, Datastore source) {
        this(data, loaded, source, MongoStorage::new);
    }

    ActivitySaveGuard(
            PlayerActivityData data,
            Document loaded,
            Datastore source,
            Function<Datastore, Storage> factory) {
        this.data = data;
        if (data.getId() == null && loaded == null) data.setId(new ObjectId().toHexString());
        id = Objects.requireNonNull(data.getId(), "Activity document has no identity");
        uid = data.getUid();
        activityId = data.getActivityId();
        this.factory = factory;
        bsonId = loaded == null ? id : loaded.get("_id");
        if (!id.equals(javaId(bsonId)))
            throw new IllegalStateException("Activity BSON identity differs");
        destination = source;
        exists = loaded != null;
        confirmed = loaded == null ? null : ActivitySaveStamp.from(loaded);
        storedSchedule = loaded == null ? data.getScheduleId() : loaded.getInteger("scheduleId", 0);
        if (loaded != null && !owner(loaded))
            throw new IllegalStateException("Activity owner differs");
        data.setSaveStamp(confirmed);
    }

    private static String javaId(Object value) {
        return value instanceof String s
                ? s
                : value instanceof ObjectId oid ? oid.toHexString() : null;
    }

    public void save(Datastore datastore) {
        synchronized (data) {
            if (data.hasUnconfirmedActivityCoins())
                throw new IllegalStateException("Activity coin write requires reconciliation");
            resolvePending(datastore);
            if (storage == null) {
                destination = datastore;
                storage = factory.apply(datastore);
            }
            // Encoding errors have sent no write and must not consume a revision.
            var document = datastore.getMapper().toDocument(data);
            if (!id.equals(javaId(document.get("_id"))) || !owner(document))
                throw new IllegalStateException("Encoded activity identity changed");
            // Retain the exact stored BSON identity rather than allowing an encoder to change it.
            document.put("_id", bsonId);
            pending = new Attempt(confirmed, storedSchedule, document, !exists);
            if (pending.insert) storage.insert(pending.document);
            else if (!storage.replace(
                    filter(pending.before, pending.beforeSchedule), pending.document))
                throw conflict();
            accept(pending.after, pending.afterSchedule);
        }
    }

    public void resolvePending(Datastore datastore) {
        synchronized (data) {
            if (!id.equals(data.getId())
                    || uid != data.getUid()
                    || activityId != data.getActivityId())
                throw new IllegalStateException("Activity save identity changed");
            if (destination != null && destination != datastore)
                throw new IllegalStateException("Activity save destination changed");
            if (conflicted) throw conflict();
            if (confirmed != null) confirmed.validate();
            if (pending != null) resolve();
        }
    }

    private Document filter(ActivitySaveStamp stamp, int schedule) {
        var result =
                new Document("_id", bsonId).append("uid", uid).append("activityId", activityId);
        if (schedule == 0)
            result.append(
                    "$or",
                    List.of(new Document("scheduleId", 0), new Document("scheduleId", null)));
        else result.append("scheduleId", schedule);
        if (stamp == null) result.append("saveStamp", null);
        else
            result.append("saveStamp.revision", stamp.getRevision())
                    .append("saveStamp.token", stamp.getToken());
        return result;
    }

    private boolean owner(Document row) {
        return Objects.equals(row.get("uid"), uid)
                && Objects.equals(row.get("activityId"), activityId);
    }

    private void resolve() {
        var row = storage.read(bsonId);
        if (recognize(row)) return;
        if (pending.insert && row == null) {
            try {
                storage.insert(pending.document);
                accept(pending.after, pending.afterSchedule);
            } catch (MongoWriteException duplicate) {
                if (duplicate.getError() == null
                        || duplicate.getError().getCode() != 11000
                        || !recognize(storage.read(bsonId))) throw duplicate;
            }
            return;
        }
        if (row == null
                || !owner(row)
                || pending.insert
                || row.getInteger("scheduleId", 0) != pending.beforeSchedule
                || !ActivitySaveStamp.same(ActivitySaveStamp.from(row), pending.before))
            throw conflict();
        if (pending.barrier == null) pending.barrier = ActivitySaveStamp.next(pending.before);
        // This CAS retires the original delayed replacement and changes only metadata.
        if (storage.advance(filter(pending.before, pending.beforeSchedule), pending.barrier))
            accept(pending.barrier, pending.beforeSchedule);
        else if (!recognize(storage.read(bsonId))) throw conflict();
    }

    private boolean recognize(Document row) {
        if (row == null || !owner(row)) return false;
        var stamp = ActivitySaveStamp.from(row);
        int schedule = row.getInteger("scheduleId", 0);
        if (schedule == pending.afterSchedule && ActivitySaveStamp.same(stamp, pending.after)) {
            accept(pending.after, pending.afterSchedule);
            return true;
        }
        if (pending.barrier != null
                && schedule == pending.beforeSchedule
                && ActivitySaveStamp.same(stamp, pending.barrier)) {
            accept(pending.barrier, pending.beforeSchedule);
            return true;
        }
        return false;
    }

    private void accept(ActivitySaveStamp stamp, int schedule) {
        confirmed = stamp;
        data.setSaveStamp(stamp);
        storedSchedule = schedule;
        exists = true;
        pending = null;
    }

    /** A new replay may reuse only this confirmed document identity, never an uncertain write. */
    ActivitySaveGuard forReplay(PlayerActivityData replacement) {
        synchronized (data) {
            if (pending != null || conflicted || !exists || data.hasUnconfirmedActivityCoins())
                throw new IllegalStateException("Activity replay requires confirmed saved state");
            resolvePending(destination);
            if (replacement.getUid() != uid || replacement.getActivityId() != activityId)
                throw new IllegalStateException("Activity replay owner changed");
            replacement.setId(id);
            var loaded =
                    new Document("_id", bsonId)
                            .append("uid", uid)
                            .append("activityId", activityId)
                            .append("scheduleId", storedSchedule)
                            .append("saveStamp", confirmed == null ? null : confirmed.document());
            return new ActivitySaveGuard(replacement, loaded, destination, factory);
        }
    }

    private IllegalStateException conflict() {
        conflicted = true;
        pending = null;
        return new IllegalStateException(
                "Stale or missing activity save for UID "
                        + uid
                        + ", activity "
                        + activityId
                        + "; reload before saving");
    }
}
