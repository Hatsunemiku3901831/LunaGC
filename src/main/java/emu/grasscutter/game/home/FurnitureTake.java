package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import java.util.*;
import java.util.function.Consumer;

/** One acknowledged furniture claim, including speedup, storage and first-craft progress. */
public final class FurnitureTake {
    private FurnitureTake() {}

    record Result(
            int retcode,
            int makeId,
            List<ItemParam> outputs,
            List<emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData>
                    remaining) {}

    private static void fail(Consumer<Result> reply, int makeId, Retcode code) {
        reply.accept(new Result(code.getNumber(), makeId, null, null));
    }

    static int[] trustAfter(GameHome home, int award) {
        if (home.getLevel() <= 0 || home.getExp() < 0 || award < 0) return null;
        long exp = (long) home.getExp() + award;
        int level = home.getLevel();
        while (true) {
            var definition = GameData.getHomeWorldLevelDataMap().get(level);
            if (definition == null || definition.getId() != level || definition.getExp() < 0)
                return null;
            int threshold = definition.getExp();
            if (threshold == 0 || exp < threshold) break;
            var next = GameData.getHomeWorldLevelDataMap().get(level + 1);
            if (next == null || next.getId() != level + 1) return null;
            exp -= threshold;
            level++;
        }
        return exp > Integer.MAX_VALUE ? null : new int[] {level, (int) exp};
    }

    public static void take(Player player, int index, int makeId, boolean fast) {
        execute(
                player,
                index,
                makeId,
                fast,
                null,
                null,
                result ->
                        player.getSession()
                                .send(
                                        new PacketTakeFurnitureMakeRsp(
                                                result.retcode(),
                                                result.makeId(),
                                                result.outputs(),
                                                result.remaining())));
    }

    static Result claim(
            Player player, FurnitureBulkReceipt owner, FurnitureBulkReceipt.Entry entry) {
        Result[] result = {null};
        execute(
                player,
                entry.getIndex(),
                entry.getMakeId(),
                entry.isFast(),
                owner,
                entry,
                value -> result[0] = value);
        return result[0];
    }

    private static void execute(
            Player player,
            int index,
            int makeId,
            boolean fast,
            FurnitureBulkReceipt owner,
            FurnitureBulkReceipt.Entry entry,
            Consumer<Result> reply) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(reply, makeId, Retcode.RET_HOME_PLAYER_NOT_IN_SELF_HOME_WORLD);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureStart() != null
                        || home.getPendingFurnitureCancel() != null
                        || home.getPendingFurnitureBulk() != owner
                        || owner != null && !owner.canRun(entry)) {
                    fail(reply, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (slots == null || index < 0) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                var matches =
                        slots.stream()
                                .filter(
                                        slot ->
                                                slot != null
                                                        && slot.getIndex() == index
                                                        && slot.getMakeId() == makeId)
                                .toList();
                if (matches.size() != 1) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                var slot = matches.get(0);
                var plan = slot.getPlan();
                int itemId, count, trustExp;
                List<FurnitureMakePlan.Material> materialReturns;
                if (owner != null) {
                    if (!entry.matches(slot)) {
                        fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    itemId = entry.getItemId();
                    count = entry.getCount();
                    trustExp = entry.getTrustExp();
                    materialReturns = entry.getReturns();
                } else if (plan != null) {
                    if (!plan.isValidFor(slot)) {
                        fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    itemId = plan.getItemId();
                    count = plan.getCount();
                    trustExp = plan.getTrustExp();
                    materialReturns = plan.getReturns();
                } else {
                    // Legacy jobs have no captured definition; do not invent one on reload.
                    var recipe = GameData.getFurnitureMakeConfigDataMap().get(makeId);
                    if (recipe == null
                            || recipe.getId() != makeId
                            || recipe.getCount() <= 0
                            || recipe.getExp() < 0) {
                        fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    itemId = recipe.getFurnitureItemID();
                    count = recipe.getCount();
                    trustExp = recipe.getExp();
                    materialReturns = List.of();
                }
                var data = GameData.getItemDataMap().get(itemId);
                if (slot.getBeginTime() <= 0
                        || slot.getDurTime() < 0
                        || slot.getAccelerateTime() < 0
                        || slot.getDurTime() == 0 && plan == null
                        || data == null
                        || data.getId() != itemId
                        || data.getItemType() != ItemType.ITEM_FURNITURE
                        || data.isUseOnGain()
                        || data.getStackLimit() <= 0) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                long end = slot.getCompletionTime();
                boolean needsVial = end > Utils.getCurrentSeconds();
                if (needsVial && !fast) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                    return;
                }
                int before = inventory.getItemCountById(data.getId());
                var tab = inventory.getInventoryTab(ItemType.ITEM_FURNITURE);
                var existing = inventory.getItemById(data.getId());
                if (before < 0
                        || (long) before + count > data.getStackLimit()
                        || tab == null
                        || before == 0 && tab.getSize() >= tab.getMaxCapacity()
                        || existing != null && existing.getOwnerId() != player.getUid()) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                int vialBefore = inventory.getItemCountById(107013);
                var vial = inventory.getItemById(107013);
                var vialData = GameData.getItemDataMap().get(107013);
                if (needsVial
                        && (vialBefore < 1
                                || vial == null
                                || vial.getOwnerId() != player.getUid()
                                || vialData == null
                                || vialData.getId() != 107013
                                || vialData.getItemType() != ItemType.ITEM_MATERIAL)) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                    return;
                }
                boolean first = !home.getCraftedFurnitureIds().contains(data.getId());
                var returns = FurnitureReturns.reserve(player, materialReturns, needsVial);
                if (returns == null) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                int award = first ? trustExp : 0;
                var progress = trustAfter(home, award);
                if (progress == null) {
                    fail(reply, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                var receipt =
                        new FurnitureTakeReceipt(
                                slot,
                                data.getId(),
                                count,
                                before,
                                vialBefore,
                                needsVial,
                                first,
                                award,
                                home.getLevel(),
                                home.getExp(),
                                progress[0],
                                progress[1],
                                returns);
                home.setPendingFurnitureTake(receipt);
                boolean removed = false;
                try {
                    DatabaseHelper.saveGameSync(home);
                    if (needsVial) {
                        vial.ensurePersistenceId();
                        if (!inventory.payItem(107013, 1)
                                || vialBefore - inventory.getItemCountById(107013) != 1)
                            throw new IllegalStateException(
                                    "Furniture speedup was not paid exactly");
                        DatabaseHelper.saveGameSync(vial);
                    }
                    receipt.paid();
                    DatabaseHelper.saveGameSync(home);
                    if (!inventory.addItem(new GameItem(data, count))
                            || (long) inventory.getItemCountById(data.getId()) - before != count)
                        throw new IllegalStateException("Furniture output was not stored exactly");
                    var stored = inventory.getItemById(data.getId());
                    if (stored == null || stored.getOwnerId() != player.getUid())
                        throw new IllegalStateException("Furniture output has no owned stack");
                    stored.ensurePersistenceId();
                    DatabaseHelper.saveGameSync(stored);
                    receipt.delivered(stored.getObjectId().toHexString());
                    DatabaseHelper.saveGameSync(home);
                    for (var value : receipt.getReturns()) {
                        var material = GameData.getItemDataMap().get(value.getItemId());
                        if (material == null
                                || material.getId() != value.getItemId()
                                || material.getItemType() != ItemType.ITEM_MATERIAL
                                || material.isUseOnGain()
                                || !inventory.addItem(new GameItem(material, value.getCount()))
                                || (long) inventory.getItemCountById(value.getItemId())
                                                - value.getBefore()
                                        != value.getCount())
                            throw new IllegalStateException(
                                    "Furniture material return was not stored exactly");
                        var returned = inventory.getItemById(value.getItemId());
                        if (returned == null || returned.getOwnerId() != player.getUid())
                            throw new IllegalStateException(
                                    "Furniture material return has no owned stack");
                        returned.ensurePersistenceId();
                        DatabaseHelper.saveGameSync(returned);
                        receipt.returned(value, returned.getObjectId().toHexString());
                        DatabaseHelper.saveGameSync(home);
                    }
                    if (home.getLevel() != receipt.getLevelBefore()
                            || home.getExp() != receipt.getExpBefore()
                            || first && home.getCraftedFurnitureIds().contains(data.getId()))
                        throw new IllegalStateException("Furniture trust changed during delivery");
                    home.setLevel(progress[0]);
                    home.setExp(progress[1]);
                    home.getCraftedFurnitureIds().add(data.getId());
                    receipt.progressed();
                    DatabaseHelper.saveGameSync(home);
                    player.getBattlePassManager()
                            .triggerMission(WatcherTriggerType.TRIGGER_FURNITURE_MAKE, 0, count);
                    DatabaseHelper.saveGameSync(player.getBattlePassManager());
                    receipt.missionApplied();
                    if (!slots.remove(slot))
                        throw new IllegalStateException("Furniture source slot changed");
                    removed = true;
                    receipt.completed();
                    if (owner != null) owner.completed(entry, data.getId(), count);
                    DatabaseHelper.saveGameSync(home);
                    home.setPendingFurnitureTake(null);
                    DatabaseHelper.saveGameSync(home);
                } catch (RuntimeException failure) {
                    home.setPendingFurnitureTake(receipt);
                    if (removed && !slots.contains(slot)) slots.add(slot);
                    try {
                        DatabaseHelper.saveGameSync(home);
                    } catch (RuntimeException saving) {
                        failure.addSuppressed(saving);
                    }
                    Grasscutter.getLogger()
                            .error(
                                    "Furniture claim needs reconciliation: UID {}, recipe {}, slot"
                                            + " {}",
                                    player.getUid(),
                                    makeId,
                                    index,
                                    failure);
                    fail(reply, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                if (award > 0 && player.getCurHomeWorld() != null)
                    player.sendPacket(new PacketHomeBasicInfoNotify(player, false));
                reply.accept(
                        new Result(
                                0,
                                makeId,
                                outputs(data.getId(), count, receipt.getReturns()),
                                slots.stream().map(FurnitureMakeSlotItem::toProto).toList()));
            }
        }
    }

    private static List<ItemParam> outputs(
            int itemId, int count, List<FurnitureTakeReceipt.Return> returns) {
        List<ItemParam> outputs = new ArrayList<>();
        outputs.add(ItemParam.newBuilder().setItemId(itemId).setCount(count).build());
        for (var value : returns)
            outputs.add(
                    ItemParam.newBuilder()
                            .setItemId(value.getItemId())
                            .setCount(value.getCount())
                            .build());
        return outputs;
    }
}
