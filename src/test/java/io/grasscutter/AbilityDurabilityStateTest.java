package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityMetaModifierDurabilityChangeOuterClass.AbilityMetaModifierDurabilityChange;
import emu.grasscutter.net.proto.AbilityMixinShieldBarOuterClass.AbilityMixinShieldBar;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

class AbilityDurabilityStateTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private AbilityModifierController controller;
    private static final AbilityInvokeArgument DURABILITY =
            AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_DURABILITY_CHANGE;
    private static final AbilityInvokeArgument SHIELD =
            AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_SHIELD_BAR;
    private static final AbilityInvokeArgument MODIFIER =
            AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        install(
                "{abilityName:'Durability',abilityMixins:[{$type:'ShieldBarMixin'},{$type:'ShieldBarMixin'}],modifiers:{Other:{modifierMixins:[{$type:'CostStaminaMixin'}]},Shield:{modifierMixins:[{$type:'ShieldBarMixin',onShieldBroken:[{$type:'AddGlobalValue',key:'broken',value:1,target:'Self'}]}]}}}");
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
        controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Shield"));
        fixture.entity.getInstancedModifiers().clear();
        fixture.entity.getInstancedModifiers().put(71, controller);
    }

    private AbilityInvokeEntryHead.Builder head(int modifier, int local) {
        return AbilityInvokeEntryHead.newBuilder()
                .setInstancedAbilityId(1)
                .setInstancedModifierId(modifier)
                .setLocalId(local);
    }

    private boolean invoke(
            AbilityInvokeArgument argument, AbilityInvokeEntryHead.Builder head, ByteString data)
            throws Exception {
        return fixture.player
                .getAbilityManager()
                .acceptAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(fixture.entity.getId())
                                .setHead(head)
                                .setArgumentType(argument)
                                .setAbilityData(data)
                                .build());
    }

    private AbilityMetaModifierDurabilityChange update(float remaining, float extra) {
        return AbilityMetaModifierDurabilityChange.newBuilder()
                .setRemainingDurability(remaining)
                .setJODMMPLFMEG(extra)
                .build();
    }

    private void durability(float value) throws Exception {
        invoke(DURABILITY, head(71, 0), update(value, 0.25f).toByteString());
    }

    private AbilityMixinShieldBar shield(float remaining) {
        return AbilityMixinShieldBar.newBuilder()
                .setMaxDurability(100)
                .setRemainingDurability(remaining)
                .setPlayerNum(4)
                .setElementType(7)
                .build();
    }

    private int shieldLocal() {
        var mixin = controller.getModifierData().modifierMixins[0];
        return ability.getData().localIdToMixin.entrySet().stream()
                .filter(e -> e.getValue() == mixin)
                .findFirst()
                .orElseThrow()
                .getKey();
    }

    private AbilityDurabilityState.Snapshot snapshot() {
        return fixture.entity.getAbilityDurability().snapshot();
    }

    @Test
    void records_actual_parent_and_keeps_both_live_floats_separate() throws Exception {
        durability(38);
        var saved = snapshot().modifiers().get(0);
        assertEquals(fixture.entity.getId(), saved.parentEntityId());
        assertEquals("Durability", saved.parentAbilityName());
        assertEquals(71, saved.head().getInstancedModifierId());
        assertEquals(update(38, 0.25f), saved.update());
    }

    @ParameterizedTest
    @ValueSource(floats = {0, -3, 112})
    void preserves_finite_remaining_values_including_depleted_and_overshot(float value)
            throws Exception {
        durability(value);
        assertEquals(value, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @Test
    void latest_report_replaces_old_values_without_history_growth() throws Exception {
        for (int i = 0; i < 200; i++) durability(i);
        assertEquals(1, snapshot().modifiers().size());
        assertEquals(199, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @Test
    void zero_instance_does_not_create_an_unbound_report() throws Exception {
        invoke(DURABILITY, head(0, 0), update(4, 1).toByteString());
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void missing_modifier_does_not_allocate_state() throws Exception {
        invoke(DURABILITY, head(72, 0), update(4, 1).toByteString());
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void serverbuff_payload_cannot_overwrite_normal_instance_with_the_same_id() throws Exception {
        durability(38);
        invoke(
                DURABILITY,
                head(71, 0),
                update(2, 1).toBuilder().setIsServerbuffModifier(true).build().toByteString());
        assertEquals(38, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void non_normal_source_cannot_overwrite_normal_durability(int source) throws Exception {
        durability(38);
        invoke(DURABILITY, head(71, 0).setFPDAONBEBHKValue(source), update(2, 1).toByteString());
        assertEquals(38, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void non_normal_modifier_add_does_not_replace_a_normal_controller(int source) throws Exception {
        var data =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setModifierLocalId(0)
                        .build();
        invoke(MODIFIER, head(71, 0).setFPDAONBEBHKValue(source), data.toByteString());
        assertSame(controller, fixture.entity.getInstancedModifiers().get(71));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void non_normal_modifier_remove_does_not_remove_a_normal_controller(int source)
            throws Exception {
        var data =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build();
        invoke(MODIFIER, head(71, 0).setFPDAONBEBHKValue(source), data.toByteString());
        assertSame(controller, fixture.entity.getInstancedModifiers().get(71));
    }

    @Test
    void malformed_live_message_is_rejected_before_forwarding() throws Exception {
        assertFalse(invoke(DURABILITY, head(71, 0), ByteString.copyFrom(new byte[] {45, 1})));
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void non_finite_live_values_do_not_poison_a_previous_report(float value) throws Exception {
        durability(38);
        assertFalse(invoke(DURABILITY, head(71, 0), update(value, 1).toByteString()));
        assertFalse(invoke(DURABILITY, head(71, 0), update(2, value).toByteString()));
        assertEquals(38, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @Test
    void metadata_local_id_does_not_route_durability_to_a_child_action() throws Exception {
        invoke(DURABILITY, head(71, 2097164), update(28, 1).toByteString());
        assertEquals(28, snapshot().modifiers().get(0).update().getRemainingDurability());
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("broken"));
    }

    @Test
    void wrong_known_parent_ability_cannot_update_a_modifier() throws Exception {
        fixture.entity
                .getInstancedAbilities()
                .add(
                        new Ability(
                                JsonUtils.decode("{abilityName:'OtherParent'}", AbilityData.class),
                                fixture.entity,
                                fixture.player));
        invoke(DURABILITY, head(71, 0).setInstancedAbilityId(2), update(4, 1).toByteString());
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void records_modifier_shield_bar_without_running_break_callbacks() throws Exception {
        invoke(SHIELD, head(71, shieldLocal()), shield(0).toByteString());
        assertEquals(shield(0), snapshot().shieldBars().get(0).update());
        assertEquals(71, snapshot().shieldBars().get(0).head().getInstancedModifierId());
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("broken"));
    }

    @Test
    void root_mixins_keep_separate_reports_and_latest_values() throws Exception {
        invoke(SHIELD, head(0, 2), shield(20).toByteString());
        invoke(SHIELD, head(0, 10), shield(40).toByteString());
        invoke(SHIELD, head(0, 2), shield(7).toByteString());
        var bars = snapshot().shieldBars();
        assertEquals(2, bars.size());
        assertEquals(
                Set.of(7f, 40f),
                new HashSet<>(
                        bars.stream().map(x -> x.update().getRemainingDurability()).toList()));
    }

    @Test
    void shield_update_requires_the_mixin_belonging_to_the_actual_parent() throws Exception {
        invoke(SHIELD, head(71, 2), shield(4).toByteString());
        invoke(SHIELD, head(0, shieldLocal()), shield(4).toByteString());
        fixture.entity
                .getInstancedModifiers()
                .put(
                        72,
                        new AbilityModifierController(
                                ability,
                                ability.getData(),
                                ability.getData().modifiers.get("Other")));
        invoke(SHIELD, head(72, shieldLocal()), shield(4).toByteString());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void arbitrary_mixin_ids_cannot_create_reports() throws Exception {
        for (int i = 100; i < 200; i++) invoke(SHIELD, head(71, i), shield(4).toByteString());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void malformed_shield_message_is_rejected_before_forwarding() throws Exception {
        assertFalse(
                invoke(SHIELD, head(71, shieldLocal()), ByteString.copyFrom(new byte[] {125, 1})));
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void non_finite_shield_values_do_not_poison_a_previous_report(float value) throws Exception {
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        assertFalse(invoke(SHIELD, head(71, shieldLocal()), shield(value).toByteString()));
        assertFalse(
                invoke(
                        SHIELD,
                        head(71, shieldLocal()),
                        shield(4).toBuilder().setMaxDurability(value).build().toByteString()));
        assertEquals(shield(4), snapshot().shieldBars().get(0).update());
    }

    @Test
    void disabled_shield_mixins_do_not_create_reports() throws Exception {
        controller.getModifierData().modifierMixins[0].enable = false;
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void raw_unknown_fields_survive_state_storage() throws Exception {
        var extra =
                UnknownFieldSet.newBuilder()
                        .addField(55, UnknownFieldSet.Field.newBuilder().addVarint(123).build())
                        .build();
        var payload = update(4, 0.25f).toBuilder().setUnknownFields(extra).build();
        invoke(DURABILITY, head(71, 0), payload.toByteString());
        assertEquals(payload, snapshot().modifiers().get(0).update());
    }

    @Test
    void removed_modifier_and_reused_id_do_not_inherit_previous_reports() throws Exception {
        durability(38);
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        invoke(
                MODIFIER,
                head(71, 0),
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build()
                        .toByteString());
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
        fixture.entity
                .getInstancedModifiers()
                .put(
                        71,
                        new AbilityModifierController(
                                ability, ability.getData(), controller.getModifierData()));
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void direct_controller_replacement_invalidates_saved_reports() throws Exception {
        durability(38);
        fixture.entity
                .getInstancedModifiers()
                .put(
                        71,
                        new AbilityModifierController(
                                ability, ability.getData(), controller.getModifierData()));
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void replacement_of_the_same_named_root_invalidates_saved_reports() throws Exception {
        durability(38);
        invoke(SHIELD, head(0, 2), shield(4).toByteString());
        fixture.entity
                .getInstancedAbilities()
                .set(0, new Ability(ability.getData(), fixture.entity, fixture.player));
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void entity_lifetime_change_clears_reports_even_when_controllers_are_reused() throws Exception {
        durability(38);
        invoke(SHIELD, head(0, 2), shield(4).toByteString());
        fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.activateAbilityLifecycle();
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
        durability(12);
        assertEquals(12, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @Test
    void a_replacement_entity_with_the_same_id_cannot_reuse_saved_reports() throws Exception {
        durability(38);
        var replacement = new AbilityTestFixture.TestEntity(fixture.scene);
        replacement.setId(fixture.entity.getId());
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(replacement.getAbilityDurability().snapshot().modifiers().isEmpty());
    }

    @Test
    void current_player_leaving_the_scene_invalidates_saved_reports() throws Exception {
        durability(38);
        fixture.player.setScene(null);
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void snapshot_lists_are_immutable_and_do_not_change_after_a_later_update() throws Exception {
        durability(38);
        var previous = snapshot();
        assertThrows(UnsupportedOperationException.class, () -> previous.modifiers().clear());
        durability(12);
        assertEquals(38, previous.modifiers().get(0).update().getRemainingDurability());
        assertEquals(12, snapshot().modifiers().get(0).update().getRemainingDurability());
    }

    @Test
    void repeated_reports_are_safe_during_snapshot_reads() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writes =
                    pool.submit(
                            () -> {
                                for (int i = 0; i < 200; i++) {
                                    try {
                                        durability(i);
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    }
                                }
                            });
            var reads =
                    pool.submit(
                            () -> {
                                for (int i = 0; i < 200; i++)
                                    assertTrue(snapshot().modifiers().size() <= 1);
                            });
            writes.get(10, TimeUnit.SECONDS);
            reads.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        fixture.entity.invalidateAbilityLifecycle();
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @Test
    void duplicate_confirmation_of_the_same_controller_preserves_current_reports()
            throws Exception {
        install(
                "{abilityName:'Durability',modifiers:{Shield:{modifierMixins:[{$type:'ShieldBarMixin'},{$type:'AttachActionToModifierMixin',onAdded:[]}]}}}");
        ability.registerModifier(fixture.entity, controller);
        durability(38);
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        invoke(
                MODIFIER,
                head(71, 0),
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setModifierLocalId(0)
                        .build()
                        .toByteString());
        assertSame(controller, fixture.entity.getInstancedModifiers().get(71));
        assertEquals(38, snapshot().modifiers().get(0).update().getRemainingDurability());
        assertEquals(shield(4), snapshot().shieldBars().get(0).update());
    }

    @Test
    void modifier_reports_bind_to_the_actual_caster_on_another_bearer() throws Exception {
        var caster = new AbilityTestFixture.TestEntity(fixture.scene);
        caster.setId(80);
        fixture.scene.getEntities().put(caster.getId(), caster);
        ability = new Ability(ability.getData(), caster, fixture.player);
        caster.getInstancedAbilities().add(ability);
        controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Shield"));
        fixture.entity.getInstancedModifiers().put(71, controller);
        durability(38);
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        assertEquals(80, snapshot().modifiers().get(0).parentEntityId());
        assertEquals(80, snapshot().shieldBars().get(0).parentEntityId());
        caster.invalidateAbilityLifecycle();
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void named_dynamic_parent_uses_its_identity_instead_of_a_null_root_slot() throws Exception {
        fixture.entity.getInstancedAbilities().clear();
        fixture.entity.getInstancedAbilities().add(null);
        fixture.entity.getDynamicAbilities().put("Durability", ability);
        durability(38);
        assertEquals(1, snapshot().modifiers().size());
        fixture.entity
                .getDynamicAbilities()
                .put("Durability", new Ability(ability.getData(), fixture.entity, fixture.player));
        assertTrue(snapshot().modifiers().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void other_sources_do_not_overwrite_a_normal_shield_bar(int source) throws Exception {
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        invoke(
                SHIELD,
                head(71, shieldLocal()).setFPDAONBEBHKValue(source),
                shield(77).toByteString());
        assertEquals(shield(4), snapshot().shieldBars().get(0).update());
    }

    @Test
    void modifier_instance_can_identify_its_parent_without_an_ability_index() throws Exception {
        invoke(DURABILITY, head(71, 0).setInstancedAbilityId(0), update(4, 1).toByteString());
        assertEquals(1, snapshot().modifiers().size());
    }

    @Test
    void reports_from_an_invalidated_bearer_do_not_repopulate_its_state() throws Exception {
        fixture.entity.invalidateAbilityLifecycle();
        durability(38);
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
    }

    @Test
    void native_header_source_tag_keeps_serverbuff_out_of_the_normal_registry() throws Exception {
        // Current OIAPAJELNJP: modifier ID 3, ability ID 4, source enum 10.
        var nativeHead = AbilityInvokeEntryHead.parseFrom(new byte[] {24, 71, 32, 1, 80, 1});
        assertEquals(1, nativeHead.getFPDAONBEBHKValue());
        invoke(
                MODIFIER,
                nativeHead.toBuilder(),
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build()
                        .toByteString());
        assertSame(controller, fixture.entity.getInstancedModifiers().get(71));
    }

    @Test
    void separate_normal_instances_have_independent_values_and_removal() throws Exception {
        fixture.entity
                .getInstancedModifiers()
                .put(
                        72,
                        new AbilityModifierController(
                                ability, ability.getData(), controller.getModifierData()));
        durability(38);
        invoke(DURABILITY, head(72, 0), update(12, 0.5f).toByteString());
        assertEquals(2, snapshot().modifiers().size());
        assertEquals(
                Set.of(12f, 38f),
                new HashSet<>(
                        snapshot().modifiers().stream()
                                .map(x -> x.update().getRemainingDurability())
                                .toList()));
        invoke(
                MODIFIER,
                head(71, 0),
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build()
                        .toByteString());
        assertEquals(1, snapshot().modifiers().size());
        assertEquals(72, snapshot().modifiers().get(0).head().getInstancedModifierId());
    }

    @Test
    void a_replacement_created_by_modifier_add_does_not_inherit_old_reports() throws Exception {
        durability(38);
        invoke(SHIELD, head(71, shieldLocal()), shield(4).toByteString());
        invoke(
                MODIFIER,
                head(71, 0),
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setModifierLocalId(1)
                        .build()
                        .toByteString());
        assertNotSame(controller, fixture.entity.getInstancedModifiers().get(71));
        assertTrue(snapshot().modifiers().isEmpty());
        assertTrue(snapshot().shieldBars().isEmpty());
    }
}
