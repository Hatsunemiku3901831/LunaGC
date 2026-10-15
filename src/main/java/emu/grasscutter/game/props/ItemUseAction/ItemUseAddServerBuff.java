package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseAddServerBuff extends ItemUseInt {
    private float duration = -1;

    public ItemUseAddServerBuff(String[] useParam) {
        super(useParam);
        try {
            this.duration = Float.parseFloat(useParam[1]);
        } catch (NumberFormatException | IndexOutOfBoundsException | NullPointerException ignored) {
        }
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_ADD_SERVER_BUFF;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        return params != null
                && params.player != null
                && params.player
                        .getBuffManager()
                        .addBuff(this.i, this.duration, params.targetAvatar);
    }
}
