package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.ArrayList;

class TpsGlobalValueDispatchTest {
    TpsAmmunitionSupplyTest state;
    AbilityModifierAction parent;

    @BeforeEach
    void setup() throws Exception {
        state = new TpsAmmunitionSupplyTest();
        state.setup();
    }

    @AfterEach
    void close() {
        state.close();
    }

    void configure(boolean partial) {
        String dispatch =
                "{$type:DoActionByGlobalValue,valueSteps:[0.5,1.5,2.5],"
                    + "FKPKBNOINEG:{$type:AFKDGJBEGHN,name:AmmoType},NGHBADANOHL:["
                    + "[{$type:TPSSupplyAmmo,FMGHPKANDIA:true,EMNMAKMAKLM:6,NHCCEJKBOPB:1003,target:CurLocalAvatar}],"
                    + "[{$type:TPSSupplyAmmo,FMGHPKANDIA:true,EMNMAKMAKLM:75,NHCCEJKBOPB:1001,target:CurLocalAvatar}]]}";
        var data =
                JsonUtils.decode(
                        "{abilityName:TpsGlobalAwards,modifiers:{Pulse:{onAdded:["
                                + dispatch
                                + (partial ? ",{$type:AttachUIEffect}" : "")
                                + "],stacking:Unique}}}",
                        AbilityData.class);
        state.ability = new Ability(data, state.weapon, state.player);
        state.weapon.getInstancedAbilities().add(state.ability);
        parent = data.modifiers.get("Pulse").onAdded[0];
    }

    int abilityId() {
        return state.weapon.getInstancedAbilities().indexOf(state.ability) + 1;
    }

    void change(ModifierAction action) throws Exception {
        var payload =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(action)
                        .setParentAbilityName(
                                AbilityString.newBuilder()
                                        .setStr(state.ability.getData().abilityName))
                        .setModifierLocalId(
                                new ArrayList<>(state.ability.getData().modifiers.keySet())
                                        .indexOf("Pulse"));
        state.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(state.weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setInstancedModifierId(110))
                                .setAbilityData(payload.build().toByteString())
                                .build());
    }

    void confirm(AbilityModifierAction action, int modifierId) throws Exception {
        int local =
                state.ability.getData().localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == action)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        state.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(state.weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_NONE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setInstancedModifierId(modifierId)
                                                .setLocalId(local))
                                .build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void modifierAwardCannotBeReselectedOrBypassedByUnselectedLeafConfirmations(boolean partial)
            throws Exception {
        configure(partial);
        state.weapon.getGlobalAbilityValues().put("AmmoType", 1f);
        change(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        // A confirmation from an unselected branch must never become another award.
        confirm(parent.actionSteps[1][0], 110);
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        state.weapon.getGlobalAbilityValues().put("AmmoType", 2f);
        change(ModifierAction.MODIFIER_ACTION_ADDED);
        confirm(parent, 110);
        confirm(parent, 0);
        confirm(parent.actionSteps[0][0], 110);
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(1, state.player.saves);
        change(ModifierAction.MODIFIER_ACTION_REMOVED);
        confirm(parent, 110);
        confirm(parent.actionSteps[1][0], 110);
        assertEquals(1, state.player.saves);
        change(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(2, state.player.saves);
        assertEquals(6, state.notifiedDelta(1003));
        assertEquals(75, state.notifiedDelta(1001));
    }

    AbilityModifierAction actualAmmoPackDispatcher() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V7_0_ShadowArea.json")))
                        .getAsJsonArray();
        var data = rows.get(19).getAsJsonObject().getAsJsonObject("Default");
        assertEquals(
                "SceneObj_Area_Zd_Property_Ani_Prop_TPSAmmoBox_AmmoPack_01",
                data.get("abilityName").getAsString());
        return JsonUtils.decode(
                data.getAsJsonObject("modifiers")
                        .getAsJsonObject("TryAddAmmo")
                        .getAsJsonArray("onThinkInterval")
                        .get(0)
                        .toString(),
                AbilityModifierAction.class);
    }

    @ParameterizedTest
    @CsvSource({"1,1001,75", "2,1003,6", "3,1005,1", "4,0,0", "5,1004,0"})
    void realAmmoPackDispatchReachesOnlyItsSelectedKnownSupply(int type, int pool, int amount)
            throws Exception {
        state.weapon.getGlobalAbilityValues().put("SGV_TheGrey_AmmoType", (float) type);
        assertTrue(
                state.ability
                        .getManager()
                        .executeCallbackActionsNow(
                                state.ability,
                                new AbilityModifierAction[] {actualAmmoPackDispatcher()},
                                ByteString.EMPTY,
                                state.weapon,
                                state.recipient));
        assertEquals(amount, TpsWeaponSystem.getReserve(state.player, pool));
        assertEquals(amount, state.notifiedDelta(pool));
        assertEquals(amount > 0 ? 1 : 0, state.player.saves);
        if (type == 1)
            assertEquals(1f, state.weapon.getGlobalAbilityValues().get("AmmoPackTakenFlag"));
    }

    @Test
    void realAmmoPackDispatcherDoesNotMarkAFullPoolAsTaken() throws Exception {
        state.weapon.getGlobalAbilityValues().put("SGV_TheGrey_AmmoType", 1f);
        state.player.getTpsAmmunition().put(1001, 2000);
        assertTrue(
                state.ability
                        .getManager()
                        .executeCallbackActionsNow(
                                state.ability,
                                new AbilityModifierAction[] {actualAmmoPackDispatcher()},
                                ByteString.EMPTY,
                                state.weapon,
                                state.recipient));
        assertFalse(state.weapon.getGlobalAbilityValues().containsKey("AmmoPackTakenFlag"));
        assertEquals(0, state.player.saves);
        assertEquals(0, state.notifiedDelta(1001));
    }
}
