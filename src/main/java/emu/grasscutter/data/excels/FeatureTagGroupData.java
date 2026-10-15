package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

@ResourceType(name = "FeatureTagGroupExcelConfigData.json")
@Getter
public final class FeatureTagGroupData extends GameResource {
    @Getter(onMethod_ = @Override)
    @SerializedName(value = "groupID", alternate = "groupId")
    private int id;

    @SerializedName(value = "tagIDs", alternate = "tagIds")
    private long[] tagIds = new long[0];
}
