package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.tower.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

class TowerManagementTest {
    @TempDir Path directory;

    @Test
    void settingsSurviveRestartAndRejectStaleBrowser() throws Exception {
        var path = directory.resolve("TowerPresets.json");
        var store = new TowerPresetStore(path);
        var profile = new TowerBattleProfile(62, 1.5, 2, 0.5, -5, List.of());
        assertFalse(Files.exists(path));
        assertEquals(1, store.save(0, profile).revision());
        assertEquals(profile, new TowerPresetStore(path).get().profile());
        assertThrows(IllegalStateException.class, () -> store.save(0, TowerBattleProfile.DEFAULT));
        assertEquals(profile, new TowerPresetStore(path).get().profile());
    }

    @Test
    void failedDiskWriteDoesNotPublishSettings() throws Exception {
        var path = directory.resolve("TowerPresets.json");
        var store = new TowerPresetStore(path);
        Files.createDirectory(path);
        Files.writeString(path.resolve("block"), "occupied");
        assertThrows(
                java.io.IOException.class,
                () -> store.save(0, new TowerBattleProfile(62, 1, 1, 1, 0, List.of())));
        assertEquals(0, store.get().revision());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 21, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidStatsRejected(double number) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TowerBattleProfile(62, number, 1, 1, 0, List.of()));
    }

    @Test
    void snapshotsAreImmutableAndTargetsUnique() {
        var overrides = new ArrayList<TowerBattleProfile.MonsterOverride>();
        var monster = new TowerBattleProfile.MonsterOverride(233746004, 4001, 0, 100, 2, 1, 1);
        overrides.add(monster);
        var profile = new TowerBattleProfile(62, 1, 1, 1, -10, overrides);
        overrides.clear();
        assertEquals(100, profile.level(95, 233746004, 4001));
        assertEquals(85, profile.level(95, 233746005, 5001));
        assertThrows(UnsupportedOperationException.class, () -> profile.monsters().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new TowerBattleProfile(62, 1, 1, 1, 0, List.of(monster, monster)));
    }

    @Test
    void deserializationCannotBypassValidation() {
        assertThrows(
                RuntimeException.class,
                () ->
                        TowerBattleProfile.fromJson(
                                com.google.gson.JsonParser.parseString(
                                        "{scheduleId:62,hp:-1,attack:1,defense:1,levelOffset:0,monsters:[]}")));
        assertThrows(
                RuntimeException.class,
                () ->
                        TowerBattleProfile.fromJson(
                                com.google.gson.JsonParser.parseString(
                                        "{scheduleId:62.5,hp:1,attack:1,defense:1,levelOffset:0,monsters:[]}")));
        assertThrows(
                RuntimeException.class,
                () -> new TowerPresetStore.State(2, 0, TowerBattleProfile.DEFAULT));
    }

    @Test
    void abyssFloorScalingAndLevelClamps() {
        assertEquals(2, TowerBattleProfile.floorHp(8));
        assertEquals(2, TowerBattleProfile.floorHp(11));
        assertEquals(2.5, TowerBattleProfile.floorHp(12));
        assertEquals(1, new TowerBattleProfile(62, 1, 1, 1, -90, List.of()).level(80, 0, 0));
        assertEquals(200, new TowerBattleProfile(62, 1, 1, 1, 100, List.of()).level(110, 0, 0));
    }

    @Test
    void affixesDoNotAccumulateOrModifyCachedLua() {
        var script = new ArrayList<>(List.of(6105, 1026));
        var base = List.of(1026, 1);
        for (int i = 0; i < 10; i++)
            assertEquals(List.of(6105, 1026, 1), EntityMonster.mergeAffixes(script, base));
        assertEquals(List.of(6105, 1026), script);
        assertEquals(List.of(1026, 1), base);
        assertEquals(List.of(), EntityMonster.mergeAffixes(null, null));
    }

    @Test
    void historicalCatalogueDoesNotInventMissingWaves() {
        var catalog = new TowerCatalog();
        var periods = catalog.summary().getAsJsonArray("periods");
        assertEquals(90, periods.size());
        assertEquals(
                26,
                java.util.stream.StreamSupport.stream(periods.spliterator(), false)
                        .filter(
                                p ->
                                        p.getAsJsonObject()
                                                .get("status")
                                                .getAsString()
                                                .equals("scripted"))
                        .count());
        var period = catalog.details(62);
        assertEquals("2023-01-16 04:00:00", period.get("startsAt").getAsString());
        assertEquals(5, period.getAsJsonArray("floors").size());
        var unavailable = catalog.details(63);
        assertEquals("unavailable", unavailable.get("status").getAsString());
        assertFalse(unavailable.getAsJsonArray("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> catalog.details(99999));
    }

    @Test
    void incompleteSceneIsRejectedEvenWhenItHasAGroupFile() throws Exception {
        var dir = directory.resolve("Scene/33746");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("scene33746_group233746004.lua"), "monsters = {{monster_id=1}}");
        assertFalse(TowerSceneResources.complete(directory, 33746));
        Files.writeString(dir.resolve("scene33746.lua"), "blocks={33746}");
        Files.writeString(
                dir.resolve("scene33746_block33746.lua"), "groups={{id=233746004},{id=233746005}}");
        assertFalse(TowerSceneResources.complete(directory, 33746));
    }

    @Test
    void currentResourceFloorEightToTwelveHaveCompleteSceneDependencies() {
        var period = new TowerCatalog().details(62);
        for (var f : period.getAsJsonArray("floors"))
            for (var c : f.getAsJsonObject().getAsJsonArray("chambers")) {
                int id = c.getAsJsonObject().get("sceneId").getAsInt();
                assertTrue(
                        TowerSceneResources.complete(Path.of("resources/Scripts"), id),
                        "scene " + id);
            }
    }
}
