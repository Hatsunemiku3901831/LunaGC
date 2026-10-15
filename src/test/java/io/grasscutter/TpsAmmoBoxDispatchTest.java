package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.tps.TpsWeaponData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.AbilityModifierDuration;
import emu.grasscutter.game.ability.AbilityPeriodicHealing;
import emu.grasscutter.game.ability.AbilityTpsAmmoBoxDispatch;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class TpsAmmoBoxDispatchTest {
    TpsAmmunitionSupplyTest state;
    final AtomicBoolean failSave = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        state =
                new TpsAmmunitionSupplyTest() {
                    @Override
                    TpsEquipmentTest.RecordingPlayer createPlayer() {
                        return new TpsEquipmentTest.RecordingPlayer() {
                            @Override
                            public void save() {
                                super.save();
                                if (failSave.get())
                                    throw new IllegalStateException("injected save failure");
                            }
                        };
                    }
                };
        state.setup();
        state.put(
                GameData.getTpsWeaponDataMap(),
                224003,
                JsonUtils.decode(
                        "{id:224003,wearSlotType:1,tpsWeaponType:TPS_WEAPON_TYPE_HOWITZER,ammoSlotIds:[301]}",
                        TpsWeaponData.class));
    }

    @AfterEach
    void close() {
        state.close();
    }

    JsonObject actual() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V7_0_ShadowArea.json")))
                        .getAsJsonArray();
        var data = rows.get(17).getAsJsonObject().getAsJsonObject("Default");
        assertEquals(
                "TPSAmmoBoxDispatch",
                data.getAsJsonObject("modifiers")
                        .getAsJsonObject("DelayToDrop")
                        .getAsJsonArray("onRemoved")
                        .get(0)
                        .getAsJsonObject()
                        .get("$type")
                        .getAsString());
        return data.getAsJsonObject("modifiers")
                .getAsJsonObject("DelayToDrop")
                .getAsJsonArray("onRemoved")
                .get(0)
                .getAsJsonObject()
                .deepCopy();
    }

    AbilityModifierAction action(float total) throws Exception {
        var raw = actual();
        raw.addProperty("OCAMECDEBDO", total);
        return JsonUtils.decode(raw.toString(), AbilityModifierAction.class);
    }

    void weights(float rifle, float howitzer, float adaptive) {
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Rifle", rifle);
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Howitzer", howitzer);
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Adaptive", adaptive);
    }

    void run(AbilityModifierAction action) {
        state.ability
                .getManager()
                .executeActionNow(state.ability, action, ByteString.EMPTY, state.recipient);
    }

    List<Integer> packetPools() throws Exception {
        var ids = new ArrayList<Integer>();
        for (var packet : state.player.packets)
            if (packet.getOpcode() == PacketOpcodes.TpsAmmunitionChangeNotify)
                for (var e :
                        TpsAmmunitionChangeNotify.parseFrom(packet.getData())
                                .getAmmunitionListList()) ids.add(e.getAmmunitionConfigId());
        return ids;
    }

    void equip(int id, String type, int slot) {
        state.put(
                GameData.getItemDataMap(),
                id,
                JsonUtils.decode("{id:" + id + ",itemType:ITEM_TPS_WEAPON}", ItemData.class));
        state.put(
                GameData.getTpsWeaponDataMap(),
                id,
                JsonUtils.decode(
                        "{id:"
                                + id
                                + ",wearSlotType:"
                                + slot
                                + ",tpsWeaponType:TPS_WEAPON_TYPE_"
                                + type
                                + ",ammoSlotIds:[301]}",
                        TpsWeaponData.class));
        state.player.getInventory().addItem(new TpsEquipmentTest.RecordingItem(id));
        state.recipient.getAvatar().getTpsWeaponIds().add(id);
    }

    @Test
    void explicitWeightsAreRoundRobinUnitsRatherThanProportionalShares() throws Exception {
        weights(3, 2, 10);
        run(action(4));
        assertEquals(150, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(12, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(List.of(1001, 1003, 1001, 1003), packetPools());
    }

    @Test
    void weightsAreTruncatedAndExhaustedBeforeAdaptiveAllocation() throws Exception {
        weights(1.9f, 2.9f, 1.9f);
        run(action(4.9f));
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(18, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(List.of(1001, 1003, 1003, 1003), packetPools());
    }

    @Test
    void adaptiveAllocationPreservesWeaponOrderAndDuplicateCategoriesButSkipsGrenades()
            throws Exception {
        state.recipient.getAvatar().getTpsWeaponIds().clear();
        equip(224001, "ASSAULT_RIFLE", 1);
        equip(224002, "ASSAULT_RIFLE", 1);
        state.recipient.getAvatar().getTpsWeaponIds().add(224003);
        equip(224004, "GRENADE", 2);
        weights(0, 0, 5);
        run(action(5));
        assertEquals(300, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1005));
        assertEquals(List.of(1001, 1001, 1003, 1001, 1001), packetPools());
    }

    @Test
    void aFullPoolStillConsumesItsAllocationWithoutRedistribution() throws Exception {
        state.player.getTpsAmmunition().put(1001, 2000);
        weights(1, 0, 10);
        run(action(1));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(0, state.player.saves);
    }

    @Test
    void anExplicitEmptySniperBranchConsumesItsUnit() throws Exception {
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Sniper", 1f);
        weights(0, 0, 10);
        run(action(1));
        assertEquals(0, state.player.saves);
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
    }

    @Test
    void allSelectedPoolsAreCheckedBeforeAnyAward() throws Exception {
        weights(1, 0, 0);
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Shotgun", 1f);
        run(action(2));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(0, state.player.saves);
    }

    @Test
    void dynamicTotalAndWeightsReadTheSourceRatherThanTheRecipient() throws Exception {
        weights(1, 0, 0);
        state.weapon.getGlobalAbilityValues().put("Weight_Total", 1f);
        state.recipient.getGlobalAbilityValues().put("Weight_Total", 99f);
        state.recipient.getGlobalAbilityValues().put("SGV_Weight_Howitzer", 99f);
        run(JsonUtils.decode(actual().toString(), AbilityModifierAction.class));
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
    }

    @ParameterizedTest
    @ValueSource(floats = {0, 0.9f, -1, 101, Float.NaN, Float.POSITIVE_INFINITY})
    void invalidOrOutOfRangeTotalDoesNotAward(float total) throws Exception {
        weights(10, 10, 10);
        var parent = action(1);
        parent.tpsAmmoBoxTotal = new DynamicFloat(total);
        run(parent);
        assertEquals(0, state.player.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "ability", "recipient", "equipment", "foreign"})
    void invalidLifetimeOrOwnershipCannotAward(String kind) throws Exception {
        weights(1, 0, 0);
        var parent = action(1);
        switch (kind) {
            case "source" -> state.fixture.scene.getWeaponEntities().remove(state.weapon.getId());
            case "ability" -> state.weapon.getInstancedAbilities().remove(state.ability);
            case "recipient" -> state.fixture.scene.getEntities().remove(state.recipient.getId());
            case "equipment" -> state.recipient.getAvatar().getTpsWeaponIds().clear();
            case "foreign" -> state.ability =
                    new Ability(state.ability.getData(), state.weapon, state.fixture.player);
        }
        run(parent);
        assertEquals(0, state.player.saves);
    }

    @Test
    void removalDispatchRunsOnceAndNeitherEarlyNorUnselectedLeafConfirmationCanAward()
            throws Exception {
        var dispatch = actual();
        dispatch.addProperty("OCAMECDEBDO", 3);
        var data =
                JsonUtils.decode(
                        "{abilityName:AmmoBoxRemoval,modifiers:{Pulse:{stacking:Unique,onRemoved:["
                                + dispatch
                                + "]}}}",
                        AbilityData.class);
        state.ability = new Ability(data, state.weapon, state.player);
        state.weapon.getInstancedAbilities().add(state.ability);
        var nativeCallbacks = new TpsGlobalValueDispatchTest();
        nativeCallbacks.state = state;
        var parent = data.modifiers.get("Pulse").onRemoved[0];
        weights(3, 0, 0);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_ADDED);
        nativeCallbacks.confirm(parent, 110);
        assertEquals(0, state.player.saves);
        // Local numbering must retain every branch even when a pool is absent in the table.
        var unselected =
                data.localIdToAction.values().stream()
                        .filter(
                                a ->
                                        a.type == AbilityModifierAction.Type.TPSSupplyAmmo
                                                && a.tpsSupplyAmmunitionId == 1003)
                        .findFirst()
                        .orElseThrow();
        nativeCallbacks.confirm(unselected, 110);
        assertEquals(0, state.player.saves);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_REMOVED);
        assertEquals(225, TpsWeaponSystem.getReserve(state.player, 1001));
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_REMOVED);
        nativeCallbacks.confirm(parent, 110);
        nativeCallbacks.confirm(unselected, 0);
        assertEquals(225, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
        weights(0, 1, 0);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_ADDED);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_REMOVED);
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
    }

    @Test
    void aSaveFailureCannotReplayTheStartedAwardOnDuplicateAddition() throws Exception {
        var dispatch = actual();
        dispatch.addProperty("OCAMECDEBDO", 3);
        var data =
                JsonUtils.decode(
                        "{abilityName:AmmoBoxSaveFailure,modifiers:{Pulse:{stacking:Unique,onAdded:["
                                + dispatch
                                + "]}}}",
                        AbilityData.class);
        state.ability = new Ability(data, state.weapon, state.player);
        state.weapon.getInstancedAbilities().add(state.ability);
        var nativeCallbacks = new TpsGlobalValueDispatchTest();
        nativeCallbacks.state = state;
        weights(3, 0, 0);
        failSave.set(true);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        failSave.set(false);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_ADDED);
        nativeCallbacks.confirm(data.modifiers.get("Pulse").onAdded[0], 110);
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(1, state.player.saves);
    }

    @Test
    void aRejectedPlanCanBeRetriedAfterItsMissingPoolIsNoLongerSelected() throws Exception {
        var dispatch = actual();
        dispatch.addProperty("OCAMECDEBDO", 2);
        var data =
                JsonUtils.decode(
                        "{abilityName:AmmoBoxRejectedPlan,modifiers:{Pulse:{stacking:Unique,onAdded:["
                                + dispatch
                                + "]}}}",
                        AbilityData.class);
        state.ability = new Ability(data, state.weapon, state.player);
        state.weapon.getInstancedAbilities().add(state.ability);
        var nativeCallbacks = new TpsGlobalValueDispatchTest();
        nativeCallbacks.state = state;
        weights(1, 0, 0);
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Shotgun", 1f);
        nativeCallbacks.change(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(0, state.player.saves);
        state.weapon.getGlobalAbilityValues().put("SGV_Weight_Shotgun", 0f);
        nativeCallbacks.confirm(data.modifiers.get("Pulse").onAdded[0], 110);
        assertEquals(75, TpsWeaponSystem.getReserve(state.player, 1001));
        nativeCallbacks.confirm(data.modifiers.get("Pulse").onAdded[0], 0);
        assertEquals(1, state.player.saves);
    }

    @Test
    void cooldownOnlyRemovalBookkeepingCannotGrantABox() throws Exception {
        var parent = action(1);
        weights(1, 0, 0);
        var data =
                JsonUtils.decode(
                        "{abilityName:NoBookkeepingAward,modifiers:{Pulse:{onRemoved:["
                                + actual()
                                + "]}}}",
                        AbilityData.class);
        var controller =
                new AbilityModifierController(state.ability, data, data.modifiers.get("Pulse"));
        AbilityCooldownInvocation.run(
                new AbilityCooldownInvocation.Scope(controller, true), () -> run(parent));
        assertEquals(0, state.player.saves);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null-group",
                "null-key",
                "unknown-type",
                "missing-total",
                "unknown-action",
                "bonus",
                "non-explicit",
                "selector",
                "leaf-predicate",
                "offstage",
                "too-many-groups"
            })
    void unsupportedDescriptorsRejectTheWholeBoxBeforeAnyMutation(String kind) throws Exception {
        var raw = actual();
        var groups = raw.getAsJsonArray("DJGANAOKMHA");
        var first = groups.get(0).getAsJsonObject();
        var leaf = first.getAsJsonArray("actionList").get(0).getAsJsonObject();
        switch (kind) {
            case "null-group" -> groups.set(0, JsonNull.INSTANCE);
            case "null-key" -> first.remove("key");
            case "unknown-type" -> first.addProperty("JIMCDBEIAEN", "UNKNOWN");
            case "missing-total" -> raw.remove("OCAMECDEBDO");
            case "unknown-action" -> leaf.addProperty("$type", "KillSelf");
            case "bonus" -> leaf.addProperty("DBPIABFDELD", true);
            case "non-explicit" -> leaf.addProperty("FMGHPKANDIA", false);
            case "selector" -> raw.add(
                    "otherTargets", JsonParser.parseString("{$type:SelectTargetsByShape}"));
            case "leaf-predicate" -> leaf.add(
                    "predicates",
                    JsonParser.parseString("[{$type:ByTargetGlobalValue,key:changed,value:1}]"));
            case "offstage" -> leaf.addProperty("doOffStage", "invalid");
            case "too-many-groups" -> {
                while (groups.size() < 65) groups.add(first.deepCopy());
            }
        }
        var parent = JsonUtils.decode(raw.toString(), AbilityModifierAction.class);
        assertFalse(AbilityTpsAmmoBoxDispatch.supported(parent));
        weights(5, 5, 5);
        run(parent);
        assertEquals(0, state.player.saves);
    }

    @Test
    void theFollowingRootRetainsItsIdAfterAllGroupLeaves() throws Exception {
        var root = action(1);
        var data = new AbilityData();
        data.abilityName = "BoxLocalIds";
        data.onAdded =
                new AbilityModifierAction[] {
                    root,
                    JsonUtils.decode(
                            "{$type:ClearGlobalValue,key:after}", AbilityModifierAction.class)
                };
        data.initialize();
        assertSame(root, data.localIdToAction.get(513));
        assertSame(root.tpsAmmoBoxGroups[0].actionList[0], data.localIdToAction.get(1025));
        assertSame(root.tpsAmmoBoxGroups[4].actionList[0], data.localIdToAction.get(2561));
        assertSame(data.onAdded[1], data.localIdToAction.get(3073));
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "removed-source", "removed-ability"})
    void aServerAppliedBoxAwardsOnlyAtExpiryAndRejectsAnInvalidatedSource(String kind)
            throws Exception {
        var now = new AtomicLong();
        var source =
                new AbilityTestFixture.TestEntity(state.fixture.scene) {
                    final AbilityPeriodicHealing healing =
                            new AbilityPeriodicHealing(this, now::get);
                    final AbilityModifierDuration duration =
                            new AbilityModifierDuration(this, now::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return healing;
                    }

                    @Override
                    public AbilityModifierDuration getAbilityModifierDuration() {
                        return duration;
                    }
                };
        source.setId(4242);
        state.fixture.scene.getEntities().put(source.getId(), source);
        source.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        source.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var dispatch = actual();
        dispatch.addProperty("OCAMECDEBDO", 3);
        var data =
                JsonUtils.decode(
                        "{abilityName:ServerBoxExpiry,modifiers:{Start:{thinkInterval:1,onThinkInterval:"
                            + "[{$type:ApplyModifier,modifierName:Pulse}]},Pulse:{stacking:Unique,duration:0.45,onRemoved:["
                                + dispatch
                                + "]}}}",
                        AbilityData.class);
        var ability = new Ability(data, source, state.player);
        source.getInstancedAbilities().add(ability);
        source.getGlobalAbilityValues().put("SGV_Weight_Rifle", 3f);
        ability.registerModifier(
                source, new AbilityModifierController(ability, data, data.modifiers.get("Start")));
        now.set(1_000_000_000L);
        source.getAbilityPeriodicHealing().tick();
        assertTrue(ability.getModifiers(source).get("Pulse").hasServerCallbacks());
        now.set(1_449_000_000L);
        source.getAbilityModifierDuration().tick();
        assertEquals(0, state.player.saves);
        if (kind.equals("removed-source")) state.fixture.scene.getEntities().remove(source.getId());
        if (kind.equals("removed-ability")) source.getInstancedAbilities().remove(ability);
        now.set(1_451_000_000L);
        source.getAbilityModifierDuration().tick();
        assertEquals(
                kind.equals("normal") ? 225 : 0, TpsWeaponSystem.getReserve(state.player, 1001));
        source.getAbilityModifierDuration().tick();
        ability.removeModifier(source, "Pulse");
        assertEquals(kind.equals("normal") ? 3 : 0, state.player.saves);
    }
}
