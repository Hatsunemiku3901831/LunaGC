package emu.grasscutter.game.shop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.activity.ActivityShopSheetData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.AsterSchedule;
import emu.grasscutter.game.activity.condition.ActivityDayCalendar;
import emu.grasscutter.game.player.Player;

import java.io.IOException;
import java.util.*;

/**
 * Activity exchange windows follow the current replay, including the post-gameplay claim period.
 */
public final class ActivityShop {
    private ActivityShop() {}

    public record Sheet(int id, int beginTime, int endTime, boolean preview, boolean open) {}

    public record Access(boolean activityShop, ActivityConfigItem config, List<Sheet> sheets) {
        public boolean isOpen() {
            return !activityShop || config != null && !sheets.isEmpty();
        }

        public Sheet sheet(ShopInfo goods) {
            return sheets.stream()
                    .filter(s -> s.id() == goods.getSecondarySheetId())
                    .findFirst()
                    .orElse(null);
        }

        public boolean canDisplay(ShopInfo goods) {
            if (!activityShop) return true;
            var sheet = sheet(goods);
            return config != null && sheet != null && (sheet.preview() || sheet.open());
        }

        public boolean canPurchase(ShopInfo goods) {
            if (!activityShop) return true;
            var sheet = sheet(goods);
            return config != null && sheet != null && sheet.open();
        }

        public ShopLimit prepareLimit(Player player, ShopInfo goods) {
            var limit = player.getGoodsLimit(goods.getGoodsId());
            if (!activityShop || config == null) return limit;
            if (limit == null) {
                limit = new ShopLimit();
                limit.setShopGoodId(goods.getGoodsId());
                player.getShopLimit().add(limit);
            }
            limit.bindActivitySchedule(config.getScheduleId());
            if (config.getActivityId() == AsterSchedule.ACTIVITY_ID) {
                var data =
                        player.getActivityManager()
                                .getPlayerActivityDataMap()
                                .get(config.getActivityId());
                synchronized (data) {
                    limit.setHasBoughtInPeriod(
                            Math.max(
                                    limit.getHasBoughtInPeriod(),
                                    AsterSchedule.progress(data).shopBought(goods.getGoodsId())));
                }
            }
            return limit;
        }
    }

    private static final class SourceSchedules {
        static final Map<Integer, Integer> ACTIVITIES = load();

        private static Map<Integer, Integer> load() {
            var ids = new HashMap<Integer, Integer>();
            try {
                for (var row :
                        DataLoader.loadList("ActivityHistory.json", HistoricalActivity.class)) {
                    if (row.getScheduleId() <= 0 || row.getActivityId() <= 0) continue;
                    var prior = ids.putIfAbsent(row.getScheduleId(), row.getActivityId());
                    if (prior != null && prior != row.getActivityId()) {
                        throw new IllegalArgumentException("Conflicting activity source schedule");
                    }
                }
                return Map.copyOf(ids);
            } catch (IOException | RuntimeException invalid) {
                Grasscutter.getLogger()
                        .warn("Unable to resolve historical activity shops", invalid);
                return Map.of();
            }
        }
    }

    /** Original resource schedules remain stable when GM allocates a new replay schedule. */
    public static Integer sourceActivityId(int scheduleId) {
        return SourceSchedules.ACTIVITIES.get(scheduleId);
    }

    public static Access resolve(Player player, int shopType, long now) {
        var overall = GameData.getActivityShopDataMap().get(shopType);
        boolean activityShop =
                overall != null
                        || ShopType.getById(shopType).name().startsWith("SHOP_TYPE_ACTIVITY_");
        if (!activityShop) return new Access(false, null, List.of());
        var closed = new Access(true, null, List.of());
        if (overall == null
                || player == null
                || player.getActivityManager() == null
                || overall.getSheetList() == null
                || overall.getSheetList().isEmpty()) return closed;

        // Source schedule IDs identify the activity. GM reruns deliberately receive a fresh ID.
        var schedules = ActivityManager.getScheduleActivityConfigMap();
        var sourceActivity = sourceActivityId(overall.getScheduleId());
        var config =
                sourceActivity == null
                        ? schedules.get(overall.getScheduleId())
                        : schedules.values().stream()
                                .filter(c -> c.getActivityId() == sourceActivity)
                                .findFirst()
                                .orElse(null);
        if (config == null || !config.isActiveAt(now)) return closed;
        var manager = player.getActivityManager();
        var data = manager.getPlayerActivityDataMap().get(config.getActivityId());
        if (data == null
                || data.getActivityId() != config.getActivityId()
                || data.getScheduleId() != config.getScheduleId()
                || data.getUid() != player.getUid()
                || data.getPlayer() != player) return closed;
        if (config.getActivityId() == AsterSchedule.ACTIVITY_ID) {
            var preview = GameData.getAsterPreviewDataMap().get(config.getActivityId());
            if (preview == null || player.getLevel() < preview.getUnlockLevel()) return closed;
        }
        var conditions = config.getMeetCondList();
        if (conditions != null
                && !conditions.isEmpty()
                && (manager.getConditionExecutor() == null
                        || !manager.getConditionExecutor()
                                .getMeetActivitiesConditions(conditions, now)
                                .containsAll(conditions))) return closed;

        var sheets = new ArrayList<Sheet>();
        for (Integer id : overall.getSheetList()) {
            if (id == null) continue;
            var sheet = GameData.getActivityShopSheetDataMap().get(id.intValue());
            if (sheet == null) continue;
            var window = sheet(config, sheet, now);
            if (window != null) sheets.add(window);
        }
        return new Access(true, config, List.copyOf(sheets));
    }

    private static Sheet sheet(ActivityConfigItem config, ActivityShopSheetData sheet, long now) {
        if (sheet.getCond() == null) return null;
        boolean supported =
                sheet.getCondComb() == null
                        || Set.of("LOGIC_NONE", "LOGIC_AND").contains(sheet.getCondComb());
        int day = 1;
        for (var condition : sheet.getCond()) {
            if (condition == null || condition.getParam() == null) return null;
            String type = condition.getType();
            if (type == null || type.equals("ACTIVITY_SHOP_SHEET_COND_NONE")) {
                if (!condition.getParam().isEmpty()) return null;
            } else if (type.equals("ACTIVITY_SHOP_SHEET_COND_TIME_EQUAL_GREATER")) {
                if (condition.getParam().size() != 1
                        || condition.getParam().get(0) == null
                        || condition.getParam().get(0) < 1) return null;
                day = Math.max(day, condition.getParam().get(0));
            } else {
                // Phase/quest conditions need their gameplay implementation before purchase opens.
                supported = false;
            }
        }
        try {
            long begin = ActivityDayCalendar.beginTime(config, day);
            long end = config.getEndTime().getTime();
            if (begin < 0 || begin >= end || end > Integer.MAX_VALUE * 1000L) return null;
            return new Sheet(
                    sheet.getId(),
                    Math.toIntExact(begin / 1000),
                    Math.toIntExact(end / 1000),
                    sheet.isAheadPreview(),
                    supported && now >= begin);
        } catch (ArithmeticException | java.time.DateTimeException invalid) {
            return null;
        }
    }
}
