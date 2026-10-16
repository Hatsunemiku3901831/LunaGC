package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.props.ItemUseOp;

/** Resource-defined theme coupons may be delivered before the player owns the music book. */
public final class ItemUseMusicGameBookUnlockTheme extends ItemUseInt {
    public ItemUseMusicGameBookUnlockTheme(String[] params) {
        super(params);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_MUSIC_GAME_BOOK_UNLOCK_THEME;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        if (params == null
                || params.player == null
                || params.count != 1
                || params.optionId != 0
                || params.optionIds == null
                || !params.optionIds.isEmpty()) return false;
        var source = GameData.getItemDataMap().get(params.usedItemId);
        if (source == null
                || source.getMaterialType() != MaterialType.MATERIAL_MUSIC_GAME_BOOK_THEME
                || source.getItemUseActions() == null
                || source.getItemUseActions().size() != 1
                || source.getItemUseActions().get(0) != this) return false;
        return params.player.getMusicGameBook().unlockTheme(i);
    }
}
