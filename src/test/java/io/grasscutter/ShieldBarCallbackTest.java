package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

class ShieldBarCallbackTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private AbilityModifierController controller;
    private static final int FIRST = 2097156; // Modifier mixin 0, modifier 0, callback 0, action 1.

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        install(
                "{abilityName:'ShieldCallbacks',modifiers:{Shield:{modifierMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'SetGlobalValue',key:'value',value:2,target:'Self'},{$type:'AddGlobalValue',key:'value',value:3,target:'Self'}]}]}}}");
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private void install(String json) {
        ability =
                new Ability(
                        JsonUtils.decode(json, AbilityData.class), fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().clear();
        fixture.entity.getInstancedAbilities().add(ability);
        fixture.entity.getInstancedModifiers().clear();
        if (!ability.getData().modifiers.isEmpty()) {
            controller =
                    new AbilityModifierController(
                            ability, ability.getData(), ability.getData().modifiers.get("Shield"));
            fixture.entity.getInstancedModifiers().put(71, controller);
        }
    }

    private void invoke(int localId, int modifierId, AbilityInvokeArgument argument) {
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setArgumentType(argument)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setLocalId(localId)
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(modifierId))
                                .setAbilityData(ByteString.EMPTY)
                                .build());
    }

    private void invoke(int id) {
        invoke(id, 71, AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
    }

    private float value() {
        return fixture.entity.getGlobalAbilityValues().getOrDefault("value", 0f);
    }

    @Test
    void modifier_callbacks_keep_the_native_mixin_action_ids() {
        assertEquals(2, ability.getData().localIdToAction.size());
        assertEquals("value", ability.getData().localIdToAction.get(FIRST).key);
        assertEquals(
                AbilityModifier.AbilityModifierAction.Type.AddGlobalValue,
                ability.getData().localIdToAction.get(4194308).type);
        assertEquals(
                AbilityMixinData.Type.ShieldBarMixin, ability.getData().localIdToMixin.get(4).type);
    }

    @Test
    void received_actions_run_in_order_without_dispatching_the_entire_callback() {
        invoke(FIRST);
        assertEquals(2, value());
        invoke(4194308);
        assertEquals(5, value());
    }

    @Test
    void root_callbacks_are_available_without_a_modifier_instance() {
        install(
                "{abilityName:'RootShield',abilityMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'SetGlobalValue',key:'value',value:7,target:'Self'}]}]}");
        invoke(
                32770,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(7, value());
    }

    @Test
    void sorted_modifier_index_and_mixin_index_are_both_preserved() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'IndexedShield',modifiers:{Z:{modifierMixins:[{$type:'CostStaminaMixin'},{$type:'ShieldBarMixin',onShieldBroken:[{$type:'AddGlobalValue',key:'z',value:1}]}]},A:{},M:{}}}",
                        AbilityData.class);
        data.initialize();
        assertNotNull(data.localIdToAction.get(2097684));
        assertNotNull(data.localIdToMixin.get(532));
        assertEquals(1, data.localIdToAction.size());
        data.initialize();
        assertEquals(1, data.localIdToAction.size());
    }

    @Test
    void nested_actions_do_not_reuse_a_sibling_id() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'NestedShield',modifiers:{Shield:{modifierMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'Predicated',actions:[{$type:'SetGlobalValue',key:'nested',value:1}]},{$type:'AddGlobalValue',key:'last',value:1}]}]}}}",
                        AbilityData.class);
        data.initialize();
        assertEquals("nested", data.localIdToAction.get(4194308).key);
        assertEquals("last", data.localIdToAction.get(6291460).key);
    }

    @Test
    void root_action_ids_and_other_mixin_ids_are_unchanged() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'MixedShield',onAdded:[{$type:'SetGlobalValue',key:'root',value:1}],abilityMixins:[{$type:'CostStaminaMixin'},{$type:'ShieldBarMixin',onShieldBroken:[{$type:'SetGlobalValue',key:'shield',value:1}]}]}",
                        AbilityData.class);
        data.initialize();
        assertEquals("root", data.localIdToAction.get(513).key);
        assertEquals("shield", data.localIdToAction.get(32778).key);
        assertEquals(AbilityMixinData.Type.CostStaminaMixin, data.localIdToMixin.get(2).type);
        assertEquals(AbilityMixinData.Type.ShieldBarMixin, data.localIdToMixin.get(10).type);
    }

    @Test
    void removed_parent_cannot_fall_back_to_a_live_root() {
        fixture.entity.getInstancedModifiers().clear();
        invoke(FIRST);
        assertEquals(0, value());
    }

    @Test
    void callback_cannot_use_another_modifier_in_the_same_ability() {
        install(
                "{abilityName:'WrongParent',modifiers:{Other:{},Shield:{modifierMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'SetGlobalValue',key:'value',value:2,target:'Self'}]}]}}}");
        fixture.entity
                .getInstancedModifiers()
                .put(
                        72,
                        new AbilityModifierController(
                                ability,
                                ability.getData(),
                                ability.getData().modifiers.get("Other")));
        invoke(
                2097164,
                72,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(0, value());
    }

    @Test
    void a_modifier_callback_requires_its_instance_id() {
        invoke(
                FIRST,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(0, value());
    }

    @Test
    void root_callback_cannot_borrow_a_modifier_context() {
        install(
                "{abilityName:'RootWithParent',modifiers:{Shield:{}},abilityMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'SetGlobalValue',key:'value',value:2,target:'Self'}]}]}");
        invoke(32770);
        assertEquals(0, value());
    }

    @Test
    void disabled_mixin_cannot_execute_a_child_action() {
        controller.getModifierData().modifierMixins[0].enable = false;
        invoke(FIRST);
        assertEquals(0, value());
    }

    @Test
    void shield_bar_state_update_is_not_a_child_action() {
        invoke(FIRST, 71, AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_SHIELD_BAR);
        invoke(4, 71, AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_SHIELD_BAR);
        assertEquals(0, value());
    }

    @Test
    void an_unrelated_mixin_message_cannot_execute_a_child_action() {
        invoke(FIRST, 71, AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_COST_STAMINA);
        assertEquals(0, value());
    }

    @Test
    void two_live_applications_keep_their_own_parent_identity() {
        fixture.entity
                .getInstancedModifiers()
                .put(
                        72,
                        new AbilityModifierController(
                                ability, ability.getData(), controller.getModifierData()));
        invoke(FIRST);
        invoke(
                4194308,
                72,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(5, value());
    }

    @Test
    void a_new_application_can_receive_the_same_callback_ids() {
        invoke(FIRST);
        fixture.entity.getInstancedModifiers().clear();
        fixture.entity
                .getInstancedModifiers()
                .put(
                        72,
                        new AbilityModifierController(
                                ability, ability.getData(), controller.getModifierData()));
        invoke(4194308);
        assertEquals(2, value());
        invoke(
                4194308,
                72,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(5, value());
    }

    @Test
    void a_reused_entity_id_cannot_reach_an_old_modifier() {
        var replacement = new AbilityTestFixture.TestEntity(fixture.scene);
        replacement.setId(fixture.entity.getId());
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        invoke(FIRST);
        assertEquals(0, value());
    }

    @Test
    void disabled_entity_lifecycle_cannot_receive_a_callback() {
        fixture.entity.invalidateAbilityLifecycle();
        invoke(FIRST);
        assertEquals(0, value());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void a_separate_bearer_must_still_be_current(boolean inactive) {
        var owner = new AbilityTestFixture.TestEntity(fixture.scene);
        owner.setId(43);
        fixture.scene.getEntities().put(43, owner);
        ability = new Ability(ability.getData(), owner, fixture.player);
        owner.getInstancedAbilities().add(ability);
        controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Shield"));
        fixture.entity.getInstancedModifiers().put(71, controller);
        if (inactive) fixture.entity.invalidateAbilityLifecycle();
        invokeOnTarget(43);
        assertEquals(inactive ? 0f : 2f, owner.getGlobalAbilityValues().getOrDefault("value", 0f));
    }

    private void invokeOnTarget(int targetId) {
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setLocalId(FIRST)
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(71)
                                                .setTargetId(targetId))
                                .build());
    }

    @Test
    void a_missing_explicit_target_does_not_redirect_the_callback_to_its_bearer() {
        invokeOnTarget(999);
        assertEquals(0, value());
    }

    @Test
    void callback_runs_under_the_scene_registry_lock() throws Exception {
        var started = new CountDownLatch(1);
        CompletableFuture<Void> future;
        synchronized (fixture.scene) {
            future =
                    CompletableFuture.runAsync(
                            () -> {
                                started.countDown();
                                invoke(FIRST);
                            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
        }
        future.get(5, TimeUnit.SECONDS);
        assertEquals(2, value());
    }

    static Stream<Arguments> resources() throws Exception {
        var cases = new ArrayList<Arguments>();
        for (String family : List.of("Electric", "Fire", "Grass", "Ice", "Rock", "Water", "Wind")) {
            var path =
                    Path.of(
                            "resources/BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Effigy_"
                                    + family
                                    + ".json");
            for (var entry : JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                var data = entry.getAsJsonObject().getAsJsonObject("Default");
                if (!data.has("modifiers")) continue;
                for (var modifier : data.getAsJsonObject("modifiers").entrySet()) {
                    var mod = modifier.getValue().getAsJsonObject();
                    if (!mod.has("modifierMixins")) continue;
                    var mixins = mod.getAsJsonArray("modifierMixins");
                    for (int i = 0; i < mixins.size(); i++) {
                        var mix = mixins.get(i).getAsJsonObject();
                        if ("ShieldBarMixin".equals(mix.get("$type").getAsString()))
                            cases.add(
                                    Arguments.of(
                                            data.toString(),
                                            modifier.getKey(),
                                            i,
                                            mix.has("onShieldBroken")
                                                    ? mix.getAsJsonArray("onShieldBroken").size()
                                                    : 0));
                    }
                }
            }
        }
        assertEquals(12, cases.size());
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}] {1}, mixin {2}, actions {3}")
    @MethodSource("resources")
    void real_effigy_callbacks_have_distinct_positions(
            String json, String name, int mixinIndex, int count) {
        var data = JsonUtils.decode(json, AbilityData.class);
        data.initialize();
        var names = new ArrayList<>(new TreeSet<>(data.modifiers.keySet()));
        int prefix = 4 + names.indexOf(name) * 8 + mixinIndex * 512;
        for (int i = 1; i <= count; i++)
            assertNotNull(data.localIdToAction.get(prefix + i * 2097152), name + " action " + i);
        assertNull(data.localIdToAction.get(prefix + (count + 1) * 2097152));
    }

    @Test
    void actual_fire_shield_break_updates_its_resource_watcher() throws Exception {
        var path =
                Path.of(
                        "resources/BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Effigy_Fire.json");
        var raw = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
        JsonObject selected = null;
        for (var entry : raw) {
            var data = entry.getAsJsonObject().getAsJsonObject("Default");
            if ("Monster_Effigy_Fire_FightingPoseManager"
                    .equals(data.get("abilityName").getAsString())) selected = data;
        }
        assertNotNull(selected);
        var data = JsonUtils.decode(selected, AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().set(0, ability);
        var name = "Effigy_Fire_BurningModifier";
        controller = new AbilityModifierController(ability, data, data.modifiers.get(name));
        fixture.entity.getInstancedModifiers().put(71, controller);
        int index = new ArrayList<>(new TreeSet<>(data.modifiers.keySet())).indexOf(name);
        invoke(6291460 + index * 8);
        assertEquals(
                1, fixture.entity.getGlobalAbilityValues().getOrDefault("_WATCHER_82097_", 0f));
    }
}
