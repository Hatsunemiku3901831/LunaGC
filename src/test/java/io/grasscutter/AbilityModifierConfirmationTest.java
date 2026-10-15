package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityMetaModifierDurabilityChangeOuterClass.AbilityMetaModifierDurabilityChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class AbilityModifierConfirmationTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Confirmation',modifiers:{Plain:{onAdded:[{$type:'SetGlobalValue',key:'confirmation',value:7,target:'Self'}]},Other:{}}}",
                                AbilityData.class),
                        fixture.entity,
                        fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private void change(int instance, int root, String name, ModifierAction action)
            throws Exception {
        change(instance, root, name, action, 10.5);
    }

    private void change(int instance, int root, String name, ModifierAction action, double tick)
            throws Exception {
        int local = new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name);
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setTotalTickTime(tick)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(root)
                                                .setInstancedModifierId(instance))
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setAbilityData(
                                        AbilityMetaModifierChange.newBuilder()
                                                .setAction(action)
                                                .setModifierLocalId(local)
                                                .build()
                                                .toByteString())
                                .build());
    }

    private void add(int instance) throws Exception {
        change(instance, 1, "Plain", ModifierAction.MODIFIER_ACTION_ADDED);
    }

    private void durability(int instance, int root, float remaining) throws Exception {
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(root)
                                                .setInstancedModifierId(instance))
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_DURABILITY_CHANGE)
                                .setAbilityData(
                                        AbilityMetaModifierDurabilityChange.newBuilder()
                                                .setRemainingDurability(remaining)
                                                .build()
                                                .toByteString())
                                .build());
    }

    @Test
    void plain_confirmation_keeps_the_same_live_controller_and_durability() throws Exception {
        add(71);
        var original = fixture.entity.getInstancedModifiers().get(71);
        durability(71, 1, 38);
        add(71);
        assertSame(original, fixture.entity.getInstancedModifiers().get(71));
        assertEquals(
                38,
                fixture.entity
                        .getAbilityDurability()
                        .snapshot()
                        .modifiers()
                        .get(0)
                        .update()
                        .getRemainingDurability());
    }

    @Test
    void duplicate_does_not_replay_on_added_state_actions() throws Exception {
        add(71);
        assertEquals(7f, fixture.entity.getGlobalAbilityValues().get("confirmation").floatValue());
        fixture.entity.getGlobalAbilityValues().put("confirmation", 99f);
        add(71);
        assertEquals(99f, fixture.entity.getGlobalAbilityValues().get("confirmation").floatValue());
    }

    @Test
    void another_instance_of_the_same_modifier_is_independent() throws Exception {
        add(71);
        durability(71, 1, 38);
        add(72);
        durability(72, 1, 11);
        assertNotSame(
                fixture.entity.getInstancedModifiers().get(71),
                fixture.entity.getInstancedModifiers().get(72));
        assertEquals(2, fixture.entity.getAbilityDurability().snapshot().modifiers().size());
    }

    @Test
    void later_timed_add_is_a_new_native_application() throws Exception {
        add(71);
        var previous = fixture.entity.getInstancedModifiers().get(71);
        durability(71, 1, 38);
        fixture.entity.getGlobalAbilityValues().put("confirmation", 99f);
        change(71, 1, "Plain", ModifierAction.MODIFIER_ACTION_ADDED, 11.5);
        assertNotSame(previous, fixture.entity.getInstancedModifiers().get(71));
        assertEquals(7f, fixture.entity.getGlobalAbilityValues().get("confirmation").floatValue());
        assertTrue(fixture.entity.getAbilityDurability().snapshot().modifiers().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void unavailable_time_does_not_guess_that_an_add_is_a_replay(double tick) throws Exception {
        change(71, 1, "Plain", ModifierAction.MODIFIER_ACTION_ADDED, tick);
        var previous = fixture.entity.getInstancedModifiers().get(71);
        fixture.entity.getGlobalAbilityValues().put("confirmation", 99f);
        change(71, 1, "Plain", ModifierAction.MODIFIER_ACTION_ADDED, tick);
        assertNotSame(previous, fixture.entity.getInstancedModifiers().get(71));
        assertEquals(7f, fixture.entity.getGlobalAbilityValues().get("confirmation").floatValue());
    }

    @Test
    void removal_then_new_add_starts_a_new_application() throws Exception {
        add(71);
        var old = fixture.entity.getInstancedModifiers().get(71);
        durability(71, 1, 38);
        change(71, 1, "Plain", ModifierAction.MODIFIER_ACTION_REMOVED);
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(71));
        add(71);
        assertNotSame(old, fixture.entity.getInstancedModifiers().get(71));
        assertTrue(fixture.entity.getAbilityDurability().snapshot().modifiers().isEmpty());
        assertEquals(7f, fixture.entity.getGlobalAbilityValues().get("confirmation").floatValue());
    }

    @Test
    void same_id_with_another_modifier_replaces_the_old_application() throws Exception {
        add(71);
        var old = fixture.entity.getInstancedModifiers().get(71);
        durability(71, 1, 38);
        change(71, 1, "Other", ModifierAction.MODIFIER_ACTION_ADDED);
        assertNotSame(old, fixture.entity.getInstancedModifiers().get(71));
        assertEquals("Other", fixture.entity.getInstancedModifiers().get(71).getName());
        assertTrue(fixture.entity.getAbilityDurability().snapshot().modifiers().isEmpty());
    }

    @Test
    void replacement_root_does_not_reuse_a_same_named_previous_controller() throws Exception {
        add(71);
        var old = fixture.entity.getInstancedModifiers().get(71);
        ability = new Ability(ability.getData(), fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().set(0, ability);
        add(71);
        assertNotSame(old, fixture.entity.getInstancedModifiers().get(71));
        assertSame(ability, fixture.entity.getInstancedModifiers().get(71).getAbility());
    }

    @Test
    void reactivated_entity_does_not_reuse_an_application_from_an_old_lifetime() throws Exception {
        add(71);
        var old = fixture.entity.getInstancedModifiers().get(71);
        fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.activateAbilityLifecycle();
        add(71);
        assertNotSame(old, fixture.entity.getInstancedModifiers().get(71));
    }

    @Test
    void confirming_an_already_installed_controller_records_its_lifetime() throws Exception {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Confirmation',modifiers:{Plain:{modifierMixins:[{$type:'AttachActionToModifierMixin',onAdded:[]}]}}}",
                                AbilityData.class),
                        fixture.entity,
                        fixture.player);
        fixture.entity.getInstancedAbilities().set(0, ability);
        var controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Plain"));
        fixture.entity.getInstancedModifiers().put(71, controller);
        ability.registerModifier(fixture.entity, controller);
        add(71);
        assertSame(controller, fixture.entity.getInstancedModifiers().get(71));
        fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.activateAbilityLifecycle();
        add(71);
        assertNotSame(controller, fixture.entity.getInstancedModifiers().get(71));
    }

    @Test
    void delayed_remove_from_a_different_root_does_not_clear_the_new_instance() throws Exception {
        add(71);
        fixture.entity
                .getInstancedAbilities()
                .add(new Ability(ability.getData(), fixture.entity, fixture.player));
        change(71, 2, "Plain", ModifierAction.MODIFIER_ACTION_ADDED);
        var current = fixture.entity.getInstancedModifiers().get(71);
        durability(71, 2, 11);
        change(71, 1, "Plain", ModifierAction.MODIFIER_ACTION_REMOVED);
        assertSame(current, fixture.entity.getInstancedModifiers().get(71));
        assertEquals(
                11,
                fixture.entity
                        .getAbilityDurability()
                        .snapshot()
                        .modifiers()
                        .get(0)
                        .update()
                        .getRemainingDurability());
    }

    private void removeNamed(AbilityString parent) throws Exception {
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedModifierId(71))
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setAbilityData(
                                        AbilityMetaModifierChange.newBuilder()
                                                .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                                                .setParentAbilityName(parent)
                                                .build()
                                                .toByteString())
                                .build());
    }

    @Test
    void wrong_named_parent_cannot_remove_the_current_application() throws Exception {
        add(71);
        var current = fixture.entity.getInstancedModifiers().get(71);
        removeNamed(AbilityString.newBuilder().setStr("AnotherParent").build());
        assertSame(current, fixture.entity.getInstancedModifiers().get(71));
    }

    @Test
    void correct_hashed_parent_can_remove_an_instance_without_a_root_id() throws Exception {
        add(71);
        removeNamed(AbilityString.newBuilder().setHash(Utils.abilityHash("Confirmation")).build());
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(71));
    }

    @Test
    void an_omitted_parent_does_not_disable_the_native_instance_only_removal() throws Exception {
        add(71);
        change(71, 0, "Plain", ModifierAction.MODIFIER_ACTION_REMOVED);
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(71));
    }
}
