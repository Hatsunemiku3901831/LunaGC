package emu.grasscutter.game.activity.aster;

import java.util.*;

/**
 * Kept in the activity document, so a new replay resets the event currency and collected points.
 */
public final class AsterProgress {
    private Set<Long> collected = new HashSet<>();
    private int pendingWatcherReward;
    private boolean specialRewardTaken, pendingSpecialReward;
    private int midRefreshId, midCollectCount, midCredit, pendingMidClaim;
    private Set<Integer> midFinishedCamps = new HashSet<>();
    private Map<Integer, Integer> midOffers = new HashMap<>();
    private Map<Integer, AsterMidRewardState> midRewardStates = new HashMap<>();
    private Set<String> midClaims = new HashSet<>();
    private String pendingMidReward = "";
    private int spentCredit;
    private Map<Integer, Integer> shopPurchases = new HashMap<>();
    private ShopPurchase pendingShopPurchase;
    private int bigCredit, bigCollectCount, spentBigCredit;
    private AsterBigRewardState bigReward;
    private Set<String> bigClaims = new HashSet<>();
    private String pendingBigClaim = "";

    public int bigCredit() {
        return bigCredit;
    }

    public int bigCreditBalance() {
        return Math.subtractExact(bigCredit, spentBigCredit);
    }

    public int currencyBalance(int currencyId) {
        return switch (currencyId) {
            case 109 -> creditBalance();
            case 110 -> bigCreditBalance();
            default -> throw new IllegalArgumentException("Invalid Aster shop currency");
        };
    }

    public int bigCollectCount() {
        return bigCollectCount;
    }

    public AsterBigRewardState.View bigReward() {
        return bigReward == null ? null : bigReward.view();
    }

    public boolean hasBigClaim(String token) {
        return bigClaims.contains(token);
    }

    public void openBigReward(Map<Integer, AsterBigRewards.Reward> rewards) {
        if (bigReward != null && !bigReward.view().reserved().isEmpty())
            throw new IllegalStateException("Unconfirmed Aster large reward cannot be replaced");
        var next = new AsterBigRewardState(rewards);
        if (!next.isValid()) throw new IllegalArgumentException("Invalid Aster large reward");
        bigReward = next;
    }

    public void reserveBigReward(int uid) {
        bigReward.reserve(uid);
    }

    /**
     * Starting a replacement battle retires only the old offer, preserving currency and receipts.
     */
    public void retireBigReward() {
        if (bigReward != null && !bigReward.view().reserved().isEmpty())
            throw new IllegalStateException("Unconfirmed Aster large reward cannot be retired");
        bigReward = null;
    }

    public void cancelBigReward(int uid) {
        bigReward.cancel(uid);
    }

    public void acknowledgeBigReward(int uid) {
        bigReward.acknowledge(uid);
    }

    public void beginBigClaim(String token) {
        if (hasPendingReward() || !AsterMidRewardState.validToken(token) || hasBigClaim(token))
            throw new IllegalStateException("Aster large claim cannot be reserved");
        pendingBigClaim = token;
    }

    public void cancelBigClaim() {
        pendingBigClaim = "";
    }

    public void finishBigClaim(int essence) {
        if (pendingBigClaim.isEmpty() || essence <= 0 || bigClaims.contains(pendingBigClaim))
            throw new IllegalStateException("No Aster large reservation");
        bigCredit = Math.addExact(bigCredit, essence);
        bigCollectCount = Math.addExact(bigCollectCount, 1);
        bigClaims.add(pendingBigClaim);
        pendingBigClaim = "";
    }

    /** Kept until both the delivered item and the completion have acknowledged writes. */
    public static final class ShopPurchase {
        private int goodsId, count, cost, itemId, itemCount;
        // Older reservations were all paid with exploration/recovery credit.
        private int currencyId = 109;

        private ShopPurchase() {}

        public ShopPurchase(int goodsId, int count, int cost, int itemId, int itemCount) {
            this(goodsId, count, cost, itemId, itemCount, 109);
        }

        public ShopPurchase(
                int goodsId, int count, int cost, int itemId, int itemCount, int currencyId) {
            this.goodsId = goodsId;
            this.count = count;
            this.cost = cost;
            this.itemId = itemId;
            this.itemCount = itemCount;
            this.currencyId = currencyId;
        }

        public int currencyId() {
            return currencyId;
        }

        public int goodsId() {
            return goodsId;
        }

        public int count() {
            return count;
        }

        public int cost() {
            return cost;
        }

        public int itemId() {
            return itemId;
        }

        public int itemCount() {
            return itemCount;
        }

        boolean isValid() {
            return goodsId > 0
                    && count > 0
                    && cost > 0
                    && itemId > 0
                    && itemCount > 0
                    && (currencyId == 109 || currencyId == 110);
        }
    }

    public Set<Long> collected() {
        return Set.copyOf(collected);
    }

    public int credit() {
        return Math.addExact(collected.size(), midCredit);
    }

    public int creditBalance() {
        return Math.subtractExact(credit(), spentCredit);
    }

    public int shopBought(int goodsId) {
        return shopPurchases.getOrDefault(goodsId, 0);
    }

    public ShopPurchase pendingShopPurchase() {
        return pendingShopPurchase;
    }

    public boolean hasPendingReward() {
        return pendingWatcherReward != 0
                || pendingSpecialReward
                || pendingMidClaim != 0
                || pendingShopPurchase != null
                || !pendingBigClaim.isEmpty();
    }

    public boolean specialRewardTaken() {
        return specialRewardTaken;
    }

    public boolean pendingSpecialReward() {
        return pendingSpecialReward;
    }

    public void beginSpecialReward() {
        if (specialRewardTaken || hasPendingReward())
            throw new IllegalStateException("Aster special reward cannot be reserved");
        pendingSpecialReward = true;
    }

    public void finishSpecialReward() {
        if (!pendingSpecialReward || specialRewardTaken)
            throw new IllegalStateException("No Aster special reward reservation");
        specialRewardTaken = true;
        pendingSpecialReward = false;
    }

    public void beginShopPurchase(ShopPurchase purchase, int previouslyBought) {
        if (hasPendingReward()
                || purchase == null
                || !purchase.isValid()
                || previouslyBought < 0
                || purchase.cost() > currencyBalance(purchase.currencyId()))
            throw new IllegalStateException("Aster shop purchase cannot be reserved");
        int bought =
                Math.addExact(
                        Math.max(shopBought(purchase.goodsId()), previouslyBought),
                        purchase.count());
        if (purchase.currencyId() == 109) spentCredit = Math.addExact(spentCredit, purchase.cost());
        else spentBigCredit = Math.addExact(spentBigCredit, purchase.cost());
        shopPurchases.put(purchase.goodsId(), bought);
        pendingShopPurchase = purchase;
    }

    public void finishShopPurchase() {
        if (pendingShopPurchase == null)
            throw new IllegalStateException("No Aster shop reservation");
        pendingShopPurchase = null;
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
        if (hasPendingReward()) throw new IllegalStateException("Aster reward already reserved");
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
        if (hasPendingReward()
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
        if (hasPendingReward() || !midOffers.containsKey(id) || midRewardStates.containsKey(id))
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
        return (!pendingSpecialReward
                        || !specialRewardTaken
                                && pendingWatcherReward == 0
                                && pendingMidClaim == 0
                                && pendingShopPurchase == null
                                && pendingBigClaim != null
                                && pendingBigClaim.isEmpty())
                && bigCredit >= 0
                && spentBigCredit >= 0
                && spentBigCredit <= bigCredit
                && bigCollectCount >= 0
                && bigClaims != null
                && bigClaims.size() <= bigCollectCount
                && bigClaims.stream().allMatch(AsterMidRewardState::validToken)
                && (bigReward == null || bigReward.isValid())
                && pendingBigClaim != null
                && (pendingBigClaim.isEmpty()
                        || AsterMidRewardState.validToken(pendingBigClaim)
                                && !bigClaims.contains(pendingBigClaim)
                                && pendingWatcherReward == 0
                                && pendingMidClaim == 0
                                && pendingShopPurchase == null)
                && collected != null
                && collected.stream().allMatch(k -> k != null && AsterFragments.contains(k))
                && (pendingWatcherReward == 0
                        || pendingWatcherReward >= 1200101 && pendingWatcherReward <= 1200106
                        || pendingWatcherReward >= 1200110 && pendingWatcherReward <= 1200115)
                && midCredit >= 0
                && midCollectCount >= 0
                && (long) midCredit + collected.size() <= Integer.MAX_VALUE
                && spentCredit >= 0
                && spentCredit <= (long) midCredit + collected.size()
                && shopPurchases != null
                && shopPurchases.entrySet().stream()
                        .allMatch(
                                e ->
                                        e.getKey() != null
                                                && e.getKey() > 0
                                                && e.getValue() != null
                                                && e.getValue() > 0)
                && (pendingShopPurchase == null
                        || pendingShopPurchase.isValid()
                                && pendingWatcherReward == 0
                                && pendingMidClaim == 0
                                && (pendingShopPurchase.currencyId() == 109
                                                ? spentCredit
                                                : spentBigCredit)
                                        >= pendingShopPurchase.cost()
                                && shopBought(pendingShopPurchase.goodsId())
                                        >= pendingShopPurchase.count())
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
