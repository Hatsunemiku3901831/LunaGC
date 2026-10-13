package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;

import org.bson.types.ObjectId;

import java.util.*;

/** Restores confirmed starts; uncertain payments and conflicting jobs stay reserved. */
public final class FurnitureStartRecovery {
    private FurnitureStartRecovery() {}

    public static boolean recover(Player player) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) return false;
        synchronized (player.getInventory()) {
            synchronized (home) {
                var receipt = home.getPendingFurnitureStart();
                if (receipt == null) return true;
                var original = home.getFurnitureMakeSlotItemList();
                var restored = restoredQueue(home, receipt);
                if (restored == null) return false;
                home.setFurnitureMakeSlotItemList(restored);
                home.setPendingFurnitureStart(null);
                try {
                    DatabaseHelper.saveGameSync(home);
                    return true;
                } catch (RuntimeException error) {
                    home.setFurnitureMakeSlotItemList(original);
                    home.setPendingFurnitureStart(receipt);
                    Grasscutter.getLogger()
                            .error(
                                    "Could not confirm furniture start recovery: UID {}, operation"
                                            + " {}",
                                    player.getUid(),
                                    receipt.getOperationId(),
                                    error);
                    return false;
                }
            }
        }
    }

    private static List<FurnitureMakeSlotItem> restoredQueue(
            GameHome home, FurnitureStartReceipt receipt) {
        if (receipt.getState() != 2 && receipt.getState() != 3
                || receipt.getOperationId() == null
                || receipt.getOperationId().isBlank()
                || home.getPendingFurnitureTake() != null
                || home.getPendingFurnitureCancel() != null
                || home.getPendingFurnitureBulk() != null
                || receipt.getCosts() == null
                || receipt.getJobs().isEmpty()
                || home.getFurnitureMakeSlotItemList() == null) return null;
        Map<Integer, FurnitureMakeSlotItem> live = new HashMap<>();
        for (var slot : home.getFurnitureMakeSlotItemList()) {
            if (slot == null
                    || slot.getIndex() < 0
                    || slot.getAccelerateTime() < 0
                    || live.putIfAbsent(slot.getIndex(), slot) != null) return null;
        }
        Set<Integer> indexes = new HashSet<>();
        Map<Integer, Integer> paid = new HashMap<>();
        int present = 0;
        for (var job : receipt.getJobs()) {
            if (job == null
                    || job.getIndex() < 0
                    || !indexes.add(job.getIndex())
                    || job.getAvatarId() < 0
                    || job.getBeginTime() <= 0
                    || job.getAccelerateTime() != 0
                    || job.getPlan() == null
                    || !job.getPlan().isValidFor(job)) return null;
            var actual = live.get(job.getIndex());
            // Encode only the bounded job/plan, never the home and its player reference.
            if (actual == null) {
                if (receipt.getState() == 3) return null;
            } else {
                if (!JsonUtils.encode(job).equals(JsonUtils.encode(actual))) return null;
                present++;
            }
            for (var material : job.getPlan().getMaterials()) {
                long total = (long) paid.getOrDefault(material.getId(), 0) + material.getCount();
                if (total > Integer.MAX_VALUE) return null;
                paid.put(material.getId(), (int) total);
            }
        }
        var first = receipt.getJobs().get(0);
        if (first.getIndex() != receipt.getIndex()
                || first.getMakeId() != receipt.getMakeId()
                || first.getAvatarId() != receipt.getAvatarId()
                || first.getBeginTime() != receipt.getBeginTime()
                || first.getDurTime() != receipt.getDurTime()) return null;
        Map<Integer, Integer> costs = new HashMap<>();
        Set<String> objects = new HashSet<>();
        for (var cost : receipt.getCosts()) {
            if (cost == null
                    || cost.getItemId() <= 0
                    || cost.getCount() <= 0
                    || cost.getBefore() < cost.getCount()
                    || cost.getObjectId() == null
                    || !ObjectId.isValid(cost.getObjectId())
                    || !objects.add(cost.getObjectId())
                    || costs.putIfAbsent(cost.getItemId(), cost.getCount()) != null) return null;
        }
        if (!paid.equals(costs) || present != 0 && present != receipt.getJobs().size()) return null;
        if (present == receipt.getJobs().size()) return home.getFurnitureMakeSlotItemList();
        // State 2 is written only after every original material payment was acknowledged.
        // Persist the queue and cleared reservation together; do not debit the current balances.
        var restored = new ArrayList<>(home.getFurnitureMakeSlotItemList());
        restored.addAll(receipt.getJobs());
        return restored;
    }
}
