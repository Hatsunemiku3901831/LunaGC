package emu.grasscutter.game.activity.condition;

import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.utils.GameCalendar;

import java.time.*;

/** Counts schedule days at the same 04:00 China boundary as restored historical activities. */
public final class ActivityDayCalendar {
    private ActivityDayCalendar() {}

    /** Day one keeps the configured start; subsequent days open at the server's daily reset. */
    public static long beginTime(ActivityConfigItem config, int day) {
        long begin = config.getBeginTime().getTime();
        if (day <= 1) return begin;
        return resetDay(begin)
                .plusDays(day - 1L)
                .atTime(GameCalendar.RESET_HOUR, 0)
                .atZone(GameCalendar.SERVER_ZONE)
                .toInstant()
                .toEpochMilli();
    }

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
