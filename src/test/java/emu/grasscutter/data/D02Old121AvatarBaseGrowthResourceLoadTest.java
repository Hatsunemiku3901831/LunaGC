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
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
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

/** The 121 approved base/growth fragments only; no runtime Avatar construction. */
class D02Old121AvatarBaseGrowthResourceLoadTest {
    private static JsonObject projection;
    private static JsonObject baseEvidence;
    private static JsonObject promoteEvidence;
    private static Field fetters;
    @TempDir Path directory;
    private Int2ObjectMap<AvatarData> oldAvatars;
    private Int2ObjectMap<AvatarCurveData> oldCurves;
    private Int2ObjectMap<AvatarSkillDepotData> oldDepots;
    private Map<Integer, List<Integer>> oldFetters;
    private boolean oldLoadedAll;

    @BeforeAll
    static void loadEvidence() throws Exception {
        Grasscutter.getLogger();
        projection = readPinned("d02.old121.projection", required("d02.old121.projection.sha256"));
        baseEvidence = readPinned("d02.old121.base", "32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e");
        promoteEvidence = readPinned("d02.old121.promote", "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertTrue(projection.get("complete").getAsBoolean());
        assertFalse(projection.get("resourceReady").getAsBoolean());
        assertFalse(baseEvidence.get("resourceReady").getAsBoolean());
        fetters = GameData.class.getDeclaredField("fetters");
        fetters.setAccessible(true);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void isolate() throws Exception {
        oldAvatars = new Int2ObjectOpenHashMap<>(GameData.getAvatarDataMap());
        oldCurves = new Int2ObjectLinkedOpenHashMap<>(GameData.getAvatarCurveDataMap());
        oldDepots = new Int2ObjectOpenHashMap<>(GameData.getAvatarSkillDepotDataMap());
        oldFetters = (Map<Integer, List<Integer>>) fetters.get(null);
        oldLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getAvatarDataMap().clear();
        GameData.getAvatarCurveDataMap().clear();
        GameData.getAvatarSkillDepotDataMap().clear();
        fetters.set(null, new HashMap<Integer, List<Integer>>());
    }

    @AfterEach
    void restore() throws Exception {
        GameData.getAvatarDataMap().clear(); GameData.getAvatarDataMap().putAll(oldAvatars);
        GameData.getAvatarCurveDataMap().clear(); GameData.getAvatarCurveDataMap().putAll(oldCurves);
        GameData.getAvatarSkillDepotDataMap().clear(); GameData.getAvatarSkillDepotDataMap().putAll(oldDepots);
        fetters.set(null, oldFetters);
        assertSame(oldFetters, fetters.get(null));
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void loadFragmentsAndCompareEveryLevel100NativeResult() throws Exception {
        JsonArray curves = new JsonArray();
        for (JsonElement element : promoteEvidence.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("completeDeclaredJavaInputRow").getAsBoolean());
            curves.add(row.getAsJsonObject("javaFields").deepCopy());
        }
        assertEquals(100, curves.size());
        JsonArray avatars = new JsonArray();
        Map<Integer, JsonObject> fragments = new HashMap<>();
        for (JsonElement element : projection.getAsJsonArray("fragments")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            assertFalse(row.get("fullDeclaredJavaInputRow").getAsBoolean());
            JsonObject fields = row.getAsJsonObject("javaFields").deepCopy();
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves"), fields.keySet());
            int id = fields.get("id").getAsInt();
            assertEquals(id, row.get("key").getAsInt());
            assertNull(fragments.put(id, row));
            avatars.add(fields);
        }
        assertEquals(121, fragments.size());
        assertTrue(fragments.containsKey(10000007));
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Grasscutter.getLogger().addAppender(appender);
        try {
            ResourceLoader.loadFromResource(AvatarCurveData.class, write("curves.json", curves), GameData.getAvatarCurveDataMap());
            ResourceLoader.loadFromResource(AvatarData.class, write("avatars.json", avatars), GameData.getAvatarDataMap());
            assertEquals(100, GameData.getAvatarCurveDataMap().size());
            assertEquals(fragments.keySet(), GameData.getAvatarDataMap().keySet());
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("rejected")),
                    () -> "ResourceLoader rejection logs: " + appender.list);
            for (var entry : fragments.entrySet()) {
                AvatarData actual = GameData.getAvatarDataMap().get(entry.getKey());
                JsonObject expected = entry.getValue();
                JsonObject loaded = JsonUtils.toJson(actual).getAsJsonObject();
                for (String name : List.of("hpBase", "attackBase", "defenseBase")) {
                    assertEquals(bits(expected.getAsJsonObject("floatBits").get(name).getAsString()),
                            Float.floatToRawIntBits(loaded.get(name).getAsFloat()));
                }
                assertEquals(expected.getAsJsonObject("javaFields").getAsJsonArray("propGrowCurves"),
                        loaded.getAsJsonArray("propGrowCurves"));
            }
            Set<String> cases = new HashSet<>();
            for (JsonElement element : projection.getAsJsonArray("nativeOracle")) {
                JsonObject c = element.getAsJsonObject();
                int id = c.get("avatarId").getAsInt();
                int property = c.get("property").getAsInt();
                assertTrue(fragments.containsKey(id));
                assertEquals(100, c.get("level").getAsInt());
                assertTrue(cases.add(id + "/" + property));
                AvatarData row = GameData.getAvatarDataMap().get(id);
                float actual = switch (property) {
                    case 1 -> row.getBaseHp(100);
                    case 4 -> row.getBaseAttack(100);
                    case 7 -> row.getBaseDefense(100);
                    default -> throw new AssertionError("unapproved property " + property);
                };
                assertEquals(bits(c.get("resultBits").getAsString()), Float.floatToRawIntBits(actual));
                assertEquals(bits(c.get("factorBits").getAsString()), Float.floatToRawIntBits(
                        row.getGrowthCurveById(100, FightProperty.getPropById(property))));
            }
            assertEquals(363, cases.size());
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    private Path write(String name, JsonArray rows) throws Exception {
        return Files.writeString(directory.resolve(name), rows.toString());
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        return value;
    }

    private static JsonObject readPinned(String property, String expected) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(required(property)));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return JsonUtils.decode(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static int bits(String value) { return Integer.parseUnsignedInt(value, 16); }
}
