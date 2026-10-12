package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ForgeData;
import emu.grasscutter.game.inventory.ItemType;

import java.util.*;

/**
 * Current forge drop references are guaranteed fixed rows; never guess unknown random semantics.
 */
final class ForgeProducts {
    private ForgeProducts() {}

    static List<ItemParamData> resolve(ForgeData recipe) {
        var quantities = new LinkedHashMap<Integer, Integer>();
        if (recipe.getMainRandomDropId() != 0) {
            var drop = GameData.getDropTableDataMap().get(recipe.getMainRandomDropId());
            if (recipe.getMainRandomDropId() < 0
                    || drop == null
                    || drop.getId() != recipe.getMainRandomDropId()
                    || drop.getNodeType() != 1
                    || drop.getRandomType() != 1
                    || drop.getDropVec() == null
                    || drop.getDropVec().isEmpty()
                    || drop.getEverydayLimit() != 0
                    || drop.getHistoryLimit() != 0
                    || drop.getActivityLimit() != 0)
                throw new IllegalArgumentException("Unsupported forge drop definition");
            for (var row : drop.getDropVec()) {
                if (row == null
                        || row.getId() <= 0
                        || row.getWeight() != 10000
                        || row.getCountRange() == null
                        || !row.getCountRange().matches("[1-9][0-9]{0,9}")
                        || GameData.getDropTableDataMap().containsKey(row.getId()))
                    throw new IllegalArgumentException("Unsupported forge drop row");
                quantities.merge(
                        row.getId(), Integer.parseInt(row.getCountRange()), Math::addExact);
            }
        } else {
            int id =
                    recipe.getResultItemId() > 0
                            ? recipe.getResultItemId()
                            : recipe.getShowItemId();
            quantities.put(id, recipe.getResultItemCount());
        }
        var result = new ArrayList<ItemParamData>();
        boolean hasProduct = false;
        for (var entry : quantities.entrySet()) {
            var data = GameData.getItemDataMap().get(entry.getKey());
            if (entry.getValue() <= 0
                    || data == null
                    || data.getId() != entry.getKey()
                    || data.isUseOnGain())
                throw new IllegalArgumentException("Invalid forge product");
            var type = data.getItemType();
            if (type == ItemType.ITEM_MATERIAL || type == ItemType.ITEM_WEAPON) hasProduct = true;
            else if (type != ItemType.ITEM_VIRTUAL
                    || entry.getKey() != 102 && entry.getKey() != 105)
                throw new IllegalArgumentException("Unsupported forge product type");
            result.add(new ItemParamData(entry.getKey(), entry.getValue()));
        }
        if (!hasProduct) throw new IllegalArgumentException("Forge drop has no inventory product");
        return result;
    }
}
