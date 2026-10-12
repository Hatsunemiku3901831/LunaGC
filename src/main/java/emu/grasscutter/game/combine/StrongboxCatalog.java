package emu.grasscutter.game.combine;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.*;

import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/**
 * Resolve configured strongboxes through their display-set previews and normal relic definitions.
 */
final class StrongboxCatalog {
    private StrongboxCatalog() {}

    static Int2ObjectMap<List<Integer>> build() {
        var result = new Int2ObjectOpenHashMap<List<Integer>>();
        for (var definition : GameData.getReliquaryDecomposeDataMap().values()) {
            var pool = candidates(definition);
            if (pool != null) result.put(definition.getId(), pool);
            else
                Grasscutter.getLogger()
                        .warn("Incomplete strongbox resource join: {}", definition.getId());
        }
        return result;
    }

    private static List<Integer> candidates(ReliquaryDecomposeData definition) {
        if (definition == null || !definition.isValid()) return null;
        var preview = GameData.getRewardPreviewDataMap().get(definition.getRewardPreviewId());
        if (preview == null || preview.getRawPreviewItems() == null) return null;
        var displays =
                Arrays.stream(preview.getRawPreviewItems())
                        .filter(Objects::nonNull)
                        .filter(item -> item.getId() > 0)
                        .toList();
        if (displays.size() != 1) return null;
        var display = GameData.getDisplayItemDataMap().get(displays.get(0).getId());
        if (display == null
                || display.getParam() <= 0
                || !"RELIQUARY_ITEM_SMELT".equals(display.getDisplayType())
                || display.getRankLevel() != definition.getNeedReliquaryRankLevel()) return null;
        var items =
                GameData.getItemDataMap().values().stream()
                        .filter(
                                item ->
                                        item.getSetId() == display.getParam()
                                                && item.getRankLevel()
                                                        == definition.getNeedReliquaryRankLevel()
                                                && item.getItemType() == ItemType.ITEM_RELIQUARY
                                                && !item.isDropable()
                                                && !item.isUseOnGain()
                                                && (item.getAppendPropNum() == 3
                                                        || item.getAppendPropNum() == 4)
                                                && item.getMainPropDepotId() > 0
                                                && item.getAppendPropDepotId() > 0
                                                && item.getEquipType() != null
                                                && item.getEquipType().getValue() >= 1
                                                && item.getEquipType().getValue() <= 5
                                                && GameData.getItemDataMap().get(item.getId())
                                                        == item)
                        .sorted(Comparator.comparingInt(ItemData::getId))
                        .toList();
        if (items.size() != 10) return null;
        var variants = new EnumMap<EquipType, Set<Integer>>(EquipType.class);
        for (var item : items)
            variants.computeIfAbsent(item.getEquipType(), ignored -> new HashSet<>())
                    .add(item.getAppendPropNum());
        if (variants.size() != 5
                || variants.values().stream().anyMatch(v -> !v.equals(Set.of(3, 4)))) return null;
        return items.stream().map(ItemData::getId).toList();
    }
}
