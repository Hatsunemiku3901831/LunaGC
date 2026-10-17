package emu.grasscutter.game;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;

import emu.grasscutter.database.DatabaseWriteBarrier;

import org.bson.Document;

import java.util.Objects;
import java.util.function.Function;

/** Ordinary account writes are revision-checked replacements; only creation may insert a row. */
public final class AccountSaveGuard {
    interface Storage {
        Document read(String id);

        void insert(Document document);

        boolean replace(Document filter, Document document);

        boolean advance(Document filter, AccountSaveStamp stamp);
    }

    static final class MongoStorage implements Storage {
        private final MongoCollection<Document> rows;

        MongoStorage(Datastore store) {
            rows =
                    store.getDatabase()
                            .getCollection("accounts")
                            .withReadPreference(ReadPreference.primary())
                            .withReadConcern(ReadConcern.MAJORITY)
                            .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        }

        public Document read(String id) {
            return rows.find(new Document("_id", id))
                    .projection(new Document("saveStamp", 1))
                    .first();
        }

        public void insert(Document document) {
            DatabaseWriteBarrier.run(() -> rows.insertOne(document));
        }

        public boolean replace(Document filter, Document document) {
            return DatabaseWriteBarrier.call(
                    () -> rows.replaceOne(filter, document).getModifiedCount() == 1);
        }

        public boolean advance(Document filter, AccountSaveStamp stamp) {
            return DatabaseWriteBarrier.call(
                    () ->
                            rows.updateOne(
                                                    filter,
                                                    new Document(
                                                            "$set",
                                                            new Document(
                                                                    "saveStamp", stamp.document())))
                                            .getModifiedCount()
                                    == 1);
        }
    }

    private static final class Attempt {
        final AccountSaveStamp before, after;
        final Document document;
        final String resetPassword;
        AccountSaveStamp barrier;

        Attempt(AccountSaveStamp before, Document document) {
            this(before, document, null);
        }

        Attempt(AccountSaveStamp before, Document document, String resetPassword) {
            this.before = before;
            this.resetPassword = resetPassword;
            after = AccountSaveStamp.next(before);
            this.document = document;
            document.put("saveStamp", after.document());
        }
    }

    private final Account account;
    private final String id;
    private final Datastore destination;
    private final Function<Datastore, Storage> factory;
    private AccountSaveStamp confirmed;
    private Storage storage;
    private Attempt pending;
    private boolean conflicted;

    AccountSaveGuard(Account account, Document loaded, Datastore source) {
        this(account, loaded, source, MongoStorage::new);
    }

    AccountSaveGuard(
            Account account,
            Document loaded,
            Datastore source,
            Function<Datastore, Storage> factory) {
        this.account = account;
        id = account.getId();
        destination = source;
        this.factory = factory;
        if (loaded != null) {
            if (!Objects.equals(id, loaded.get("_id")))
                throw new IllegalStateException("Account BSON identity differs");
            confirmed = AccountSaveStamp.from(loaded);
            account.setSaveStamp(confirmed);
        }
    }

    public boolean belongsTo(Datastore source) {
        return destination == source && id != null && id.equals(account.getId());
    }

    /** Called only by insert-only account creation, which has already reserved the ID. */
    public static void insertNew(Account account, Datastore store) {
        DatabaseWriteBarrier.run(
                () -> {
                    synchronized (account) {
                        if (account.getId() == null || account.getId().isBlank())
                            throw new IllegalStateException("Account ID required");
                        var document = store.getMapper().toDocument(account);
                        var stamp = AccountSaveStamp.next(null);
                        document.put("saveStamp", stamp.document());
                        new MongoStorage(store).insert(document);
                        account.installSaveGuard(new AccountSaveGuard(account, document, store));
                    }
                });
    }

    public void save(Datastore store) {
        DatabaseWriteBarrier.run(
                () -> {
                    synchronized (account) {
                        resolvePendingAdmitted(store);
                        if (storage == null) storage = factory.apply(store);
                        var document = store.getMapper().toDocument(account);
                        if (!id.equals(document.get("_id")))
                            throw new IllegalStateException("Encoded account identity changed");
                        pending = new Attempt(confirmed, document);
                        if (!storage.replace(
                                AccountSaveStamp.condition(id, pending.before), pending.document))
                            throw conflict();
                        accept(pending.after);
                    }
                });
    }

    public void resolvePending(Datastore store) {
        DatabaseWriteBarrier.run(
                () -> {
                    synchronized (account) {
                        resolvePendingAdmitted(store);
                    }
                });
    }

    /** Stage credentials in the exact CAS document; publish them in memory only on confirmation. */
    public void resetPassword(Datastore store, String hash) {
        if (hash == null || hash.isBlank())
            throw new IllegalArgumentException("Password hash required");
        DatabaseWriteBarrier.run(
                () -> {
                    synchronized (account) {
                        resolvePendingAdmitted(store);
                        if (storage == null) storage = factory.apply(store);
                        var document = store.getMapper().toDocument(account);
                        if (!id.equals(document.get("_id")))
                            throw new IllegalStateException("Encoded account identity changed");
                        document.put("password", hash);
                        document.remove("token");
                        document.remove("sessionKey");
                        document.put("passwordRequired", true);
                        pending = new Attempt(confirmed, document, hash);
                        if (!storage.replace(
                                AccountSaveStamp.condition(id, pending.before), pending.document))
                            throw conflict();
                        accept(pending.after);
                    }
                });
    }

    private void resolvePendingAdmitted(Datastore store) {
        if (id == null || id.isBlank() || destination == null)
            throw new IllegalStateException(
                    "Account has no confirmed creation or loaded save state");
        if (!id.equals(account.getId()))
            throw new IllegalStateException("Account save identity changed");
        if (destination != store)
            throw new IllegalStateException("Account save destination changed");
        if (conflicted) throw conflict();
        if (confirmed != null) confirmed.validate();
        if (pending != null) resolve();
    }

    private void resolve() {
        var row = storage.read(id);
        if (recognize(row)) return;
        if (row == null || !AccountSaveStamp.same(AccountSaveStamp.from(row), pending.before))
            throw conflict();
        if (pending.barrier == null) pending.barrier = AccountSaveStamp.next(pending.before);
        // Retire a delayed original by CAS before allowing a changed in-memory snapshot to save.
        if (storage.advance(AccountSaveStamp.condition(id, pending.before), pending.barrier))
            accept(pending.barrier);
        else if (!recognize(storage.read(id))) throw conflict();
    }

    private boolean recognize(Document row) {
        if (row == null) return false;
        var stamp = AccountSaveStamp.from(row);
        if (AccountSaveStamp.same(stamp, pending.after)) {
            accept(pending.after);
            return true;
        }
        if (pending.barrier != null && AccountSaveStamp.same(stamp, pending.barrier)) {
            accept(pending.barrier);
            return true;
        }
        return false;
    }

    private void accept(AccountSaveStamp stamp) {
        if (pending != null
                && pending.resetPassword != null
                && AccountSaveStamp.same(stamp, pending.after))
            account.acceptPasswordReset(pending.resetPassword);
        confirmed = stamp;
        account.setSaveStamp(stamp);
        pending = null;
    }

    private IllegalStateException conflict() {
        conflicted = true;
        pending = null;
        return new IllegalStateException(
                "Stale or missing account save for ID " + id + "; reload before saving");
    }
}
