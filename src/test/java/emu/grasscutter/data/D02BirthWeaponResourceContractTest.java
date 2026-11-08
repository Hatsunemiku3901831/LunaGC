package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.weapon.WeaponCurveData;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks only the source-pinned, native-derived birth weapon fragments. */
class D02BirthWeaponResourceContractTest {
    private static JsonObject equipment;
    private static JsonObject promote;
    private static JsonArray upstreamWeapons;

    @BeforeAll
    static void loadPinnedInputs() throws Exception {
        equipment = JsonUtils.loadToClass(pinnedPath(
                "d02.weapon.equipment",
                "50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc"),
                JsonObject.class);
        promote = JsonUtils.loadToClass(pinnedPath(
                "d02.weapon.promote",
                "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1"),
                JsonObject.class);
        upstreamWeapons = JsonUtils.loadToClass(pinnedPath(
                "d02.weapon.upstream",
                "7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24"),
                JsonArray.class);
    }

    @Test
    void birthWeaponPreservesBothNativeProperties() {
        JsonObject fragment = equipmentFragment("Weapon", 11101);
        assertTrue(fragment.get("fragmentOnly").getAsBoolean());
        JsonObject fields = fragment.getAsJsonObject("javaFields");
        ItemData item = JsonUtils.decode(fields, ItemData.class);
        item.onLoad();

        assertEquals(11101, item.getId());
        assertEquals(fields.get("itemType").getAsString(), item.getItemType().name());
        assertEquals(11101, item.getWeaponPromoteId());
        JsonArray source = fields.getAsJsonArray("weaponProp");
        assertEquals(2, source.size());
        assertEquals(source.size(), item.getWeaponProperties().length);
        for (int i = 0; i < source.size(); i++) {
            JsonObject expected = source.get(i).getAsJsonObject();
            ItemData.WeaponProperty actual = item.getWeaponProperties()[i];
            assertEquals(expected.get("propType").getAsString(), actual.getPropType().name());
            assertEquals(expected.get("type").getAsString(), actual.getType());
            assertFloatBits(expected.get("initValue").getAsFloat(), actual.getInitValue());
        }
        assertEquals(FightProperty.FIGHT_PROP_BASE_ATTACK, item.getWeaponProperties()[0].getPropType());
        assertEquals(4, item.getWeaponProperties()[0].getPropType().getId());
        assertEquals(FightProperty.FIGHT_PROP_NONE, item.getWeaponProperties()[1].getPropType());
        assertEquals(0, item.getWeaponProperties()[1].getPropType().getId());
        assertFloatBits(0f, item.getWeaponProperties()[1].getInitValue());
    }

    @Test
    void everyWeaponCurveLevelAndEntrySurvivesRealLoading() {
        Map<Integer, WeaponCurveData> byLevel = new HashMap<>();
        int curveRows = 0;
        for (JsonElement element : equipment.getAsJsonArray("javaProjectionFragments")) {
            JsonObject fragment = element.getAsJsonObject();
            if (!"WeaponCurve".equals(fragment.get("table").getAsString())) continue;
            curveRows++;
            JsonObject fields = fragment.getAsJsonObject("javaFields");
            WeaponCurveData curve = JsonUtils.decode(fields, WeaponCurveData.class);
            curve.onLoad();
            int level = fragment.get("key").getAsInt();
            assertEquals(level, curve.getId());
            assertEquals(level, fields.get("level").getAsInt());
            assertNull(byLevel.put(level, curve), "duplicate curve level " + level);

            JsonArray expected = fields.getAsJsonArray("curveInfos");
            JsonArray actual = JsonUtils.toJson(curve).getAsJsonObject().getAsJsonArray("curveInfos");
            assertEquals(expected.size(), actual.size());
            Set<String> types = new HashSet<>();
            for (int i = 0; i < expected.size(); i++) {
                JsonObject before = expected.get(i).getAsJsonObject();
                JsonObject after = actual.get(i).getAsJsonObject();
                String type = before.get("type").getAsString();
                assertTrue(types.add(type), "duplicate curve type at level " + level);
                assertEquals(type, after.get("type").getAsString());
                assertEquals(before.get("arith").getAsString(), after.get("arith").getAsString());
                assertEquals("ARITH_MULTI", after.get("arith").getAsString());
                float value = before.get("value").getAsFloat();
                assertFloatBits(value, after.get("value").getAsFloat());
                assertFloatBits(value, curve.getMultByProp(type));
            }
        }
        assertEquals(100, curveRows);
        assertEquals(100, byLevel.size());
        for (int level = 1; level <= 100; level++) assertTrue(byLevel.containsKey(level));

        ItemData item = JsonUtils.decode(equipmentFragment("Weapon", 11101).get("javaFields"), ItemData.class);
        item.onLoad();
        int observedPhaseZeroCases = 0;
        for (JsonElement element : promote.getAsJsonObject("consumers").getAsJsonArray("weaponStats")) {
            JsonObject nativeCase = element.getAsJsonObject();
            if (nativeCase.get("phase").getAsInt() != 0) continue;
            observedPhaseZeroCases++;
            int index = nativeCase.get("index").getAsInt();
            ItemData.WeaponProperty property = item.getWeaponProperties()[index];
            WeaponCurveData curve = byLevel.get(nativeCase.get("level").getAsInt());
            float factor = curve.getMultByProp(property.getType());
            assertFloatBits(nativeCase.get("factor").getAsFloat(), factor);
            assertEquals(nativeCase.get("fightProperty").getAsInt(), property.getPropType().getId());
            assertFloatBits(nativeCase.get("promoteValue").getAsFloat(), 0f);
            float result = property.getInitValue() * factor;
            assertEquals(parseBits(nativeCase.get("resultBits").getAsString()), Float.floatToRawIntBits(result));
            assertEquals(parseBits(nativeCase.get("independentOracleBits").getAsString()), Float.floatToRawIntBits(result));
        }
        assertEquals(3, observedPhaseZeroCases);
    }

    @Test
    void phaseZeroInputListsAndOnLoadFilteringMatchTheNativeFragment() {
        JsonObject fragment = birthPromoteFragment();
        JsonObject fields = fragment.getAsJsonObject("javaFields");
        WeaponPromoteData row = JsonUtils.decode(fields, WeaponPromoteData.class);
        assertEquals(11101, row.getWeaponPromoteId());
        assertEquals(0, row.getPromoteLevel());
        assertEquals(fragment.get("key").getAsInt(), row.getId());

        JsonArray properties = fields.getAsJsonArray("addProps");
        JsonArray costs = fields.getAsJsonArray("costItems");
        assertEquals(5, properties.size());
        assertEquals(3, costs.size());
        assertEquals(properties.size(), row.getAddProps().length);
        assertEquals(costs.size(), row.getCostItems().length);
        for (int i = 0; i < properties.size(); i++) {
            JsonObject expected = properties.get(i).getAsJsonObject();
            assertEquals(expected.get("propType").getAsString(), row.getAddProps()[i].getPropType());
            assertFloatBits(expected.get("value").getAsFloat(), row.getAddProps()[i].getValue());
            assertFloatBits(0f, row.getAddProps()[i].getValue());
        }
        for (int i = 0; i < costs.size(); i++) {
            JsonObject expected = costs.get(i).getAsJsonObject();
            assertEquals(expected.get("id").getAsInt(), row.getCostItems()[i].getId());
            assertEquals(expected.get("count").getAsInt(), row.getCostItems()[i].getCount());
        }
        row.onLoad();
        JsonObject model = fragment.getAsJsonObject("onLoadSourceModel");
        assertEquals(model.getAsJsonArray("addProps").size(), row.getAddProps().length);
        assertEquals(model.getAsJsonArray("costItems").size(), row.getCostItems().length);
        assertEquals(0, row.getAddProps().length);
        assertEquals(0, row.getCostItems().length);
    }

    @Test
    void completeUpstreamWeaponIsAnIndependentMissingFieldControl() {
        JsonObject source = null;
        for (JsonElement element : upstreamWeapons) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.get("id").getAsInt() == 11101) {
                assertNull(source, "duplicate upstream weapon id");
                source = candidate;
            }
        }
        assertNotNull(source);
        JsonArray properties = source.getAsJsonArray("weaponProp");
        assertEquals(2, properties.size());
        assertFalse(properties.get(1).getAsJsonObject().has("propType"));
        assertFalse(properties.get(1).getAsJsonObject().has("initValue"));
        ItemData upstream = JsonUtils.decode(source, ItemData.class);
        assertNull(upstream.getWeaponProperties()[1].getPropType());
        assertFloatBits(0f, upstream.getWeaponProperties()[1].getInitValue());
        upstream.onLoad();
        assertEquals(1, upstream.getWeaponProperties().length);
        assertEquals(FightProperty.FIGHT_PROP_BASE_ATTACK, upstream.getWeaponProperties()[0].getPropType());
    }

    private static Path pinnedPath(String property, String expectedSha256) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        Path path = Path.of(value);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        assertEquals(expectedSha256, actual, "input SHA-256 for " + property);
        return path;
    }

    private static JsonObject equipmentFragment(String table, int key) {
        JsonObject match = null;
        for (JsonElement element : equipment.getAsJsonArray("javaProjectionFragments")) {
            JsonObject candidate = element.getAsJsonObject();
            if (table.equals(candidate.get("table").getAsString()) && candidate.get("key").getAsInt() == key) {
                assertNull(match, "duplicate equipment fragment " + table + "/" + key);
                match = candidate;
            }
        }
        assertNotNull(match, "missing equipment fragment " + table + "/" + key);
        return match;
    }

    private static JsonObject birthPromoteFragment() {
        JsonObject match = null;
        for (JsonElement element : promote.getAsJsonObject("javaProjection").getAsJsonArray("completeBirthPromoteInputRows")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("WeaponPromote".equals(candidate.get("table").getAsString())) {
                assertNull(match, "duplicate birth weapon promote fragment");
                match = candidate;
            }
        }
        assertNotNull(match, "missing birth weapon promote fragment");
        return match;
    }

    private static void assertFloatBits(float expected, float actual) {
        assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
    }

    private static int parseBits(String bits) {
        return Integer.parseUnsignedInt(bits, 16);
    }
}
