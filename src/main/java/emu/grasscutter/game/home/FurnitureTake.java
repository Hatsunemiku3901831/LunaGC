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

import java.util.List;

/** One acknowledged furniture claim, including speedup, storage and first-craft progress. */
public final class FurnitureTake {
    private FurnitureTake() {}

    private static void fail(Player player, int makeId, Retcode code) {
        player.getSession()
                .send(new PacketTakeFurnitureMakeRsp(code.getNumber(), makeId, null, null));
    }

    private static int[] trustAfter(GameHome home, int award) {
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
        var home = player.getHome();
        if (home == null || home.getOwnerUid() != player.getUid()) {
            fail(player, makeId, Retcode.RET_HOME_PLAYER_NOT_IN_SELF_HOME_WORLD);
            return;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            synchronized (home) {
                if (home.getPendingFurnitureTake() != null || home.getPendingFurnitureStart() != null) {
                    fail(player, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                var slots = home.getFurnitureMakeSlotItemList();
                if (slots == null || index < 0) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
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
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA);
                    return;
                }
                var slot = matches.get(0);
                var recipe = GameData.getFurnitureMakeConfigDataMap().get(makeId);
                var data =
                        recipe == null
                                ? null
                                : GameData.getItemDataMap().get(recipe.getFurnitureItemID());
                if (recipe == null
                        || recipe.getId() != makeId
                        || recipe.getCount() <= 0
                        || recipe.getExp() < 0
                        || slot.getBeginTime() <= 0
                        || slot.getDurTime() <= 0
                        || data == null
                        || data.getId() != recipe.getFurnitureItemID()
                        || data.getItemType() != ItemType.ITEM_FURNITURE
                        || data.isUseOnGain()
                        || data.getStackLimit() <= 0) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                long end = (long) slot.getBeginTime() + slot.getDurTime();
                boolean needsVial = end > Utils.getCurrentSeconds();
                if (needsVial && !fast) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                    return;
                }
                int before = inventory.getItemCountById(data.getId());
                var tab = inventory.getInventoryTab(ItemType.ITEM_FURNITURE);
                var existing = inventory.getItemById(data.getId());
                if (before < 0
                        || (long) before + recipe.getCount() > data.getStackLimit()
                        || tab == null
                        || before == 0 && tab.getSize() >= tab.getMaxCapacity()
                        || existing != null && existing.getOwnerId() != player.getUid()) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
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
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_UNFINISH);
                    return;
                }
                boolean first = !home.getCraftedFurnitureIds().contains(data.getId());
                int award = first ? recipe.getExp() : 0;
                var progress = trustAfter(home, award);
                if (progress == null) {
                    fail(player, makeId, Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR);
                    return;
                }
                var receipt =
                        new FurnitureTakeReceipt(
                                slot,
                                data.getId(),
                                recipe.getCount(),
                                before,
                                vialBefore,
                                needsVial,
                                first,
                                award,
                                home.getLevel(),
                                home.getExp(),
                                progress[0],
                                progress[1]);
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
                    if (!inventory.addItem(new GameItem(data, recipe.getCount()))
                            || (long) inventory.getItemCountById(data.getId()) - before
                                    != recipe.getCount())
                        throw new IllegalStateException("Furniture output was not stored exactly");
                    var stored = inventory.getItemById(data.getId());
                    if (stored == null || stored.getOwnerId() != player.getUid())
                        throw new IllegalStateException("Furniture output has no owned stack");
                    stored.ensurePersistenceId();
                    DatabaseHelper.saveGameSync(stored);
                    receipt.delivered(stored.getObjectId().toHexString());
                    DatabaseHelper.saveGameSync(home);
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
                            .triggerMission(
                                    WatcherTriggerType.TRIGGER_FURNITURE_MAKE,
                                    0,
                                    recipe.getCount());
                    DatabaseHelper.saveGameSync(player.getBattlePassManager());
                    receipt.missionApplied();
                    if (!slots.remove(slot))
                        throw new IllegalStateException("Furniture source slot changed");
                    removed = true;
                    receipt.completed();
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
                    fail(player, makeId, Retcode.RET_SVR_ERROR);
                    return;
                }
                if (award > 0 && player.getCurHomeWorld() != null)
                    player.sendPacket(new PacketHomeBasicInfoNotify(player, false));
                player.getSession()
                        .send(
                                new PacketTakeFurnitureMakeRsp(
                                        0,
                                        makeId,
                                        List.of(
                                                ItemParam.newBuilder()
                                                        .setItemId(data.getId())
                                                        .setCount(recipe.getCount())
                                                        .build()),
                                        slots.stream()
                                                .map(FurnitureMakeSlotItem::toProto)
                                                .toList()));
            }
        }
    }
}
