package emu.grasscutter.game.inventory;

import dev.morphia.Datastore;

import org.bson.Document;

import java.util.function.Consumer;

/** Business callbacks around real majority-acknowledged item writes in isolated databases. */
public final class GuardedItemTestStore {
    private GuardedItemTestStore() {}

    public static void bind(
            GameItem item,
            Datastore store,
            boolean loaded,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        bind(item, store, loaded ? store : null, loaded, before, after);
    }

    public static void bind(
            GameItem item,
            Datastore store,
            Datastore destination,
            boolean loaded,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        if (!store.getDatabase().getName().startsWith("lunagc_"))
            throw new IllegalArgumentException("Expected isolated fixture database");
        bind(item, store, destination, loaded, false, before, after);
    }

    public static void bind(
            GameItem item,
            Datastore store,
            Datastore destination,
            boolean loaded,
            boolean observeDeletionConfirmation,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        if (!store.getDatabase().getName().startsWith("lunagc_"))
            throw new IllegalArgumentException("Expected isolated fixture database");
        var delegate = new ItemSaveGuard.MongoStorage(store);
        var id = item.ensurePersistenceId();
        var row = loaded ? delegate.collection.find(new Document("_id", id)).first() : null;
        if (loaded && row == null) throw new IllegalStateException("Item fixture missing");
        var wrapper =
                new ItemSaveGuard.Storage() {
                    public Document read(org.bson.types.ObjectId id) {
                        var row = delegate.read(id);
                        // A queued deletion can finish before the explicit economic save.
                        // That save confirms the terminal row by reading it without a replacement.
                        // Pending-write recovery and food authorization reads stay outside this
                        // seam.
                        if (observeDeletionConfirmation
                                && row != null
                                && Boolean.TRUE.equals(row.get("deleted"))
                                && StackWalker.getInstance()
                                        .walk(
                                                frames ->
                                                        frames.skip(1)
                                                                .findFirst()
                                                                .map(
                                                                        frame ->
                                                                                frame.getClassName()
                                                                                                .equals(
                                                                                                        ItemSaveGuard
                                                                                                                .class
                                                                                                                .getName())
                                                                                        && frame.getMethodName()
                                                                                                .equals(
                                                                                                        "write"))
                                                                .orElse(false))) {
                            before.accept(row);
                            after.accept(row);
                        }
                        return row;
                    }

                    public void insert(Document row) {
                        before.accept(row);
                        delegate.insert(row);
                        after.accept(row);
                    }

                    public boolean replace(Document filter, Document row) {
                        before.accept(row);
                        boolean saved = delegate.replace(filter, row);
                        if (saved) after.accept(row);
                        return saved;
                    }

                    public boolean advance(Document filter, ItemSaveStamp stamp) {
                        return delegate.advance(filter, stamp);
                    }
                };
        var f = GameItem.class.getDeclaredField("saveGuard");
        f.setAccessible(true);
        f.set(item, new ItemSaveGuard(item, row, loaded ? destination : null, ignored -> wrapper));
    }
}
