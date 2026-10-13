package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

class AbilityOffStageExportTest {
    AbilityModifierAction action(String flag) {
        return JsonUtils.decode(
                "{$type:'AddGlobalValue',key:'mark',value:1,target:'Self',doOffStage:" + flag + "}",
                AbilityModifierAction.class);
    }

    JsonElement flag(AbilityModifierAction action) {
        return JsonUtils.toJson(action).getAsJsonObject().get("doOffStage");
    }

    @Test
    void exportedEmptyArrayUsesTheDefaultFalseFlagAndRoundTrips() {
        var action = assertDoesNotThrow(() -> action("[]"));
        assertFalse(action.doOffStage);
        assertEquals(JsonParser.parseString("[]"), flag(action));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canonicalBooleansKeepTheirMeaning(boolean value) {
        var action = action(String.valueOf(value));
        assertEquals(value, action.doOffStage);
        assertEquals(new JsonPrimitive(value), flag(action));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"[1]", "{future:true}", "'true'", "7", "[{$type:'ByTargetGlobalValue'}]"})
    void unrecognizedShapesArePreservedWithoutEnablingOffStage(String value) {
        var action = assertDoesNotThrow(() -> action(value));
        assertFalse(action.doOffStage);
        assertEquals(JsonParser.parseString(value), flag(action));
    }

    @Test
    void explicitNullRetainsItsRawFormAndUsesTheDefaultFlag() {
        var action = action("null");
        assertFalse(action.doOffStage);
        assertTrue(flag(action).isJsonNull());
    }

    @Test
    void anAbsentFlagStillUsesTheOrdinaryFalseDefault() {
        var action =
                JsonUtils.decode(
                        "{$type:'AddGlobalValue',key:'mark',value:1}", AbilityModifierAction.class);
        assertFalse(action.doOffStage);
    }

    @Test
    void decodingDoesNotChangeTheCallersResourceTree() {
        var tree =
                JsonParser.parseString(
                        "{$type:'AddGlobalValue',value:1,ratio:null,repeatTimes:null,doOffStage:false}");
        var before = tree.deepCopy();
        JsonUtils.decode(tree, AbilityModifierAction.class);
        assertEquals(before, tree);
    }

    @Test
    void callerChangesDoNotRewriteThePreservedExport() {
        var tree =
                JsonParser.parseString("{$type:'AddGlobalValue',value:1,doOffStage:[]}")
                        .getAsJsonObject();
        var action = assertDoesNotThrow(() -> JsonUtils.decode(tree, AbilityModifierAction.class));
        tree.getAsJsonArray("doOffStage").add(1);
        assertEquals(JsonParser.parseString("[]"), flag(action));
    }

    @Test
    void anExplicitJavaOverrideWritesTheBooleanValue() {
        var action = action("[]");
        action.doOffStage = true;
        assertEquals(new JsonPrimitive(true), flag(action));
    }

    @Test
    void nestedExportsAndRandomGroupsUseTheSameActionDecoder() {
        var action =
                assertDoesNotThrow(
                        () ->
                                JsonUtils.decode(
                                        "{$type:'Predicated',doOffStage:[],successActions:[{$type:'AddGlobalValue',key:'mark',value:1,doOffStage:[]}]}",
                                        AbilityModifierAction.class));
        assertEquals(JsonParser.parseString("[]"), flag(action.successActions[0]));
        var group =
                assertDoesNotThrow(
                        () ->
                                JsonUtils.decode(
                                        "{randomPredicates:[],actionQueue:[{$type:'AddGlobalValue',value:1,doOffStage:[]}]}",
                                        AbilityModifierAction.RandomActionGroup.class));
        assertFalse(group.actionQueue[0].doOffStage);
    }

    @Test
    void anEmptyExportStillExecutesAnOrdinaryOnSceneAction() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var data =
                    JsonUtils.decode(
                            "{abilityName:'OffStage_Test',modifiers:{}}", AbilityData.class);
            var ability = new Ability(data, fixture.entity, fixture.player);
            fixture.player
                    .getAbilityManager()
                    .executeActionNow(ability, action("[]"), ByteString.EMPTY, fixture.entity);
            assertEquals(1, fixture.entity.getGlobalAbilityValues().getOrDefault("mark", 0f));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"'true'", "'false'"})
    void unprovenStringFlagsDoNotExecuteOnTheSynchronousDispatchPath(String flag) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var data =
                    JsonUtils.decode(
                            "{abilityName:'OffStage_Test',modifiers:{}}", AbilityData.class);
            var ability = new Ability(data, fixture.entity, fixture.player);
            fixture.player
                    .getAbilityManager()
                    .executeActionNow(ability, action(flag), ByteString.EMPTY, fixture.entity);
            assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("mark"));
        }
    }

    @Test
    void unprovenFlagPreventsPartialStateChildApplication() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var data =
                    JsonUtils.decode(
                            "{abilityName:'OffStage_Test',abilityMixins:[{$type:'AttachToAbilityStateMixin',modifierName:'Child',abilityStates:[],reject:true}],modifiers:{Child:{onAdded:[{$type:'AddGlobalValue',key:'mark',value:1},{$type:'AddGlobalValue',key:'other',value:1,doOffStage:'true'}]}}}",
                            AbilityData.class);
            var ability = new Ability(data, fixture.entity, fixture.player);
            fixture.entity.getInstancedAbilities().add(ability);
            assertFalse(ability.getModifiers().containsKey("Child"));
            assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("mark"));
        }
    }

    @Test
    void theEntireActualCommonFileLoadsThroughTheResourceLoader() throws Exception {
        var path =
                Path.of(
                        "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Common.json");
        var original = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
        var names = new ArrayList<String>();
        for (var entry : original)
            names.add(
                    entry.getAsJsonObject()
                            .getAsJsonObject("Default")
                            .get("abilityName")
                            .getAsString());
        var previous = new HashMap<>(GameData.getAbilityDataMap());
        var hashes = new HashMap<>(GameData.getAbilityHashes());
        try {
            names.forEach(GameData.getAbilityDataMap()::remove);
            var method = ResourceLoader.class.getDeclaredMethod("loadAbilityModifiers", Path.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(null, path));
            for (var name : names) assertNotNull(GameData.getAbilityData(name), name);
            assertNotNull(GameData.getAbilityData("Avatar_FallAnthem_Achievement_Listener"));
            assertNotNull(GameData.getAbilityData("Avatar_NyxState_Listener"));
        } finally {
            GameData.getAbilityDataMap().clear();
            GameData.getAbilityDataMap().putAll(previous);
            GameData.getAbilityHashes().clear();
            GameData.getAbilityHashes().putAll(hashes);
        }
    }
}
