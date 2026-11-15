package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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

/** Only approved Skill id/element and Depot id/skills/energySkill fragments. */
class D02Old121SkillDepotResourceLoadTest {
    private static final String DEPOT_SHA = "24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654";
    private static Map<Integer, JsonObject> skills;
    private static Map<Integer, JsonObject> depots;
    @TempDir Path directory;
    private Int2ObjectMap<AvatarSkillData> skillMap;
    private Int2ObjectMap<AvatarSkillDepotData> depotMap;
    private Int2ObjectMap<AvatarSkillData> oldSkills;
    private Int2ObjectMap<AvatarSkillDepotData> oldDepots;
    private boolean oldLoadedAll;

    @BeforeAll
    static void preparePinnedInputs() throws Exception {
        Grasscutter.getLogger();
        JsonObject skillReport = report("d02.skillDepot.skillsReport", "d02.skillDepot.skillsReportSHA256");
        JsonObject depotReport = report("d02.skillDepot.depotReport", "d02.skillDepot.depotReportSHA256");
        assertEquals(DEPOT_SHA, required("d02.skillDepot.depotReportSHA256"));
        assertEquals("R3-SKILL-ELEMENT-SUFFIX-HOST-BATCH-20261116", skillReport.get("task").getAsString());
        assertTrue(skillReport.get("passed").getAsBoolean());
        assertTrue(skillReport.get("fragmentOnly").getAsBoolean());
        assertFalse(skillReport.get("idRawDecoded").getAsBoolean());
        assertFalse(skillReport.get("resourceReady").getAsBoolean());
        assertEquals("R3-OLD121-DEPOT-CORE-FRAGMENT-PROJECTION", depotReport.get("task").getAsString());

        skills = fragments(skillReport.getAsJsonArray("javaProjectionFragments"), "AvatarSkill", Set.of("id", "costElemType"));
        depots = fragments(depotReport.getAsJsonArray("fragments"), "AvatarSkillDepot", Set.of("id", "skills", "energySkill"));
        assertEquals(369, skills.size());
        assertEquals(121, depots.size());
        JsonObject cohort = skillReport.getAsJsonObject("cohort");
        Set<Integer> demand = integers(cohort.getAsJsonArray("demandKeys"));
        Set<Integer> added = integers(cohort.getAsJsonArray("newKeys"));
        Set<Integer> reused = integers(cohort.getAsJsonArray("reusedJavaApprovedKeys"));
        Set<Integer> energy = integers(cohort.getAsJsonArray("energyKeys"));
        assertEquals(skills.keySet(), demand);
        assertEquals(Set.of(10067, 10068), reused);
        Set<Integer> expectedNew = new HashSet<>(demand);
        expectedNew.removeAll(reused);
        assertEquals(expectedNew, added);
        assertEquals(367, added.size());

        Set<Integer> references = new HashSet<>();
        Set<Integer> energyReferences = new HashSet<>();
        int positiveSlots = 0;
        int zeroSlots = 0;
        for (JsonObject fields : depots.values()) {
            JsonArray slots = fields.getAsJsonArray("skills");
            assertEquals(4, slots.size());
            for (JsonElement slot : slots) {
                int id = slot.getAsInt();
                assertTrue(id >= 0);
                if (id == 0) zeroSlots++;
                else { positiveSlots++; references.add(id); }
            }
            int id = fields.get("energySkill").getAsInt();
            assertTrue(id > 0);
            references.add(id);
            energyReferences.add(id);
        }
        assertEquals(248, positiveSlots);
        assertEquals(236, zeroSlots);
        assertEquals(demand, references);
        assertEquals(121, energy.size());
        assertEquals(energy, energyReferences);
        for (JsonObject fields : skills.values()) {
            // valueOf rejects an unknown symbol instead of silently mapping it to None.
            ElementType.valueOf(fields.get("costElemType").getAsString());
        }
    }

    @BeforeEach
    void isolate() {
        skillMap = GameData.getAvatarSkillDataMap();
        depotMap = GameData.getAvatarSkillDepotDataMap();
        oldSkills = new Int2ObjectOpenHashMap<>(skillMap);
        oldDepots = new Int2ObjectOpenHashMap<>(depotMap);
        oldLoadedAll = ResourceLoader.isLoadedAll();
        skillMap.clear();
        depotMap.clear();
    }

    @AfterEach
    void restore() {
        skillMap.clear(); skillMap.putAll(oldSkills);
        depotMap.clear(); depotMap.putAll(oldDepots);
        assertSame(skillMap, GameData.getAvatarSkillDataMap());
        assertSame(depotMap, GameData.getAvatarSkillDepotDataMap());
        assertEquals(oldSkills, skillMap);
        assertEquals(oldDepots, depotMap);
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void approvedSkillAndDepotFragmentsLoadAndResolveReferences() throws Exception {
        ResourceLoader.loadFromResource(AvatarSkillData.class, write("skills.json", skills), skillMap);
        assertEquals(skills.keySet(), skillMap.keySet());
        Map<Integer, AvatarSkillData> loadedSkills = new HashMap<>();
        for (var entry : skills.entrySet()) {
            AvatarSkillData actual = skillMap.get(entry.getKey());
            assertNotNull(actual);
            assertEquals(entry.getKey().intValue(), actual.getId());
            assertEquals(ElementType.valueOf(entry.getValue().get("costElemType").getAsString()), actual.getCostElemType());
            loadedSkills.put(entry.getKey(), actual);
        }

        ResourceLoader.loadFromResource(AvatarSkillDepotData.class, write("depots.json", depots), depotMap);
        assertEquals(depots.keySet(), depotMap.keySet());
        for (var entry : depots.entrySet()) {
            AvatarSkillDepotData actual = depotMap.get(entry.getKey());
            JsonObject expected = entry.getValue();
            assertNotNull(actual);
            assertEquals(entry.getKey().intValue(), actual.getId());
            List<Integer> expectedSlots = java.util.Arrays.asList(
                    JsonUtils.decode(expected.getAsJsonArray("skills"), Integer[].class));
            assertEquals(expectedSlots, actual.getSkills());
            for (int id : actual.getSkills()) {
                if (id > 0) {
                    assertNotNull(loadedSkills.get(id));
                    assertSame(loadedSkills.get(id), skillMap.get(id));
                }
            }
            int energyId = expected.get("energySkill").getAsInt();
            assertEquals(energyId, actual.getEnergySkill());
            AvatarSkillData energySkill = loadedSkills.get(energyId);
            assertNotNull(energySkill);
            assertSame(energySkill, skillMap.get(energyId));
            assertSame(energySkill, actual.getEnergySkillData());
            assertEquals(energySkill.getCostElemType(), actual.getElementType());
        }
    }

    private Path write(String name, Map<Integer, JsonObject> fragments) throws Exception {
        JsonArray rows = new JsonArray();
        fragments.values().forEach(fields -> rows.add(fields.deepCopy()));
        return Files.writeString(directory.resolve(name), rows.toString(), StandardCharsets.UTF_8);
    }

    private static Map<Integer, JsonObject> fragments(JsonArray rows, String table, Set<String> fieldNames) {
        Map<Integer, JsonObject> result = new HashMap<>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            assertEquals(table, row.get("table").getAsString());
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            JsonObject source = row.getAsJsonObject("source");
            assertEquals(Set.of("begin", "end", "sha256"), source.keySet());
            assertTrue(source.get("begin").getAsLong() >= 0);
            assertTrue(source.get("end").getAsLong() > source.get("begin").getAsLong());
            assertTrue(source.get("sha256").getAsString().matches("[0-9a-f]{64}"));
            JsonObject fields = row.getAsJsonObject("javaFields");
            assertEquals(fieldNames, fields.keySet());
            int id = row.get("key").getAsInt();
            assertTrue(id > 0);
            assertEquals(id, fields.get("id").getAsInt());
            if (table.equals("AvatarSkill")) {
                assertFalse(row.get("idRawDecoded").getAsBoolean());
                assertEquals("accepted tableKey identity", row.get("logicalIDSource").getAsString());
            }
            assertNull(result.put(id, fields.deepCopy()), "duplicate " + table + "/" + id);
        }
        return result;
    }

    private static Set<Integer> integers(JsonArray array) {
        Set<Integer> result = new HashSet<>();
        for (JsonElement element : array) assertTrue(result.add(element.getAsInt()), "duplicate cohort key");
        return result;
    }

    private static JsonObject report(String property, String hashProperty) throws Exception {
        String expected = required(hashProperty);
        assertTrue(expected.matches("[0-9a-f]{64}"), hashProperty);
        byte[] bytes = Files.readAllBytes(Path.of(required(property)));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), property);
        return JsonUtils.decode(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        return value;
    }
}
