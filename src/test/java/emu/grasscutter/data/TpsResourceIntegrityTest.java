package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.EquipAffixData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;

class TpsResourceIntegrityTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");

    @BeforeAll
    static void init() {
        Grasscutter.getLogger();
    }

    private <T> List<T> load(String name, Class<T> type) throws Exception {
        return JsonUtils.loadToList(EXCEL.resolve(name + "ExcelConfigData.json"), type);
    }

    @Test
    void allEightWeaponRowsHaveAValidItemAndEquipAffix() throws Exception {
        var weapons = load("TpsWeapon", TpsWeaponData.class);
        var items = load("TpsWeapon", ItemData.class);
        var affixes =
                load("EquipAffix", EquipAffixData.class).stream()
                        .map(EquipAffixData::getId)
                        .collect(Collectors.toSet());
        assertEquals(8, weapons.size());
        for (var weapon : weapons) {
            assertTrue(weapon.getGadgetId() >= 50016001 && weapon.getGadgetId() <= 50016008);
            assertTrue(weapon.getWearSlotType() == 1 || weapon.getWearSlotType() == 2);
            assertTrue(affixes.contains(weapon.getEquipAffixId()), "Missing affix for " + weapon.getId());
            assertEquals(
                    "ITEM_TPS_WEAPON",
                    items.stream()
                            .filter(i -> i.getId() == weapon.getId())
                            .findFirst()
                            .orElseThrow()
                            .getItemType()
                            .name());
        }
    }

    @Test
    void allAccessoriesMatchAWeaponAffixAndUnlockMaterial() throws Exception {
        var weapons =
                load("TpsWeapon", TpsWeaponData.class).stream()
                        .map(TpsWeaponData::getId)
                        .collect(Collectors.toSet());
        var materials =
                load("Material", ItemData.class).stream().map(ItemData::getId).collect(Collectors.toSet());
        var affixes =
                load("EquipAffix", EquipAffixData.class).stream()
                        .map(EquipAffixData::getId)
                        .collect(Collectors.toSet());
        var accessories = load("TpsWeaponAccessory", TpsWeaponAccessoryData.class);
        assertEquals(12, accessories.size());
        for (var accessory : accessories) {
            assertTrue(weapons.contains(accessory.getTpsWeaponId()));
            assertTrue(materials.contains(accessory.getUnlockMaterialId()));
            assertTrue(affixes.contains(accessory.getEquipAffixId()));
        }
    }

    @Test
    void fiveReservePoolsAreUnambiguousAndReportTheUnmappedElectricSlot() throws Exception {
        var ammunition = load("TpsAmmunition", TpsAmmunitionData.class);
        assertEquals(5, ammunition.size());
        var slots = new HashSet<Integer>();
        for (var ammo : ammunition) {
            assertTrue(ammo.getTpsAmmoLimit() > 0);
            for (int slot : ammo.getAmmoSlotIds())
                assertTrue(slots.add(slot), "Ambiguous ammo slot " + slot);
        }
        var unmapped = new TreeMap<Integer, List<Integer>>();
        for (var weapon : load("TpsWeapon", TpsWeaponData.class)) {
            var missing = weapon.getAmmoSlotIds().stream().filter(slot -> !slots.contains(slot)).toList();
            if (!missing.isEmpty()) unmapped.put(weapon.getId(), missing);
        }
        // The actual resource does not map 202 to a reserve. Keep this gap explicit;
        // borrowing IceGun's 102 pool would silently change ElectricGun's behavior.
        assertEquals(Map.of(224002, List.of(202)), unmapped);
    }

    @Test
    void dedicatedTravelerConfigsAndAttackSkillArePresent() throws Exception {
        for (String name : List.of("AetherShadow", "LumineShadow")) {
            var path = Path.of("resources/BinOutput/Avatar/ConfigAvatar_" + name + ".json");
            var config = JsonUtils.loadToClass(path, ResourceLoader.AvatarConfig.class);
            assertTrue(
                    config.abilities.stream()
                            .anyMatch(a -> a.toString().equals("Avatar_TPS_Innate_Ability")));
        }
        var depot =
                load("AvatarSkillDepot", AvatarSkillDepotData.class).stream()
                        .filter(d -> d.getId() == 50001)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        assertArrayEquals(new int[] {20000}, depot.getSkillsAndEnergySkill().toArray());
        assertEquals(
                7, load("Scene", SceneData.class).stream().filter(TpsAvatarSystem::isTpsScene).count());
    }

    @Test
    void tpsTalentEntriesActuallyYieldTheWeaponAbilities() throws Exception {
        var raw = tpsTalents();
        var config =
                new OpenConfigEntry(
                        "TPS_Weapon_IceGun",
                        JsonUtils.decode(raw.get("TPS_Weapon_IceGun").toString(), OpenConfigData[].class));
        assertTrue(List.of(config.getAddAbilities()).contains("Avatar_TPS_Ammo_Manager"));
        assertTrue(List.of(config.getAddAbilities()).contains("Avatar_TPS_Ammo_Reload"));
    }

    @Test
    void actualIceMagazineAccessoryLoadsItsUnlockAndFireInterval() throws Exception {
        String name = "TPS_Weapon_IceGun_Accessory_Magazine";
        var config =
                new OpenConfigEntry(
                        name, JsonUtils.decode(tpsTalents().get(name).toString(), OpenConfigData[].class));
        assertNotNull(config.getTalentParamEntries());
        assertEquals("Unlock_Accessory_Magazine", config.getTalentParamEntries()[0].getParamName());
        assertNotNull(config.getAbilityVarSetters());
        assertEquals(
                "IceGun_Accessory_Magazine_FireIntervalRatio",
                config.getAbilityVarSetters()[0].getVarName());
        assertEquals(0, config.getAbilityVarSetters()[0].getParamIndex());
    }

    private com.google.gson.JsonObject tpsTalents() throws Exception {
        var files = Path.of("resources/BinOutput/Talent/EquipTalents");
        var path = files.resolve("ConfigTalent_TPS_Weapon.json");
        // Find the resource containing the weapon openConfig rather than assume its filename.
        if (!Files.isRegularFile(path)) {
            try (var paths = Files.walk(Path.of("resources/BinOutput/Talent"))) {
                path =
                        paths
                                .filter(Files::isRegularFile)
                                .filter(
                                        p -> {
                                            try {
                                                return Files.readString(p).contains("\"TPS_Weapon_IceGun\"");
                                            } catch (java.io.IOException e) {
                                                throw new java.io.UncheckedIOException(e);
                                            }
                                        })
                                .findFirst()
                                .orElseThrow();
            }
        }
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
