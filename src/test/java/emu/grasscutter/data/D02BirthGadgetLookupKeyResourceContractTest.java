package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The approved lookup key alone is not a Gadget configuration or entity contract. */
class D02BirthGadgetLookupKeyResourceContractTest {
    private static final String CONFIG_SHA =
            "10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd";
    private static JsonObject approvedFields;

    @TempDir Path directory;
    private Int2ObjectMap<GadgetData> previousGadgets;
    private boolean previousLoadedAll;

    @BeforeAll
    static void loadPinnedInputs() throws Exception {
        Grasscutter.getLogger();
        JsonObject approval = pinnedReport(
                "d02.gadget.lookup-key",
                "f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398");
        JsonObject config = pinnedReport("d02.gadget.config-key", CONFIG_SHA);
        approvedFields = validateApproval(approval, config);
    }

    @BeforeEach
    void isolateGameData() {
        previousGadgets = new Int2ObjectOpenHashMap<>(GameData.getGadgetDataMap());
        previousLoadedAll = ResourceLoader.isLoadedAll();
        GameData.getGadgetDataMap().clear();
    }

    @AfterEach
    void restoreGameData() {
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(previousGadgets);
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
    }

    @Test
    void approvedKeySurvivesRealResourceLoading() throws Exception {
        GadgetData loaded = load(approvedFields.deepCopy(), "GadgetExcelConfigData.json");
        assertEquals(50011101, loaded.getId());
        assertEquals("Weapon_Sword_Metal", loaded.getJsonName());
    }

    @Test
    void missingKeyIsNullOnlyAsCurrentJavaControl() throws Exception {
        JsonObject fields = approvedFields.deepCopy();
        fields.remove("jsonName");
        GadgetData loaded = load(fields, "missing-json-name.json");
        assertNull(loaded.getJsonName());
    }

    private GadgetData load(JsonObject fields, String name) throws Exception {
        JsonArray rows = new JsonArray();
        rows.add(fields);
        Path path = Files.writeString(directory.resolve(name), rows.toString());
        ResourceLoader.loadFromResource(GadgetData.class, path, GameData.getGadgetDataMap());
        assertEquals(Set.of(50011101), GameData.getGadgetDataMap().keySet());
        return GameData.getGadgetDataMap().get(50011101);
    }

    private static JsonObject validateApproval(JsonObject approval, JsonObject config) {
        assertTrue(approval.get("lookupKeyFragmentApproved").getAsBoolean());
        assertTrue(approval.get("priorFrozenAliasFalseRetained").getAsBoolean());
        assertEquals(CONFIG_SHA, approval.get("nativeRoutingSourceSHA256").getAsString());
        assertFalse(config.get("javaJsonNameAliasApproved").getAsBoolean());
        assertTrue(config.get("sourceConfigurationKeyRoutingVerified").getAsBoolean());
        assertFalseFields(approval, "completeGadgetInputRow", "gadgetConfigurationApproved",
                "configurationSourceBytesVerified", "defaultComparerExecuted",
                "nativeConfigurationHitVerified", "abilityInputsApproved",
                "naturalWeaponFactoryExecuted", "entityLifecycleExecuted", "javaResourcesLoaded",
                "activeRevisionVerified", "resourceReady", "d02Complete");
        assertFalseFields(config, "completeGadgetInputRow", "configurationOwnerLookupExecuted",
                "configurationSourceBytesVerified", "defaultComparerExecuted",
                "finalConfigurationLookupExecuted", "field50AliasApproved", "fullFactoryExecuted",
                "nativeConfigurationHitVerified", "opaque25270ValueInstalled", "abilityInputsApproved",
                "javaResourcesLoaded", "activeRevisionVerified", "resourceReady", "d02Complete");

        JsonArray fragments = approval.getAsJsonArray("javaProjectionFragments");
        assertEquals(1, fragments.size());
        JsonObject fragment = fragments.get(0).getAsJsonObject();
        assertEquals("GadgetExcelConfigData", fragment.get("table").getAsString());
        assertEquals(50011101, fragment.get("id").getAsInt());
        assertTrue(fragment.get("fragmentOnly").getAsBoolean());
        assertTrue(fragment.get("sourceLookupKeyOnly").getAsBoolean());
        assertFalse(fragment.get("completeDeclaredJavaInputRow").getAsBoolean());
        JsonObject fields = fragment.getAsJsonObject("fields");
        assertEquals(Set.of("id", "jsonName"), fields.keySet());
        assertEquals(50011101, fields.get("id").getAsInt());
        assertEquals("Weapon_Sword_Metal", fields.get("jsonName").getAsString());

        JsonObject nativeField = approval.getAsJsonObject("nativeField");
        JsonObject row = findBy(config.getAsJsonArray("rows"), "key", "50011101");
        JsonObject value = findBy(row.getAsJsonArray("values"), "owner", "40472",
                "field", "index", "199649");
        JsonObject field = value.getAsJsonObject("field");
        assertEquals(40472, nativeField.get("owner").getAsInt());
        assertEquals(field.get("index"), nativeField.get("fieldIndex"));
        assertEquals(field.get("rawOffset"), nativeField.get("offset"));
        assertEquals(field.getAsJsonObject("type").get("kind"), nativeField.get("kind"));
        assertEquals(config.getAsJsonObject("staticContract").getAsJsonObject("fields")
                .getAsJsonObject("field58"), field);
        assertEquals(nativeField.get("source"), row.get("source"));
        assertTrue(row.get("sourceLogicalReadoutEqual").getAsBoolean());
        assertEquals(nativeField.get("value"), value.get("value"));
        assertEquals(fields.get("jsonName"), value.get("value"));

        JsonObject nativeCase = findBy(config.getAsJsonArray("cases"), "name",
                approval.get("nativeRoutingCase").getAsString());
        assertEquals(row.get("source"), nativeCase.get("source"));
        assertEquals(row.get("key"), nativeCase.get("sourceKey"));
        assertEquals(row.get("pointer"), nativeCase.get("rowPointer"));
        assertEquals(value.get("value"), nativeCase.get("selectedText"));
        assertEquals(nativeCase.get("originalField58Pointer"), nativeCase.get("suppliedField58Pointer"));
        assertTrue(nativeCase.get("independentFactorySlice").getAsBoolean());
        assertTrue(nativeCase.get("stoppedBeforeUnprovenComparer").getAsBoolean());
        assertFalseFields(nativeCase, "configurationHitVerified", "fullFactoryExecuted");

        JsonObject domain = approval.getAsJsonObject("staticDomainWitness");
        assertFalse(domain.get("executed").getAsBoolean());
        JsonObject factory = domain.getAsJsonObject("worldFactory");
        JsonObject sourceFactory = config.getAsJsonObject("nativeSources")
                .getAsJsonObject(factory.get("entry").getAsString());
        assertEquals(factory.get("sha256"), sourceFactory.get("sha256"));
        assertEquals(factory.get("end"), sourceFactory.get("end"));
        assertEquals(25270, domain.get("configurationType").getAsInt());
        assertEquals(28174, domain.get("entityType").getAsInt());
        JsonObject entityField = domain.getAsJsonObject("entityField");
        assertEquals(138419, entityField.get("index").getAsInt());
        assertEquals("0x2b0", entityField.get("rawOffset").getAsString());
        assertEquals(domain.getAsJsonObject("configurationBase").get("typeDefinition"),
                entityField.getAsJsonObject("type").get("typeDefinition"));
        assertEquals(37853, domain.getAsJsonObject("configurationBase")
                .get("typeDefinition").getAsInt());
        JsonObject cacheFields = config.getAsJsonObject("staticContract").getAsJsonObject("fields");
        JsonArray keyToAsset = cacheFields.getAsJsonObject("keyToAsset").getAsJsonObject("type")
                .getAsJsonArray("arguments");
        JsonArray assetToOwner = cacheFields.getAsJsonObject("assetToOwner").getAsJsonObject("type")
                .getAsJsonArray("arguments");
        JsonArray ownerToConfig = cacheFields.getAsJsonObject("ownerToConfig").getAsJsonObject("type")
                .getAsJsonArray("arguments");
        assertEquals("string", keyToAsset.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("uint64", keyToAsset.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals("uint64", assetToOwner.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals(17591, assetToOwner.get(1).getAsJsonObject().get("typeDefinition").getAsInt());
        assertEquals("string", ownerToConfig.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals(25270, ownerToConfig.get(1).getAsJsonObject().get("typeDefinition").getAsInt());
        return fields.deepCopy();
    }

    private static void assertFalseFields(JsonObject object, String... names) {
        for (String name : names) assertFalse(object.get(name).getAsBoolean(), name);
    }

    private static JsonObject findBy(JsonArray items, String key, String expected, String... nested) {
        JsonObject match = null;
        for (JsonElement item : items) {
            JsonObject candidate = item.getAsJsonObject();
            if (!expected.equals(candidate.get(key).getAsString())) continue;
            if (nested.length != 0 && !nested[2].equals(candidate.getAsJsonObject(nested[0])
                    .get(nested[1]).getAsString())) continue;
            assertNull(match, "duplicate " + key + "=" + expected);
            match = candidate;
        }
        assertNotNull(match, "missing " + key + "=" + expected);
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
