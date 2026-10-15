package emu.grasscutter.game.reliquary;

import dev.morphia.annotations.Entity;

import lombok.Getter;
import lombok.Setter;

/** Per-player extraction progress; the manager serializes changes with the player's inventory. */
@Entity
@Getter
@Setter
public final class ArtifactTransmuterData {
    private int scheduleId;
    private long cycleEndTime;
    private long progressUnits;
    private int extractedCount;
    private ArtifactTransmuterReceipt pending;
}
