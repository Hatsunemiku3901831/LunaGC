package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.inventory.*;

import java.math.BigDecimal;
import java.util.*;

/** Rebind a borrowed drop table to the relic sets advertised by the current dungeon. */
final class DungeonRelicRewards {
    private DungeonRelicRewards() {}

    private record RelicKey(
            int setId,
            int rarity,
            EquipType slot,
            int mainDepot,
            int appendDepot,
            int appendCount,
            int maxLevel) {
        static RelicKey of(ItemData item, int setId) {
            return new RelicKey(
                    setId,
                    item.getRankLevel(),
                    item.getEquipType(),
                    item.getMainPropDepotId(),
                    item.getAppendPropDepotId(),
                    item.getAppendPropNum(),
                    item.getMaxLevel());
        }
    }

    static Map<Integer, Integer> itemIds(DungeonData dungeon, int dropId) {
        validateCurrencies(dungeon, dropId);
        var source = new TreeMap<Integer, ItemData>();
        collectItems(dropId, source, new HashSet<>(), new HashSet<>());
        var sourceRanks = new TreeMap<Integer, SortedSet<Integer>>();
        source.values()
                .forEach(
                        item ->
                                sourceRanks
                                        .computeIfAbsent(
                                                item.getSetId(), ignored -> new TreeSet<>())
                                        .add(item.getRankLevel()));
        var targetRanks = new TreeMap<Integer, SortedSet<Integer>>();
        var preview = dungeon.getRewardPreviewData();
        if (preview != null && preview.getRawPreviewItems() != null) {
            for (var item : preview.getRawPreviewItems()) {
                var display = GameData.getDisplayItemDataMap().get(item.getId());
                if (display != null && display.getParam() > 0 && display.getRankLevel() > 0)
                    targetRanks
                            .computeIfAbsent(display.getParam(), ignored -> new TreeSet<>())
                            .add(display.getRankLevel());
            }
        }
        if (sourceRanks.equals(targetRanks)) return Map.of();
        if (sourceRanks.isEmpty() || targetRanks.isEmpty())
            throw new IllegalArgumentException(
                    "Missing relic reward pools for dungeon " + dungeon.getId());
        var sourceGroups = groups(sourceRanks);
        var targetGroups = groups(targetRanks);
        if (!sourceGroups.keySet().equals(targetGroups.keySet()))
            throw new IllegalArgumentException(
                    "Incompatible relic rarities for dungeon " + dungeon.getId());
        var sets = new HashMap<Integer, Integer>();
        for (var group : sourceGroups.entrySet()) {
            var wanted = targetGroups.get(group.getKey());
            var old = new TreeSet<>(group.getValue());
            var replacement = new TreeSet<>(wanted);
            old.removeAll(wanted);
            replacement.removeAll(group.getValue());
            if (old.size() != replacement.size())
                throw new IllegalArgumentException(
                        "Incompatible relic set counts for dungeon " + dungeon.getId());
            var targets = replacement.iterator();
            for (int setId : old) sets.put(setId, targets.next());
        }
        var index = new HashMap<RelicKey, List<ItemData>>();
        for (var item : GameData.getItemDataMap().values()) {
            if (item.getItemType() == ItemType.ITEM_RELIQUARY
                    && sets.containsValue(item.getSetId()))
                index.computeIfAbsent(
                                RelicKey.of(item, item.getSetId()), ignored -> new ArrayList<>())
                        .add(item);
        }
        var result = new HashMap<Integer, Integer>();
        for (var entry : source.entrySet()) {
            var item = entry.getValue();
            var target = sets.get(item.getSetId());
            if (target == null) continue;
            var candidates = index.getOrDefault(RelicKey.of(item, target), List.of());
            if (candidates.size() != 1)
                throw new IllegalArgumentException(
                        "Ambiguous relic replacement for item " + entry.getKey());
            result.put(entry.getKey(), candidates.get(0).getId());
        }
        return Map.copyOf(result);
    }

    private static void validateCurrencies(DungeonData dungeon, int dropId) {
        var table = GameData.getDropTableDataMap().get(dropId);
        var preview = dungeon.getRewardPreviewData();
        if (table == null
                || table.getRandomType() != 1
                || table.getDropVec() == null
                || preview == null
                || preview.getRawPreviewItems() == null)
            throw new IllegalArgumentException("Missing borrowed reward root or preview " + dropId);
        for (int currency : List.of(102, 105, 202)) {
            var expected =
                    Arrays.stream(preview.getRawPreviewItems())
                            .filter(item -> item.getId() == currency)
                            .toList();
            var drops =
                    table.getDropVec().stream().filter(item -> item.getId() == currency).toList();
            if (expected.size() != 1
                    || drops.size() != 1
                    || drops.get(0).getWeight() != 10000
                    || new BigDecimal(expected.get(0).getCount())
                                    .compareTo(new BigDecimal(drops.get(0).getCountRange()))
                            != 0)
                throw new IllegalArgumentException(
                        "Incompatible borrowed reward currency " + currency);
        }
    }

    private static void collectItems(
            int id, Map<Integer, ItemData> source, Set<Integer> visited, Set<Integer> path) {
        if (visited.contains(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic borrowed reward table " + id);
        var table = GameData.getDropTableDataMap().get(id);
        if (table == null) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null) throw new IllegalArgumentException("Undefined borrowed reward " + id);
            if (item.getItemType() == ItemType.ITEM_RELIQUARY) source.put(id, item);
        } else {
            if (table.getDropVec() == null)
                throw new IllegalArgumentException("Missing drop vector " + id);
            for (var child : table.getDropVec())
                if (child.getId() > 0 && child.getWeight() > 0)
                    collectItems(child.getId(), source, visited, path);
        }
        path.remove(id);
        visited.add(id);
    }

    private static Map<List<Integer>, SortedSet<Integer>> groups(
            Map<Integer, SortedSet<Integer>> sets) {
        var result = new HashMap<List<Integer>, SortedSet<Integer>>();
        sets.forEach(
                (setId, ranks) ->
                        result.computeIfAbsent(List.copyOf(ranks), ignored -> new TreeSet<>())
                                .add(setId));
        return result;
    }
}
