package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeStartInfoOuterClass.FurnitureMakeStartInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeStartBatchRsp;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeStartRsp;
import emu.grasscutter.utils.Utils;

import java.util.*;
import java.util.function.Consumer;

/** Learned jobs start only after a saved reservation and exact aggregate material payment. */
public final class FurnitureStart {
    private FurnitureStart() {}

    private record Selection(int makeId, int avatarId) {}

    private record Result(int retcode, List<FurnitureMakeData> jobs) {}

    private static void fail(Consumer<Result> reply, Retcode code) {
        reply.accept(new Result(code.getNumber(), null));
    }

    public static void start(Player player, int makeId, int avatarId) {
        execute(
                player,
                List.of(new Selection(makeId, avatarId)),
                result ->
                        player.getSession()
                                .send(
                                        new PacketFurnitureMakeStartRsp(
                                                result.retcode(), result.jobs())));
    }

    public static void startBatch(Player player, List<FurnitureMakeStartInfo> entries) {
        Consumer<Result> reply =
                result ->
                        player.getSession()
                                .send(
                                        new PacketFurnitureMakeStartBatchRsp(
                                                result.retcode(), result.jobs()));
        if (entries == null || entries.isEmpty() || entries.stream().anyMatch(Objects::isNull)) {
            fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
            return;
        }
        execute(
                player,
                entries.stream()
                        .map(entry -> new Selection(entry.getMakeId(), entry.getAvatarId()))
                        .toList(),
                reply);
    }

    private static void execute(Player player, List<Selection> entries, Consumer<Result> reply) {
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(reply, Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureTake() != null
                        || home.getPendingFurnitureStart() != null
                        || home.getPendingFurnitureCancel() != null
                        || home.getPendingFurnitureBulk() != null) {
                    fail(reply, Retcode.RET_SVR_ERROR);
                    return;
                }
                var level = GameData.getHomeWorldLevelDataMap().get(home.getLevel());
                if (level == null
                        || level.getId() != home.getLevel()
                        || level.getFurnitureMakeSlotCount() <= 0
                        || level.getFurnitureMakeTimeReduction() < 0) {
                    fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (slots == null) slots = new ArrayList<>();
                Set<Integer> used = new HashSet<>();
                for (var slot : slots) {
                    if (slot == null || slot.getIndex() < 0 || !used.add(slot.getIndex())) {
                        fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                }
                if ((long) slots.size() + entries.size() > level.getFurnitureMakeSlotCount()) {
                    fail(reply, Retcode.RET_FURNITURE_MAKE_SLOT_FULL);
                    return;
                }
                Map<Integer, Integer> totals = new LinkedHashMap<>();
                List<FurnitureMakeSlotItem> jobs = new ArrayList<>();
                int beginTime = Utils.getCurrentSeconds(), index = 0;
                for (var selection : entries) {
                    int makeId = selection.makeId(), avatarId = selection.avatarId();
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
                        fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    if (!player.getUnlockedFurniture().contains(product.getId())) {
                        fail(reply, Retcode.RET_FURNITURE_MAKE_LOCKED);
                        return;
                    }
                    if (avatarId < 0
                            || avatarId != 0
                                    && player.getAvatars().getAvatarById(avatarId) == null) {
                        fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    Map<Integer, Integer> materialCosts = new LinkedHashMap<>();
                    for (var cost : recipe.getMaterialItems()) {
                        if (cost == null
                                || cost.getId() <= 0
                                || cost.getCount() <= 0
                                || (long) totals.getOrDefault(cost.getId(), 0) + cost.getCount()
                                        > Integer.MAX_VALUE) {
                            fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                            return;
                        }
                        totals.merge(cost.getId(), cost.getCount(), Integer::sum);
                        materialCosts.merge(cost.getId(), cost.getCount(), Integer::sum);
                    }
                    while (used.contains(index)) index++;
                    int duration =
                            Math.max(
                                    0,
                                    recipe.getMakeTime() - level.getFurnitureMakeTimeReduction());
                    jobs.add(
                            FurnitureMakeSlotItem.of()
                                    .index(index)
                                    .makeId(makeId)
                                    .avatarId(avatarId)
                                    .beginTime(beginTime)
                                    .durTime(duration)
                                    .plan(
                                            new FurnitureMakePlan(
                                                    makeId,
                                                    product.getId(),
                                                    recipe.getCount(),
                                                    duration,
                                                    recipe.getExp(),
                                                    materialCosts))
                                    .build());
                    used.add(index++);
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
                        fail(reply, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                        return;
                    }
                    if (item == null
                            || item.getOwnerId() != player.getUid()
                            || before < entry.getValue()) {
                        fail(reply, Retcode.RET_HOME_FURNITURE_COUNT_NOT_ENOUGH);
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
                var receipt = new FurnitureStartReceipt(jobs, costs);
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
                    slots.addAll(jobs);
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
                                    "Furniture start needs reconciliation: UID {}, jobs {}, first"
                                        + " recipe {}, first slot {}",
                                    player.getUid(),
                                    jobs.size(),
                                    jobs.get(0).getMakeId(),
                                    jobs.get(0).getIndex(),
                                    failure);
                    fail(reply, Retcode.RET_SVR_ERROR);
                    return;
                }
                reply.accept(
                        new Result(0, slots.stream().map(FurnitureMakeSlotItem::toProto).toList()));
            }
        }
    }
}
