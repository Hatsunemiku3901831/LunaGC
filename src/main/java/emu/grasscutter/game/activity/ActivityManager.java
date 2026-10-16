package emu.grasscutter.game.activity;

import com.esotericsoftware.reflectasm.ConstructorAccess;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass;
import emu.grasscutter.server.packet.send.*;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.*;

@Getter
public class ActivityManager extends BasePlayerManager {
    public record Configuration(Map<Integer, ActivityConfigItem> activities,
                                Map<Integer, ActivityConfigItem> schedules,
                                Map<Integer, Integer> currencyActivities) {
        public Configuration { currencyActivities = Map.copyOf(currencyActivities); }
        public Configuration(Map<Integer, ActivityConfigItem> activities, Map<Integer, ActivityConfigItem> schedules) {
            this(activities, schedules, ActivityCurrencies.catalog());
        }
    }

    private static volatile Configuration configuration = new Configuration(Map.of(), Map.of(), Map.of());

    static Configuration configurationSnapshot() { return configuration; }
    private final Map<Integer, PlayerActivityData> playerActivityDataMap = new ConcurrentHashMap<>();
    private ActivityConditionExecutor conditionExecutor;
    private Configuration loadedConfiguration;
    private boolean notificationsEnabled;

    public static Map<Integer, ActivityConfigItem> getScheduleActivityConfigMap() {
        return configuration.schedules();
    }

    /** Build all handlers before publishing, so invalid input leaves the live state intact. */
    public static Configuration prepareConfiguration(Collection<ActivityConfigItem> items) {
        var handlers = new HashMap<ActivityType, ConstructorAccess<?>>();
        var watchers = new HashMap<WatcherTriggerType, ConstructorAccess<?>>();
        Grasscutter.reflector.getSubTypesOf(ActivityHandler.class).forEach(type -> {
            var annotation = type.getAnnotation(GameActivity.class);
            if (annotation != null) handlers.put(annotation.value(), ConstructorAccess.get(type));
        });
        Grasscutter.reflector.getSubTypesOf(ActivityWatcher.class).forEach(type -> {
            var annotation = type.getAnnotation(ActivityWatcherType.class);
            if (annotation != null) watchers.put(annotation.value(), ConstructorAccess.get(type));
        });
        var activities = new HashMap<Integer, ActivityConfigItem>();
        var schedules = new HashMap<Integer, ActivityConfigItem>();
        var activityIds = new HashSet<Integer>();
        for (var item : items) {
            item.onLoad();
            if (item.getBeginTime() == null || item.getEndTime() == null
                    || !item.getEndTime().after(item.getBeginTime())
                    || item.getScheduleId() <= 0 || item.getActivityId() <= 0) {
                throw new IllegalArgumentException("活动时间或排期 ID 无效: " + item.getActivityId());
            }
            if (!activityIds.add(item.getActivityId()) || schedules.putIfAbsent(item.getScheduleId(), item) != null) {
                throw new IllegalArgumentException("重复的活动或排期 ID: " + item.getActivityId());
            }
            if (item.isDisabled()) continue;
            var data = GameData.getActivityDataMap().get(item.getActivityId());
            if (data == null)
                throw new IllegalArgumentException("活动资源不存在: " + item.getActivityId());
            var constructor = handlers.get(ActivityType.getTypeByName(data.getActivityType()));
            var handler = constructor == null ? new DefaultActivityHandler()
                    : (ActivityHandler) constructor.newInstance();
            handler.setActivityConfigItem(item);
            handler.initWatchers(watchers);
            item.setActivityHandler(handler);
            activities.put(item.getActivityId(), item);
        }
        return new Configuration(Map.copyOf(activities), Map.copyOf(schedules));
    }

    public static void installConfiguration(Configuration prepared) { configuration = prepared; }

    public static void loadActivityConfigData() {
        try {
            installConfiguration(
                    prepareConfiguration(
                            DataLoader.loadList("ActivityConfig.json", ActivityConfigItem.class)));
            Grasscutter.getLogger()
                    .debug("Loaded {} activities.", configuration.activities().size());
            Grasscutter.getLogger().info("Loaded {} activity currency bindings.", configuration.currencyActivities().size());
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Unable to load activities config.", e);
        }
    }

    public ActivityManager(Player player) {
        this(player, false);
    }

    /** Load conditions before login quests without sending activity packets before player data. */
    public ActivityManager(Player player, boolean deferLoginNotifications) {
        super(player);
        notificationsEnabled = !deferLoginNotifications;
        refreshActivities();
    }

    /** Closing preserves progress; a fresh schedule resets the existing database document. */
    public void refreshActivities() {
        synchronized (player.getInventory()) {
            synchronized (this) { refreshActivitiesLocked(); }
        }
    }

    private void refreshActivitiesLocked() {
        var snapshot = configuration;
        playerActivityDataMap.keySet().removeIf(id -> !snapshot.activities().containsKey(id));
        for (var item : snapshot.activities().values()) {
            var data = playerActivityDataMap.get(item.getActivityId());
            if (data == null) data = PlayerActivityData.getByPlayer(player, item.getActivityId());
            if (data == null || (data.getScheduleId() != 0 && data.getScheduleId() != item.getScheduleId())) {
                var previous = data;
                data = item.getActivityHandler().initPlayerActivityData(player);
                if (previous != null) data.inheritSaveIdentity(previous);
                data.save();
            } else if (data.getScheduleId() == 0) {
                data.setScheduleId(item.getScheduleId());
                data.save();
            }
            data.setPlayer(player);
            data.setActivityHandler(item.getActivityHandler());
            synchronized (data) {
                var originalWatchers = data.getWatcherInfoMap();
                var previousWatchers =
                        originalWatchers == null ? null : new HashMap<>(originalWatchers);
                String previousDetail = data.getDetail();
                try {
                    boolean changed = originalWatchers == null;
                    if (changed) data.setWatcherInfoMap(new HashMap<>());
                    changed |= item.getActivityHandler().onLoadPlayerActivityData(data);
                    changed |= item.getActivityHandler().refreshWatcherProgress(data);
                    if (changed) data.saveSync();
                } catch (RuntimeException | Error failure) {
                    if (originalWatchers != null) {
                        originalWatchers.clear();
                        originalWatchers.putAll(previousWatchers);
                    }
                    data.setWatcherInfoMap(originalWatchers);
                    data.setDetailJson(previousDetail);
                    throw failure;
                }
            }
            playerActivityDataMap.put(item.getActivityId(), data);
        }
        conditionExecutor =
                new BasicActivityConditionExecutor(
                        snapshot.activities(),
                        GameData.getActivityCondExcelConfigDataMap(),
                        PlayerActivityDataMappingBuilder.buildPlayerActivityDataByActivityCondId(
                                playerActivityDataMap),
                        AllActivityConditionBuilder.buildActivityConditions());
        loadedConfiguration = snapshot;
        if (notificationsEnabled) sendActivitySnapshot(snapshot);
        // During construction Player has not installed this manager yet. Login triggers after
        // assignment; later GM refreshes must unlock quests without requiring a new login.
        triggerActivityConditions();
    }

    public void onPlayerLogin() {
        synchronized (player.getInventory()) {
            synchronized (this) { onPlayerLoginLocked(); }
        }
    }

    private void onPlayerLoginLocked() {
        if (loadedConfiguration != configuration) refreshActivities();
        notificationsEnabled = true;
        sendActivitySnapshot(loadedConfiguration);
        triggerActivityConditions();
    }

    private void sendActivitySnapshot(Configuration snapshot) {
        long now = System.currentTimeMillis();
        player.sendPacket(new PacketActivityScheduleInfoNotify(snapshot.schedules().values(), now));
        snapshot.activities().values().stream()
                .filter(item -> item.isActiveAt(now))
                .forEach(item -> player.sendPacket(new PacketActivityInfoNotify(
                        getInfoProtoByActivityId(snapshot, item.getActivityId(), now))));
    }

    public void triggerWatcher(WatcherTriggerType type, String... params) {
        configuration.activities().values().stream()
                .filter(item -> item.isActiveAt(System.currentTimeMillis()))
                .map(ActivityConfigItem::getActivityHandler)
                .map(ActivityHandler::getWatchersMap).map(map -> map.get(type))
                .filter(Objects::nonNull).flatMap(Collection::stream)
                .forEach(watcher -> {
                    var data = playerActivityDataMap.get(watcher.getActivityHandler().getActivityConfigItem().getActivityId());
                    if (data != null) watcher.trigger(data, params);
                });
    }

    /** A queued round must not contribute to a newer activity schedule. */
    public void triggerWatcher(int activityId, int scheduleId, WatcherTriggerType type, String... params) {
        synchronized (player.getInventory()) {
            synchronized (this) { triggerWatcherLocked(activityId, scheduleId, type, params); }
        }
    }

    private void triggerWatcherLocked(int activityId, int scheduleId, WatcherTriggerType type, String... params) {
        var item = configuration.activities().get(activityId);
        var data = playerActivityDataMap.get(activityId);
        if (item == null || data == null || !item.isActiveAt(System.currentTimeMillis())
                || item.getScheduleId() != scheduleId || data.getScheduleId() != scheduleId) return;
        item.getActivityHandler().getWatchersMap().getOrDefault(type, List.of())
                .forEach(watcher -> watcher.trigger(data, params));
    }

    public boolean isActivityActive(int id) {
        var item = configuration.activities().get(id);
        return item != null && item.isActiveAt(System.currentTimeMillis());
    }

    public boolean hasActivityEnded(int id) {
        var item = configuration.activities().get(id);
        return item == null || System.currentTimeMillis() >= item.getEndTime().getTime();
    }

    public boolean isActivityOpen(int id) {
        var item = configuration.activities().get(id);
        return item != null && item.isOpenAt(System.currentTimeMillis());
    }

    public int getOpenDay(int id) {
        var item = configuration.activities().get(id);
        if (item == null || System.currentTimeMillis() < item.getOpenTime().getTime()) return 0;
        if (id == emu.grasscutter.game.activity.aster.AsterSchedule.ACTIVITY_ID) {
            return emu.grasscutter.game.activity.aster.AsterSchedule.dayIndex(item, System.currentTimeMillis());
        }
        return item == null ? 0 : Math.max(0, (int) TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - item.getOpenTime().getTime()) + 1);
    }

    public boolean isActivityClosed(int id) {
        var item = configuration.activities().get(id);
        return item == null || System.currentTimeMillis() >= item.getCloseTime().getTime();
    }

    public boolean meetsCondition(int id) { return conditionExecutor.meetsCondition(id); }

    /** Publish newly unlocked days without replacing the player's stored schedule or progress. */
    public void onDailyReset(long now) {
        synchronized (player.getInventory()) {
            synchronized (this) { onDailyResetLocked(now); }
        }
    }

    private void onDailyResetLocked(long now) {
        if (conditionExecutor == null || !notificationsEnabled) return;
        var snapshot = configuration;
        player.sendPacket(new PacketActivityScheduleInfoNotify(snapshot.schedules().values(), now));
        for (var item : snapshot.schedules().values()) {
            var info = getInfoProtoByActivityId(snapshot, item.getActivityId(), now);
            player.sendPacket(new PacketActivityInfoNotify(info));
        }
        if (canTriggerQuestConditions())
            snapshot.activities().values().stream()
                    .filter(item -> item.isActiveAt(now))
                    .forEach(item -> item.getActivityHandler().triggerCondEvents(player));
    }

    public void triggerActivityConditions() {
        if (!canTriggerQuestConditions()) return;
        configuration.activities().values().stream()
                .filter(item -> item.isActiveAt(System.currentTimeMillis()))
                .forEach(item -> item.getActivityHandler().triggerCondEvents(player));
    }

    private boolean canTriggerQuestConditions() {
        return notificationsEnabled && conditionExecutor != null
                && player.getActivityManager() == this
                && player.getWorld() != null
                && player.getQuestManager().isQuestingEnabled();
    }

    /** A story completion can unlock activity side quests in the same session. */
    public void onQuestStateChanged(int questId) {
        if (!canTriggerQuestConditions()) return;
        var snapshot = configuration;
        for (var item : snapshot.activities().values()) {
            if (!item.isActiveAt(System.currentTimeMillis())) continue;
            var meta = item.getActivityHandler().getActivityData();
            if (meta == null || meta.getCondGroupId() == null) continue;
            var affected = new LinkedHashSet<Integer>();
            for (int groupId : meta.getCondGroupId()) {
                var group = GameData.getActivityCondGroupMap().get(groupId);
                if (group == null || group.getCondIds() == null) continue;
                for (int id : group.getCondIds()) {
                    var condition = GameData.getActivityCondExcelConfigDataMap().get(id);
                    if (condition == null || condition.getCond() == null) continue;
                    if (condition.getCond().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(
                                    c ->
                                            c.getType()
                                                            == ActivityConditions
                                                                    .NEW_ACTIVITY_COND_QUEST_FINISH
                                                    && c.getParam() != null
                                                    && !c.getParam().isEmpty()
                                                    && String.valueOf(questId)
                                                            .equals(c.getParam().get(0))))
                        affected.add(id);
                }
            }
            if (affected.isEmpty()) continue;
            for (int id : affected)
                player.getQuestManager()
                        .queueEvent(
                                emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_ACTIVITY_COND,
                                id);
            player.sendPacket(
                    new PacketActivityInfoNotify(
                            getInfoProtoByActivityId(
                                    snapshot, item.getActivityId(), System.currentTimeMillis())));
        }
    }

    public ActivityInfoOuterClass.ActivityInfo getInfoProtoByActivityId(int id) {
        return getInfoProtoByActivityId(configuration, id, System.currentTimeMillis());
    }

    private ActivityInfoOuterClass.ActivityInfo getInfoProtoByActivityId(
            Configuration snapshot, int id, long now) {
        var item = snapshot.activities().get(id);
        if (item == null || !item.isActiveAt(now)) {
            return ActivityInfoOuterClass.ActivityInfo.newBuilder().setActivityId(id).build();
        }
        return item.getActivityHandler()
                .toProto(playerActivityDataMap.get(id), conditionExecutor, now);
    }

    public Optional<ActivityHandler> getActivityHandler(ActivityType type) {
        return configuration.activities().values().stream()
                .filter(item -> item.getActivityType() == type.getValue() && item.isActiveAt(System.currentTimeMillis()))
                .map(ActivityConfigItem::getActivityHandler).findFirst();
    }

    public <T extends ActivityHandler> Optional<T> getActivityHandlerAs(ActivityType type, Class<T> clazz) {
        return getActivityHandler(type).filter(clazz::isInstance).map(clazz::cast);
    }

    public Optional<Integer> getActivityIdByActivityType(ActivityType type) {
        return getActivityHandler(type).map(ActivityHandler::getActivityConfigItem).map(ActivityConfigItem::getActivityId);
    }

    public Optional<PlayerActivityData> getPlayerActivityDataByActivityType(ActivityType type) {
        return getActivityIdByActivityType(type).map(playerActivityDataMap::get);
    }

    public Optional<ActivityInfoOuterClass.ActivityInfo> getInfoProtoByActivityType(ActivityType type) {
        return getActivityIdByActivityType(type).map(this::getInfoProtoByActivityId);
    }
}
