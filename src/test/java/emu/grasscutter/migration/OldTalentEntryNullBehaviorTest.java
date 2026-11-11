package emu.grasscutter.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.ability.AbilityTalentResolver;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Current Java behavior for pinned candidate rows, not a 7.2 target-resource claim. */
class OldTalentEntryNullBehaviorTest {
    private static final String CANDIDATE_SHA = "f573e89101fc47a8a395def6b840f075dd7dfb52d6df69488b9fefbe2b6ab0d4";
    private static final String CHECKOUT_SHA = "6b10e3bcf21dc0cb5f07e0abd74624af8c4fd853eb75689da9906f560841c886";
    private static final String EFFECTS = "[{\"$type\":\"AddAbility\",\"abilityName\":\"Test_Positive\"},"
            + "{\"$type\":\"UnlockTalentParam\",\"abilityName\":\"Test_Positive\",\"talentParam\":\"Enabled\"}]";
    @TempDir Path dir;
    private Int2ObjectMap<AvatarTalentData> oldTalents;
    private Int2ObjectMap<AvatarSkillDepotData> oldDepots;
    private Map<String, OpenConfigEntry> oldConfigs;
    private boolean oldLoadedAll;

    @BeforeEach void isolate() {
        oldTalents = new Int2ObjectOpenHashMap<>(GameData.getAvatarTalentDataMap());
        oldDepots = new Int2ObjectOpenHashMap<>(GameData.getAvatarSkillDepotDataMap());
        oldConfigs = new HashMap<>(GameData.getOpenConfigEntries());
        oldLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getAvatarTalentDataMap().clear();
        GameData.getAvatarSkillDepotDataMap().clear();
        GameData.getOpenConfigEntries().clear();
    }

    @AfterEach void restore() {
        GameData.getAvatarTalentDataMap().clear(); GameData.getAvatarTalentDataMap().putAll(oldTalents);
        GameData.getAvatarSkillDepotDataMap().clear(); GameData.getAvatarSkillDepotDataMap().putAll(oldDepots);
        GameData.getOpenConfigEntries().clear(); GameData.getOpenConfigEntries().putAll(oldConfigs);
        assertEquals(oldTalents, GameData.getAvatarTalentDataMap());
        assertEquals(oldDepots, GameData.getAvatarSkillDepotDataMap());
        assertEquals(oldConfigs, GameData.getOpenConfigEntries());
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test void pinnedSixRowsLoadAndExerciseCurrentConsumers() throws Exception {
        installOpenConfig();
        for (var side : new String[] {"candidate", "checkout"}) {
            loadSix(side);
            for (int id = 621; id <= 626; id++) {
                var row = GameData.getAvatarTalentDataMap().get(id);
                assertNotNull(row, side + " real onLoad " + id);
                assertEquals(side.equals("candidate") ? null : "", row.getOpenConfig());
                assertEquals(2, row.getAddProps().length);
                var avatar = avatar(id);
                assertEquals(Set.of(id), new HashSet<>(avatar.getTalentIdList()));
                avatar.recalcConstellations();
                assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
                avatar.recalcStats(false, false);
                assertTrue(avatar.getExtraAbilityEmbryos().isEmpty());
                assertEquals(0, scene(avatar).getAppliedAbilitiesCount());
                assertFalse(AbilityTalentResolver.unlocked(avatar, "Test_Positive", "Enabled"));
            }
        }
    }

    @Test void positiveAndMissingRowControlsProveConsumersAreActive() throws Exception {
        installOpenConfig();
        Files.writeString(dir.resolve("positive.json"), "[{\"talentId\":900001,\"openConfig\":\"Positive\",\"addProps\":[]},{\"talentId\":900003,\"openConfig\":\"MissingConfig\",\"addProps\":[]}]", StandardCharsets.UTF_8);
        loadTalentFile(dir.resolve("positive.json"));
        var positive = avatar(900001);
        assertEquals(Set.of(900001), new HashSet<>(positive.getTalentIdList()));
        positive.recalcConstellations();
        positive.recalcStats(false, false);
        assertEquals(Set.of("Test_Positive"), positive.getExtraAbilityEmbryos());
        var scene = scene(positive);
        assertEquals(1, scene.getAppliedAbilitiesCount());
        assertEquals(Utils.abilityHash("Test_Positive"), scene.getAppliedAbilities(0).getAbilityName().getHash());
        assertTrue(AbilityTalentResolver.unlocked(positive, "Test_Positive", "Enabled"));
        var missing = avatar(900002);
        assertNull(GameData.getAvatarTalentDataMap().get(900002));
        missing.recalcConstellations();
        missing.recalcStats(false, false);
        assertTrue(missing.getExtraAbilityEmbryos().isEmpty());
        assertEquals(0, scene(missing).getAppliedAbilitiesCount());
        assertFalse(AbilityTalentResolver.unlocked(missing, "Test_Positive", "Enabled"));

        var missingConfig = avatar(900003);
        assertEquals("MissingConfig", GameData.getAvatarTalentDataMap().get(900003).getOpenConfig());
        assertFalse(GameData.getOpenConfigEntries().containsKey("MissingConfig"));
        missingConfig.recalcConstellations();
        missingConfig.recalcStats(false, false);
        assertTrue(missingConfig.getExtraAbilityEmbryos().isEmpty());
        assertEquals(0, scene(missingConfig).getAppliedAbilitiesCount());
        assertFalse(AbilityTalentResolver.unlocked(missingConfig, "Test_Positive", "Enabled"));
    }

    @Test void syntheticEmptyKeyShowsSceneLookupButAvatarRejectsIt() throws Exception {
        installOpenConfig();
        loadSix("checkout");
        // A direct sentinel is intentionally impossible through OpenConfigLoader; it exposes
        // the otherwise invisible difference between a lookup and a pre-lookup filter.
        GameData.getOpenConfigEntries().put("", GameData.getOpenConfigEntries().get("Positive"));
        var avatar = avatar(621);
        avatar.recalcConstellations();
        avatar.recalcStats(false, false);
        assertTrue(avatar.getExtraAbilityEmbryos().isEmpty());
        assertEquals(1, scene(avatar).getAppliedAbilitiesCount());
        assertTrue(AbilityTalentResolver.unlocked(avatar, "Test_Positive", "Enabled"));
    }

    @Test void syntheticNullKeyShowsResolverLookupButSceneSkipsNull() throws Exception {
        installOpenConfig();
        loadSix("candidate");
        // The loader cannot produce this null key. It demonstrates resolver map behavior only.
        GameData.getOpenConfigEntries().put(null, GameData.getOpenConfigEntries().get("Positive"));
        var avatar = avatar(621);
        avatar.recalcConstellations();
        avatar.recalcStats(false, false);
        assertTrue(avatar.getExtraAbilityEmbryos().isEmpty());
        assertEquals(0, scene(avatar).getAppliedAbilitiesCount());
        assertTrue(AbilityTalentResolver.unlocked(avatar, "Test_Positive", "Enabled"));
    }

    @Test void pinnedSourceHitsAreIndividuallyCheckedAfterRealLoader() throws Exception {
        String reportPath = System.getProperty("d02.talent.sources");
        String sourcePath = System.getProperty("d02.talent.binroot");
        assertNotNull(reportPath, "required d02.talent.sources");
        assertNotNull(sourcePath, "required d02.talent.binroot");
        byte[] reportBytes = Files.readAllBytes(Path.of(reportPath));
        assertEquals("19be8f2bde7e36eddb7e2d844c946e8b7d78c877c35f43cda4593353ab8f2cb0", sha(reportBytes));
        var report = JsonUtils.decode(new String(reportBytes, StandardCharsets.UTF_8), JsonObject.class);
        Path root = Path.of(sourcePath).toAbsolutePath().normalize();
        var expectedFiles = new TreeSet<String>();
        for (JsonElement e : report.getAsJsonArray("sourceFiles")) {
            var file = e.getAsJsonObject();
            assertTrue(file.get("directOpenConfigDirectory").getAsBoolean());
            String name = file.get("path").getAsString();
            assertEquals(name, Path.of(name).getFileName().toString());
            assertTrue(expectedFiles.add(name));
            assertEquals(file.get("sha256").getAsString(), sha(Files.readAllBytes(root.resolve(name))));
        }
        try (var files = Files.list(root)) {
            assertEquals(expectedFiles, files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        }
        Method method = ResourceLoader.class.getDeclaredMethod("loadOpenConfig", Path.class, Path[].class);
        method.setAccessible(true);
        method.invoke(null, dir.resolve("absent-cache.json"), new Path[] {root});
        var missing = new TreeSet<String>();
        int checked = 0;
        for (JsonElement e : report.getAsJsonArray("rows")) {
            var row = e.getAsJsonObject();
            var candidate = row.getAsJsonObject("candidate");
            if (!candidate.get("state").getAsString().equals("nonempty")) continue;
            assertEquals(1, candidate.get("sourceCount").getAsInt());
            String name = candidate.get("value").getAsString();
            if (!GameData.getOpenConfigEntries().containsKey(name)) missing.add(row.get("talentId").getAsInt() + ":" + name);
            checked++;
        }
        assertEquals(720, checked);
        assertTrue(missing.size() < checked, "no positive candidate survived loader");
        System.out.println("OLD_TALENT_SOURCE_REPORT checked=" + checked + " unresolved=" + missing.size() + " rows=" + missing);
    }

    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private void installOpenConfig() throws Exception {
        Path source = Files.createDirectory(dir.resolve("source"));
        Files.writeString(source.resolve("effects.json"), "{\"\":" + EFFECTS + ",\"Positive\":" + EFFECTS + "}", StandardCharsets.UTF_8);
        Path cache = Files.writeString(dir.resolve("cache.json"), "[{\"name\":\"\",\"addAbilities\":[\"Bad\"]}]", StandardCharsets.UTF_8);
        Method method = ResourceLoader.class.getDeclaredMethod("loadOpenConfig", Path.class, Path[].class);
        method.setAccessible(true);
        method.invoke(null, cache, new Path[] {source});
        assertFalse(GameData.getOpenConfigEntries().containsKey(""));
        assertNotNull(GameData.getOpenConfigEntries().get("Positive"));
    }

    private void loadSix(String side) throws Exception {
        String property = "d02.talent." + side;
        String value = System.getProperty(property);
        assertNotNull(value, "required JVM property " + property);
        byte[] raw = Files.readAllBytes(Path.of(value));
        assertEquals(side.equals("candidate") ? CANDIDATE_SHA : CHECKOUT_SHA,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var selected = new JsonArray();
        for (JsonElement element : JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonArray.class)) {
            JsonObject row = element.getAsJsonObject();
            int id = row.get("talentId").getAsInt();
            if (id >= 621 && id <= 626) selected.add(row);
        }
        assertEquals(6, selected.size());
        Path file = Files.writeString(dir.resolve(side + ".json"), JsonUtils.encode(selected), StandardCharsets.UTF_8);
        GameData.getAvatarTalentDataMap().clear();
        loadTalentFile(file);
        assertEquals(6, GameData.getAvatarTalentDataMap().size());
    }

    private static void loadTalentFile(Path file) throws Exception {
        Method method = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        method.setAccessible(true);
        method.invoke(null, AvatarTalentData.class, file, GameData.getAvatarTalentDataMap());
    }

    private Avatar avatar(int talentId) throws Exception {
        var depot = JsonUtils.decode("{\"id\":900000,\"skills\":[],\"talents\":[" + talentId + "]}", AvatarSkillDepotData.class);
        depot.onLoad();
        GameData.getAvatarSkillDepotDataMap().put(900000, depot);
        var avatar = new Avatar();
        set(avatar, "avatarId", 900000);
        set(avatar, "skillDepotId", 900000);
        set(avatar, "skillDepot", depot);
        set(avatar, "avatarData", JsonUtils.decode("{\"id\":900000,\"hpBase\":100}", AvatarData.class));
        set(avatar, "talentIdList", new HashSet<>(Set.of(talentId)));
        set(avatar, "proudSkillList", new HashSet<Integer>());
        return avatar;
    }

    private static void set(Avatar avatar, String name, Object value) throws Exception {
        var field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(avatar, value);
    }

    private static AbilitySyncStateInfo scene(Avatar avatar) {
        var info = AbilitySyncStateInfo.newBuilder();
        PacketPlayerEnterSceneInfoNotify.buildAvatarAbilityVars(new EntityAvatar(avatar), info);
        return info.build();
    }
}
