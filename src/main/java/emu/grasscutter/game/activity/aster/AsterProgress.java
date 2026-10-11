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
    private Map<Integer, AsterMidRewardState> midRewardStates = new HashMap<>();
    private Set<String> midClaims = new HashSet<>();
    private String pendingMidReward = "";

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

    public AsterMidRewardState.View midReward(int camp) {
        var state = midRewardStates.get(camp);
        return state == null ? null : state.view(midOffers.get(camp));
    }

    public boolean hasMidClaim(String token) {
        return midClaims.contains(token);
    }

    public boolean offerMidReward(int camp, int difficulty, Set<Integer> qualified) {
        if (qualified == null
                || qualified.isEmpty()
                || qualified.stream().anyMatch(uid -> uid == null || uid <= 0)) return false;
        if (!offerMidReward(camp, difficulty)) return false;
        midRewardStates.put(camp, new AsterMidRewardState(qualified));
        return true;
    }

    /** Existing host-only offers retain their original qualification, including after reload. */
    public boolean upgradeMidReward(int camp, int hostUid) {
        if (!midOffers.containsKey(camp)
                || midRewardStates.containsKey(camp)
                || pendingMidClaim == camp
                || hostUid <= 0) return false;
        midRewardStates.put(camp, new AsterMidRewardState(Set.of(hostUid)));
        return true;
    }

    public void reserveMidReward(int camp, int uid) {
        midRewardStates.get(camp).reserve(uid);
    }

    public void cancelMidReward(int camp, int uid) {
        midRewardStates.get(camp).cancel(uid);
    }

    public void acknowledgeMidReward(int camp, int uid, long now) {
        midRewardStates.get(camp).acknowledge(uid, now);
    }

    public boolean finishMidReward(int camp, long now, boolean multiplayer) {
        var state = midRewardStates.get(camp);
        if (state == null || !state.canFinish(now, multiplayer) || pendingMidClaim == camp)
            return false;
        midRewardStates.remove(camp);
        midOffers.remove(camp);
        midFinishedCamps.add(camp);
        return true;
    }

    public void beginSharedMidClaim(int camp, String token) {
        if (pendingMidClaim != 0
                || pendingWatcherReward != 0
                || !AsterMidRewardState.validToken(token)
                || midClaims.contains(token)
                || !AsterMidCamps.camps().containsKey(camp))
            throw new IllegalStateException("Aster reward already reserved");
        pendingMidClaim = camp;
        pendingMidReward = token;
    }

    public void finishSharedMidClaim(int energy) {
        if (pendingMidClaim == 0 || energy <= 0 || pendingMidReward.isEmpty())
            throw new IllegalStateException("No shared Aster recovery claim");
        midCredit = Math.addExact(midCredit, energy);
        midCollectCount = Math.addExact(midCollectCount, 1);
        midClaims.add(pendingMidReward);
        cancelMidClaim();
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
        if (pendingMidClaim != 0
                || pendingWatcherReward != 0
                || !midOffers.containsKey(id)
                || midRewardStates.containsKey(id))
            throw new IllegalStateException("Aster reward already reserved");
        pendingMidClaim = id;
    }

    public void cancelMidClaim() {
        pendingMidClaim = 0;
        pendingMidReward = "";
    }

    public void finishMidClaim(int energy) {
        if (pendingMidClaim == 0 || energy <= 0 || !pendingMidReward.isEmpty())
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
                && midRewardStates != null
                && midClaims != null
                && pendingMidReward != null
                && midClaims.stream().allMatch(AsterMidRewardState::validToken)
                && midClaims.size() <= midCollectCount
                && midRewardStates.entrySet().stream()
                        .allMatch(
                                e ->
                                        midOffers.containsKey(e.getKey())
                                                && e.getValue() != null
                                                && e.getValue().isValid())
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
                        ? pendingMidReward.isEmpty()
                        : pendingWatcherReward == 0
                                && AsterMidCamps.camps().containsKey(pendingMidClaim)
                                && (pendingMidReward.isEmpty()
                                        ? midOffers.containsKey(pendingMidClaim)
                                        : AsterMidRewardState.validToken(pendingMidReward)
                                                && !midClaims.contains(pendingMidReward)))
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
