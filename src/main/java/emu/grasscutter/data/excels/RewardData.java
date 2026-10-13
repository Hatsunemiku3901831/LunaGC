package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;

import java.util.ArrayList;
import java.util.List;

@ResourceType(name = "RewardExcelConfigData.json")
public class RewardData extends GameResource {
    public int rewardId;
    public List<ItemParamData> rewardItemList;
    private int hcoin, scoin, playerExp, resin;
    private int MEBGMPIJIDE, NBNIDEOMGOD;

    public List<ItemParamData> getAdditionalRewards() {
        if (hcoin < 0
                || scoin < 0
                || playerExp < 0
                || resin < 0
                || MEBGMPIJIDE != 0
                || NBNIDEOMGOD != 0)
            throw new IllegalArgumentException("Unsupported reward quantities");
        var result = new ArrayList<ItemParamData>();
        if (hcoin > 0) result.add(new ItemParamData(201, hcoin));
        if (scoin > 0) result.add(new ItemParamData(202, scoin));
        if (playerExp > 0) result.add(new ItemParamData(102, playerExp));
        if (resin > 0) result.add(new ItemParamData(106, resin));
        return result;
    }

    @Override
    public int getId() {
        return rewardId;
    }

    public List<ItemParamData> getRewardItemList() {
        return rewardItemList;
    }

    @Override
    public void onLoad() {
        rewardItemList = rewardItemList.stream().filter(i -> i.getId() > 0).toList();
    }
}
