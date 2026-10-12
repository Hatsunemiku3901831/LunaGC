package emu.grasscutter.game.combine;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.shop.BeyondCurrency;

import java.util.*;

final class CombineDelivery {
    private CombineDelivery() {}

    static int count(Player player, int id) {
        var data = GameData.getItemDataMap().get(id);
        if (data == null || data.getId() != id || data.isUseOnGain()) return -1;
        if (data.getItemType() != ItemType.ITEM_VIRTUAL)
            return player.getInventory().getItemCountById(id);
        var beyond = BeyondCurrency.propertyForItem(id);
        if (beyond != null) return player.getProperty(beyond);
        return switch (id) {
            case 201 -> player.getPrimogems();
            case 202 -> player.getMora();
            case 203 -> player.getCrystals();
            case 204 -> player.getHomeCoin();
            case 106 -> player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
            case 107 -> player.getProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
            default -> -1;
        };
    }

    static Map<Integer, Integer> paymentStock(Player player, List<ItemParamData> costs) {
        var before = new LinkedHashMap<Integer, Integer>();
        for (var cost : costs) {
            var data = GameData.getItemDataMap().get(cost.getId());
            if (data == null
                    || data.getId() != cost.getId()
                    || data.isUseOnGain()
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                            && data.getItemType() != ItemType.ITEM_VIRTUAL
                    || data.getItemType() == ItemType.ITEM_MATERIAL && data.getStackLimit() <= 0)
                throw new IllegalArgumentException("Invalid combine cost definition");
            int stock = count(player, cost.getId());
            if (stock < 0) throw new IllegalArgumentException("Unknown combine currency");
            before.put(cost.getId(), stock);
        }
        return before;
    }

    static void pay(Player player, CombineReceipt receipt, List<ItemParamData> costs) {
        var inventory = player.getInventory();
        var stacks = new ArrayList<GameItem>();
        for (var cost : costs) {
            var item = inventory.getItemById(cost.getId());
            if (item != null) stacks.add(item);
        }
        if (!inventory.payItems(costs, 1, ActionReason.Combine))
            throw new IllegalStateException("Combine payment was refused");
        for (var cost : receipt.getCosts())
            if ((long) cost.getBefore() - count(player, cost.getId()) != cost.getCount())
                throw new IllegalStateException("Combine cost was not deducted exactly");
        for (var stack : stacks) DatabaseHelper.saveGameSync(stack);
        receipt.paid();
        DatabaseHelper.saveGameSync(player);
    }

    static void grant(Player player, ItemData output, int amount, CombineReceipt receipt) {
        var inventory = player.getInventory();
        boolean equipment =
                output.getItemType() == ItemType.ITEM_WEAPON
                        || output.getItemType() == ItemType.ITEM_RELIQUARY;
        if (equipment) {
            for (int i = 0; i < amount; i++) {
                var item = new GameItem(output, 1);
                int before = count(player, output.getId());
                if (!inventory.addItem(item, ActionReason.Combine)
                        || inventory.getItemByGuid(item.getGuid()) != item
                        || item.getOwnerId() != player.getUid()
                        || item.getCount() != 1
                        || (long) count(player, output.getId()) - before != 1)
                    throw new IllegalStateException("Combine equipment identity changed");
                DatabaseHelper.saveGameSync(item);
                receipt.recordDelivery(output.getId(), 1, item.getGuid());
            }
        } else {
            int before = count(player, output.getId());
            if (before < 0
                    || !inventory.addItem(output.getId(), amount, ActionReason.Combine)
                    || (long) count(player, output.getId()) - before != amount)
                throw new IllegalStateException("Combine output was not fully delivered");
            long guid = 0;
            if (output.getItemType() != ItemType.ITEM_VIRTUAL) {
                var stored = inventory.getItemById(output.getId());
                if (stored == null || stored.getOwnerId() != player.getUid())
                    throw new IllegalStateException("Combine output has no owned stored stack");
                DatabaseHelper.saveGameSync(stored);
                guid = stored.getGuid();
            }
            receipt.recordDelivery(output.getId(), amount, guid);
        }
    }
}
