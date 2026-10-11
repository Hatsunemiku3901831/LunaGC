package emu.grasscutter.game.activity.aster;

import java.util.*;

/** The host's persisted reward point. Qualification is frozen when the original Lua creates it. */
public final class AsterMidRewardState {
    private String token;
    private Set<Integer> qualified, remaining, reserved = new HashSet<>();
    private long deadTime;

    AsterMidRewardState(Set<Integer> qualified) {
        token = UUID.randomUUID().toString();
        this.qualified = new HashSet<>(qualified);
        remaining = new HashSet<>(qualified);
    }

    public record View(
            String token,
            int difficulty,
            Set<Integer> qualified,
            Set<Integer> remaining,
            Set<Integer> reserved,
            long deadTime) {
        public boolean expired(long now) {
            return deadTime != 0 && now / 1000 >= deadTime;
        }

        public boolean canClaim(int uid, long now) {
            return !expired(now) && remaining.contains(uid) && !reserved.contains(uid);
        }
    }

    View view(int difficulty) {
        return new View(
                token,
                difficulty,
                Set.copyOf(qualified),
                Set.copyOf(remaining),
                Set.copyOf(reserved),
                deadTime);
    }

    void reserve(int uid) {
        if (!remaining.contains(uid) || !reserved.add(uid))
            throw new IllegalStateException("Aster reward is not available for this player");
    }

    void cancel(int uid) {
        if (!reserved.remove(uid)) throw new IllegalStateException("No Aster reward reservation");
    }

    void acknowledge(int uid, long now) {
        cancel(uid);
        if (!remaining.remove(uid)) throw new IllegalStateException("Aster reward already claimed");
        // The reference starts this ten-second timer on the first successful MP claim.
        if (deadTime == 0 && !remaining.isEmpty()) deadTime = now / 1000 + 10;
    }

    boolean canFinish(long now, boolean multiplayer) {
        return reserved.isEmpty()
                && (remaining.isEmpty()
                        || deadTime != 0 && (now / 1000 >= deadTime || !multiplayer));
    }

    boolean isValid() {
        return validToken(token)
                && qualified != null
                && !qualified.isEmpty()
                && qualified.stream().allMatch(uid -> uid != null && uid > 0)
                && remaining != null
                && reserved != null
                && qualified.containsAll(remaining)
                && remaining.containsAll(reserved)
                && deadTime >= 0
                && deadTime <= 0xffff_ffffL;
    }

    static boolean validToken(String token) {
        if (token == null) return false;
        try {
            return UUID.fromString(token).toString().equals(token);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
