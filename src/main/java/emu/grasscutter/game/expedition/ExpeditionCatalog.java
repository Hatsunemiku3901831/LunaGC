package emu.grasscutter.game.expedition;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ExpeditionData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestState;

import java.util.*;

/** Current client locations and ranges; preview-derived output is explicitly identified. */
public final class ExpeditionCatalog {
    private ExpeditionCatalog() {}

    public record Definition(List<ExpeditionRewardData> items, String source) {}

    public static List<Integer> ids() {
        return GameData.getExpeditionDataMap().values().stream()
                .filter(d -> d != null && d.getId() > 0)
                .map(ExpeditionData::getId)
                .distinct()
                .sorted()
                .toList();
    }

    public static List<Integer> openIds(Player player) {
        return ids().stream()
                .filter(id -> open(player, GameData.getExpeditionDataMap().get(id)))
                .toList();
    }

    public static boolean open(Player player, ExpeditionData data) {
        if (player == null
                || data == null
                || data.getId() <= 0
                || data.getOpenConditionVec() == null) return false;
        for (var condition : data.getOpenConditionVec()) {
            if (condition == null || condition.getPara() < 0 || condition.getPara2() < 0)
                return false;
            String type = Objects.toString(condition.getType(), "EXP_OPEN_COND_LEVEL");
            switch (type) {
                case "EXP_OPEN_COND_LEVEL" -> {
                    if (player.getLevel() < condition.getPara()) return false;
                }
                case "EXP_OPEN_COND_POINT" -> {
                    var points = player.getUnlockedScenePoints().get(condition.getPara2());
                    if (condition.getPara() <= 0
                            || condition.getPara2() <= 0
                            || points == null
                            || !points.contains(condition.getPara())) return false;
                }
                case "EXP_OPEN_COND_QUEST" -> {
                    var quest =
                            player.getQuestManager() == null
                                    ? null
                                    : player.getQuestManager().getQuestById(condition.getPara());
                    if (quest == null
                            || quest.getState() == null
                            || quest.getState().getValue()
                                    != QuestState.QUEST_STATE_FINISHED.getValue()) return false;
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<ExpeditionRewardDataList> rewards(ExpeditionData data) {
        if (data == null || data.getTimeRewardVec() == null) return List.of();
        var result = new ArrayList<ExpeditionRewardDataList>();
        var seen = new HashSet<Integer>();
        for (var duration : data.getTimeRewardVec()) {
            if (duration == null
                    || duration.getHourTime() <= 0
                    || !seen.add(duration.getHourTime())) return List.of();
            var definition = definition(duration);
            if (definition != null)
                result.add(
                        new ExpeditionRewardDataList(duration.getHourTime(), definition.items()));
        }
        return List.copyOf(result);
    }

    public static Definition definition(ExpeditionData.TimeReward duration) {
        if (duration == null || duration.getHourTime() <= 0) return null;
        var preview = GameData.getRewardPreviewDataMap().get(duration.getRewardPreview());
        var previewCounts = new TreeMap<Integer, long[]>();
        boolean previewValid =
                preview != null
                        && preview.getId() == duration.getRewardPreview()
                        && preview.getRawPreviewItems() != null;
        if (previewValid)
            for (var row : preview.getRawPreviewItems()) {
                if (row == null) {
                    previewValid = false;
                    break;
                }
                if (row.getId() == 0) continue;
                if (!add(previewCounts, row.getId(), row.getCount())) {
                    previewValid = false;
                    break;
                }
            }
        var drop = GameData.getDropTableExcelConfigDataMap().get(duration.getDropId());
        var dropCounts = new TreeMap<Integer, long[]>();
        if (drop != null) {
            if (drop.getId() != duration.getDropId()
                    || drop.getRandomType() != 1
                    || drop.getDropVec() == null) return null;
            for (var row : drop.getDropVec()) {
                if (row == null
                        || row.getWeight() != 10000
                        || GameData.getDropTableExcelConfigDataMap().containsKey(row.getItemId())
                        || !add(dropCounts, row.getItemId(), row.getCountRange())) return null;
            }
            if (dropCounts.isEmpty()) return null;
            if (!previewValid || previewCounts.isEmpty() || equal(previewCounts, dropCounts))
                return new Definition(items(dropCounts), "server_drop");
        }
        if (!previewValid || previewCounts.isEmpty()) return null;
        // Some shipped server tables predate current mining rewards; the current preview retains
        // them.
        return new Definition(items(previewCounts), "client_preview");
    }

    private static boolean add(Map<Integer, long[]> counts, int id, String text) {
        if (id <= 0 || text == null || !text.matches("[0-9]+(;[0-9]+)?")) return false;
        try {
            var pair = text.split(";", -1);
            long min = Long.parseLong(pair[0]);
            long max = Long.parseLong(pair[pair.length - 1]);
            if (min <= 0 || max < min || max > Integer.MAX_VALUE) return false;
            var total = counts.computeIfAbsent(id, ignored -> new long[2]);
            total[0] += min;
            total[1] += max;
            return total[0] <= Integer.MAX_VALUE && total[1] <= Integer.MAX_VALUE;
        } catch (NumberFormatException malformed) {
            return false;
        }
    }

    private static boolean equal(Map<Integer, long[]> first, Map<Integer, long[]> second) {
        return first.keySet().equals(second.keySet())
                && first.keySet().stream()
                        .allMatch(id -> Arrays.equals(first.get(id), second.get(id)));
    }

    private static List<ExpeditionRewardData> items(Map<Integer, long[]> counts) {
        return counts.entrySet().stream()
                .map(
                        e ->
                                new ExpeditionRewardData(
                                        e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1]))
                .toList();
    }
}
