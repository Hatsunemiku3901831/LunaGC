package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;

import lombok.Getter;

/** A confirmed official performance; reward-claim IDs have a separate, unimplemented lifecycle. */
@Entity
@Getter
public final class MusicGameBookRecordData {
    private int maximumScore;
    private boolean fullCombo;

    public MusicGameBookRecordData() {}

    public MusicGameBookRecordData(int maximumScore, boolean fullCombo) {
        this.maximumScore = maximumScore;
        this.fullCombo = fullCombo;
    }
}
