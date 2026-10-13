package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.props.ItemUseOp;

import java.util.List;

/** Owned consumables with a complete reward plan that must be confirmed after payment. */
public interface ItemUseProductAction {
    ItemUseOp getItemUseOp();

    List<ItemParamData> planItems(UseItemParams params);
}
