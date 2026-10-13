package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;

import java.util.*;

public abstract class ItemUseSelectItems extends ItemUseAction {
    protected static final int INVALID = -1;
    protected int[] optionItemIds;

    protected int getItemId(int index) {
        if ((optionItemIds == null) || (index < 0) || (index >= optionItemIds.length))
            return INVALID;
        return this.optionItemIds[index];
    }

    protected int getItemCount(int index) {
        return 1;
    }

    protected static String[] options(String[] params) {
        return params == null || params.length == 0 || params[0] == null || params[0].isBlank()
                ? new String[0]
                : params[0].split(",", -1);
    }

    public List<ItemParamData> resolveItems(UseItemParams params) {
        if (params == null || params.player == null || params.count <= 0 || params.optionId <= 0)
            return List.of();
        int id = getItemId(params.optionId - 1), count = getItemCount(params.optionId - 1);
        if (id <= 0 || count <= 0) return List.of();
        long amount = (long) count * params.count;
        if (amount > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Selection quantity overflow");
        return List.of(new ItemParamData(id, (int) amount));
    }

    /** Native lists contain one one-based candidate index per consumed box. */
    public List<ItemParamData> resolveItems(UseItemParams params, List<Integer> choices) {
        if (choices == null || params == null) return List.of();
        if (choices.isEmpty()) return resolveItems(params);
        if (params.player == null || params.count <= 0 || choices.size() != params.count)
            return List.of();
        var quantities = new LinkedHashMap<Integer, Long>();
        for (var choice : choices) {
            if (choice == null || choice <= 0 || params.optionId != 0 && params.optionId != choice)
                return List.of();
            var selected =
                    new UseItemParams(
                            params.player,
                            params.itemUseTarget,
                            params.targetAvatar,
                            1,
                            choice,
                            params.isEnterMpDungeonTeam);
            selected.usedItemId = params.usedItemId;
            var products = resolveItems(selected);
            if (products == null || products.isEmpty()) return List.of();
            for (var product : products) {
                if (product == null || product.getId() <= 0 || product.getCount() <= 0)
                    return List.of();
                long total = quantities.getOrDefault(product.getId(), 0L) + product.getCount();
                if (total > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Selection quantity overflow");
                quantities.put(product.getId(), total);
            }
        }
        return quantities.entrySet().stream()
                .map(entry -> new ItemParamData(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    protected GameItem getItemStack(int index, int useCount) {
        int id = this.getItemId(index);
        int count = this.getItemCount(index);
        if (id <= 0 || count <= 0 || useCount <= 0 || (long) count * useCount > Integer.MAX_VALUE)
            return null;

        var item = GameData.getItemDataMap().get(id);
        if (item == null) return null;

        long amount = (long) count * useCount;
        if (item.isEquip() && amount != 1
                || !item.isEquip()
                        && item.getItemType()
                                != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL
                        && amount > item.getStackLimit()) return null;
        return new GameItem(item, (int) amount);
    }

    @Override
    public boolean useItem(UseItemParams params) {
        // Selected chests require their owned inventory source and payment confirmation.
        return false;
    }
}
