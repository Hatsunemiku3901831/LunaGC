package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;

import org.bson.types.ObjectId;

import java.util.*;

/**
 * Completes confirmed queue bookkeeping without replaying inventory, trust or mission mutations.
 */
public final class FurnitureCompletedRecovery {
    private FurnitureCompletedRecovery() {}

    private record Change(
            List<FurnitureMakeSlotItem> slots,
            boolean take,
            boolean cancel,
            boolean bulk,
            boolean complete) {}

    public static boolean recover(Player player) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) return false;
        synchronized (player.getInventory()) {
            synchronized (home) {
                var take = home.getPendingFurnitureTake();
                var cancel = home.getPendingFurnitureCancel();
                var bulk = home.getPendingFurnitureBulk();
                if (home.getPendingFurnitureStart() != null) {
                    if (take != null || cancel != null || bulk != null) return false;
                    return FurnitureStartRecovery.recover(player);
                }
                if (take == null && cancel == null && bulk == null) return true;
                var before = home.getFurnitureMakeSlotItemList();
                if (!validQueue(before) || cancel != null && (take != null || bulk != null))
                    return false;
                var slots = new ArrayList<>(before);
                Change change;
                if (cancel != null) {
                    if (!validCancel(cancel)
                            || !remove(slots, cancel.getIndex(), cancel.getSourceSlot()))
                        return false;
                    change = new Change(slots, false, true, false, true);
                } else {
                    if (take != null) {
                        if (!validTake(home, take)
                                || !remove(slots, take.getIndex(), take.getSourceSlot()))
                            return false;
                        if (bulk == null && take.getParentOperationId() != null
                                || bulk != null && !belongs(take, bulk)) return false;
                    }
                    if (bulk == null) {
                        change = new Change(slots, true, false, false, true);
                    } else {
                        if (!validBulk(bulk)) return false;
                        boolean completed =
                                bulk.getEntries().stream()
                                        .allMatch(FurnitureBulkReceipt.Entry::isCompleted);
                        if (bulk.getState() == 2 && !completed) return false;
                        for (var entry : bulk.getEntries())
                            if (entry.isCompleted()
                                    && slots.stream()
                                            .anyMatch(s -> s.getIndex() == entry.getIndex()))
                                return false;
                        if (!completed && take == null) return FurnitureBulkResume.resume(player);
                        change = new Change(slots, take != null, false, completed, completed);
                    }
                }
                home.setFurnitureMakeSlotItemList(change.slots());
                if (change.take()) home.setPendingFurnitureTake(null);
                if (change.cancel()) home.setPendingFurnitureCancel(null);
                if (change.bulk()) home.setPendingFurnitureBulk(null);
                try {
                    DatabaseHelper.saveGameSync(home);
                } catch (RuntimeException error) {
                    home.setFurnitureMakeSlotItemList(before);
                    home.setPendingFurnitureTake(take);
                    home.setPendingFurnitureCancel(cancel);
                    home.setPendingFurnitureBulk(bulk);
                    Grasscutter.getLogger()
                            .error(
                                    "Could not confirm completed furniture recovery for UID {}",
                                    player.getUid(),
                                    error);
                    return false;
                }
                return change.complete() || FurnitureBulkResume.resume(player);
            }
        }
    }

    private static boolean validQueue(List<FurnitureMakeSlotItem> slots) {
        if (slots == null) return false;
        Set<Integer> indexes = new HashSet<>();
        return slots.stream()
                .allMatch(
                        s ->
                                s != null
                                        && s.getIndex() >= 0
                                        && s.getAccelerateTime() >= 0
                                        && indexes.add(s.getIndex()));
    }

    private static boolean remove(
            List<FurnitureMakeSlotItem> slots, int index, FurnitureMakeSlotItem source) {
        var current = slots.stream().filter(s -> s.getIndex() == index).findFirst().orElse(null);
        if (current == null) return true;
        if (source == null || !JsonUtils.encode(source).equals(JsonUtils.encode(current)))
            return false;
        slots.remove(current);
        return true;
    }

    private static boolean operation(String id) {
        return id != null && !id.isBlank();
    }

    private static boolean object(String id) {
        return id != null && ObjectId.isValid(id);
    }

    private static boolean amount(int before, int count) {
        return before >= 0 && count > 0 && (long) before + count <= Integer.MAX_VALUE;
    }

    private static boolean source(
            FurnitureMakeSlotItem source, int index, int make, int accelerate) {
        return source.getIndex() == index
                && source.getMakeId() == make
                && source.getAccelerateTime() == accelerate
                && source.getAvatarId() >= 0
                && source.getBeginTime() > 0
                && source.getDurTime() >= 0;
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

    private static boolean validCancel(FurnitureCancelReceipt receipt) {
        if (receipt.getState() != 2 && receipt.getState() != 3
                || !operation(receipt.getOperationId())
                || receipt.getIndex() < 0
                || receipt.getMakeId() <= 0
                || receipt.getAccelerateTime() < 0
                || receipt.getRefunds() == null) return false;
        Map<Integer, Integer> refunded = new HashMap<>();
        Set<String> objects = new HashSet<>();
        for (var value : receipt.getRefunds())
            if (value == null
                    || !value.isDelivered()
                    || !object(value.getObjectId())
                    || !objects.add(value.getObjectId())
                    || value.getItemId() <= 0
                    || !amount(value.getBefore(), value.getCount())
                    || refunded.putIfAbsent(value.getItemId(), value.getCount()) != null)
                return false;
        var source = receipt.getSourceSlot();
        return source == null
                || source(
                                source,
                                receipt.getIndex(),
                                receipt.getMakeId(),
                                receipt.getAccelerateTime())
                        && source.getPlan() != null
                        && source.getPlan().isValidFor(source)
                        && refunded.equals(materials(source.getPlan().getMaterials()));
    }

    private static Map<Integer, Integer> returned(FurnitureTakeReceipt receipt) {
        Map<Integer, Integer> result = new HashMap<>();
        Set<String> objects = new HashSet<>();
        for (var value : receipt.getReturns())
            if (value == null
                    || !value.isDelivered()
                    || !object(value.getObjectId())
                    || !objects.add(value.getObjectId())
                    || value.getItemId() <= 0
                    || !amount(value.getBefore(), value.getCount())
                    || result.putIfAbsent(value.getItemId(), value.getCount()) != null) return null;
        return result;
    }

    private static boolean validTake(GameHome home, FurnitureTakeReceipt receipt) {
        if (receipt.getState() != 5
                || !operation(receipt.getOperationId())
                || !object(receipt.getProductObjectId())
                || !receipt.isMissionApplied()
                || receipt.getIndex() < 0
                || receipt.getMakeId() <= 0
                || receipt.getAvatarId() < 0
                || receipt.getBeginTime() <= 0
                || receipt.getDurTime() < 0
                || receipt.getAccelerateTime() < 0
                || receipt.getItemId() <= 0
                || !amount(receipt.getItemBefore(), receipt.getCount())
                || receipt.getVialBefore() < 0
                || receipt.isNeedsVial() && receipt.getVialBefore() == 0
                || receipt.getTrustExp() < 0
                || receipt.getLevelBefore() <= 0
                || receipt.getExpBefore() < 0
                || receipt.getLevelAfter() < receipt.getLevelBefore()
                || receipt.getExpAfter() < 0
                || home.getLevel() < receipt.getLevelAfter()
                || home.getLevel() == receipt.getLevelAfter()
                        && home.getExp() < receipt.getExpAfter()
                || home.getCraftedFurnitureIds() == null
                || !home.getCraftedFurnitureIds().contains(receipt.getItemId())) return false;
        var returned = returned(receipt);
        if (returned == null) return false;
        var source = receipt.getSourceSlot();
        if (source == null) return true;
        if (!source(source, receipt.getIndex(), receipt.getMakeId(), receipt.getAccelerateTime())
                || source.getAvatarId() != receipt.getAvatarId()
                || source.getBeginTime() != receipt.getBeginTime()
                || source.getDurTime() != receipt.getDurTime()) return false;
        var plan = source.getPlan();
        // A batch child delivers the parent's frozen selection. Its current source plan may
        // legitimately differ; belongs() below verifies the durable parent and output values.
        return receipt.getParentOperationId() != null
                || plan == null
                || plan.isValidFor(source)
                        && plan.getItemId() == receipt.getItemId()
                        && plan.getCount() == receipt.getCount()
                        && receipt.getTrustExp()
                                == (receipt.isFirstCraft() ? plan.getTrustExp() : 0)
                        && returned.equals(materials(plan.getReturns()));
    }

    private static boolean validBulk(FurnitureBulkReceipt receipt) {
        if (receipt.getState() != 1 && receipt.getState() != 2
                || !operation(receipt.getOperationId())
                || receipt.getEntries() == null
                || receipt.getEntries().isEmpty()) return false;
        Set<Integer> indexes = new HashSet<>();
        for (var entry : receipt.getEntries())
            if (entry == null
                    || entry.getIndex() < 0
                    || !indexes.add(entry.getIndex())
                    || entry.getMakeId() <= 0
                    || entry.getBeginTime() <= 0
                    || entry.getDurTime() < 0
                    || entry.getAccelerateTime() < 0
                    || entry.getItemId() <= 0
                    || entry.getCount() <= 0
                    || entry.getTrustExp() < 0
                    || materials(entry.getReturns()) == null) return false;
        return true;
    }

    private static boolean belongs(FurnitureTakeReceipt take, FurnitureBulkReceipt bulk) {
        if (!Objects.equals(take.getParentOperationId(), bulk.getOperationId())
                || !operation(take.getParentOperationId())
                || !validBulk(bulk)) return false;
        var entry =
                bulk.getEntries().stream()
                        .filter(e -> e.getIndex() == take.getIndex())
                        .findFirst()
                        .orElse(null);
        return entry != null
                && entry.isCompleted()
                && entry.getMakeId() == take.getMakeId()
                && entry.getBeginTime() == take.getBeginTime()
                && entry.getDurTime() == take.getDurTime()
                && entry.getAccelerateTime() == take.getAccelerateTime()
                && entry.getItemId() == take.getItemId()
                && entry.getCount() == take.getCount()
                && take.getTrustExp() == (take.isFirstCraft() ? entry.getTrustExp() : 0)
                && Objects.equals(materials(entry.getReturns()), returned(take));
    }
}
