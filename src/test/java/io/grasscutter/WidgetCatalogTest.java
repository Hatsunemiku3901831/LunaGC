package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.WidgetCatalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

class WidgetCatalogTest {
    @TempDir Path directory;

    Path config(String content) throws Exception {
        var p = directory.resolve("widgets.json");
        Files.writeString(p, content);
        return p;
    }

    @Test
    void actualGroupExportOverridesReferenceAlias() throws Exception {
        var p =
                config(
                        "{\"widgetConfigMap\":{\"220023\":{\"tags\":[\"FlyAttach\"],\"isTeam\":true,\"abilityGroupName\":\"DynamicAbility_Toys_Endora\"}}}");
        Files.writeString(
                directory.resolve("groups.json"),
                "{\"DynamicAbility_Toys_Endora\":{\"abilityGroupSourceType\":\"ABILITY_GROUP_SOURCE_WIDGET\",\"abilityGroupTargetType\":\"ABILITY_GROUP_TARGET_TEAM\",\"targetAbilities\":[{\"abilityName\":\"CurrentResourceEndora\"}]}}");
        var entry = WidgetCatalog.load(p, directory).get(220023);
        assertEquals(List.of(1), entry.tags());
        assertEquals("CurrentResourceEndora", entry.abilities().get(0).name());
    }

    @Test
    void absentGroupExportUsesOnlyTheKnownReferenceAlias() throws Exception {
        var p =
                config(
                        "{\"widgetConfigMap\":{\"220023\":{\"tags\":[\"FlyAttach\"],\"isTeam\":true,\"abilityGroupName\":\"DynamicAbility_Toys_Endora\"},\"900\":{\"tags\":[\"FlyAttach\"],\"isTeam\":true,\"abilityGroupName\":\"UnpairedFutureWidget\"}}}");
        var loaded = WidgetCatalog.load(p, directory);
        assertEquals("SceneObj_Toys_Endora", loaded.get(220023).abilities().get(0).name());
        assertFalse(loaded.get(900).hasGroup());
    }

    @Test
    void badConfigurationDoesNotProduceAPartialCatalog() throws Exception {
        var p =
                config(
                        "{\"widgetConfigMap\":{\"220023\":{\"tags\":[\"FlyAttach\"],\"isTeam\":true,\"abilityGroupName\":\"DynamicAbility_Toys_Endora\"},\"invalid-id\":{\"tags\":[]}}}");
        var loaded = WidgetCatalog.load(p, directory);
        assertFalse(loaded.available());
        assertNull(loaded.get(220023));
    }

    @Test
    void reusableCreatorUsesItsResourceGadgetAndFractionalCooldown() throws Exception {
        var p =
                config(
                        "{cdGroupConfigMap:{'15':{coolDownSuccess:2.75}},widgetConfigMap:{'220026':{doBagType:'RetractAndCreate',gadgetId:70500025,cdGroup:15}}}");
        var creator = WidgetCatalog.load(p, directory).creator(220026);
        assertNotNull(creator);
        assertEquals(70500025, creator.gadgetId());
        assertEquals(15, creator.cooldownGroup());
        assertEquals(2750, creator.cooldownMillis());
    }

    @Test
    void consumablesAndOtherCreatorEffectsAreNotEnabledByThePlacementPort() throws Exception {
        var p =
                config(
                        "{cdGroupConfigMap:{'15':{coolDownSuccess:5}},widgetConfigMap:{'220026':{doBagType:'RetractAndCreate',isConsumeMaterial:true,gadgetId:70500025,cdGroup:15},'220080':{doBagType:'RetractAndCreate',gadgetId:70291056,cdGroup:15}}}");
        var catalog = WidgetCatalog.load(p, directory);
        assertNull(catalog.creator(220026));
        assertNull(catalog.creator(220080));
    }

    @Test
    void missingCooldownDefinitionCannotSilentlyDisableTheLimit() throws Exception {
        var p =
                config(
                        "{widgetConfigMap:{'220026':{doBagType:'RetractAndCreate',gadgetId:70500025,cdGroup:15}}}");
        assertNull(WidgetCatalog.load(p, directory).creator(220026));
    }
}
