package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** Continues only unclaimed entries whose durable selection and source identity remain intact. */
public final class FurnitureBulkResume {
    private FurnitureBulkResume() {}

    public static boolean resume(Player player) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) return false;
        synchronized (player.getInventory()) {
            synchronized (home) {
                var receipt = home.getPendingFurnitureBulk();
                if (receipt == null
                        || receipt.getState() != 1
                        || home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureCancel() != null
                        || home.getPendingFurnitureStart() != null
                        || !preflight(player, receipt)) return false;
                try {
                    for (var entry : receipt.getEntries()) {
                        if (entry.isCompleted()) continue;
                        var result = FurnitureTake.claim(player, receipt, entry);
                        if (result == null || result.retcode() != 0) return false;
                    }
                    receipt.finished();
                    DatabaseHelper.saveGameSync(home);
                    home.setPendingFurnitureBulk(null);
                    DatabaseHelper.saveGameSync(home);
                    return true;
                } catch (RuntimeException error) {
                    home.setPendingFurnitureBulk(receipt);
                    try {
                        DatabaseHelper.saveGameSync(home);
                    } catch (RuntimeException saving) {
                        error.addSuppressed(saving);
                    }
                    Grasscutter.getLogger()
                            .error(
                                    "Could not finish resumed furnishing batch: UID {}, operation"
                                            + " {}",
                                    player.getUid(),
                                    receipt.getOperationId(),
                                    error);
                    return false;
                }
            }
        }
    }

    private static Map<Integer, Integer> materials(List<FurnitureMakePlan.Material> values) {
        Map<Integer, Integer> result = new HashMap<>();
        for (var value : values)
            if (value == null
                    || value.getId() <= 0
                    || value.getCount() <= 0
                    || result.putIfAbsent(value.getId(), value.getCount()) != null) return null;
        return result;
    }

    private static boolean selection(FurnitureBulkReceipt.Entry entry) {
        var source = entry.getSourceSlot();
        if (source == null
                || source.getIndex() != entry.getIndex()
                || source.getMakeId() != entry.getMakeId()
                || source.getBeginTime() != entry.getBeginTime()
                || source.getDurTime() != entry.getDurTime()
                || source.getAccelerateTime() != entry.getAccelerateTime()
                || source.getAvatarId() < 0
                || source.getBeginTime() <= 0
                || source.getDurTime() < 0
                || source.getAccelerateTime() < 0) return false;
        var plan = source.getPlan();
        if (plan == null) return source.getDurTime() > 0 && entry.getReturns().isEmpty();
        return plan.isValidFor(source)
                && plan.getItemId() == entry.getItemId()
                && plan.getCount() == entry.getCount()
                && plan.getTrustExp() == entry.getTrustExp()
                && Objects.equals(materials(plan.getReturns()), materials(entry.getReturns()));
    }

    private static boolean preflight(Player player, FurnitureBulkReceipt receipt) {
        if (receipt.getOperationId() == null
                || receipt.getOperationId().isBlank()
                || receipt.getEntries() == null
                || receipt.getEntries().isEmpty()) return false;
        var home = player.getHome();
        var slots = home.getFurnitureMakeSlotItemList();
        if (slots == null) return false;
        Map<Integer, FurnitureMakeSlotItem> current = new HashMap<>();
        for (var slot : slots)
            if (slot == null
                    || slot.getIndex() < 0
                    || slot.getAccelerateTime() < 0
                    || current.putIfAbsent(slot.getIndex(), slot) != null) return false;
        Set<Integer> indexes = new HashSet<>(), first = new HashSet<>();
        Map<Integer, Integer> products = new LinkedHashMap<>();
        var capacity = new FurnitureReturns.Capacity(player);
        long trust = 0;
        for (var entry : receipt.getEntries()) {
            if (entry == null
                    || entry.getIndex() < 0
                    || !indexes.add(entry.getIndex())
                    || entry.getMakeId() <= 0
                    || entry.getItemId() <= 0
                    || entry.getCount() <= 0
                    || entry.getTrustExp() < 0
                    || !selection(entry)
                    || materials(entry.getReturns()) == null) return false;
            var slot = current.get(entry.getIndex());
            if (entry.isCompleted()) {
                if (slot != null) return false;
                continue;
            }
            if (slot == null
                    || !entry.matches(slot)
                    || slot.getAvatarId() != entry.getSourceSlot().getAvatarId()) return false;
            var data = GameData.getItemDataMap().get(entry.getItemId());
            if (data == null
                    || data.getId() != entry.getItemId()
                    || data.getItemType() != ItemType.ITEM_FURNITURE
                    || data.isUseOnGain()
                    || data.getStackLimit() <= 0
                    || (long) products.getOrDefault(entry.getItemId(), 0) + entry.getCount()
                            > Integer.MAX_VALUE) return false;
            boolean unfinished = slot.getCompletionTime() > Utils.getCurrentSeconds();
            if (unfinished && (!entry.isFast() || !capacity.spendVial())) return false;
            for (var value : entry.getReturns())
                if (!capacity.add(value.getId(), value.getCount())) return false;
            products.merge(entry.getItemId(), entry.getCount(), Integer::sum);
            if (!home.getCraftedFurnitureIds().contains(entry.getItemId())
                    && first.add(entry.getItemId())) trust += entry.getTrustExp();
        }
        var inventory = player.getInventory();
        var tab = inventory.getInventoryTab(ItemType.ITEM_FURNITURE);
        int added = 0;
        for (var product : products.entrySet()) {
            var data = GameData.getItemDataMap().get(product.getKey());
            var existing = inventory.getItemById(product.getKey());
            int before = inventory.getItemCountById(product.getKey());
            if (before < 0
                    || (long) before + product.getValue() > data.getStackLimit()
                    || existing != null && existing.getOwnerId() != player.getUid()) return false;
            if (existing == null) added++;
        }
        return tab != null
                && (long) tab.getSize() + added <= tab.getMaxCapacity()
                && trust <= Integer.MAX_VALUE
                && FurnitureTake.trustAfter(home, (int) trust) != null;
    }
}
