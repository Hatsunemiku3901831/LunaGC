package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
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

/** Only the pinned 11101 fragments and controlled Java affix behavior. */
class D02BirthItemAffixResourceContractTest {
    private static JsonObject itemEvidence;
    private static JsonObject equipmentEvidence;

    @TempDir Path directory;
    private Int2ObjectMap<ItemData> previousItems;
    private boolean previousLoadedAll;

    @BeforeAll
    static void loadPinnedInputs() throws Exception {
        Grasscutter.getLogger();
        itemEvidence = pinnedReport(
                "d02.item.affix",
                "9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2");
        equipmentEvidence = pinnedReport(
                "d02.item.equipment",
                "50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc");
        assertFalse(itemEvidence.get("gadgetIdAliasApproved").getAsBoolean());
        assertFalse(itemEvidence.get("actualPositiveAffixReferenceLoaded").getAsBoolean());
        assertFalse(itemEvidence.get("completeItemInputRow").getAsBoolean());
        assertFalse(itemEvidence.get("javaResourcesLoaded").getAsBoolean());
        assertFalse(itemEvidence.get("resourceReady").getAsBoolean());
        assertFalse(itemEvidence.get("d02Complete").getAsBoolean());
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
    void pinnedZeroSlotsSurviveRealLoadingAndProduceEmptyGameItemAffixes() throws Exception {
        JsonObject fields = approvedFields();
        ItemData loaded = load(fields, "WeaponExcelConfigData.json");
        assertArrayEquals(new int[] {0, 0}, loaded.getSkillAffix());
        assertEquals(List.of(0, 0), List.of(
                fields.getAsJsonArray("skillAffix").get(0).getAsInt(),
                fields.getAsJsonArray("skillAffix").get(1).getAsInt()));
        assertEquals(2, fields.getAsJsonArray("skillAffix").size());
        assertEquals(2, loaded.getWeaponProperties().length);
        assertEquals("FIGHT_PROP_NONE", loaded.getWeaponProperties()[1].getPropType().name());
        assertEquals(Float.floatToRawIntBits(0f),
                Float.floatToRawIntBits(loaded.getWeaponProperties()[1].getInitValue()));

        GameItem item = new GameItem(11101);
        assertSame(loaded, item.getItemData());
        assertEquals(11101, item.getItemId());
        assertEquals(1, item.getCount());
        assertEquals(1, item.getLevel());
        assertEquals(List.of(), item.getAffixes());
    }

    @Test
    void missingNullAndEmptyAffixesHaveEmptyGameItemListsInCurrentJava() throws Exception {
        for (String variant : List.of("missing", "null", "empty")) {
            JsonObject fields = approvedFields();
            switch (variant) {
                case "missing" -> fields.remove("skillAffix");
                case "null" -> fields.add("skillAffix", JsonNull.INSTANCE);
                case "empty" -> fields.add("skillAffix", new JsonArray());
                default -> throw new AssertionError(variant);
            }
            GameData.getItemDataMap().clear();
            ItemData loaded = load(fields, variant + ".json");
            if (variant.equals("empty")) assertArrayEquals(new int[0], loaded.getSkillAffix());
            else assertNull(loaded.getSkillAffix());
            GameItem item = new GameItem(11101);
            assertSame(loaded, item.getItemData());
            assertEquals(List.of(), item.getAffixes(), variant);
        }
    }

    @Test
    void controlledPositiveAffixesAreFilteredInOrderInCurrentJava() throws Exception {
        JsonObject fields = approvedFields();
        JsonArray controlled = new JsonArray();
        for (int value : new int[] {0, 111501, -1, 222202, 111501, 0}) controlled.add(value);
        fields.add("skillAffix", controlled);
        ItemData loaded = load(fields, "controlled-positive.json");
        assertArrayEquals(new int[] {0, 111501, -1, 222202, 111501, 0}, loaded.getSkillAffix());
        GameItem item = new GameItem(11101);
        assertSame(loaded, item.getItemData());
        assertEquals(List.of(111501, 222202, 111501), item.getAffixes());
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
        JsonObject affix = weaponFragment(itemEvidence);
        JsonObject equipment = weaponFragment(equipmentEvidence);
        assertTrue(affix.get("fragmentOnly").getAsBoolean());
        assertTrue(equipment.get("fragmentOnly").getAsBoolean());
        assertFalse(affix.get("completeItemInputRow").getAsBoolean());
        assertFalse(affix.get("gadgetIdAliasApproved").getAsBoolean());
        assertEquals(affix.getAsJsonObject("source"), equipment.getAsJsonObject("source"));

        JsonObject affixFields = affix.getAsJsonObject("javaFields");
        JsonObject fields = equipment.getAsJsonObject("javaFields").deepCopy();
        assertEquals(Set.of("id", "skillAffix"), affixFields.keySet());
        assertEquals(Set.of("id", "itemType", "weaponPromoteId", "weaponProp"), fields.keySet());
        assertEquals(11101, affixFields.get("id").getAsInt());
        assertEquals(11101, fields.get("id").getAsInt());
        JsonArray slots = affixFields.getAsJsonArray("skillAffix");
        assertEquals(2, slots.size());
        assertEquals(0, slots.get(0).getAsInt());
        assertEquals(0, slots.get(1).getAsInt());
        fields.add("skillAffix", slots.deepCopy());
        return fields;
    }

    private static JsonObject weaponFragment(JsonObject report) {
        JsonObject match = null;
        for (JsonElement element : report.getAsJsonArray("javaProjectionFragments")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("Weapon".equals(candidate.get("table").getAsString())
                    && candidate.get("key").getAsInt() == 11101) {
                assertNull(match, "duplicate 11101 fragment");
                match = candidate;
            }
        }
        assertNotNull(match, "missing 11101 fragment");
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
