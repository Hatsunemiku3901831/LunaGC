package emu.grasscutter.game.activity.aster;

import java.util.*;

/**
 * Kept in the activity document, so a new replay resets the event currency and collected points.
 */
public final class AsterProgress {
    private Set<Long> collected = new HashSet<>();
    private int pendingWatcherReward;
    private int midRefreshId, midCollectCount, midCredit, pendingMidClaim;
    private Set<Integer> midFinishedCamps = new HashSet<>();
    private Map<Integer, Integer> midOffers = new HashMap<>();

    public Set<Long> collected() {
        return Set.copyOf(collected);
    }

    public int credit() {
        return Math.addExact(collected.size(), midCredit);
    }

    public boolean collect(int groupId, int configId) {
        return collected.add(AsterFragments.key(groupId, configId));
    }

    public boolean hasCollected(int groupId, int configId) {
        return collected.contains(AsterFragments.key(groupId, configId));
    }

    public int pendingWatcherReward() {
        return pendingWatcherReward;
    }

    public void beginWatcherReward(int id) {
        if (pendingWatcherReward != 0 || pendingMidClaim != 0)
            throw new IllegalStateException("Aster reward already reserved");
        pendingWatcherReward = id;
    }

    public void finishWatcherReward() {
        pendingWatcherReward = 0;
    }

    public int midRefreshId() {
        return midRefreshId;
    }

    public int midCollectCount() {
        return midCollectCount;
    }

    public int pendingMidClaim() {
        return pendingMidClaim;
    }

    public Set<Integer> midFinishedCamps() {
        return Set.copyOf(midFinishedCamps);
    }

    public Map<Integer, Integer> midOffers() {
        return Map.copyOf(midOffers);
    }

    public void startMidBatch(int id) {
        var batch = emu.grasscutter.data.GameData.getAsterMidDataMap().get(id);
        if (batch == null
                || !midOffers.isEmpty()
                || pendingMidClaim != 0
                || midRefreshId != 0 && !midBatchFinished())
            throw new IllegalStateException("Aster camps still pending");
        midRefreshId = id;
        midFinishedCamps.clear();
    }

    public boolean midBatchFinished() {
        var batch = emu.grasscutter.data.GameData.getAsterMidDataMap().get(midRefreshId);
        return batch != null && midFinishedCamps.containsAll(batch.getCampVec());
    }

    public boolean midCampAvailable(int id) {
        var batch = emu.grasscutter.data.GameData.getAsterMidDataMap().get(midRefreshId);
        return batch != null && batch.getCampVec().contains(id) && !midFinishedCamps.contains(id);
    }

    public boolean offerMidReward(int id, int difficulty) {
        return midCampAvailable(id)
                && difficulty >= 1
                && difficulty <= 9
                && !midOffers.containsKey(id)
                && midOffers.putIfAbsent(id, difficulty) == null;
    }

    public void beginMidClaim(int id) {
        if (pendingMidClaim != 0 || pendingWatcherReward != 0 || !midOffers.containsKey(id))
            throw new IllegalStateException("Aster reward already reserved");
        pendingMidClaim = id;
    }

    public void cancelMidClaim() {
        pendingMidClaim = 0;
    }

    public void finishMidClaim(int energy) {
        if (pendingMidClaim == 0 || energy <= 0)
            throw new IllegalStateException("No Aster recovery claim");
        midCredit = Math.addExact(midCredit, energy);
        midCollectCount = Math.addExact(midCollectCount, 1);
        midOffers.remove(pendingMidClaim);
        midFinishedCamps.add(pendingMidClaim);
        pendingMidClaim = 0;
    }

    public boolean isValid() {
        return collected != null
                && collected.stream().allMatch(k -> k != null && AsterFragments.contains(k))
                && (pendingWatcherReward == 0
                        || pendingWatcherReward >= 1200101 && pendingWatcherReward <= 1200106
                        || pendingWatcherReward >= 1200110 && pendingWatcherReward <= 1200115)
                && midCredit >= 0
                && midCollectCount >= 0
                && (long) midCredit + collected.size() <= Integer.MAX_VALUE
                && midOffers != null
                && midFinishedCamps != null
                && midOffers.entrySet().stream()
                        .allMatch(
                                e ->
                                        e.getKey() != null
                                                && e.getValue() != null
                                                && AsterMidCamps.camps().containsKey(e.getKey())
                                                && e.getValue() >= 1
                                                && e.getValue() <= 9)
                && midFinishedCamps.stream()
                        .allMatch(id -> id != null && AsterMidCamps.camps().containsKey(id))
                && (pendingMidClaim == 0
                        || pendingWatcherReward == 0 && midOffers.containsKey(pendingMidClaim))
                && (midRefreshId == 0
                        ? midOffers.isEmpty() && midFinishedCamps.isEmpty()
                        : emu.grasscutter.data.GameData.getAsterMidDataMap()
                                        .containsKey(midRefreshId)
                                && emu.grasscutter.data.GameData.getAsterMidDataMap()
                                        .get(midRefreshId)
                                        .getCampVec()
                                        .containsAll(midOffers.keySet())
                                && emu.grasscutter.data.GameData.getAsterMidDataMap()
                                        .get(midRefreshId)
                                        .getCampVec()
                                        .containsAll(midFinishedCamps)
                                && Collections.disjoint(midOffers.keySet(), midFinishedCamps));
    }
}
