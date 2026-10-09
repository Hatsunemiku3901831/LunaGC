package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class OpenConfigResourceRefreshTest {
    @TempDir Path temporary;
    private Path cache, equipment, avatars;
    private Map<String, OpenConfigEntry> previous;

    @BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @BeforeEach
    void setup() throws Exception {
        cache = temporary.resolve("OpenConfig.json");
        equipment = Files.createDirectory(temporary.resolve("EquipTalents"));
        avatars = Files.createDirectory(temporary.resolve("AvatarTalents"));
        previous = new HashMap<>(GameData.getOpenConfigEntries());
        GameData.getOpenConfigEntries().clear();
    }

    @AfterEach
    void restore() {
        GameData.getOpenConfigEntries().clear();
        GameData.getOpenConfigEntries().putAll(previous);
    }

    private void cached(String json) throws Exception {
        Files.writeString(cache, json);
    }

    private void source(Path directory, String filename, String json) throws Exception {
        Files.writeString(directory.resolve(filename + ".json"), json);
    }

    private void load() {
        ResourceLoader.loadOpenConfig(cache, equipment, avatars);
    }

    private OpenConfigEntry entry(String name) {
        return GameData.getOpenConfigEntries().get(name);
    }

    private String level(int amount) {
        return "[{\"$type\":\"AddTalentExtraLevel\",\"talentType\":\"AvatarSkill\",\"talentIndex\":2,\"extraLevel\":"
                + amount
                + "}]";
    }

    private String named(String name, String body) {
        return "{\"" + name + "\":" + body + "}";
    }

    @Test
    void currentDefinitionsAndNewNamesWinOverAnExistingCompiledCache() throws Exception {
        cached("[{\"name\":\"Updated\",\"extraTalentIndex\":2}]");
        source(
                avatars,
                "Current",
                "{\"Updated\":"
                        + level(1)
                        + ",\"New\":[{\"$type\":\"AddAbility\",\"abilityName\":\"CurrentAbility\"}]}");
        load();
        assertEquals(1, entry("Updated").getExtraTalentModifiers()[0].extraLevel());
        assertArrayEquals(new String[] {"CurrentAbility"}, entry("New").getAddAbilities());
    }

    @Test
    void cacheOnlyExtensionsRemainWithoutReplacingCurrentResources() throws Exception {
        cached("[{\"name\":\"Custom\",\"addAbilities\":[\"Extension\"]}]");
        source(equipment, "Current", named("Current", level(3)));
        load();
        assertEquals(Set.of("Custom", "Current"), GameData.getOpenConfigEntries().keySet());
        assertArrayEquals(new String[] {"Extension"}, entry("Custom").getAddAbilities());
    }

    @Test
    void malformedCacheDoesNotBlockSourcesAndIsNotRewritten() throws Exception {
        cached("[ broken");
        var bytes = Files.readAllBytes(cache);
        source(avatars, "Current", named("Current", level(1)));
        assertDoesNotThrow(this::load);
        assertEquals(1, entry("Current").getExtraTalentModifiers()[0].extraLevel());
        assertArrayEquals(bytes, Files.readAllBytes(cache));
    }

    @Test
    void invalidCompiledRowsDoNotDiscardLaterValidEntries() throws Exception {
        cached(
                "[null,7,{\"name\":\"\"},{\"name\":\"Broken\",\"skillPointModifiers\":\"bad\"},{\"name\":\"Valid\",\"extraTalentIndex\":9}]");
        load();
        assertEquals(Set.of("Valid"), GameData.getOpenConfigEntries().keySet());
        assertEquals(3, entry("Valid").getExtraTalentModifiers()[0].extraLevel());
    }

    @Test
    void oneMissingDirectoryDoesNotPreventTheOtherDirectoryLoading() throws Exception {
        source(avatars, "Current", named("Current", level(1)));
        ResourceLoader.loadOpenConfig(cache, temporary.resolve("Missing"), avatars);
        assertNotNull(entry("Current"));
    }

    @Test
    void malformedSourceFileDoesNotStopLaterFilesOrCachedFallbacks() throws Exception {
        cached("[{\"name\":\"Fallback\",\"extraTalentIndex\":2}]");
        source(equipment, "a", "broken");
        source(equipment, "b", named("Current", level(1)));
        load();
        assertNotNull(entry("Fallback"));
        assertNotNull(entry("Current"));
    }

    @Test
    void emptyArrayPlaceholderDoesNotDiscardDefinitionsInOtherFiles() throws Exception {
        source(equipment, "Common", "[]");
        source(equipment, "Current", named("Current", level(1)));
        load();
        assertEquals(Set.of("Current"), GameData.getOpenConfigEntries().keySet());
    }

    @Test
    void invalidDefinitionAndEffectsAreIsolatedWithinOneFile() throws Exception {
        cached("[{\"name\":\"Fallback\",\"extraTalentIndex\":2}]");
        source(
                avatars,
                "Mixed",
                "{\"Fallback\":null,\"Mixed\":[null,{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":\"broken\"},{\"$type\":\"AddAbility\",\"abilityName\":\"Valid\"}],\"After\":"
                        + level(1)
                        + "}");
        load();
        assertEquals(3, entry("Fallback").getExtraTalentModifiers()[0].extraLevel());
        assertArrayEquals(new String[] {"Valid"}, entry("Mixed").getAddAbilities());
        assertNotNull(entry("After"));
    }

    @Test
    void whollyUnreadableEffectsKeepOnlyTheirValidCachedFallback() throws Exception {
        cached("[{\"name\":\"Fallback\",\"extraTalentIndex\":2}]");
        source(avatars, "Broken", "{\"Fallback\":[null,{\"extraLevel\":1}],\"NoFallback\":[null]}");
        load();
        assertEquals(Set.of("Fallback"), GameData.getOpenConfigEntries().keySet());
        assertEquals(3, entry("Fallback").getExtraTalentModifiers()[0].extraLevel());
    }

    @Test
    void explicitEmptyCurrentDefinitionsRemoveLegacyEffects() throws Exception {
        cached("[{\"name\":\"Cleared\",\"extraTalentIndex\":2,\"addAbilities\":[\"Old\"]}]");
        source(avatars, "Empty", "{\"Cleared\":[]}");
        load();
        assertEquals(0, entry("Cleared").getExtraTalentModifiers().length);
        assertNull(entry("Cleared").getAddAbilities());
    }

    @Test
    void unknownCurrentTypesCannotReactivateTheLegacyTalentIndexFallback() throws Exception {
        cached("[{\"name\":\"Current\",\"extraTalentIndex\":2}]");
        source(
                avatars,
                "Unknown",
                "{\"Current\":[{\"$type\":\"Unknown\",\"talentIndex\":2,\"extraLevel\":3}]}");
        load();
        assertEquals(0, entry("Current").getExtraTalentModifiers().length);
    }

    @Test
    void unsuccessfulReloadKeepsThePreviousCatalog() throws Exception {
        var prior = new OpenConfigEntry("Previous", new ResourceLoader.OpenConfigData[0]);
        GameData.getOpenConfigEntries().put("Previous", prior);
        cached("null");
        source(avatars, "Broken", "null");
        load();
        assertSame(prior, entry("Previous"));
    }

    @Test
    void successfulReloadRemovesMemoryOnlyEntries() throws Exception {
        GameData.getOpenConfigEntries()
                .put(
                        "Removed",
                        new OpenConfigEntry("Removed", new ResourceLoader.OpenConfigData[0]));
        source(avatars, "Current", named("Current", level(1)));
        load();
        assertEquals(Set.of("Current"), GameData.getOpenConfigEntries().keySet());
    }

    @Test
    void duplicateSourcesUseSortedFilesThenDirectoryOrder() throws Exception {
        source(equipment, "a", named("Duplicate", level(1)));
        source(equipment, "b", named("Duplicate", level(2)));
        load();
        assertEquals(2, entry("Duplicate").getExtraTalentModifiers()[0].extraLevel());
        source(avatars, "a", named("Duplicate", level(3)));
        load();
        assertEquals(3, entry("Duplicate").getExtraTalentModifiers()[0].extraLevel());
    }

    @Test
    void newCompiledCacheRoundTripPreservesAllModifiersAndAmounts() throws Exception {
        var raw =
                new OpenConfigEntry(
                        "Combined",
                        JsonUtils.decode(
                                JsonParser.parseString(
                                        "[{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":1},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":9,\"extraLevel\":1}]"),
                                ResourceLoader.OpenConfigData[].class));
        cached(JsonUtils.encode(List.of(raw)));
        load();
        assertEquals(2, entry("Combined").getExtraTalentModifiers().length);
        assertTrue(
                Arrays.stream(entry("Combined").getExtraTalentModifiers())
                        .allMatch(m -> m.extraLevel() == 1));
    }

    @Test
    void allCurrentResourceNamesAndTalentAmountsLoadDespiteAStaleCache() throws Exception {
        cached(
                "[{\"name\":\"Tartaglia_PermanentSkill_3\",\"extraTalentIndex\":1},{\"name\":\"Custom\",\"addAbilities\":[\"Extension\"]}]");
        var dirs =
                List.of(
                        Path.of("resources/BinOutput/Talent/EquipTalents"),
                        Path.of("resources/BinOutput/Talent/AvatarTalents"),
                        Path.of("resources/BinOutput/Talent/RelicTalents"),
                        Path.of("resources/BinOutput/Talent/TeamTalents"));
        var expected = new TreeMap<String, com.google.gson.JsonArray>();
        for (var dir : dirs)
            try (var files = Files.list(dir)) {
                for (var file : files.sorted().toList()) {
                    var root = JsonParser.parseString(Files.readString(file));
                    if (root.isJsonArray()) {
                        assertEquals(0, root.getAsJsonArray().size());
                        continue;
                    }
                    for (var e : root.getAsJsonObject().entrySet())
                        expected.put(e.getKey(), e.getValue().getAsJsonArray());
                }
            }
        ResourceLoader.loadOpenConfig(cache, dirs.toArray(new Path[0]));
        var names = new TreeSet<>(expected.keySet());
        names.add("Custom");
        assertEquals(names, GameData.getOpenConfigEntries().keySet());
        int levels = 0, charges = 0;
        for (var e : expected.entrySet()) {
            var levelRows = new ArrayList<String>();
            for (var row : e.getValue()) {
                var r = row.getAsJsonObject();
                if (r.get("$type").getAsString().equals("AddTalentExtraLevel")) {
                    levelRows.add(
                            r.get("talentIndex").getAsInt() + ":" + r.get("extraLevel").getAsInt());
                    levels++;
                }
                if (r.get("$type").getAsString().equals("ModifySkillPoint")) charges++;
            }
            assertEquals(
                    levelRows,
                    Arrays.stream(entry(e.getKey()).getExtraTalentModifiers())
                            .map(m -> m.talentIndex() + ":" + m.extraLevel())
                            .toList(),
                    e.getKey());
        }
        assertEquals(259, levels);
        assertEquals(11, charges);
        assertEquals(2001, expected.size());
        assertArrayEquals(
                new String[] {"TeamResonance_Fire_Lv2"},
                entry("TeamResonance_Fire_Lv2").getAddAbilities());
        assertEquals("Relic_ExtraAtkCritUp", entry("Relic_ExtraAtkCritUp").getAddAbilities()[0]);
        assertEquals(
                1, entry("Tartaglia_PermanentSkill_3").getExtraTalentModifiers()[0].extraLevel());
    }
}
