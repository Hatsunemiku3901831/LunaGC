package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.server.ActivityCondGroup;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.activity.condition.all.DaysGreatEqual;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ActivityInfoNotifyOuterClass.ActivityInfoNotify;
import emu.grasscutter.net.proto.ActivityScheduleInfoNotifyOuterClass.ActivityScheduleInfoNotify;
import emu.grasscutter.server.packet.send.*;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

class ActivityDailyRefreshTest {
    @BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    static final int GROUP = 980003, CONDITION = 980004;
    static final long FOUR = time("2026-10-10T04:00:00+08:00");
    ActivityManager.Configuration previous;
    ActivityCondGroup previousGroup;
    final List<BasePacket> packets = new ArrayList<>();
    final Player player =
            new Player() {
                @Override
                public void sendPacket(BasePacket packet) {
                    packets.add(packet);
                }
            };

    static long time(String text) {
        return OffsetDateTime.parse(text).toInstant().toEpochMilli();
    }

    static class Handler extends DefaultActivityHandler {
        int conditionTriggers;

        @Override
        protected void triggerCondEvents(Player player) {
            conditionTriggers++;
        }
    }

    static void field(Object owner, String name, Object value) throws Exception {
        var field = ActivityManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var field = ActivityManager.class.getDeclaredField("configuration");
        field.setAccessible(true);
        previous = (ActivityManager.Configuration) field.get(null);
        var group = new ActivityCondGroup();
        group.setCondGroupId(GROUP);
        group.setCondIds(List.of(CONDITION));
        previousGroup = GameData.getActivityCondGroupMap().put(GROUP, group);
    }

    @AfterEach
    void restore() {
        ActivityManager.installConfiguration(previous);
        if (previousGroup == null) GameData.getActivityCondGroupMap().remove(GROUP);
        else GameData.getActivityCondGroupMap().put(GROUP, previousGroup);
    }

    ActivityConfigItem config(int id, long begin, long end, boolean disabled) {
        var item = new ActivityConfigItem();
        item.setActivityId(id);
        item.setScheduleId(id * 1000 + 1);
        item.setActivityType(0);
        item.setBeginTime(new Date(begin));
        item.setEndTime(new Date(end));
        item.setDisabled(disabled);
        item.onLoad();
        var handler = new Handler();
        handler.setActivityConfigItem(item);
        handler.setActivityData(
                new Gson()
                        .fromJson(
                                "{\"activityId\":" + id + ",\"condGroupId\":[" + GROUP + "]}",
                                ActivityData.class));
        item.setActivityHandler(handler);
        return item;
    }

    ActivityManager manager(List<ActivityConfigItem> configs) throws Exception {
        var active = new HashMap<Integer, ActivityConfigItem>();
        var schedules = new HashMap<Integer, ActivityConfigItem>();
        configs.forEach(
                c -> {
                    schedules.put(c.getScheduleId(), c);
                    if (!c.isDisabled()) active.put(c.getActivityId(), c);
                });
        ActivityManager.installConfiguration(new ActivityManager.Configuration(active, schedules));
        var manager =
                new ActivityManager(player) {
                    @Override
                    public void refreshActivities() {}
                };
        var conditions = new Int2ObjectOpenHashMap<ActivityCondExcelConfigData>();
        var condition =
                new Gson()
                        .fromJson(
                                "{\"id\":"
                                        + CONDITION
                                        + ",\"condComb\":\"LOGIC_AND\",\"cond\":[{\"type\":\"NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL\",\"param\":[\"2\"]}]}",
                                ActivityCondExcelConfigData.class);
        condition.onLoad();
        conditions.put(CONDITION, condition);
        var mapped = new Int2ObjectOpenHashMap<PlayerActivityData>();
        for (var item : active.values()) {
            var data =
                    PlayerActivityData.of()
                            .activityId(item.getActivityId())
                            .scheduleId(item.getScheduleId())
                            .id("persisted-" + item.getActivityId())
                            .detail("{\"progress\":7}")
                            .watcherInfoMap(new HashMap<>())
                            .build();
            manager.getPlayerActivityDataMap().put(item.getActivityId(), data);
            if (item.getActivityId() == 901) mapped.put(CONDITION, data);
        }
        field(
                manager,
                "conditionExecutor",
                new BasicActivityConditionExecutor(
                        active,
                        conditions,
                        mapped,
                        Map.of(
                                ActivityConditions.NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL,
                                new DaysGreatEqual()),
                        () -> FOUR - 1));
        return manager;
    }

    List<ActivityInfoNotify> infos() throws Exception {
        var results = new ArrayList<ActivityInfoNotify>();
        for (var packet : packets)
            if (packet instanceof PacketActivityInfoNotify)
                results.add(ActivityInfoNotify.parseFrom(packet.getData()));
        return results;
    }

    @Test
    void crossingFourPublishesNewConditionsUsingOneTimeSnapshot() throws Exception {
        var item = config(901, time("2026-10-09T10:00:00+08:00"), FOUR + 86400000, false);
        var manager = manager(List.of(item));
        manager.onDailyReset(FOUR - 1);
        assertTrue(infos().get(0).getActivityInfo().getMeetCondListList().isEmpty());
        packets.clear();
        manager.onDailyReset(FOUR);
        assertEquals(List.of(CONDITION), infos().get(0).getActivityInfo().getMeetCondListList());
        assertEquals(2, ((Handler) item.getActivityHandler()).conditionTriggers);
    }

    @Test
    void dailySchedulePacketIncludesClosedFutureAndDisabledFlags() throws Exception {
        var active = config(901, FOUR - 86400000, FOUR + 86400000, false);
        var expired = config(902, FOUR - 86400000, FOUR, false);
        var future = config(903, FOUR + 1, FOUR + 86400000, false);
        var disabled = config(904, FOUR - 86400000, FOUR + 86400000, true);
        manager(List.of(active, expired, future, disabled)).onDailyReset(FOUR);
        var packet =
                packets.stream()
                        .filter(PacketActivityScheduleInfoNotify.class::isInstance)
                        .findFirst()
                        .orElseThrow();
        var schedule = ActivityScheduleInfoNotify.parseFrom(packet.getData());
        assertEquals(4, schedule.getActivityScheduleListCount());
        for (var entry : schedule.getActivityScheduleListList())
            assertEquals(entry.getActivityId() == 901, entry.getIsOpen());
        for (var info : infos())
            if (info.getActivityInfo().getActivityId() != 901)
                assertEquals(0, info.getActivityInfo().getMeetCondListCount());
        assertEquals(1, ((Handler) active.getActivityHandler()).conditionTriggers);
        assertEquals(0, ((Handler) expired.getActivityHandler()).conditionTriggers);
        assertEquals(0, ((Handler) future.getActivityHandler()).conditionTriggers);
        assertEquals(0, ((Handler) disabled.getActivityHandler()).conditionTriggers);
    }

    @Test
    void dailyNotificationKeepsStoredActivityIdentityAndProgress() throws Exception {
        var manager = manager(List.of(config(901, FOUR - 86400000, FOUR + 86400000, false)));
        var before = manager.getPlayerActivityDataMap().get(901);
        manager.onDailyReset(FOUR);
        assertFalse(packets.isEmpty());
        assertSame(before, manager.getPlayerActivityDataMap().get(901));
        assertEquals("persisted-901", before.getId());
        assertEquals("{\"progress\":7}", before.getDetail());
    }

    @Test
    void earlyLoginDoesNotPublishAnUninitializedActivityManager() {
        var manager =
                new ActivityManager(player) {
                    @Override
                    public void refreshActivities() {}
                };
        assertDoesNotThrow(() -> manager.onDailyReset(FOUR));
        assertTrue(packets.isEmpty());
    }
}
