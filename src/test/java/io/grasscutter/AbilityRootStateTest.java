package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionSetOverrideMapValue;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

class AbilityRootStateTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private AbilityData data;
    private AbilityData previous;
    private static final String NAME = "Root_State_Test";

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        data =
                JsonUtils.decode(
                        "{abilityName:'"
                                + NAME
                                + "',abilitySpecials:{rate:7},modifiers:{Old:{state:'Invincible'}}}",
                        AbilityData.class);
        previous = GameData.getAbilityDataMap().put(NAME, data);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    @AfterEach
    void close() {
        if (previous == null) GameData.getAbilityDataMap().remove(NAME);
        else GameData.getAbilityDataMap().put(NAME, previous);
        fixture.close();
    }

    private AbilityScalarValueEntry scalar(String key, float value) {
        return AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder().setStr(key))
                .setFloatValue(value)
                .build();
    }

    private boolean invoke(int id, int source, AbilityInvokeArgument argument, ByteString payload)
            throws Exception {
        return fixture.player
                .getAbilityManager()
                .acceptAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(id)
                                                .setFPDAONBEBHKValue(source))
                                .setArgumentType(argument)
                                .setAbilityData(payload)
                                .build());
    }

    private boolean add(int headerId, int source, AbilityAppliedAbility body) throws Exception {
        return invoke(
                headerId,
                source,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_ADD_NEW_ABILITY,
                AbilityMetaAddAbility.newBuilder().setAbility(body).build().toByteString());
    }

    private AbilityAppliedAbility.Builder root(String name, int id) {
        return AbilityAppliedAbility.newBuilder()
                .setAbilityName(AbilityString.newBuilder().setStr(name))
                .setInstancedAbilityId(id);
    }

    private List<AbilityAppliedAbility> roots() {
        return fixture.entity.getAbilityRoots().snapshot();
    }

    private Map<String, Float> values(AbilityAppliedAbility root) {
        var result = new HashMap<String, Float>();
        for (var value : root.getOverrideMapList()) {
            String key =
                    value.getKey().hasStr()
                            ? value.getKey().getStr()
                            : "#" + Integer.toUnsignedString(value.getKey().getHash());
            result.put(key, value.getFloatValue());
        }
        return result;
    }

    @Test
    void installed_roots_keep_their_slot_and_live_server_parameters() {
        ability.getAbilitySpecials().put("rate", 4);
        assertEquals(1, roots().get(0).getInstancedAbilityId());
        assertEquals(Map.of("rate", 4f), values(roots().get(0)));
    }

    @Test
    void sparse_unknown_root_keeps_description_override_and_unknown_fields() throws Exception {
        var extra =
                UnknownFieldSet.newBuilder()
                        .addField(55, UnknownFieldSet.Field.newBuilder().addVarint(123).build())
                        .build();
        var raw =
                root("Unknown_Root", 3)
                        .setAbilityOverride(AbilityString.newBuilder().setStr("Custom"))
                        .addOverrideMap(scalar("foreign", 2))
                        .setUnknownFields(extra)
                        .build();
        assertTrue(add(3, 0, raw));
        assertEquals(
                List.of(1, 3),
                roots().stream().map(AbilityAppliedAbility::getInstancedAbilityId).toList());
        assertEquals(raw, roots().get(1));
    }

    @Test
    void unknown_replacement_clears_the_old_executable_slot_and_its_modifiers() throws Exception {
        ability.registerModifier(
                fixture.entity,
                new AbilityModifierController(ability, data, data.modifiers.get("Old")));
        assertTrue(add(1, 0, root("Unknown_Root", 1).build()));
        assertNull(fixture.entity.getInstancedAbilities().get(0));
        assertTrue(ability.getModifiers().isEmpty());
        assertEquals("Unknown_Root", roots().get(0).getAbilityName().getStr());
    }

    @Test
    void a_zero_body_id_is_normalized_to_the_header_without_moving_the_slot() throws Exception {
        add(3, 0, root("Unknown_Root", 0).build());
        assertEquals(3, roots().get(1).getInstancedAbilityId());
        assertEquals(3, fixture.entity.getInstancedAbilities().size());
    }

    @Test
    void conflicting_body_id_cannot_replace_a_different_header_slot() throws Exception {
        assertFalse(add(1, 0, root("Unknown_Root", 2).build()));
        assertSame(ability, fixture.entity.getInstancedAbilities().get(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void other_source_namespaces_cannot_replace_normal_roots(int source) throws Exception {
        add(1, source, root("Unknown_Root", 1).build());
        assertSame(ability, fixture.entity.getInstancedAbilities().get(0));
    }

    @Test
    void unknown_root_parameter_updates_are_retained_without_executing_it() throws Exception {
        add(3, 0, root("Unknown_Root", 3).addOverrideMap(scalar("old", 1)).build());
        invoke(
                3,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM,
                scalar("rate", 4).toByteString());
        invoke(
                3,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP,
                AbilityMetaReInitOverrideMap.newBuilder()
                        .addOverrideMap(scalar("rate", 0))
                        .build()
                        .toByteString());
        assertNull(fixture.entity.getInstancedAbilities().get(2));
        assertEquals(0, values(roots().get(1)).get("rate"));
    }

    @Test
    void known_parameter_update_and_server_action_are_visible_in_the_next_snapshot()
            throws Exception {
        invoke(
                1,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM,
                scalar("rate", 4).toByteString());
        assertEquals(4, values(roots().get(0)).get("rate"));
        var action =
                JsonUtils.decode(
                        "{$type:'SetOverrideMapValue',overrideMapKey:'rate',value:5}",
                        AbilityModifier.AbilityModifierAction.class);
        assertTrue(
                new ActionSetOverrideMapValue()
                        .execute(ability, action, ByteString.EMPTY, fixture.entity));
        assertEquals(5, values(roots().get(0)).get("rate"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 99})
    void other_sources_cannot_update_normal_parameters(int source) throws Exception {
        invoke(
                1,
                source,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM,
                scalar("rate", 4).toByteString());
        assertEquals(7, ability.getAbilitySpecials().getFloat("rate"));
    }

    @Test
    void stored_hashed_parameters_do_not_duplicate_resolved_string_keys() throws Exception {
        var key = AbilityString.newBuilder().setHash(Utils.abilityHash("rate"));
        add(
                1,
                0,
                root(NAME, 1)
                        .addOverrideMap(
                                AbilityScalarValueEntry.newBuilder().setKey(key).setFloatValue(4))
                        .build());
        assertEquals(1, roots().get(0).getOverrideMapCount());
        assertEquals(4, roots().get(0).getOverrideMap(0).getFloatValue());
    }

    @Test
    void invalid_float_reports_do_not_poison_a_root_snapshot() throws Exception {
        add(1, 0, root(NAME, 1).addOverrideMap(scalar("bad", Float.NaN)).build());
        ability.getAbilitySpecials().put("bad2", Float.POSITIVE_INFINITY);
        assertEquals(Map.of("rate", 7f), values(roots().get(0)));
    }

    @Test
    void root_replacement_does_not_inherit_raw_fields_or_unknown_parameters() throws Exception {
        add(3, 0, root("Unknown_Root", 3).addOverrideMap(scalar("foreign", 2)).build());
        add(3, 0, root(NAME, 3).build());
        assertEquals(Map.of("rate", 7f), values(roots().get(1)));
    }

    @Test
    void structural_list_changes_invalidate_unknown_slots_instead_of_shifting_their_ids()
            throws Exception {
        add(3, 0, root("Unknown_Root", 3).build());
        fixture.entity
                .getInstancedAbilities()
                .add(0, new Ability(data, fixture.entity, fixture.player));
        assertEquals(
                List.of(1, 2),
                roots().stream().map(AbilityAppliedAbility::getInstancedAbilityId).toList());
    }

    @Test
    void dead_or_replaced_entity_does_not_emit_old_root_metadata() throws Exception {
        add(3, 0, root("Unknown_Root", 3).build());
        fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.activateAbilityLifecycle();
        assertTrue(roots().isEmpty());
    }

    @Test
    void root_lists_are_immutable_snapshots_of_the_values_at_read_time() {
        var before = roots();
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        ability.getAbilitySpecials().put("rate", 4);
        assertEquals(7, values(before.get(0)).get("rate"));
        assertEquals(4, values(roots().get(0)).get("rate"));
    }

    @Test
    void partial_sync_merges_live_parameters_with_talent_unlocks_without_full_init() {
        var info =
                AbilitySyncStateInfo.newBuilder()
                        .addAppliedAbilities(
                                AbilityAppliedAbility.newBuilder()
                                        .setAbilityName(
                                                AbilityString.newBuilder()
                                                        .setHash(Utils.abilityHash(NAME)))
                                        .addOverrideMap(scalar("Unlocked", 1)));
        ability.getAbilitySpecials().put("rate", 4);
        fixture.entity.getAbilityRoots().appendOverrides(info);
        assertFalse(info.getIsInited());
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(1, info.getAppliedAbilities(0).getInstancedAbilityId());
        assertEquals(Map.of("rate", 4f, "Unlocked", 1f), values(info.getAppliedAbilities(0)));
    }

    @Test
    void duplicate_names_keep_separate_ids_in_partial_sync() {
        ability.getAbilitySpecials().put("rate", 4);
        var second = new Ability(data, fixture.entity, fixture.player);
        second.getAbilitySpecials().put("rate", 5);
        fixture.entity.getInstancedAbilities().add(second);
        assertEquals(
                List.of(1, 2),
                roots().stream().map(AbilityAppliedAbility::getInstancedAbilityId).toList());
        assertEquals(
                List.of(1, 2),
                fixture.entity.getAbilityRoots().toSyncState().getAppliedAbilitiesList().stream()
                        .map(AbilityAppliedAbility::getInstancedAbilityId)
                        .toList());
    }

    @Test
    void concurrent_server_parameter_writes_and_snapshot_reads_do_not_break_iteration()
            throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writes =
                    pool.submit(
                            () -> {
                                for (int i = 0; i < 200; i++)
                                    ability.getAbilitySpecials().put("key" + i, i);
                            });
            var reads =
                    pool.submit(
                            () -> {
                                for (int i = 0; i < 200; i++) assertEquals(1, roots().size());
                            });
            writes.get(10, TimeUnit.SECONDS);
            reads.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void weapon_affixes_do_not_claim_an_empty_complete_ability_snapshot() {
        var itemData =
                JsonUtils.decode(
                        "{id:11101,itemType:'ITEM_WEAPON',equipType:'EQUIP_WEAPON',gadgetId:5001,skillAffix:[123]}",
                        emu.grasscutter.data.excels.ItemData.class);
        var item = new emu.grasscutter.game.inventory.GameItem(itemData);
        assertFalse(item.createSceneWeaponInfo().getAbilityInfo().getIsInited());
    }

    @Test
    void removing_a_later_root_retains_an_unchanged_custom_override() throws Exception {
        add(
                1,
                0,
                root(NAME, 1)
                        .setAbilityOverride(AbilityString.newBuilder().setStr("Custom"))
                        .build());
        var second = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(second);
        fixture.entity.getInstancedAbilities().removeIf(a -> a == second);
        assertEquals("Custom", roots().get(0).getAbilityOverride().getStr());
    }

    @Test
    void stable_sort_retains_unknown_root_metadata() throws Exception {
        add(3, 0, root("Unknown_Root", 3).addOverrideMap(scalar("foreign", 2)).build());
        fixture.entity.getInstancedAbilities().sort((first, last) -> 0);
        assertEquals(
                List.of(1, 3),
                roots().stream().map(AbilityAppliedAbility::getInstancedAbilityId).toList());
    }

    @Test
    void a_hashed_parameter_collision_cannot_choose_one_of_two_names() throws Exception {
        assertEquals(Utils.abilityHash("A£"), Utils.abilityHash("B "));
        ability.getAbilitySpecials().put("A£", 1);
        ability.getAbilitySpecials().put("B ", 2);
        var value =
                AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setHash(Utils.abilityHash("A£")))
                        .setFloatValue(99);
        invoke(
                1,
                0,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM,
                value.build().toByteString());
        assertEquals(1, ability.getAbilitySpecials().getFloat("A£"));
        assertEquals(2, ability.getAbilitySpecials().getFloat("B "));
        assertTrue(
                fixture.entity.getAbilityRoots().toSyncState().getAppliedAbilitiesList().isEmpty());
    }

    @Test
    void filtering_an_unchanged_default_cannot_hide_a_parameter_collision() {
        data.abilitySpecials.put("A£", 1f);
        data.abilitySpecials.put("B ", 2f);
        ability.getAbilitySpecials().put("A£", 4f);
        ability.getAbilitySpecials().put("B ", 2f);
        assertTrue(
                fixture.entity.getAbilityRoots().toSyncState().getAppliedAbilitiesList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void filtering_a_non_finite_value_cannot_hide_its_colliding_name(float bad) {
        ability.getAbilitySpecials().put("A£", 4f);
        ability.getAbilitySpecials().put("B ", bad);
        ability.getAbilitySpecials().put("rate", 3f);
        var info = fixture.entity.getAbilityRoots().toSyncState();
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(Map.of("rate", 3f), values(info.getAppliedAbilities(0)));
    }

    private AbilitySyncStateInfo.Builder template(String name, int id) {
        return AbilitySyncStateInfo.newBuilder()
                .addAppliedAbilities(root(NAME, id).addOverrideMap(scalar(name, 8)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void a_caller_parameter_cannot_alias_a_different_known_default(int id) {
        data.abilitySpecials.put("A£", 1f);
        ability.getAbilitySpecials().put("A£", 1f);
        var info = template("B ", id);
        fixture.entity.getAbilityRoots().appendOverrides(info);
        assertTrue(info.getAppliedAbilitiesList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void merging_caller_and_runtime_parameters_keeps_unambiguous_values(int id) {
        ability.getAbilitySpecials().put("A£", 4f);
        ability.getAbilitySpecials().put("rate", 3f);
        var info = template("B ", id);
        fixture.entity.getAbilityRoots().appendOverrides(info);
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(1, info.getAppliedAbilities(0).getInstancedAbilityId());
        assertEquals(Map.of("rate", 3f), values(info.getAppliedAbilities(0)));
    }

    @Test
    void duplicate_caller_templates_are_checked_before_hash_deduplication() {
        var info = template("A£", 0);
        info.addAppliedAbilities(
                root(NAME, 0)
                        .addOverrideMap(scalar("B ", 2))
                        .addOverrideMap(scalar("Unlocked", 1)));
        fixture.entity.getAbilityRoots().appendOverrides(info);
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(Map.of("Unlocked", 1f), values(info.getAppliedAbilities(0)));
    }

    @Test
    void an_unknown_root_retains_good_parameters_without_changing_its_raw_record()
            throws Exception {
        var raw =
                root("Unknown_Root", 3)
                        .addOverrideMap(scalar("A£", 1))
                        .addOverrideMap(scalar("B ", 2))
                        .addOverrideMap(scalar("Good", 3))
                        .build();
        assertTrue(add(3, 0, raw));
        var info = fixture.entity.getAbilityRoots().toSyncState();
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(3, info.getAppliedAbilities(0).getInstancedAbilityId());
        assertEquals(Map.of("Good", 3f), values(info.getAppliedAbilities(0)));
        assertEquals(raw, roots().get(1));
    }

    @Test
    void independent_roots_do_not_share_parameter_name_scopes() {
        ability.getAbilitySpecials().put("A£", 1f);
        var otherData =
                JsonUtils.decode(
                        "{abilityName:'Other_Root',abilitySpecials:{}}", AbilityData.class);
        var other = new Ability(otherData, fixture.entity, fixture.player);
        other.getAbilitySpecials().put("B ", 2f);
        fixture.entity.getInstancedAbilities().add(other);
        var info = fixture.entity.getAbilityRoots().toSyncState();
        assertEquals(2, info.getAppliedAbilitiesCount());
        assertEquals(Map.of("A£", 1f), values(info.getAppliedAbilities(0)));
        assertEquals(Map.of("B ", 2f), values(info.getAppliedAbilities(1)));
    }

    @Test
    void a_matching_hash_alias_still_merges_with_its_single_known_name() {
        ability.getAbilitySpecials().put("rate", 3f);
        var info =
                AbilitySyncStateInfo.newBuilder()
                        .addAppliedAbilities(
                                root(NAME, 0)
                                        .addOverrideMap(
                                                AbilityScalarValueEntry.newBuilder()
                                                        .setKey(
                                                                AbilityString.newBuilder()
                                                                        .setHash(
                                                                                Utils.abilityHash(
                                                                                        "rate")))
                                                        .setFloatValue(8)));
        fixture.entity.getAbilityRoots().appendOverrides(info);
        assertEquals(1, info.getAppliedAbilitiesCount());
        assertEquals(Map.of("rate", 3f), values(info.getAppliedAbilities(0)));
    }

    @Test
    void departed_caster_does_not_emit_previous_scene_roots() {
        fixture.player.setScene(null);
        assertTrue(roots().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, Integer.MAX_VALUE})
    void invalid_allocation_ids_are_rejected_before_resizing_the_root_list(int id)
            throws Exception {
        assertFalse(add(id, 0, root("Unknown_Root", 0).build()));
        assertEquals(1, fixture.entity.getInstancedAbilities().size());
    }

    @Test
    void untouched_resource_defaults_are_not_sent_over_client_initialization() {
        assertEquals(Map.of("rate", 7f), values(roots().get(0)));
        assertTrue(
                fixture.entity.getAbilityRoots().toSyncState().getAppliedAbilitiesList().isEmpty());
    }

    @Test
    void an_explicit_reset_to_a_resource_default_is_still_restored() {
        var action =
                JsonUtils.decode(
                        "{$type:'SetOverrideMapValue',overrideMapKey:'rate',value:4}",
                        AbilityModifier.AbilityModifierAction.class);
        assertTrue(
                new ActionSetOverrideMapValue()
                        .execute(ability, action, ByteString.EMPTY, fixture.entity));
        action =
                JsonUtils.decode(
                        "{$type:'SetOverrideMapValue',overrideMapKey:'rate',value:7}",
                        AbilityModifier.AbilityModifierAction.class);
        assertTrue(
                new ActionSetOverrideMapValue()
                        .execute(ability, action, ByteString.EMPTY, fixture.entity));
        assertEquals(
                7,
                values(fixture.entity.getAbilityRoots().toSyncState().getAppliedAbilities(0))
                        .get("rate"));
    }
}
