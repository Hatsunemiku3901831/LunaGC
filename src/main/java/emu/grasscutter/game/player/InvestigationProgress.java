package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player handbook history, embedded in the existing player progress document. */
@Entity
public final class InvestigationProgress {
    private Map<Integer, Integer> targets = new ConcurrentHashMap<>();
    private Set<Integer> firstDungeonRewards = ConcurrentHashMap.newKeySet();
    // 1 is a durable reservation; 2 is an acknowledged grant. Pending grants require
    // reconciliation.
    private Map<Integer, Integer> targetRewards = new ConcurrentHashMap<>();

    public synchronized int getTargetRewardState(int id) {
        return targetRewards == null ? 0 : targetRewards.getOrDefault(id, 0);
    }

    public synchronized void setTargetRewardState(int id, int state) {
        if (id <= 0 || state < 0 || state > 2)
            throw new IllegalArgumentException("Invalid handbook receipt");
        if (targetRewards == null) targetRewards = new ConcurrentHashMap<>();
        if (state == 0) targetRewards.remove(id);
        else targetRewards.put(id, state);
    }

    public synchronized int get(int id) {
        return targets == null ? 0 : Math.max(0, targets.getOrDefault(id, 0));
    }

    public synchronized boolean raise(int id, long next, int total) {
        if (id <= 0 || total <= 0 || next < 0) return false;
        int value = (int) Math.min(next, total);
        if (value <= get(id)) return false;
        if (targets == null) targets = new ConcurrentHashMap<>();
        targets.put(id, value);
        return true;
    }

    public synchronized boolean add(int id, int amount, int total) {
        return amount > 0 && raise(id, (long) get(id) + amount, total);
    }

    public synchronized boolean hasFirstDungeonReward(int dungeonId) {
        return firstDungeonRewards != null && firstDungeonRewards.contains(dungeonId);
    }

    public synchronized boolean recordFirstDungeonReward(int dungeonId) {
        if (dungeonId <= 0) return false;
        if (firstDungeonRewards == null) firstDungeonRewards = ConcurrentHashMap.newKeySet();
        return firstDungeonRewards.add(dungeonId);
    }
}
