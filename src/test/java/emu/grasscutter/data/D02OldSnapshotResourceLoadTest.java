package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure file/onLoad diagnostics. Never reads a datastore, creates Player, or grants full ready. */
class D02OldSnapshotResourceLoadTest {
    @TempDir Path temporary;

    @Test
    void fixedBundleLoadsEverySelectedRowBeforeAnyPlayerHydration() throws Exception {
        Grasscutter.getConfig();
        String selected = System.getenv("LUNAGC_OLD_RESOURCE_BUNDLE");
        assertNotNull(selected, "LUNAGC_OLD_RESOURCE_BUNDLE must identify an immutable bundle");
        Path root = Path.of(selected);
        byte[] manifestBytes = Files.readAllBytes(root.resolve("D02-OLD-RESOURCE-MANIFEST.json"));
        assertEquals(System.getenv("LUNAGC_OLD_RESOURCE_MANIFEST_SHA256"), sha(manifestBytes));
        var manifest = JsonUtils.decode(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        assertFalse(manifest.get("ready").getAsBoolean());
        for (var output : manifest.getAsJsonObject("outputs").entrySet())
            assertEquals(output.getValue().getAsString(), sha(Files.readAllBytes(root.resolve(output.getKey()))), output.getKey());
        assertFalse(ResourceLoader.isLoadedAll());
        ResourceLoader.loadAbilityEmbryos(temporary.resolve("AbilityEmbryos.json"),
                root.resolve("BinOutput/Avatar"), root.resolve("BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json"));
        ResourceLoader.loadOpenConfig(temporary.resolve("OpenConfig.json"),
                root.resolve("BinOutput/Talent/AvatarTalents"), root.resolve("BinOutput/Talent/EquipTalents"),
                root.resolve("BinOutput/Talent/RelicTalents"));
        var results = new JsonObject();
        var failures = new ArrayList<String>();
        for (var type : new Class<?>[] {AvatarSkillData.class, AvatarTalentData.class,
                AvatarCurveData.class, AvatarPromoteData.class, WeaponCurveData.class,
                WeaponPromoteData.class, ProudSkillData.class, EquipAffixData.class,
                ReliquaryMainPropData.class, ReliquaryAffixData.class, ReliquaryLevelData.class,
                ReliquarySetData.class, TpsWeaponData.class, TpsWeaponAccessoryData.class,
                GadgetData.class, AvatarSkillDepotData.class}) {
            var map = GameData.getMapByResourceDef(type);
            assertTrue(map.isEmpty(), "fresh worker resource map: " + type.getSimpleName());
            String name = type.getAnnotation(ResourceType.class).name()[0];
            Path file = root.resolve("ExcelBinOutput").resolve(name);
            int expected = JsonUtils.loadToClass(file, JsonArray.class).size();
            ResourceLoader.loadFromResource(type, file, map);
            results.addProperty(name, map.size() + "/" + expected);
            if (map.size() != expected) failures.add(name + " loaded " + map.size() + "/" + expected);
        }
        int expectedItems = 0;
        for (var name : ItemData.class.getAnnotation(ResourceType.class).name()) {
            Path file = root.resolve("ExcelBinOutput").resolve(name);
            expectedItems += JsonUtils.loadToClass(file, JsonArray.class).size();
            ResourceLoader.loadFromResource(ItemData.class, file, GameData.getItemDataMap());
        }
        results.addProperty("ItemData", GameData.getItemDataMap().size() + "/" + expectedItems);
        if (GameData.getItemDataMap().size() != expectedItems) failures.add("ItemData collapsed or rejected rows");
        ResourceLoader.loadFromResource(AvatarData.class, root.resolve("ExcelBinOutput/AvatarExcelConfigData.json"), GameData.getAvatarDataMap());
        results.addProperty("AvatarData", GameData.getAvatarDataMap().size() + "/121");
        if (GameData.getAvatarDataMap().size() != 121) failures.add("AvatarData incomplete");
        results.addProperty("abilityEmbryos", GameData.getAbilityEmbryoInfo().size());
        results.addProperty("playerAbilityGroups", GameDepot.getPlayerAbilities().size());
        results.addProperty("openConfigs", GameData.getOpenConfigEntries().size());
        results.addProperty("fullReady", ResourceLoader.isLoadedAll());
        results.addProperty("playerHydrated", false);
        System.out.println("OLD_SNAPSHOT_RESOURCE_DIAGNOSTIC " + results);
        assertFalse(ResourceLoader.isLoadedAll());
        assertTrue(failures.isEmpty(), String.join("; ", failures));
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
