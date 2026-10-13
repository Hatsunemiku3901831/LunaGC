package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;

import lombok.Getter;

/** Canonical suite definitions used to validate furnishing blueprint unlocks. */
@Getter
@ResourceType(name = {"FurnitureSuiteExcelConfigData.json"})
public class FurnitureSuiteData extends GameResource {
    private int suiteID;
    private String jsonName;

    @Override
    public int getId() {
        return suiteID;
    }
}
