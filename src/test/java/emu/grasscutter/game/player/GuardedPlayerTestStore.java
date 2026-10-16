package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;
import dev.morphia.Morphia;

import org.bson.Document;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Real isolated Player writes for business tests retaining their own item-write fault seam. */
public final class GuardedPlayerTestStore implements AutoCloseable, PlayerSaveGuard.Storage {
    private final MongoClient client;
    private final Datastore store;
    private final PlayerSaveGuard.Storage delegate;
    private final Runnable beforeWrite;
    private final Consumer<Document> afterWrite;
    private final String name =
            "lunagc_test_guarded_flow_" + UUID.randomUUID().toString().replace("-", "");

    public GuardedPlayerTestStore(Player player, Runnable beforeWrite) throws Exception {
        this(player, beforeWrite, ignored -> {});
    }

    public GuardedPlayerTestStore(
            Player player, Runnable beforeWrite, Consumer<Document> afterWrite) throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        if (uri == null || uri.isBlank())
            throw new IllegalStateException("Isolated test Mongo URI is required");
        this.beforeWrite = Objects.requireNonNull(beforeWrite);
        this.afterWrite = Objects.requireNonNull(afterWrite);
        client = MongoClients.create(uri);
        store = Morphia.createDatastore(client, name);
        try {
            store.getMapper().map(Player.class);
            delegate = new PlayerSaveGuard.MongoStorage(store);
            var collectionField = PlayerSaveGuard.MongoStorage.class.getDeclaredField("collection");
            collectionField.setAccessible(true);
            var collection = (MongoCollection<?>) collectionField.get(delegate);
            assertEquals(WriteConcern.MAJORITY.withJournal(true), collection.getWriteConcern());
            assertEquals(ReadConcern.MAJORITY, collection.getReadConcern());
            assertEquals(ReadPreference.primary(), collection.getReadPreference());
            // Fixture players can override getUid; keep their persisted identity identical.
            player.setUid(player.getUid());
            var field = Player.class.getDeclaredField("saveGuard");
            field.setAccessible(true);
            field.set(
                    player,
                    new PlayerSaveGuard(player, player.getSaveStamp(), false, ignored -> this));
        } catch (Exception | Error error) {
            client.close();
            throw error;
        }
    }

    public Datastore datastore() {
        return store;
    }

    @Override
    public Document read(int uid) {
        return delegate.read(uid);
    }

    @Override
    public void insert(Document document) {
        beforeWrite.run();
        delegate.insert(document);
        afterWrite.accept(document);
    }

    @Override
    public boolean replace(Document filter, Document document) {
        beforeWrite.run();
        boolean replaced = delegate.replace(filter, document);
        if (replaced) afterWrite.accept(document);
        return replaced;
    }

    @Override
    public boolean advance(Document filter, PlayerSaveStamp stamp) {
        return delegate.advance(filter, stamp);
    }

    @Override
    public void close() {
        try {
            if (!name.startsWith("lunagc_test_guarded_flow_")
                    || !name.equals(store.getDatabase().getName()))
                throw new IllegalStateException("Unexpected test database");
            store.getDatabase().drop();
        } finally {
            client.close();
        }
    }
}
