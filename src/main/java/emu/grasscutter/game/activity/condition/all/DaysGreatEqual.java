package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.*;

@ActivityCondition(NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL)
public class DaysGreatEqual extends ActivityConditionBaseHandler {
    @Override
    public boolean execute(
            PlayerActivityData activityData, ActivityConfigItem activityConfig, int... params) {
        return executeAt(activityData, activityConfig, System.currentTimeMillis(), params);
    }

    @Override
    public boolean executeAt(
            PlayerActivityData activityData,
            ActivityConfigItem activityConfig,
            long now,
            int... params) {
        if (params == null || params.length == 0 || params[0] < 1) return false;
        return ActivityDayCalendar.dayIndex(activityConfig, now) >= params[0];
    }
}
