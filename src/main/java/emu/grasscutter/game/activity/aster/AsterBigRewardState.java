package emu.grasscutter.game.activity.aster;

import java.util.*;

/**
 * The original scene-play point has no first-claim timeout; its generation freezes personal drops.
 */
public final class AsterBigRewardState {
    private String token;
    private Map<Integer, AsterBigRewards.Reward> rewards;
    private Set<Integer> remaining, reserved = new HashSet<>();

    AsterBigRewardState(Map<Integer, AsterBigRewards.Reward> rewards) {
        token = UUID.randomUUID().toString();
        this.rewards = new HashMap<>(rewards);
        remaining = new HashSet<>(rewards.keySet());
    }

    public record View(
            String token,
            Map<Integer, AsterBigRewards.Reward> rewards,
            Set<Integer> remaining,
            Set<Integer> reserved) {
        public boolean canClaim(int uid) {
            return remaining.contains(uid) && !reserved.contains(uid);
        }
    }

    View view() {
        return new View(token, Map.copyOf(rewards), Set.copyOf(remaining), Set.copyOf(reserved));
    }

    void reserve(int uid) {
        if (!remaining.contains(uid) || !reserved.add(uid))
            throw new IllegalStateException("Aster large reward unavailable");
    }

    void cancel(int uid) {
        if (!reserved.remove(uid))
            throw new IllegalStateException("Aster large reward not reserved");
    }

    void acknowledge(int uid) {
        cancel(uid);
        if (!remaining.remove(uid))
            throw new IllegalStateException("Aster large reward already taken");
    }

    boolean isValid() {
        return AsterMidRewardState.validToken(token)
                && rewards != null
                && !rewards.isEmpty()
                && rewards.size() <= 4
                && rewards.entrySet().stream()
                        .allMatch(
                                e ->
                                        e.getKey() != null
                                                && e.getKey() > 0
                                                && e.getValue() != null
                                                && e.getValue().isValid())
                && remaining != null
                && reserved != null
                && rewards.keySet().containsAll(remaining)
                && remaining.containsAll(reserved);
    }
}
