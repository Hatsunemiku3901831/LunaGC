package emu.grasscutter.game.world;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import com.mongodb.WriteConcern;

import dev.morphia.Datastore;
import dev.morphia.DeleteOptions;
import dev.morphia.InsertOneOptions;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.SceneType;

import java.util.*;

/** Persistent world placements; storage is acknowledged before the live scene changes. */
public final class SceneBuildingManager {
    public interface Store {
        List<SceneBuilding> load(int ownerUid, int sceneId);

        void save(SceneBuilding building);

        boolean delete(int ownerUid, String id);
    }

    public static final class MongoStore implements Store {
        private final Datastore datastore;

        public MongoStore(Datastore datastore) {
            this.datastore = Objects.requireNonNull(datastore);
        }

        @Override
        public List<SceneBuilding> load(int uid, int scene) {
            return datastore
                    .find(SceneBuilding.class)
                    .filter(Filters.eq("ownerUid", uid), Filters.eq("sceneId", scene))
                    .iterator()
                    .toList();
        }

        @Override
        public void save(SceneBuilding building) {
            datastore.save(
                    building,
                    new InsertOneOptions().writeConcern(WriteConcern.MAJORITY.withJournal(true)));
        }

        @Override
        public boolean delete(int uid, String id) {
            datastore
                    .find(SceneBuilding.class)
                    .filter(Filters.eq("ownerUid", uid), Filters.eq("id", id))
                    .delete(
                            new DeleteOptions()
                                    .writeConcern(WriteConcern.MAJORITY.withJournal(true)));
            // Removing an already absent durable row is an idempotent successful deletion.
            return true;
        }
    }

    private record Live(SceneBuilding building, EntityGadget entity) {}

    private final Scene scene;
    private final Store injected;
    private Store store;
    private boolean loaded;
    private Player catalogOwner;
    private final Map<String, Live> live = new LinkedHashMap<>();
    private final Map<String, SceneBuilding> catalog = new LinkedHashMap<>();

    public SceneBuildingManager(Scene scene) {
        this(scene, null);
    }

    public SceneBuildingManager(Scene scene, Store store) {
        this.scene = Objects.requireNonNull(scene);
        this.injected = store;
    }

    private Store store() {
        if (store == null)
            store =
                    injected != null
                            ? injected
                            : new MongoStore(DatabaseManager.getGameDatastore());
        return store;
    }

    private void owner(Player player) {
        if (player == null
                || player != scene.getHost()
                || player.getScene() != scene
                || player.getWorld() != scene.getWorld()
                || player.getUid() <= 0
                || scene.getSceneType() != SceneType.SCENE_WORLD
                || catalogOwner != null && catalogOwner != player)
            throw new IllegalStateException(
                    "Only the current world host can manage this scene's buildings");
    }

    private static boolean finite(Position value) {
        return value != null
                && Float.isFinite(value.getX())
                && Float.isFinite(value.getY())
                && Float.isFinite(value.getZ());
    }

    private void valid(int gadget, Position pos, Position rot) {
        var data = GameData.getGadgetDataMap().get(gadget);
        if (data == null || data.getType() == EntityType.Vehicle || !finite(pos) || !finite(rot))
            throw new IllegalArgumentException("Invalid building gadget or coordinates");
    }

    private EntityGadget entity(SceneBuilding row) {
        var result =
                new EntityGadget(scene, row.getGadgetId(), row.getPosition(), row.getRotation());
        result.buildContent();
        return result;
    }

    private void room() {
        if (scene.getEntities().size() >= GAME_OPTIONS.sceneEntityLimit)
            throw new IllegalStateException("Scene entity limit reached");
    }

    public SceneBuilding create(
            Player player, int gadget, Position pos, Position rot, boolean persistent) {
        if (player == null) throw new IllegalArgumentException("A world host is required");
        synchronized (player.getSceneTransferLock()) {
            synchronized (this) {
                owner(player);
                restore(player);
                valid(gadget, pos, rot);
                synchronized (scene) {
                    room();
                }
                var row =
                        new SceneBuilding(
                                player.getUid(), scene.getId(), gadget, pos, rot, persistent);
                // Construct before persisting: invalid resources must not leave a durable ghost.
                var entity = entity(row);
                if (persistent) {
                    try {
                        store().save(row);
                    } catch (RuntimeException failure) {
                        loaded = false;
                        throw failure;
                    }
                }
                synchronized (scene) {
                    // Another spawn may have filled the scene while storage was acknowledging.
                    if (!persistent) room();
                    catalog.put(row.getId(), row);
                    if (scene.getEntities().size() < GAME_OPTIONS.sceneEntityLimit) {
                        scene.addEntity(entity);
                        live.put(row.getId(), new Live(row, entity));
                    }
                    return row;
                }
            }
        }
    }

    public synchronized boolean isSpawned(String id) {
        var placement = live.get(id);
        return placement != null
                && scene.getEntities().get(placement.entity().getId()) == placement.entity();
    }

    public List<SceneBuilding> list(Player player) {
        if (player == null) throw new IllegalArgumentException("A world host is required");
        synchronized (player.getSceneTransferLock()) {
            synchronized (this) {
                owner(player);
                restore(player);
                return List.copyOf(catalog.values());
            }
        }
    }

    public boolean remove(Player player, String id) {
        if (player == null) throw new IllegalArgumentException("A world host is required");
        synchronized (player.getSceneTransferLock()) {
            synchronized (this) {
                owner(player);
                restore(player);
                var row = catalog.get(id);
                if (row == null) return false;
                if (row.isPersistent() && !store().delete(player.getUid(), id)) return false;
                synchronized (scene) {
                    catalog.remove(id);
                    var placement = live.remove(id);
                    if (placement != null) {
                        var entity = placement.entity();
                        if (scene.getEntities().get(entity.getId()) == entity)
                            scene.removeEntity(entity);
                    }
                }
                return true;
            }
        }
    }

    public void restore(Player player) {
        if (player == null) throw new IllegalArgumentException("A world host is required");
        synchronized (player.getSceneTransferLock()) {
            synchronized (this) {
                restoreCurrent(player);
            }
        }
    }

    private void restoreCurrent(Player player) {
        owner(player);
        if (!loaded) {
            // A captured store keeps the same database target for this live scene catalog.
            var rows = store().load(player.getUid(), scene.getId());
            catalogOwner = player;
            for (var row : rows) {
                if (row == null
                        || row.getId() == null
                        || row.getOwnerUid() != player.getUid()
                        || row.getSceneId() != scene.getId()) continue;
                try {
                    valid(row.getGadgetId(), row.getPosition(), row.getRotation());
                    catalog.putIfAbsent(row.getId(), row);
                } catch (IllegalArgumentException invalid) {
                    emu.grasscutter.Grasscutter.getLogger()
                            .warn("Skipped invalid scene building {}", row.getId());
                }
            }
            loaded = true;
        }
        synchronized (scene) {
            owner(player);
            for (var row : catalog.values()) {
                if (live.containsKey(row.getId())) continue;
                try {
                    valid(row.getGadgetId(), row.getPosition(), row.getRotation());
                    if (scene.getEntities().size() >= GAME_OPTIONS.sceneEntityLimit) break;
                    var entity = entity(row);
                    scene.addEntity(entity);
                    live.put(row.getId(), new Live(row, entity));
                } catch (IllegalArgumentException invalid) {
                    emu.grasscutter.Grasscutter.getLogger()
                            .warn("Skipped invalid scene building {}", row.getId());
                }
            }
        }
    }
}
