package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;

import lombok.Getter;

/** Mineral cost definitions for the experimental burner lamp's processing acceleration. */
@Getter
@ResourceType(name = "CompoundBoostExcelConfigData.json")
public final class CompoundBoostData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int boostTime;

    @Override
    public void onLoad() {
        if (id <= 0 || boostTime <= 0)
            throw new IllegalArgumentException("Compound boost material and time must be positive");
    }
}
