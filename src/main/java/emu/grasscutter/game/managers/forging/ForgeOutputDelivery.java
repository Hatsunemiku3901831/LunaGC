package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;

/** Validate plugin-adjusted results as one batch, then confirm their actual stored identities. */
final class ForgeOutputDelivery {
    private ForgeOutputDelivery() {}

    static int validate(Player player, List<GameItem> items) {
        if (items == null || items.isEmpty()) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        var materials = new ArrayList<ItemParamData>();
        var unique = Collections.newSetFromMap(new IdentityHashMap<GameItem, Boolean>());
        int weapons = 0;
        for (var item : items) {
            if (item == null
                    || item.getCount() <= 0
                    || item.getGuid() != 0
                    || item.getObjectId() != null
                    || item.getOwnerId() != 0
                    || !unique.add(item)) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            var data = GameData.getItemDataMap().get(item.getItemId());
            if (data == null || data != item.getItemData() || data.isUseOnGain())
                return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            if (data.getItemType() == ItemType.ITEM_WEAPON) {
                if (item.getCount() != 1) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                weapons++;
            } else if (data.getItemType() == ItemType.ITEM_MATERIAL) {
                materials.add(new ItemParamData(item.getItemId(), item.getCount()));
            } else return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        }
        if (!materials.isEmpty()) {
            int validation = InvestigationRewardDelivery.validate(player, materials);
            if (validation != 0) return validation;
        }
        if (weapons > 0) {
            var tab = player.getInventory().getInventoryTab(ItemType.ITEM_WEAPON);
            if (tab == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            if ((long) tab.getSize() + weapons > tab.getMaxCapacity())
                return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        }
        return 0;
    }

    /** Caller holds the inventory monitor from validation through receipt and confirmation. */
    static List<ItemHint> grant(Player player, List<GameItem> items) {
        if (validate(player, items) != 0)
            throw new IllegalStateException("Forge output preflight changed");
        var inventory = player.getInventory();
        var hints = new ArrayList<ItemHint>();
        for (var item : items) {
            int amount = item.getCount();
            boolean weapon = item.getItemData().getItemType() == ItemType.ITEM_WEAPON;
            int before = inventory.getItemCountById(item.getItemId());
            if (!inventory.addItem(item))
                throw new IllegalStateException("Forge output was refused");
            GameItem stored;
            if (weapon) {
                stored = inventory.getItemByGuid(item.getGuid());
                if (stored != item || item.getCount() != 1)
                    throw new IllegalStateException("Forge weapon identity changed");
            } else stored = inventory.getItemById(item.getItemId());
            if (stored == null
                    || (long) inventory.getItemCountById(item.getItemId()) - before != amount)
                throw new IllegalStateException("Forge output count changed");
            DatabaseHelper.saveGameSync(stored);
            hints.add(
                    stored.toItemHintProto().toBuilder()
                            .setCount(amount)
                            .setIsNew(item.isNewItem())
                            .build());
        }
        return hints;
    }
}
