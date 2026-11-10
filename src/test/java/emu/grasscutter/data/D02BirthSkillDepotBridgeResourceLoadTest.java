package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarCurveData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.props.ElementType;
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

/** Approved field fragments through actual loader; not a complete Avatar constructor input. */
class D02BirthSkillDepotBridgeResourceLoadTest {
    private static JsonArray skills, depots, avatars, curves;
    private static Field fettersCache;
    @TempDir Path directory;
    private Int2ObjectMap<AvatarSkillData> oldSkills;
    private Int2ObjectMap<AvatarSkillDepotData> oldDepots;
    private Int2ObjectMap<AvatarCurveData> oldCurves;
    private Int2ObjectMap<AvatarData> oldAvatars;
    private Object oldFetters;
    private boolean oldLoadedAll;

    @BeforeAll
    static void preparePinnedInputs() throws Exception {
        Grasscutter.getLogger();
        JsonObject bridge = report("d02.bridge.report", "d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e");
        JsonObject avatar = report("d02.bridge.avatar", "32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e");
        JsonObject promote = report("d02.bridge.promote", "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        JsonArray fragments = bridge.getAsJsonArray("javaProjectionFragments");
        JsonArray parsed = bridge.getAsJsonObject("native").getAsJsonArray("parsedTables");
        JsonObject skillTable = table(parsed, "AvatarSkill", "a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe");
        JsonObject depotTable = table(parsed, "AvatarSkillDepot", "fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b");
        JsonObject avatarTable = table(parsed, "Avatar", "466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9");
        skills = new JsonArray();
        for (int id : new int[] {100540, 100550, 10067, 10068}) {
            JsonObject fields = approved(fragments, skillTable, "AvatarSkill", id, Set.of("id", "costElemType"));
            String symbol = id == 10068 ? "Wind" : "None";
            int number = id == 10068 ? 7 : 0;
            assertEquals(symbol, fields.get("costElemType").getAsString());
            JsonObject nativeValue = field(selected(skillTable, id), 440577).getAsJsonObject();
            assertEquals(number, nativeValue.get("value").getAsInt());
            assertEquals(symbol, nativeValue.getAsJsonArray("symbols").get(0).getAsString());
            JsonObject enumEntry = findByString(bridge.getAsJsonArray("enumMap"), "targetSymbol", symbol);
            assertTrue(enumEntry.get("accepted").getAsBoolean());
            assertEquals(number, enumEntry.get("targetValue").getAsInt());
            assertEquals(symbol, enumEntry.get("javaSymbol").getAsString());
            assertEquals(number, enumEntry.get("javaValue").getAsInt());
            skills.add(fields);
        }
        depots = new JsonArray();
        for (int id : new int[] {501, 701, 704}) {
            JsonObject fields = approved(fragments, depotTable, "AvatarSkillDepot", id, Set.of("id", "skills", "energySkill"));
            JsonObject selected = selected(depotTable, id);
            assertEquals(field(selected, 105358), fields.get("skills"));
            assertEquals(field(selected, 105369), fields.get("energySkill"));
            assertEquals(id == 704, selected.getAsJsonObject("scalarPresence").get("field6c").getAsBoolean());
            JsonObject role = findByInt(bridge.getAsJsonObject("native").getAsJsonArray("roleCases"), "depotId", id);
            assertEquals(id == 704, role.getAsJsonObject("scalarPresence").get("field6c").getAsBoolean());
            JsonObject expectedRoles = JsonUtils.decode(id == 501 ? "{\"100540\":0}" : id == 701 ? "{\"100550\":0}" : "{\"100553\":0,\"10067\":1,\"10068\":4}", JsonObject.class);
            assertEquals(expectedRoles, role.getAsJsonObject("roles"));
            if (id == 704) assertEquals(10068, role.get("energySlot4Id").getAsInt());
            else assertTrue(role.get("energySlot4Id").isJsonNull());
            JsonArray expectedSkills = JsonUtils.decode(id == 501 ? "[100540,0,0,0]" : id == 701 ? "[100550,0,0,0]" : "[100553,10067,0,0]", JsonArray.class);
            assertEquals(expectedSkills, fields.getAsJsonArray("skills"));
            assertEquals(id == 704 ? 10068 : 0, fields.get("energySkill").getAsInt());
            depots.add(fields);
        }
        avatars = new JsonArray();
        for (int id : new int[] {10000005, 10000007}) {
            JsonObject bridgeRow = findFragment(fragments, "Avatar", id);
            JsonObject fields = approved(fragments, avatarTable, "Avatar", id, Set.of("id", "skillDepotId"));
            assertEquals(field(selected(avatarTable, id), 289485), fields.get("skillDepotId"));
            assertEquals(id == 10000005 ? 501 : 701, fields.get("skillDepotId").getAsInt());
            JsonObject hpRow = findFragment(avatar.getAsJsonArray("javaProjectionFragments"), "Avatar", id);
            assertTrue(hpRow.get("fragmentOnly").getAsBoolean());
            assertEquals(bridgeRow.get("source"), hpRow.get("source"));
            JsonObject hpFields = hpRow.getAsJsonObject("javaFields");
            assertEquals(Set.of("id", "hpBase", "attackBase", "defenseBase", "propGrowCurves"), hpFields.keySet());
            assertEquals(id, hpFields.get("id").getAsInt());
            JsonObject merged = hpFields.deepCopy();
            merged.add("skillDepotId", fields.get("skillDepotId").deepCopy());
            avatars.add(merged);
        }
        curves = new JsonArray();
        JsonArray curveRows = promote.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows");
        assertEquals(100, curveRows.size());
        for (int i = 0; i < 100; i++) {
            JsonObject row = curveRows.get(i).getAsJsonObject();
            assertTrue(row.get("completeDeclaredJavaInputRow").getAsBoolean());
            assertEquals("AvatarCurve", row.get("table").getAsString());
            JsonObject fields = row.getAsJsonObject("javaFields");
            assertEquals(Set.of("level", "curveInfos"), fields.keySet());
            assertEquals(i + 1, row.get("key").getAsInt());
            assertEquals(i + 1, fields.get("level").getAsInt());
            assertEquals(4, fields.getAsJsonArray("curveInfos").size());
            curves.add(fields.deepCopy());
        }
        fettersCache = GameData.class.getDeclaredField("fetters");
        fettersCache.setAccessible(true);
    }

    @BeforeEach
    void isolate() throws Exception {
        oldSkills = new Int2ObjectOpenHashMap<>(GameData.getAvatarSkillDataMap());
        oldDepots = new Int2ObjectOpenHashMap<>(GameData.getAvatarSkillDepotDataMap());
        oldCurves = new Int2ObjectLinkedOpenHashMap<>(GameData.getAvatarCurveDataMap());
        oldAvatars = new Int2ObjectOpenHashMap<>(GameData.getAvatarDataMap());
        oldFetters = fettersCache.get(null);
        oldLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getAvatarSkillDataMap().clear();
        GameData.getAvatarSkillDepotDataMap().clear();
        GameData.getAvatarCurveDataMap().clear();
        GameData.getAvatarDataMap().clear();
        fettersCache.set(null, new HashMap<Integer, List<Integer>>());
    }

    @AfterEach
    void restore() throws Exception {
        GameData.getAvatarSkillDataMap().clear(); GameData.getAvatarSkillDataMap().putAll(oldSkills);
        GameData.getAvatarSkillDepotDataMap().clear(); GameData.getAvatarSkillDepotDataMap().putAll(oldDepots);
        GameData.getAvatarCurveDataMap().clear(); GameData.getAvatarCurveDataMap().putAll(oldCurves);
        GameData.getAvatarDataMap().clear(); GameData.getAvatarDataMap().putAll(oldAvatars);
        fettersCache.set(null, oldFetters);
        assertSame(oldFetters, fettersCache.get(null));
        assertEquals(oldSkills, GameData.getAvatarSkillDataMap());
        assertEquals(oldDepots, GameData.getAvatarSkillDepotDataMap());
        assertEquals(List.copyOf(oldCurves.keySet()), List.copyOf(GameData.getAvatarCurveDataMap().keySet()));
        assertEquals(oldCurves, GameData.getAvatarCurveDataMap());
        assertEquals(oldAvatars, GameData.getAvatarDataMap());
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void approvedBirthAndNonzeroEnergyBindSameLoadedObjects() throws Exception {
        loadAll(0, 0);
        assertEquals(Set.of(100540, 100550, 10067, 10068), GameData.getAvatarSkillDataMap().keySet());
        assertEquals(Set.of(501, 701, 704), GameData.getAvatarSkillDepotDataMap().keySet());
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        assertEquals(100, GameData.getAvatarCurveDataMap().size());
        for (int id : new int[] {100540, 100550, 10067, 10068}) assertEquals(id, GameData.getAvatarSkillDataMap().get(id).getId());
        assertBirthBindings();
        var depot = GameData.getAvatarSkillDepotDataMap().get(704);
        assertEquals(704, depot.getId());
        assertEquals(List.of(100553, 10067, 0, 0), depot.getSkills());
        assertEquals(10068, depot.getEnergySkill());
        assertNull(GameData.getAvatarSkillDataMap().get(100553)); // This fragment was not approved.
        assertSame(GameData.getAvatarSkillDataMap().get(10068), depot.getEnergySkillData());
        assertEquals(ElementType.Wind, depot.getElementType());
        assertArrayEquals(new int[] {100553, 10067, 10068}, depot.getSkillsAndEnergySkill().toArray());
    }

    @Test
    void missingEnergyReferenceIsAcceptedAsNullAndNone() throws Exception {
        loadAll(10068, 0);
        assertNull(GameData.getAvatarSkillDataMap().get(10068));
        assertBirthBindings();
        var depot = GameData.getAvatarSkillDepotDataMap().get(704);
        assertNotNull(depot);
        assertEquals(10068, depot.getEnergySkill());
        assertNull(depot.getEnergySkillData());
        assertEquals(ElementType.None, depot.getElementType());
    }

    @Test
    void missingDefaultDepotStillLoadsAvatarWithNullBinding() throws Exception {
        loadAll(0, 701);
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
        assertSame(GameData.getAvatarSkillDepotDataMap().get(501), GameData.getAvatarDataMap().get(10000005).getSkillDepot());
        assertNull(GameData.getAvatarSkillDepotDataMap().get(701));
        assertEquals(701, GameData.getAvatarDataMap().get(10000007).getSkillDepotId());
        assertNull(GameData.getAvatarDataMap().get(10000007).getSkillDepot());
    }

    @Test
    void depotBeforeSkillDoesNotLazilyRepairButReloadRebinds() throws Exception {
        loadDepots(0);
        var old = GameData.getAvatarSkillDepotDataMap().get(704);
        assertNotNull(old);
        assertNull(old.getEnergySkillData());
        assertEquals(ElementType.None, old.getElementType());
        loadSkills(0);
        assertSame(old, GameData.getAvatarSkillDepotDataMap().get(704));
        assertNull(old.getEnergySkillData());
        assertEquals(ElementType.None, old.getElementType());
        loadDepots(0);
        var replacement = GameData.getAvatarSkillDepotDataMap().get(704);
        assertNotSame(old, replacement);
        assertSame(GameData.getAvatarSkillDataMap().get(10068), replacement.getEnergySkillData());
        assertEquals(ElementType.Wind, replacement.getElementType());
        assertNull(old.getEnergySkillData());
    }

    private void assertBirthBindings() {
        for (int[] pair : new int[][] {{10000005, 501, 100540}, {10000007, 701, 100550}}) {
            var avatar = GameData.getAvatarDataMap().get(pair[0]);
            var depot = GameData.getAvatarSkillDepotDataMap().get(pair[1]);
            assertNotNull(avatar); assertNotNull(depot);
            assertEquals(pair[0], avatar.getId()); assertEquals(pair[1], depot.getId());
            assertEquals(pair[1], avatar.getSkillDepotId()); assertSame(depot, avatar.getSkillDepot());
            assertEquals(List.of(pair[2], 0, 0, 0), depot.getSkills());
            assertEquals(0, depot.getEnergySkill()); assertNull(depot.getEnergySkillData());
            assertEquals(ElementType.None, depot.getElementType());
            assertArrayEquals(new int[] {pair[2]}, depot.getSkillsAndEnergySkill().toArray());
            assertNotNull(GameData.getAvatarSkillDataMap().get(pair[2]));
        }
    }

    private void loadAll(int skipSkill, int skipDepot) throws Exception {
        loadSkills(skipSkill); loadDepots(skipDepot);
        ResourceLoader.loadFromResource(AvatarCurveData.class, write("curves.json", curves, 0), GameData.getAvatarCurveDataMap());
        ResourceLoader.loadFromResource(AvatarData.class, write("avatars.json", avatars, 0), GameData.getAvatarDataMap());
    }
    private void loadSkills(int skip) throws Exception {
        ResourceLoader.loadFromResource(AvatarSkillData.class, write("skills.json", skills, skip), GameData.getAvatarSkillDataMap());
    }
    private void loadDepots(int skip) throws Exception {
        ResourceLoader.loadFromResource(AvatarSkillDepotData.class, write("depots.json", depots, skip), GameData.getAvatarSkillDepotDataMap());
    }
    private Path write(String name, JsonArray rows, int skip) throws Exception {
        JsonArray result = new JsonArray();
        for (JsonElement e : rows) if (skip == 0 || e.getAsJsonObject().get("id").getAsInt() != skip) result.add(e.deepCopy());
        return Files.writeString(directory.resolve(name), JsonUtils.encode(result), StandardCharsets.UTF_8);
    }

    private static JsonObject report(String property, String expected) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required property " + property); assertFalse(value.isBlank());
        byte[] raw = Files.readAllBytes(Path.of(value));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)), property);
        return JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
    }
    private static JsonObject table(JsonArray tables, String name, String sha) {
        JsonObject table = findByString(tables, "name", name);
        assertEquals(sha, table.get("payloadSHA256").getAsString());
        return table;
    }
    private static JsonObject approved(JsonArray fragments, JsonObject table, String name, int id, Set<String> keys) {
        JsonObject row = findFragment(fragments, name, id);
        assertTrue(row.get("fragmentOnly").getAsBoolean());
        JsonObject found = selected(table, id);
        assertTrue(found.get("found").getAsBoolean());
        assertEquals(found.get("source"), row.get("source"));
        JsonObject fields = row.getAsJsonObject("javaFields");
        assertEquals(keys, fields.keySet()); assertEquals(id, fields.get("id").getAsInt());
        return fields.deepCopy();
    }
    private static JsonObject selected(JsonObject table, int id) { return findByInt(table.getAsJsonArray("selected"), "key", id); }
    private static JsonElement field(JsonObject row, int index) { return findByInt(row.getAsJsonArray("fields"), "fieldIndex", index).get("value"); }
    private static JsonObject findFragment(JsonArray rows, String table, int id) {
        JsonObject match = null;
        for (JsonElement e : rows) {
            JsonObject row = e.getAsJsonObject();
            if (table.equals(row.get("table").getAsString()) && row.get("key").getAsInt() == id) {
                assertNull(match, "duplicate fragment " + table + "/" + id); match = row;
            }
        }
        assertNotNull(match, "missing fragment " + table + "/" + id); return match;
    }
    private static JsonObject findByInt(JsonArray rows, String key, int value) {
        JsonObject match = null;
        for (JsonElement e : rows) { JsonObject row = e.getAsJsonObject(); if (row.get(key).getAsInt() == value) { assertNull(match, "duplicate " + key); match = row; } }
        assertNotNull(match, "missing " + key + "=" + value); return match;
    }
    private static JsonObject findByString(JsonArray rows, String key, String value) {
        JsonObject match = null;
        for (JsonElement e : rows) { JsonObject row = e.getAsJsonObject(); if (value.equals(row.get(key).getAsString())) { assertNull(match, "duplicate " + key); match = row; } }
        assertNotNull(match, "missing " + key + "=" + value); return match;
    }
}
