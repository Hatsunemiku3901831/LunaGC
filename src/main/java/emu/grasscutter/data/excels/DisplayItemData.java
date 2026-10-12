package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;

import lombok.Getter;

@Getter
@ResourceType(name = "DisplayItemExcelConfigData.json")
public final class DisplayItemData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int param;
    private int rankLevel;
    private String displayType;
}
