package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "InvestigationTargetConfigData.json")
public class InvestigationTargetData extends GameResource {
    private int id;
    private int investigationId;
    private int questId;
    private int progress;
    private int rewardId;
    private boolean isDisuse;
    private Trigger triggerConfig;

    @Getter
    public static class Trigger {
        private String triggerType;
        private List<String> paramList = List.of();
    }
}
