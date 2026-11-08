package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarCurveData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.props.FightProperty;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real loading of approved HP/growth fragments; no full Avatar or default-field permission. */
class D02BirthAvatarResourceContractTest {
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
        // ResourceLoaderIsolationTest follows startup's logger-before-FileUtils order.
        Grasscutter.getLogger();
        avatarEvidence = pinnedReport(
                "d02.avatar.resource",
                "32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e");
        promoteEvidence = pinnedReport(
                "d02.avatar.promote",
                "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertFalse(avatarEvidence.get("completeAvatarInputRow").getAsBoolean());
        assertFalse(avatarEvidence.get("completeItemInputRow").getAsBoolean());
        assertFalse(avatarEvidence.get("resourceReady").getAsBoolean());
        assertFalse(avatarEvidence.get("javaResourcesLoaded").getAsBoolean());
        assertFalse(avatarEvidence.get("d02Complete").getAsBoolean());
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
        // AvatarData.onLoad calls the lazy getter; preserve the original cache
        // reference rather than accidentally populating an existing empty map.
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
    void everyDeclaredCurveRowSurvivesActualJsonAndOnLoad() throws Exception {
        JsonArray inputs = curveInputs();
        loadCurves(inputs, "curves.json");
        assertEquals(100, GameData.getAvatarCurveDataMap().size());
        Set<Integer> levels = new HashSet<>();
        for (JsonElement element : inputs) {
            JsonObject expected = element.getAsJsonObject();
            int level = expected.get("level").getAsInt();
            assertTrue(levels.add(level));
            AvatarCurveData actual = GameData.getAvatarCurveDataMap().get(level);
            assertNotNull(actual, "missing actual Curve row " + level);
            assertEquals(level, actual.getId());
            JsonArray entries = expected.getAsJsonArray("curveInfos");
            JsonArray retained = JsonUtils.toJson(actual).getAsJsonObject().getAsJsonArray("curveInfos");
            assertEquals(4, entries.size());
            assertEquals(entries.size(), retained.size());
            assertEquals(entries.size(), actual.getCurveInfos().size());
            Set<String> types = new HashSet<>();
            for (int index = 0; index < entries.size(); index++) {
                JsonObject before = entries.get(index).getAsJsonObject();
                JsonObject after = retained.get(index).getAsJsonObject();
                String type = before.get("type").getAsString();
                assertTrue(types.add(type));
                assertEquals(type, after.get("type").getAsString());
                assertEquals("ARITH_MULTI", before.get("arith").getAsString());
                assertEquals(before.get("arith").getAsString(), after.get("arith").getAsString());
                assertFloatBits(before.get("value").getAsFloat(), after.get("value").getAsFloat());
                assertFloatBits(before.get("value").getAsFloat(), actual.getCurveInfos().get(type));
            }
        }
        for (int level = 1; level <= 100; level++) assertTrue(levels.contains(level));
        assertTrue(GameData.getAvatarDataMap().isEmpty());
    }

    @Test
    void birthHpAttackDefenseAndGrowthMatchTheEighteenActualNativeCases() throws Exception {
        loadCurves(curveInputs(), "curves.json");
        JsonArray inputs = avatarInputs();
        loadAvatars(inputs, "avatars.json");
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        int comparedCases = 0;
        Set<String> comparedKeys = new HashSet<>();
        for (JsonElement element : avatarEvidence.getAsJsonObject("consumers").getAsJsonArray("completeHelper")) {
            JsonObject nativeCase = element.getAsJsonObject();
            int avatarId = nativeCase.get("avatarId").getAsInt();
            if (avatarId != 10000005 && avatarId != 10000007) continue;
            int level = nativeCase.get("level").getAsInt();
            int property = nativeCase.get("property").getAsInt();
            assertTrue(Set.of(1, 20, 100).contains(level));
            assertTrue(comparedKeys.add(avatarId + "/" + level + "/" + property));
            assertTrue(nativeCase.get("completeNativeFunction").getAsBoolean());
            AvatarData row = GameData.getAvatarDataMap().get(avatarId);
            assertNotNull(row);
            float actual = switch (property) {
                case 1 -> row.getBaseHp(level);
                case 4 -> row.getBaseAttack(level);
                case 7 -> row.getBaseDefense(level);
                default -> throw new AssertionError("unapproved base property " + property);
            };
            assertEquals(parseBits(nativeCase.get("resultBits").getAsString()), Float.floatToRawIntBits(actual));
            assertEquals(parseBits(nativeCase.get("independentOracleBits").getAsString()), Float.floatToRawIntBits(actual));
            float factor = row.getGrowthCurveById(level, FightProperty.getPropById(property));
            assertEquals(parseBits(nativeCase.get("factorBits").getAsString()), Float.floatToRawIntBits(factor));
            comparedCases++;
        }
        assertEquals(18, comparedCases);
        // Critical, icon and ability fields are absent inputs with no target
        // default permission. This test never constructs a runtime Avatar.
        for (JsonElement element : inputs) {
            JsonObject fields = element.getAsJsonObject();
            assertFalse(fields.has("critical"));
            assertFalse(fields.has("criticalHurt"));
            assertFalse(fields.has("iconName"));
        }
    }

    @Test
    void missingGrowthBindingRejectsOnlyItsRowThroughTheRealLoader() throws Exception {
        loadCurves(curveInputs(), "curves.json");
        JsonArray inputs = avatarInputs();
        inputs.get(0).getAsJsonObject().remove("propGrowCurves");
        var appender = capture();
        try {
            loadAvatars(inputs, "missing-binding.json");
            assertEquals(Set.of(10000007), GameData.getAvatarDataMap().keySet());
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("AvatarData rejected 1 entries")
                            && event.getFormattedMessage().contains("missing-binding.json")
                            && event.getFormattedMessage().contains("row 1")));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    @Test
    void missingMiddleCurveDoesNotBecomeAValidAvatarByGetterFallback() throws Exception {
        JsonArray inputs = curveInputs();
        inputs.remove(49); // Remove level50, retain level100: current onLoad allocates size99.
        loadCurves(inputs, "missing-level.json");
        assertEquals(99, GameData.getAvatarCurveDataMap().size());
        assertFalse(GameData.getAvatarCurveDataMap().containsKey(50));
        assertTrue(GameData.getAvatarCurveDataMap().containsKey(100));
        var appender = capture();
        try {
            loadAvatars(avatarInputs(), "incomplete-curves-avatars.json");
            assertTrue(GameData.getAvatarDataMap().isEmpty());
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("AvatarData rejected 2 entries")
                            && event.getFormattedMessage().contains("incomplete-curves-avatars.json")
                            && event.getFormattedMessage().contains("ArrayIndexOutOfBoundsException")));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    private void loadCurves(JsonArray inputs, String name) throws Exception {
        Path path = Files.writeString(directory.resolve(name), inputs.toString());
        ResourceLoader.loadFromResource(AvatarCurveData.class, path, GameData.getAvatarCurveDataMap());
    }

    private void loadAvatars(JsonArray inputs, String name) throws Exception {
        Path path = Files.writeString(directory.resolve(name), inputs.toString());
        ResourceLoader.loadFromResource(AvatarData.class, path, GameData.getAvatarDataMap());
    }

    private static JsonArray curveInputs() {
        JsonArray rows = new JsonArray();
        for (JsonElement element : promoteEvidence.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows")) {
            JsonObject input = element.getAsJsonObject();
            assertEquals("AvatarCurve", input.get("table").getAsString());
            assertTrue(input.get("completeDeclaredJavaInputRow").getAsBoolean());
            rows.add(input.getAsJsonObject("javaFields").deepCopy());
        }
        assertEquals(100, rows.size());
        return rows;
    }

    private static JsonArray avatarInputs() {
        Map<Integer, JsonObject> byId = new HashMap<>();
        for (JsonElement element : avatarEvidence.getAsJsonArray("javaProjectionFragments")) {
            JsonObject input = element.getAsJsonObject();
            assertEquals("Avatar", input.get("table").getAsString());
            assertTrue(input.get("fragmentOnly").getAsBoolean());
            assertFalse(input.get("fullDeclaredJavaInputRow").getAsBoolean());
            assertFalse(input.get("criticalAliasesApproved").getAsBoolean());
            JsonObject fields = input.getAsJsonObject("javaFields").deepCopy();
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves"), fields.keySet());
            assertNull(byId.put(fields.get("id").getAsInt(), fields));
        }
        assertEquals(Set.of(10000005, 10000007), byId.keySet());
        JsonArray rows = new JsonArray();
        rows.add(byId.get(10000005));
        rows.add(byId.get(10000007));
        return rows;
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

    private static ListAppender<ILoggingEvent> capture() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Grasscutter.getLogger().addAppender(appender);
        return appender;
    }

    private static void assertFloatBits(float expected, float actual) {
        assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
    }

    private static int parseBits(String bits) {
        return Integer.parseUnsignedInt(bits, 16);
    }
}
