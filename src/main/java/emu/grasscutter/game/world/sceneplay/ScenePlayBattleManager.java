package emu.grasscutter.game.world.sceneplay;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.ScenePlayBattleInfoOuterClass.ScenePlayBattleInfo;
import emu.grasscutter.net.proto.ScenePlayBattleResultNotifyOuterClass.ScenePlayBattleResultNotify;
import emu.grasscutter.net.proto.ScenePlayBattleSettlePlayerInfoOuterClass.ScenePlayBattleSettlePlayerInfo;
import emu.grasscutter.net.proto.ScenePlayBattleUidOpNotifyOuterClass.ScenePlayBattleUidOpNotify;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Scene-local MP battle lifecycle; the activity controller supplies eligible participants. */
public final class ScenePlayBattleManager {
    public static final int NONE = 0,
            INIT = 1,
            PREPARE = 2,
            READY = 3,
            PRESTART = 4,
            START = 5,
            STOP = 6;
    public static final int DIRECT = 1, MATCH = 2;
    public static final int LOSE = 0, WIN = 1, INTERRUPT = 2;

    private static final class Round {
        final MpPlayGroupData entry;
        final int playType, type, minimumPlayers;
        final Player owner;
        final Map<Integer, Player> members;
        final Map<Integer, SceneGroup> groups;
        final Map<Integer, EntityPlayTeam> playTeams = new LinkedHashMap<>();
        final Map<Integer, Map<String, Long>> uidValues = new HashMap<>();
        final Set<Future<?>> callbacks = new HashSet<>();
        long lastTime, prepareEnd, startTime, progress;
        int state = INIT;
        int playTeamGadgetId;
        boolean valid = true;
        int luaCalls;
        ScenePlayBattleParameters parameters;

        Round(
                MpPlayGroupData entry,
                int playType,
                int type,
                int minimumPlayers,
                Player owner,
                Map<Integer, Player> members,
                Map<Integer, SceneGroup> groups,
                long now) {
            this.entry = entry;
            this.playType = playType;
            this.type = type;
            this.minimumPlayers = minimumPlayers;
            this.owner = owner;
            this.members = members;
            this.groups = groups;
            this.lastTime = now;
        }
    }

    private final Scene scene;
    private final LongSupplier clock;
    // All state, packet ordering and callback tickets follow the scene monitor.
    private final Map<Integer, Round> rounds = new LinkedHashMap<>();
    private final Map<Integer, Round> byGroup = new HashMap<>();
    // Ability callbacks look up registered carriers without acquiring the scene monitor.
    private final Map<Integer, EntityPlayTeam> playTeamEntities = new ConcurrentHashMap<>();
    private boolean closed;

    public ScenePlayBattleManager(Scene scene) {
        this(scene, () -> System.currentTimeMillis() / 1000);
    }

    public ScenePlayBattleManager(Scene scene, LongSupplier clock) {
        this.scene = Objects.requireNonNull(scene);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Does not perform activity unlock checks, matchmaking, or create script groups. */
    public boolean prepare(
            MpPlayGroupData entry,
            MpPlayMatchData match,
            int playType,
            int type,
            Collection<Player> participants) {
        synchronized (scene) {
            var owner = scene.getHost();
            long now = clock.getAsLong();
            if (closed
                    || entry == null
                    || match == null
                    || entry.getId() <= 0
                    || match.getId() != entry.getId()
                    || playType <= 0
                    || type < DIRECT
                    || type > MATCH
                    || match.getMinPlayers() <= 0
                    || match.getMaxPlayers() < match.getMinPlayers()
                    || match.getMaxPlayers() > 4
                    || participants == null
                    || !present(owner)
                    || scene.isPaused()
                    || now < 0
                    || now > ScenePlayBattleParameters.MAX_UINT32
                    || entry.getPrepareTime() < 0
                    || now + entry.getPrepareTime() > ScenePlayBattleParameters.MAX_UINT32)
                return false;
            var previous = rounds.get(entry.getId());
            if (previous != null && (active(previous) || !callbacksDone(previous))) return false;
            int minimum = type == DIRECT ? 1 : match.getMinPlayers();
            var members = new LinkedHashMap<Integer, Player>();
            for (var player : participants)
                if (!present(player)
                        || player.getUid() <= 0
                        || members.putIfAbsent(player.getUid(), player) != null) return false;
            if (members.get(owner.getUid()) != owner
                    || members.size() < minimum
                    || members.size() > match.getMaxPlayers()) return false;
            var ids = new LinkedHashSet<Integer>();
            if (entry.getGroupList() != null) ids.addAll(entry.getGroupList());
            if (entry.getServerLuaCallGroupList() != null)
                ids.addAll(entry.getServerLuaCallGroupList());
            if (ids.isEmpty()) return false;
            var groups = new LinkedHashMap<Integer, SceneGroup>();
            for (var id : ids) {
                if (id == null || id <= 0) return false;
                var group = scene.getScriptManager().getLoadedGroupById(id);
                var other = byGroup.get(id);
                if (group == null
                        || other != null
                                && other != previous
                                && (active(other) || !callbacksDone(other))) return false;
                groups.put(id, group);
            }
            if (previous != null)
                previous.groups.keySet().forEach(id -> byGroup.remove(id, previous));
            var round = new Round(entry, playType, type, minimum, owner, members, groups, now);
            rounds.put(entry.getId(), round);
            groups.keySet().forEach(id -> byGroup.put(id, round));
            round.state = PREPARE;
            round.prepareEnd = now + entry.getPrepareTime();
            emitState(round);
            broadcastInfo(round);
            return true;
        }
    }

    public boolean prestart(int groupId, ScenePlayBattleParameters parameters) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null || parameters == null) return false;
            advance(round);
            long now = time(round);
            if (round.state != READY
                    || now + parameters.startCd() + parameters.duration()
                            > ScenePlayBattleParameters.MAX_UINT32) return false;
            List<EntityPlayTeam> created = new ArrayList<>();
            if (round.playTeamGadgetId != 0) {
                var abilities = EntityPlayTeam.configuredAbilities(round.playTeamGadgetId);
                if (abilities == null
                        || round.members.values().stream()
                                .anyMatch(
                                        player ->
                                                player.getPeerId() <= 0
                                                        || player.getTeamManager().getEntity()
                                                                == null)) return false;
                for (var player : round.members.values())
                    created.add(
                            new EntityPlayTeam(scene, player, round.playTeamGadgetId, abilities));
            }
            round.parameters = parameters;
            round.progress = 0;
            round.startTime = now + parameters.startCd();
            round.state = PRESTART;
            for (var entity : created) {
                round.playTeams.put(entity.getPlayerUid(), entity);
                playTeamEntities.put(entity.getId(), entity);
            }
            if (!created.isEmpty())
                scene.broadcastPacket(
                        new PacketSyncScenePlayTeamEntityNotify(scene.getId(), created));
            emitState(round);
            broadcastInfo(round);
            try {
                created.forEach(EntityPlayTeam::initAbilities);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger().error("Scene-play carrier initialization failed", failure);
                interrupt(round, true);
                return false;
            }
            advance(round);
            return true;
        }
    }

    public boolean setPlayTeamEntityGadgetId(int groupId, int gadgetId) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null) return false;
            advance(round);
            if (round.state != PREPARE && round.state != READY
                    || gadgetId <= 0
                    || EntityPlayTeam.configuredAbilities(gadgetId) == null) return false;
            round.playTeamGadgetId = gadgetId;
            return true;
        }
    }

    public EntityPlayTeam getPlayTeamEntity(int id) {
        var entity = playTeamEntities.get(id);
        if (entity == null
                || entity.getId() != id
                || entity.getPlayer().getUid() != entity.getPlayerUid()
                || entity.getPlayer().getPeerId() != entity.getAuthorityPeerId()
                || !present(entity.getPlayer())
                || !entity.isAbilityLifecycleCurrent(entity.getAbilityLifecycleVersion()))
            return null;
        return entity;
    }

    public List<EntityPlayTeam> getPlayTeamEntities() {
        synchronized (scene) {
            return playTeamEntities.values().stream()
                    .filter(entity -> getPlayTeamEntity(entity.getId()) == entity)
                    .sorted(Comparator.comparingInt(GameEntity::getId))
                    .toList();
        }
    }

    public boolean addProgress(int groupId, long delta) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null || delta < 0 || delta > ScenePlayBattleParameters.MAX_UINT32)
                return false;
            advance(round);
            if (round.state != START) return false;
            if (delta == 0) return true;
            int before = stage(round);
            round.progress = Math.min(ScenePlayBattleParameters.MAX_UINT32, round.progress + delta);
            int after = stage(round);
            if (before != after)
                emit(
                        round,
                        EventType.EVENT_SCENE_MP_PLAY_BATTLE_STAGE_CHANGE,
                        before,
                        after,
                        round.parameters.stages().size() - 1);
            broadcastInfo(round);
            if (round.progress
                    >= round.parameters.stages().get(round.parameters.stages().size() - 1))
                finish(round, WIN, true);
            return true;
        }
    }

    public long getUidValue(int groupId, int uid, String key) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null || key == null) return 0;
            return round.uidValues.getOrDefault(uid, Map.of()).getOrDefault(key, 0L);
        }
    }

    public boolean setUidValue(int groupId, int uid, String key, long value) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null
                    || round.state == NONE
                    || key == null
                    || key.isBlank()
                    || !present(round.members.get(uid))) return false;
            round.uidValues.computeIfAbsent(uid, ignored -> new HashMap<>()).put(key, value);
            return true;
        }
    }

    public int getHostUid(int groupId) {
        synchronized (scene) {
            var round = find(groupId);
            return round == null ? 0 : round.owner.getUid();
        }
    }

    public int getType(int groupId) {
        synchronized (scene) {
            var round = find(groupId);
            return round == null ? 0 : round.type;
        }
    }

    public ScenePlayBattleInfo getInfo(int groupId) {
        synchronized (scene) {
            var round = find(groupId);
            return round == null ? ScenePlayBattleInfo.getDefaultInstance() : info(round);
        }
    }

    /** An asynchronous scene-play ability keeps the rounds present when it was submitted. */
    public BooleanSupplier captureAbilityScope(Player player) {
        synchronized (scene) {
            int uid = player == null ? 0 : player.getUid();
            var captured = abilityRounds(player);
            return () -> {
                synchronized (scene) {
                    return !closed
                            && present(player)
                            && player.getUid() == uid
                            && captured.equals(abilityRounds(player));
                }
            };
        }
    }

    private List<Round> abilityRounds(Player player) {
        return rounds.values().stream()
                .filter(
                        round ->
                                active(round)
                                        && round.valid
                                        && groupsCurrent(round)
                                        && present(round.owner)
                                        && player != null
                                        && round.members.get(player.getUid()) == player)
                .toList();
    }

    /** CurScenePlay selects the resource's server Lua groups, independently of the entity group. */
    public boolean callServerLua(
            GameEntity source,
            GameEntity owner,
            Player player,
            Function<SceneGroup, Boolean> call) {
        synchronized (scene) {
            if (closed
                    || call == null
                    || !present(player)
                    || !currentAbilityEntity(source)
                    || !currentAbilityEntity(owner)) return false;
            for (var round : List.copyOf(rounds.values())) advance(round);
            var selected =
                    abilityRounds(player).stream()
                            .filter(
                                    round ->
                                            belongsToRound(source, round)
                                                    && belongsToRound(owner, round))
                            .toList();
            if (selected.isEmpty()) return false;
            record Route(Round round, SceneGroup group) {}
            var routes = new TreeMap<Integer, Route>();
            for (var round : selected) {
                var ids = round.entry.getServerLuaCallGroupList();
                if (ids != null)
                    for (int id : ids) {
                        var group = round.groups.get(id);
                        if (group != null) routes.putIfAbsent(id, new Route(round, group));
                    }
            }
            if (routes.isEmpty()) return false;
            long sourceVersion = source.getAbilityLifecycleVersion();
            long ownerVersion = owner.getAbilityLifecycleVersion();
            int sourceId = source.getId(), ownerId = owner.getId(), uid = player.getUid();
            selected.forEach(round -> round.luaCalls++);
            try {
                boolean successful = true;
                for (var route : routes.values()) {
                    var round = route.round();
                    if (closed
                            || player.getUid() != uid
                            || !present(player)
                            || !active(round)
                            || !round.valid
                            || !groupsCurrent(round)
                            || rounds.get(round.entry.getId()) != round
                            || round.members.get(uid) != player
                            || !present(round.owner)
                            || source.getId() != sourceId
                            || owner.getId() != ownerId
                            || !source.isAbilityLifecycleCurrent(sourceVersion)
                            || !owner.isAbilityLifecycleCurrent(ownerVersion)
                            || !currentAbilityEntity(source)
                            || !currentAbilityEntity(owner)) return false;
                    successful &= Boolean.TRUE.equals(call.apply(route.group()));
                }
                return successful;
            } finally {
                selected.forEach(round -> round.luaCalls--);
            }
        }
    }

    private boolean currentAbilityEntity(GameEntity entity) {
        if (entity == null
                || entity.getScene() != scene
                || !entity.isAbilityLifecycleCurrent(entity.getAbilityLifecycleVersion()))
            return false;
        if (entity instanceof EntityTeam team)
            return present(team.getPlayer())
                    && team.getPlayer().getTeamManager().getEntity() == team;
        return scene.getEntityById(entity.getId()) == entity;
    }

    private static boolean belongsToRound(GameEntity entity, Round round) {
        return !(entity instanceof EntityPlayTeam playTeam)
                || round.playTeams.get(playTeam.getPlayerUid()) == playTeam;
    }

    public boolean uidOp(
            int groupId,
            int configId,
            int[] uids,
            int operation,
            String name,
            int[] parameters,
            int[] targets,
            int index,
            int duration) {
        synchronized (scene) {
            var round = find(groupId);
            if (round == null
                    || !active(round)
                    || name == null
                    || name.isBlank()
                    || uids == null
                    || uids.length == 0
                    || parameters == null
                    || targets == null) return false;
            advance(round);
            if (!active(round)) return false;
            var entity = scene.getEntityByConfigId(configId, groupId);
            if (!(entity instanceof EntityGadget gadget)
                    || gadget.getScene() != scene
                    || scene.getEntities().get(gadget.getId()) != gadget) return false;
            var selected = new LinkedHashSet<Integer>();
            for (int uid : uids) {
                if (!present(round.members.get(uid))) return false;
                selected.add(uid);
            }
            var packet =
                    ScenePlayBattleUidOpNotify.newBuilder()
                            .setPlayType(round.playType)
                            .setPlayId(round.entry.getId())
                            .setEntityId(gadget.getId())
                            .addAllUidList(selected)
                            .setOp(operation)
                            .setParamStr(name)
                            .setParamIndex(index)
                            .setParamDuration(duration);
            for (int value : parameters) packet.addParamList(value);
            for (int value : targets) packet.addParamTargetList(value);
            scene.broadcastPacket(PacketScenePlayBattle.uidOp(packet.build()));
            return true;
        }
    }

    public void tick() {
        synchronized (scene) {
            if (closed) return;
            for (var round : rounds.values()) advance(round);
            for (var entity : getPlayTeamEntities()) entity.onAbilityTick();
        }
    }

    private void advance(Round round) {
        if (!active(round)) return;
        if (!currentMember(round, round.owner)
                || round.members.values().stream()
                                .filter(player -> currentMember(round, player))
                                .count()
                        < round.minimumPlayers
                || !groupsCurrent(round)) {
            interrupt(round, groupsCurrent(round));
            return;
        }
        var absent =
                round.members.entrySet().stream()
                        .filter(entry -> !currentMember(round, entry.getValue()))
                        .toList();
        for (var entry : absent) {
            removePlayTeamEntities(round, List.of(entry.getKey()));
            round.members.remove(entry.getKey());
        }
        long now = time(round);
        if (round.state == PREPARE && now >= round.prepareEnd) {
            round.state = READY;
            emitState(round);
            broadcastInfo(round);
        }
        if (round.state == PRESTART && now >= round.startTime) {
            round.state = START;
            emitState(round);
            broadcastInfo(round);
        }
        if (round.state == START && now >= round.startTime + round.parameters.duration())
            finish(round, LOSE, true);
    }

    public void onPlayerLeaving(Player player) {
        synchronized (scene) {
            for (var round : rounds.values()) {
                if (!active(round) || round.members.get(player.getUid()) != player) continue;
                long remaining =
                        round.members.values().stream()
                                .filter(member -> member != player && currentMember(round, member))
                                .count();
                if (round.owner == player || remaining < round.minimumPlayers)
                    interrupt(round, true);
                else removePlayTeamEntities(round, List.of(player.getUid()));
                round.members.remove(player.getUid(), player);
            }
        }
    }

    public void onGroupUnloaded(SceneGroup group) {
        synchronized (scene) {
            var round = byGroup.get(group.id);
            if (round != null && round.groups.get(group.id) == group) {
                round.valid = false;
                interrupt(round, false);
            }
        }
    }

    public void sendTo(Player player) {
        synchronized (scene) {
            if (closed || !present(player)) return;
            for (var round : rounds.values()) advance(round);
            player.sendPacket(
                    PacketScenePlayBattle.list(
                            rounds.values().stream()
                                    .filter(round -> active(round) && round.valid)
                                    .map(ScenePlayBattleManager::info)
                                    .toList()));
        }
    }

    public void close() {
        synchronized (scene) {
            closed = true;
            rounds.values().forEach(round -> round.valid = false);
            rounds.values()
                    .forEach(
                            round ->
                                    removePlayTeamEntities(
                                            round, List.copyOf(round.playTeams.keySet())));
            rounds.clear();
            byGroup.clear();
        }
    }

    private void interrupt(Round round, boolean callback) {
        if (!active(round)) return;
        int interrupted = round.state;
        if (callback)
            emit(
                    round,
                    EventType.EVENT_SCENE_MP_PLAY_BATTLE_INTERRUPT,
                    round.playType,
                    round.entry.getId(),
                    interrupted);
        scene.broadcastPacket(
                PacketScenePlayBattle.interrupt(round.playType, round.entry.getId(), interrupted));
        if (interrupted >= READY) finish(round, INTERRUPT, callback);
        else {
            round.state = NONE;
            broadcastInfo(round);
            removePlayTeamEntities(round, List.copyOf(round.playTeams.keySet()));
        }
    }

    private void finish(Round round, int reason, boolean callback) {
        if (!active(round)) return;
        long cost = round.startTime == 0 ? 0 : Math.max(0, time(round) - round.startTime);
        round.state = STOP;
        // The original scripts consume result reason in param3, not a STATE/STOP event.
        if (callback)
            emit(
                    round,
                    EventType.EVENT_SCENE_MP_PLAY_BATTLE_RESULT,
                    round.playType,
                    round.entry.getId(),
                    reason);
        var result =
                ScenePlayBattleResultNotify.newBuilder()
                        .setPlayId(round.entry.getId())
                        .setPlayType(round.playType)
                        .setIsWin(reason == WIN)
                        .setCostTime((int) cost);
        for (var member : round.members.values())
            result.addSettlePlayerInfoList(
                    ScenePlayBattleSettlePlayerInfo.newBuilder()
                            .setUid(member.getUid())
                            .setNickname(Objects.toString(member.getNickname(), ""))
                            .setHeadImage(member.getHeadImage())
                            .setProfilePicture(member.getProfilePicture()));
        // Activity-specific titles, score and resin rewards belong to its controller.
        scene.broadcastPacket(PacketScenePlayBattle.result(result.build()));
        broadcastInfo(round);
        removePlayTeamEntities(round, List.copyOf(round.playTeams.keySet()));
    }

    private void removePlayTeamEntities(Round round, Collection<Integer> uids) {
        var removed = new ArrayList<Integer>();
        for (int uid : uids) {
            var entity = round.playTeams.remove(uid);
            if (entity == null) continue;
            playTeamEntities.remove(entity.getId(), entity);
            removed.add(entity.getId());
            try {
                entity.dispose();
            } catch (RuntimeException failure) {
                Grasscutter.getLogger().error("Scene-play carrier cleanup failed", failure);
            }
        }
        if (!removed.isEmpty())
            scene.broadcastPacket(new PacketDelScenePlayTeamEntityNotify(scene.getId(), removed));
    }

    private boolean currentMember(Round round, Player player) {
        if (!present(player) || round.members.get(player.getUid()) != player) return false;
        var entity = round.playTeams.get(player.getUid());
        return entity == null || getPlayTeamEntity(entity.getId()) == entity;
    }

    private Round find(int groupId) {
        var round = byGroup.get(groupId);
        return closed || round == null || !round.valid || !groupsCurrent(round) ? null : round;
    }

    private boolean present(Player player) {
        return player != null
                && player.getScene() == scene
                && player.getWorld() == scene.getWorld()
                && scene.getPlayers().stream().anyMatch(member -> member == player);
    }

    private boolean groupsCurrent(Round round) {
        return round.groups.entrySet().stream()
                .allMatch(
                        entry ->
                                scene.getScriptManager().getLoadedGroupById(entry.getKey())
                                        == entry.getValue());
    }

    private long time(Round round) {
        return round.lastTime =
                Math.max(
                        round.lastTime,
                        Math.min(
                                ScenePlayBattleParameters.MAX_UINT32,
                                Math.max(0, clock.getAsLong())));
    }

    private static boolean active(Round round) {
        return round.state >= INIT && round.state <= START;
    }

    private static boolean callbacksDone(Round round) {
        return round.luaCalls == 0 && round.callbacks.stream().allMatch(Future::isDone);
    }

    /** A nested synchronous group call also keeps its battle alive until the call returns. */
    public int withGroupLuaCall(
            SceneGroup origin, SceneGroup target, java.util.function.IntSupplier call) {
        synchronized (scene) {
            if (closed || target == null || call == null) return -1;
            var selected = new LinkedHashSet<Round>();
            for (var group : new SceneGroup[] {origin, target}) {
                if (group == null) continue;
                var round = byGroup.get(group.id);
                if (round == null) continue;
                if (!round.valid || !groupsCurrent(round)) return -1;
                selected.add(round);
            }
            selected.forEach(round -> round.luaCalls++);
            try {
                return call.getAsInt();
            } finally {
                selected.forEach(round -> round.luaCalls--);
            }
        }
    }

    /** Retain the round of queued group events, including timer and gadget callbacks. */
    public Future<?> submitCallback(int groupId, Function<BooleanSupplier, Future<?>> submit) {
        synchronized (scene) {
            var round = byGroup.get(groupId);
            BooleanSupplier guard =
                    () -> {
                        synchronized (scene) {
                            return !closed
                                    && (round == null
                                            || round.valid
                                                    && byGroup.get(groupId) == round
                                                    && groupsCurrent(round));
                        }
                    };
            // Registration and submission are atomic with respect to starting the next round.
            var future = submit.apply(guard);
            if (round != null) round.callbacks.add(future);
            return future;
        }
    }

    private static int stage(Round round) {
        int index = 0;
        var stages = round.parameters.stages();
        while (index + 1 < stages.size() && round.progress >= stages.get(index + 1)) index++;
        return index;
    }

    private void emitState(Round round) {
        emit(
                round,
                EventType.EVENT_SCENE_MP_PLAY_BATTLE_STATE,
                round.playType,
                round.entry.getId(),
                round.state);
    }

    private void emit(Round round, int type, int p1, int p2, int p3) {
        for (var group : round.groups.values()) {
            var event =
                    new ScriptArgs(group.id, type, p1, p2)
                            .setParam3(p3)
                            .setUid(round.owner.getUid());
            round.callbacks.add(
                    scene.getScriptManager()
                            .callEvent(
                                    event,
                                    () -> {
                                        synchronized (scene) {
                                            return !closed
                                                    && round.valid
                                                    && rounds.get(round.entry.getId()) == round
                                                    && scene.getScriptManager()
                                                                    .getLoadedGroupById(group.id)
                                                            == group;
                                        }
                                    }));
        }
    }

    private void broadcastInfo(Round round) {
        scene.broadcastPacket(PacketScenePlayBattle.info(info(round)));
    }

    private static ScenePlayBattleInfo info(Round round) {
        var builder =
                ScenePlayBattleInfo.newBuilder()
                        .setPlayId(round.entry.getId())
                        .setPlayType(round.playType)
                        .setType(round.type)
                        .setState(round.state)
                        .setPrepareEndTime((int) round.prepareEnd)
                        .setStartTime((int) round.startTime)
                        .setProgress((int) round.progress);
        if (round.parameters != null) {
            builder.setDuration((int) round.parameters.duration()).setMode(round.parameters.mode());
            for (long threshold : round.parameters.stages())
                builder.addProgressStageList((int) threshold);
        }
        return builder.build();
    }
}
