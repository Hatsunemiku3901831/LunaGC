package emu.grasscutter.game.activity.condition;

import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.utils.GameCalendar;

import java.time.*;

/** Counts schedule days at the same 04:00 China boundary as restored historical activities. */
public final class ActivityDayCalendar {
    private ActivityDayCalendar() {}

    public static long dayIndex(ActivityConfigItem config, long now) {
        if (config == null || !config.isActiveAt(now)) return 0;
        var first = resetDay(config.getBeginTime().getTime());
        var current = resetDay(now);
        // Count calendar days, avoiding both millisecond subtraction and int overflow.
        return current.toEpochDay() - first.toEpochDay() + 1;
    }

    private static LocalDate resetDay(long milliseconds) {
        return GameCalendar.resetDay(Instant.ofEpochMilli(milliseconds));
    }
}
