package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;

import lombok.Getter;

@Getter
@ResourceType(name = "ReliquaryDecomposeExcelConfigData.json")
public final class ReliquaryDecomposeData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int rewardPreviewId;
    private int needReliquaryCount;
    private int needReliquaryRankLevel;
    private int maxReliquaryNum;

    public boolean isValid() {
        return id > 0
                && rewardPreviewId > 0
                && needReliquaryCount > 0
                && needReliquaryRankLevel > 0
                && needReliquaryRankLevel <= 5
                && maxReliquaryNum > 0;
    }

    @Override
    public void onLoad() {
        if (!isValid()) throw new IllegalArgumentException("Invalid strongbox rule");
    }
}
