package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.BookUnlocks;
import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseUnlockCodex extends ItemUseInt {
    public ItemUseUnlockCodex(String[] useParam) {
        super(useParam);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_UNLOCK_CODEX;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        if (params == null || params.player == null || params.count != 1) return false;
        var book = BookUnlocks.definition(GameData.getItemDataMap().get(params.usedItemId));
        return book != null
                && book.getId() == this.i
                && params.player.getCodex().unlockBook(this.i);
    }
}
