package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "InvestigationConfigData.json")
public class InvestigationData extends GameResource {
    private int id;
    private String investigationType;
    private int unlockLevel;
    private String unlockOpenStateType;
    private int rewardId;
    private boolean isRewardEmpty;
    private List<Integer> nextInvestigationIdList = List.of();
}
