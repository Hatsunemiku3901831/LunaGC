package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseUnlockProfilePicture extends ItemUseInt {
    public ItemUseUnlockProfilePicture(String[] useParam) {
        super(useParam == null ? new String[0] : useParam);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_UNLOCK_PROFILE_PICTURE;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        return params.count == 1 && params.player.unlockProfilePicture(i, params.usedItemId);
    }
}
