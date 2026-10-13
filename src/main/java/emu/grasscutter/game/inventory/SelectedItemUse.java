package emu.grasscutter.game.inventory;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.shop.BeyondCurrency;
import emu.grasscutter.game.shop.SpecialCosmeticShop;

import java.util.*;

/** Owned choice boxes reserve their selection before payment, then confirm every delivered unit. */
public final class SelectedItemUse {
    private SelectedItemUse() {}

    public static boolean use(
            Player player, GameItem source, UseItemParams params, ItemUseSelectItems action) {
        if (player == null
                || source == null
                || params == null
                || action == null
                || params.itemUseTarget != ItemUseTarget.ITEM_USE_TARGET_NONE) return false;
        var inventory = player.getInventory();
        synchronized (inventory) {
            if (player.getPendingItemUse() != null) return false;
            List<ItemParamData> products;
            try {
                products = action.resolveItems(params);
                if (!preflight(player, source, params.count, products)) return false;
            } catch (IllegalArgumentException invalid) {
                return false;
            }
            var receipt =
                    new ItemUseReceipt(
                            source,
                            params.count,
                            params.optionId,
                            action.getItemUseOp().getValue(),
                            products);
            player.setPendingItemUse(receipt);
            try {
                DatabaseHelper.saveGameSync(player);
                if (inventory.getItemByGuid(source.getGuid()) != source
                        || source.getOwnerId() != player.getUid()
                        || !inventory.removeItem(source, params.count)
                        || receipt.getSourceBefore() - source.getCount() != params.count
                        || source.getCount() > 0
                                && inventory.getItemByGuid(source.getGuid()) != source
                        || source.getCount() == 0
                                && inventory.getItemByGuid(source.getGuid()) != null)
                    throw new IllegalStateException("Choice box payment was not deducted exactly");
                DatabaseHelper.saveGameSync(source);
                receipt.paid();
                DatabaseHelper.saveGameSync(player);
                for (var product : products) {
                    var data = GameData.getItemDataMap().get(product.getId());
                    if (!SpecialCosmeticShop.kind(product.getId()).isEmpty()) {
                        if (!SpecialCosmeticShop.grant(player, product.getId())
                                || !SpecialCosmeticShop.owned(player, product.getId()))
                            throw new IllegalStateException("Selected cosmetic was not unlocked");
                        DatabaseHelper.saveGameSync(player);
                        receipt.delivered(product.getId(), product.getCount(), null);
                    } else if (data.getItemType() == ItemType.ITEM_WEAPON) {
                        for (int i = 0; i < product.getCount(); i++) {
                            var item = new GameItem(data, 1);
                            item.ensurePersistenceId();
                            int before = count(player, product.getId());
                            if (!inventory.addItem(item, ActionReason.Shop)
                                    || inventory.getItemByGuid(item.getGuid()) != item
                                    || item.getOwnerId() != player.getUid()
                                    || item.getCount() != 1
                                    || (long) count(player, product.getId()) - before != 1)
                                throw new IllegalStateException(
                                        "Selected weapon was not stored exactly");
                            DatabaseHelper.saveGameSync(item);
                            receipt.delivered(product.getId(), 1, item);
                        }
                    } else {
                        int before = count(player, product.getId());
                        if (!inventory.addItem(
                                        product.getId(), product.getCount(), ActionReason.Shop)
                                || (long) count(player, product.getId()) - before
                                        != product.getCount())
                            throw new IllegalStateException(
                                    "Selected product was not fully stored");
                        GameItem stored = null;
                        if (data.getItemType() == ItemType.ITEM_MATERIAL
                                || data.getItemType() == ItemType.ITEM_FURNITURE) {
                            stored = inventory.getItemById(product.getId());
                            if (stored == null || stored.getOwnerId() != player.getUid())
                                throw new IllegalStateException(
                                        "Selected material has no owned stack");
                            stored.ensurePersistenceId();
                            DatabaseHelper.saveGameSync(stored);
                        }
                        receipt.delivered(product.getId(), product.getCount(), stored);
                    }
                }
                receipt.completed();
                DatabaseHelper.saveGameSync(player);
                player.setPendingItemUse(null);
                DatabaseHelper.saveGameSync(player);
                return true;
            } catch (RuntimeException failure) {
                player.setPendingItemUse(receipt);
                try {
                    DatabaseHelper.saveGameSync(player);
                } catch (RuntimeException saveFailure) {
                    failure.addSuppressed(saveFailure);
                }
                Grasscutter.getLogger()
                        .error(
                                "Item use needs reconciliation: UID {}, item {}, option {}",
                                player.getUid(),
                                source.getItemId(),
                                params.optionId,
                                failure);
                return false;
            }
        }
    }

    private static boolean preflight(
            Player player, GameItem source, int used, List<ItemParamData> products) {
        var inventory = player.getInventory();
        if (used <= 0
                || source.getCount() < used
                || products == null
                || products.isEmpty()
                || source.getItemType() != ItemType.ITEM_MATERIAL) return false;
        var slots = new EnumMap<ItemType, Long>(ItemType.class);
        for (var product : products) {
            var data = GameData.getItemDataMap().get(product.getId());
            if (!SpecialCosmeticShop.kind(product.getId()).isEmpty()) {
                if (product.getCount() != 1
                        || SpecialCosmeticShop.owned(player, product.getId())
                        || data != null && !SpecialCosmeticShop.canPurchase(player, data, 1))
                    return false;
                continue;
            }
            if (product.getCount() <= 0
                    || data == null
                    || data.getId() != product.getId()
                    || data.isUseOnGain()) return false;
            int before = count(player, product.getId());
            long after =
                    (long) before
                            - (product.getId() == source.getItemId() ? used : 0)
                            + product.getCount();
            if (before < 0 || after < 0 || after > Integer.MAX_VALUE) return false;
            if (data.getItemType() == ItemType.ITEM_WEAPON)
                slots.merge(ItemType.ITEM_WEAPON, (long) product.getCount(), Long::sum);
            else if (data.getItemType() == ItemType.ITEM_MATERIAL
                    || data.getItemType() == ItemType.ITEM_FURNITURE) {
                if (data.getStackLimit() <= 0
                        || after > data.getStackLimit()
                        || Set.of(
                                        MaterialType.MATERIAL_AVATAR,
                                        MaterialType.MATERIAL_FLYCLOAK,
                                        MaterialType.MATERIAL_COSTUME,
                                        MaterialType.MATERIAL_NAMECARD,
                                        MaterialType.MATERIAL_AVATAR_TRACE,
                                        MaterialType.MATERIAL_WEAPON_SKIN)
                                .contains(data.getMaterialType())) return false;
                long remaining = (long) before - (product.getId() == source.getItemId() ? used : 0);
                if (remaining == 0) slots.merge(data.getItemType(), 1L, Long::sum);
            } else if (data.getItemType() == ItemType.ITEM_VIRTUAL) {
                if (product.getId() == 106
                        && (!emu.grasscutter.config.Configuration.GAME_OPTIONS
                                        .resinOptions
                                        .resinUsage
                                || after > player.getPropertyMax(PlayerProperty.PROP_PLAYER_RESIN)))
                    return false;
                if (product.getId() == 107
                        && after > player.getPropertyMax(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY))
                    return false;
            } else return false;
        }
        for (var entry : slots.entrySet()) {
            var tab = inventory.getInventoryTab(entry.getKey());
            int freed =
                    entry.getKey() == ItemType.ITEM_MATERIAL && source.getCount() == used ? 1 : 0;
            if (tab == null
                    || (long) tab.getSize() - freed + entry.getValue() > tab.getMaxCapacity())
                return false;
        }
        return true;
    }

    private static int count(Player player, int id) {
        var data = GameData.getItemDataMap().get(id);
        if (data == null) return -1;
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
}
