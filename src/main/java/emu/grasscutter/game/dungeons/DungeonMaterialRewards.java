package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamStringData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.inventory.*;

import java.math.BigDecimal;
import java.util.*;

/** Resource templates for audited talent domains whose own drop roots are missing. */
final class DungeonMaterialRewards {
    private DungeonMaterialRewards() {}

    record Template(int dropId, Map<Integer, Integer> itemIds) {}

    private record Slot(int currencyId, int rarity) {}

    static Template template(DungeonData target) {
        int id = target.getId();
        int first = id >= 4651 && id <= 4662 ? 4651 : id >= 4691 && id <= 4702 ? 4691 : 0;
        if (first == 0) return null;
        // These two regions use the same four reward profiles as the existing talent domains.
        // Keep their complete resource trees, including correlated rarity rolls, and rebind IDs.
        var source = GameData.getDungeonDataMap().get(4210 + (id - first) % 4);
        if (source == null
                || source.getStatueCostID() != target.getStatueCostID()
                || source.getStatueCostCount() != target.getStatueCostCount())
            throw new IllegalArgumentException(
                    "Missing compatible material reward source for " + id);
        var sourceSlots = slots(source.getRewardPreviewData());
        var targetSlots = slots(target.getRewardPreviewData());
        if (!sourceSlots.keySet().equals(targetSlots.keySet()))
            throw new IllegalArgumentException("Incompatible material reward rarities for " + id);
        var itemIds = new HashMap<Integer, Integer>();
        for (var entry : sourceSlots.entrySet()) {
            var wanted = targetSlots.get(entry.getKey());
            if (!quantity(entry.getValue().getCount()).equals(quantity(wanted.getCount())))
                throw new IllegalArgumentException(
                        "Incompatible material reward quantity for " + id);
            itemIds.put(entry.getValue().getId(), wanted.getId());
        }
        int rootId = source.getStatueDrop();
        var root = GameData.getDropTableDataMap().get(rootId);
        if (root == null || root.getRandomType() != 1 || root.getDropVec() == null)
            throw new IllegalArgumentException("Missing material reward root " + rootId);
        for (int currency : List.of(102, 105, 202)) {
            var preview = sourceSlots.get(new Slot(currency, 0));
            if (preview == null
                    || root.getDropVec().stream()
                            .noneMatch(
                                    drop ->
                                            drop.getId() == currency
                                                    && drop.getWeight() == 10000
                                                    && quantity(drop.getCountRange())
                                                            .equals(quantity(preview.getCount()))))
                throw new IllegalArgumentException(
                        "Incompatible material reward currency " + currency);
        }
        validateTree(rootId, itemIds.keySet(), new HashSet<>(), new HashSet<>());
        return new Template(rootId, Map.copyOf(itemIds));
    }

    private static Map<Slot, ItemParamStringData> slots(RewardPreviewData preview) {
        if (preview == null || preview.getRawPreviewItems() == null)
            throw new IllegalArgumentException("Missing material reward preview");
        var result = new HashMap<Slot, ItemParamStringData>();
        for (var entry : preview.getRawPreviewItems()) {
            if (entry.getId() <= 0) continue;
            var item = GameData.getItemDataMap().get(entry.getId());
            if (item == null)
                throw new IllegalArgumentException("Undefined material reward " + entry.getId());
            Slot slot;
            if (Set.of(102, 105, 202).contains(item.getId())
                    && item.getItemType() == ItemType.ITEM_VIRTUAL)
                slot = new Slot(item.getId(), 0);
            else if (item.getItemType() == ItemType.ITEM_MATERIAL
                    && item.getMaterialType() == MaterialType.MATERIAL_AVATAR_MATERIAL
                    && item.getRankLevel() > 0) slot = new Slot(0, item.getRankLevel());
            else throw new IllegalArgumentException("Unsupported material reward " + item.getId());
            if (result.putIfAbsent(slot, entry) != null)
                throw new IllegalArgumentException("Ambiguous material reward slot " + slot);
        }
        return result;
    }

    private static String quantity(String value) {
        return value == null || value.isBlank()
                ? ""
                : new BigDecimal(value).stripTrailingZeros().toPlainString();
    }

    private static void validateTree(
            int id, Set<Integer> items, Set<Integer> visited, Set<Integer> path) {
        if (visited.contains(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic material reward table " + id);
        var table = GameData.getDropTableDataMap().get(id);
        if (table == null) {
            if (!items.contains(id))
                throw new IllegalArgumentException("Unmapped material reward " + id);
        } else {
            if (table.getDropVec() == null)
                throw new IllegalArgumentException("Missing material reward vector " + id);
            for (var child : table.getDropVec())
                if (child.getId() > 0 && child.getWeight() > 0)
                    validateTree(child.getId(), items, visited, path);
        }
        path.remove(id);
        visited.add(id);
    }
}
