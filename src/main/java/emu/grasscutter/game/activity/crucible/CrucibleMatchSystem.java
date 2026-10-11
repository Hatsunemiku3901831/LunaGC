package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.activity.crucible.CrucibleMatchmaking.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.MatchTypeOuterClass.MatchType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.function.*;

/** Independent play queues share admission reservations. Called outside scene/world monitors. */
public final class CrucibleMatchSystem {
    private static final int TYPE = MatchType.MatchType_MP_PLAY.getNumber();
    private final GameServer server;
    private final LongSupplier clock;
    private final List<Pool> pools = List.of(new Pool(1), new Pool(2));

    private static final class Pool {
        final int playId;
        final CrucibleMatchmaking queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
        final Map<Integer, Team> teams = new HashMap<>();
        final Map<Long, World> destinations = new HashMap<>();

        Pool(int playId) {
            this.playId = playId;
        }
    }

    private record Team(
            Scene source,
            int scheduleId,
            long serial,
            List<Player> players,
            Map<Integer, Integer> worldLevels,
            BooleanSupplier current,
            Runnable release) {
        Team {
            players = List.copyOf(players);
            worldLevels = Map.copyOf(worldLevels);
        }
    }

    public CrucibleMatchSystem(GameServer server) {
        this(server, () -> System.currentTimeMillis() / 1000);
    }

    public CrucibleMatchSystem(GameServer server, LongSupplier clock) {
        this.server = Objects.requireNonNull(server);
        this.clock = Objects.requireNonNull(clock);
    }

    // Scene/world callers must not acquire this manager's monitor: ticks call back into scenes.
    public boolean isReserved(Player player) {
        return poolFor(player.getUid()) != null;
    }

    private Pool poolFor(int uid) {
        return pools.stream().filter(p -> p.queue.runFor(uid) != null).findFirst().orElse(null);
    }

    private Pool play(int playId) {
        return pools.stream().filter(p -> p.playId == playId).findFirst().orElse(null);
    }

    private Team invitation(Player owner, Pool pool, boolean permitSoloStart) {
        Scene scene = owner.getScene();
        if (scene == null) return null;
        if (pool.playId == 1) {
            var controller = scene.getCrucibleSceneController();
            var snapshot = controller.matchingTeam(owner, 1, permitSoloStart);
            if (snapshot == null) return null;
            return new Team(
                    scene,
                    snapshot.context().scheduleId(),
                    snapshot.serial(),
                    snapshot.players(),
                    snapshot.context().members(),
                    () -> controller.isMatching(snapshot),
                    () -> controller.releaseMatching(snapshot.serial()));
        }
        var controller = scene.getAsterBigSceneController();
        var snapshot = controller.matchingTeam(owner, 2, permitSoloStart);
        if (snapshot == null) return null;
        return new Team(
                scene,
                snapshot.scheduleId(),
                snapshot.serial(),
                snapshot.players(),
                snapshot.worldLevels(),
                () -> controller.isMatching(snapshot),
                () -> controller.releaseMatching(snapshot.serial()));
    }

    private boolean enqueue(Pool pool, Player owner, Team team, long now) {
        if (!current(team) || team.players().stream().anyMatch(this::isReserved)) return false;
        if (!pool.queue.enqueue(
                new Party(owner.getUid(), team.scheduleId(), team.worldLevels()), now))
            return false;
        pool.teams.put(owner.getUid(), team);
        return true;
    }

    public synchronized void start(Player owner, int type, int playId, int matchId, int dungeonId) {
        int retcode = Retcode.RET_MP_MATCH_PLAY_NOT_OPEN.getNumber();
        Team team = null;
        Pool pool = play(playId);
        if (type == TYPE && pool != null && matchId == 0 && dungeonId == 0) {
            if (isReserved(owner)) retcode = Retcode.RET_MATCH_ALREADY_IN_MATCH.getNumber();
            else if (owner.getWorld() == null || owner.getWorld().getHost() != owner)
                retcode = Retcode.RET_MP_NOT_IN_MY_WORLD.getNumber();
            else if (owner.getScene() != null) {
                team = invitation(owner, pool, true);
                retcode =
                        team != null && enqueue(pool, owner, team, clock.getAsLong())
                                ? 0
                                : Retcode.RET_MP_GUEST_MATCH_COND_NOT_MEET.getNumber();
            }
        }
        owner.sendPacket(PacketCrucibleMatch.start(type, playId, matchId, dungeonId, retcode));
        if (retcode == 0) send(team.players(), PacketCrucibleMatch.info(playId, owner.getUid()));
        else if (team != null && !pool.teams.containsKey(owner.getUid())) team.release().run();
    }

    public synchronized void cancelRequest(Player player, int type) {
        Pool pool = type == TYPE ? poolFor(player.getUid()) : null;
        Run run = pool == null ? null : pool.queue.cancel(player.getUid());
        player.sendPacket(
                PacketCrucibleMatch.cancel(
                        type, run == null ? Retcode.RET_MATCH_NOT_IN_MATCH.getNumber() : 0));
        if (run != null) stop(pool, run, 2);
    }

    public synchronized void cancel(Player player) {
        Pool pool = poolFor(player.getUid());
        if (pool != null) {
            Run run = pool.queue.cancel(player.getUid());
            if (run != null) stop(pool, run, 7);
        }
    }

    public synchronized void confirm(Player player, int type, boolean agree, boolean guest) {
        Pool pool = poolFor(player.getUid());
        Run run = pool == null ? null : pool.queue.runFor(player.getUid());
        boolean correctRole =
                player.getWorld() != null && (player.getWorld().getHost() != player) == guest;
        Reply reply =
                type == TYPE && correctRole && run != null && validSources(pool, run)
                        ? pool.queue.confirm(player.getUid(), agree, clock.getAsLong())
                        : Reply.INVALID;
        int retcode =
                reply == Reply.INVALID
                        ? Retcode.RET_MATCH_NOT_IN_MATCH.getNumber()
                        : reply == Reply.TIMED_OUT ? Retcode.RET_MP_REPLY_TIMEOUT.getNumber() : 0;
        player.sendPacket(PacketCrucibleMatch.confirm(type, agree, retcode, guest));
        if (run == null) return;
        if (reply == Reply.DECLINED || reply == Reply.TIMED_OUT) {
            pool.queue.cancel(player.getUid());
            stop(pool, run, reply == Reply.TIMED_OUT ? 9 : 2);
        } else if (reply == Reply.ALL_AGREED)
            send(members(pool, run), PacketCrucibleMatch.agreed(run.hostUid()));
    }

    /**
     * The client sends this after the successful agreed-result notification, including the host.
     */
    public synchronized void allowEnter(Player player, int targetUid) {
        Pool pool = poolFor(player.getUid());
        Run run = pool == null ? null : pool.queue.runFor(player.getUid());
        if (run != null && validSources(pool, run))
            pool.queue.allowEnter(player.getUid(), targetUid, clock.getAsLong());
    }

    public synchronized void onTick() {
        long now = clock.getAsLong();
        for (Pool pool : pools) {
            for (Run expired : pool.queue.expire(now))
                stop(
                        pool,
                        expired,
                        expired.stage() == Stage.CONFIRMING
                                ? 9
                                : expired.stage() == Stage.QUEUED ? 3 : 7);
            for (Run run : pool.queue.runs()) {
                boolean loading = run.stage() == Stage.LOADING;
                boolean valid =
                        active(pool, run.scheduleId())
                                && (loading
                                        ? validDestination(pool, run)
                                        : validSources(pool, run));
                if (!valid) {
                    pool.queue.cancel(run.members().keySet().iterator().next());
                    stop(pool, run, 7);
                    continue;
                }
                if (run.stage() == Stage.JOINING && pool.queue.beginTransfer(run.id(), now)) {
                    try {
                        transfer(pool, run);
                        pool.queue.transferred(run.id(), now);
                    } catch (RuntimeException exception) {
                        pool.queue.cancel(run.members().keySet().iterator().next());
                        stop(pool, run, 6);
                        Grasscutter.getLogger()
                                .error(
                                        "MP play {} match {} could not transfer its team",
                                        pool.playId,
                                        run.id(),
                                        exception);
                    }
                } else if (loading) {
                    Player host = server.getPlayerByUid(run.hostUid());
                    var players = members(pool, run);
                    if (players.stream()
                                    .allMatch(p -> p.getSceneLoadState() == SceneLoadState.LOADED)
                            && prepare(pool, host, run.scheduleId(), players)) {
                        pool.queue.complete(run.id());
                        release(pool, run);
                        send(players, PacketCrucibleMatch.stop(run.hostUid(), 1));
                    }
                }
            }
        }
        // Observe already-consented parties without starting invitations under scene monitors.
        for (World world : List.copyOf(server.getWorlds())) {
            Player host = world.getHost();
            if (host == null
                    || host.getWorld() != world
                    || host.getScene() == null
                    || isReserved(host)) continue;
            for (Pool pool : pools) {
                var team = invitation(host, pool, false);
                if (team != null) {
                    if (enqueue(pool, host, team, now)) {
                        send(team.players(), PacketCrucibleMatch.info(pool.playId, host.getUid()));
                        break;
                    }
                    team.release().run();
                }
            }
        }
        for (Pool pool : pools)
            for (Run match : pool.queue.findMatches(now))
                send(
                        members(pool, match),
                        PacketCrucibleMatch.success(
                                pool.playId, match.hostUid(), Math.toIntExact(match.deadline())));
    }

    private boolean active(Pool pool, int scheduleId) {
        var config = ActivityManager.getScheduleActivityConfigMap().get(scheduleId);
        long now = System.currentTimeMillis();
        if (pool.playId == 1)
            return CrucibleSceneLifecycle.activeSchedule(config, now) == scheduleId;
        return config != null
                && config.getActivityId() == AsterSchedule.ACTIVITY_ID
                && config.isOpenAt(now)
                && now >= AsterSchedule.beginTime(config, 8)
                && now < AsterSchedule.contentCloseTime(config)
                && AsterBigSceneController.resourcesAvailable();
    }

    private boolean current(Team team) {
        return team.players().stream()
                        .allMatch(p -> p.isOnline() && server.getPlayerByUid(p.getUid()) == p)
                && team.current().getAsBoolean();
    }

    private boolean validSources(Pool pool, Run run) {
        return run.parties().stream()
                .allMatch(
                        p -> {
                            var team = pool.teams.get(p.ownerUid());
                            return team != null
                                    && team.scheduleId() == run.scheduleId()
                                    && team.worldLevels().equals(p.members())
                                    && current(team);
                        });
    }

    private List<Player> members(Pool pool, Run run) {
        return run.parties().stream()
                .map(p -> pool.teams.get(p.ownerUid()))
                .filter(Objects::nonNull)
                .flatMap(t -> t.players().stream())
                .toList();
    }

    private boolean validDestination(Pool pool, Run run) {
        World world = pool.destinations.get(run.id());
        var players = members(pool, run);
        var config = ActivityManager.getScheduleActivityConfigMap().get(run.scheduleId());
        return world != null
                && world.getHost().getUid() == run.hostUid()
                && world.getWorldLevel() == run.members().get(run.hostUid())
                && players.size() == run.members().size()
                && new HashSet<>(world.getPlayers()).equals(new HashSet<>(players))
                && players.stream()
                        .allMatch(
                                p ->
                                        p.isOnline()
                                                && server.getPlayerByUid(p.getUid()) == p
                                                && p.getWorldLevel()
                                                        == run.members().get(p.getUid())
                                                && p.getWorld() == world
                                                && p.getScene() != null
                                                && p.getScene().getId() == 3
                                                && (pool.playId != 2
                                                        || AsterSchedule.phaseOpen(
                                                                p.getActivityManager()
                                                                        .getPlayerActivityDataMap()
                                                                        .get(
                                                                                AsterSchedule
                                                                                        .ACTIVITY_ID),
                                                                config,
                                                                p.getLevel(),
                                                                System.currentTimeMillis(),
                                                                3)));
    }

    private boolean prepare(Pool pool, Player host, int scheduleId, List<Player> players) {
        return pool.playId == 1
                ? host.getScene()
                        .getCrucibleSceneController()
                        .startMatchedPreparation(host, scheduleId, players)
                : host.getScene()
                        .getAsterBigSceneController()
                        .startMatchedPreparation(host, scheduleId, players);
    }

    private void transfer(Pool pool, Run run) {
        if (!validSources(pool, run))
            throw new IllegalStateException("Matching party changed before transfer");
        Player host = server.getPlayerByUid(run.hostUid());
        var players = members(pool, run);
        // Invalidate source invitations before moving anyone out of those scenes.
        run.parties().forEach(p -> pool.teams.get(p.ownerUid()).release().run());
        World world = host.getWorld();
        if (!world.isMultiplayer()) {
            var promoted = new World(host, true);
            if (!promoted.tryAddPlayer(
                    host,
                    world,
                    context -> {
                        host.sendPacket(
                                new PacketPlayerEnterSceneNotify(
                                        host,
                                        promoted,
                                        entry(host, EnterReason.HostFromSingleToMp)));
                        host.sendPacket(new PacketEnterScenePeerNotify(host));
                    }))
                throw new IllegalStateException("Cannot promote matching host " + host.getUid());
            world = promoted;
        }
        pool.destinations.put(run.id(), world);
        for (Party party : run.parties()) {
            // Removing an old host first would kick their remaining guests.
            var ordered =
                    players.stream()
                            .filter(p -> party.members().containsKey(p.getUid()))
                            .sorted(Comparator.comparing(p -> p.getUid() == party.ownerUid()))
                            .toList();
            for (Player player : ordered) {
                if (player.getWorld() == world) continue;
                var destination = world;
                var properties = entry(host, EnterReason.MpPlay);
                if (!destination.tryAddPlayer(
                        player,
                        player.getWorld(),
                        properties,
                        context -> {
                            player.sendPacket(
                                    new PacketPlayerEnterSceneNotify(
                                            player, destination, properties));
                            player.sendPacket(new PacketEnterScenePeerNotify(player));
                        }))
                    throw new IllegalStateException(
                            "Cannot enter matching world for uid " + player.getUid());
            }
        }
    }

    private static TeleportProperties entry(Player host, EnterReason reason) {
        return TeleportProperties.builder()
                .sceneId(3)
                .teleportTo(host.getPosition().clone())
                .teleportRot(host.getRotation().clone())
                .enterType(EnterType.EnterType_ENTER_OTHER)
                .enterReason(reason)
                .build();
    }

    private void stop(Pool pool, Run run, int reason) {
        send(members(pool, run), PacketCrucibleMatch.stop(run.hostUid(), reason));
        release(pool, run);
    }

    private void release(Pool pool, Run run) {
        pool.destinations.remove(run.id());
        for (Party party : run.parties()) {
            var team = pool.teams.remove(party.ownerUid());
            if (team != null) team.release().run();
        }
    }

    private void send(List<Player> players, BasePacket packet) {
        players.stream().filter(Player::isOnline).forEach(p -> p.sendPacket(packet));
    }
}
