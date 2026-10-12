package emu.grasscutter.game.managers.forging;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.common.ItemParamData;

import java.util.*;

@Entity
public class ActiveForgeData {
    private int forgeId;
    private int avatarId;
    private int count;

    private int startTime;
    private int forgeTime;

    private int lastUnfinishedCount;
    private boolean changed;
    // 0 available; 1 reserved while start costs and queue persistence are unconfirmed.
    private int startState;
    // 0 available, 1 reserved, 2 delivered before final cleanup acknowledgement.
    private int outputState;
    private int reservedOutputCount;
    private int refundState;
    private int paymentVersion;
    private int paidUnitsRemaining;
    private int paidForgePointsPerUnit;
    private List<PaidCost> paidCostsPerUnit = new ArrayList<>();
    private int bonusVersion;
    // Morphia omits empty collections; retain the valid empty default after loading a queue.
    private List<PaidCost> bonusReturnsPerUnit = new ArrayList<>();
    private int productVersion;
    private List<PaidCost> productsPerUnit;

    public int getProductVersion() {
        return productVersion;
    }

    /** Freeze all guaranteed rewards before paying; configuration edits cannot rewrite a queue. */
    public void captureProducts(List<ItemParamData> products) {
        if (isBusy()
                || productVersion != 0
                || paymentVersion != 1
                || products == null
                || products.isEmpty())
            throw new IllegalStateException("Forge products cannot be recorded");
        var copied = new ArrayList<PaidCost>();
        var ids = new HashSet<Integer>();
        for (var item : products) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0 || !ids.add(item.getId()))
                throw new IllegalArgumentException("Invalid forge product row");
            copied.add(new PaidCost(item.getId(), item.getCount()));
        }
        productsPerUnit = copied;
        productVersion = 1;
    }

    public List<ItemParamData> getProducts(int units) {
        if (productVersion != 1
                || productsPerUnit == null
                || productsPerUnit.isEmpty()
                || units <= 0
                || units > count) throw new IllegalStateException("Invalid forge product receipt");
        var result = new ArrayList<ItemParamData>();
        var ids = new HashSet<Integer>();
        for (var item : productsPerUnit) {
            if (item == null || item.itemId <= 0 || item.count <= 0 || !ids.add(item.itemId))
                throw new IllegalStateException("Invalid forge product row");
            result.add(new ItemParamData(item.itemId, Math.multiplyExact(item.count, units)));
        }
        return result;
    }

    public int getBonusVersion() {
        return bonusVersion;
    }

    public void captureBonus(List<ItemParamData> returns) {
        if (isBusy() || bonusVersion != 0 || paymentVersion != 1)
            throw new IllegalStateException("Forge bonus cannot be recorded");
        var rows = new ArrayList<PaidCost>();
        var ids = new HashSet<Integer>();
        for (var item : returns) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0 || !ids.add(item.getId()))
                throw new IllegalArgumentException("Invalid forge bonus row");
            rows.add(new PaidCost(item.getId(), item.getCount()));
        }
        bonusReturnsPerUnit = rows;
        bonusVersion = 1;
    }

    public List<ItemParamData> getBonusReturns(int units) {
        if (bonusVersion == 0) return List.of();
        if (bonusVersion != 1 || bonusReturnsPerUnit == null || units <= 0 || units > count)
            throw new IllegalStateException("Invalid forge bonus receipt");
        var rows = new ArrayList<ItemParamData>();
        var ids = new HashSet<Integer>();
        for (var item : bonusReturnsPerUnit) {
            if (item == null || item.itemId <= 0 || item.count <= 0 || !ids.add(item.itemId))
                throw new IllegalStateException("Invalid forge bonus row");
            rows.add(new ItemParamData(item.itemId, Math.multiplyExact(item.count, units)));
        }
        return rows;
    }

    @Entity
    public static class PaidCost {
        private int itemId;
        private int count;

        public PaidCost() {}

        PaidCost(int itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    public int getPaymentVersion() {
        return paymentVersion;
    }

    public int getRefundState() {
        return refundState;
    }

    /** Store the effective costs after configuration switches and duplicate-row aggregation. */
    public void capturePayment(List<ItemParamData> costs, int units, int pointsPerUnit) {
        if (isBusy() || paymentVersion != 0 || units <= 0 || count != units || pointsPerUnit < 0)
            throw new IllegalArgumentException("Invalid forge payment snapshot");
        var copied = new ArrayList<PaidCost>();
        for (var cost : costs) {
            if (cost.getId() <= 0 || cost.getCount() <= 0 || cost.getCount() % units != 0)
                throw new IllegalArgumentException("Invalid forge cost snapshot");
            copied.add(new PaidCost(cost.getId(), cost.getCount() / units));
        }
        paidCostsPerUnit = copied;
        paidForgePointsPerUnit = pointsPerUnit;
        paidUnitsRemaining = units;
        paymentVersion = 1;
    }

    public List<ItemParamData> getRefundCosts() {
        if (paymentVersion != 1
                || count <= 0
                || count != paidUnitsRemaining
                || paidCostsPerUnit == null)
            throw new IllegalStateException("Invalid forge payment receipt");
        var costs = new ArrayList<ItemParamData>();
        var ids = new HashSet<Integer>();
        for (var cost : paidCostsPerUnit) {
            if (cost == null || cost.itemId <= 0 || cost.count <= 0 || !ids.add(cost.itemId))
                throw new IllegalStateException("Invalid forge payment row");
            costs.add(new ItemParamData(cost.itemId, Math.multiplyExact(cost.count, count)));
        }
        return costs;
    }

    public int getRefundPoints() {
        if (paymentVersion != 1
                || count <= 0
                || count != paidUnitsRemaining
                || paidForgePointsPerUnit < 0)
            throw new IllegalStateException("Invalid forge point receipt");
        return Math.multiplyExact(paidForgePointsPerUnit, count);
    }

    public void reserveRefund() {
        if (isBusy() || count <= 0)
            throw new IllegalStateException("Forge refund is not available");
        refundState = 1;
    }

    public void finishRefund() {
        if (refundState != 1) throw new IllegalStateException("Forge refund is not reserved");
        refundState = 2;
    }

    public int getOutputState() {
        return outputState;
    }

    public boolean isBusy() {
        return startState != 0 || outputState != 0 || refundState != 0;
    }

    public void reserveOutput(int finished) {
        if (isBusy()
                || finished <= 0
                || finished > count
                || forgeTime <= 0
                || paymentVersion == 1 && count != paidUnitsRemaining)
            throw new IllegalStateException("Forge output is not available");
        Math.toIntExact((long) startTime + (long) finished * forgeTime);
        reservedOutputCount = finished;
        outputState = 1;
    }

    public void releaseOutputReservation() {
        if (outputState != 1) throw new IllegalStateException("Forge output is not reserved");
        outputState = 0;
        reservedOutputCount = 0;
    }

    public void finishOutput() {
        if (outputState != 1 || reservedOutputCount <= 0 || reservedOutputCount > count)
            throw new IllegalStateException("Invalid forge output reservation");
        startTime = Math.toIntExact((long) startTime + (long) reservedOutputCount * forgeTime);
        count -= reservedOutputCount;
        if (paymentVersion == 1) paidUnitsRemaining -= reservedOutputCount;
        outputState = 2;
    }

    public void acknowledgeOutput() {
        if (outputState != 2) throw new IllegalStateException("Forge output is not delivered");
        outputState = 0;
        // Keep the last quantity for reconciliation if final acknowledgement fails.
    }

    public void restoreOutputPending() {
        if (outputState == 0) outputState = 2;
    }

    public int getStartState() {
        return startState;
    }

    public void reserveStart() {
        if (isBusy()) throw new IllegalStateException("Forge start is already reserved");
        startState = 1;
    }

    public void acknowledgeStart() {
        if (startState != 1) throw new IllegalStateException("Forge start is not reserved");
        startState = 0;
    }

    public int getFinishedCount(int currentTime) {
        if (startState != 0 || forgeTime <= 0 || count <= 0) return 0;
        long finishedCount = Math.max(0, (long) currentTime - startTime) / forgeTime;
        return (int) Math.min(finishedCount, count);
    }

    public int getUnfinishedCount(int currentTime) {
        return Math.max(0, count) - getFinishedCount(currentTime);
    }

    public int getNextFinishTimestamp(int currentTime) {
        long total = (long) startTime + (long) forgeTime * count;
        if (forgeTime <= 0 || count <= 0 || total < 0 || total > Integer.MAX_VALUE) return 0;
        return currentTime >= total
                ? (int) total
                : (int) ((long) startTime + (long) (getFinishedCount(currentTime) + 1) * forgeTime);
    }

    public int getTotalFinishTimestamp() {
        long total = (long) startTime + (long) forgeTime * count;
        return forgeTime <= 0 || count <= 0 || total < 0 || total > Integer.MAX_VALUE
                ? 0
                : (int) total;
    }

    public int getForgeId() {
        return this.forgeId;
    }

    public void setForgeId(int value) {
        this.forgeId = value;
    }

    public int getAvatarId() {
        return this.avatarId;
    }

    public void setAvatarId(int value) {
        this.avatarId = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int value) {
        this.count = value;
    }

    public int getStartTime() {
        return this.startTime;
    }

    public void setStartTime(int value) {
        this.startTime = value;
    }

    public int getForgeTime() {
        return this.forgeTime;
    }

    public void setForgeTime(int value) {
        this.forgeTime = value;
    }

    public boolean isChanged() {
        return this.changed;
    }

    public void setChanged(boolean value) {
        this.changed = value;
    }

    public boolean updateChanged(int currentTime) {
        int currentUnfinished = this.getUnfinishedCount(currentTime);

        if (currentUnfinished != this.lastUnfinishedCount) {
            this.changed = true;
            this.lastUnfinishedCount = currentUnfinished;
        }

        return this.changed;
    }
}
