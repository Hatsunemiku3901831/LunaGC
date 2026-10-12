package emu.grasscutter.game.managers.forging;

import dev.morphia.annotations.Entity;

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

    public int getStartState() {
        return startState;
    }

    public void reserveStart() {
        if (startState != 0) throw new IllegalStateException("Forge start is already reserved");
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
