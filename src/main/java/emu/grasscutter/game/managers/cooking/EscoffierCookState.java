package emu.grasscutter.game.managers.cooking;

import dev.morphia.annotations.Entity;

import lombok.Getter;

/** Embedded player quota. The timestamp keeps the uint32 protocol bits in a Java int. */
@Entity
@Getter
public final class EscoffierCookState {
    private int format = 1;
    private int lastRefreshTime;
    private int finishedWeeklyCookNum;

    public EscoffierCookState() {}

    EscoffierCookState(int lastRefreshTime, int finishedWeeklyCookNum) {
        this.lastRefreshTime = lastRefreshTime;
        this.finishedWeeklyCookNum = finishedWeeklyCookNum;
    }
}
