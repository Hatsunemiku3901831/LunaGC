package emu.grasscutter.game.managers.cooking;

import dev.morphia.annotations.Entity;

import lombok.Getter;

@Entity
public class ActiveCookCompoundData {
    private final int costTime;
    @Getter private final int compoundId;
    @Getter private int totalCount;
    private int startTime;
    // Persisted receipt: 0 available, 1 reserved, 2 delivered before cleanup confirmation.
    @Getter private int claimState;
    private int reservedCount;

    public ActiveCookCompoundData(int compoundId, int processTime, int count, int startTime) {
        this.compoundId = compoundId;
        this.costTime = processTime;
        this.totalCount = count;
        this.startTime = startTime;
    }

    public int getOutputCount(int currentTime) {
        // costTime comes from resource JSON, and this is a persisted @Entity, so a stored document
        // written without the field deserialises it to 0. Either way, dividing would throw.
        if (costTime < 0) return 0;
        if (costTime == 0) return totalCount;

        long cnt = ((long) currentTime - startTime) / costTime;
        if (cnt > totalCount) return totalCount;
        else return (int) Math.max(cnt, 0);
    }

    public int getWaitCount(int currentTime) {
        return totalCount - getOutputCount(currentTime);
    }

    /** Get the timestamp of next output. If all finished,return 0 */
    public int getOutputTime(int currentTime) {
        int cnt = getOutputCount(currentTime);
        if (cnt == totalCount) return 0;
        else return startTime + (cnt + 1) * costTime;
    }

    public void addCompound(int count, int currentTime) {
        if (count <= 0 || claimState != 0 || (long) totalCount + count > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid compound append");
        if (getOutputCount(currentTime) == totalCount)
            startTime = Math.toIntExact((long) currentTime - (long) totalCount * costTime);
        totalCount = Math.addExact(totalCount, count);
    }

    public ActiveCookCompoundData copyWithAppend(int count, int resourceCostTime, int currentTime) {
        if (costTime < 0 || resourceCostTime < 0 || claimState != 0)
            throw new IllegalArgumentException("Invalid compound queue");
        int processingTime = costTime == 0 ? resourceCostTime : costTime;
        int started =
                costTime == 0 && processingTime > 0
                        ? Math.toIntExact((long) currentTime - (long) totalCount * processingTime)
                        : startTime;
        var copy = new ActiveCookCompoundData(compoundId, processingTime, totalCount, started);
        copy.addCompound(count, currentTime);
        return copy;
    }

    public void reserveClaim(int count) {
        if (claimState != 0 || count <= 0 || count > totalCount)
            throw new IllegalStateException("Compound claim is not available");
        reservedCount = count;
        claimState = 1;
    }

    public void finishClaim() {
        if (claimState != 1 || reservedCount <= 0 || reservedCount > totalCount)
            throw new IllegalStateException("Invalid compound reservation");
        startTime = Math.toIntExact((long) startTime + (long) costTime * reservedCount);
        totalCount -= reservedCount;
        claimState = 2;
    }

    public void acknowledgeClaim() {
        if (claimState != 2)
            throw new IllegalStateException("Compound delivery is not acknowledged");
        claimState = 0;
        reservedCount = 0;
    }

    /**
     * Take away all finished compound.
     *
     * @return The number of finished items.
     */
    public int takeCompound(int currentTime) {
        int count = getOutputCount(currentTime);
        startTime += costTime * count;
        totalCount -= count;
        return count;
    }
}
