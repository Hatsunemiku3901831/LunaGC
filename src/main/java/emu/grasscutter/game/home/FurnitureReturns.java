package emu.grasscutter.game.home;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;

import java.util.*;

/** Simulate material storage in claim order, including speedup payments before returns. */
final class FurnitureReturns {
    private FurnitureReturns() {}

    static List<FurnitureTakeReceipt.Return> reserve(
            Player player, List<FurnitureMakePlan.Material> values, boolean vial) {
        var capacity = new Capacity(player);
        if (vial && !capacity.spendVial()) return null;
        List<FurnitureTakeReceipt.Return> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (var value : values) {
            if (value == null || !ids.add(value.getId())) return null;
            int before = capacity.count(value.getId());
            if (!capacity.add(value.getId(), value.getCount())) return null;
            result.add(new FurnitureTakeReceipt.Return(value.getId(), value.getCount(), before));
        }
        return result;
    }

    static final class Capacity {
        private final Player player;
        private final Inventory inventory;
        private final InventoryTab tab;
        private final Map<Integer, Integer> counts = new HashMap<>();
        private int size;

        Capacity(Player player) {
            this.player = player;
            inventory = player.getInventory();
            tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
            size = tab == null ? 0 : tab.getSize();
        }

        int count(int id) {
            return counts.computeIfAbsent(id, inventory::getItemCountById);
        }

        boolean spendVial() {
            int before = count(107013);
            var data = GameData.getItemDataMap().get(107013);
            var existing = inventory.getItemById(107013);
            if (tab == null
                    || before < 1
                    || data == null
                    || data.getId() != 107013
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                    || data.isUseOnGain()
                    || existing == null
                    || existing.getOwnerId() != player.getUid()) return false;
            counts.put(107013, before - 1);
            if (before == 1) size--;
            return true;
        }

        boolean add(int id, int amount) {
            var data = GameData.getItemDataMap().get(id);
            var existing = inventory.getItemById(id);
            int before = count(id);
            if (tab == null
                    || id <= 0
                    || amount <= 0
                    || before < 0
                    || data == null
                    || data.getId() != id
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                    || data.isUseOnGain()
                    || data.getStackLimit() <= 0
                    || existing != null && existing.getOwnerId() != player.getUid()
                    || (long) before + amount > data.getStackLimit()) return false;
            int afterSize = size + (before == 0 ? 1 : 0);
            if (afterSize > tab.getMaxCapacity()) return false;
            size = afterSize;
            counts.put(id, before + amount);
            return true;
        }
    }
}
