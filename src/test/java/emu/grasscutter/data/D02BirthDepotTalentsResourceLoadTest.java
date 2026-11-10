package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.FetterData;
import emu.grasscutter.data.excels.avatar.AvatarCurveData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/** Source-bound birth fragments, real loader and constructor; not complete Avatar acceptance. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class D02BirthDepotTalentsResourceLoadTest {
    private static JsonObject projection, mapping, promote;
    private static Field fettersCache;
    @TempDir Path directory;
    private final List<Map<Object, Object>> maps = new ArrayList<>();
    private final List<Map<Object, Object>> before = new ArrayList<>();
    private Object oldFetters;
    private boolean oldLoadedAll;

    @BeforeAll
    static void pinInputs() throws Exception {
        Grasscutter.getLogger();
        projection = report("d02.talents.projection", "ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f");
        mapping = report("d02.talents.mapping", "d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e");
        promote = report("d02.talents.promote", "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertTrue(projection.get("partial").getAsBoolean());
        assertFalse(projection.get("completeAvatar").getAsBoolean());
        assertFalse(projection.get("resourceReady").getAsBoolean());
        assertFalse(projection.get("d02Complete").getAsBoolean());
        fettersCache = GameData.class.getDeclaredField("fetters");
        fettersCache.setAccessible(true);
    }

    @BeforeEach
    void isolate() throws Exception {
        isolateMap(GameData.getAvatarSkillDataMap());
        isolateMap(GameData.getAvatarSkillDepotDataMap());
        isolateMap(GameData.getAvatarCurveDataMap());
        isolateMap(GameData.getAvatarDataMap());
        isolateMap(promoteMap());
        isolateMap(GameData.getMapByResourceDef(FetterData.class));
        isolateMap(GameData.getFetterCharacterCardDataMap());
        isolateMap(GameData.getRewardDataMap());
        isolateMap(GameData.getAvatarTalentDataMap());
        isolateMap(GameData.getProudSkillDataMap());
        isolateMap(GameData.getOpenConfigEntries());
        isolateMap(GameData.getAbilityEmbryoInfo());
        oldFetters = fettersCache.get(null);
        oldLoadedAll = ResourceLoader.isLoadedAll();
        fettersCache.set(null, new HashMap<Integer, List<Integer>>());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void isolateMap(Map<?, ?> map) {
        assertNotNull(map);
        Map<Object, Object> original = (Map) map;
        maps.add(original);
        before.add(new LinkedHashMap<>(original));
        original.clear();
    }

    @AfterEach
    void restore() throws Exception {
        for (int i = 0; i < maps.size(); i++) {
            maps.get(i).clear();
            maps.get(i).putAll(before.get(i));
            assertEquals(before.get(i), maps.get(i));
            if (maps.get(i) == (Object) GameData.getAvatarCurveDataMap())
                assertEquals(List.copyOf(before.get(i).keySet()), List.copyOf(maps.get(i).keySet()));
        }
        fettersCache.set(null, oldFetters);
        assertSame(oldFetters, fettersCache.get(null));
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test @Order(1)
    void exactTalentsSurviveRealDepotOnLoad() throws Exception {
        loadAll(0);
        assertEquals(Set.of(501, 701, 704), GameData.getAvatarSkillDepotDataMap().keySet());
        assertEquals(List.of(0, 0, 0, 0, 0, 0), GameData.getAvatarSkillDepotDataMap().get(501).getTalents());
        assertEquals(List.of(0, 0, 0, 0, 0, 0), GameData.getAvatarSkillDepotDataMap().get(701).getTalents());
        assertEquals(List.of(71, 72, 73, 74, 75, 76), GameData.getAvatarSkillDepotDataMap().get(704).getTalents());
        assertSame(GameData.getAvatarSkillDataMap().get(10068), GameData.getAvatarSkillDepotDataMap().get(704).getEnergySkillData());
        assertNull(GameData.getAvatarSkillDataMap().get(100553));
        for (int id : new int[] {501, 701, 704}) {
            var depot = GameData.getAvatarSkillDepotDataMap().get(id);
            // Current Java normalizes these missing fields; target proud semantics remain unproved.
            assertTrue(depot.getInherentProudSkillOpens().isEmpty());
            assertTrue(depot.getQuestProudSkillGroupIds().isEmpty());
        }
    }

    @Test @Order(2) void maleRealConstructor() throws Exception { construct(10000005, 501); }
    @Test @Order(3) void femaleRealConstructor() throws Exception { construct(10000007, 701); }
    @Test @Order(4) void maleMissingTalents() throws Exception { missingTalents(10000005, 501); }
    @Test @Order(5) void femaleMissingTalents() throws Exception { missingTalents(10000007, 701); }

    private void construct(int avatarId, int depotId) throws Exception {
        loadAll(0);
        var data = GameData.getAvatarDataMap().get(avatarId);
        assertNotNull(data);
        assertSame(GameData.getAvatarSkillDepotDataMap().get(depotId), data.getSkillDepot());
        // Do not catch failure: JUnit must report the actual first constructor stack.
        var avatar = new Avatar(data);
        System.out.println("D02 constructor returned avatar=" + avatarId + " depot=" + depotId);
        assertEquals(avatarId, avatar.getAvatarId());
        assertSame(data, avatar.getAvatarData());
        assertSame(data.getSkillDepot(), avatar.getSkillDepot());
        assertTrue(avatar.getTalentIdList().isEmpty());
    }

    private void missingTalents(int avatarId, int depotId) throws Exception {
        loadAll(depotId);
        var data = GameData.getAvatarDataMap().get(avatarId);
        assertNotNull(data);
        assertNull(GameData.getAvatarSkillDepotDataMap().get(depotId).getTalents());
        assertEquals(List.of(0, 0, 0, 0, 0, 0), GameData.getAvatarSkillDepotDataMap().get(depotId == 501 ? 701 : 501).getTalents());
        assertEquals(List.of(71, 72, 73, 74, 75, 76), GameData.getAvatarSkillDepotDataMap().get(704).getTalents());
        NullPointerException error = assertThrows(NullPointerException.class, () -> new Avatar(data));
        error.printStackTrace(System.err);
        assertTrue(java.util.Arrays.stream(error.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(Avatar.class.getName())
                        && frame.getMethodName().equals("getTalentIdList") && frame.getLineNumber() == 529),
                "Earlier/different failure is not the expected talents boundary; inspect actual stack above");
    }

    private void loadAll(int omitDepotTalents) throws Exception {
        JsonArray skills = new JsonArray();
        for (int id : new int[] {100540, 100550, 10067, 10068}) {
            JsonObject row = fragment(mapping.getAsJsonArray("javaProjectionFragments"), "AvatarSkill", id);
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            JsonObject fields = row.getAsJsonObject("javaFields");
            assertEquals(Set.of("id", "costElemType"), fields.keySet());
            assertEquals(id, fields.get("id").getAsInt());
            skills.add(fields.deepCopy());
        }
        ResourceLoader.loadFromResource(AvatarSkillData.class, write("skills.json", skills), GameData.getAvatarSkillDataMap());
        assertEquals(4, GameData.getAvatarSkillDataMap().size());
        JsonArray depots = projection.getAsJsonArray("mergedDepots").deepCopy();
        JsonArray fragments = projection.getAsJsonArray("depotFragments");
        assertEquals(3, depots.size()); assertEquals(3, fragments.size());
        for (JsonElement element : depots) {
            JsonObject fields = element.getAsJsonObject();
            assertEquals(Set.of("id", "skills", "energySkill", "talents"), fields.keySet());
            int id = fields.get("id").getAsInt();
            JsonObject projected = fragment(fragments, "AvatarSkillDepot", id);
            JsonObject bridge = fragment(mapping.getAsJsonArray("javaProjectionFragments"), "AvatarSkillDepot", id);
            assertEquals(bridge.get("source"), projected.get("source"));
            assertEquals(projected.getAsJsonObject("javaFields").get("talents"), fields.get("talents"));
            if (id == omitDepotTalents) fields.remove("talents");
        }
        ResourceLoader.loadFromResource(AvatarSkillDepotData.class, write("depots.json", depots), GameData.getAvatarSkillDepotDataMap());
        assertEquals(3, GameData.getAvatarSkillDepotDataMap().size());
        JsonArray curves = new JsonArray();
        for (JsonElement element : promote.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows"))
            curves.add(element.getAsJsonObject().getAsJsonObject("javaFields").deepCopy());
        assertEquals(100, curves.size());
        ResourceLoader.loadFromResource(AvatarCurveData.class, write("curves.json", curves), GameData.getAvatarCurveDataMap());
        assertEquals(100, GameData.getAvatarCurveDataMap().size());
        JsonObject group = fragment(promote.getAsJsonObject("javaProjection").getAsJsonArray("completeBirthPromoteInputRows"), "AvatarPromote", 12 << 8);
        JsonArray promoteRows = new JsonArray(); promoteRows.add(group.getAsJsonObject("javaFields").deepCopy());
        ResourceLoader.loadFromResource(AvatarPromoteData.class, write("promote.json", promoteRows), promoteMap());
        assertEquals(Set.of(12 << 8), promoteMap().keySet());
        JsonArray avatars = projection.getAsJsonArray("birthAvatarInputs");
        assertEquals(2, avatars.size());
        ResourceLoader.loadFromResource(AvatarData.class, write("avatars.json", avatars), GameData.getAvatarDataMap());
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
    }

    private Path write(String name, JsonArray rows) throws Exception {
        return Files.writeString(directory.resolve(name), JsonUtils.encode(rows), StandardCharsets.UTF_8);
    }

    private static JsonObject fragment(JsonArray rows, String table, int id) {
        JsonObject found = null;
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            if (table.equals(row.get("table").getAsString()) && row.get("key").getAsInt() == id) {
                assertNull(found, "duplicate " + table + "/" + id); found = row;
            }
        }
        assertNotNull(found, "missing " + table + "/" + id);
        return found;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Int2ObjectMap<AvatarPromoteData> promoteMap() {
        return (Int2ObjectMap) GameData.getMapByResourceDef(AvatarPromoteData.class);
    }

    private static JsonObject report(String property, String expected) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required property " + property);
        assertFalse(value.isBlank(), "required property " + property);
        byte[] raw = Files.readAllBytes(Path.of(value));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)), property);
        return JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
    }
}
