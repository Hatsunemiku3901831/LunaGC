package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.tower.TowerScheduleData;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Serves one Spiral Abyss rotation at a time.
 *
 * <p>The rotation is chosen from what the resources can actually build rather than from a fixed id.
 * A schedule names twelve floors, each floor three chambers, each chamber a dungeon, and each
 * dungeon a scene; if that scene has no group scripts the chamber opens onto an empty room with
 * nothing to fight. Most of the newer rotations in the excel are in exactly that state, so the
 * playable ones are worked out once and only those enter the cycle.
 */
public class TowerSystem extends BaseGameSystem {

    /** Legacy private-server rotation cadence; historical dates come from the catalogue. */
    private static final int ROTATION_HOUR = 4;

    private TowerScheduleConfig towerScheduleConfig;
    private TowerPresetStore presetStore;
    private TowerCatalog catalog;

    public synchronized TowerPresetStore getPresetStore() {
        if (presetStore == null) {
            try { presetStore = new TowerPresetStore(FileUtils.getDataUserPath("TowerPresets.json")); }
            catch (IOException e) { throw new IllegalStateException("Cannot read TowerPresets.json", e); }
        }
        return presetStore;
    }

    public synchronized TowerCatalog getCatalog() {
        if (catalog == null) catalog = new TowerCatalog();
        return catalog;
    }

    public TowerBattleProfile getBattleProfile() { return getPresetStore().get().profile(); }

    public record Selection(TowerBattleProfile profile, List<Integer> floors) {}

    public synchronized Selection captureSelection() {
        return new Selection(getBattleProfile(), List.copyOf(getAllFloors()));
    }

    public synchronized TowerPresetStore.State applyProfile(long revision, TowerBattleProfile profile) throws IOException {
        getCatalog().validate(profile);
        if (!getPlayableSchedules().contains(profile.scheduleId()))
            throw new IllegalArgumentException("排期未通过当前资源依赖检查。");
        return getPresetStore().save(revision, profile);
    }

    /**
     * Rotations whose every chamber resolves to a scripted scene, ascending. Resolved on first use,
     * never in the constructor: the game server is built before {@code ResourceLoader.loadAll()}
     * runs, so the excel maps are still empty at that point.
     */
    private List<Integer> playableSchedules;

    /** Answers per scene, since one rotation asks about the same scenes repeatedly. */
    private final Map<Integer, Boolean> scriptedScenes = new HashMap<>();

    public TowerSystem(GameServer server) {
        super(server);
        this.load();
    }

    public synchronized void load() {
        playableSchedules = null;
        scriptedScenes.clear();
        try {
            towerScheduleConfig = DataLoader.loadClass("TowerSchedule.json", TowerScheduleConfig.class);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load tower schedule config.", e);
        }
    }

    public TowerScheduleConfig getTowerScheduleConfig() {
        return towerScheduleConfig;
    }

    // region Rotation

    /** Whether the scene behind a chamber holds anything to fight. */
    private boolean isScripted(int sceneId) {
        return scriptedScenes.computeIfAbsent(sceneId,
                id -> TowerSceneResources.complete(FileUtils.getScriptPath(""), id));
    }

    /** Every floor a rotation can put a player on, corridor and schedule floors alike. */
    private static List<Integer> floorsOf(TowerScheduleData schedule) {
        var floors = new ArrayList<Integer>();
        if (schedule.getEntranceFloorId() != null) floors.addAll(schedule.getEntranceFloorId());
        if (schedule.getSchedules() != null) {
            schedule.getSchedules().stream()
                    .filter(Objects::nonNull)
                    .map(TowerScheduleData.ScheduleDetail::getFloorList)
                    .filter(Objects::nonNull)
                    .forEach(floors::addAll);
        }
        return floors;
    }

    /** True when every chamber of every floor leads somewhere with monsters in it. */
    private boolean isPlayable(TowerScheduleData schedule) {
        var floors = floorsOf(schedule);
        if (floors.isEmpty()) return false;

        for (int floorId : floors) {
            var floorData = GameData.getTowerFloorDataMap().get(floorId);
            if (floorData == null) return false;

            var levels =
                    GameData.getTowerLevelDataMap().values().stream()
                            .filter(level -> level.getLevelGroupId() == floorData.getLevelGroupId())
                            .toList();
            if (levels.size() != 3) return false;
            var ordered = levels.stream().sorted(java.util.Comparator.comparingInt(l -> l.getLevelIndex())).toList();
            for (int i = 0; i < 3; i++) {
                if (ordered.get(i).getLevelIndex() != i + 1
                        || ordered.get(i).getLevelId() != ordered.get(0).getLevelId() + i) return false;
            }

            for (var level : levels) {
                var dungeon = GameData.getDungeonDataMap().get(level.getDungeonId());
                if (dungeon == null || !isScripted(dungeon.getSceneId())) return false;
            }
        }
        return true;
    }

    /** Playable rotations, ascending. Worked out once, after the resources are in. */
    public synchronized List<Integer> getPlayableSchedules() {
        if (playableSchedules != null) return playableSchedules;

        var all = GameData.getTowerScheduleDataMap();
        if (all.isEmpty()) return List.of(); // Resources are not in yet; do not cache that.

        var playable =
                all.values().stream()
                        .filter(this::isPlayable)
                        .map(TowerScheduleData::getScheduleId)
                        .sorted()
                        .toList();

        if (playable.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "No Spiral Abyss rotation is playable: every one names dungeon scenes this server has no group scripts for.");
        } else {
            Grasscutter.getLogger()
                    .info(
                            "Spiral Abyss: {} of {} rotations are playable, newest is {}.",
                            playable.size(),
                            all.size(),
                            playable.get(playable.size() - 1));
        }

        playableSchedules = playable;
        return playableSchedules;
    }

    /** The rotations actually cycled through - the newest {@code rotationPool} playable ones. */
    private List<Integer> getRotationPool() {
        var playable = getPlayableSchedules();
        int pool = GAME_OPTIONS.tower.rotationPool;
        if (pool <= 0 || pool >= playable.size()) return playable;
        return playable.subList(playable.size() - pool, playable.size());
    }

    /** The newest rotation this server can actually build, or 0 if none can be. */
    private int getNewestPlayable() {
        var playable = getPlayableSchedules();
        return playable.isEmpty() ? 0 : playable.get(playable.size() - 1);
    }

    /** Start of the rotation period holding {@code now}: the 1st or the 16th, at 04:00. */
    private static Calendar periodStart(Date now) {
        var calendar = Calendar.getInstance();
        calendar.setTime(now);

        // The hours before 04:00 on a changeover day still belong to the rotation going out.
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        if ((day == 1 || day == 16) && calendar.get(Calendar.HOUR_OF_DAY) < ROTATION_HOUR) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            day = calendar.get(Calendar.DAY_OF_MONTH);
        }

        calendar.set(Calendar.DAY_OF_MONTH, day >= 16 ? 16 : 1);
        calendar.set(Calendar.HOUR_OF_DAY, ROTATION_HOUR);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static Calendar periodEnd(Calendar start) {
        var calendar = (Calendar) start.clone();
        if (calendar.get(Calendar.DAY_OF_MONTH) >= 16) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.add(Calendar.MONTH, 1);
        } else {
            calendar.set(Calendar.DAY_OF_MONTH, 16);
        }
        return calendar;
    }

    /** Half-months since year zero, so consecutive periods pick consecutive rotations. */
    private static long periodOrdinal(Calendar start) {
        return start.get(Calendar.YEAR) * 24L
                + start.get(Calendar.MONTH) * 2L
                + (start.get(Calendar.DAY_OF_MONTH) >= 16 ? 1 : 0);
    }

    /** Whether the rotation moves on its own, rather than staying on the newest playable one. */
    private static boolean isRotating() {
        return GAME_OPTIONS.tower.scheduleId <= 0 && GAME_OPTIONS.tower.rotate;
    }

    /** When the rotation on offer began. */
    public Date getScheduleStartTime() {
        if (getBattleProfile().scheduleId() > 0 || !isRotating()) return towerScheduleConfig.getScheduleStartTime();
        return periodStart(new Date()).getTime();
    }

    /** When it gives way to the next one. The client counts down to this. */
    public Date getNextScheduleChangeTime() {
        if (getBattleProfile().scheduleId() > 0 || !isRotating()) return towerScheduleConfig.getNextScheduleChangeTime();
        return periodEnd(periodStart(new Date())).getTime();
    }

    // endregion

    public TowerScheduleData getCurrentTowerScheduleData() {
        // Both operator presets and legacy pins must pass the resource dependency check.
        int managed = getBattleProfile().scheduleId();
        int pinned = managed > 0 ? managed : GAME_OPTIONS.tower.scheduleId;
        if (pinned > 0) {
            var data = GameData.getTowerScheduleDataMap().get(pinned);
            if (data != null && getPlayableSchedules().contains(pinned)) return data;
            Grasscutter.getLogger()
                    .error("Spiral Abyss rotation {} is missing or incomplete; refusing this selection.", pinned);
            return null;
        }

        if (managed <= 0 && isRotating()) {
            var pool = getRotationPool();
            if (!pool.isEmpty()) {
                int index = (int) Math.floorMod(periodOrdinal(periodStart(new Date())), pool.size());
                var data = GameData.getTowerScheduleDataMap().get(pool.get(index).intValue());
                if (data != null) return data;
            }
        } else {
            // The newest rotation the resources can build is as close to the live game as this
            // server gets; anything older is a step backwards from it.
            var newest = getNewestPlayable();
            if (newest > 0) {
                var data = GameData.getTowerScheduleDataMap().get(newest);
                if (data != null) return data;
            }
        }

        // The legacy fallback is also required to have complete scene dependencies.
        var fallback = GameData.getTowerScheduleDataMap().get(towerScheduleConfig.getScheduleId());
        if (fallback != null && !isPlayable(fallback)) fallback = null;
        if (fallback == null) {
            Grasscutter.getLogger()
                    .error(
                            "Could not get current tower schedule data by schedule id {}, please check your resource files",
                            towerScheduleConfig.getScheduleId());
        }
        return fallback;
    }

    public List<Integer> getAllFloors() {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null) return List.of();

        List<Integer> floors = new ArrayList<>(schedule.getEntranceFloorId());
        floors.addAll(this.getScheduleFloors());
        return floors;
    }

    public List<Integer> getScheduleFloors() {
        var schedule = this.getCurrentTowerScheduleData();
        // TowerScheduleData.onLoad drops the empty slots, so the first one left is the live half.
        if (schedule == null || schedule.getSchedules().isEmpty()) return List.of();
        return schedule.getSchedules().get(0).getFloorList();
    }

    public int getNextFloorId(int floorId) {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null) return 0;

        var entranceFloors = schedule.getEntranceFloorId();
        var scheduleFloors = getScheduleFloors();
        var nextId = 0;

        // find in entrance floors first
        for (int i = 0; i < entranceFloors.size() - 1; i++) {
            if (floorId == entranceFloors.get(i)) {
                nextId = entranceFloors.get(i + 1);
            }
        }

        if (!entranceFloors.isEmpty()
                && floorId == entranceFloors.get(entranceFloors.size() - 1)
                && !scheduleFloors.isEmpty()) {
            nextId = scheduleFloors.get(0);
        }

        if (nextId != 0) {
            return nextId;
        }

        // find in schedule floors
        for (int i = 0; i < scheduleFloors.size() - 1; i++) {
            if (floorId == scheduleFloors.get(i)) {
                nextId = scheduleFloors.get(i + 1);
            }
        }
        return nextId;
    }

    public Integer getLastEntranceFloor() {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null || schedule.getEntranceFloorId().isEmpty()) return 0;
        return schedule.getEntranceFloorId().get(schedule.getEntranceFloorId().size() - 1);
    }
}
