package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** Refund one unfinished paid furniture job, using its captured material costs. */
public final class FurnitureCancel {
    private FurnitureCancel() {}

    private static void fail(Player player, int makeId, Retcode code) {
        player.getSession().send(new PacketFurnitureMakeCancelRsp(code.getNumber(), makeId, null));
    }

    public static void cancel(Player player, int index, int makeId) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(player, makeId, Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureStart() != null
                        || home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureCancel() != null) {
                    fail(player, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (slots == null || index < 0 || makeId <= 0) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                Set<Integer> indexes = new HashSet<>();
                for (var queued : slots)
                    if (queued == null
                            || queued.getIndex() < 0
                            || !indexes.add(queued.getIndex())) {
                        fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                var matches =
                        slots.stream().filter(s -> s != null && s.getIndex() == index).toList();
                if (matches.size() != 1 || matches.get(0).getMakeId() != makeId) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                var slot = matches.get(0);
                var plan = slot.getPlan();
                if (slot.getBeginTime() <= 0
                        || slot.getDurTime() <= 0
                        || plan == null
                        || !plan.isValidFor(slot)) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                if ((long) slot.getBeginTime() + slot.getDurTime() <= Utils.getCurrentSeconds()) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_IS_FINISH);
                    return;
                }
                List<FurnitureCancelReceipt.Refund> refunds = new ArrayList<>();
                List<GameItem> hints = new ArrayList<>();
                var tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
                int newStacks = 0;
                for (var cost : plan.getMaterials()) {
                    var data = GameData.getItemDataMap().get(cost.getId());
                    var existing = inventory.getItemById(cost.getId());
                    int before = inventory.getItemCountById(cost.getId());
                    if (data == null
                            || data.getId() != cost.getId()
                            || data.getItemType() != ItemType.ITEM_MATERIAL
                            || data.isUseOnGain()
                            || data.getStackLimit() <= 0
                            || before < 0
                            || existing != null && existing.getOwnerId() != player.getUid()) {
                        fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    if ((long) before + cost.getCount() > data.getStackLimit()) {
                        fail(player, makeId, Retcode.RET_MATERIAL_EXCEED_LIMIT);
                        return;
                    }
                    if (existing == null) newStacks++;
                    refunds.add(
                            new FurnitureCancelReceipt.Refund(
                                    cost.getId(), cost.getCount(), before));
                    hints.add(new GameItem(data, cost.getCount()));
                }
                if (tab == null || (long) tab.getSize() + newStacks > tab.getMaxCapacity()) {
                    fail(player, makeId, Retcode.RET_MATERIAL_EXCEED_LIMIT);
                    return;
                }
                int position = slots.indexOf(slot);
                var receipt = new FurnitureCancelReceipt(slot, refunds);
                home.setPendingFurnitureCancel(receipt);
                boolean removed = false;
                try {
                    DatabaseHelper.saveGameSync(home);
                    for (int i = 0; i < refunds.size(); i++) {
                        var refund = refunds.get(i);
                        var data = hints.get(i).getItemData();
                        if (!inventory.addItem(new GameItem(data, refund.getCount()))
                                || (long) inventory.getItemCountById(refund.getItemId())
                                                - refund.getBefore()
                                        != refund.getCount())
                            throw new IllegalStateException(
                                    "Furniture refund was not stored exactly");
                        var stored = inventory.getItemById(refund.getItemId());
                        if (stored == null || stored.getOwnerId() != player.getUid())
                            throw new IllegalStateException("Furniture refund has no owned stack");
                        stored.ensurePersistenceId();
                        DatabaseHelper.saveGameSync(stored);
                        receipt.delivered(refund, stored.getObjectId().toHexString());
                        DatabaseHelper.saveGameSync(home);
                    }
                    receipt.refunded();
                    DatabaseHelper.saveGameSync(home);
                    if (!slots.remove(slot))
                        throw new IllegalStateException("Furniture cancellation source changed");
                    removed = true;
                    receipt.completed();
                    DatabaseHelper.saveGameSync(home);
                    home.setPendingFurnitureCancel(null);
                    DatabaseHelper.saveGameSync(home);
                } catch (RuntimeException failure) {
                    home.setPendingFurnitureCancel(receipt);
                    if (removed && !slots.contains(slot))
                        slots.add(Math.min(position, slots.size()), slot);
                    try {
                        DatabaseHelper.saveGameSync(home);
                    } catch (RuntimeException saving) {
                        failure.addSuppressed(saving);
                    }
                    Grasscutter.getLogger()
                            .error(
                                    "Furniture cancellation needs reconciliation: UID {}, recipe"
                                        + " {}, slot {}",
                                    player.getUid(),
                                    makeId,
                                    index,
                                    failure);
                    fail(player, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                if (!hints.isEmpty())
                    player.sendPacket(
                            new PacketItemAddHintNotify(hints, ActionReason.FurnitureMakeCancel));
                player.getSession()
                        .send(
                                new PacketFurnitureMakeCancelRsp(
                                        0,
                                        makeId,
                                        slots.stream()
                                                .map(FurnitureMakeSlotItem::toProto)
                                                .toList()));
            }
        }
    }
}
