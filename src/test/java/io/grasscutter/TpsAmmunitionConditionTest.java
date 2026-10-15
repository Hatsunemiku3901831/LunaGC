package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tps.TpsAmmunitionData;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

class TpsAmmunitionConditionTest {
    TpsAmmunitionSupplyTest state;

    @BeforeEach
    void setup() throws Exception {
        state = new TpsAmmunitionSupplyTest();
        state.setup();
    }

    @AfterEach
    void close() {
        if (state != null) state.close();
    }

    boolean condition(Object id) {
        var pred = new HashMap<String, Object>();
        pred.put("$type", "FGDONAGOCFP");
        pred.put("target", "CurLocalAvatar");
        if (id != null) pred.put("NHCCEJKBOPB", id);
        return PredicateEvaluator.evaluate(
                pred, state.ability, state.weapon, state.recipient, null);
    }

    static Stream<Integer> resourcePools() throws Exception {
        var rows = new ArrayList<Integer>();
        Path path =
                Path.of(
                        "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V7_0_ShadowArea.json");
        collect(JsonParser.parseString(Files.readString(path)), rows);
        assertEquals(4, rows.size());
        return rows.stream();
    }

    static void collect(JsonElement value, List<Integer> ids) {
        if (value.isJsonArray()) for (var child : value.getAsJsonArray()) collect(child, ids);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type") && object.get("$type").getAsString().equals("FGDONAGOCFP"))
                ids.add(object.get("NHCCEJKBOPB").getAsInt());
            for (var entry : object.entrySet()) collect(entry.getValue(), ids);
        }
    }

    @ParameterizedTest
    @MethodSource("resourcePools")
    void realResourceConditionsOnlyPassForKnownPoolsBelowCapacity(int id) {
        var data = GameData.getTpsAmmunitionDataMap().get(id);
        if (data == null) {
            assertFalse(condition(id));
            return;
        }
        state.player.getTpsAmmunition().put(id, data.getTpsAmmoLimit() - 1);
        assertTrue(condition(id));
        state.player.getTpsAmmunition().put(id, data.getTpsAmmoLimit());
        assertFalse(condition(id));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"missing", "negative", "fraction", "string", "nan", "overflow", "unknown"})
    void invalidPoolIdsCannotBecomeTrue(String variant) {
        Object id =
                switch (variant) {
                    case "missing" -> null;
                    case "negative" -> -1003;
                    case "fraction" -> 1003.5;
                    case "string" -> "1003";
                    case "nan" -> Double.NaN;
                    case "overflow" -> 4294968299L;
                    default -> 1004;
                };
        assertFalse(condition(id));
        assertEquals(0, state.player.saves);
    }

    @Test
    void aFullPoolDoesNotConsumeTheResourceBranch() throws Exception {
        var branch =
                JsonUtils.decode(
                        "{$type:Predicated,target:Self,targetPredicates:["
                            + "{$type:FGDONAGOCFP,NHCCEJKBOPB:1003,target:CurLocalAvatar}],successActions:["
                            + "{$type:TPSSupplyAmmo,target:CurLocalAvatar,FMGHPKANDIA:true,EMNMAKMAKLM:6,NHCCEJKBOPB:1003},"
                            + "{$type:SetGlobalValueV2,target:Self,key:AmmoPackTakenFlag,value:1}]}",
                        AbilityModifierAction.class);
        state.player.getTpsAmmunition().put(1003, 50);
        state.ability
                .getManager()
                .executeActionNow(state.ability, branch, ByteString.EMPTY, state.recipient);
        assertFalse(state.recipient.getGlobalAbilityValues().containsKey("AmmoPackTakenFlag"));
        assertEquals(0, state.player.saves);
        state.player.getTpsAmmunition().put(1003, 49);
        state.ability
                .getManager()
                .executeActionNow(state.ability, branch, ByteString.EMPTY, state.recipient);
        assertEquals(1f, state.recipient.getGlobalAbilityValues().get("AmmoPackTakenFlag"));
        assertEquals(50, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(1, state.notifiedDelta(1003));
        assertEquals(1, state.player.saves);
    }

    void distinctLimits() {
        state.put(
                GameData.getTpsAmmunitionDataMap(),
                1003,
                JsonUtils.decode(
                        "{id:1003,tpsAmmoLimit:10,DFKOLMLANEJ:7,ammoSlotIds:[301]}",
                        TpsAmmunitionData.class));
    }

    @Test
    void ordinarySceneUsesTheNativeNormalLimitForReadsChangesAndRefills() {
        distinctLimits();
        state.player.getTpsAmmunition().put(1003, 10);
        assertEquals(7, TpsWeaponSystem.getReserve(state.player, 1003));
        assertFalse(condition(1003));
        state.player.getTpsAmmunition().put(1003, 5);
        assertEquals(7, TpsWeaponSystem.changeReserve(state.player, 1003, 100));
        assertEquals(1, state.player.saves);
        state.player.getTpsAmmunition().put(1003, 1);
        TpsWeaponSystem.refillAmmunition(state.player);
        assertEquals(7, state.player.getTpsAmmunition().get(1003));
    }

    @Test
    void scene7001UsesTheSeparateTpsLimit() {
        distinctLimits();
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) state.fixture.scene.getWorld(),
                        JsonUtils.decode("{id:7001,type:SCENE_WORLD}", SceneData.class));
        state.player.setScene(scene);
        state.player.getTpsAmmunition().put(1003, 10);
        assertEquals(10, TpsWeaponSystem.getReserve(state.player, 1003));
        state.player.getTpsAmmunition().put(1003, 6);
        assertEquals(10, TpsWeaponSystem.changeReserve(state.player, 1003, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"scene", "recipient", "owner", "equipment", "source", "ability"})
    void inactiveOrForeignPlayerCannotClaimAnAvailablePool(String variant) {
        switch (variant) {
            case "scene" -> state.player.setScene(null);
            case "recipient" -> state.fixture.scene.getEntities().remove(state.recipient.getId());
            case "owner" -> state.ability =
                    new emu.grasscutter.game.ability.Ability(
                            state.ability.getData(), state.weapon, state.fixture.player);
            case "equipment" -> state.recipient.getAvatar().getTpsWeaponIds().clear();
            case "source" -> state.fixture.scene.getWeaponEntities().remove(state.weapon.getId());
            case "ability" -> state.weapon.getInstancedAbilities().remove(state.ability);
        }
        assertFalse(condition(1003));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void anExplicitNonpositiveNormalLimitIsNotReplacedByTheTpsLimit(int limit) {
        state.put(
                GameData.getTpsAmmunitionDataMap(),
                1003,
                JsonUtils.decode(
                        "{id:1003,tpsAmmoLimit:10,DFKOLMLANEJ:" + limit + ",ammoSlotIds:[301]}",
                        TpsAmmunitionData.class));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
        assertFalse(condition(1003));
        assertEquals(0, state.player.saves);
    }
}
