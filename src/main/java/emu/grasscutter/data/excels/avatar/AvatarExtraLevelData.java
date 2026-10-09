package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.common.ItemParamData;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

@ResourceType(name = "AvatarExtraLevelExcelConfigData.json")
@Getter
public class AvatarExtraLevelData extends GameResource {
    @SerializedName(
            value = "requireLevel",
            alternate = {"fromLevel", "DEJBOJMHHJB", "GGCNFKENPPB", "LFKJOBPEIKM"})
    private int requireLevel;

    @SerializedName(
            value = "maxLevel",
            alternate = {"toLevel", "BBPEGJLEEEC", "LCBMNGNFOCE", "DKLMGPLCDGG"})
    private int maxLevel;

    private ItemParamData[] costItems;
    private transient boolean valid;

    @Override
    public int getId() {
        return requireLevel;
    }

    @Override
    public void onLoad() {
        valid = false;
        if (requireLevel < 90 || maxLevel <= requireLevel || costItems == null) return;
        Map<Integer, Integer> costs = new LinkedHashMap<>();
        for (var cost : costItems) {
            if (cost == null) return;
            if (cost.getId() == 0 && cost.getCount() == 0) continue;
            if (cost.getId() <= 0 || cost.getCount() <= 0) return;
            try {
                costs.merge(cost.getId(), cost.getCount(), Math::addExact);
            } catch (ArithmeticException overflow) {
                return;
            }
        }
        if (costs.isEmpty()) return;
        costItems =
                costs.entrySet().stream()
                        .map(entry -> new ItemParamData(entry.getKey(), entry.getValue()))
                        .toArray(ItemParamData[]::new);
        valid = true;
    }
}
