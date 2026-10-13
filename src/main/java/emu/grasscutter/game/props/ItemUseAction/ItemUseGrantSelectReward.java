package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.props.ItemUseOp;

import java.util.*;

public class ItemUseGrantSelectReward extends ItemUseSelectItems {
    public ItemUseGrantSelectReward(String[] useParam) {
        String[] options = options(useParam);
        this.optionItemIds = new int[options.length];
        for (int i = 0; i < options.length; i++) {
            try {
                this.optionItemIds[i] = Integer.parseInt(options[i]);
            } catch (NumberFormatException ignored) {
                this.optionItemIds[i] = INVALID;
            }
        }
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD;
    }

    @Override
    public List<ItemParamData> resolveItems(UseItemParams params) {
        if (params == null || params.player == null || params.count <= 0 || params.optionId <= 0)
            return List.of();
        int id = getItemId(params.optionId - 1);
        var reward = GameData.getRewardDataMap().get(id);
        if (id <= 0 || reward == null || reward.getId() != id || reward.getRewardItemList() == null)
            return List.of();
        var quantities = new LinkedHashMap<Integer, Long>();
        var rows = new ArrayList<>(reward.getRewardItemList());
        rows.addAll(reward.getAdditionalRewards());
        for (var item : rows) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0) return List.of();
            long amount = (long) item.getCount() * params.count;
            long total = quantities.merge(item.getId(), amount, Long::sum);
            if (total > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Selection reward quantity overflow");
        }
        return quantities.entrySet().stream()
                .map(entry -> new ItemParamData(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }
}
