package emu.grasscutter.game.world;

import static emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType.SCRIPT;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.EntityWorld;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.SystemHintOuterClass;
import emu.grasscutter.net.proto.SystemHintTypeOuterClass;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.ConversionUtils;
import io.netty.util.concurrent.FastThreadLocalThread;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.val;
import org.jetbrains.annotations.NotNull;

public class World implements Iterable<Player> {
    @Getter private final GameServer server;
    @Getter private volatile Player host;
    @Getter private final List<Player> players;
    @Getter private final Int2ObjectMap<Scene> scenes;

    @Getter private EntityWorld entity;
    // Entities are also created under scene locks; allocation must not acquire the world monitor.
    private final AtomicInteger nextEntityId = new AtomicInteger();
    private final AtomicInteger nextPeerId = new AtomicInteger();
    // Only membership metadata is changed here. No scene callbacks, packet sends or
    // other-player locks run under this lock, so two world monitors cannot be inverted.
    private static final Object MEMBERSHIP_LOCK = new Object();
    private volatile boolean closing;
    private final AtomicInteger pendingDepartures = new AtomicInteger();
    private final ConcurrentMap<Player, EntrySource> pendingEvacuations = new ConcurrentHashMap<>();
    private final AtomicBoolean evacuatingGuests = new AtomicBoolean();
    private volatile long nextEvacuationAt;
    private int worldLevel;

    @Getter private boolean isMultiplayer = false;
    @Getter private boolean timeLocked;

    private long lastUpdateTime;
    @Getter protected int tickCount = 0;
    private long lastTimeSync = 0;
    private long lastTimeStore = 0;
    @Getter private boolean isPaused = false;
    @Getter private long currentWorldTime;

    private static final ExecutorService eventExecutor =
            new ThreadPoolExecutor(
                    4,
                    4,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.AbortPolicy());

    public World(Player player) {
        this(player, false);
    }

    public World(Player player, boolean isMultiplayer) {
        this.host = player;
        this.server = player.getServer();
        this.players = new CopyOnWriteArrayList<>();
        this.scenes = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

        // this.levelEntityId = this.getNextEntityId(EntityIdType.MPLEVEL);
        this.entity = new EntityWorld(this);
        this.worldLevel = player.getWorldLevel();
        this.isMultiplayer = isMultiplayer;
        this.timeLocked = player.getProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED) != 0;

        this.lastUpdateTime = System.currentTimeMillis();
        this.currentWorldTime = host.getPlayerGameTime();

    }

    public World(GameServer server, Player owner) {
        this.server = server;
        this.host = owner;
        this.players = new CopyOnWriteArrayList<>();
        this.scenes = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.entity = new EntityWorld(this);
        this.lastUpdateTime = System.currentTimeMillis();

    }

    public int getLevelEntityId() {
        return entity.getId();
    }

    /**
     * Gets the peer ID of the world's host.
     *
     * @return The peer ID of the world's host. 0 if the host is null.
     */
    public int getHostPeerId() {
        return this.getHost() == null ? 0 : this.getHost().getPeerId();
    }

    public int getNextPeerId() {
        return this.nextPeerId.incrementAndGet();
    }

    public int getWorldLevel() {
        return worldLevel;
    }

    public void setWorldLevel(int worldLevel) {
        this.worldLevel = worldLevel;
    }

    protected synchronized void setHost(Player host) {
        this.host = host;
    }

    /**
     * Gets an associated scene by ID. Creates a new instance of the scene if it doesn't exist.
     *
     * @param sceneId The scene ID.
     * @return The scene.
     */
    @Nullable public Scene getSceneById(int sceneId) {
        synchronized (this.getScenes()) {
            // Get scene normally
            var scene = this.getScenes().get(sceneId);
            if (scene != null) {
                return scene;
            }

            // Create scene from scene data if it doesn't exist
            var sceneData = GameData.getSceneDataMap().get(sceneId);
            if (sceneData != null) {
                scene = new Scene(this, sceneData);
                this.registerScene(scene);
                return scene;
            }

            return null;
        }
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    /** Atomically retires an empty world so a concurrent join cannot enter an unregistered world. */
    public boolean tryCloseIfEmpty() {
        synchronized (MEMBERSHIP_LOCK) {
            if (!this.players.isEmpty() || !this.pendingEvacuations.isEmpty() || this.pendingDepartures.get() != 0) return false;
            this.closing = true;
            return true;
        }
    }

    /**
     * Gets the next entity ID for the specified entity type.
     *
     * @param idType The entity type.
     * @return The next entity ID.
     */
    public int getNextEntityId(EntityIdType idType) {
        return (idType.getId() << GameConstants.ENTITY_ID_BIT_SHIFT)
                + this.nextEntityId.incrementAndGet();
    }

    public void addPlayer(Player player) {
        addPlayerToWorld(player, null, null, null);
    }

    public void addPlayer(Player player, int newSceneId) {
        addPlayerToWorld(player, newSceneId, null, null);
    }

    public boolean tryAddPlayer(Player player) {
        return addPlayerToWorld(player, null, null, null);
    }

    public boolean tryAddPlayer(Player player, int newSceneId) {
        return addPlayerToWorld(player, newSceneId, null, null);
    }

    public record WorldEntryContext(
            World previousWorld, int previousSceneId, Position previousPosition, Position previousRotation) {}

    private record EntryAction(TeleportProperties properties, Consumer<WorldEntryContext> onEntered) {}
    // A non-null source wrapper also permits a conditional retry after failed cleanup left no world.
    private record EntrySource(World world) {}

    public boolean tryAddPlayer(Player player, World expectedSource, Consumer<WorldEntryContext> onEntered) {
        if (expectedSource == null) return false;
        return addPlayerToWorld(player, null, new EntrySource(expectedSource), new EntryAction(null, onEntered));
    }

    /** Position and caller notifications are applied only after entry succeeds, under the transfer lock. */
    public boolean tryAddPlayer(Player player, World expectedSource, TeleportProperties properties,
            Consumer<WorldEntryContext> onEntered) {
        if (expectedSource == null || properties == null) return false;
        return addPlayerToWorld(player, properties.getSceneId(), new EntrySource(expectedSource),
                new EntryAction(properties, onEntered));
    }

    private record Departure(World world, Scene scene, List<Player> members, List<Player> kicked, AtomicBoolean pending) {}

    private boolean addPlayerToWorld(Player player, Integer newSceneId, EntrySource expectedPrevious, EntryAction action) {
        Departure departure = null;
        Departure failedEntry = null;
        try {
            synchronized (player.getSceneTransferLock()) {
                if (expectedPrevious != null && player.getWorld() != expectedPrevious.world) return false;
                if (this.players.contains(player)) return false;
                int sceneId = newSceneId == null ? player.getSceneId() : newSceneId;
                var scene = this.getSceneById(sceneId);
                if (scene == null
                        || !emu.grasscutter.game.tps.TpsAvatarSystem.canEnter(player, scene.getSceneData()))
                    return false;

                var previousWorld = player.getWorld();
                var previousScene = player.getScene();
                var context = new WorldEntryContext(previousWorld, previousScene == null ? player.getSceneId() : previousScene.getId(),
                        player.getPosition().clone(), player.getRotation().clone());
                var position = action == null || action.properties == null || action.properties.getTeleportTo() == null
                        ? null : action.properties.getTeleportTo().clone();
                var rotation = action == null || action.properties == null || action.properties.getTeleportRot() == null
                        ? null : action.properties.getTeleportRot().clone();
                int previousPeerId = player.getPeerId();
                final int peerId;
                synchronized (MEMBERSHIP_LOCK) {
                    if (this.closing || this.players.contains(player)) return false;
                    if (this.getScenes().get(sceneId) != scene) return false;
                    if (previousWorld != null)
                        departure = previousWorld.detachPlayer(player, previousScene);
                    this.players.add(player);
                    peerId = peerIdForJoiningPlayer(player);
                    // Publish only after construction and admission preflight have completed.
                    this.server.registerWorld(this);
                }

                try {
                    player.setWorld(null);
                    if (departure != null) departure.world.finishDeparture(player, departure);
                    else if (previousScene != null) previousScene.removePlayer(player);

                    player.setWorld(this);
                    player.setPeerId(peerId);
                    player.getTeamManager().setEntity(new EntityTeam(player));
                    if (this.isMultiplayer()) {
                        player.getTeamManager().getMpTeam().copyFrom(
                                player.getTeamManager().getCurrentSinglePlayerTeamInfo(),
                                player.getTeamManager().getMaxTeamSize());
                        player.getTeamManager().setCurrentCharacterIndex(0);
                    }
                    player.setSceneId(sceneId);
                    if (position != null) player.getPosition().set(position);
                    if (rotation != null) player.getRotation().set(rotation);
                    scene.addPlayer(player);
                    if (action != null && action.onEntered != null) action.onEntered.accept(context);
                    if (this.isMultiplayer() && player != this.getHost())
                        this.broadcastPacket(new PacketPlayerChatNotify(player, 0,
                                SystemHintOuterClass.SystemHint.newBuilder()
                                        .setType(SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_ENTER_WORLD.getNumber())
                                        .build()));
                } catch (RuntimeException | Error failure) {
                    var currentScene = player.getScene();
                    var enteredScene = currentScene != null && currentScene.getWorld() == this
                            ? currentScene : null;
                    synchronized (MEMBERSHIP_LOCK) {
                        failedEntry = detachPlayer(player, enteredScene);
                    }
                    if (player.getWorld() == this) player.setWorld(null);
                    if (enteredScene != null) {
                        try {
                            enteredScene.removePlayer(player);
                        } catch (RuntimeException | Error cleanupFailure) {
                            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                        }
                    }
                    if (departure != null && departure.world.restoreDeparture(player, departure))
                        departure = null;
                    player.getPosition().set(context.previousPosition);
                    player.getRotation().set(context.previousRotation);
                    player.setSceneId(player.getScene() == null ? context.previousSceneId : player.getScene().getId());
                    player.setPeerId(previousPeerId);
                    throw failure;
                }
            }
        } finally {
            // Moving another player must never be done while holding the departing host's lock.
            if (departure != null) {
                departure.world.completeDeparture(departure);
                departure.world.evacuateGuests(departure.kicked);
                departure.world.updatePlayerInfos(player);
            }
            if (failedEntry != null) {
                completeDeparture(failedEntry);
                evacuateGuests(failedEntry.kicked);
                updatePlayerInfos(player);
            }
        }
        if (this.getPlayerCount() > 1) this.updatePlayerInfos(player);
        return true;
    }

    /** Called only while MEMBERSHIP_LOCK is held; overrides must only allocate an ID. */
    protected int peerIdForJoiningPlayer(Player player) {
        return this.getNextPeerId();
    }

    protected boolean disbandsOnHostLeave() {
        return true;
    }

    private Departure detachPlayer(Player player, Scene scene) {
        if (this.players.stream().noneMatch(member -> member == player)) return null;
        var members = List.copyOf(this.players);
        this.players.removeIf(member -> member == player);
        List<Player> kicked = List.of();
        if (player == this.getHost() && this.disbandsOnHostLeave()) {
            this.closing = true;
            kicked = List.copyOf(this.players);
        }
        this.pendingDepartures.incrementAndGet();
        return new Departure(this, scene, members, kicked, new AtomicBoolean(true));
    }

    public void removePlayer(Player player) {
        Departure departure = null;
        try {
            synchronized (player.getSceneTransferLock()) {
                if (player.getWorld() != this) return;
                var scene = player.getScene();
                synchronized (MEMBERSHIP_LOCK) {
                    departure = detachPlayer(player, scene);
                }
                if (departure == null) return;
                player.setWorld(null);
                try {
                    finishDeparture(player, departure);
                } catch (RuntimeException | Error failure) {
                    if (restoreDeparture(player, departure)) departure = null;
                    throw failure;
                }
            }
        } finally {
            // Run even if the departing host's cleanup throws, after releasing its transfer lock.
            if (departure != null) {
                completeDeparture(departure);
                evacuateGuests(departure.kicked);
                updatePlayerInfos(player);
            }
        }
    }

    private void finishDeparture(Player player, Departure departure) {
        var teamIds = departure.members.stream()
                .map(p -> p.getTeamManager().getEntity() == null
                        ? 0 : p.getTeamManager().getEntity().getId()).toList();
        int sceneId = departure.scene == null ? player.getSceneId() : departure.scene.getId();
        this.sendDepartureTeamPacket(player, sceneId, teamIds);
        if (departure.scene != null && departure.scene.getWorld() == this)
            departure.scene.removePlayer(player);
        if (player != this.getHost() || !this.disbandsOnHostLeave())
            this.broadcastPacket(new PacketPlayerChatNotify(player, 0,
                    SystemHintOuterClass.SystemHint.newBuilder()
                            .setType(SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_LEAVE_WORLD.getNumber())
                            .build()));
    }

    private boolean restoreDeparture(Player player, Departure departure) {
        // If scene removal never happened, keep the actual source membership. Once the player
        // has left its scene, do not re-register a stale scene or repeat completed callbacks.
        var scene = departure.scene;
        if (scene == null || player.getScene() != scene
                || scene.getPlayers().stream().noneMatch(member -> member == player)) return false;
        synchronized (MEMBERSHIP_LOCK) {
            if (this.players.stream().noneMatch(member -> member == player)) this.players.add(player);
            if (player == this.getHost() && this.disbandsOnHostLeave()) this.closing = false;
            completeDeparture(departure);
        }
        player.setWorld(this);
        player.setSceneId(scene.getId());
        return true;
    }

    private void completeDeparture(Departure departure) {
        if (departure.pending.compareAndSet(true, false)) this.pendingDepartures.decrementAndGet();
    }

    protected void sendDepartureTeamPacket(Player player, int sceneId, List<Integer> teamIds) {
        player.sendPacket(new PacketDelTeamEntityNotify(sceneId, teamIds));
    }

    private void evacuateGuests(List<Player> kicked) {
        for (var victim : kicked) pendingEvacuations.putIfAbsent(victim, new EntrySource(this));
        retryGuestEvacuations();
    }

    protected long evacuationTimeMillis() {
        return System.currentTimeMillis();
    }

    private void retryGuestEvacuations() {
        if (pendingEvacuations.isEmpty() || evacuationTimeMillis() < nextEvacuationAt
                || !evacuatingGuests.compareAndSet(false, true)) return;
        try {
            nextEvacuationAt = evacuationTimeMillis() + 1000L;
            for (var entry : List.copyOf(pendingEvacuations.entrySet())) {
                var victim = entry.getKey();
                var expectedSource = entry.getValue();
                synchronized (victim.getSceneTransferLock()) {
                    // A joining guest is pre-registered before its old scene is cleaned up.
                    // Wait for that transfer instead of treating its temporary pointer as a departure.
                    if (!victim.isOnline() || victim.getWorld() != expectedSource.world) {
                        pendingEvacuations.remove(victim, expectedSource);
                        continue;
                    }
                }
                World destination = null;
                try {
                    destination = new World(victim);
                    var personal = destination;
                    Consumer<WorldEntryContext> notify = context -> {
                        var properties = TeleportProperties.builder().sceneId(victim.getSceneId())
                                .teleportTo(victim.getPosition().clone()).teleportRot(victim.getRotation().clone())
                                .enterType(EnterType.EnterType_ENTER_SELF).enterReason(EnterReason.TeamKick).build();
                        victim.sendPacket(new PacketPlayerEnterSceneNotify(victim, personal, properties));
                        victim.sendPacket(new PacketEnterScenePeerNotify(victim));
                    };
                    boolean moved = personal.addPlayerToWorld(victim, null, expectedSource, new EntryAction(null, notify));
                    if (!moved && victim.getWorld() == expectedSource.world) {
                        // A deleted dungeon/home scene cannot strand a guest in a closed world.
                        var fallback = personal.getSceneById(3);
                        if (fallback != null) {
                            var config = fallback.getScriptManager().getConfig();
                            var properties = TeleportProperties.builder().sceneId(3)
                                    .teleportTo(config != null && config.born_pos != null
                                            ? config.born_pos : GameConstants.START_POSITION)
                                    .teleportRot(config != null && config.born_rot != null
                                            ? config.born_rot : new Position()).build();
                            moved = personal.addPlayerToWorld(victim, 3, expectedSource, new EntryAction(properties, notify));
                        }
                    }
                    if (moved || victim.getWorld() != expectedSource.world)
                        pendingEvacuations.remove(victim, expectedSource);
                } catch (RuntimeException failure) {
                    // Each guest is independent. A later guest must still reach its own world.
                    if (victim.getWorld() == null && victim.isOnline())
                        pendingEvacuations.replace(victim, expectedSource, new EntrySource(null));
                    else if (victim.getWorld() != expectedSource.world)
                        pendingEvacuations.remove(victim, expectedSource);
                    Grasscutter.getLogger().error("Could not return guest {} to its personal world; retrying later", victim.getUid(), failure);
                } finally {
                    if (destination != null && destination.getPlayerCount() == 0) {
                        try { this.server.deregisterWorld(destination); }
                        catch (RuntimeException failure) {
                            Grasscutter.getLogger().error("Could not clean up empty guest world for {}", victim.getUid(), failure);
                        }
                    }
                }
            }
        } finally {
            evacuatingGuests.set(false);
        }
    }

    public void registerScene(Scene scene) {
        this.getScenes().put(scene.getId(), scene);
    }

    public void deregisterScene(Scene scene) {
        scene.saveGroups();
        this.getScenes().remove(scene.getId(), scene);
    }

    public void save() {
        final List<Scene> snapshot;
        synchronized (this.scenes) { snapshot = new ArrayList<>(this.scenes.values()); }
        snapshot.forEach(Scene::saveGroups);
    }

    public void queueTransferPlayerToScene(Player player, int sceneId, Position pos, int delayMs) {
        player.setQueuedTeleport(
                eventExecutor.submit(
                        () -> {
                            try {
                                Thread.sleep(delayMs);
                                // The player can log out during the delay: onLogout ->
                                // World.removePlayer nulls their world reference, and the
                                // enter-scene notify dereferences it unconditionally, so a
                                // teleport that fires after the session closed used to NPE
                                // down the whole transfer chain instead of being dropped.
                                // There is nothing to teleport at that point -- the client is
                                // gone and would never have read the packet anyway.
                                if (player.getWorld() == null) {
                                    Grasscutter.getLogger()
                                            .trace(
                                                    "queueTransferPlayerToScene: skipping teleport to"
                                                            + " scene {} for uid {} - no world (logged out"
                                                            + " during the {}ms delay)",
                                                    sceneId,
                                                    player.getUid(),
                                                    delayMs);
                                    return;
                                }
                                transferPlayerToScene(player, sceneId, pos);
                            } catch (InterruptedException e) {
                                Grasscutter.getLogger()
                                        .trace(
                                                "queueTransferPlayerToScene: teleport to scene {} is interrupted", sceneId);
                            } catch (Throwable e) {
                                // submit() parks anything thrown in a Future nobody reads, so a failed
                                // teleport just left the player standing there with nothing logged
                                Grasscutter.getLogger()
                                        .error("Queued teleport to scene {} failed.", sceneId, e);
                            }
                        }));
    }

    public boolean transferPlayerToScene(Player player, int sceneId, Position pos) {
        return this.transferPlayerToScene(player, sceneId, TeleportType.INTERNAL, null, pos);
    }

    public boolean transferPlayerToScene(
            Player player, int sceneId, TeleportType teleportType, Position pos) {
        return this.transferPlayerToScene(player, sceneId, teleportType, null, pos);
    }

    public boolean transferPlayerToScene(Player player, int sceneId, DungeonData data) {
        return this.transferPlayerToScene(player, sceneId, TeleportType.DUNGEON, data, null);
    }

    public boolean transferPlayerToScene(
            Player player,
            int sceneId,
            TeleportType teleportType,
            DungeonData dungeonData,
            Position teleportTo) {
        EnterReason enterReason =
                switch (teleportType) {
                        // shouldn't affect the teleportation, but its clearer when inspecting the packets
                        // TODO add more conditions for different reason.
                    case INTERNAL -> EnterReason.TransPoint;
                    case WAYPOINT -> EnterReason.TransPoint;
                    case MAP -> EnterReason.TransPoint;
                    case COMMAND -> EnterReason.Gm;
                    case SCRIPT -> EnterReason.Lua;
                    case CLIENT -> EnterReason.ClientTransmit;
                    case DUNGEON -> EnterReason.DungeonEnter;
                    default -> EnterReason.None;
                };
        return transferPlayerToScene(
                player, sceneId, teleportType, enterReason, dungeonData, teleportTo);
    }

    public boolean transferPlayerToScene(
            Player player,
            int sceneId,
            TeleportType teleportType,
            EnterReason enterReason,
            DungeonData dungeonData,
            Position teleportTo) {
        // Get enter types
        val teleportProps =
                TeleportProperties.builder()
                        .sceneId(sceneId)
                        .teleportType(teleportType)
                        .enterReason(enterReason)
                        .teleportTo(teleportTo)
                        .enterType(EnterType.EnterType_ENTER_JUMP);

        val sceneData = GameData.getSceneDataMap().get(sceneId);
        if (dungeonData != null) {
            teleportProps
                    .teleportTo(dungeonData.getStartPosition())
                    .teleportRot(dungeonData.getStartRotation());
            teleportProps.enterType(EnterType.EnterType_ENTER_DUNGEON).enterReason(EnterReason.DungeonEnter);
            teleportProps.dungeonId(dungeonData.getId());
        } else if (player.getSceneId() == sceneId) {
            teleportProps.enterType(EnterType.EnterType_ENTER_GOTO);
        } else if (sceneData != null && sceneData.getSceneType() == SceneType.SCENE_HOME_WORLD) {
            // Home
            teleportProps.enterType(EnterType.EnterType_ENTER_SELF_HOME).enterReason(EnterReason.EnterHome);
        }

        return transferPlayerToScene(player, teleportProps.build());
    }

    public boolean transferPlayerToScene(Player player, TeleportProperties teleportProperties) {
        synchronized (player.getSceneTransferLock()) {
            if (player.getWorld() != this) return false;
            return transferPlayerToSceneLocked(player, teleportProperties);
        }
    }

    /** Rebuild only a solo dungeon, after the normal teleport event and admission checks. */
    public boolean restartDungeonScene(Player player, DungeonData data, Consumer<Scene> prepare) {
        return restartDungeonScene(player, data, prepare, () -> true);
    }

    public boolean restartDungeonScene(
            Player player, DungeonData data, Consumer<Scene> prepare, BooleanSupplier ready) {
        if (player == null || data == null || prepare == null || ready == null) return false;
        synchronized (player.getSceneTransferLock()) {
            var source = player.getScene();
            var manager = source == null ? null : source.getDungeonManager();
            if (!canRestartDungeonScene(player, source, manager, data) || !ready.getAsBoolean())
                return false;
            var properties =
                    TeleportProperties.builder()
                            .sceneId(data.getSceneId())
                            .dungeonId(data.getId())
                            .teleportType(TeleportType.DUNGEON)
                            .enterReason(EnterReason.DungeonEnter)
                            .enterType(EnterType.EnterType_ENTER_DUNGEON)
                            .teleportTo(data.getStartPosition())
                            .teleportRot(data.getStartRotation())
                            .build();
            return transferPlayerToSceneLocked(player, properties, source, manager, prepare, ready);
        }
    }

    /** Build a fresh solo tower scene and prepare its manager before publishing it. */
    public boolean handoffTowerDungeonScene(
            Player player, DungeonData data, Consumer<Scene> prepare) {
        if (player == null || data == null || prepare == null) return false;
        synchronized (player.getSceneTransferLock()) {
            var source = player.getScene();
            var manager = source == null ? null : source.getDungeonManager();
            var transition =
                    new DungeonSceneTransition(
                            source,
                            manager,
                            data,
                            this.scenes.get(data.getSceneId()),
                            data.getSceneId(),
                            data.getId(),
                            false,
                            false,
                            prepare,
                            () -> true);
            if (!canTransitionDungeon(player, transition)) return false;
            var properties =
                    TeleportProperties.builder()
                            .sceneId(data.getSceneId())
                            .dungeonId(data.getId())
                            .teleportType(TeleportType.DUNGEON)
                            .enterReason(EnterReason.DungeonEnter)
                            .enterType(EnterType.EnterType_ENTER_DUNGEON)
                            .teleportTo(data.getStartPosition())
                            .teleportRot(data.getStartRotation())
                            .build();
            return transferPlayerToSceneLocked(player, properties, transition);
        }
    }

    private record DungeonSceneTransition(
            Scene source,
            DungeonManager manager,
            DungeonData data,
            Scene replaced,
            int sceneId,
            int dungeonId,
            boolean restart,
            boolean persistentGroups,
            Consumer<Scene> prepare,
            BooleanSupplier ready) {}

    private boolean canTransitionDungeon(Player player, DungeonSceneTransition transition) {
        var source = transition.source();
        var data = transition.data();
        if (data == null
                || data.getId() != transition.dungeonId()
                || data.getSceneId() != transition.sceneId()) return false;
        if (transition.restart())
            return canRestartDungeonScene(player, source, transition.manager(), data)
                    && this.scenes.get(transition.sceneId()) == transition.replaced()
                    && transition.persistentGroups() == !transition.manager().isTowerDungeon();
        var resource = GameData.getSceneDataMap().get(transition.sceneId());
        var replaced = transition.replaced();
        var manager = transition.manager();
        return !this.isMultiplayer
                && this.host == player
                && this.players.size() == 1
                && this.players.get(0) == player
                && player.getWorld() == this
                && source != null
                && player.getScene() == source
                && source.getWorld() == this
                && this.scenes.get(source.getId()) == source
                && source.getPlayers().size() == 1
                && source.getPlayers().get(0) == player
                && !source.getScriptManager().isDestroyed()
                && source.getDungeonManager() == manager
                && (source.getSceneType() == SceneType.SCENE_WORLD && manager == null
                        || source.getSceneType() == SceneType.SCENE_DUNGEON
                                && manager != null
                                && manager.isTowerDungeon()
                                && manager.getDungeonData() != null
                                && manager.getDungeonData().getSceneId() == source.getId())
                && transition.dungeonId() > 0
                && resource != null
                && resource.getSceneType() == SceneType.SCENE_DUNGEON
                && GameData.getDungeonDataMap().get(transition.dungeonId()) == data
                && this.scenes.get(transition.sceneId()) == replaced
                && (replaced == null
                        || replaced == source
                        || replaced.getWorld() == this
                                && replaced.getId() == transition.sceneId()
                                && replaced.getPlayerCount() == 0);
    }

    private boolean canRestartDungeonScene(
            Player player, Scene source, DungeonManager manager, DungeonData data) {
        if (data == null || manager == null) return false;
        var resource = GameData.getSceneDataMap().get(data.getSceneId());
        return !this.isMultiplayer
                && this.host == player
                && this.players.size() == 1
                && this.players.get(0) == player
                && player.getWorld() == this
                && player.getScene() == source
                && source != null
                && source.getWorld() == this
                && source.getId() == data.getSceneId()
                && data.getId() > 0
                && source.getSceneType() == SceneType.SCENE_DUNGEON
                && resource != null
                && resource.getSceneType() == SceneType.SCENE_DUNGEON
                && GameData.getDungeonDataMap().get(data.getId()) == data
                && this.getScenes().get(source.getId()) == source
                && source.getPlayers().size() == 1
                && source.getPlayers().get(0) == player
                && !source.getScriptManager().isDestroyed()
                && source.getDungeonManager() == manager
                && manager.getDungeonData() == data;
    }

    private boolean transferPlayerToSceneLocked(
            Player player, TeleportProperties teleportProperties) {
        return transferPlayerToSceneLocked(player, teleportProperties, null, null, null);
    }

    private boolean transferPlayerToSceneLocked(
            Player player,
            TeleportProperties teleportProperties,
            Scene restartSource,
            DungeonManager restartManager,
            Consumer<Scene> prepare) {
        return transferPlayerToSceneLocked(
                player, teleportProperties, restartSource, restartManager, prepare, () -> true);
    }

    private boolean transferPlayerToSceneLocked(
            Player player,
            TeleportProperties teleportProperties,
            Scene restartSource,
            DungeonManager restartManager,
            Consumer<Scene> prepare,
            BooleanSupplier ready) {
        var data = restartManager == null ? null : restartManager.getDungeonData();
        var transition =
                restartSource == null
                        ? null
                        : new DungeonSceneTransition(
                                restartSource,
                                restartManager,
                                data,
                                restartSource,
                                data.getSceneId(),
                                data.getId(),
                                true,
                                !restartManager.isTowerDungeon(),
                                prepare,
                                ready);
        return transferPlayerToSceneLocked(player, teleportProperties, transition);
    }

    private boolean transferPlayerToSceneLocked(
            Player player,
            TeleportProperties teleportProperties,
            DungeonSceneTransition transition) {
        // If a queued teleport already exists, cancel it. This prevents the player from
        // becoming stranded in a dungeon due to quitting it by teleporting to a map waypoint.
        synchronized (player) {
            var queuedTeleport = player.getQueuedTeleport();
            if (queuedTeleport != null) {
                player.setQueuedTeleport(null);
                queuedTeleport.cancel(true);
            }
        }

        // Check if the teleport properties are valid.
        if (teleportProperties.getTeleportTo() == null)
            teleportProperties.setTeleportTo(player.getPosition());

        // Call player teleport event.
        PlayerTeleportEvent event =
                new PlayerTeleportEvent(player, teleportProperties, player.getPosition());
        // Call event and check if it was canceled.
        event.call();
        if (event.isCanceled()) {
            return false; // Teleport was canceled.
        }

        if (GameData.getSceneDataMap().get(teleportProperties.getSceneId()) == null) {
            return false;
        }

        Scene oldScene = player.getScene();
        if (transition != null
                && (!canTransitionDungeon(player, transition)
                        || teleportProperties.getSceneId() != transition.sceneId()
                        || teleportProperties.getDungeonId() != transition.dungeonId()
                        || !transition.ready().getAsBoolean())) return false;
        var newScene =
                transition == null
                        ? this.getSceneById(teleportProperties.getSceneId())
                        : new Scene(
                                this,
                                GameData.getSceneDataMap().get(teleportProperties.getSceneId()),
                                transition.persistentGroups());
        if (newScene == null
                || !emu.grasscutter.game.tps.TpsAvatarSystem.canEnter(
                        player, newScene.getSceneData())) {
            if (transition != null && newScene != null) newScene.getScriptManager().onDestroy();
            return false;
        }

        if (transition != null) {
            try {
                transition.prepare().accept(newScene);
                if (!transition.ready().getAsBoolean()) return false;
                if (!transition.restart()
                        && (newScene.getDungeonManager() == null
                                || newScene.getDungeonManager().getDungeonData()
                                        != transition.data()
                                || !newScene.getDungeonManager().isTowerDungeon())) return false;
                synchronized (MEMBERSHIP_LOCK) {
                    if (!canTransitionDungeon(player, transition)
                            || !(transition.replaced() == null
                                    ? this.scenes.putIfAbsent(transition.sceneId(), newScene)
                                            == null
                                    : this.scenes.replace(
                                            transition.sceneId(),
                                            transition.replaced(),
                                            newScene))) {
                        return false;
                    }
                }
            } finally {
                if (this.getScenes().get(newScene.getId()) != newScene)
                    newScene.getScriptManager().onDestroy();
            }
            transition.source().setDontDestroyWhenEmpty(false);
            if (transition.replaced() != null && transition.replaced() != transition.source())
                transition.replaced().getScriptManager().onDestroy();
        }

        // Move directly in the same scene.
        if (newScene == oldScene && teleportProperties.getTeleportType() == TeleportType.COMMAND) {
            // Set player position and rotation
            if (teleportProperties.getTeleportTo() != null) {
                player.getPosition().set(teleportProperties.getTeleportTo());
            }
            if (teleportProperties.getTeleportRot() != null) {
                player.getRotation().set(teleportProperties.getTeleportRot());
            }
            player.sendPacket(new PacketSceneEntityAppearNotify(player));
            return true;
        }

        if (oldScene != null) {
            // Don't deregister scenes if the player is going to tp back into them
            if (oldScene == newScene) {
                oldScene.setDontDestroyWhenEmpty(true);
            }
            oldScene.removePlayer(player);
        }

        if (newScene != null) {
            newScene.addPlayer(player);

            player.getTeamManager().applyAbilities(newScene);

            // Dungeon
            // Dungeon system is handling this already
            // if(dungeonData!=null){
            //     var dungeonManager = new DungeonManager(newScene, dungeonData);
            //     dungeonManager.startDungeon();
            // }

            SceneConfig config = newScene.getScriptManager().getConfig();
            if (teleportProperties.getTeleportTo() == null && config != null) {
                if (config.born_pos != null) {
                    teleportProperties.setTeleportTo(config.born_pos);
                }
                if (config.born_rot != null) {
                    teleportProperties.setTeleportRot(config.born_rot);
                }
            }
        }

        // Set player position and rotation
        if (teleportProperties.getTeleportTo() != null) {
            player.getPosition().set(teleportProperties.getTeleportTo());
        }
        if (teleportProperties.getTeleportRot() != null) {
            player.getRotation().set(teleportProperties.getTeleportRot());
        }

        if (oldScene != null && newScene != null && newScene != oldScene) {
            newScene.setPrevScenePoint(oldScene.getPrevScenePoint());
            oldScene.setDontDestroyWhenEmpty(false);
        }

        // Teleport packet
        player.sendPacket(new PacketPlayerEnterSceneNotify(player, teleportProperties));

        if (teleportProperties.getTeleportType() != TeleportType.INTERNAL
                && teleportProperties.getTeleportType() != SCRIPT) {
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ANY_MANUAL_TRANSPORT);
        }

        return true;
    }

    protected void updatePlayerInfos(Player paramPlayer) {
        for (Player player : this.getPlayers()) {
            if (player == paramPlayer) continue;
            synchronized (player.getSceneTransferLock()) {
                // Dont send packets if player is logging in and filter out joining player
                if (player.getWorld() != this || !player.hasSentLoginPackets()) {
                    continue;
                }

                // Update team of all players since max players has been changed - Probably not the best way
                // to do it
                if (this.isMultiplayer()) {
                    player
                            .getTeamManager()
                            .getMpTeam()
                            .copyFrom(
                                    player.getTeamManager().getMpTeam(), player.getTeamManager().getMaxTeamSize());
                    player.getTeamManager().updateTeamEntities(null);
                }

                // Dont send packets if player is loading into the scene
                if (player.getSceneLoadState().getValue() >= SceneLoadState.INIT.getValue()) {
                    // World player info packets
                    player.getSession().send(new PacketWorldPlayerInfoNotify(this));
                    player.getSession().send(new PacketScenePlayerInfoNotify(this));
                    player.getSession().send(new PacketWorldPlayerRTTNotify(this));

                    // Team packets
                    player.getSession().send(new PacketSyncTeamEntityNotify(player));
                    player.getSession().send(new PacketSyncScenePlayTeamEntityNotify(player));
                }
            }
        }
    }

    public void broadcastPacket(BasePacket packet) {
        // Send to all players - might have to check if player has been sent data packets
        for (Player player : this.getPlayers()) {
            player.getSession().send(packet);
        }
    }

    /**
     * Invoked every game tick.
     *
     * @return True if the world should be removed.
     */
    public boolean onTick() {
        if (this.closing && !this.pendingEvacuations.isEmpty()) {
            retryGuestEvacuations();
            // Keep detached online guests reachable until retry succeeds or they disconnect.
            if (!this.pendingEvacuations.isEmpty()) return false;
        }
        // Check if there are players in this world.
        if (this.getPlayerCount() == 0) return this.pendingDepartures.get() == 0 && this.pendingEvacuations.isEmpty();
        // Tick all associated scenes.
        final List<Scene> snapshot;
        synchronized (this.scenes) { snapshot = new ArrayList<>(this.scenes.values()); }
        for (var scene : snapshot) {
            if (this.scenes.get(scene.getId()) != scene || scene.getPlayerCount() == 0) continue;
            try {
                scene.onTick();
            } catch (Throwable e) {
                // One scene's trouble is not the next one's, nor the world's clock below.
                Grasscutter.getLogger().error("Scene {} threw while ticking.", scene.getId(), e);
            }
        }

        // These are wall-clock cadences, not tick counts: the tick rate is configurable, so
        // counting ticks would sync the clock 5x too often at 200ms and not at all at 5000ms.
        var now = System.currentTimeMillis();

        // sync time every 10 seconds
        if (now - this.lastTimeSync >= 10_000L) {
            this.lastTimeSync = now;
            this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        }

        // store updated world time every 60 seconds. (in-game hour)
        if (now - this.lastTimeStore >= 60_000L && !this.timeLocked) {
            this.lastTimeStore = now;
            this.getHost().updatePlayerGameTime(this.currentWorldTime);
        }

        this.tickCount++;
        return false;
    }

    public void close() {}

    /** Returns the in-game world time in real milliseconds. */
    public long getWorldTime() {
        if (!this.isPaused && !this.timeLocked) {
            var newUpdateTime = System.currentTimeMillis();
            this.currentWorldTime += (newUpdateTime - lastUpdateTime);
            this.lastUpdateTime = newUpdateTime;
        }

        return this.currentWorldTime;
    }

    /** Returns the current in game days world time in in-game minutes (0-1439) */
    public int getGameTime() {
        return (int) (getTotalGameTimeMinutes() % 1440);
    }

    /** Returns the current in game days world time in ingame hours (0-23) */
    public int getGameTimeHours() {
        return this.getGameTime() / 60;
    }

    /** Returns the total number of in game days that got completed since the beginning of the game */
    public long getTotalGameTimeDays() {
        return ConversionUtils.gameTimeToDays(getTotalGameTimeMinutes());
    }

    /**
     * Returns the total number of in game hours that got completed since the beginning of the game
     */
    public long getTotalGameTimeHours() {
        return ConversionUtils.gameTimeToHours(getTotalGameTimeMinutes());
    }

    /** Returns the elapsed in-game minutes since the creation of the world. */
    public long getTotalGameTimeMinutes() {
        return this.getWorldTime() / 1000;
    }

    /**
     * Sets the world's pause status. Updates players and scenes accordingly.
     *
     * @param paused True if the world should be paused.
     */
    public void setPaused(boolean paused) {
        // Check if this world is a multiplayer world.
        if (this.isMultiplayer) return;

        // Update the world time.
        this.getWorldTime();
        this.updateTime();

        // If the world is being un-paused, update the last update time.
        if (this.isPaused != paused && !paused) {
            this.lastUpdateTime = System.currentTimeMillis();
        }

        this.isPaused = paused;
        this.getPlayers().forEach(player -> player.setPaused(paused));
        this.getScenes().forEach((key, scene) -> scene.setPaused(paused));
    }

    /**
     * Changes the game time of the world.
     *
     * @param gameTime The time in game minutes.
     */
    public void changeTime(long gameTime) {
        this.currentWorldTime = gameTime;
    }

    /**
     * Changes the time of the world.
     *
     * @param time The new time in minutes.
     * @param days The number of days to add.
     */
    public void changeTime(int time, int days) {
        // Check if the time is locked.
        if (this.timeLocked) return;

        // Calculate time differences.
        var currentTime = this.getGameTime();
        var diff = time - currentTime;
        if (diff < 0) diff = 1440 + diff;

        // Update the world time.
        this.currentWorldTime += days * 1440 * 1000L + diff * 1000L;

        // Update all players.
        this.host.updatePlayerGameTime(currentWorldTime);
        this.players.forEach(
                player -> player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_GAME_TIME_TICK));
    }

    /** Notifies all players of the current world time. */
    public void updateTime() {
        this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        this.getPlayers().forEach(p -> p.sendPacket(new PacketSceneTimeNotify(p)));
    }

    /**
     * Locks the world time.
     *
     * @param locked True if the world time should be locked.
     */
    public void lockTime(boolean locked) {
        this.timeLocked = locked;

        // Notify players of the locking.
        this.updateTime();
        this.getPlayers()
                .forEach(player -> player.setProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED, locked));
    }

    @NotNull @Override
    public Iterator<Player> iterator() {
        return this.getPlayers().iterator();
    }
}
