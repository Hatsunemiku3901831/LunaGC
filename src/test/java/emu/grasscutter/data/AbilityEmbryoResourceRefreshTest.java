package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ResourceLoader.AvatarConfig;
import emu.grasscutter.data.binout.AbilityEmbryoEntry;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AbilityEmbryoResourceRefreshTest {
    @TempDir Path temporary;
    private Path cache,avatars,groups;
    private Map<String,AbilityEmbryoEntry> previousEmbryos;
    private Map<String,AvatarConfig> previousGroups;
    @BeforeAll static void initialize() throws Exception { Class.forName("emu.grasscutter.Grasscutter"); }
    @BeforeEach void paths() throws Exception {
        cache=temporary.resolve("AbilityEmbryos.json"); avatars=Files.createDirectory(temporary.resolve("Avatar")); groups=temporary.resolve("groups.json");
        previousEmbryos=new HashMap<>(GameData.getAbilityEmbryoInfo()); previousGroups=GameDepot.getPlayerAbilities();
        GameData.getAbilityEmbryoInfo().clear(); GameDepot.setPlayerAbilities(new HashMap<>());
        Files.writeString(groups,"{}");
    }
    @AfterEach void restore() {
        GameData.getAbilityEmbryoInfo().clear(); GameData.getAbilityEmbryoInfo().putAll(previousEmbryos);
        GameDepot.setPlayerAbilities(previousGroups);
    }
    private void config(String avatar,String json) throws Exception { Files.writeString(avatars.resolve("ConfigAvatar_"+avatar+".json"),json); }
    private void cached(String json) throws Exception { Files.writeString(cache,json); }
    private void load() { ResourceLoader.loadAbilityEmbryos(cache,avatars,groups); }
    private String[] abilities(String avatar) { return GameData.getAbilityEmbryoInfo().get(avatar).getAbilities(); }

    @Test void existingCacheCannotHideNewAvatarsOrNewBaseSkills() throws Exception {
        cached("[{\"name\":\"Old\",\"abilities\":[\"Stale\"]}]");
        config("Old","{\"abilities\":[{\"abilityName\":\"Updated\"},{\"abilityName\":\"Second\"}]}");
        config("New","{\"abilities\":[{\"abilityName\":\"NewSkill\"}]}"); load();
        assertArrayEquals(new String[]{"Updated","Second"},abilities("Old")); assertArrayEquals(new String[]{"NewSkill"},abilities("New"));
    }
    @Test void cachedAvatarsDoNotPreventTravelerGroupsFromLoading() throws Exception {
        cached("[{\"name\":\"Traveler\",\"abilities\":[\"Base\"]}]");
        Files.writeString(groups,"{\"PlayerPyro\":{\"abilities\":[{\"abilityName\":\"PyroSkill\"}]}}"); load();
        assertEquals("PyroSkill",GameDepot.getPlayerAbilities().get("PlayerPyro").abilities.get(0).abilityName);
    }
    @Test void resourceOrderAndDuplicatesArePreservedAndEmptyListsCanClearBaseSkills() throws Exception {
        cached("[{\"name\":\"Ordered\",\"abilities\":[\"Old\"]},{\"name\":\"Empty\",\"abilities\":[\"Old\"]}]");
        config("Ordered","{\"targetAbilities\":[{\"abilityName\":\"B\"},{\"abilityName\":\"A\"},{\"abilityName\":\"B\"}]}"); config("Empty","{\"abilities\":[]}"); load();
        assertArrayEquals(new String[]{"B","A","B"},abilities("Ordered")); assertArrayEquals(new String[0],abilities("Empty"));
    }
    @Test void cacheOnlyExtensionsRemainAvailableWithoutActivatingOtherDefinitions() throws Exception {
        cached("[{\"name\":\"Custom\",\"abilities\":[\"CustomBase\"]}]"); config("Modern","{\"abilities\":[{\"abilityName\":\"ModernBase\"}]}"); load();
        assertArrayEquals(new String[]{"CustomBase"},abilities("Custom")); assertArrayEquals(new String[]{"ModernBase"},abilities("Modern"));
    }
    @Test void invalidCacheRowsAreIsolatedFromValidFallbackRowsAndSourceFiles() throws Exception {
        cached("[null,{\"name\":\"\",\"abilities\":[\"Bad\"]},{\"name\":\"NullBody\",\"abilities\":null},{\"name\":\"BadBody\",\"abilities\":[null]},{\"name\":\"Custom\",\"abilities\":[\"Valid\"]}]");
        config("Modern","{\"abilities\":[{\"abilityName\":\"ModernBase\"}]}"); assertDoesNotThrow(this::load);
        assertEquals(Set.of("Custom","Modern"),GameData.getAbilityEmbryoInfo().keySet());
    }
    @Test void malformedCacheStillAllowsCurrentResourcesAndDoesNotModifyTheCache() throws Exception {
        cached("[ broken"); byte[] before=Files.readAllBytes(cache); config("Current","{\"abilities\":[{\"abilityName\":\"Skill\"}]}"); load();
        assertArrayEquals(new String[]{"Skill"},abilities("Current")); assertArrayEquals(before,Files.readAllBytes(cache));
    }
    @Test void invalidSourcesPreserveOnlyTheirValidCachedFallbackAndDoNotStopTheNextFile() throws Exception {
        cached("[{\"name\":\"Broken\",\"abilities\":[\"Fallback\"]},{\"name\":\"NullBody\",\"abilities\":[\"OtherFallback\"]},{\"name\":\"MissingName\",\"abilities\":[\"ThirdFallback\"]}]");
        config("Broken","broken"); config("NullBody","null"); config("MissingName","{\"abilities\":[{\"abilityName\":\"\"}]}"); config("Valid","{\"abilities\":[{\"abilityName\":\"Current\"}]}"); load();
        assertArrayEquals(new String[]{"Fallback"},abilities("Broken")); assertArrayEquals(new String[]{"OtherFallback"},abilities("NullBody")); assertArrayEquals(new String[]{"ThirdFallback"},abilities("MissingName")); assertArrayEquals(new String[]{"Current"},abilities("Valid"));
    }
    @Test void missingSourceDirectoryCanStillUseCacheAndLoadTravelerGroups() throws Exception {
        cached("[{\"name\":\"Custom\",\"abilities\":[\"Base\"]}]"); Files.writeString(groups,"{\"Traveler\":{\"abilities\":[]}}");
        ResourceLoader.loadAbilityEmbryos(cache,temporary.resolve("Missing"),groups);
        assertArrayEquals(new String[]{"Base"},abilities("Custom")); assertTrue(GameDepot.getPlayerAbilities().containsKey("Traveler"));
    }
    @Test void failedCatalogReloadPreservesTheExistingValidCatalogButStillLoadsGroups() throws Exception {
        GameData.getAbilityEmbryoInfo().put("Previous",new AbilityEmbryoEntry("Previous",new String[]{"Base"}));
        Files.writeString(groups,"{\"Traveler\":{\"abilities\":[]}}"); load();
        assertArrayEquals(new String[]{"Base"},abilities("Previous")); assertTrue(GameDepot.getPlayerAbilities().containsKey("Traveler"));
    }
    @Test void successfulCatalogReloadRemovesEarlierMemoryOnlyEntries() throws Exception {
        GameData.getAbilityEmbryoInfo().put("Removed",new AbilityEmbryoEntry("Removed",new String[]{"Old"}));
        config("Current","{\"abilities\":[{\"abilityName\":\"New\"}]}"); load();
        assertEquals(Set.of("Current"),GameData.getAbilityEmbryoInfo().keySet());
    }
    @Test void failedTravelerGroupReadPreservesItsPreviousValidMap() throws Exception {
        var prior=JsonUtils.decode("{\"abilities\":[{\"abilityName\":\"Prior\"}]}",AvatarConfig.class);
        GameDepot.setPlayerAbilities(new HashMap<>(Map.of("Previous",prior))); config("Current","{\"abilities\":[]}"); Files.writeString(groups,"broken"); load();
        assertSame(prior,GameDepot.getPlayerAbilities().get("Previous")); assertTrue(GameData.getAbilityEmbryoInfo().containsKey("Current"));
    }
    @Test void nullOrInvalidTravelerGroupsCannotReplaceAValidMapAndEmptyGroupsCanClearIt() throws Exception {
        var prior=JsonUtils.decode("{\"abilities\":[]}",AvatarConfig.class); GameDepot.setPlayerAbilities(new HashMap<>(Map.of("Previous",prior)));
        cached("[{\"name\":\"Custom\",\"abilities\":[\"Base\"]}]");
        for(var json:List.of("null","{\"Bad\":null}","{\"Bad\":{\"abilities\":[null]}}")) { Files.writeString(groups,json); load(); assertSame(prior,GameDepot.getPlayerAbilities().get("Previous")); }
        Files.writeString(groups,"{}"); load(); assertTrue(GameDepot.getPlayerAbilities().isEmpty());
    }
    @Test void duplicateCacheNamesUseTheLastValidEntryWithoutLettingAnInvalidOneOverwriteIt() throws Exception {
        cached("[{\"name\":\"Custom\",\"abilities\":[\"First\"]},{\"name\":\"Custom\",\"abilities\":[\"Second\"]},{\"name\":\"Custom\",\"abilities\":null}]"); load(); assertArrayEquals(new String[]{"Second"},abilities("Custom"));
    }
    @Test void actualAvatarAndTravelerResourcesWinOverAnOldCacheWithoutDynamicSkillInjection() throws Exception {
        cached("[{\"name\":\"Nahida\",\"abilities\":[\"Avatar_Nahida_BossRush_SpecialArt\"]}]");
        var realAvatars=Path.of("resources/BinOutput/Avatar"); var realGroups=Path.of("resources/BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json");
        ResourceLoader.loadAbilityEmbryos(cache,realAvatars,realGroups);
        try(var files=Files.newDirectoryStream(realAvatars,"ConfigAvatar_*.json")) {
            for(var file:files) {
                var config=JsonUtils.loadToClass(file,AvatarConfig.class); if(config==null || config.abilities==null) continue;
                var name=file.getFileName().toString().substring("ConfigAvatar_".length()).replaceFirst("\\.json$","");
                assertArrayEquals(config.abilities.stream().map(Object::toString).toArray(String[]::new),abilities(name),name);
            }
        }
        assertFalse(Arrays.asList(abilities("Nahida")).contains("Avatar_Nahida_BossRush_SpecialArt"));
        var expected=JsonUtils.loadToMap(realGroups,String.class,AvatarConfig.class);
        assertEquals(expected.keySet(),GameDepot.getPlayerAbilities().keySet());
        expected.forEach((name,config)->assertEquals(JsonUtils.encode(config),JsonUtils.encode(GameDepot.getPlayerAbilities().get(name)),name));
    }
}
