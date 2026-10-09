package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.AbilityEmbryoEntry;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TpsResourceCompatibilityTest {
    @BeforeAll
    static void init() {
        Grasscutter.getLogger();
    }

    @Test
    void tpsItemHasItsOwnInventoryType() {
        var data = JsonUtils.decode("{\"id\":224001,\"itemType\":\"ITEM_TPS_WEAPON\"}", ItemData.class);
        assertNotNull(data.getItemType());
        assertEquals("ITEM_TPS_WEAPON", data.getItemType().name());
        assertEquals(7, data.getItemType().getValue());
    }

    @Test
    void itemResourceIncludesTpsWeapons() {
        assertTrue(
                List.of(ItemData.class.getAnnotation(ResourceType.class).name())
                        .contains("TpsWeaponExcelConfigData.json"));
    }

    @Test
    void attackModeSkillIsIncludedEvenWithoutRegularSkills() {
        var depot =
                JsonUtils.decode(
                        "{\"id\":50001,\"attackModeSkill\":20000,\"skills\":[0,0,0,0]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        assertArrayEquals(new int[] {20000}, depot.getSkillsAndEnergySkill().toArray());
    }

    @Test
    void sparseDepotHasNoDuplicateOrNullSkills() {
        var depot =
                JsonUtils.decode(
                        "{\"id\":50001,\"attackModeSkill\":20000,\"energySkill\":20000,\"talents\":[]}",
                        AvatarSkillDepotData.class);
        assertDoesNotThrow(depot::onLoad);
        assertArrayEquals(new int[] {20000}, depot.getSkillsAndEnergySkill().toArray());
    }

    @Test
    void latestAddAbilityAliasIsLoaded() {
        var entries =
                JsonUtils.decode(
                        "[{\"$type\":\"PHNIGFHBFMD\",\"NCCKLDFFDOH\":\"Avatar_TPS_Ammo_Manager\"}]",
                        OpenConfigData[].class);
        assertArrayEquals(
                new String[] {"Avatar_TPS_Ammo_Manager"},
                new OpenConfigEntry("TPS_Weapon_IceGun", entries).getAddAbilities());
    }

    @Test
    void latestModifyAbilityAndTalentAliasesAreLoaded() {
        var entries =
                JsonUtils.decode(
                        "[{\"$type\":\"GMOELNAHCOH\",\"NCCKLDFFDOH\":\"TPS_Weapon_IceGun\",\"LDACNDBDKBA\":\"Ammo\",\"IJFENBIJGLJ\":\"%1\"},{\"$type\":\"CMGFNDNMFFO\",\"NCCKLDFFDOH\":\"TPS_Weapon_IceGun\",\"AAAENHAPNLB\":\"Unlock\"}]",
                        OpenConfigData[].class);
        var config = new OpenConfigEntry("TPS", entries);
        assertNotNull(config.getAbilityVarSetters());
        assertNotNull(config.getTalentParamEntries());
        assertEquals(0, config.getAbilityVarSetters()[0].getParamIndex());
        assertEquals("Ammo", config.getAbilityVarSetters()[0].getVarName());
        assertEquals("Unlock", config.getTalentParamEntries()[0].getParamName());
    }

    @Test
    void tpsTravelersUseDedicatedEmbryosAndRebuildWithoutAccumulating() {
        for (int id : new int[] {10000134, 10000135}) {
            String name = id == 10000134 ? "AetherShadow" : "LumineShadow";
            var before =
                    GameData.getAbilityEmbryoInfo()
                            .put(name, new AbilityEmbryoEntry(name, new String[] {"Avatar_TPS_Innate_Ability"}));
            try {
                var data =
                        JsonUtils.decode(
                                "{\"id\":" + id + ",\"iconName\":\"UI_AvatarIcon_PlayerBoy\"}", AvatarData.class);
                data.buildEmbryo();
                data.buildEmbryo();
                assertEquals(name, data.getName());
                assertEquals(List.of("Avatar_TPS_Innate_Ability"), data.getAbilitieNames());
            } finally {
                if (before == null) GameData.getAbilityEmbryoInfo().remove(name);
                else GameData.getAbilityEmbryoInfo().put(name, before);
            }
        }
    }
}
