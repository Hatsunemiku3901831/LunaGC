package emu.grasscutter.game.reliquary;

import dev.morphia.annotations.Entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** The plan is durable before payment; transient inventory GUIDs are never used as storage keys. */
@Entity
@Getter
@Setter
public final class ArtifactReshapeCandidate {
    public enum Phase {
        RESERVED,
        READY,
        ADOPTING,
        KEEPING
    }

    private String itemObjectId;
    private String operationId;
    private String materialObjectId;
    private int materialId;
    private int materialBefore;
    private int itemId;
    private int level;
    private int mainPropId;
    private int cost;
    private int tier;
    private long progressBefore;
    private long progressAfter;
    private List<Integer> original = List.of();
    private List<Integer> result = List.of();
    private List<Integer> selected = List.of();
    private Phase phase;
}
