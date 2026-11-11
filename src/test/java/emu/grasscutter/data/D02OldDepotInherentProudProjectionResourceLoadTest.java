package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class D02OldDepotInherentProudProjectionResourceLoadTest {
    private static final String PROJECTION_SHA = "0dc990d1331619073cfc42023b4fe34ce7234590703cb621d884eaae93a917c9";
    private static final String ALL_SHA = "b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a";
    private static final String ACTIVE_SHA = "8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5";
    @TempDir Path temp;

    @Test
    void loadsAll121OriginalRowsAnd605OrderedPairs() throws Exception {
        var projected = projectionRows();
        var original = originalRows("d02.proud.opens.allDepots", ALL_SHA);
        assertEquals(121, projected.size());
        assertEquals(121, original.size());
        var resource = new JsonArray();
        Set<Integer> keys = new HashSet<>();
        for (var element : projected) {
            JsonObject fields = element.getAsJsonObject().getAsJsonObject("javaFields");
            verifyAllowedKeys(fields);
            assertTrue(keys.add(fields.get("id").getAsInt()));
            resource.add(fields);
        }
        var expected = byKey(original);
        assertEquals(expected.keySet(), keys);
        var loaded = load(resource, "all.json");
        assertEquals(keys, loaded.keySet());
        int slots = 0;
        int zeroThreshold = 0;
        int zeroGroup = 0;
        for (var item : expected.entrySet()) {
            JsonArray nativeSlots = nativeSlots(item.getValue());
            var actual = loaded.get(item.getKey());
            assertNotNull(actual);
            assertEquals(item.getKey().intValue(), actual.getId());
            assertEquals(5, nativeSlots.size());
            assertEquals(nativeSlots.size(), actual.getInherentProudSkillOpens().size());
            for (int i = 0; i < nativeSlots.size(); i++) {
                int phase = nativeLeaf(nativeSlots.get(i).getAsJsonArray(), 404853);
                int group = nativeLeaf(nativeSlots.get(i).getAsJsonArray(), 404854);
                var actualSlot = actual.getInherentProudSkillOpens().get(i);
                assertEquals(phase, actualSlot.getNeedAvatarPromoteLevel(), "phase " + item.getKey() + "/" + i);
                assertEquals(group, actualSlot.getProudSkillGroupId(), "group " + item.getKey() + "/" + i);
                if (phase == 0) zeroThreshold++;
                if (group == 0) zeroGroup++;
                slots++;
            }
        }
        assertEquals(605, slots);
        assertEquals(363, zeroThreshold);
        assertEquals(215, zeroGroup);
    }

    @Test
    void loadsFourActiveRowsAgainstBothOriginalReports() throws Exception {
        var projected = projectionRows();
        Map<Integer, JsonObject> all = byKey(originalRows("d02.proud.opens.allDepots", ALL_SHA));
        JsonArray active = originalRows("d02.proud.opens.activeDepots", ACTIVE_SHA);
        assertEquals(4, active.size());
        Map<Integer, JsonObject> projectionByKey = new HashMap<>();
        for (var row : projected) {
            JsonObject fields = row.getAsJsonObject().getAsJsonObject("javaFields");
            verifyAllowedKeys(fields);
            assertNull(projectionByKey.put(fields.get("id").getAsInt(), fields));
        }
        var resource = new JsonArray();
        Set<Integer> keys = new HashSet<>();
        for (var element : active) {
            var nativeRow = element.getAsJsonObject();
            int key = nativeRow.get("key").getAsInt();
            assertTrue(keys.add(key));
            var allRow = all.get(key);
            assertNotNull(allRow);
            assertEquals(allRow.get("avatarId"), nativeRow.get("avatarId"));
            assertEquals(allRow.get("source"), nativeRow.get("source"));
            JsonObject fields = projectionByKey.get(key);
            assertNotNull(fields);
            resource.add(fields);
        }
        var loaded = load(resource, "active.json");
        assertEquals(keys, loaded.keySet());
        int slots = 0;
        for (var element : active) {
            var nativeRow = element.getAsJsonObject();
            int key = nativeRow.get("key").getAsInt();
            JsonArray activeSlots = nativeSlots(nativeRow);
            JsonArray allSlots = nativeSlots(all.get(key));
            var actual = loaded.get(key).getInherentProudSkillOpens();
            assertEquals(5, activeSlots.size());
            assertEquals(activeSlots.size(), allSlots.size());
            assertEquals(activeSlots.size(), actual.size());
            for (int i = 0; i < activeSlots.size(); i++) {
                int phase = nativeLeaf(activeSlots.get(i).getAsJsonArray(), 404853);
                int group = nativeLeaf(activeSlots.get(i).getAsJsonArray(), 404854);
                assertEquals(phase, nativeLeaf(allSlots.get(i).getAsJsonArray(), 404853));
                assertEquals(group, nativeLeaf(allSlots.get(i).getAsJsonArray(), 404854));
                assertEquals(phase, actual.get(i).getNeedAvatarPromoteLevel());
                assertEquals(group, actual.get(i).getProudSkillGroupId());
                slots++;
            }
        }
        assertEquals(20, slots);
    }

    private JsonArray projectionRows() throws Exception {
        return readPinned("d02.proud.opens.projection", PROJECTION_SHA).getAsJsonArray("rows");
    }

    private JsonArray originalRows(String property, String hash) throws Exception {
        return readPinned(property, hash).getAsJsonArray("samples");
    }

    private JsonObject readPinned(String property, String hash) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "missing required property " + property);
        byte[] raw = Files.readAllBytes(Path.of(value));
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        return JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private Map<Integer, JsonObject> byKey(JsonArray rows) {
        Map<Integer, JsonObject> result = new HashMap<>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            assertNull(result.put(row.get("key").getAsInt(), row));
        }
        return result;
    }

    private JsonArray nativeSlots(JsonObject row) {
        JsonObject match = null;
        for (var element : row.getAsJsonArray("fields")) {
            JsonObject field = element.getAsJsonObject();
            if (field.get("fieldIndex").getAsInt() == 105355) {
                assertNull(match);
                match = field;
            }
        }
        assertNotNull(match);
        assertEquals(22284, match.get("owner").getAsInt());
        assertEquals("0x18", match.get("offset").getAsString());
        assertEquals(81127, match.getAsJsonObject("type").getAsJsonObject("elementType").get("typeDefinition").getAsInt());
        return match.getAsJsonArray("value");
    }

    private int nativeLeaf(JsonArray slot, int fieldIndex) {
        JsonObject match = null;
        for (var element : slot) {
            JsonObject field = element.getAsJsonObject();
            if (field.get("fieldIndex").getAsInt() == fieldIndex) {
                assertNull(match);
                match = field;
            }
        }
        assertNotNull(match);
        assertEquals(81127, match.get("owner").getAsInt());
        assertEquals(fieldIndex == 404853 ? "0x10" : "0x14", match.get("offset").getAsString());
        assertEquals(58209, match.getAsJsonObject("type").get("typeDefinition").getAsInt());
        return match.get("value").getAsInt();
    }

    private void verifyAllowedKeys(JsonObject fields) {
        assertEquals(Set.of("id", "inherentProudSkillOpens"), fields.keySet());
        for (var element : fields.getAsJsonArray("inherentProudSkillOpens")) {
            assertEquals(Set.of("proudSkillGroupId", "needAvatarPromoteLevel"), element.getAsJsonObject().keySet());
        }
    }

    private Int2ObjectOpenHashMap<AvatarSkillDepotData> load(JsonArray resource, String filename) throws Exception {
        Path path = Files.writeString(temp.resolve(filename), resource.toString(), StandardCharsets.UTF_8);
        Grasscutter.getLogger();
        boolean previousLoadedAll = ResourceLoader.isLoadedAll();
        var loaded = new Int2ObjectOpenHashMap<AvatarSkillDepotData>();
        ResourceLoader.loadFromResource(AvatarSkillDepotData.class, path, loaded);
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
        return loaded;
    }
}
