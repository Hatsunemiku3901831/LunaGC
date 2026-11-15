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

/** Limited seven-field resource fragments and original named-property readbacks. */
class D02Old121AvatarCriticalResourceLoadTest {
    private static JsonObject critical;
    private static JsonObject base;
    private static JsonObject promote;
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
        critical = readPinned("d02.old121.critical", "961ff8f670175f55d9d775a17e906a9d434029fda88b4902558bc0f7ba8969af");
        base = readPinned("d02.old121.projection", "db4a4e6774de9a18e1ff88a515b905a48735bb5c6b5cec5c0a095766fd743bcc");
        promote = readPinned("d02.old121.promote", "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertTrue(critical.get("complete").getAsBoolean());
        assertTrue(base.get("complete").getAsBoolean());
        assertFalse(critical.get("resourceReady").getAsBoolean());
        assertFalse(base.get("resourceReady").getAsBoolean());
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
    void loadFragmentsAndCompareOriginalNamedReadbacks() throws Exception {
        JsonArray curves = new JsonArray();
        for (JsonElement element : promote.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("completeDeclaredJavaInputRow").getAsBoolean());
            curves.add(row.getAsJsonObject("javaFields").deepCopy());
        }
        assertEquals(100, curves.size());
        Map<Integer, JsonObject> baseRows = new HashMap<>();
        for (JsonElement element : base.getAsJsonArray("fragments")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            assertFalse(row.get("fullDeclaredJavaInputRow").getAsBoolean());
            int id = row.get("key").getAsInt();
            assertNull(baseRows.put(id, row));
        }
        Map<Integer, JsonObject> criticalRows = new HashMap<>();
        for (JsonElement element : critical.getAsJsonArray("fragments")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            assertFalse(row.get("fullDeclaredJavaInputRow").getAsBoolean());
            int id = row.get("key").getAsInt();
            assertNull(criticalRows.put(id, row));
        }
        assertEquals(121, baseRows.size());
        assertEquals(baseRows.keySet(), criticalRows.keySet());
        assertTrue(baseRows.containsKey(10000007));
        Map<String, String> reads = new HashMap<>();
        for (JsonElement element : critical.getAsJsonArray("namedReadback")) {
            JsonObject read = element.getAsJsonObject();
            assertNull(reads.put(read.get("avatarId").getAsInt() + "/" + read.get("property").getAsInt(), read.get("bits").getAsString()));
        }
        assertEquals(240, reads.size());
        for (JsonElement element : critical.getAsJsonArray("reused7NamedReadback")) {
            JsonObject read = element.getAsJsonObject();
            assertNull(reads.put("10000007/" + read.get("property").getAsInt(), read.get("bits").getAsString()));
        }
        assertEquals(242, reads.size());
        JsonArray avatars = new JsonArray();
        for (var entry : baseRows.entrySet()) {
            int id = entry.getKey();
            JsonObject b = entry.getValue();
            JsonObject c = criticalRows.get(id);
            JsonObject source = base.getAsJsonObject("selectedSources").getAsJsonObject(Integer.toString(id));
            assertEquals(source, b.getAsJsonObject("source"));
            assertEquals(source, c.getAsJsonObject("source"));
            assertEquals("466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9", critical.get("payloadSHA256").getAsString());
            JsonObject fields = b.getAsJsonObject("javaFields").deepCopy();
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves"), fields.keySet());
            JsonObject extra = c.getAsJsonObject("javaFields");
            assertEquals(Set.of("id", "critical", "criticalHurt"), extra.keySet());
            assertEquals(id, fields.get("id").getAsInt());
            assertEquals(id, extra.get("id").getAsInt());
            for (JsonElement element : c.getAsJsonArray("fields")) {
                JsonObject proof = element.getAsJsonObject();
                assertEquals(source, proof.getAsJsonObject("source"));
                int property = proof.get("targetProperty").getAsInt();
                String name = proof.get("javaField").getAsString();
                assertTrue((property == 20 && name.equals("critical")) || (property == 22 && name.equals("criticalHurt")));
                assertEquals(reads.get(id + "/" + property), proof.get("bits").getAsString());
                assertEquals(bits(proof.get("bits").getAsString()), Float.floatToRawIntBits(extra.get(name).getAsFloat()));
            }
            assertEquals(2, c.getAsJsonArray("fields").size());
            fields.add("critical", extra.get("critical"));
            fields.add("criticalHurt", extra.get("criticalHurt"));
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves", "critical", "criticalHurt"), fields.keySet());
            avatars.add(fields);
        }
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Grasscutter.getLogger().addAppender(appender);
        try {
            ResourceLoader.loadFromResource(AvatarCurveData.class, write("curves.json", curves), GameData.getAvatarCurveDataMap());
            ResourceLoader.loadFromResource(AvatarData.class, write("avatars.json", avatars), GameData.getAvatarDataMap());
            assertEquals(100, GameData.getAvatarCurveDataMap().size());
            assertEquals(baseRows.keySet(), GameData.getAvatarDataMap().keySet());
            var rejected = appender.list.stream().filter(e -> e.getFormattedMessage().contains("rejected")).toList();
            assertTrue(rejected.isEmpty(), () -> "ResourceLoader rejection logs: " + rejected);
            for (int id : baseRows.keySet()) {
                AvatarData row = GameData.getAvatarDataMap().get(id);
                assertNotNull(row);
                assertEquals(id, row.getId());
                assertSame(row, GameData.getAvatarDataMap().get(id));
                assertEquals(bits(reads.get(id + "/20")), Float.floatToRawIntBits(row.getBaseCritical()));
                assertEquals(bits(reads.get(id + "/22")), Float.floatToRawIntBits(row.getBaseCriticalHurt()));
            }
        } finally {
            appender.list.stream().filter(e -> e.getFormattedMessage().contains("rejected"))
                    .forEach(e -> System.err.println(e.getFormattedMessage()));
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    private Path write(String name, JsonArray rows) throws Exception {
        return Files.writeString(directory.resolve(name), rows.toString());
    }

    private static JsonObject readPinned(String property, String expected) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        byte[] bytes = Files.readAllBytes(Path.of(value));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return JsonUtils.decode(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static int bits(String value) { return Integer.parseUnsignedInt(value, 16); }
}
