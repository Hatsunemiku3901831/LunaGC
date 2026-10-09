package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecialEnergyResourceTest {
    @Test
    void currentSkillResourcesRetainCapacityAndBurstThreshold() throws Exception {
        var skills = JsonUtils.loadToList(Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                AvatarSkillData.class);
        for (var expected : Map.of(11065, new float[]{200,100}, 11145, new float[]{100,50}).entrySet()) {
            var skill = skills.stream().filter(row -> row.getId() == expected.getKey()).findFirst().orElseThrow();
            assertEquals(expected.getValue()[0], skill.getSpecialEnergyMin(), "capacity: " + skill.getId());
            assertEquals(expected.getValue()[1], skill.getSpecialEnergyMax(), "threshold: " + skill.getId());
        }
    }

    @Test
    void legacySkillFieldsRemainReadable() {
        var skill = JsonUtils.decode("{\"LNEGGCDIDCP\":200,\"GHCNBMAPHML\":100}", AvatarSkillData.class);
        assertEquals(200, skill.getSpecialEnergyMin());
        assertEquals(100, skill.getSpecialEnergyMax());
        var current = JsonUtils.decode("{\"specialEnergyCostMax\":240,\"specialEnergyCostStart\":60}", AvatarSkillData.class);
        assertEquals(240, current.getSpecialEnergyMin());
        assertEquals(60, current.getSpecialEnergyMax());
    }

    @Test
    void currentActionNamesResolveToRegisteredKinds() {
        for (var name : Map.of("ReviveSpecialEnergy", AbilityModifierAction.Type.AddSpecialEnergy,
                "AddNyxValue", AbilityModifierAction.Type.NyxAdd,
                "SetNyxValue", AbilityModifierAction.Type.NyxSet).entrySet()) {
            assertEquals(name.getValue(), JsonUtils.decode("{\"$type\":\"" + name.getKey() + "\"}",
                    AbilityModifierAction.class).type, name.getKey());
        }
        assertEquals(AbilityMixinData.Type.NyxCostMixin,
                JsonUtils.decode("{\"$type\":\"ChangeNyxValueMixin\"}", AbilityMixinData.class).type);
    }

    @Test
    void explicitValueSurvivesASeparateZeroRatioInEitherJsonOrder() {
        for (String json : new String[]{"{\"$type\":\"ReviveSpecialEnergy\",\"value\":8,\"ratio\":0}",
                "{\"$type\":\"ReviveSpecialEnergy\",\"ratio\":0,\"value\":8}"}) {
            var action = JsonUtils.decode(json, AbilityModifierAction.class);
            assertEquals(8, action.writtenValue().get(), json);
            assertEquals(0, action.ratio.get(), json);
        }
    }

    @Test
    void actionRoundTripsRetainAbsentWritesAndExplicitValues() {
        for (String json : new String[]{"{\"$type\":\"SetGlobalValue\"}",
                "{\"$type\":\"SetAnimatorBool\",\"value\":true}",
                "{\"$type\":\"SetGlobalValue\",\"value\":null,\"ratio\":null}",
                "{\"$type\":\"ReviveSpecialEnergy\",\"value\":8,\"ratio\":0}"}) {
            var original = JsonUtils.decode(json, AbilityModifierAction.class);
            var restored = JsonUtils.decode(JsonUtils.encode(original), AbilityModifierAction.class);
            assertEquals(original.writtenValue().get(), restored.writtenValue().get(), json);
            assertEquals(original.ratio.get(), restored.ratio.get(), json);
        }
    }

    @Test
    void dynamicExpressionsRetainKeysSignsAndOperatorsAfterSerialization() {
        var action = JsonUtils.decode("{\"$type\":\"ReviveSpecialEnergy\",\"value\":[\"-%Gain\",2,\"MUL\",true,\"ADD\"]}",
                AbilityModifierAction.class);
        var restored = JsonUtils.decode(JsonUtils.encode(action), AbilityModifierAction.class);
        var properties = new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<String>();
        properties.put("Gain", 8f);
        assertEquals(-15, restored.writtenValue().get(properties, 0f));
    }
}
