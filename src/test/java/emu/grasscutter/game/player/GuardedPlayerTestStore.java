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

    /** Match Player's post-load guard when a persistence fixture seeds a profile directly. */
    public static void loadSeededProfile(Player player, Datastore datastore) throws Exception {
        loadSeededProfile(player, datastore, () -> {}, ignored -> {});
    }

    public static void loadSeededProfile(
            Player player, Datastore datastore, Runnable before, Consumer<Document> after)
            throws Exception {
        var name = datastore.getDatabase().getName();
        if (!name.startsWith("lunagc_aster_shop_")
                && !name.startsWith("lunagc_aster_special_")
                && !name.startsWith("lunagc_test_"))
            throw new IllegalArgumentException("Expected an isolated fixture database");
        var row =
                datastore
                        .getDatabase()
                        .getCollection("players")
                        .withReadConcern(ReadConcern.MAJORITY)
                        .withReadPreference(ReadPreference.primary())
                        .find(new Document("_id", player.getUid()))
                        .first();
        if (row == null) throw new IllegalStateException("Fixture Player has not been seeded");
        var stamp = PlayerSaveStamp.from(row);
        player.setSaveStamp(stamp);
        var field = Player.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        var delegate = new PlayerSaveGuard.MongoStorage(datastore);
        var wrapper =
                new PlayerSaveGuard.Storage() {
                    public Document read(int uid) {
                        return delegate.read(uid);
                    }

                    public void insert(Document document) {
                        before.run();
                        delegate.insert(document);
                        after.accept(document);
                    }

                    public boolean replace(Document filter, Document document) {
                        before.run();
                        boolean result = delegate.replace(filter, document);
                        if (result) after.accept(document);
                        return result;
                    }

                    public boolean advance(Document filter, PlayerSaveStamp value) {
                        return delegate.advance(filter, value);
                    }
                };
        field.set(player, new PlayerSaveGuard(player, stamp, true, ignored -> wrapper));
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
