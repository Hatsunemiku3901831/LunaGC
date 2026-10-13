package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.home.FurnitureMakeSlotItem;
import emu.grasscutter.game.inventory.RecipeUnlocks;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;

public class FurnitureManager extends BasePlayerManager {

    public FurnitureManager(Player player) {
        super(player);
    }

    public void onLogin() {
        notifyUnlockFurniture();
        notifyUnlockFurnitureSuite();
    }

    public void notifyUnlockFurniture() {
        player
                .getSession()
                .send(new PacketUnlockedFurnitureFormulaDataNotify(player.getUnlockedFurniture()));
    }

    public void notifyUnlockFurnitureSuite() {
        player
                .getSession()
                .send(new PacketUnlockedFurnitureSuiteDataNotify(player.getUnlockedFurnitureSuite()));
    }

    public boolean unlockFurnitureFormula(int id) {
        if (!RecipeUnlocks.canUnlock(player, ItemUseOp.ITEM_USE_UNLOCK_FURNITURE_FORMULA, id))
            return false;
        if (!player.getUnlockedFurniture().add(id)) {
            return false; // Already unlocked!
        }
        notifyUnlockFurniture();
        return true;
    }

    public boolean unlockFurnitureSuite(int id) {
        if (!RecipeUnlocks.canUnlock(player, ItemUseOp.ITEM_USE_UNLOCK_FURNITURE_SUITE, id))
            return false;
        if (!player.getUnlockedFurnitureSuite().add(id)) {
            return false; // Already unlocked!
        }
        notifyUnlockFurnitureSuite();
        return true;
    }

    public void startMake(int makeId, int avatarId) {
        if (player.getHome() != null && player.getHome().getPendingFurnitureTake() != null) {
            player.getSession().send(new PacketFurnitureMakeStartRsp(Retcode.RET_SVR_ERROR_VALUE, null));
            return;
        }
        var makeData = GameData.getFurnitureMakeConfigDataMap().get(makeId);
        if (makeData == null) {
            player
                    .getSession()
                    .send(
                            new PacketFurnitureMakeStartRsp(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, null));
            return;
        }

        // check slot count
        if (player.getHome().getLevelData().getFurnitureMakeSlotCount()
                <= player.getHome().getFurnitureMakeSlotItemList().size()) {
            player
                    .getSession()
                    .send(new PacketFurnitureMakeStartRsp(Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE, null));
            return;
        }

        // pay items first
        if (!player.getInventory().payItems(makeData.getMaterialItems())) {
            player
                    .getSession()
                    .send(
                            new PacketFurnitureMakeStartRsp(
                                    Retcode.RET_HOME_FURNITURE_COUNT_NOT_ENOUGH_VALUE, null));
            return;
        }

        var furnitureSlot =
                FurnitureMakeSlotItem.of()
                        .avatarId(avatarId)
                        .makeId(makeId)
                        .beginTime(Utils.getCurrentSeconds())
                        .durTime(makeData.getMakeTime())
                        .build();

        // add furniture make task
        player.getHome().getFurnitureMakeSlotItemList().add(furnitureSlot);
        player
                .getSession()
                .send(
                        new PacketFurnitureMakeStartRsp(
                                Retcode.RET_SUCC_VALUE,
                                player.getHome().getFurnitureMakeSlotItemList().stream()
                                        .map(FurnitureMakeSlotItem::toProto)
                                        .toList()));

        player.getHome().save();
    }

    public void queryStatus() {
        if (player.getHome().getFurnitureMakeSlotItemList() == null) {
            player.getHome().setFurnitureMakeSlotItemList(new ArrayList<>());
        }

        player.sendPacket(new PacketFurnitureMakeRsp(player.getHome()));
    }

    public void take(int index, int makeId, boolean isFastFinish) {
        emu.grasscutter.game.home.FurnitureTake.take(player, index, makeId, isFastFinish);
    }
}
