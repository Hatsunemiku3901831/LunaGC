package emu.grasscutter.game.reliquary;

import dev.morphia.annotations.Entity;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

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
    private Map<Integer, Integer> definedCounts = new HashMap<>();

    public Map<Integer, Integer> getDefinedCounts() {
        return definedCounts == null ? Map.of() : definedCounts;
    }

    public void setDefinedCounts(Map<Integer, Integer> counts) {
        definedCounts = Map.copyOf(counts);
    }
}
