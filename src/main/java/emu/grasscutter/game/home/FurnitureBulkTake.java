package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.FurnitureMakeTakeInfoOuterClass.FurnitureMakeTakeInfo;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketTakeFurnitureMakeAllRsp;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** Preflight and journal the entire selected batch, then reuse acknowledged single claims. */
public final class FurnitureBulkTake {
    private FurnitureBulkTake() {}

    private static void fail(Player player, Retcode code) {
        player.getSession().send(new PacketTakeFurnitureMakeAllRsp(code.getNumber(), null, null));
    }

    public static void take(Player player, List<FurnitureMakeTakeInfo> selections) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(player, Retcode.RET_HOME_PLAYER_NOT_IN_SELF_HOME_WORLD);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureBulk() != null
                        || home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureStart() != null
                        || home.getPendingFurnitureCancel() != null) {
                    fail(player, Retcode.RET_SVR_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (selections == null
                        || selections.isEmpty()
                        || slots == null
                        || selections.size() > slots.size()) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                Set<Integer> indexes = new HashSet<>();
                for (var slot : slots)
                    if (slot == null || slot.getIndex() < 0 || !indexes.add(slot.getIndex())) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                List<FurnitureBulkReceipt.Entry> entries = new ArrayList<>();
                Map<Integer, Integer> totals = new LinkedHashMap<>();
                Set<Integer> selected = new HashSet<>(), firstCrafts = new HashSet<>();
                int vials = 0;
                long trust = 0;
                for (var selection : selections) {
                    if (selection == null
                            || selection.getIndex() < 0
                            || !selected.add(selection.getIndex())) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                        return;
                    }
                    var slot =
                            slots.stream()
                                    .filter(
                                            s ->
                                                    s.getIndex() == selection.getIndex()
                                                            && s.getMakeId()
                                                                    == selection.getMakeId())
                                    .findFirst()
                                    .orElse(null);
                    if (slot == null) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                        return;
                    }
                    var plan = slot.getPlan();
                    int itemId, count, exp;
                    if (plan != null) {
                        if (!plan.isValidFor(slot)) {
                            fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                            return;
                        }
                        itemId = plan.getItemId();
                        count = plan.getCount();
                        exp = plan.getTrustExp();
                    } else {
                        var recipe =
                                GameData.getFurnitureMakeConfigDataMap().get(selection.getMakeId());
                        if (recipe == null
                                || recipe.getId() != selection.getMakeId()
                                || recipe.getCount() <= 0
                                || recipe.getExp() < 0) {
                            fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                            return;
                        }
                        itemId = recipe.getFurnitureItemID();
                        count = recipe.getCount();
                        exp = recipe.getExp();
                    }
                    var data = GameData.getItemDataMap().get(itemId);
                    if (slot.getBeginTime() <= 0
                            || slot.getDurTime() <= 0
                            || data == null
                            || data.getId() != itemId
                            || data.getItemType() != ItemType.ITEM_FURNITURE
                            || data.isUseOnGain()
                            || data.getStackLimit() <= 0
                            || (long) totals.getOrDefault(itemId, 0) + count > Integer.MAX_VALUE) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    boolean unfinished =
                            (long) slot.getBeginTime() + slot.getDurTime()
                                    > Utils.getCurrentSeconds();
                    if (unfinished && !selection.getIsFastFinish()) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                        return;
                    }
                    if (unfinished) vials++;
                    totals.merge(itemId, count, Integer::sum);
                    if (!home.getCraftedFurnitureIds().contains(itemId) && firstCrafts.add(itemId))
                        trust += exp;
                    entries.add(
                            new FurnitureBulkReceipt.Entry(
                                    slot, selection.getIsFastFinish(), itemId, count, exp));
                }
                var tab = inventory.getInventoryTab(ItemType.ITEM_FURNITURE);
                int newStacks = 0;
                for (var total : totals.entrySet()) {
                    var data = GameData.getItemDataMap().get(total.getKey());
                    var existing = inventory.getItemById(total.getKey());
                    int before = inventory.getItemCountById(total.getKey());
                    if (before < 0
                            || (long) before + total.getValue() > data.getStackLimit()
                            || existing != null && existing.getOwnerId() != player.getUid()) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    if (existing == null) newStacks++;
                }
                if (tab == null
                        || (long) tab.getSize() + newStacks > tab.getMaxCapacity()
                        || trust > Integer.MAX_VALUE
                        || FurnitureTake.trustAfter(home, (int) trust) == null) {
                    fail(player, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                if (vials > 0) {
                    var data = GameData.getItemDataMap().get(107013);
                    var item = inventory.getItemById(107013);
                    if (data == null
                            || data.getId() != 107013
                            || data.getItemType() != ItemType.ITEM_MATERIAL
                            || item == null
                            || item.getOwnerId() != player.getUid()
                            || inventory.getItemCountById(107013) < vials) {
                        fail(player, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                        return;
                    }
                }
                var receipt = new FurnitureBulkReceipt(entries);
                home.setPendingFurnitureBulk(receipt);
                try {
                    DatabaseHelper.saveGameSync(home);
                    for (var entry : entries) {
                        var result = FurnitureTake.claim(player, receipt, entry);
                        if (result == null || result.retcode() != 0)
                            throw new IllegalStateException(
                                    "Furniture batch entry was not confirmed");
                    }
                    receipt.finished();
                    DatabaseHelper.saveGameSync(home);
                    home.setPendingFurnitureBulk(null);
                    DatabaseHelper.saveGameSync(home);
                } catch (RuntimeException failure) {
                    home.setPendingFurnitureBulk(receipt);
                    try {
                        DatabaseHelper.saveGameSync(home);
                    } catch (RuntimeException saving) {
                        failure.addSuppressed(saving);
                    }
                    Grasscutter.getLogger()
                            .error(
                                    "Furniture batch needs reconciliation: UID {}, operation {}",
                                    player.getUid(),
                                    receipt.getOperationId(),
                                    failure);
                    fail(player, Retcode.RET_SVR_ERROR);
                    return;
                }
                var outputs =
                        totals.entrySet().stream()
                                .map(
                                        e ->
                                                ItemParam.newBuilder()
                                                        .setItemId(e.getKey())
                                                        .setCount(e.getValue())
                                                        .build())
                                .toList();
                player.getSession()
                        .send(
                                new PacketTakeFurnitureMakeAllRsp(
                                        0,
                                        outputs,
                                        slots.stream()
                                                .map(FurnitureMakeSlotItem::toProto)
                                                .toList()));
            }
        }
    }
}
