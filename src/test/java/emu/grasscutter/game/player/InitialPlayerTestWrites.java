package emu.grasscutter.game.player;

import dev.morphia.Datastore;

import org.bson.Document;

import java.util.function.Consumer;

/** Observe the real initial guarded insert in an explicitly isolated UID fixture database. */
public final class InitialPlayerTestWrites {
    private InitialPlayerTestWrites() {}

    public static void bind(
            Player player, Datastore store, Runnable before, Consumer<Document> after) {
        if (!store.getDatabase().getName().startsWith("lunagc_uid_game_"))
            throw new IllegalArgumentException("Expected isolated UID fixture database");
        var actual = new PlayerSaveGuard.MongoStorage(store);
        var observed =
                new PlayerSaveGuard.Storage() {
                    public Document read(int uid) {
                        return actual.read(uid);
                    }

                    public void insert(Document document) {
                        before.run();
                        actual.insert(document);
                        after.accept(document);
                    }

                    public boolean replace(Document filter, Document document) {
                        return actual.replace(filter, document);
                    }

                    public boolean advance(Document filter, PlayerSaveStamp stamp) {
                        return actual.advance(filter, stamp);
                    }
                };
        try {
            var field = Player.class.getDeclaredField("saveGuard");
            field.setAccessible(true);
            field.set(player, new PlayerSaveGuard(player, null, false, ignored -> observed));
        } catch (ReflectiveOperationException failed) {
            throw new AssertionError(failed);
        }
    }
}
