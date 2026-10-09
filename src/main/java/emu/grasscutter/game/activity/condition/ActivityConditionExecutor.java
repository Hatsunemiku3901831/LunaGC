package emu.grasscutter.game.activity.condition;

import java.util.List;

public interface ActivityConditionExecutor {

    List<Integer> getMeetActivitiesConditions(List<Integer> condIds);

    default List<Integer> getMeetActivitiesConditions(List<Integer> condIds, long now) {
        return getMeetActivitiesConditions(condIds);
    }

    boolean meetsCondition(int activityCondId);
}
