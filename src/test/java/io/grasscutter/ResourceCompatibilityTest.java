package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import emu.grasscutter.data.excels.activity.ActivityData;
import emu.grasscutter.data.excels.activity.ActivityWatcherData;
import emu.grasscutter.data.excels.tower.TowerScheduleData;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class ResourceCompatibilityTest {
    @Test
    void towerScheduleSkipsEntriesWithoutFloors() {
        var json =
                JsonParser.parseString(
                        "{\"scheduleId\":1,\"schedules\":[{\"floorList\":null},{\"floorList\":[101]}]}");
        var data = JsonUtils.decode(json, TowerScheduleData.class);

        data.onLoad();

        assertEquals(1, data.getSchedules().size());
        assertEquals(101, data.getSchedules().get(0).getFloorList().get(0));
    }

    @Test
    void activityWithoutWatchersLoadsAsEmpty() {
        var json = JsonParser.parseString("{\"activityId\":1}");
        var data = JsonUtils.decode(json, ActivityData.class);

        data.onLoad();

        assertTrue(data.getWatcherDataList().isEmpty());
    }

    @Test
    void towerScheduleWithoutSchedulesLoadsAsEmpty() {
        var data =
                JsonUtils.decode(JsonParser.parseString("{\"scheduleId\":45}"), TowerScheduleData.class);
        data.onLoad();
        assertTrue(data.getSchedules().isEmpty());
    }

    @Test
    void activityIgnoresNullAndMissingWatcherIds() {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString("{\"activityId\":1,\"watcherId\":[null,2147483647]}"),
                        ActivityData.class);
        data.onLoad();
        assertTrue(data.getWatcherDataList().isEmpty());
    }

    @Test
    void watcherWithoutParametersKeepsItsTrigger() {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString(
                                "{\"id\":1,\"triggerConfig\":{\"triggerType\":\"TRIGGER_LOGIN\"}}"),
                        ActivityWatcherData.class);
        data.onLoad();
        assertTrue(data.getTriggerConfig().getParamList().isEmpty());
        assertEquals("TRIGGER_LOGIN", data.getTriggerConfig().getTriggerType());
    }

    @Test
    void embeddedWatcherWithoutParametersLoadsAsEmpty() {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString("{\"triggerType\":\"TRIGGER_LOGIN\",\"paramList\":null}"),
                        ActivityWatcherData.WatcherTrigger.class);
        data.onLoad();
        assertTrue(data.getParamList().isEmpty());
    }
}
