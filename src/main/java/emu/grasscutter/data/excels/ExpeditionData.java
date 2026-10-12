package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "ExpeditionDataExcelConfigData.json")
public final class ExpeditionData extends GameResource {
    @SerializedName(
            value = "ID",
            alternate = {"id"})
    private int id;

    @SerializedName("city_id")
    private int cityId;

    private List<OpenCondition> openConditionVec = List.of();
    private List<TimeReward> timeRewardVec = List.of();

    @Override
    public int getId() {
        return id;
    }

    @Getter
    public static final class OpenCondition {
        private String type;
        private int para;
        private int para2;
    }

    @Getter
    public static final class TimeReward {
        @SerializedName("htime")
        private int hourTime;

        @SerializedName("reward_drop_id")
        private int dropId;

        private int rewardPreview;
    }
}
