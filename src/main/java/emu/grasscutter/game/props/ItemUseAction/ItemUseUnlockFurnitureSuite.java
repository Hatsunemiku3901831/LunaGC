package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.inventory.RecipeUnlocks;
import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseUnlockFurnitureSuite extends ItemUseInt {
    public ItemUseUnlockFurnitureSuite(String[] useParam) {
        super(useParam);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_UNLOCK_FURNITURE_SUITE;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        return RecipeUnlocks.canUse(params, getItemUseOp(), this.i);
    }

    @Override
    public boolean postUseItem(UseItemParams params) {
        return RecipeUnlocks.canUse(params, getItemUseOp(), this.i)
                && params.player.getFurnitureManager().unlockFurnitureSuite(this.i);
    }
}
