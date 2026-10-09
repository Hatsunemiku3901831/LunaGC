package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityTalentResolver;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Resource examples plus arithmetic verified in the supplied client, independent of callback
 * values.
 */
class TalentAbilityMathTest {
    private static final Map<String, OpenConfigEntry> CONFIGS = new LinkedHashMap<>();
    private static final Map<String, AbilityData> ABILITIES = new LinkedHashMap<>();
    private static final Map<String, AvatarTalentData> TALENTS = new LinkedHashMap<>();
    private AbilityTestFixture fixture;
    private TalentApplicationTest.TestAvatar avatar;
    private EntityAvatar entity;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        try (var directories = Files.list(Path.of("resources/BinOutput/Talent"))) {
            for (var directory :
                    directories
                            .filter(p -> p.getFileName().toString().endsWith("Talents"))
                            .toList())
                try (var files = Files.newDirectoryStream(directory, "*.json")) {
                    for (var file : files) {
                        var parsed = JsonParser.parseString(Files.readString(file));
                        if (!parsed.isJsonObject()) continue;
                        for (var row : parsed.getAsJsonObject().entrySet())
                            CONFIGS.put(
                                    row.getKey(),
                                    new OpenConfigEntry(
                                            row.getKey(),
                                            JsonUtils.decode(
                                                    row.getValue(), OpenConfigData[].class)));
                    }
                }
        }
        for (String character : List.of("Eula", "Ayaka", "Zhongli", "Layla", "Yae", "Nahida"))
            for (var row :
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                            + character
                                                            + ".json")))
                            .getAsJsonArray()) {
                var data =
                        JsonUtils.decode(row.getAsJsonObject().get("Default"), AbilityData.class);
                ABILITIES.put(data.abilityName, data);
            }
        for (var talent :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarTalentExcelConfigData.json"),
                        AvatarTalentData.class)) TALENTS.put(talent.getOpenConfig(), talent);
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(73566);
        avatar = new TalentApplicationTest.TestAvatar(fixture.player);
        entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        CONFIGS.forEach((name, data) -> put(GameData.getOpenConfigEntries(), name, data));
        ABILITIES.forEach((name, data) -> put(GameData.getAbilityDataMap(), name, data));
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        V old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private static OpenConfigEntry config(String rows) {
        return new OpenConfigEntry("Math", JsonUtils.decode(rows, OpenConfigData[].class));
    }

    private void activate(int id, String name, float... parameters) {
        put(
                GameData.getAvatarTalentDataMap(),
                id,
                JsonUtils.decode(
                        "{\"talentId\":"
                                + id
                                + ",\"openConfig\":\""
                                + name
                                + "\",\"paramList\":"
                                + JsonUtils.encode(parameters)
                                + "}",
                        AvatarTalentData.class));
        avatar.talents.add(id);
    }

    private void activate(String rows, float... parameters) {
        put(GameData.getOpenConfigEntries(), "Math", config(rows));
        activate(735661, "Math", parameters);
    }

    private Ability ability(String name, Map<String, Float> defaults) {
        var data = new AbilityData();
        data.abilityName = name;
        data.abilitySpecials = defaults;
        return new Ability(data, entity, fixture.player);
    }

    private Ability math(float original) {
        return ability("Test", Map.of("Value", original));
    }

    private static String modify(String delta, String ratio) {
        return "{\"$type\":\"ModifyAbility\",\"abilityName\":\"Test\",\"paramSpecial\":\"Value\","
                + "\"paramDelta\":"
                + delta
                + ",\"paramRatio\":"
                + ratio
                + "}";
    }

    static Stream<Arguments> actualConstellations() {
        return Stream.of(
                Arguments.of(
                        "Eula_Constellation_2",
                        "Avatar_Eula_ElementalArt_Burst",
                        "SkillStart_Ratio",
                        1f),
                Arguments.of("Zhongli_Constellation_4", "Avatar_Zhongli_GeoOrder", "Radius", 9f),
                Arguments.of(
                        "Layla_Constellation_6",
                        "Avatar_Layla_ElementalArt_Handler",
                        "Interval",
                        1.2f),
                Arguments.of(
                        "Yae_Constellation_2",
                        "Avatar_Yae_ElementalArt_FoxTower_Material",
                        "SizeRatio",
                        3.56f));
    }

    @ParameterizedTest
    @MethodSource("actualConstellations")
    void actualConstellationsUseTheirResourceBaselineAndOwnParameters(
            String config, String name, String variable, float expected) {
        var talent = TALENTS.get(config);
        assertNotNull(talent);
        activate(talent.getId(), config, talent.getParamList());
        var data = ABILITIES.get(name);
        var ability = ability(name, data.abilitySpecials);
        assertEquals(expected, ability.getAbilitySpecials().getFloat(variable), .00001f);
        assertEquals(
                expected, AbilityTalentResolver.overrides(avatar, name).get(variable), .00001f);
    }

    @Test
    void actualAyakaRadiusRatiosAddBeforeMultiplication() {
        activate(735661, "Ayaka_RinRenka_2");
        activate(735662, "Ayaka_RinRenka_3");
        String name = "Avatar_Ayaka_LiquidStrike";
        var data = ABILITIES.get(name);
        assertEquals(
                2.6f,
                ability(name, data.abilitySpecials)
                        .getAbilitySpecials()
                        .getFloat("Ice_Bloom_Radius"),
                .00001f);
        assertEquals(2f, data.abilitySpecials.get("Ice_Bloom_Radius"));
    }

    @Test
    void allDeltasAndRatiosAccumulateOnceRegardlessOfRowOrder() {
        var first = modify("2", "0.1");
        var second = modify("\"-%1\"", "\"%2\"");
        activate("[" + first + "," + second + "]", 1f, .2f);
        assertEquals(14.3f, math(10f).getAbilitySpecials().getFloat("Value"), .00001f);
        put(GameData.getOpenConfigEntries(), "Math", config("[" + second + "," + first + "]"));
        assertEquals(14.3f, math(10f).getAbilitySpecials().getFloat("Value"), .00001f);
    }

    @Test
    void numericOnlyModifiersWorkWithoutAParameterArray() {
        activate("[" + modify("-1.5", "0") + "]");
        assertEquals(1f, math(2.5f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void ratioOnlyAndDeltaOnlyRowsDefaultTheirMissingOperandToZero() {
        activate(
                "[{\"$type\":\"ModifyAbility\",\"abilityName\":\"Test\",\"paramSpecial\":\"Value\",\"paramRatio\":0.5},"
                    + "{\"$type\":\"ModifyAbility\",\"abilityName\":\"Test\",\"paramSpecial\":\"Value\",\"paramDelta\":2}]");
        assertEquals(18f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void currentClientAliasesAndWrappedSignedReferencesUseTheSameMath() {
        activate(
                "[{\"$type\":\"ModifyAbility\",\"EEGHGBOCFMM\":\"Test\",\"LCFJHKJNGKL\":\"Value\","
                    + "\"JOBOBBGALMD\":{\"CBOMLBFIPJM\":\"-%1\"},\"ALMMICJIJKP\":{\"MLHKFPOMJDJ\":\"%2\"}}]",
                2f, .5f);
        assertEquals(12f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void legacyModifyAbilityAliasesAlsoSupportRatios() {
        activate(
                "[{\"$type\":\"GMOELNAHCOH\",\"NCCKLDFFDOH\":\"Test\",\"LDACNDBDKBA\":\"Value\","
                        + "\"IJFENBIJGLJ\":\"%1\",\"paramRatio\":-0.5}]",
                2f);
        assertEquals(6f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void absoluteSetAbilityVarKeepsItsExistingReplacementBehavior() {
        activate(
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"Test\",\"varName\":\"Value\",\"varValue\":\"%1\"}]",
                2f);
        assertEquals(2f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void compiledCacheRoundTripRetainsNumbersRatiosAndSignedReferences() {
        var source = config("[" + modify("2", "\"-%1\"") + "]");
        var cached = JsonUtils.decode(JsonUtils.encode(source), OpenConfigEntry.class);
        put(GameData.getOpenConfigEntries(), "Math", cached);
        activate(735661, "Math", .5f);
        assertTrue(cached.getAbilityVarSetters()[0].isModifyAbility());
        assertEquals(6f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void olderCacheEntriesStillResolveAsAbsoluteBindings() {
        var cached =
                JsonUtils.decode(
                        "{\"name\":\"Old\",\"abilityVarSetters\":[{\"abilityName\":\"Test\","
                                + "\"varName\":\"Value\",\"paramIndex\":0}]}",
                        OpenConfigEntry.class);
        put(GameData.getOpenConfigEntries(), "Old", cached);
        activate(735661, "Old", 2f);
        assertFalse(cached.getAbilityVarSetters()[0].isModifyAbility());
        assertEquals(2f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void changingParametersRecomputesFromResourcesAndPreservesRuntimeVariables() {
        activate("[" + modify("\"%1\"", "\"%2\"") + "]", 2f, .5f);
        var ability = math(10f);
        assertEquals(18f, ability.getAbilitySpecials().getFloat("Value"));
        ability.getAbilitySpecials().put("Runtime", 77f);
        for (int i = 0; i < 10; i++) assertTrue(ability.refreshTalentSpecials().isEmpty());
        assertEquals(18f, ability.getAbilitySpecials().getFloat("Value"));
        activate(735661, "Math", 4f, .2f);
        assertEquals(16.8f, ability.refreshTalentSpecials().get("Value"), .00001f);
        assertEquals(77f, ability.getAbilitySpecials().getFloat("Runtime"));
        avatar.talents.clear();
        assertEquals(10f, ability.refreshTalentSpecials().get("Value"));
        assertEquals(77f, ability.getAbilitySpecials().getFloat("Runtime"));
    }

    @Test
    void unchangedTalentBindingsDoNotOverwriteRuntimeCounters() {
        activate("[" + modify("2", "0.5") + "]");
        var ability = math(10f);
        ability.getAbilitySpecials().put("Value", 123f);
        assertTrue(ability.refreshTalentSpecials().isEmpty());
        assertEquals(123f, ability.getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void missingParametersDoNotPartiallyApplyAModifier() {
        activate("[" + modify("2", "\"%2\"") + "]", .5f);
        assertEquals(10f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void nonfiniteParametersAndOverflowNeverEnterAbilitySpecials() {
        activate("[" + modify("\"%1\"", "\"%2\"") + "]", 0f, .5f);
        GameData.getAvatarTalentDataMap().get(735661).getParamList()[0] = Float.NaN;
        assertEquals(10f, math(10f).getAbilitySpecials().getFloat("Value"));
        activate(735661, "Math", Float.MAX_VALUE, Float.MAX_VALUE);
        assertEquals(10f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void missingVariablesAreNotInventedAndSharedNamesUseEachAbilitysBaseline() {
        activate("[" + modify("2", "0.5") + "]");
        assertFalse(ability("Test", Map.of()).getAbilitySpecials().containsKey("Value"));
        assertEquals(18f, math(10f).getAbilitySpecials().getFloat("Value"));
        assertEquals(33f, math(20f).getAbilitySpecials().getFloat("Value"));
        assertEquals(
                10f,
                ability("Unrelated", Map.of("Value", 10f)).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void taggedTalentsAreNotActivatedWithoutTheirTagState() {
        String tagged =
                modify("2", "0.5")
                        .replace(
                                "\"paramDelta\"", "\"CMLJMLJBBKI\":\"Hexenzirkel\",\"paramDelta\"");
        activate("[" + tagged + "]");
        assertEquals(10f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void finiteNegativeResultsAndZeroRatiosAreNotChangedIntoReplacements() {
        activate("[" + modify("-12", "0") + "]");
        assertEquals(-2f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"%0\"",
                "\"+%1\"",
                "\"%2147483649\"",
                "\"NaN\"",
                "\"Infinity\"",
                "true",
                "[]",
                "{\"a\":\"%1\",\"b\":\"%2\"}",
                "{\"$type\":\"Missing\"}",
                "1e500"
            })
    void malformedOperandsLeaveTheResourceDefault(String operand) {
        activate("[" + modify(operand, "0") + "]", 2f);
        assertEquals(10f, math(10f).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void actualResourceCatalogPreservesNumbersRatiosAndRejectsEmptyOperands() {
        int count = 0;
        int filtered = 0;
        int numeric = 0;
        for (var config : CONFIGS.values()) {
            if (config.getAbilityVarSetters() == null) continue;
            for (var setter : config.getAbilityVarSetters()) {
                if (!setter.isModifyAbility()) continue;
                count++;
                if (!setter.isUnconditional()) filtered++;
                if (setter.getParamIndex() < 0 && setter.resolve(null) != null) numeric++;
            }
        }
        // 5,828 canonical rows and 32 legacy rows, minus one empty object operand.
        assertEquals(5859, count);
        assertTrue(
                Arrays.stream(CONFIGS.get("Alyosha_SkillUpgrade_2").getAbilityVarSetters())
                        .noneMatch(setter -> setter.getVarName().equals("Trigger_CD")));
        assertEquals(6, filtered);
        // Numeric deltas plus the three ratio-only rows; missing delta resolves to zero.
        assertEquals(108, numeric);
    }
}
