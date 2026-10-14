package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL_GLOBAL_UNITY;

import emu.grasscutter.game.activity.condition.ActivityCondition;

/** The common schedule opens together for all players, using the same server reset calendar. */
@ActivityCondition(NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL_GLOBAL_UNITY)
public final class DaysGreatEqualGlobalUnity extends DaysGreatEqual {}
