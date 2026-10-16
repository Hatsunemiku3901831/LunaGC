package emu.grasscutter.game.player;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;

import org.bson.Document;

import java.util.function.Function;

/** Whole Player document writes must retain the revision read by this specific in-memory copy. */
public final class PlayerSaveGuard {
    interface Storage {
        Document read(int uid);

        void insert(Document document);

        boolean replace(Document filter, Document document);

        boolean advance(Document filter, PlayerSaveStamp stamp);
    }

    static final class MongoStorage implements Storage {
        private final MongoCollection<Document> collection;

        MongoStorage(Datastore datastore) {
            collection =
                    datastore
                            .getMapper()
                            .getCollection(Player.class)
                            .withDocumentClass(Document.class)
                            .withReadPreference(ReadPreference.primary())
                            .withReadConcern(ReadConcern.MAJORITY)
                            .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        }

        public Document read(int uid) {
            return collection
                    .find(new Document("_id", uid))
                    .projection(new Document("saveStamp", 1))
                    .first();
        }

        public void insert(Document document) {
            collection.insertOne(document);
        }

        public boolean replace(Document filter, Document document) {
            return collection.replaceOne(filter, document).getModifiedCount() == 1;
        }

        public boolean advance(Document filter, PlayerSaveStamp stamp) {
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
        final PlayerSaveStamp before, after;
        final Document document;
        final boolean insert;
        PlayerSaveStamp barrier;

        Attempt(PlayerSaveStamp before, Document document, boolean insert) {
            this.before = before;
            this.after = PlayerSaveStamp.next(before);
            this.document = document;
            this.insert = insert;
            document.put("saveStamp", after.document());
        }
    }

    private final Player player;
    private final int uid;
    private final Function<Datastore, Storage> storageFactory;
    private PlayerSaveStamp confirmed;
    private boolean exists, conflicted;
    private Datastore destination;
    private Storage storage;
    private Attempt pending;

    PlayerSaveGuard(Player player, PlayerSaveStamp stamp, boolean loaded) {
        this(player, stamp, loaded, MongoStorage::new);
    }

    PlayerSaveGuard(
            Player player,
            PlayerSaveStamp stamp,
            boolean loaded,
            Function<Datastore, Storage> factory) {
        this.player = player;
        this.uid = player.getUid();
        this.confirmed = stamp;
        this.exists = loaded;
        this.storageFactory = factory;
    }

    public void save(Datastore datastore) {
        synchronized (player) {
            if (uid != player.getUid())
                throw new IllegalStateException("Player save identity changed");
            if (destination != null && destination != datastore)
                throw new IllegalStateException("Player save destination changed");
            if (conflicted) throw conflict();
            if (storage == null) {
                destination = datastore;
                storage = storageFactory.apply(datastore);
            }
            if (confirmed != null) confirmed.validate();
            if (pending != null) resolve();
            // Encode before publishing an attempt: an encoding failure has sent no write.
            var document = datastore.getMapper().toDocument(player);
            if (!(document.get("_id") instanceof Integer id) || id != uid)
                throw new IllegalStateException("Encoded player save identity changed");
            pending = new Attempt(confirmed, document, !exists);
            if (pending.insert) storage.insert(pending.document);
            else if (!storage.replace(filter(pending.before), pending.document)) throw conflict();
            accept(pending.after);
        }
    }

    private Document filter(PlayerSaveStamp stamp) {
        var result = new Document("_id", uid);
        if (stamp == null)
            return result.append("saveStamp", null); // Missing and explicit null legacy rows.
        return result.append("saveStamp.revision", stamp.getRevision())
                .append("saveStamp.token", stamp.getToken());
    }

    /** Resolve only our own write; never adopt the version of a foreign Player copy. */
    private void resolve() {
        var row = storage.read(uid);
        if (recognize(row)) return;
        if (pending.insert && row == null) {
            // Reissue the exact insert. A late copy can only insert the identical document once.
            try {
                storage.insert(pending.document);
                accept(pending.after);
            } catch (MongoWriteException duplicate) {
                if (duplicate.getError() == null
                        || duplicate.getError().getCode() != 11000
                        || !recognize(storage.read(uid))) throw duplicate;
            }
            return;
        }
        if (row == null
                || pending.insert
                || !PlayerSaveStamp.same(PlayerSaveStamp.from(row), pending.before))
            throw conflict();
        if (pending.barrier == null) pending.barrier = PlayerSaveStamp.next(pending.before);
        // Retire an uncertain old replacement before writing any changed in-memory result.
        // This touches metadata only. Either this CAS or the delayed original can win, never both.
        if (storage.advance(filter(pending.before), pending.barrier)) accept(pending.barrier);
        else if (!recognize(storage.read(uid))) throw conflict();
    }

    private boolean recognize(Document row) {
        if (row == null) return false;
        var stamp = PlayerSaveStamp.from(row);
        if (PlayerSaveStamp.same(stamp, pending.after)) {
            accept(pending.after);
            return true;
        }
        if (pending.barrier != null && PlayerSaveStamp.same(stamp, pending.barrier)) {
            accept(pending.barrier);
            return true;
        }
        return false;
    }

    private void accept(PlayerSaveStamp stamp) {
        confirmed = stamp;
        player.setSaveStamp(stamp);
        exists = true;
        pending = null;
    }

    private IllegalStateException conflict() {
        conflicted = true;
        pending = null;
        return new IllegalStateException(
                "Stale or missing player save for UID "
                        + uid
                        + "; reload the player before saving");
    }
}
