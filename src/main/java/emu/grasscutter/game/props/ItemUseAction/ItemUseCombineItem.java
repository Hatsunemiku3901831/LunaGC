package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.props.ItemUseOp;

import java.util.List;

public class ItemUseCombineItem extends ItemUseInt implements ItemUseProductAction {
    private final int resultId;
    private final int resultCount;

    public ItemUseCombineItem(String[] useParam) {
        super(useParam);
        this.resultId = parameter(useParam, 1, -1);
        this.resultCount = parameter(useParam, 2, 1);
    }

    private static int parameter(String[] params, int index, int omitted) {
        if (params == null
                || params.length <= index
                || params[index] == null
                || params[index].isBlank()) return omitted;
        try {
            return Integer.parseInt(params[index]);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_COMBINE_ITEM;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        // The owned source, its exact payment and this complete plan are handled by inventory.
        return false;
    }

    @Override
    public List<ItemParamData> planItems(UseItemParams params) {
        if (params == null
                || params.player == null
                || this.i <= 0
                || params.count != this.i
                || params.optionId != 0
                || params.optionIds == null
                || !params.optionIds.isEmpty()
                || resultId <= 0
                || resultCount <= 0) return List.of();
        return List.of(new ItemParamData(resultId, resultCount));
    }
}
