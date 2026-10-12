package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AvatarSkillInfoOuterClass.AvatarSkillInfo;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;

import java.util.*;
import java.util.function.LongSupplier;

/** Ordinary skill recovery belongs to the player session and survives scene entity replacement. */
public final class AbilitySkillCooldown {
    private static final long UINT32_MAX = 0xffffffffL;
    private final Player player;
    private final LongSupplier clock;
    // Stored avatars retain their state through scene changes. Discarded trial avatars can expire.
    private final Map<Avatar, Map<Integer, Recovery>> avatars = new WeakHashMap<>();
    private GameSession session;
    private long lastTime = -1;

    private static final class Recovery {
        final Deque<Long> durations = new ArrayDeque<>();
        long firstEnd;

        void advance(long now, int count) {
            while (!durations.isEmpty() && firstEnd <= now) {
                durations.removeFirst();
                if (!durations.isEmpty()) firstEnd += durations.getFirst();
            }
            // Changing maximum charges discards excess queued recoveries from the end.
            while (durations.size() > count) durations.removeLast();
        }

        AvatarSkillInfo toProto(long now, int count) {
            var result = AvatarSkillInfo.newBuilder().setMaxChargeCount(count);
            if (!durations.isEmpty()) {
                long full = durations.getFirst();
                result.setPassCdTime((int) Math.max(0, full - (firstEnd - now)));
                for (long duration : durations) result.addFullCdTimeList((int) duration);
            }
            return result.build();
        }
    }

    public AbilitySkillCooldown(Player player) {
        this(player, player::getPlayerTimeMs);
    }

    public AbilitySkillCooldown(Player player, LongSupplier clock) {
        this.player = Objects.requireNonNull(player);
        this.clock = Objects.requireNonNull(clock);
        session = player.getSession();
    }

    private long time() {
        var current = player.getSession();
        long now = clock.getAsLong();
        if (session != current || now < lastTime) avatars.clear();
        session = current;
        lastTime = now;
        return now < 0 || current != player.getSession() ? -1 : now;
    }

    private static boolean ordinary(int id) {
        var data = GameData.getAvatarSkillDataMap().get(id);
        return id > 0 && data != null && data.getShareCDID() == 0;
    }

    private static boolean permanent(Avatar avatar, int id) {
        var depot = avatar.getSkillDepot();
        return depot != null
                && (depot.getSkillsAndEnergySkill().anyMatch(skill -> skill == id)
                        || depot.getSubSkills() != null && depot.getSubSkills().contains(id));
    }

    public static boolean ownsSkill(Avatar avatar, int id) {
        return avatar != null
                && (permanent(avatar, id) || avatar.getSkillInfoCharges().containsKey(id));
    }

    private static boolean validCount(int count) {
        return count >= 1 && count <= 64;
    }

    private static long milliseconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds * 1000 > UINT32_MAX) return -1;
        return Math.round(seconds * 1000);
    }

    /** Record an already resolved use. The client runs its own start action, so do not echo it. */
    public boolean start(Avatar avatar, int id, double seconds) {
        if (avatar == null
                || avatar.getPlayer() != player
                || !ordinary(id)
                || !ownsSkill(avatar, id)) return false;
        long duration = milliseconds(seconds);
        int count =
                avatar.getSkillInfoCharges()
                        .getOrDefault(
                                id, GameData.getAvatarSkillDataMap().get(id).getMaxChargeNum());
        if (duration < 0 || !validCount(count)) return false;
        var scene = player.getScene();
        if (scene == null) return false;
        // Temporary skill information has its own lock. Read it before acquiring this lock.
        synchronized (this) {
            long now = time();
            if (now < 0 || now > Long.MAX_VALUE - UINT32_MAX * 64 || scene != player.getScene())
                return false;
            var skills = avatars.computeIfAbsent(avatar, ignored -> new HashMap<>());
            var recovery = skills.computeIfAbsent(id, ignored -> new Recovery());
            recovery.advance(now, count);
            if (duration == 0 || recovery.durations.size() >= count) return true;
            if (recovery.durations.isEmpty()) recovery.firstEnd = now + duration;
            recovery.durations.addLast(duration);
            return true;
        }
    }

    /** Delta and ratio change the first pending recovery, never the durations of later charges. */
    public boolean modifyRemaining(Avatar avatar, int id, float delta, float ratio) {
        if (avatar == null
                || avatar.getPlayer() != player
                || !ordinary(id)
                || !ownsSkill(avatar, id)
                || !Float.isFinite(delta)
                || !Float.isFinite(ratio)) return false;
        int count =
                avatar.getSkillInfoCharges()
                        .getOrDefault(
                                id, GameData.getAvatarSkillDataMap().get(id).getMaxChargeNum());
        if (!validCount(count)) return false;
        synchronized (this) {
            long now = time();
            if (now < 0 || now > Long.MAX_VALUE - UINT32_MAX * 64) return false;
            var skills = avatars.get(avatar);
            var recovery = skills == null ? null : skills.get(id);
            if (recovery == null) return true;
            recovery.advance(now, count);
            if (recovery.durations.isEmpty()) return true;
            float remaining = (recovery.firstEnd - now) / 1000f;
            float changed = (remaining + delta) * (1f + ratio);
            if (!Float.isFinite(changed)) return false;
            long next = milliseconds(Math.max(0f, changed));
            if (next < 0) return false;
            // An extension beyond the original duration needs a larger full time for uint32
            // elapsed.
            long full = Math.max(recovery.durations.removeFirst(), next);
            recovery.durations.addFirst(full);
            recovery.firstEnd = now + next;
            recovery.advance(now, count);
            return true;
        }
    }

    public synchronized AvatarSkillInfo state(Avatar avatar, int id, int count) {
        if (avatar == null || avatar.getPlayer() != player || !validCount(count)) return null;
        long now = time();
        if (now < 0) return null;
        var skills = avatars.get(avatar);
        var recovery = skills == null ? null : skills.get(id);
        if (recovery == null) return AvatarSkillInfo.newBuilder().setMaxChargeCount(count).build();
        recovery.advance(now, count);
        return recovery.toProto(now, count);
    }

    /** Caller supplies charge information before entering the cooldown lock. */
    public synchronized Map<Integer, AvatarSkillInfo> snapshot(
            Avatar avatar, Map<Integer, Integer> charges) {
        if (avatar == null || avatar.getPlayer() != player || time() < 0) return Map.of();
        var counts = new TreeMap<Integer, Integer>(charges);
        var skills = avatars.get(avatar);
        if (skills != null) {
            for (var id : skills.keySet()) {
                var data = GameData.getAvatarSkillDataMap().get(id);
                if (data != null && permanent(avatar, id))
                    counts.putIfAbsent(id, data.getMaxChargeNum());
            }
        }
        var result = new TreeMap<Integer, AvatarSkillInfo>();
        counts.forEach(
                (id, count) -> {
                    var state = state(avatar, id, count);
                    if (state != null) result.put(id, state);
                });
        return Collections.unmodifiableMap(result);
    }

    public void synchronize() {
        if (player.getScene() == null) return;
        for (var entity : player.getTeamManager().getActiveTeamSnapshot()) {
            var avatar = entity.getAvatar();
            var states = avatar.getSkillInfoStates();
            if (!states.isEmpty())
                player.sendPacket(new PacketAvatarSkillInfoNotify(avatar.getGuid(), states));
        }
    }

    /** Reset selected owned avatars and report full charges, including the inactive half. */
    public boolean reset(Collection<Avatar> selected) {
        if (selected == null || selected.isEmpty() || player.getScene() == null) return false;
        var targets = new LinkedHashSet<Avatar>();
        var charges = new LinkedHashMap<Avatar, Map<Integer, Integer>>();
        for (var avatar : selected) {
            if (avatar == null
                    || avatar.getPlayer() != player
                    || player.getAvatars().getAvatarById(avatar.getAvatarId()) != avatar
                    || !targets.add(avatar)) return false;
            charges.put(avatar, avatar.getSkillInfoCharges());
        }
        var resets = new LinkedHashMap<Avatar, Map<Integer, AvatarSkillInfo>>();
        synchronized (this) {
            if (time() < 0) return false;
            for (var avatar : targets) {
                var states = snapshot(avatar, charges.get(avatar));
                var ready = new TreeMap<Integer, AvatarSkillInfo>();
                states.forEach(
                        (id, state) ->
                                ready.put(
                                        id,
                                        AvatarSkillInfo.newBuilder()
                                                .setMaxChargeCount(state.getMaxChargeCount())
                                                .build()));
                resets.put(avatar, ready);
            }
            targets.forEach(avatars::remove);
        }
        resets.forEach(
                (avatar, ready) ->
                        player.sendPacket(
                                new PacketAvatarSkillInfoNotify(avatar.getGuid(), ready)));
        return true;
    }
}
