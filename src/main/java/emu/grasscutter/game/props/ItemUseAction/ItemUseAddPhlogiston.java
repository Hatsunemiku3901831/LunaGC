package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.player.Phlogiston;
import emu.grasscutter.game.props.ItemUseOp;

/** Resource operation 60; each current fuel ball carries its own configured amount. */
public final class ItemUseAddPhlogiston extends ItemUseAction {
    private float amount = Float.NaN;

    public ItemUseAddPhlogiston(String[] useParam) {
        try {
            amount = Float.parseFloat(useParam[0]);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_ADD_PHLOGISTON;
    }

    public float amount(int count) {
        double total = (double) amount * count;
        return count > 0 && Float.isFinite(amount) && amount > 0 && total <= Float.MAX_VALUE
                ? (float) total
                : Float.NaN;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        float total = params == null ? Float.NaN : amount(params.count);
        if (!Float.isFinite(total)
                || params.player == null
                || !Float.isFinite(params.player.getPhlogistonValue())) return false;
        Phlogiston.change(params.player, total);
        return true;
    }
}
