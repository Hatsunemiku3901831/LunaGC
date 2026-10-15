package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.*;

/** The current city table includes regions absent from StatuePromoteExcelConfigData. */
@ResourceType(name = "CityLevelupConfigData.json")
@Getter
public final class CityLevelupData extends GameResource {
    @SerializedName(
            value = "cityId",
            alternate = {"city_id"})
    private int cityId;

    private int level;

    @SerializedName(
            value = "sceneId",
            alternate = {"scene_id"})
    private int sceneId;

    @SerializedName(
            value = "rewardId",
            alternate = {"rewardID", "reward_id"})
    private int rewardId;

    @SerializedName(
            value = "consumeItem",
            alternate = {"consume_item"})
    private ConsumeItem consumeItem;

    @SerializedName(
            value = "actionVec",
            alternate = {"HJDKMDJFECH"})
    private List<LevelupAction> actionVec;

    @Override
    public int getId() {
        return (cityId << 8) + level;
    }

    public int getCostItemId() {
        return consumeItem == null ? 0 : consumeItem.itemId;
    }

    public int getCostItemCount() {
        return consumeItem == null ? 0 : consumeItem.itemNum;
    }

    public int getStaminaGain() {
        int result = 0;
        if (actionVec != null)
            for (var action : actionVec)
                if (action != null
                        && "WORLD_AREA_ACTION_IMPROVE_STAMINA".equals(action.type)
                        && action.param1Vec != null)
                    for (int amount : action.param1Vec) {
                        if (amount < 0)
                            throw new IllegalArgumentException("Negative statue stamina gain");
                        result = Math.addExact(result, amount);
                    }
        return result;
    }

    @Getter
    public static final class ConsumeItem {
        @SerializedName(
                value = "itemId",
                alternate = {"item_id", "id"})
        private int itemId;

        @SerializedName(
                value = "itemNum",
                alternate = {"item_num", "count"})
        private int itemNum;
    }

    @Getter
    public static final class LevelupAction {
        private String type;

        @SerializedName(
                value = "param1Vec",
                alternate = {"param1_vec"})
        private int[] param1Vec;

        @SerializedName(
                value = "param2Vec",
                alternate = {"param2_vec"})
        private int[] param2Vec;
    }
}
