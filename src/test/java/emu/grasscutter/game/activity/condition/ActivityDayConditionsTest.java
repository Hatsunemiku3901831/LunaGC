package emu.grasscutter.game.activity.condition;

import static emu.grasscutter.game.activity.condition.ActivityConditions.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.all.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

class ActivityDayConditionsTest {
    private static final long BEGIN = time("2026-10-09T10:00:00+08:00");
    private static final long SECOND_DAY = time("2026-10-10T04:00:00+08:00");
    private final DayLess less = new DayLess();
    private final DaysGreatEqual atLeast = new DaysGreatEqual();

    @BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private static long time(String iso) {
        return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
    }

    private static ActivityConfigItem config() {
        var config = new ActivityConfigItem();
        config.setActivityId(321);
        config.setScheduleId(3210001);
        config.setBeginTime(new Date(BEGIN));
        config.setOpenTime(new Date(BEGIN));
        config.setCloseTime(new Date(BEGIN + 30 * 86400000L));
        config.setEndTime(new Date(BEGIN + 32 * 86400000L));
        return config;
    }

    private static PlayerActivityData progress(ActivityConfigItem config) {
        return PlayerActivityData.of()
                .activityId(config.getActivityId())
                .scheduleId(config.getScheduleId())
                .build();
    }

    private void bounds(ActivityConfigItem config, long now, int day) {
        assertTrue(atLeast.executeAt(progress(config), config, now, day));
        assertFalse(atLeast.executeAt(progress(config), config, now, day + 1));
        assertFalse(less.executeAt(progress(config), config, now, day));
        assertTrue(less.executeAt(progress(config), config, now, day + 1));
    }

    @Test
    void futureBeginInTheSameResetDayDoesNotMeetEitherCondition() {
        assertFalse(atLeast.executeAt(null, config(), BEGIN - 1, 1));
        assertFalse(less.executeAt(null, config(), BEGIN - 1, 2));
    }

    @Test
    void beginningIsInclusiveAndStartsWithDayOne() {
        bounds(config(), BEGIN, 1);
    }

    @Test
    void midnightDoesNotStartANewActivityDay() {
        bounds(config(), time("2026-10-10T00:00:00+08:00"), 1);
    }

    @Test
    void theSecondDayBeginsAtFourRatherThanTwentyFourHoursLater() {
        bounds(config(), SECOND_DAY - 1, 1);
        bounds(config(), SECOND_DAY, 2);
    }

    @Test
    void anOpeningBeforeFourCanHaveAShortFirstDay() {
        var config = config();
        config.setBeginTime(new Date(time("2026-10-09T03:59:59+08:00")));
        config.setOpenTime(config.getBeginTime());
        bounds(config, time("2026-10-09T04:00:00+08:00"), 2);
    }

    @Test
    void daysUseScheduleBeginEvenWhenGameplayOpensLater() {
        var config = config();
        config.setOpenTime(new Date(BEGIN + 4 * 86400000L));
        bounds(config, SECOND_DAY, 2);
    }

    @Test
    void missingAndInvalidThresholdsAreRejectedWithoutThrowing() {
        for (int[] params : new int[][] {null, new int[0], new int[] {0}, new int[] {-1}}) {
            assertFalse(atLeast.executeAt(null, config(), BEGIN, params));
            assertFalse(less.executeAt(null, config(), BEGIN, params));
        }
    }

    @Test
    void missingDisabledAndExpiredSchedulesDoNotMeetDays() {
        assertFalse(atLeast.executeAt(null, null, BEGIN, 1));
        assertFalse(less.executeAt(null, null, BEGIN, 2));
        var config = config();
        config.setBeginTime(null);
        assertFalse(atLeast.executeAt(null, config, BEGIN, 1));
        config = config();
        config.setDisabled(true);
        assertFalse(less.executeAt(null, config, BEGIN, 2));
        assertFalse(atLeast.executeAt(null, config, BEGIN, 1));
        config = config();
        assertFalse(atLeast.executeAt(null, config, config.getEndTime().getTime(), 1));
        assertFalse(less.executeAt(null, config, config.getEndTime().getTime(), Integer.MAX_VALUE));
    }

    @Test
    void rewardPeriodStillCountsDaysAfterGameplayHasClosed() {
        var config = config();
        config.setActivityId(2001);
        config.setCloseTime(new Date(SECOND_DAY + 13 * 86400000L));
        bounds(config, SECOND_DAY + 13 * 86400000L, 15);
    }

    @Test
    void dateSubtractionAndDayCountsDoNotWrapAtIntegerOrLongBounds() {
        var config = config();
        config.setBeginTime(new Date(Long.MIN_VALUE + 1));
        config.setOpenTime(config.getBeginTime());
        config.setCloseTime(new Date(Long.MAX_VALUE));
        config.setEndTime(config.getCloseTime());
        assertTrue(atLeast.executeAt(null, config, Long.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertFalse(less.executeAt(null, config, Long.MAX_VALUE - 1, Integer.MAX_VALUE));
    }

    private static ActivityCondExcelConfigData row(int id, String logic, String predicates) {
        var row =
                JsonUtils.decode(
                        "{\"condId\":"
                                + id
                                + ",\"condComb\":\""
                                + logic
                                + "\",\"cond\":"
                                + predicates
                                + "}",
                        ActivityCondExcelConfigData.class);
        row.onLoad();
        return row;
    }

    private static String predicate(String type, String params) {
        return "{\"type\":\"" + type + "\",\"param\":" + params + "}";
    }

    private static BasicActivityConditionExecutor executor(
            ActivityConfigItem config,
            List<ActivityCondExcelConfigData> rows,
            LongSupplier clock,
            Map<ActivityConditions, ActivityConditionBaseHandler> extras) {
        var conditions = new Int2ObjectOpenHashMap<ActivityCondExcelConfigData>();
        var mapping = new Int2ObjectOpenHashMap<PlayerActivityData>();
        rows.forEach(
                row -> {
                    conditions.put(row.getId(), row);
                    mapping.put(row.getId(), progress(config));
                });
        var handlers = new HashMap<ActivityConditions, ActivityConditionBaseHandler>(extras);
        handlers.put(NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL, new DaysGreatEqual());
        handlers.put(NEW_ACTIVITY_COND_DAYS_LESS, new DayLess());
        return new BasicActivityConditionExecutor(
                Map.of(config.getActivityId(), config), conditions, mapping, handlers, clock);
    }

    private static BasicActivityConditionExecutor executor(
            ActivityConfigItem config, ActivityCondExcelConfigData row, long now) {
        return executor(config, List.of(row), () -> now, Map.of());
    }

    @Test
    void explicitPaddingLeavesAnUnconditionalRow() {
        var row = row(1, "LOGIC_NONE", "[" + predicate("NEW_ACTIVITY_COND_NONE", "[]") + "]");
        assertTrue(row.getCond().isEmpty());
        assertTrue(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void omittedTypeIsTheExportedNoneDefaultButExplicitNullIsInvalid() {
        var padding = row(1, "LOGIC_NONE", "[{\"param\":[]}]");
        assertTrue(padding.getCond().isEmpty());
        assertTrue(executor(config(), padding, BEGIN).meetsCondition(1));
        var invalid = row(2, "LOGIC_NONE", "[{\"type\":null,\"param\":[]}]");
        assertEquals(1, invalid.getCond().size());
        assertFalse(executor(config(), invalid, BEGIN).meetsCondition(2));
    }

    @Test
    void unknownConditionsAreKeptAndCannotBecomePadding() {
        var row =
                row(
                        1,
                        "LOGIC_AND",
                        "["
                                + predicate("NEW_ACTIVITY_COND_FUTURE_TEST", "[1]")
                                + ","
                                + predicate("NEW_ACTIVITY_COND_NONE", "[]")
                                + "]");
        assertEquals(1, row.getCond().size());
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void unknownConditionsDoNotEraseTheOtherAndRequirements() {
        var row =
                row(
                        1,
                        "LOGIC_AND",
                        "["
                                + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]")
                                + ","
                                + predicate("NEW_ACTIVITY_COND_FUTURE_TEST", "[1]")
                                + "]");
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void unknownOrBranchDoesNotPreventAKnownAlternative() {
        var row =
                row(
                        1,
                        "LOGIC_OR",
                        "["
                                + predicate("NEW_ACTIVITY_COND_FUTURE_TEST", "[1]")
                                + ","
                                + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]")
                                + "]");
        assertTrue(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void noneLogicRequiresAllRealPredicates() {
        var row =
                row(
                        1,
                        "LOGIC_NONE",
                        "["
                                + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]")
                                + ","
                                + predicate("NEW_ACTIVITY_COND_DAYS_LESS", "[2]")
                                + "]");
        assertTrue(executor(config(), row, BEGIN).meetsCondition(1));
        assertFalse(executor(config(), row, SECOND_DAY).meetsCondition(1));
    }

    @Test
    void anUnknownLogicTypeCannotBecomeAnAndOperation() {
        var row =
                row(
                        1,
                        "LOGIC_FUTURE_TEST",
                        "[" + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]") + "]");
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void anOmittedLogicTypeUsesTheExportedNoneDefault() {
        var row =
                JsonUtils.decode(
                        "{\"condId\":1,\"cond\":["
                                + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]")
                                + ","
                                + predicate("NEW_ACTIVITY_COND_DAYS_LESS", "[2]")
                                + "]}",
                        ActivityCondExcelConfigData.class);
        row.onLoad();
        assertTrue(executor(config(), row, BEGIN).meetsCondition(1));
        assertFalse(executor(config(), row, SECOND_DAY).meetsCondition(1));
    }

    @Test
    void corruptParameterDoesNotShiftTheSecondValueIntoTheThreshold() {
        var row =
                row(
                        1,
                        "LOGIC_AND",
                        "["
                                + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[\"bad\",\"1\"]")
                                + "]");
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void overflowNullAndMissingParametersDoNotAbortAResponse() {
        for (String params : new String[] {"[\"99999999999999999999\"]", "[null]", "null", "[]"}) {
            var row =
                    row(
                            1,
                            "LOGIC_AND",
                            "[" + predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", params) + "]");
            assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
        }
    }

    @Test
    void corruptPredicateIsNotAnUnconditionalRow() {
        var row = row(1, "LOGIC_AND", "[null]");
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
        row = row(1, "LOGIC_NONE", "null");
        assertFalse(executor(config(), row, BEGIN).meetsCondition(1));
    }

    @Test
    void unconditionalRowsStillRequireAnActiveSchedule() {
        var row = row(1, "LOGIC_NONE", "[]");
        var config = config();
        assertFalse(executor(config, row, BEGIN - 1).meetsCondition(1));
        assertFalse(executor(config, row, config.getEndTime().getTime()).meetsCondition(1));
        config.setDisabled(true);
        assertFalse(executor(config, row, BEGIN).meetsCondition(1));
    }

    @Test
    void anOldPlayerScheduleCannotContributeConditionsToTheNewReplay() {
        var config = config();
        var row = row(1, "LOGIC_NONE", "[]");
        var executor = executor(config, row, BEGIN);
        config.setScheduleId(config.getScheduleId() + 1);
        assertFalse(executor.meetsCondition(1));
    }

    @Test
    void oneConditionListUsesASingleDayBoundarySnapshot() {
        var rows =
                List.of(
                        row(
                                1,
                                "LOGIC_AND",
                                "[" + predicate("NEW_ACTIVITY_COND_DAYS_LESS", "[2]") + "]"),
                        row(
                                2,
                                "LOGIC_AND",
                                "[" + predicate("NEW_ACTIVITY_COND_DAYS_LESS", "[2]") + "]"));
        var calls = new AtomicInteger();
        var executor =
                executor(
                        config(),
                        rows,
                        () -> calls.getAndIncrement() == 0 ? SECOND_DAY - 1 : SECOND_DAY,
                        Map.of());
        assertEquals(List.of(1, 2), executor.getMeetActivitiesConditions(List.of(1, 2)));
    }

    @Test
    void aBrokenPredicateDoesNotAbortOtherConditionsOrAlternatives() {
        var broken = predicate("NEW_ACTIVITY_COND_QUEST_FINISH", "[123]");
        var valid = predicate("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL", "[1]");
        var rows =
                List.of(
                        row(1, "LOGIC_AND", "[" + broken + "]"),
                        row(2, "LOGIC_OR", "[" + broken + "," + valid + "]"));
        var executor =
                executor(
                        config(),
                        rows,
                        () -> BEGIN,
                        Map.of(
                                NEW_ACTIVITY_COND_QUEST_FINISH,
                                new ActivityConditionBaseHandler() {
                                    @Override
                                    public boolean execute(
                                            PlayerActivityData data,
                                            ActivityConfigItem config,
                                            int... params) {
                                        throw new IllegalStateException(
                                                "injected condition failure");
                                    }
                                }));
        assertEquals(List.of(2), executor.getMeetActivitiesConditions(List.of(1, 2)));
    }

    private static com.google.gson.JsonArray resourceRows() throws Exception {
        return JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "resources/ExcelBinOutput/NewActivityCondExcelConfigData.json")))
                .getAsJsonArray();
    }

    @Test
    void allCurrentDayNodesFollowTheResetBoundaryAndTheirExactThresholds() throws Exception {
        int great = 0, lessCount = 0;
        for (var raw : resourceRows())
            for (var item : raw.getAsJsonObject().getAsJsonArray("cond")) {
                var node = item.getAsJsonObject();
                if (!node.has("type")) continue;
                String type = node.get("type").getAsString();
                if (!type.equals("NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL")
                        && !type.equals("NEW_ACTIVITY_COND_DAYS_LESS")) continue;
                int threshold = node.getAsJsonArray("param").get(0).getAsInt();
                var handler = type.endsWith("GREAT_EQUAL") ? atLeast : less;
                boolean expected = type.endsWith("GREAT_EQUAL") ? 2 >= threshold : 2 < threshold;
                assertEquals(
                        expected,
                        handler.executeAt(null, config(), SECOND_DAY, threshold),
                        raw.toString());
                if (type.endsWith("GREAT_EQUAL")) great++;
                else lessCount++;
            }
        assertEquals(2883, great);
        assertEquals(759, lessCount);
    }

    @Test
    void resourceLoadingOnlyRemovesExplicitNonePredicatesAcrossTheEntireTable() throws Exception {
        int rows = 0;
        for (var raw : resourceRows()) {
            long retained = 0;
            for (var item : raw.getAsJsonObject().getAsJsonArray("cond"))
                if (item.getAsJsonObject().has("type")
                        && !item.getAsJsonObject()
                                .get("type")
                                .getAsString()
                                .equals("NEW_ACTIVITY_COND_NONE")) retained++;
            var row = JsonUtils.decode(raw, ActivityCondExcelConfigData.class);
            row.onLoad();
            assertEquals(retained, row.getCond().size(), raw.toString());
            rows++;
        }
        assertEquals(3022, rows);
    }

    @Test
    void actualAsterRewardPeriodAndWatcherConditionsUseNoneAsConjunction() throws Exception {
        var config = config();
        config.setActivityId(2001);
        config.setCloseTime(new Date(SECOND_DAY + 13 * 86400000L));
        for (var raw : resourceRows())
            if (raw.getAsJsonObject().get("condId").getAsInt() == 2001304) {
                var row = JsonUtils.decode(raw, ActivityCondExcelConfigData.class);
                row.onLoad();
                assertTrue(
                        executor(config, row, SECOND_DAY + 13 * 86400000L).meetsCondition(2001304));
                return;
            }
        fail("Missing actual Aster reward-period condition");
    }
}
