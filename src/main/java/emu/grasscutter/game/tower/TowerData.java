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
    private List<List<Integer>> abyssAvatarIds = new ArrayList<>();

    public List<List<Long>> getAbyssTeamGuids() {
        return snapshot(abyssTeamGuids);
    }

    public List<List<Integer>> getAbyssAvatarIds() {
        return snapshot(abyssAvatarIds);
    }

    private static <T> List<List<T>> snapshot(List<List<T>> teams) {
        if (teams == null) return List.of();
        var result = new ArrayList<List<T>>(teams.size());
        for (var team : teams) {
            result.add(team == null ? null : Collections.unmodifiableList(new ArrayList<>(team)));
        }
        return Collections.unmodifiableList(result);
    }

    void rememberAbyssTeams(List<List<Long>> teams, List<List<Integer>> avatarIds) {
        var result = new ArrayList<List<Long>>(teams.size());
        for (var team : teams) result.add(new ArrayList<>(team));
        var identities = new ArrayList<List<Integer>>(avatarIds.size());
        for (var team : avatarIds) identities.add(new ArrayList<>(team));
        this.abyssTeamGuids = result;
        this.abyssAvatarIds = identities;
    }
}
