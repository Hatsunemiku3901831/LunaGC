package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;

import lombok.Getter;

/**
 * Best performance and standard clear; the latter shares a Player write with first-clear rewards.
 */
@Entity
@Getter
public final class MusicGameBookRecordData {
    private int maximumScore;
    private boolean fullCombo;
    private boolean normalCompleted;

    public MusicGameBookRecordData() {}

    public MusicGameBookRecordData(int maximumScore, boolean fullCombo) {
        this(maximumScore, fullCombo, false);
    }

    public MusicGameBookRecordData(int maximumScore, boolean fullCombo, boolean normalCompleted) {
        this.maximumScore = maximumScore;
        this.fullCombo = fullCombo;
        this.normalCompleted = normalCompleted;
    }
}
