package emu.grasscutter.game.tower;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerBuffData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import java.util.*;

/** Choices belong to one floor attempt; offers refresh at the China server's daily reset. */
@Entity
public class TowerBlessingState {
    private int floorId;
    private List<Integer> choices = new ArrayList<>();
    private int offeredLevelIndex;
    private long offeredDay;
    private List<Integer> offeredBuffIds = new ArrayList<>();

    public void reset(int floor) {
        floorId = floor;
        choices = new ArrayList<>();
        offeredLevelIndex = 0;
        offeredDay = 0;
        offeredBuffIds = new ArrayList<>();
    }

    public int selected(int floor, int level) {
        if (floor != floorId || level < 1 || level > 3 || choices == null || choices.size() < level)
            return 0;
        var id = choices.get(level - 1);
        return id == null ? 0 : id;
    }

    public List<Integer> prepare(int floor, int level, int uid, long day, TowerLevelData data) {
        if (floorId != floor) reset(floor);
        if (selected(floor, level) > 0) return List.of();
        if (offeredLevelIndex == level && offeredDay == day && offeredBuffIds != null)
            return List.copyOf(offeredBuffIds);
        offeredLevelIndex = level;
        offeredDay = day;
        offeredBuffIds = new ArrayList<>();
        if (level < 1
                || level > 3
                || data == null
                || data.getTowerBuffConfigStrList() == null
                || data.getTowerBuffConfigStrList().size() != 3) return List.of();
        // Re-entering, restarting the floor, and logging in again cannot reroll the same day's cards.
        var random = new Random(Objects.hash(uid, floor, level, day));
        for (var pool : data.getTowerBuffConfigStrList()) {
            var weights = new LinkedHashMap<Integer, Integer>();
            if (pool != null)
                for (var entry : pool.split(",")) {
                    var pair = entry.trim().split(":");
                    if (pair.length != 2) continue;
                    try {
                        int id = Integer.parseInt(pair[0].trim()), weight = Integer.parseInt(pair[1].trim());
                        if (weight > 0 && available(id) && !offeredBuffIds.contains(id))
                            weights.put(id, weight);
                    } catch (NumberFormatException ignored) {
                        /* Ignore malformed resource entries. */
                    }
                }
            long total = weights.values().stream().mapToLong(Integer::longValue).sum();
            if (total == 0) {
                offeredBuffIds.clear();
                return List.of();
            }
            long roll = random.nextLong(total);
            for (var entry : weights.entrySet()) {
                roll -= entry.getValue();
                if (roll < 0) {
                    offeredBuffIds.add(entry.getKey());
                    break;
                }
            }
        }
        return List.copyOf(offeredBuffIds);
    }

    public static boolean available(int id) {
        var card = GameData.getTowerBuffDataMap().get(id);
        if (card == null
                || card.getLastingType() == null
                || card.getLastingType() == TowerBuffData.LastingType.TOWER_BUFF_LASTING_NONE) return false;
        var buff = GameData.getBuffDataMap().get(card.getBuffId());
        var ability = buff == null ? null : GameData.getAbilityData(buff.getAbilityName());
        return ability != null
                && ability.modifiers != null
                && ability.modifiers.containsKey(buff.getModifierName());
    }

    public boolean choose(int floor, int level, int id) {
        int selected = selected(floor, level);
        if (selected != 0) return selected == id;
        if (floor != floorId
                || level != offeredLevelIndex
                || level < 1
                || level > 3
                || offeredBuffIds == null
                || !offeredBuffIds.contains(id)
                || !available(id)) return false;
        if (choices == null) choices = new ArrayList<>();
        while (choices.size() < level) choices.add(0);
        choices.set(level - 1, id);
        return true;
    }

    /** Preserve repeated floor cards: each chamber contributes an independent stack. */
    public List<Integer> active(int floor, int level) {
        var result = new ArrayList<Integer>();
        if (level < 1 || level > 3) return result;
        for (int chamber = 1; chamber <= level; chamber++) {
            int id = selected(floor, chamber);
            var data = GameData.getTowerBuffDataMap().get(id);
            if (data != null
                    && (data.getLastingType() == TowerBuffData.LastingType.TOWER_BUFF_LASTING_FLOOR
                            || chamber == level
                                    && data.getLastingType() == TowerBuffData.LastingType.TOWER_BUFF_LASTING_LEVEL))
                result.add(id);
        }
        return List.copyOf(result);
    }
}
