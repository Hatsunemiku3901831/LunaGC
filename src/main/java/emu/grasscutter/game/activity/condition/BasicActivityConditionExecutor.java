package emu.grasscutter.game.activity.condition;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.SalesmanActivityHandler;
import emu.grasscutter.game.quest.enums.LogicType;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

public class BasicActivityConditionExecutor implements ActivityConditionExecutor {

    private final Map<Integer, ActivityConfigItem> activityConfigItemMap;
    private final Int2ObjectMap<ActivityCondExcelConfigData> activityConditions;

    private final Int2ObjectMap<PlayerActivityData> playerActivityDataByActivityCondId;
    private final Map<ActivityConditions, ActivityConditionBaseHandler> activityConditionsHandlers;
    private final LongSupplier clock;

    public BasicActivityConditionExecutor(
            Map<Integer, ActivityConfigItem> activityConfigItemMap,
            Int2ObjectMap<ActivityCondExcelConfigData> activityConditions,
            Int2ObjectMap<PlayerActivityData> playerActivityDataByActivityCondId,
            Map<ActivityConditions, ActivityConditionBaseHandler> activityConditionsHandlers) {
        this(
                activityConfigItemMap,
                activityConditions,
                playerActivityDataByActivityCondId,
                activityConditionsHandlers,
                System::currentTimeMillis);
    }

    public BasicActivityConditionExecutor(
            Map<Integer, ActivityConfigItem> activityConfigItemMap,
            Int2ObjectMap<ActivityCondExcelConfigData> activityConditions,
            Int2ObjectMap<PlayerActivityData> playerActivityDataByActivityCondId,
            Map<ActivityConditions, ActivityConditionBaseHandler> activityConditionsHandlers,
            LongSupplier clock) {
        this.activityConfigItemMap = activityConfigItemMap;
        this.activityConditions = activityConditions;
        this.playerActivityDataByActivityCondId = playerActivityDataByActivityCondId;
        this.activityConditionsHandlers = activityConditionsHandlers;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<Integer> getMeetActivitiesConditions(List<Integer> condIds) {
        return getMeetActivitiesConditions(condIds, clock.getAsLong());
    }

    @Override
    public List<Integer> getMeetActivitiesConditions(List<Integer> condIds, long now) {
        return condIds.stream().filter(id -> meetsCondition(id, now)).collect(Collectors.toList());
    }

    @Override
    public boolean meetsCondition(int activityCondId) {
        return meetsCondition(activityCondId, clock.getAsLong());
    }

    private boolean meetsCondition(int activityCondId, long now) {
        ActivityCondExcelConfigData condData = activityConditions.get(activityCondId);

        if (condData == null) {
            Grasscutter.getLogger()
                    .error("Could not find condition for activity with id = {}", activityCondId);
            return false;
        }

        LogicType condComb = condData.getCondComb();
        if (condComb == null) return false;

        PlayerActivityData activity = playerActivityDataByActivityCondId.get(activityCondId);
        if (activity == null) {
            return false;
        }
        ActivityConfigItem activityConfig = activityConfigItemMap.get(activity.getActivityId());
        if (activityConfig == null
                || !activityConfig.isActiveAt(now)
                || activityConfig.getActivityId() != activity.getActivityId()
                || activityConfig.getScheduleId() != activity.getScheduleId()) return false;
        if (activityConfig.getActivityHandler() instanceof SalesmanActivityHandler) {
            return activity.getPlayer() != null
                    && SalesmanActivityHandler.conditions(
                                    activity, activityConfig, activity.getPlayer().getLevel(), now)
                            .contains(activityCondId);
        }
        if (condData.getCond() == null) return false;
        // Activity LOGIC_NONE is a conjunction, including rows with multiple real predicates.
        if (condComb == LogicType.LOGIC_NONE) condComb = LogicType.LOGIC_AND;
        List<BooleanSupplier> predicates =
                condData.getCond().stream()
                        .map(
                                c ->
                                        (BooleanSupplier)
                                                () ->
                                                        evaluate(
                                                                activityCondId,
                                                                c,
                                                                activity,
                                                                activityConfig,
                                                                now))
                        .collect(Collectors.toList());

        return LogicType.calculate(condComb, predicates);
    }

    private boolean evaluate(
            int conditionId,
            ActivityCondExcelConfigData.ActivityConfigCondition condition,
            PlayerActivityData activity,
            ActivityConfigItem config,
            long now) {
        if (condition == null || condition.getType() == null) return false;
        var handler = activityConditionsHandlers.get(condition.getType());
        if (handler == null) return false;
        try {
            return handler.executeAt(activity, config, now, condition.paramArray());
        } catch (RuntimeException invalid) {
            Grasscutter.getLogger()
                    .warn(
                            "Activity condition {} ({}) could not be evaluated: {}",
                            conditionId,
                            condition.getType(),
                            invalid.toString());
            return false;
        }
    }
}
