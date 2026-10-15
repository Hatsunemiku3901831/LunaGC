package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;

/** Validate every row before a receipt is reserved, then acknowledge actual inventory writes. */
public final class InvestigationRewardDelivery {
    private InvestigationRewardDelivery() {}

    public static int validate(Player player, List<ItemParamData> items) {
        return validate(player, items, 0);
    }

    /** A single material payment may release its stack before these rewards are delivered. */
    public static int validate(Player player, List<ItemParamData> items, int materialSlotsFreed) {
        if (materialSlotsFreed < 0 || materialSlotsFreed > 1) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        if (items == null || items.isEmpty()) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        var counts = new LinkedHashMap<Integer, Long>();
        var slots = new HashMap<InventoryTab, Long>();
        var newStacks = new HashMap<InventoryTab, Set<Integer>>();
        var inventory = player.getInventory();
        for (var item : items) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0)
                return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            var data = GameData.getItemDataMap().get(item.getId());
            if (data == null || data.getId() != item.getId() || data.isUseOnGain())
                return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            long count = counts.merge(item.getId(), (long) item.getCount(), Long::sum);
            if (count > Integer.MAX_VALUE) return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            var type = data.getItemType();
            if (type == ItemType.ITEM_VIRTUAL) {
                int before =
                        switch (item.getId()) {
                            case 102 -> player.getExp();
                            case 201 -> player.getPrimogems();
                            case 202 -> player.getMora();
                            default -> -1;
                        };
                if (before < 0) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                if ((long) before + count > Integer.MAX_VALUE)
                    return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            } else if (type == ItemType.ITEM_RELIQUARY) {
                var tab = inventory.getInventoryTab(type);
                if (tab == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                slots.merge(tab, (long) item.getCount(), Long::sum);
            } else if (type == ItemType.ITEM_MATERIAL) {
                if (data.getStackLimit() <= 0
                        || Set.of(
                                        MaterialType.MATERIAL_AVATAR,
                                        MaterialType.MATERIAL_FLYCLOAK,
                                        MaterialType.MATERIAL_COSTUME,
                                        MaterialType.MATERIAL_NAMECARD,
                                        MaterialType.MATERIAL_AVATAR_TRACE,
                                        MaterialType.MATERIAL_WEAPON_SKIN)
                                .contains(data.getMaterialType()))
                    return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                var tab = inventory.getInventoryTab(type);
                if (tab == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                var existing = tab.getItemById(item.getId());
                if (count + (existing == null ? 0 : existing.getCount()) > data.getStackLimit())
                    return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
                if (existing == null)
                    newStacks.computeIfAbsent(tab, ignored -> new HashSet<>()).add(item.getId());
            } else return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        }
        for (var entry : newStacks.entrySet())
            slots.merge(entry.getKey(), (long) entry.getValue().size(), Long::sum);
        for (var entry : slots.entrySet())
            if ((long) entry.getKey().getSize() + entry.getValue()
                    - (entry.getKey() == inventory.getInventoryTab(ItemType.ITEM_MATERIAL) ? materialSlotsFreed : 0)
                    > entry.getKey().getMaxCapacity()) return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        return 0;
    }

    /** The caller holds the inventory monitor through preflight, receipt and delivery. */
    public static void grant(Player player, List<ItemParamData> items) {
        grant(player, items, ActionReason.InvestigationTargetReward);
    }

    public static void grant(Player player, List<ItemParamData> items, ActionReason reason) {
        if (validate(player, items) != 0)
            throw new IllegalStateException("Handbook reward preflight changed");
        var inventory = player.getInventory();
        for (var param : items) {
            var data = GameData.getItemDataMap().get(param.getId());
            if (data.getItemType() == ItemType.ITEM_RELIQUARY) {
                for (int i = 0; i < param.getCount(); i++) {
                    var relic = new GameItem(data, 1);
                    if (!inventory.addItem(relic, reason)
                            || inventory.getItemByGuid(relic.getGuid()) != relic)
                        throw new IllegalStateException("Handbook relic was not stored");
                    DatabaseHelper.saveGameSync(relic);
                }
            } else {
                int before = count(player, param.getId());
                int levelBefore = player.getLevel();
                if (!inventory.addItem(param, reason))
                    throw new IllegalStateException("Handbook reward was not granted");
                if (param.getId() == 102) {
                    long after = (long) player.getExp();
                    for (int level = levelBefore; level < player.getLevel(); level++) {
                        var definition = GameData.getPlayerLevelDataMap().get(level);
                        if (definition == null)
                            throw new IllegalStateException("Adventure level resource changed");
                        after += definition.getExp();
                    }
                    if (after - before != param.getCount())
                        throw new IllegalStateException("Adventure EXP grant changed");
                } else if ((long) count(player, param.getId()) - before != param.getCount())
                    throw new IllegalStateException("Handbook reward count changed");
                if (data.getItemType() == ItemType.ITEM_MATERIAL)
                    DatabaseHelper.saveGameSync(inventory.getItemById(param.getId()));
            }
        }
        DatabaseHelper.saveGameSync(player);
    }

    private static int count(Player player, int id) {
        return switch (id) {
            case 102 -> player.getExp();
            case 201 -> player.getPrimogems();
            case 202 -> player.getMora();
            default -> player.getInventory().getItemCountById(id);
        };
    }
}
