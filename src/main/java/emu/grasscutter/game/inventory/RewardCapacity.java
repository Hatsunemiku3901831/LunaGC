package emu.grasscutter.game.inventory;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.activity.ActivityCurrencies;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.shop.BeyondCurrency;

import java.util.*;

/** Capacity projection for a complete rolled reward batch, including an optional resin debit. */
public final class RewardCapacity {
    private RewardCapacity() {}

    /** Caller holds the inventory monitor through this check, payment and delivery. */
    public static boolean canReceive(
            Player player, List<GameItem> rewards, boolean spendsCondensed) {
        if (rewards == null || rewards.isEmpty()) return false;
        var inventory = player.getInventory();
        var cost = inventory.getItemById(220007);
        if (spendsCondensed && (cost == null || cost.getCount() < 1)) return false;
        int freedMaterialSlots = spendsCondensed && cost.getCount() == 1 ? 1 : 0;
        var counts = new HashMap<Integer, Long>();
        var slots = new HashMap<InventoryTab, Integer>();
        var newStacks = new HashMap<InventoryTab, Set<Integer>>();
        for (var item : rewards) {
            if (item == null
                    || item.getCount() <= 0
                    || item.getItemData() == null
                    || item.getItemId() != item.getItemData().getId()) return false;
            var data = item.getItemData();
            long count = counts.merge(item.getItemId(), (long) item.getCount(), Long::sum);
            if (count > Integer.MAX_VALUE) return false;
            if (data.isUseOnGain()) {
                // Plot books unlock the codex directly and have no physical inventory stack.
                if (BookUnlocks.definition(data) == null || item.getCount() != 1) return false;
                continue;
            }
            var type = data.getItemType();
            if (type == ItemType.ITEM_VIRTUAL) {
                if (ActivityCurrencies.contains(item.getItemId())
                        && !ActivityCurrencies.canCredit(player, item.getItemId(), count)) return false;
                long before = virtualBalance(player, item.getItemId());
                if (before == Long.MIN_VALUE || before < 0 || before + count > Integer.MAX_VALUE)
                    return false;
                continue;
            }
            var tab = inventory.getInventoryTab(type);
            if (tab == null) return false;
            if (data.isEquip()) {
                // A rolled equipment instance must have its own GUID and random properties.
                if (item.getCount() != 1) return false;
                slots.merge(tab, 1, Integer::sum);
                continue;
            }
            if (type != ItemType.ITEM_MATERIAL
                    && type != ItemType.ITEM_FURNITURE
                    && type != ItemType.ITEM_TPS_WEAPON) return false;
            if (Set.of(
                            MaterialType.MATERIAL_AVATAR,
                            MaterialType.MATERIAL_FLYCLOAK,
                            MaterialType.MATERIAL_COSTUME,
                            MaterialType.MATERIAL_NAMECARD,
                            MaterialType.MATERIAL_AVATAR_TRACE,
                            MaterialType.MATERIAL_WEAPON_SKIN)
                    .contains(data.getMaterialType())) return false;
            var existing = tab.getItemById(item.getItemId());
            int before = existing == null ? 0 : existing.getCount();
            boolean removedCostStack = spendsCondensed && item.getItemId() == 220007 && before == 1;
            if (spendsCondensed && item.getItemId() == 220007) before--;
            if (before < 0 || data.getStackLimit() <= 0 || before + count > data.getStackLimit())
                return false;
            if (type == ItemType.ITEM_TPS_WEAPON && (existing != null || count != 1)) return false;
            if (existing == null || removedCostStack)
                newStacks.computeIfAbsent(tab, ignored -> new HashSet<>()).add(item.getItemId());
        }
        newStacks.forEach((tab, ids) -> slots.merge(tab, ids.size(), Integer::sum));
        for (var entry : slots.entrySet()) {
            long needed = (long) entry.getKey().getSize() + entry.getValue();
            if (entry.getKey() == inventory.getInventoryTab(ItemType.ITEM_MATERIAL))
                needed -= freedMaterialSlots;
            if (needed > entry.getKey().getMaxCapacity()) return false;
        }
        return true;
    }

    private static long virtualBalance(Player player, int itemId) {
        var property = BeyondCurrency.propertyForItem(itemId);
        if (property != null) return player.getProperty(property);
        if (ActivityCurrencies.contains(itemId)) return ActivityCurrencies.balance(player, itemId);
        return switch (itemId) {
            case 101, 105 -> 0; // Character and companionship EXP are applied to the active team.
            case 102 -> player.getExp();
            case 106 -> player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
            case 107 -> player.getProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
            case 121 -> 0; // Realm EXP follows Inventory's immediate-effect path.
            case 201 -> player.getPrimogems();
            case 202 -> player.getMora();
            case 203 -> player.getCrystals();
            case 204 -> player.getHomeCoin();
            case 207 -> player.getProperty(PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN);
                // Unsupported virtual items must not turn a paid reward into a no-op.
            default -> Long.MIN_VALUE;
        };
    }
}
