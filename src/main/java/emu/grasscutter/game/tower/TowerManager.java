package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import lombok.*;

public class TowerManager extends BasePlayerManager {
    private static final List<DungeonSettleListener> towerDungeonSettleListener =
            List.of(new TowerDungeonSettleListener());

    private int currentPossibleStars = 0;
    @Getter private boolean inProgress;
    @Getter private int currentTimeLimit;
    private volatile HalfTime halfTime;
    private TowerBattleProfile battleProfile;
    private List<Integer> selectedFloors;

    public TowerBattleProfile getBattleProfile() {
        return battleProfile == null ? player.getServer().getTowerSystem().getBattleProfile() : battleProfile;
    }
    private record HalfTime(
            Scene scene,
            DungeonManager manager,
            int floor,
            int level,
            List<List<Integer>> parties,
            WorldChallenge upper,
            int seconds) {}

    public TowerManager(Player player) {
        super(player);
    }

    public TowerData getTowerData() {
        return this.getPlayer().getTowerData();
    }

    public int getCurrentFloorId() {
        return this.getTowerData().currentFloorId;
    }

    /** floor number: 1 - 12, or 0 when no floor is selected * */
    public int getCurrentFloorNumber() {
        // EntityMonster.recalcStats reads this while scaling tower monsters, and it runs from the
        // constructor - early enough that no floor need be chosen yet. 0 simply scales nothing.
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        return floorData != null ? floorData.getFloorIndex() : 0;
    }

    public int getCurrentLevelId() {
        return this.getTowerData().currentLevelId + this.getTowerData().currentLevel;
    }

    /** form 1-3 */
    public int getCurrentLevel() {
        return this.getTowerData().currentLevel + 1;
    }

    public void onTick() {
        var challenge = player.getScene().getChallenge();
        if (!inProgress || challenge == null || !challenge.inProgress()) return;

        // Check star conditions and notify client if any failed.
        int stars = getCurLevelStars();
        while (stars < currentPossibleStars) {
            player
                    .getSession()
                    .send(
                            new PacketTowerLevelStarCondNotify(
                                    getTowerData().currentFloorId, getCurrentLevel(), currentPossibleStars));
            currentPossibleStars--;
        }
    }

    public void onBegin() {
        // onTick() already treats a missing scene challenge as normal; this re-reads it from the
        // scene rather than using the one that triggered the call, so it can be null here too.
        var challenge = player.getScene().getChallenge();
        inProgress = true;
        currentTimeLimit = challenge != null ? challenge.getTimeLimit() : 0;
        var clock = halfTime;
        if (challenge != null
                && challenge.getTimeLimit() > 0
                && (player.getTeamManager().getTemporaryTeamIndex() == 0 || matchesHalfTime(clock)))
            halfTime = null;

        // Skills are NOT re-enabled here: the floor's own Lua owns that. It holds them off across
        // the chamber change and switches them back on from the worktop the player starts the half
        // with (SetIsAllowUseSkill(1) in the EVENT_SELECT_OPTION action). Sending it from here
        // races that and turns them on while the script means them off.

        this.captureChamberState();
    }

    public void onEnd() {
        inProgress = false;
        halfTime = null;
    }

    /** Capture upper-half remaining time before its success callback can switch parties. */
    public void recordHalfTime(WorldChallenge challenge, int remaining) {
        var scene = player.getScene();
        var manager = scene == null ? null : scene.getDungeonManager();
        var floor = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        var level = getCurrentTowerLevelDataMap();
        var parties = getTowerData().getAbyssAvatarIds();
        if (remaining < 0
                || challenge == null
                || challenge.getScene() != scene
                || scene.getChallenge() != challenge
                || !challenge.isSuccess()
                || manager == null
                || !manager.isTowerDungeon()
                || manager.getDungeonData() == null
                || scene.getPlayers().size() != 1
                || scene.getPlayers().get(0) != player
                || floor == null
                || floor.getTeamNum() != 2
                || level == null
                || level.getDungeonId() != manager.getDungeonData().getId()
                || player.getTeamManager().getTemporaryTeamIndex() != 0
                || parties.size() != 2
                || !parties.equals(player.getTeamManager().getTemporaryTeamAvatarIds())) return;
        halfTime =
                new HalfTime(
                        scene,
                        manager,
                        getCurrentFloorId(),
                        getCurrentLevelId(),
                        parties,
                        challenge,
                        remaining);
    }

    private boolean matchesHalfTime(HalfTime clock) {
        var scene = player.getScene();
        return clock != null
                && scene == clock.scene()
                && scene.getDungeonManager() == clock.manager()
                && clock.manager().isTowerDungeon()
                && getCurrentFloorId() == clock.floor()
                && getCurrentLevelId() == clock.level()
                && getTowerData().getAbyssAvatarIds().equals(clock.parties())
                && player.getTeamManager().getTemporaryTeamAvatarIds().equals(clock.parties());
    }

    /** A zero budget must not turn a timed lower half into an unlimited challenge. */
    public int resolveHalfTime(int luaSeconds) {
        var clock = halfTime;
        if (player.getTeamManager().getTemporaryTeamIndex() == 1
                && matchesHalfTime(clock)
                && player.getScene().getChallenge() == clock.upper())
            return clock.seconds() > 0 ? clock.seconds() : -1;
        return luaSeconds;
    }

    private static final int LEVELS_PER_FLOOR = 3;
    private static final int STARS_PER_LEVEL = 3;
    private static final int STARS_PER_FLOOR = LEVELS_PER_FLOOR * STARS_PER_LEVEL;

    public Map<Integer, TowerLevelRecord> getRecordMap() {
        Map<Integer, TowerLevelRecord> recordMap = getTowerData().recordMap;
        if (recordMap == null) {
            recordMap = new HashMap<>();
            getTowerData().recordMap = recordMap;
        }
        grantEntranceFloors(recordMap);
        return recordMap;
    }

    /**
     * Hands over entrance floors 1-8 already cleared, so an account starts on the floors that
     * actually rotate.
     *
     * <p>The schedule floors are gated twice: TowerAllDataRsp reports {@code
     * is_finished_entrance_floor} from {@link #canEnterScheduleFloor()}, which wants six stars on
     * the last entrance floor, and each floor's own {@code unlockStarCount} wants six stars on the
     * one before it. Full nine-star records on every entrance floor satisfy both. Turn
     * {@code game.tower.skipEntranceFloors} off to play floors 1-8 for real.
     */
    private void grantEntranceFloors(Map<Integer, TowerLevelRecord> recordMap) {
        if (!GAME_OPTIONS.tower.skipEntranceFloors) return;

        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        if (schedule == null) return;

        var entranceFloors = schedule.getEntranceFloorId();
        if (entranceFloors == null || entranceFloors.isEmpty()) return;

        for (int floorId : entranceFloors) {
            // Levels within a floor are consecutive ids from levelIndex 1, which is the same
            // assumption getCurrentLevelId() makes when it walks the floor.
            int firstLevelId = getFirstLevelId(floorId);
            if (firstLevelId == 0) continue;

            var record = recordMap.computeIfAbsent(floorId, TowerLevelRecord::new);
            if (record.getPassedLevelMap() == null) {
                // A record loaded from a save written before the map existed.
                record.setPassedLevelMap(new HashMap<>());
            }

            // Drop chambers that do not belong to this floor. The old /setprop towerlevel faked the
            // unlock by writing chamber id 0 with six stars, and a save that still carries it would
            // report a chamber that does not exist to the client in passed_level_map.
            record
                    .getPassedLevelMap()
                    .keySet()
                    .removeIf(id -> id < firstLevelId || id >= firstLevelId + LEVELS_PER_FLOOR);

            if (record.getStarCount() >= STARS_PER_FLOOR) continue;

            for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
                record.setLevelStars(firstLevelId + i, STARS_PER_LEVEL);
                // These stars were never played for, so their first-pass reward is forfeit -
                // otherwise handing over floors 1-8 would also mail every chamber's loot.
                record.markLevelRewarded(firstLevelId + i);
            }
            record.setFloorStarRewardProgress(STARS_PER_FLOOR);
        }

        // Clearing a floor also opens the next one by giving it an empty record - that is what
        // notifyCurLevelRecordChangeWhenDone does every time. Granting the stars without it leaves
        // the floor after the corridor with no record at all, which is not a state the game can
        // otherwise reach, and the client shows it locked.
        int firstScheduleFloor =
                player
                        .getServer()
                        .getTowerSystem()
                        .getNextFloorId(entranceFloors.get(entranceFloors.size() - 1));
        if (firstScheduleFloor > 0) {
            recordMap.computeIfAbsent(firstScheduleFloor, TowerLevelRecord::new);
        }
    }

    /** Id of a floor's first chamber, or 0 if the resources do not describe the floor. */
    private static int getFirstLevelId(int floorId) {
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) return 0;
        return GameData.getTowerLevelDataMap().values().stream()
                .filter(x -> x.getLevelGroupId() == floorData.getLevelGroupId() && x.getLevelIndex() == 1)
                .findFirst()
                .map(TowerLevelData::getId)
                .orElse(0);
    }

    public void teamSelect(int floor, List<List<Long>> towerTeams) {
        tryTeamSelect(floor, towerTeams);
    }

    public boolean tryTeamSelect(int floor, List<List<Long>> towerTeams) {
        synchronized (player.getSceneTransferLock()) {
            var floorData = GameData.getTowerFloorDataMap().get(floor);
            if (floorData == null
                    || towerTeams == null
                    || floorData.getTeamNum() <= 0
                    || towerTeams.size() != floorData.getTeamNum()) {
                Grasscutter.getLogger()
                        .warn(
                                "Tower team selection rejected for floor {}: missing floor or"
                                        + " incorrect team count",
                                floor);
                return false;
            }
            var firstLevel = getFirstLevelId(floor);
            var selection = player.getServer().getTowerSystem().captureSelection();
            if (!selection.floors().contains(floor))
                return false;
            if (firstLevel <= 0 || !player.getTeamManager().trySetupTemporaryTeam(towerTeams))
                return false;
            battleProfile = selection.profile();
            selectedFloors = selection.floors();
            getTowerData().currentFloorId = floorData.getFloorId();
            getTowerData().currentLevel = 0;
            getTowerData().currentLevelId = firstLevel;
            getTowerData().chamberState = null;
            halfTime = null;
            getTowerData()
                    .rememberAbyssTeams(
                            towerTeams, player.getTeamManager().getTemporaryTeamAvatarIds());

            if (getTowerData().entryScene == 0) {
                getTowerData().entryScene = player.getSceneId();
            }

            // The teams the client picked are the whole point of this packet, and every way they
            // can go
            // missing looks identical in game - the overworld team just walks in instead. Say what
            // arrived: no teams at all means the request did not carry them, whereas teams that
            // arrive
            // and then get refused are reported by setupTemporaryTeam.
            Grasscutter.getLogger()
                    .info(
                            "Tower team select on floor {}: {} team(s), sizes {}",
                            floor,
                            towerTeams.size(),
                            towerTeams.stream().map(List::size).toList());

            player.save();
            return true;
        }
    }

    public List<List<Long>> getAbyssTeamGuids() {
        return getTowerData().getAbyssTeamGuids();
    }

    public boolean captureChamberState() {
        synchronized (player.getSceneTransferLock()) {
            if (!restoreTemporaryTeams()) return false;
            var data = getTowerData();
            if (data.chamberState != null
                    && data.chamberState.matches(
                            getCurrentFloorId(), getCurrentLevelId(), data.getAbyssAvatarIds()))
                return true;
            var state =
                    TowerChamberState.capture(
                            player,
                            getCurrentFloorId(),
                            getCurrentLevelId(),
                            data.getAbyssAvatarIds());
            if (state == null) return false;
            data.chamberState = state;
            player.save();
            return true;
        }
    }

    public boolean restoreChamberState() {
        synchronized (player.getSceneTransferLock()) {
            var state = getTowerData().chamberState;
            return state != null
                    && state.restore(
                            player,
                            getCurrentFloorId(),
                            getCurrentLevelId(),
                            getTowerData().getAbyssAvatarIds());
        }
    }

    public boolean canRestartChamber(DungeonData dungeon) {
        var scene = player.getScene();
        var manager = scene == null ? null : scene.getDungeonManager();
        var floor = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        var level = GameData.getTowerLevelDataMap().get(getCurrentLevelId());
        var data = getTowerData();
        return dungeon != null
                && manager != null
                && manager.isTowerDungeon()
                && manager.getDungeonData() == dungeon
                && floor != null
                && level != null
                && level.getLevelGroupId() == floor.getLevelGroupId()
                && level.getDungeonId() == dungeon.getId()
                && data.getAbyssAvatarIds()
                        .equals(player.getTeamManager().getTemporaryTeamAvatarIds())
                && data.chamberState != null
                && data.chamberState.canRestore(
                        player, getCurrentFloorId(), getCurrentLevelId(), data.getAbyssAvatarIds());
    }

    public boolean tryRestartChamber(DungeonData dungeon, BooleanSupplier enter) {
        synchronized (player.getSceneTransferLock()) {
            if (enter == null || !restoreTemporaryTeams() || !canRestartChamber(dungeon))
                return false;
            if (!player.getTeamManager().enterSceneWithTemporaryTeam(0, enter)) return false;
            if (!restoreChamberState()) return false;
            var avatars =
                    getTowerData().getAbyssAvatarIds().stream()
                            .flatMap(Collection::stream)
                            .map(id -> player.getAvatars().getAvatarById(id))
                            .toList();
            if (!player.getAbilityManager().getSkillCooldown().reset(avatars)) return false;
            var groups = new HashSet<Integer>();
            for (var avatar : avatars) {
                var ids = new HashSet<>(avatar.getSkillInfoCharges().keySet());
                var depot = avatar.getSkillDepot();
                if (depot != null) {
                    depot.getSkillsAndEnergySkill().forEach(ids::add);
                    if (depot.getSubSkills() != null) ids.addAll(depot.getSubSkills());
                }
                for (var id : ids) {
                    var skill = id == null ? null : GameData.getAvatarSkillDataMap().get(id);
                    if (skill != null && skill.getShareCDID() > 0) groups.add(skill.getShareCDID());
                }
            }
            groups.forEach(id -> player.getAbilityManager().getSharedCooldown().clear(id));
            inProgress = false;
            currentTimeLimit = 0;
            halfTime = null;
            currentPossibleStars = STARS_PER_LEVEL;
            player.sendPacket(new PacketCanUseSkillNotify(false));
            notifyCurLevelRecordChange();
            player.sendPacket(
                    new PacketTowerLevelStarCondNotify(getCurrentFloorId(), getCurrentLevel(), 4));
            player.save();
            return true;
        }
    }

    public boolean restoreTemporaryTeams() {
        synchronized (player.getSceneTransferLock()) {
            var data = getTowerData();
            var floor = GameData.getTowerFloorDataMap().get(data.currentFloorId);
            if (floor == null
                    || floor.getTeamNum() <= 0
                    || data.currentLevel < 0
                    || data.currentLevel >= LEVELS_PER_FLOOR) return false;
            int firstLevel = getFirstLevelId(data.currentFloorId);
            var level = GameData.getTowerLevelDataMap().get(firstLevel + data.currentLevel);
            if (firstLevel <= 0
                    || level == null
                    || level.getLevelGroupId() != floor.getLevelGroupId()) return false;
            var selected = getAbyssTeamGuids();
            var identities = data.getAbyssAvatarIds();
            boolean migrate = identities.isEmpty();
            if (migrate) {
                var live = player.getTeamManager().getTemporaryTeamGuids();
                if (!selected.isEmpty() && !selected.equals(live)) return false;
                selected = live;
            } else {
                var rebound = new ArrayList<List<Long>>(identities.size());
                for (var team : identities) {
                    if (team == null || team.isEmpty()) return false;
                    var guids = new ArrayList<Long>(team.size());
                    for (var id : team) {
                        var avatar = id == null ? null : player.getAvatars().getAvatarById(id);
                        if (avatar == null) return false;
                        guids.add(avatar.getGuid());
                    }
                    rebound.add(guids);
                }
                selected = rebound;
            }
            if (selected.size() != floor.getTeamNum()
                    || !player.getTeamManager().trySetupTemporaryTeam(selected)) return false;
            data.currentLevelId = firstLevel;
            if (migrate || !selected.equals(getAbyssTeamGuids())) {
                data.rememberAbyssTeams(
                        selected, player.getTeamManager().getTemporaryTeamAvatarIds());
                player.save();
            }
            return true;
        }
    }

    public TowerLevelData getCurrentTowerLevelDataMap() {
        return GameData.getTowerLevelDataMap().get(getCurrentLevelId());
    }

    public int getCurrentMonsterLevel() {
        // monsterLevel given in TowerLevelExcelConfigData.json is off by one.
        var levelData = getCurrentTowerLevelDataMap();
        if (levelData != null) {
            return levelData.getMonsterLevel() + 1;
        }
        // Spawning is not worth aborting over a missing row; the floor's own override is the same
        // number the client shows for the floor.
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        Grasscutter.getLogger()
                .warn("No tower level data for level {}, falling back to the floor level", getCurrentLevelId());
        return floorData != null ? floorData.getOverrideMonsterLevel() : 1;
    }

    public void enterLevel(int enterPointId) {
        tryEnterLevel(enterPointId);
    }

    public boolean tryEnterLevel(int enterPointId) {
        synchronized (player.getSceneTransferLock()) {
            var source = player.getScene();
            if (source == null || player.getWorld() == null || !restoreTemporaryTeams())
                return rejectEntry();
            var levelData = getCurrentTowerLevelDataMap();
            if (levelData == null
                    || !GameData.getDungeonDataMap().containsKey(levelData.getDungeonId()))
                return rejectEntry();
            int previousScene =
                    getTowerData().entryScene > 0 ? getTowerData().entryScene : source.getId();
            boolean entered =
                    player.getTeamManager()
                            .enterSceneWithTemporaryTeam(
                                    0,
                                    () ->
                                            player.getServer()
                                                    .getDungeonSystem()
                                                    .handoffDungeon(
                                                            player,
                                                            levelData.getDungeonId(),
                                                            towerDungeonSettleListener));
            if (!entered) return rejectEntry();
            inProgress = false;
            currentTimeLimit = 0;
            halfTime = null;
            player.getScene().setPrevScene(previousScene);
            player.getScene().setPrevScenePoint(enterPointId);
            captureChamberState();
            notifyCurLevelRecordChange();
            player.getSession()
                    .send(new PacketTowerEnterLevelRsp(getCurrentFloorId(), getCurrentLevel()));
            player.getSession().send(new PacketCanUseSkillNotify(false));
            currentPossibleStars = 3;
            player.getSession()
                    .send(
                            new PacketTowerLevelStarCondNotify(
                                    getCurrentFloorId(),
                                    getCurrentLevel(),
                                    currentPossibleStars + 1));
            return true;
        }
    }

    private boolean rejectEntry() {
        player.getSession()
                .send(
                        new PacketTowerEnterLevelRsp(
                                getCurrentFloorId(), getCurrentLevel(), Retcode.RET_FAIL));
        return false;
    }

    public void notifyCurLevelRecordChange() {
        player
                .getSession()
                .send(
                        new PacketTowerCurLevelRecordChangeNotify(
                                getTowerData().currentFloorId, getCurrentLevel()));
    }

    public int getCurLevelStars() {
        var scene = player.getScene();
        var challenge = scene.getChallenge();
        if (challenge == null) {
            Grasscutter.getLogger().error("getCurLevelStars: no challenge registered!");
            return 0;
        }

        var levelData = getCurrentTowerLevelDataMap();
        if (levelData == null) return 0;
        // 0-based indexing. "star" = 0 means checking for 1-star conditions.
        int star;
        for (star = 2; star >= 0; star--) {
            var cond = levelData.getCondType(star);
            if (cond == TowerLevelData.TowerCondType.TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN) {
                var params = levelData.getTimeCond(star);
                if (params == null) continue;
                long elapsed =
                        challenge.isSuccess() && !challenge.inProgress()
                                ? Math.max(0, challenge.getFinishedTime())
                                : Math.max(
                                        0L,
                                        (long) scene.getSceneTimeSeconds()
                                                - challenge.getStartedAt());
                var timeRemaining = (long) challenge.getTimeLimit() - elapsed;
                if (timeRemaining >= params.getMinimumTimeInSeconds()) {
                    break;
                }
            } else if (cond == TowerLevelData.TowerCondType.TOWER_COND_LEFT_HP_GREATER_THAN) {
                var params = levelData.getHpCond(star);
                if (params == null) continue;
                var hpPercent = challenge.getGuardEntityHpPercent();
                if (hpPercent >= params.getMinimumHpPercentage()) {
                    break;
                }
            } else {
                Grasscutter.getLogger()
                        .error(
                                "getCurLevelStars: Tower level {} has no or unknown condition"
                                    + " defined for {} stars",
                                getCurrentLevelId(),
                                star + 1);
                continue;
            }
        }
        return star + 1;
    }

    /**
     * Records the clear and hands out the chamber's first-pass reward.
     *
     * @return the items just granted, for the settle screen to display alongside the stars.
     */
    public List<ItemParamData> notifyCurLevelRecordChangeWhenDone(int stars) {
        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        int currentFloorId = getTowerData().currentFloorId;
        // Read before currentLevel is bumped below; the reward belongs to the chamber just cleared.
        int currentLevelId = getCurrentLevelId();
        int completedLevelIndex = getCurrentLevel();
        if (!recordMap.containsKey(currentFloorId)) {
            recordMap.put(
                    currentFloorId,
                    new TowerLevelRecord(currentFloorId).setLevelStars(currentLevelId, stars));
        } else {
            // Only update record if better than previous
            var prevRecord = recordMap.get(currentFloorId);
            var passedLevelMap = prevRecord.getPassedLevelMap();
            int prevStars = 0;
            if (passedLevelMap.containsKey(currentLevelId)) {
                prevStars = prevRecord.getLevelStars(currentLevelId);
            }
            if (!passedLevelMap.containsKey(currentLevelId) || stars > prevStars) {
                recordMap.put(currentFloorId, prevRecord.setLevelStars(currentLevelId, stars));
            }
        }

        var granted =
                grantFirstPassReward(recordMap.get(currentFloorId), currentLevelId, stars);

        this.getTowerData().currentLevel++;

        if (!this.hasNextLevel()) {
            // set up the next floor - but floor 12 is the last one, and getNextFloorId returns 0
            // there. Recording floor 0 would put a bogus entry in the save and then report it back
            // in TowerAllDataRsp as a real floor.
            var nextFloorId = this.getNextFloorId();
            if (nextFloorId > 0) {
                recordMap.computeIfAbsent(nextFloorId, TowerLevelRecord::new);
            }
        }

        // The native settlement page displays the completed chamber and computes its next
        // button from that index. The server checkpoint has advanced, but the client should
        // receive the next floor/chamber only when tryEnterLevel actually enters it.
        player.getSession().send(
                new PacketTowerCurLevelRecordChangeNotify(currentFloorId, completedLevelIndex));

        return granted;
    }

    /**
     * Grants the chamber's first-pass reward the first time it is cleared.
     *
     * <p>The record's rewarded set is what makes this once-only: a repeat clear of an already-paid
     * chamber returns nothing, and {@link #grantEntranceFloors} pre-marks the floors it skips so a
     * record that was never played for never yields loot either.
     *
     * <p>Officially the reward only goes out on a chamber the player actually beat, which is the
     * only path here - this is called from the settle listener on a completed dungeon.
     */
    private List<ItemParamData> grantFirstPassReward(
            TowerLevelRecord record, int levelId, int stars) {
        if (record == null || record.isLevelRewarded(levelId) || stars <= 0) return List.of();

        var levelData = GameData.getTowerLevelDataMap().get(levelId);
        if (levelData == null) return List.of();

        int rewardId = levelData.getFirstPassRewardId();
        if (rewardId <= 0) return List.of();

        var rewardData = GameData.getRewardDataMap().get(rewardId);
        if (rewardData == null || rewardData.getRewardItemList() == null) {
            Grasscutter.getLogger()
                    .warn("Tower first-pass reward {} (level {}) has no reward data", rewardId, levelId);
            return List.of();
        }

        var items = rewardData.getRewardItemList();
        player.getInventory().addItemParamDatas(items, ActionReason.TowerFirstPassReward);
        record.markLevelRewarded(levelId);
        return items;
    }

    public boolean hasNextLevel() {
        return getTowerData().currentLevel < 3;
    }

    public int getNextFloorId() {
        if (selectedFloors != null && !selectedFloors.isEmpty()) {
            int index = selectedFloors.indexOf(getCurrentFloorId());
            return index >= 0 && index + 1 < selectedFloors.size() ? selectedFloors.get(index + 1) : 0;
        }
        return this.player
                .getServer()
                .getTowerSystem()
                .getNextFloorId(this.getTowerData().currentFloorId);
    }

    public boolean hasNextFloor() { return getNextFloorId() > 0; }

    public void clearEntry() {
        getTowerData().entryScene = 0;
    }

    public boolean canEnterScheduleFloor() {
        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        if (!recordMap.containsKey(this.player.getServer().getTowerSystem().getLastEntranceFloor())) {
            return false;
        }
        return recordMap
                        .get(this.player.getServer().getTowerSystem().getLastEntranceFloor())
                        .getStarCount()
                >= 6;
    }

    public void mirrorTeamSetUp(int teamId) {
        tryMirrorTeamSetUp(teamId);
    }

    public boolean tryMirrorTeamSetUp(int teamId) {
        // use team user choose
        if (!restoreTemporaryTeams() || !player.getTeamManager().tryUseTemporaryTeam(teamId))
            return false;
        player.sendPacket(new PacketTowerMiddleLevelChangeTeamNotify());

        // Keep the lower team's chamber state. Lua enables skills when the next worktop starts.
        return true;
    }
}
