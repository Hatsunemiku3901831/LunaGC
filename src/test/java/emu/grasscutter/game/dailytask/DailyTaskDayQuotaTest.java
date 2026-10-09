package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.game.player.Player;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

class DailyTaskDayQuotaTest {
    TimeZone originalZone;

    static Instant time(String text) {
        return OffsetDateTime.parse(text).toInstant();
    }

    static class Manager extends DailyTaskManager {
        transient Instant now;
        int saves;

        @Override
        protected Instant generationNow() {
            return now;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    Manager stored(int date, int tasks) {
        var manager =
                new Gson()
                        .fromJson(
                                "{\"lastGenerationDate\":"
                                        + date
                                        + ",\"scoreRewardTaken\":true,\"dailyTasks\":[]}",
                                Manager.class);
        manager.setPlayer(new Player());
        for (int i = 0; i < tasks; i++) manager.getDailyTasks().add(new DailyTask());
        return manager;
    }

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void currentCommissionSetIsPreservedBeforeFour() {
        var manager = stored(20261009, 4);
        manager.now = time("2026-10-10T03:59:59+08:00");
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(20261009, manager.getLastGenerationDate());
        assertTrue(manager.isScoreRewardTaken());
        assertEquals(0, manager.saves);
    }

    @Test
    void dateFreeLegacySetIsStampedWithTheGameDayWithoutRerolling() {
        var manager = stored(0, 4);
        manager.now = time("2026-10-10T03:59:59+08:00");
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(20261009, manager.getLastGenerationDate());
        assertTrue(manager.isScoreRewardTaken());
        assertEquals(1, manager.saves);
    }

    @Test
    void migratedMidnightQuotaFromTheFollowingDateIsNotRerolled() {
        var manager = stored(20261010, 4);
        manager.now = time("2026-10-10T00:00:00+08:00");
        assertEquals(4, manager.ensureDailyTasksForToday());
        manager.now = time("2026-10-10T04:00:00+08:00");
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(20261010, manager.getLastGenerationDate());
        assertEquals(0, manager.saves);
    }

    @Test
    void clockRollbackCannotCreateASecondQuota() {
        var manager = stored(20261011, 4);
        manager.now = time("2026-10-10T12:00:00+08:00");
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(20261011, manager.getLastGenerationDate());
        assertTrue(manager.isScoreRewardTaken());
    }

    @Test
    void partialCurrentSetIsNotSilentlyRepairedOrRerolled() {
        var manager = stored(20261009, 2);
        manager.now = time("2026-10-10T03:59:59+08:00");
        assertEquals(2, manager.ensureDailyTasksForToday());
        assertEquals(0, manager.saves);
    }

    @Test
    void playerFrameTimeIsUsedInsteadOfAnotherClockRead() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var manager = stored(0, 4);
        manager.now = time("2026-10-12T12:00:00+08:00");
        assertEquals(4, manager.resetDailyTasksForNewDay(time("2026-10-10T04:00:00+08:00")));
        assertEquals(20261010, manager.getLastGenerationDate());
    }
}
