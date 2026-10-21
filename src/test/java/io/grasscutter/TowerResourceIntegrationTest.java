package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.monster.*;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;

/** Checks actual files and Lua, independent of captured packet fixtures. */
class TowerResourceIntegrationTest {
    private static final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        load("TowerSchedule", TowerScheduleData.class, GameData.getTowerScheduleDataMap());
        GameData.getTowerScheduleDataMap().values().forEach(TowerScheduleData::onLoad);
        load("TowerFloor", TowerFloorData.class, GameData.getTowerFloorDataMap());
        load("TowerLevel", TowerLevelData.class, GameData.getTowerLevelDataMap());
        load("Dungeon", DungeonData.class, GameData.getDungeonDataMap());
        load("Monster", MonsterData.class, GameData.getMonsterDataMap());
        load("MonsterCurve", MonsterCurveData.class, GameData.getMonsterCurveDataMap());
    }

    private static <T extends GameResource> void load(
            String name, Class<T> type, Int2ObjectMap<T> target) throws Exception {
        var previous = new Int2ObjectOpenHashMap<T>(target);
        restore.add(
                () -> {
                    target.clear();
                    target.putAll(previous);
                });
        target.clear();
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/" + name + "ExcelConfigData.json"), type))
            target.put(row.getId(), row);
    }

    @AfterAll
    static void cleanup() {
        restore.forEach(Runnable::run);
        restore.clear();
    }

    @Test
    void everyEnabledHistoricalPeriodMatchesFilesAndRuntimeTables() throws Exception {
        var catalog = new TowerCatalog();
        int checked = 0;
        for (var p : catalog.summary().getAsJsonArray("periods")) {
            var period = p.getAsJsonObject();
            if (!period.get("status").getAsString().equals("scripted")) continue;
            catalog.validate(
                    new TowerBattleProfile(
                            period.get("scheduleId").getAsInt(), 1, 1, 1, 0, List.of()));
            checked++;
        }
        assertEquals(26, checked);
    }

    @Test
    void allReferencedHistoricalGroupsCompileAndMatchSpawnDefinitions() throws Exception {
        var catalog = new TowerCatalog();
        var seen = new HashSet<Integer>();
        int monsters = 0;
        for (var p : catalog.summary().getAsJsonArray("periods")) {
            var period = p.getAsJsonObject();
            if (!period.get("status").getAsString().equals("scripted")) continue;
            for (var f :
                    catalog.details(period.get("scheduleId").getAsInt()).getAsJsonArray("floors")) {
                for (var c : f.getAsJsonObject().getAsJsonArray("chambers")) {
                    var scene = c.getAsJsonObject().getAsJsonObject("scene");
                    for (var g : scene.getAsJsonArray("groups")) {
                        var group = g.getAsJsonObject();
                        int id = group.get("groupId").getAsInt();
                        if (!seen.add(id)) continue;
                        var loaded = SceneGroup.of(id).load(scene.get("sceneId").getAsInt());
                        assertNotNull(loaded.getScript(), "group " + id);
                        assertNotNull(loaded.monsters, "group " + id);
                        assertEquals(
                                group.getAsJsonArray("monsters").size(),
                                loaded.monsters.size(),
                                "group " + id);
                        for (var m : group.getAsJsonArray("monsters")) {
                            var monster = m.getAsJsonObject();
                            assertEquals(
                                    monster.get("monsterId").getAsInt(),
                                    loaded.monsters.get(monster.get("configId").getAsInt())
                                            .monster_id);
                            monsters++;
                        }
                        for (var api : group.getAsJsonArray("scriptApis"))
                            assertTrue(
                                    Arrays.stream(ScriptLib.class.getMethods())
                                            .anyMatch(
                                                    method ->
                                                            method.getName()
                                                                    .equals(api.getAsString())),
                                    api.getAsString());
                    }
                }
            }
        }
        assertEquals(375, seen.size());
        assertEquals(934, monsters);
    }

    @Test
    void unavailableOrForeignOverridesCannotActivate() {
        var catalog = new TowerCatalog();
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validate(new TowerBattleProfile(63, 1, 1, 1, 0, List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        catalog.validate(
                                new TowerBattleProfile(
                                        62,
                                        1,
                                        1,
                                        1,
                                        0,
                                        List.of(
                                                new TowerBattleProfile.MonsterOverride(
                                                        123, 1, 0, 0, 1, 1, 1)))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        catalog.validate(
                                new TowerBattleProfile(
                                        62,
                                        1,
                                        1,
                                        1,
                                        0,
                                        List.of(
                                                new TowerBattleProfile.MonsterOverride(
                                                        233746004,
                                                        4001,
                                                        Integer.MAX_VALUE,
                                                        0,
                                                        1,
                                                        1,
                                                        1)))));
    }
}
