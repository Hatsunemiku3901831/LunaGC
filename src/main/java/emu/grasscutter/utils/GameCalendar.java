package emu.grasscutter.utils;

import java.time.*;

/** The China server calendar, independent of the host's time zone. */
public final class GameCalendar {
    public static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    public static final int RESET_HOUR = 4;

    private GameCalendar() {}

    public static LocalDate calendarDay(Instant now) {
        return now.atZone(SERVER_ZONE).toLocalDate();
    }

    public static LocalDate resetDay(Instant now) {
        return now.atZone(SERVER_ZONE).minusHours(RESET_HOUR).toLocalDate();
    }

    public static int dateKey(LocalDate day) {
        return day.getYear() * 10000 + day.getMonthValue() * 100 + day.getDayOfMonth();
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.minusDays(day.getDayOfWeek().getValue() - 1);
    }
}
