package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;

import java.util.*;

final class CookDelivery {
    private CookDelivery() {}

    static List<ItemParamData> costs(List<ItemParamData> inputs, int count) {
        if (inputs == null || inputs.isEmpty())
            throw new IllegalArgumentException("Missing cooking ingredients");
        var amounts = new LinkedHashMap<Integer, Integer>();
        for (var input : inputs) {
            if (input == null
                    || input.getId() < 0
                    || input.getCount() < 0
                    || input.getId() == 0 && input.getCount() != 0)
                throw new IllegalArgumentException("Invalid cooking ingredient");
            if (input.getCount() == 0) continue;
            var data = GameData.getItemDataMap().get(input.getId());
            if (data == null
                    || data.getId() != input.getId()
                    || data.isUseOnGain()
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                    || data.getStackLimit() <= 0)
                throw new IllegalArgumentException("Unknown cooking ingredient");
            amounts.merge(
                    input.getId(), Math.multiplyExact(input.getCount(), count), Math::addExact);
        }
        if (amounts.isEmpty()) throw new IllegalArgumentException("Cooking has no ingredients");
        return amounts.entrySet().stream()
                .map(e -> new ItemParamData(e.getKey(), e.getValue()))
                .toList();
    }

    static void pay(Player player, CookReceipt receipt) {
        var inventory = player.getInventory();
        var before = new LinkedHashMap<GameItem, Integer>();
        var counts = new HashMap<Integer, Integer>();
        for (var cost : receipt.getCosts()) {
            var stack = inventory.getItemById(cost.getId());
            if (stack == null || stack.getCount() < cost.getCount())
                throw new IllegalStateException("Cooking ingredient stock changed");
            before.put(stack, stack.getCount());
            counts.put(cost.getId(), cost.getCount());
        }
        if (!inventory.payItems(receipt.getCosts(), 1, ActionReason.Cook))
            throw new IllegalStateException("Cooking payment was refused");
        for (var entry : before.entrySet()) {
            var item = entry.getKey();
            if ((long) entry.getValue() - inventory.getItemCountById(item.getItemId())
                    != counts.get(item.getItemId()))
                throw new IllegalStateException("Cooking ingredient payment changed");
            DatabaseHelper.saveGameSync(item);
        }
        receipt.paid();
        DatabaseHelper.saveGameSync(player);
    }

    static List<GameItem> grant(Player player, List<ItemParamData> products) {
        if (InvestigationRewardDelivery.validate(player, products) != 0)
            throw new IllegalStateException("Cooking capacity changed");
        var result = new ArrayList<GameItem>();
        for (var product : products) {
            var data = GameData.getItemDataMap().get(product.getId());
            if (data.getItemType() != ItemType.ITEM_MATERIAL)
                throw new IllegalStateException("Cooking result is not a material");
            int before = player.getInventory().getItemCountById(product.getId());
            var item = new GameItem(data, product.getCount());
            if (!player.getInventory().addItem(item)
                    || (long) player.getInventory().getItemCountById(product.getId()) - before
                            != product.getCount())
                throw new IllegalStateException("Cooking dish was not fully delivered");
            var stored = player.getInventory().getItemById(product.getId());
            if (stored == null)
                throw new IllegalStateException("Cooking result has no stored stack");
            DatabaseHelper.saveGameSync(stored);
            result.add(item);
        }
        return result;
    }
}
