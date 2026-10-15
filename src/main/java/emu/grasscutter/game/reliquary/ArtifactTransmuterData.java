package emu.grasscutter.game.reliquary;

import dev.morphia.annotations.Entity;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

/** Per-player transmuter state; the manager serializes changes with the player's inventory. */
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
    private long reshapeProgress;
    private Map<String, ArtifactReshapeCandidate> reshapeCandidates = new HashMap<>();

    public Map<String, ArtifactReshapeCandidate> getReshapeCandidates() {
        return reshapeCandidates == null ? Map.of() : reshapeCandidates;
    }

    public void setReshapeCandidates(Map<String, ArtifactReshapeCandidate> candidates) {
        reshapeCandidates = Map.copyOf(candidates);
    }

    public Map<Integer, Integer> getDefinedCounts() {
        return definedCounts == null ? Map.of() : definedCounts;
    }

    public void setDefinedCounts(Map<Integer, Integer> counts) {
        definedCounts = Map.copyOf(counts);
    }
}
