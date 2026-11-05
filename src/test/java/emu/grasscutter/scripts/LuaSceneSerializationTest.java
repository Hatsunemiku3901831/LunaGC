package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.scripts.data.SceneRegion;
import emu.grasscutter.scripts.data.SceneSuite;
import emu.grasscutter.scripts.data.SceneTrigger;
import emu.grasscutter.scripts.serializer.LuaSerializer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Direct serializer/data-class regressions; no ScriptLoader, server or database initialization. */
final class LuaSceneSerializationTest {
    private final LuaSerializer serializer = new LuaSerializer();

    @Test
    void numericStringsRemainStringsAlongsideNumbersBooleansAndText() {
        var values =
                serializer.toList(
                        Object.class,
                        table(
                                "{tostring(52001), '3.75', '-1', 'normal', '中文', 7, 3.5, true,"
                                    + " false}"));

        assertEquals(List.of("52001", "3.75", "-1", "normal", "中文", 7, 3.5f, true, false), values);
    }

    @Test
    void numericStringTriggerNamesBindThroughProductionSuiteInitialization() {
        var trigger =
                serializer.toObject(
                        SceneTrigger.class, table("{name=tostring(52001), config_id=1}"));
        var ordinary =
                serializer.toObject(SceneTrigger.class, table("{name='ordinary', config_id=2}"));
        var suite =
                serializer.toObject(
                        SceneSuite.class, table("{triggers={tostring(52001), 'ordinary'}}"));
        var group = new SceneGroup();
        group.triggers = Map.of("52001", trigger, "ordinary", ordinary);

        assertDoesNotThrow(() -> suite.init(group));
        assertEquals(List.of("52001", "ordinary"), suite.triggers);
        assertEquals(List.of(trigger, ordinary), suite.sceneTriggers);
    }

    @Test
    void normalTypedScalarListsKeepTheirValuesAndRuntimeTypes() {
        assertAll(
                () ->
                        assertEquals(
                                List.of(7, -2, 0),
                                serializer.toList(Integer.class, table("{7,-2,0}"))),
                () ->
                        assertEquals(
                                List.of(3.5f, -2.25f),
                                serializer.toList(Float.class, table("{3.5,-2.25}"))),
                () ->
                        assertEquals(
                                List.of("plain", "中文"),
                                serializer.toList(String.class, table("{'plain','中文'}"))),
                () ->
                        assertEquals(
                                List.of(true, false),
                                serializer.toList(Boolean.class, table("{true,false}"))));
    }

    @Test
    void monsterEliteFlagBindsBothTrueAndFalse() {
        var elite = serializer.toObject(SceneMonster.class, table("{isElite=true}"));
        var normal = serializer.toObject(SceneMonster.class, table("{isElite=false}"));

        assertAll(() -> assertTrue(elite.isElite), () -> assertFalse(normal.isElite));
    }

    @Test
    void gadgetPointArrayFlagBindsBothTrueAndFalse() {
        var enabled = serializer.toObject(SceneGadget.class, table("{is_use_point_array=true}"));
        var disabled = serializer.toObject(SceneGadget.class, table("{is_use_point_array=false}"));

        assertAll(
                () -> assertTrue(enabled.is_use_point_array),
                () -> assertFalse(disabled.is_use_point_array));
    }

    @Test
    void fractionalSphereRadiusPreservesThreeDimensionalBoundary() {
        var region =
                serializer.toObject(
                        SceneRegion.class, table("{shape=1,pos={x=10,y=20,z=30},radius=2.75}"));

        assertAll(
                () -> assertEquals(2.75f, region.radius),
                () -> assertTrue(region.contains(new Position(12.5f, 20, 30))),
                () -> assertTrue(region.contains(new Position(12.75f, 20, 30))),
                () -> assertTrue(region.contains(new Position(10, 22.75f, 30))),
                () -> assertTrue(region.contains(new Position(11.5f, 21.5f, 31.5f))),
                () -> assertFalse(region.contains(new Position(12.751f, 20, 30))),
                () -> assertFalse(region.contains(new Position(10, 20, 32.751f))));
    }

    @Test
    void fractionalCylinderRadiusKeepsRadialAndHeightBoundaries() {
        var region =
                serializer.toObject(
                        SceneRegion.class,
                        table("{shape=3,pos={x=10,y=20,z=30},radius=2.75,height=4}"));

        assertAll(
                () -> assertEquals(2.75f, region.radius),
                () -> assertTrue(region.contains(new Position(12.5f, 20, 30))),
                () -> assertTrue(region.contains(new Position(12.75f, 22, 30))),
                () -> assertTrue(region.contains(new Position(10, 18, 32.75f))),
                () -> assertFalse(region.contains(new Position(12.751f, 20, 30))),
                () -> assertFalse(region.contains(new Position(10, 22.001f, 30))),
                () -> assertFalse(region.contains(new Position(10, 17.999f, 30))));
    }

    private LuaTable table(String source) {
        return JsePlatform.standardGlobals().load("return " + source).call().checktable();
    }
}
