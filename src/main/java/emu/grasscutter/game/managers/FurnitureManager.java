package emu.grasscutter.game.managers;

import emu.grasscutter.game.home.FurnitureStart;
import emu.grasscutter.game.inventory.RecipeUnlocks;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.server.packet.send.*;
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
        FurnitureStart.start(player, makeId, avatarId);
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
