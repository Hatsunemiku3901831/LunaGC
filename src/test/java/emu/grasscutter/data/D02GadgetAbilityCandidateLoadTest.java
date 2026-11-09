package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Candidate Java loader contract only; unknown fields and target compatibility remain unverified. */
class D02GadgetAbilityCandidateLoadTest {
    private static final String KEY = "Weapon_Sword_Metal";
    private static final String METAL = "Avatar_Weapon_Metal";
    private static final Set<String> NAMES = Set.of(METAL, "Avatar_Weapon_Wood_Normal", "Avatar_Weapon_Wood_Hard");
    private static byte[] configInput;
    private static byte[] abilityInput;

    @TempDir Path directory;
    private Path configPath;
    private Path abilityPath;
    private Map<String, ConfigEntityGadget> previousConfigs;
    private Map<String, AbilityData> previousAbilities;
    private Int2ObjectMap<String> previousHashes;
    private boolean previousLoadedAll;

    @BeforeAll
    static void initializeAndPinInputs() throws Exception {
        Grasscutter.getLogger();
        configInput = pinned("d02.gadgetAbility.config", 361168,
                "700d2c4cb1ea11d5b3036002159326250b3817c127de087b697515321eda75a8");
        abilityInput = pinned("d02.gadgetAbility.abilities", 3745,
                "b3f7f417c47ff4f2d726728450ca2092e07bc060458d874f7b919c57c2c564d2");
    }

    @BeforeEach
    void isolateAndCopy() throws Exception {
        previousConfigs = new HashMap<>(GameData.getGadgetConfigData());
        previousAbilities = new HashMap<>(GameData.getAbilityDataMap());
        previousHashes = new Int2ObjectOpenHashMap<>(GameData.getAbilityHashes());
        previousLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getGadgetConfigData().clear();
        GameData.getAbilityDataMap().clear();
        GameData.getAbilityHashes().clear();
        configPath = directory.resolve("R/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json");
        abilityPath = directory.resolve("R/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json");
        Files.createDirectories(configPath.getParent());
        Files.createDirectories(abilityPath.getParent());
        Files.write(configPath, configInput);
        Files.write(abilityPath, abilityInput);
        Files.createDirectories(directory.resolve("S"));
        Files.createDirectories(directory.resolve("D"));
    }

    @AfterEach
    void restore() {
        GameData.getGadgetConfigData().clear();
        GameData.getGadgetConfigData().putAll(previousConfigs);
        GameData.getAbilityDataMap().clear();
        GameData.getAbilityDataMap().putAll(previousAbilities);
        GameData.getAbilityHashes().clear();
        GameData.getAbilityHashes().putAll(previousHashes);
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void realLoadersConnectConfigToRegisteredAbilities() throws Exception {
        loadConfig();
        loadAbilities();
        assertEquals(34, GameData.getGadgetConfigData().size());
        var config = GameData.getGadgetConfigData().get(KEY);
        assertNotNull(config);
        assertEquals(1, config.getAbilities().size());
        var reference = config.getAbilities().iterator().next();
        assertEquals("Metal", reference.getAbilityID());
        assertEquals(METAL, reference.getAbilityName());
        var property = config.getCombat().getProperty();
        assertEquals(Float.floatToRawIntBits(9999999f), Float.floatToRawIntBits(property.getHP()));
        assertTrue(property.isInvincible());
        assertTrue(property.isLockHP());
        assertTrue(property.isUseCreatorProperty());
        assertEquals(0x3f847ae140000000L,
                Double.doubleToRawLongBits(config.getCombat().getDie().getDieForceDisappearTime()));
        assertEquals(NAMES, GameData.getAbilityDataMap().keySet());
        assertEquals(3, GameData.getAbilityHashes().size());
        for (String name : NAMES) {
            assertEquals(name, GameData.getAbilityHashes().get(Utils.abilityHash(name)));
            assertEquals(name, GameData.getAbilityDataMap().get(name).abilityName);
            assertSame(GameData.getAbilityDataMap().get(name), GameData.getAbilityData(name));
        }
        assertSame(GameData.getAbilityDataMap().get(METAL), GameData.getAbilityData(reference.getAbilityName()));
    }

    @Test
    void rawFieldsExposeCurrentJavaLossAndUnknownAbilityObject() throws Exception {
        loadConfig();
        loadAbilities();
        JsonObject rawConfig = JsonParser.parseString(new String(configInput, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(KEY);
        assertEquals(999999, rawConfig.getAsJsonObject("combat").getAsJsonObject("property").get("defense").getAsInt());
        assertTrue(rawConfig.get("common").isJsonObject());
        var loaded = GameData.getGadgetConfigData().get(KEY);
        assertEquals(0f, loaded.getCombat().getProperty().getDefence());
        assertNull(loaded.getConfigCommon());
        JsonObject metal = findMetalDefault(abilityInput);
        JsonObject unknown = metal.getAsJsonObject("LKLPDIMKLKI");
        assertEquals(Set.of("elementDurability", "minus_one", "IAKPBCLBNKF", "isLimitedProperties"), unknown.keySet());
        assertEquals(100, unknown.get("elementDurability").getAsInt());
        assertEquals(-1, unknown.get("minus_one").getAsInt());
        assertEquals(-1, unknown.get("IAKPBCLBNKF").getAsInt());
        assertTrue(unknown.get("isLimitedProperties").getAsBoolean());
        assertThrows(NoSuchFieldException.class, () -> AbilityData.class.getDeclaredField("LKLPDIMKLKI"));
        // Registration does not establish that this unknown object is optional or the ability is empty.
        assertNotNull(GameData.getAbilityData(METAL));
    }

    @Test
    void missingKeysOnlyShowCurrentJavaLookupBehavior() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        config.remove(KEY);
        Files.writeString(configPath, config.toString());
        JsonArray abilities = JsonParser.parseString(Files.readString(abilityPath)).getAsJsonArray();
        for (var item : abilities) {
            JsonObject value = item.getAsJsonObject().getAsJsonObject("Default");
            if (METAL.equals(value.get("abilityName").getAsString())) value.remove("abilityName");
        }
        Files.writeString(abilityPath, abilities.toString());
        loadConfig();
        loadAbilities();
        assertEquals(33, GameData.getGadgetConfigData().size());
        assertNull(GameData.getGadgetConfigData().get(KEY));
        assertEquals(2, GameData.getAbilityDataMap().size());
        assertNull(GameData.getAbilityData(METAL));
        assertNull(GameData.getAbilityHashes().get(Utils.abilityHash(METAL)));
        // Missing lookup results do not authorize omission in target resources.
    }

    private static byte[] pinned(String property, int size, String sha256) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        byte[] bytes = Files.readAllBytes(Path.of(value));
        assertEquals(size, bytes.length, property);
        assertEquals(sha256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), property);
        return bytes;
    }

    private static JsonObject findMetalDefault(byte[] bytes) {
        JsonArray array = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray();
        JsonObject result = null;
        for (var item : array) {
            JsonObject value = item.getAsJsonObject().getAsJsonObject("Default");
            if (METAL.equals(value.get("abilityName").getAsString())) {
                assertNull(result);
                result = value;
            }
        }
        assertNotNull(result);
        return result;
    }

    private void loadConfig() throws Exception {
        var method = ResourceLoader.class.getDeclaredMethod("loadConfigMapFile", Map.class, Path.class, Class.class);
        method.setAccessible(true);
        method.invoke(null, GameData.getGadgetConfigData(), configPath, ConfigEntityGadget.class);
    }

    private void loadAbilities() throws Exception {
        var method = ResourceLoader.class.getDeclaredMethod("loadAbilityModifiers", Path.class);
        method.setAccessible(true);
        method.invoke(null, abilityPath);
    }
}
