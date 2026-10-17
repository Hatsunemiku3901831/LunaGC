package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.player.PlayerObtainItemEvent;

import org.bson.Document;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Keep real item writes and existing inventory overrides while retaining synchronous ACK faults.
 */
public final class ObservedItemTestWrites implements AutoCloseable {
    final Player player;
    final Datastore store, destination;
    final Consumer<Document> before, after;
    final Set<GameItem> bound = Collections.newSetFromMap(new IdentityHashMap<>());
    final PluginManager manager, previous;
    final java.lang.reflect.Field pluginField;
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final EventHandler<PlayerObtainItemEvent> listener;

    public ObservedItemTestWrites(
            Player player,
            Datastore actual,
            Datastore destination,
            Consumer<Document> before,
            Consumer<Document> after)
            throws Exception {
        this.player = player;
        store = actual;
        this.destination = destination;
        this.before = before;
        this.after = after;
        store.getMapper().map(GameItem.class);
        pluginField = Grasscutter.class.getDeclaredField("pluginManager");
        pluginField.setAccessible(true);
        previous = (PluginManager) pluginField.get(null);
        if (previous == null) {
            var path = emu.grasscutter.utils.FileUtils.getPluginPath("");
            java.nio.file.Files.createDirectories(path);
            try (var files = java.nio.file.Files.list(path)) {
                assertTrue(
                        files.noneMatch(p -> p.toString().endsWith(".jar")),
                        "Fixture cannot load external plugins");
            }
            manager = new PluginManager();
            pluginField.set(null, manager);
        } else manager = previous;
        bindNewItems();
        listener =
                new EventHandler<>(PlayerObtainItemEvent.class)
                        .priority(emu.grasscutter.server.event.HandlerPriority.NORMAL)
                        .listener(
                                event -> {
                                    if (event.getPlayer() != player
                                            || emu.grasscutter.database.DatabaseManager
                                                            .getGameDatastore()
                                                    != destination) return;
                                    try {
                                        bindNewItems();
                                    } catch (Throwable error) {
                                        failure.set(error);
                                        throw new IllegalStateException(
                                                "Item writer fixture failed", error);
                                    }
                                });
        manager.registerListener(listener);
    }

    private void bindNewItems() throws Exception {
        assertTrue(
                emu.grasscutter.database.DatabaseHelper.getEventExecutorItem()
                        .awaitIdle(Duration.ofSeconds(5)));
        for (var item : player.getInventory().getItems().values()) {
            if (bound.contains(item)) continue;
            boolean loaded =
                    item.getObjectId() != null
                            && store.getDatabase()
                                            .getCollection("items")
                                            .find(new Document("_id", item.getObjectId()))
                                            .first()
                                    != null;
            GuardedItemTestStore.bind(
                    item,
                    store,
                    destination,
                    loaded,
                    true,
                    doc -> {
                        if (isSynchronousWrite()) before.accept(doc);
                    },
                    doc -> {
                        if (isSynchronousWrite()) after.accept(doc);
                    });
            bound.add(item);
        }
    }

    public static boolean isSynchronousWrite() {
        return StackWalker.getInstance()
                .walk(
                        frames ->
                                frames.anyMatch(
                                        f ->
                                                f.getClassName()
                                                                .equals(
                                                                        "emu.grasscutter.database.DatabaseHelper")
                                                        && f.getMethodName()
                                                                .equals("saveGameSync")));
    }

    @Override
    public void close() throws Exception {
        var f = PluginManager.class.getDeclaredField("handlers");
        f.setAccessible(true);
        for (Object list : ((Map<?, ?>) f.get(manager)).values()) ((List<?>) list).remove(listener);
        if (previous == null && pluginField.get(null) == manager) pluginField.set(null, null);
        assertNull(failure.get(), "Item writer fixture binding failed");
    }
}
