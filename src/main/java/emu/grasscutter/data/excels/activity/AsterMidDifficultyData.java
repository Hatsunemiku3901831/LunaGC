package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "AsterMidDifficultyExcelConfigData.json")
public final class AsterMidDifficultyData extends GameResource {
    private int id, dropId, rewardId, resin, recommendLevel, monsterLevel;
    private List<Integer> worldLevelVec = List.of();

    @Override
    public int getId() {
        return id;
    }
}
