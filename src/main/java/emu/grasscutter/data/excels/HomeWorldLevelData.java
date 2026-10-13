package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ResourceType(name = {"HomeworldLevelExcelConfigData.json"})
public class HomeWorldLevelData extends GameResource {

    int level;
    int exp;
    int homeCoinStoreLimit;
    int homeFetterExpStoreLimit;
    int rewardId;
    Integer furnitureMakeSlotCount;

    @SerializedName("HMEPEMLDHII")
    Integer legacyFurnitureMakeSlotCount;

    @SerializedName("HALLBIKHGGJ")
    Integer modernFurnitureMakeSlotCount;

    int outdoorUnlockBlockCount;
    int freeUnlockModuleCount;
    int deployNpcCount;
    int limitShopGoodsCount;
    List<String> levelFuncs;

    public int getFurnitureMakeSlotCount() {
        if (furnitureMakeSlotCount != null) return furnitureMakeSlotCount;
        if (modernFurnitureMakeSlotCount != null) return modernFurnitureMakeSlotCount;
        return legacyFurnitureMakeSlotCount == null ? 0 : legacyFurnitureMakeSlotCount;
    }

    @Override
    public int getId() {
        return level;
    }
}
