package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import emu.grasscutter.game.activity.condition.ActivityConditions;
import emu.grasscutter.game.quest.enums.LogicType;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@ResourceType(name = "NewActivityCondExcelConfigData.json")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActivityCondExcelConfigData extends GameResource {
    int condId;
    LogicType condComb = LogicType.LOGIC_NONE;
    List<ActivityConfigCondition> cond;

    public static class ActivityConfigCondition {
        @Getter private ActivityConditions type = ActivityConditions.NEW_ACTIVITY_COND_NONE;
        @Getter private List<String> param;

        public int[] paramArray() {
            if (param == null)
                throw new IllegalArgumentException("Missing condition parameter list");
            int[] result = new int[param.size()];
            for (int i = 0; i < result.length; i++) {
                String value = param.get(i);
                if (value == null || !value.matches("-?[0-9]+"))
                    throw new IllegalArgumentException("Invalid integer condition parameter");
                result[i] = Integer.parseInt(value);
            }
            return result;
        }
    }

    @Override
    public int getId() {
        return condId;
    }

    @Override
    public void onLoad() {
        // An unknown enum is null in Gson; retaining it prevents accidental unconditional success.
        if (cond != null)
            cond.removeIf(c -> c != null && c.type == ActivityConditions.NEW_ACTIVITY_COND_NONE);
    }
}
