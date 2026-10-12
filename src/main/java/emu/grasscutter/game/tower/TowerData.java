package emu.grasscutter.game.tower;

import dev.morphia.annotations.*;

import java.util.*;

@Entity
public class TowerData {
    /** the floor players chose */
    int currentFloorId;

    int currentLevel;
    @Transient int currentLevelId;

    /** floorId - Record */
    Map<Integer, TowerLevelRecord> recordMap;

    @Transient int entryScene;

    private List<List<Long>> abyssTeamGuids = new ArrayList<>();

    public List<List<Long>> getAbyssTeamGuids() {
        if (abyssTeamGuids == null) return List.of();
        var result = new ArrayList<List<Long>>(abyssTeamGuids.size());
        for (var team : abyssTeamGuids) {
            result.add(team == null ? null : Collections.unmodifiableList(new ArrayList<>(team)));
        }
        return Collections.unmodifiableList(result);
    }

    void rememberAbyssTeams(List<List<Long>> teams) {
        var result = new ArrayList<List<Long>>(teams.size());
        for (var team : teams) result.add(new ArrayList<>(team));
        this.abyssTeamGuids = result;
    }
}
