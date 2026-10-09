package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ShareCDOuterClass.ShareCD;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAllShareCDDataNotify;

import java.util.*;
import java.util.function.LongSupplier;

/** Player-scoped shared cooldowns using the two clocks synchronized by PlayerTimeNotify. */
public final class AbilitySharedCooldown {
    private final Player player;
    private final LongSupplier playerClock;
    private final LongSupplier realClock;
    private final Map<Integer, List<ShareCD>> groups = new HashMap<>();
    private GameSession session;
    private long lastPlayerMs = -1;

    public AbilitySharedCooldown(Player player) {
        this(player, player::getPlayerTimeMs, System::currentTimeMillis);
    }

    public AbilitySharedCooldown(Player player, LongSupplier playerClock, LongSupplier realClock) {
        this.player = Objects.requireNonNull(player);
        this.playerClock = Objects.requireNonNull(playerClock);
        this.realClock = Objects.requireNonNull(realClock);
        session = player.getSession();
    }

    private long[] times() {
        var currentSession = player.getSession();
        if (session != currentSession) {
            groups.clear();
            lastPlayerMs = -1;
            session = currentSession;
        }
        long play = playerClock.getAsLong();
        long real = realClock.getAsLong();
        if (play < 0 || real < 0 || currentSession != player.getSession()) return null;
        // A player clock reset invalidates that clock's endpoints, never the Unix-time ones.
        if (play < lastPlayerMs)
            groups.keySet()
                    .removeIf(
                            id -> {
                                var data = GameData.getShareCDDataMap().get(id);
                                return data == null || !data.isUseRealTime();
                            });
        lastPlayerMs = play;
        groups.entrySet()
                .removeIf(
                        entry -> {
                            var data = GameData.getShareCDDataMap().get(entry.getKey());
                            if (data == null) return true;
                            long now = data.isUseRealTime() ? real : play;
                            entry.getValue().removeIf(cd -> cd.getCdEndTime() <= now);
                            return entry.getValue().isEmpty();
                        });
        return new long[] {play, real};
    }

    public synchronized boolean startAvatarSkill(int skillId, int slot) {
        var skill = GameData.getAvatarSkillDataMap().get(skillId);
        return skill != null && start(skill.getShareCDID(), slot);
    }

    public synchronized boolean startActivitySkill(int skillId, int slot) {
        var skill = GameData.getActivitySkillDataMap().get(skillId);
        return skill != null && start(skill.getShareCDID(), slot);
    }

    private boolean start(int groupId, int slot) {
        var data = GameData.getShareCDDataMap().get(groupId);
        if (groupId <= 0
                || data == null
                || data.getMaxChargeNum() < 1
                || data.getMaxChargeNum() > 64) return false;
        long duration = data.durationMs(slot);
        if (duration < 0) return false;
        var currentScene = player.getScene();
        if (currentScene == null) return false;
        var times = times();
        if (times == null || currentScene != player.getScene()) return false;
        long now = data.isUseRealTime() ? times[1] : times[0];
        var queue = groups.computeIfAbsent(groupId, ignored -> new ArrayList<>());
        if (queue.size() >= data.getMaxChargeNum()) return true;
        long base =
                queue.isEmpty() ? now : Math.max(now, queue.get(queue.size() - 1).getCdEndTime());
        if (base > Long.MAX_VALUE - duration) return false;
        queue.add(ShareCD.newBuilder().setCdSlot(slot).setCdEndTime(base + duration).build());
        notifyGroup(groupId, queue);
        return true;
    }

    public synchronized boolean clear(int groupId) {
        var currentScene = player.getScene();
        if (currentScene == null
                || groupId <= 0
                || !GameData.getShareCDDataMap().containsKey(groupId)
                || times() == null
                || currentScene != player.getScene()) return false;
        groups.remove(groupId);
        // The client clears its old queue before importing this list; an empty group is a reset.
        notifyGroup(groupId, List.of());
        return true;
    }

    private void notifyGroup(int groupId, List<ShareCD> queue) {
        player.sendPacket(
                new PacketAllShareCDDataNotify(Map.of(groupId, List.copyOf(queue)), false));
    }

    public synchronized Map<Integer, List<ShareCD>> snapshot() {
        if (times() == null) return Map.of();
        var result = new TreeMap<Integer, List<ShareCD>>();
        groups.forEach((id, queue) -> result.put(id, List.copyOf(queue)));
        return Collections.unmodifiableMap(result);
    }

    /** Scene entry imports the player's remaining cooldowns without resetting their clocks. */
    public synchronized void synchronize() {
        if (player.getScene() != null)
            player.sendPacket(new PacketAllShareCDDataNotify(snapshot(), true));
    }
}
