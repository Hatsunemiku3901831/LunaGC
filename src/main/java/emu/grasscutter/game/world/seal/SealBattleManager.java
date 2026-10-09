package emu.grasscutter.game.world.seal;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SealBattleBeginNotifyOuterClass.SealBattleBeginNotify;
import emu.grasscutter.net.proto.SealBattleEndNotifyOuterClass.SealBattleEndNotify;
import emu.grasscutter.net.proto.SealBattleProgressNotifyOuterClass.SealBattleProgressNotify;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;

import java.util.*;
import java.util.concurrent.*;

/** Scene-local seal rounds. State changes follow the scene monitor; Lua runs asynchronously. */
public final class SealBattleManager {
    private record Key(int groupId, int configId) {}

    private static final class Round {
        final Key key;
        final EntityGadget gadget;
        final SceneGroup group;
        final SceneGroup monsterGroup;
        final Player owner;
        final SealBattleParameters parameters;
        final long endTime;
        final Set<EntityMonster> credited = Collections.newSetFromMap(new IdentityHashMap<>());
        long lastTick;
        long progress;
        boolean inside;
        volatile boolean active = true;
        final List<Future<?>> callbacks = new ArrayList<>();
        volatile boolean valid = true;

        Round(
                EntityGadget gadget,
                SceneGroup group,
                Player owner,
                SealBattleParameters parameters,
                SceneGroup monsterGroup,
                long now) {
            key = new Key(gadget.getGroupId(), gadget.getConfigId());
            this.gadget = gadget;
            this.group = group;
            this.owner = owner;
            this.parameters = parameters;
            this.monsterGroup = monsterGroup;
            lastTick = now;
            endTime = now + parameters.battleTime();
        }
    }

    private final Scene scene;
    private final Map<Key, Round> rounds = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public SealBattleManager(Scene scene) {
        this.scene = scene;
    }

    public boolean start(EntityGadget gadget, SceneGroup group, SealBattleParameters parameters) {
        synchronized (scene) {
            Player owner = scene.getHost();
            if (closed
                    || gadget == null
                    || group == null
                    || parameters == null
                    || scene.isPaused()
                    || gadget.getScene() != scene
                    || gadget.getGroupId() != group.id
                    || scene.getEntities().get(gadget.getId()) != gadget
                    || scene.getScriptManager().getLoadedGroupById(group.id) != group
                    || !present(owner)
                    || !teamAlive(owner)
                    || !finite(gadget.getPosition())) return false;
            var key = new Key(group.id, gadget.getConfigId());
            var previous = rounds.get(key);
            // A terminal Lua callback must finish before the same seal can start its next round.
            if (previous != null && (previous.active || !callbacksDone(previous))) return false;
            long now = time(owner);
            var monsterGroup =
                    scene.getScriptManager().getLoadedGroupById(parameters.monsterGroupId());
            if (monsterGroup == null) return false;
            if (now < 0 || now + parameters.battleTime() > 0xffff_ffffL) return false;
            var round = new Round(gadget, group, owner, parameters, monsterGroup, now);
            rounds.put(key, round);
            scene.broadcastPacket(begin(round));
            broadcastProgress(round);
            round.callbacks.add(
                    scene.getScriptManager()
                            .callEvent(
                                    event(round, EventType.EVENT_SEAL_BATTLE_BEGIN, 0),
                                    () -> callbackCurrent(round) && round.active));
            return true;
        }
    }

    public void tick() {
        synchronized (scene) {
            if (closed || scene.isPaused()) return;
            for (var round : List.copyOf(rounds.values())) {
                if (round.active) advance(round);
                else if (scene.getEntities().get(round.gadget.getId()) != round.gadget
                        && callbacksDone(round)) {
                    rounds.remove(round.key, round);
                }
            }
        }
    }

    public void onMonsterKilled(EntityMonster monster, Player killer) {
        synchronized (scene) {
            if (closed
                    || scene.isPaused()
                    || monster == null
                    || monster.getScene() != scene
                    || !present(killer)
                    || scene.getEntities().get(monster.getId()) != monster) return;
            var meta = monster.getMetaMonster();
            if (meta == null) return;
            for (var round : List.copyOf(rounds.values())) {
                if (!round.active || monster.getGroupId() != round.parameters.monsterGroupId())
                    continue;
                advance(round);
                if (!round.active || !round.credited.add(monster)) continue;
                int score =
                        meta.kill_score == 0
                                ? round.parameters.defaultKillCharge()
                                : meta.kill_score;
                if (score <= 0) continue;
                round.progress = Math.min(round.parameters.maxEnergy(), round.progress + score);
                broadcastProgress(round);
                if (round.progress == round.parameters.maxEnergy()) finish(round, true, true);
            }
        }
    }

    private void advance(Round round) {
        if (!present(round.owner)
                || !teamAlive(round.owner)
                || !finite(round.gadget.getPosition())
                || !withinOwnerLeash(round)
                || scene.getScriptManager().getLoadedGroupById(round.monsterGroup.id)
                        != round.monsterGroup
                || scene.getEntities().get(round.gadget.getId()) != round.gadget) {
            finish(round, false, true);
            return;
        }
        long now = time(round.owner);
        if (now >= round.endTime) {
            finish(round, false, true);
            return;
        }
        long elapsed = now - round.lastTick;
        if (elapsed <= 0) return;
        boolean inside = scene.getPlayers().stream().anyMatch(player -> inside(player, round));
        long before = round.progress;
        if (inside && round.inside) {
            round.progress =
                    Math.min(
                            round.parameters.maxEnergy(),
                            before + elapsed * round.parameters.autoCharge());
        } else if (!inside && !round.inside) {
            round.progress = Math.max(0, before - elapsed * round.parameters.autoDecline());
        }
        boolean left = round.inside && !inside;
        round.inside = inside;
        round.lastTick = now;
        if (left)
            round.callbacks.add(
                    scene.getScriptManager()
                            .callEvent(
                                    event(round, EventType.EVENT_SEAL_BATTLE_PROGRESS_DECREASE, 0),
                                    () -> callbackCurrent(round) && round.active));
        broadcastProgress(round);
        if (round.progress == round.parameters.maxEnergy()) finish(round, true, true);
    }

    private void finish(Round round, boolean won, boolean callback) {
        if (!round.active) return;
        round.active = false;
        round.credited.clear();
        var packet = new BasePacket(PacketOpcodes.SealBattleEndNotify);
        packet.setData(
                SealBattleEndNotify.newBuilder()
                        .setSealEntityId(round.gadget.getId())
                        .setIsWin(won)
                        .build());
        scene.broadcastPacket(packet);
        if (callback)
            round.callbacks.add(
                    scene.getScriptManager()
                            .callEvent(
                                    event(round, EventType.EVENT_SEAL_BATTLE_END, won ? 1 : 0),
                                    () -> callbackCurrent(round)));
    }

    private boolean callbackCurrent(Round round) {
        var current = scene.getEntityByConfigId(round.key.configId(), round.key.groupId());
        return !closed
                && round.valid
                && rounds.get(round.key) == round
                && scene.getScriptManager().getLoadedGroupById(round.group.id) == round.group
                && (current == null || current == round.gadget);
    }

    public void onPlayerLeaving(Player player) {
        synchronized (scene) {
            for (var round : rounds.values()) if (round.owner == player) finish(round, false, true);
        }
    }

    public void onEntityRemoved(GameEntity entity) {
        if (!(entity instanceof EntityGadget)) return;
        synchronized (scene) {
            var round = rounds.get(new Key(entity.getGroupId(), entity.getConfigId()));
            if (round != null && round.gadget == entity) finish(round, false, true);
        }
    }

    public void onGroupUnloaded(SceneGroup group) {
        synchronized (scene) {
            for (var round : List.copyOf(rounds.values())) {
                if (round.group == group) {
                    round.valid = false;
                    finish(round, false, false);
                } else if (round.monsterGroup == group) finish(round, false, true);
            }
        }
    }

    public void sendTo(Player player) {
        synchronized (scene) {
            if (!present(player)) return;
            for (var round : rounds.values()) {
                if (!round.active) continue;
                advance(round);
                if (round.active) {
                    player.sendPacket(begin(round));
                    player.sendPacket(progress(round, player));
                }
            }
        }
    }

    public void close() {
        synchronized (scene) {
            closed = true;
            rounds.values().forEach(round -> finish(round, false, false));
            rounds.clear();
        }
    }

    private boolean present(Player player) {
        return player != null
                && player.getScene() == scene
                && scene.getPlayers().stream().anyMatch(member -> member == player);
    }

    private static boolean teamAlive(Player player) {
        return player.getTeamManager().getActiveTeamSnapshot().stream()
                .anyMatch(EntityAvatar::isAlive);
    }

    private boolean inside(Player player, Round round) {
        if (!present(player)) return false;
        var avatar = player.getTeamManager().getCurrentAvatarEntity();
        if (avatar == null || !avatar.isAlive() || avatar.getScene() != scene) return false;
        var position = avatar.getPosition();
        var center = round.gadget.getPosition();
        if (!finite(position)) return false;
        double dx = position.getX() - (double) center.getX();
        double dz = position.getZ() - (double) center.getZ();
        return dx * dx + dz * dz <= (double) round.parameters.radius() * round.parameters.radius();
    }

    private static boolean withinOwnerLeash(Round round) {
        var avatar = round.owner.getTeamManager().getCurrentAvatarEntity();
        if (avatar == null || !finite(avatar.getPosition())) return false;
        double dx = avatar.getPosition().getX() - (double) round.gadget.getPosition().getX();
        double dz = avatar.getPosition().getZ() - (double) round.gadget.getPosition().getZ();
        return dx * dx + dz * dz < 50d * 50d;
    }

    private static boolean finite(Position position) {
        return position != null
                && Float.isFinite(position.getX())
                && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ());
    }

    private static long time(Player owner) {
        return owner.getPlayerTimeMs() / 1000;
    }

    private static boolean callbacksDone(Round round) {
        return round.callbacks.stream().allMatch(Future::isDone);
    }

    private static ScriptArgs event(Round round, int type, int result) {
        return new ScriptArgs(round.key.groupId(), type, round.key.configId(), result)
                .setSourceEntityId(round.gadget.getId());
    }

    private static BasePacket begin(Round round) {
        var packet = new BasePacket(PacketOpcodes.SealBattleBeginNotify);
        // The Lua ENERGY_CHARGE ordinal is 1; the protocol's EnergyCharge value is 2.
        packet.setData(
                SealBattleBeginNotify.newBuilder()
                        .setSealEntityId(round.gadget.getId())
                        .setSealRadius(round.parameters.radius())
                        .setSealMaxProgress(round.parameters.maxEnergy())
                        .setBattleType(2)
                        .build());
        return packet;
    }

    private void broadcastProgress(Round round) {
        for (var player : scene.getPlayers())
            if (present(player)) player.sendPacket(progress(round, player));
    }

    private static BasePacket progress(Round round, Player viewer) {
        var packet = new BasePacket(PacketOpcodes.SealBattleProgressNotify);
        // Session player clocks have different origins. Announce the same remaining duration.
        long remaining = Math.max(0, round.endTime - time(round.owner));
        long end =
                viewer == round.owner
                        ? round.endTime
                        : Math.min(0xffff_ffffL, Math.max(0, time(viewer)) + remaining);
        packet.setData(
                SealBattleProgressNotify.newBuilder()
                        .setSealEntityId(round.gadget.getId())
                        .setSealRadius(round.parameters.radius())
                        .setProgress((int) round.progress)
                        .setMaxProgress(round.parameters.maxEnergy())
                        .setEndTime((int) end)
                        .build());
        return packet;
    }
}
