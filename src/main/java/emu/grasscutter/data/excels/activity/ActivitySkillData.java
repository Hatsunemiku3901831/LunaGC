package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;

import lombok.Getter;

@Getter
@ResourceType(
        name = "ActivitySkillExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.HIGHEST)
public final class ActivitySkillData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int shareCDID;
}
