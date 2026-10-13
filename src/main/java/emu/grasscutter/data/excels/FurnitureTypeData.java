package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

/** Join furniture subtype IDs to the categories used by furnishing life passives. */
@Getter
@ResourceType(name = "HomeWorldFurnitureTypeExcelConfigData.json")
public final class FurnitureTypeData extends GameResource {
    @SerializedName(value = "typeID", alternate = "typeId")
    private int typeId;

    @SerializedName(value = "typeCategoryID", alternate = "typeCategoryId")
    private int typeCategoryId;

    @Override
    public int getId() {
        return typeId;
    }
}
