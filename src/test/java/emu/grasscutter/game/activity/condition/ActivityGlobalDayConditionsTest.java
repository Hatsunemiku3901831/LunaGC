package emu.grasscutter.game.activity.condition;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

class ActivityGlobalDayConditionsTest {
    static final String TYPE = "NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL_GLOBAL_UNITY";
    static final long BEGIN =
            OffsetDateTime.parse("2026-10-15T10:00:00+08:00").toInstant().toEpochMilli();
    static final long SECOND =
            OffsetDateTime.parse("2026-10-16T04:00:00+08:00").toInstant().toEpochMilli();
    Map<ActivityConditions, ActivityConditionBaseHandler> handlers;

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        handlers = AllActivityConditionBuilder.buildActivityConditions();
    }

    ActivityConfigItem config() {
        var config = new ActivityConfigItem();
        config.setActivityId(5343);
        config.setScheduleId(5343001);
        config.setBeginTime(new Date(BEGIN));
        config.setOpenTime(new Date(BEGIN));
        config.setCloseTime(new Date(BEGIN + 30L * 86400000));
        config.setEndTime(new Date(BEGIN + 32L * 86400000));
        return config;
    }

    ActivityCondExcelConfigData row(String params) {
        var row =
                JsonUtils.decode(
                        "{condId:5343003,condComb:'LOGIC_AND',cond:[{type:'"
                                + TYPE
                                + "',param:"
                                + params
                                + "}]}",
                        ActivityCondExcelConfigData.class);
        row.onLoad();
        return row;
    }

    BasicActivityConditionExecutor executor(
            ActivityConfigItem config,
            PlayerActivityData progress,
            ActivityCondExcelConfigData row,
            long now) {
        var conditions = new Int2ObjectOpenHashMap<ActivityCondExcelConfigData>();
        conditions.put(row.getId(), row);
        var mapping = new Int2ObjectOpenHashMap<PlayerActivityData>();
        mapping.put(row.getId(), progress);
        return new BasicActivityConditionExecutor(
                Map.of(config.getActivityId(), config), conditions, mapping, handlers, () -> now);
    }

    PlayerActivityData progress(ActivityConfigItem config) {
        return PlayerActivityData.of()
                .activityId(config.getActivityId())
                .scheduleId(config.getScheduleId())
                .build();
    }

    boolean meets(ActivityConfigItem config, ActivityCondExcelConfigData row, long now) {
        return executor(config, progress(config), row, now).meetsCondition(row.getId());
    }

    @Test
    void currentTypeIsDecodedAndRegistered() {
        var row = row("[1]");
        assertNotNull(row.getCond().get(0).getType());
        assertNotNull(handlers.get(row.getCond().get(0).getType()));
    }

    @Test
    void firstDayOpensAtServerBegin() {
        var config = config();
        assertFalse(meets(config, row("[1]"), BEGIN - 1));
        assertTrue(meets(config, row("[1]"), BEGIN));
    }

    @Test
    void nextDayOpensAtFourAmReset() {
        var config = config();
        assertFalse(meets(config, row("[2]"), SECOND - 1));
        assertTrue(meets(config, row("[2]"), SECOND));
    }

    @Test
    void newlyCreatedPlayerReceivesTheSameServerDay() {
        var config = config();
        var row = row("[3]");
        long now = SECOND + 86400000L;
        assertTrue(executor(config, progress(config), row, now).meetsCondition(row.getId()));
        assertTrue(executor(config, progress(config), row, now).meetsCondition(row.getId()));
    }

    @Test
    void reopenUsesTheNewScheduleBegin() {
        var config = config();
        assertTrue(meets(config, row("[2]"), SECOND));
        config.setBeginTime(new Date(SECOND));
        config.setOpenTime(new Date(SECOND));
        assertFalse(meets(config, row("[2]"), SECOND));
        assertTrue(meets(config, row("[2]"), SECOND + 86400000L));
    }

    @Test
    void expiredOrDisabledActivityDoesNotUnlock() {
        var config = config();
        assertFalse(meets(config, row("[1]"), config.getEndTime().getTime()));
        config.setDisabled(true);
        assertFalse(meets(config, row("[1]"), SECOND));
    }

    @Test
    void oldPlayerScheduleCannotUseNewConfig() {
        var config = config();
        var p = progress(config);
        config.setScheduleId(config.getScheduleId() + 1);
        var row = row("[1]");
        assertFalse(executor(config, p, row, BEGIN).meetsCondition(row.getId()));
    }

    @Test
    void badDaysDoNotUnlock() {
        var config = config();
        for (var params : List.of("[]", "[0]", "[-1]", "['notDay']"))
            assertFalse(meets(config, row(params), BEGIN), params);
    }

    @Test
    void allTwelveCurrentResourceRowsUseTheirConfiguredDay() throws Exception {
        var rows =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/NewActivityCondExcelConfigData.json"),
                        ActivityCondExcelConfigData.class);
        int checked = 0;
        for (var row : rows) {
            row.onLoad();
            if (row.getId() < 5343001 || row.getId() > 5343012) continue;
            assertNotNull(row.getCond().get(0).getType());
            assertEquals(TYPE, row.getCond().get(0).getType().name());
            // Keep the original quest/watcher predicates; isolate their satisfied state so
            // this test checks the added date gate without claiming their gameplay is complete.
            var prerequisites = new java.util.concurrent.atomic.AtomicBoolean(false);
            for (var condition : row.getCond()) {
                if (TYPE.equals(condition.getType().name())) continue;
                handlers.put(
                        condition.getType(),
                        new ActivityConditionBaseHandler() {
                            @Override
                            public boolean execute(
                                    PlayerActivityData data,
                                    ActivityConfigItem config,
                                    int... params) {
                                return prerequisites.get();
                            }
                        });
            }
            int day = row.getCond().get(0).paramArray()[0];
            var config = config();
            long opening = ActivityDayCalendar.beginTime(config, day);
            if (row.getCond().size() > 1)
                assertFalse(
                        meets(config, row, opening), "unmet original prerequisite " + row.getId());
            prerequisites.set(true);
            assertFalse(meets(config, row, opening - 1), "early row " + row.getId());
            assertTrue(meets(config, row, opening), "opening row " + row.getId());
            checked++;
        }
        assertEquals(12, checked);
    }
}
