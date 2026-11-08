package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarCurveData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real loading of approved base critical fragments, not a complete Avatar contract. */
class D02BirthAvatarCriticalResourceContractTest {
    private static JsonObject criticalEvidence;
    private static JsonObject avatarEvidence;
    private static JsonObject promoteEvidence;
    private static Field fettersCache;

    @TempDir Path directory;
    private Int2ObjectMap<AvatarCurveData> previousCurves;
    private Int2ObjectMap<AvatarData> previousAvatars;
    private Map<Integer, List<Integer>> previousFetters;
    private boolean previousLoadedAll;

    @BeforeAll
    static void loadPinnedInputs() throws Exception {
        Grasscutter.getLogger();
        criticalEvidence = pinnedReport(
                "d02.critical.installer",
                "713bc5816eb2e9cca7dc23167e2b14ecf5e890d36286a135c66d1fa23cf0d8a1");
        avatarEvidence = pinnedReport(
                "d02.critical.avatar",
                "32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e");
        promoteEvidence = pinnedReport(
                "d02.critical.promote",
                "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertFalse(criticalEvidence.get("completeAvatarInputRow").getAsBoolean());
        assertFalse(criticalEvidence.get("completeAvatarNaturalPath").getAsBoolean());
        assertFalse(criticalEvidence.get("modifierBranchExecuted").getAsBoolean());
        assertFalse(criticalEvidence.get("targetDefaultApproved").getAsBoolean());
        assertFalse(criticalEvidence.get("javaResourcesLoaded").getAsBoolean());
        assertFalse(criticalEvidence.get("resourceReady").getAsBoolean());
        assertFalse(criticalEvidence.get("d02Complete").getAsBoolean());
        fettersCache = GameData.class.getDeclaredField("fetters");
        fettersCache.setAccessible(true);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void isolateGameData() throws Exception {
        previousCurves = new Int2ObjectLinkedOpenHashMap<>(GameData.getAvatarCurveDataMap());
        previousAvatars = new Int2ObjectOpenHashMap<>(GameData.getAvatarDataMap());
        previousFetters = (Map<Integer, List<Integer>>) fettersCache.get(null);
        previousLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getAvatarCurveDataMap().clear();
        GameData.getAvatarDataMap().clear();
        fettersCache.set(null, new HashMap<Integer, List<Integer>>());
    }

    @AfterEach
    void restoreGameData() throws Exception {
        GameData.getAvatarCurveDataMap().clear();
        GameData.getAvatarCurveDataMap().putAll(previousCurves);
        GameData.getAvatarDataMap().clear();
        GameData.getAvatarDataMap().putAll(previousAvatars);
        fettersCache.set(null, previousFetters);
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void birthFragmentsLoadAndBaseGettersMatchNativeReadbackBits() throws Exception {
        loadCurves();
        loadAvatars(approvedRows(), "AvatarExcelConfigData.json");
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        for (AvatarData row : GameData.getAvatarDataMap().values()) {
            assertSame(row, GameData.getAvatarDataMap().get(row.getId()));
            JsonObject nativeCase = nativeCase("original-" + row.getId());
            assertEquals(row.getId(), nativeCase.get("avatarId").getAsInt());
            assertReadback(row, nativeCase);
        }
    }

    @Test
    void swappedBirthFieldsFollowTheirRespectiveJavaGetters() throws Exception {
        loadCurves();
        JsonArray rows = approvedRows();
        JsonObject first = rows.get(0).getAsJsonObject();
        assertEquals(10000005, first.get("id").getAsInt());
        JsonElement critical = first.get("critical").deepCopy();
        first.add("critical", first.get("criticalHurt").deepCopy());
        first.add("criticalHurt", critical);
        loadAvatars(rows, "swapped.json");
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        AvatarData row = GameData.getAvatarDataMap().get(10000005);
        assertReadback(row, nativeCase("birth5-swapped"));
        assertReadback(GameData.getAvatarDataMap().get(10000007), nativeCase("original-10000007"));
    }

    @Test
    void missingCriticalFieldsAreZeroOnlyAsCurrentJavaControl() throws Exception {
        loadCurves();
        JsonArray rows = approvedRows();
        for (JsonElement element : rows) {
            element.getAsJsonObject().remove("critical");
            element.getAsJsonObject().remove("criticalHurt");
        }
        loadAvatars(rows, "missing-critical.json");
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        for (AvatarData row : GameData.getAvatarDataMap().values()) {
            assertEquals(0, Float.floatToRawIntBits(row.getBaseCritical()));
            assertEquals(0, Float.floatToRawIntBits(row.getBaseCriticalHurt()));
        }
    }

    private void loadCurves() throws Exception {
        JsonArray rows = new JsonArray();
        for (JsonElement element : promoteEvidence.getAsJsonObject("javaProjection")
                .getAsJsonArray("completeAvatarCurveInputRows")) {
            JsonObject fragment = element.getAsJsonObject();
            assertEquals("AvatarCurve", fragment.get("table").getAsString());
            assertTrue(fragment.get("completeDeclaredJavaInputRow").getAsBoolean());
            rows.add(fragment.getAsJsonObject("javaFields").deepCopy());
        }
        assertEquals(100, rows.size());
        Path path = Files.writeString(directory.resolve("curves.json"), rows.toString());
        ResourceLoader.loadFromResource(AvatarCurveData.class, path, GameData.getAvatarCurveDataMap());
        assertEquals(100, GameData.getAvatarCurveDataMap().size());
    }

    private void loadAvatars(JsonArray rows, String name) throws Exception {
        Path path = Files.writeString(directory.resolve(name), rows.toString());
        ResourceLoader.loadFromResource(AvatarData.class, path, GameData.getAvatarDataMap());
    }

    private static JsonArray approvedRows() {
        Map<Integer, JsonObject> criticalById = new HashMap<>();
        for (JsonElement element : criticalEvidence.getAsJsonArray("javaProjectionFragments")) {
            JsonObject fragment = element.getAsJsonObject();
            assertTrue(fragment.get("fragmentOnly").getAsBoolean());
            assertFalse(fragment.get("fullDeclaredJavaInputRow").getAsBoolean());
            assertFalse(fragment.get("naturalAvatarConsumerExecuted").getAsBoolean());
            JsonObject fields = fragment.getAsJsonObject("javaFields");
            assertEquals(Set.of("id", "critical", "criticalHurt"), fields.keySet());
            assertNull(criticalById.put(fields.get("id").getAsInt(), fragment));
        }
        assertEquals(Set.of(10000005, 10000007), criticalById.keySet());

        Map<Integer, JsonObject> rowsById = new HashMap<>();
        for (JsonElement element : avatarEvidence.getAsJsonArray("javaProjectionFragments")) {
            JsonObject fragment = element.getAsJsonObject();
            assertEquals("Avatar", fragment.get("table").getAsString());
            assertTrue(fragment.get("fragmentOnly").getAsBoolean());
            JsonObject fields = fragment.getAsJsonObject("javaFields").deepCopy();
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves"), fields.keySet());
            int id = fields.get("id").getAsInt();
            assertEquals(id, fragment.get("key").getAsInt());
            JsonObject critical = criticalById.get(id);
            assertNotNull(critical);
            JsonArray metadata = critical.getAsJsonArray("fields");
            assertEquals(2, metadata.size());
            for (int index = 0; index < metadata.size(); index++) {
                JsonObject field = metadata.get(index).getAsJsonObject();
                assertEquals(fragment.getAsJsonObject("source"), field.getAsJsonObject("source"));
                String name = field.get("javaField").getAsString();
                assertEquals(index == 0 ? "critical" : "criticalHurt", name);
                assertEquals(index == 0 ? 20 : 22, field.get("targetProperty").getAsInt());
                assertEquals(index == 0 ? "3d4ccccd" : "3f000000", field.get("bits").getAsString());
                JsonElement value = critical.getAsJsonObject("javaFields").get(name);
                assertEquals(parseBits(field.get("bits").getAsString()), Float.floatToRawIntBits(value.getAsFloat()));
                fields.add(name, value.deepCopy());
            }
            assertNull(rowsById.put(id, fields));
        }
        assertEquals(Set.of(10000005, 10000007), rowsById.keySet());
        JsonArray rows = new JsonArray();
        rows.add(rowsById.get(10000005));
        rows.add(rowsById.get(10000007));
        return rows;
    }

    private static JsonObject nativeCase(String name) {
        JsonObject match = null;
        for (JsonElement element : criticalEvidence.getAsJsonArray("cases")) {
            JsonObject candidate = element.getAsJsonObject();
            if (name.equals(candidate.get("name").getAsString())) {
                assertNull(match, "duplicate native case " + name);
                match = candidate;
            }
        }
        assertNotNull(match, "missing native case " + name);
        return match;
    }

    private static void assertReadback(AvatarData row, JsonObject nativeCase) {
        assertNotNull(row);
        JsonArray readbacks = nativeCase.getAsJsonArray("namedReadback");
        assertEquals(2, readbacks.size());
        JsonObject critical = readbacks.get(0).getAsJsonObject();
        JsonObject hurt = readbacks.get(1).getAsJsonObject();
        assertEquals(20, critical.get("property").getAsInt());
        assertEquals(22, hurt.get("property").getAsInt());
        assertEquals(parseBits(critical.get("bits").getAsString()), Float.floatToRawIntBits(row.getBaseCritical()));
        assertEquals(parseBits(hurt.get("bits").getAsString()), Float.floatToRawIntBits(row.getBaseCriticalHurt()));
    }

    private static JsonObject pinnedReport(String property, String expectedSha256) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        byte[] raw = Files.readAllBytes(Path.of(value));
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        assertEquals(expectedSha256, actual, "input SHA-256 for " + property);
        return JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static int parseBits(String bits) {
        return Integer.parseUnsignedInt(bits, 16);
    }
}
