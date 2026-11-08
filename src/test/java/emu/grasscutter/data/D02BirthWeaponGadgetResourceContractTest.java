package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Approved birth weapon fragments, not a Gadget configuration or entity contract. */
class D02BirthWeaponGadgetResourceContractTest {
    private static JsonObject bridgeEvidence;
    private static JsonObject equipmentEvidence;
    private static JsonObject affixEvidence;

    @TempDir Path directory;
    private Int2ObjectMap<ItemData> previousItems;
    private boolean previousLoadedAll;

    @BeforeAll
    static void loadPinnedInputs() throws Exception {
        Grasscutter.getLogger();
        bridgeEvidence = pinnedReport(
                "d02.gadget.bridge",
                "51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880");
        equipmentEvidence = pinnedReport(
                "d02.gadget.equipment",
                "50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc");
        affixEvidence = pinnedReport(
                "d02.gadget.affix",
                "9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2");
        assertTrue(bridgeEvidence.get("gadgetIdAliasApproved").getAsBoolean());
        assertFalse(bridgeEvidence.get("completeItemInputRow").getAsBoolean());
        assertFalse(bridgeEvidence.get("fullFactoryExecuted").getAsBoolean());
        assertFalse(bridgeEvidence.get("naturalCodexFlowExecuted").getAsBoolean());
        assertFalse(bridgeEvidence.get("entityLifecycleExecuted").getAsBoolean());
        assertFalse(bridgeEvidence.get("javaResourcesLoaded").getAsBoolean());
        assertFalse(bridgeEvidence.get("resourceReady").getAsBoolean());
        assertFalse(bridgeEvidence.get("d02Complete").getAsBoolean());
    }

    @BeforeEach
    void isolateGameData() {
        previousItems = new Int2ObjectOpenHashMap<>(GameData.getItemDataMap());
        previousLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getItemDataMap().clear();
    }

    @AfterEach
    void restoreGameData() {
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(previousItems);
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void approvedGadgetIdSurvivesRealLoadingAndGameItemBinding() throws Exception {
        JsonObject fields = approvedFields();
        ItemData loaded = load(fields, "WeaponExcelConfigData.json");
        assertEquals(50011101, loaded.getGadgetId());
        assertArrayEquals(new int[] {0, 0}, loaded.getSkillAffix());
        assertEquals(2, loaded.getWeaponProperties().length);
        assertEquals("FIGHT_PROP_NONE", loaded.getWeaponProperties()[1].getPropType().name());
        assertEquals(0, Float.floatToRawIntBits(loaded.getWeaponProperties()[1].getInitValue()));
        GameItem item = new GameItem(11101);
        assertSame(loaded, item.getItemData());
        assertEquals(1, item.getCount());
        assertEquals(1, item.getLevel());
        assertEquals(List.of(), item.getAffixes());
    }

    @Test
    void missingGadgetIdIsZeroOnlyAsCurrentJavaControl() throws Exception {
        JsonObject fields = approvedFields();
        fields.remove("gadgetId");
        ItemData loaded = load(fields, "missing-gadget.json");
        assertEquals(0, loaded.getGadgetId());
        GameItem item = new GameItem(11101);
        assertSame(loaded, item.getItemData());
        assertEquals(List.of(), item.getAffixes());
    }

    private ItemData load(JsonObject fields, String name) throws Exception {
        JsonArray rows = new JsonArray();
        rows.add(fields);
        Path path = Files.writeString(directory.resolve(name), rows.toString());
        ResourceLoader.loadFromResource(ItemData.class, path, GameData.getItemDataMap());
        assertEquals(Set.of(11101), GameData.getItemDataMap().keySet());
        return GameData.getItemDataMap().get(11101);
    }

    private static JsonObject approvedFields() {
        JsonObject bridge = weaponFragment(bridgeEvidence);
        JsonObject equipment = weaponFragment(equipmentEvidence);
        JsonObject affix = weaponFragment(affixEvidence);
        assertTrue(bridge.get("fragmentOnly").getAsBoolean());
        assertTrue(equipment.get("fragmentOnly").getAsBoolean());
        assertTrue(affix.get("fragmentOnly").getAsBoolean());
        assertFalse(bridge.get("completeDeclaredJavaInputRow").getAsBoolean());
        assertFalse(bridge.get("gadgetConfigurationApproved").getAsBoolean());
        assertFalse(bridge.get("abilityInputsApproved").getAsBoolean());
        JsonObject source = bridge.getAsJsonObject("fieldSource").getAsJsonObject("source");
        assertEquals(source, equipment.getAsJsonObject("source"));
        assertEquals(source, affix.getAsJsonObject("source"));
        JsonObject fieldSource = bridge.getAsJsonObject("fieldSource");
        assertEquals(88968, fieldSource.get("owner").getAsInt());
        assertEquals(442107, fieldSource.get("fieldIndex").getAsInt());
        assertEquals("0x2c", fieldSource.get("offset").getAsString());
        assertEquals(58209, fieldSource.get("type").getAsInt());
        assertEquals("f294755f", fieldSource.get("encodedBits").getAsString());

        JsonObject gadget = bridgeEvidence.getAsJsonObject("gadget");
        assertEquals(50011101, gadget.get("key").getAsInt());
        assertEquals(bridge.getAsJsonObject("gadgetSource"), gadget.getAsJsonObject("source"));
        JsonObject nativeCase = originalNativeCase();
        assertEquals(source, nativeCase.getAsJsonObject("source"));
        assertEquals(11101, nativeCase.get("weaponId").getAsInt());
        assertEquals(50011101, nativeCase.get("decodedId").getAsInt());
        assertEquals("5f7594f2", nativeCase.get("originalEncodedWord").getAsString());
        assertTrue(nativeCase.get("actualParsedGadgetSamePointer").getAsBoolean());
        assertTrue(nativeCase.get("selectedCacheOnly").getAsBoolean());
        assertFalse(nativeCase.get("expectedMissing").getAsBoolean());
        assertFalse(nativeCase.get("continuousNaturalFactory").getAsBoolean());

        JsonObject fields = equipment.getAsJsonObject("javaFields").deepCopy();
        JsonObject gadgetFields = bridge.getAsJsonObject("javaFields");
        JsonObject affixFields = affix.getAsJsonObject("javaFields");
        assertEquals(Set.of("id", "itemType", "weaponPromoteId", "weaponProp"), fields.keySet());
        assertEquals(Set.of("id", "gadgetId"), gadgetFields.keySet());
        assertEquals(Set.of("id", "skillAffix"), affixFields.keySet());
        assertEquals(11101, fields.get("id").getAsInt());
        assertEquals(11101, gadgetFields.get("id").getAsInt());
        assertEquals(11101, affixFields.get("id").getAsInt());
        assertEquals(50011101, gadgetFields.get("gadgetId").getAsInt());
        JsonArray slots = affixFields.getAsJsonArray("skillAffix");
        assertEquals(2, slots.size());
        assertEquals(0, slots.get(0).getAsInt());
        assertEquals(0, slots.get(1).getAsInt());
        fields.add("gadgetId", gadgetFields.get("gadgetId").deepCopy());
        fields.add("skillAffix", slots.deepCopy());
        return fields;
    }

    private static JsonObject weaponFragment(JsonObject report) {
        JsonObject match = null;
        for (JsonElement element : report.getAsJsonArray("javaProjectionFragments")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("Weapon".equals(candidate.get("table").getAsString())
                    && candidate.get("key").getAsInt() == 11101) {
                assertNull(match, "duplicate 11101 Weapon fragment");
                match = candidate;
            }
        }
        assertNotNull(match, "missing 11101 Weapon fragment");
        return match;
    }

    private static JsonObject originalNativeCase() {
        JsonObject match = null;
        for (JsonElement element : bridgeEvidence.getAsJsonArray("cases")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("original-11101".equals(candidate.get("name").getAsString())) {
                assertNull(match, "duplicate original-11101 case");
                match = candidate;
            }
        }
        assertNotNull(match, "missing original-11101 case");
        return match;
    }

    private static JsonObject pinnedReport(String property, String expectedSha256) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        byte[] raw = Files.readAllBytes(Path.of(value));
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        assertEquals(expectedSha256, actual, "input SHA-256 for " + property);
        return JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
    }
}
