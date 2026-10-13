package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeStartRsp;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** A learned furniture job starts only after a saved reservation and exact material payment. */
public final class FurnitureStart {
    private FurnitureStart() {}

    private static void fail(Player player, Retcode code) {
        player.getSession().send(new PacketFurnitureMakeStartRsp(code.getNumber(), null));
    }

    public static void start(Player player, int makeId, int avatarId) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(player, Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureStart() != null
                        || home.getPendingFurnitureCancel() != null
                        || home.getPendingFurnitureBulk() != null) {
                    fail(player, Retcode.RET_SVR_ERROR);
                    return;
                }
                var recipe = GameData.getFurnitureMakeConfigDataMap().get(makeId);
                var product =
                        recipe == null
                                ? null
                                : GameData.getItemDataMap().get(recipe.getFurnitureItemID());
                if (recipe == null
                        || recipe.getId() != makeId
                        || recipe.getCount() <= 0
                        || recipe.getMakeTime() <= 0
                        || recipe.getExp() < 0
                        || product == null
                        || product.getId() != recipe.getFurnitureItemID()
                        || product.getItemType() != ItemType.ITEM_FURNITURE
                        || product.isUseOnGain()
                        || product.getStackLimit() < recipe.getCount()
                        || recipe.getMaterialItems() == null) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                if (!player.getUnlockedFurniture().contains(product.getId())) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_LOCKED);
                    return;
                }
                if (avatarId < 0
                        || avatarId != 0 && player.getAvatars().getAvatarById(avatarId) == null) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                var level = GameData.getHomeWorldLevelDataMap().get(home.getLevel());
                if (level == null
                        || level.getId() != home.getLevel()
                        || level.getFurnitureMakeSlotCount() <= 0
                        || level.getFurnitureMakeTimeReduction() < 0) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (slots == null) slots = new ArrayList<>();
                Set<Integer> used = new HashSet<>();
                for (var slot : slots) {
                    if (slot == null || slot.getIndex() < 0 || !used.add(slot.getIndex())) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                }
                if (slots.size() >= level.getFurnitureMakeSlotCount()) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_SLOT_FULL);
                    return;
                }
                Map<Integer, Integer> totals = new LinkedHashMap<>();
                for (var cost : recipe.getMaterialItems()) {
                    if (cost == null
                            || cost.getId() <= 0
                            || cost.getCount() <= 0
                            || (long) totals.getOrDefault(cost.getId(), 0) + cost.getCount()
                                    > Integer.MAX_VALUE) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    totals.merge(cost.getId(), cost.getCount(), Integer::sum);
                }
                List<FurnitureStartReceipt.Cost> costs = new ArrayList<>();
                for (var entry : totals.entrySet()) {
                    var data = GameData.getItemDataMap().get(entry.getKey());
                    var item = inventory.getItemById(entry.getKey());
                    int before = inventory.getItemCountById(entry.getKey());
                    if (data == null
                            || data.getId() != entry.getKey()
                            || data.getItemType() != ItemType.ITEM_MATERIAL
                            || data.isUseOnGain()) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    if (item == null
                            || item.getOwnerId() != player.getUid()
                            || before < entry.getValue()) {
                        fail(player, Retcode.RET_HOME_FURNITURE_COUNT_NOT_ENOUGH);
                        return;
                    }
                    item.ensurePersistenceId();
                    costs.add(
                            new FurnitureStartReceipt.Cost(
                                    entry.getKey(),
                                    entry.getValue(),
                                    before,
                                    item.getObjectId().toHexString()));
                }
                int index = 0;
                while (used.contains(index)) index++;
                int duration =
                        Math.max(0, recipe.getMakeTime() - level.getFurnitureMakeTimeReduction());
                var slot =
                        FurnitureMakeSlotItem.of()
                                .index(index)
                                .makeId(makeId)
                                .avatarId(avatarId)
                                .beginTime(Utils.getCurrentSeconds())
                                .durTime(duration)
                                .plan(
                                        new FurnitureMakePlan(
                                                makeId,
                                                product.getId(),
                                                recipe.getCount(),
                                                duration,
                                                recipe.getExp(),
                                                totals))
                                .build();
                var receipt = new FurnitureStartReceipt(slot, costs);
                home.setFurnitureMakeSlotItemList(slots);
                home.setPendingFurnitureStart(receipt);
                try {
                    DatabaseHelper.saveGameSync(home);
                    for (var cost : costs) {
                        var item = inventory.getItemById(cost.getItemId());
                        if (item == null
                                || !item.getObjectId().toHexString().equals(cost.getObjectId())
                                || !inventory.payItem(cost.getItemId(), cost.getCount())
                                || (long) cost.getBefore()
                                                - inventory.getItemCountById(cost.getItemId())
                                        != cost.getCount())
                            throw new IllegalStateException(
                                    "Furniture material payment not confirmed");
                        DatabaseHelper.saveGameSync(item);
                    }
                    receipt.paid();
                    DatabaseHelper.saveGameSync(home);
                    slots.add(slot);
                    receipt.queued();
                    DatabaseHelper.saveGameSync(home);
                    home.setPendingFurnitureStart(null);
                    DatabaseHelper.saveGameSync(home);
                } catch (RuntimeException failure) {
                    home.setPendingFurnitureStart(receipt);
                    try {
                        DatabaseHelper.saveGameSync(home);
                    } catch (RuntimeException saving) {
                        failure.addSuppressed(saving);
                    }
                    Grasscutter.getLogger()
                            .error(
                                    "Furniture start needs reconciliation: UID {}, recipe {}, slot"
                                            + " {}",
                                    player.getUid(),
                                    makeId,
                                    index,
                                    failure);
                    fail(player, Retcode.RET_SVR_ERROR);
                    return;
                }
                player.getSession()
                        .send(
                                new PacketFurnitureMakeStartRsp(
                                        0,
                                        slots.stream()
                                                .map(FurnitureMakeSlotItem::toProto)
                                                .toList()));
            }
        }
    }
}
