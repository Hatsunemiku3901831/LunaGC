package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityAppearNotifyOuterClass.SceneEntityAppearNotify;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneNPC;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class SceneEntityAbilityStateTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private EntityMonster monster(int weapon) {
        var data =
                JsonUtils.decode(
                        "{id:21010101,type:'MONSTER_ORDINARY',affix:[],equips:[],hpDrops:[],weaponId:"
                                + weapon
                                + "}",
                        MonsterData.class);
        return new EntityMonster(fixture.scene, data, new Position(), new Position(), 1) {
            @Override
            public void recalcStats() {}

            @Override
            public void initAbilities() {}
        };
    }

    private GameEntity entity(String type) {
        GameEntity entity =
                switch (type) {
                    case "gadget" -> new EntityGadget(fixture.scene, 42004010, new Position());
                    case "client" -> new EntityClientGadget(
                            fixture.scene,
                            fixture.player,
                            EvtCreateGadgetNotify.newBuilder()
                                    .setEntityId(70001)
                                    .setConfigId(42004010)
                                    .build());
                    case "item" -> new EntityItem(
                            fixture.scene,
                            fixture.player,
                            JsonUtils.decode(
                                    "{id:100001,itemType:'ITEM_MATERIAL',gadgetId:70500011}",
                                    ItemData.class),
                            new Position(1, 2, 3),
                            1);
                    case "vehicle" -> new EntityVehicle(
                            fixture.scene,
                            fixture.player,
                            45001001,
                            1,
                            new Position(),
                            new Position());
                    case "npc" -> {
                        var npc = new SceneNPC();
                        npc.npc_id = 1001;
                        npc.group = new SceneGroup();
                        npc.pos = new Position();
                        npc.rot = new Position();
                        yield new EntityNPC(fixture.scene, npc, 1, 1);
                    }
                    case "monster" -> monster(0);
                    default -> throw new IllegalArgumentException(type);
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private Ability state(GameEntity entity, String name, float phase) {
        var ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'"
                                        + name
                                        + "',abilitySpecials:{rate:1},modifiers:{}}",
                                AbilityData.class),
                        entity,
                        fixture.player);
        entity.getInstancedAbilities().add(ability);
        ability.getAbilitySpecials().put("rate", phase);
        entity.getGlobalAbilityValues().put("SGV_Phase", phase);
        entity.getGlobalAbilityValues().put("SGV_Zero", 0f);
        entity.getGlobalAbilityValues().put("SGV_Invalid", Float.NaN);
        entity.getGlobalAbilityValues().put("Ordinary", 99f);
        return ability;
    }

    private AbilitySyncStateInfo info(GameEntity entity) {
        return entity.toProto().getEntityAuthorityInfo().getAbilityInfo();
    }

    private void assertState(AbilitySyncStateInfo info, String name, float phase) {
        assertFalse(info.getIsInited(), "A partial state must allow client ability initialization");
        assertEquals(1, info.getAppliedAbilitiesCount());
        var ability = info.getAppliedAbilities(0);
        assertEquals(1, ability.getInstancedAbilityId());
        assertEquals(name, ability.getAbilityName().getStr());
        assertEquals(1, ability.getOverrideMapCount());
        assertEquals("rate", ability.getOverrideMap(0).getKey().getStr());
        assertEquals(phase, ability.getOverrideMap(0).getFloatValue());
        var values = new HashMap<String, Float>();
        info.getSgvDynamicValueMapList()
                .forEach(v -> values.put(v.getKey().getStr(), v.getFloatValue()));
        assertEquals(Map.of("SGV_Phase", phase, "SGV_Zero", 0f), values);
        assertEquals(0, info.getAppliedModifiersCount());
        assertEquals(0, info.getMixinRecoverInfosCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void current_roots_and_sgvs_reach_actual_entity_authority_blocks(String type) throws Exception {
        var entity = entity(type);
        state(entity, "State_" + type, 3);
        assertState(info(entity), "State_" + type, 3);
        // Scan the actual outer bytes independently of the generated authority getters.
        var authority = field(entity.toProto().toByteArray(), 21);
        var rawState = field(authority, 1);
        assertArrayEquals(info(entity).toByteArray(), rawState);
        assertNotNull(field(rawState, 3));
        assertNotNull(field(rawState, 6));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void repeated_serialization_uses_current_values_without_mutating_existing_packet(String type) {
        var entity = entity(type);
        var ability = state(entity, "Live", 2);
        var before = info(entity);
        ability.getAbilitySpecials().put("rate", 5);
        entity.getGlobalAbilityValues().put("SGV_Phase", 5f);
        assertState(info(entity), "Live", 5);
        assertState(before, "Live", 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void removed_or_replaced_entity_cannot_restore_previous_ability_lifetime(String type) {
        var entity = entity(type);
        state(entity, "Old", 2);
        var replacement = new AbilityTestFixture.TestEntity(fixture.scene);
        replacement.setId(entity.getId());
        fixture.scene.getEntities().put(entity.getId(), replacement);
        assertEquals(AbilitySyncStateInfo.getDefaultInstance(), info(entity));
        fixture.scene.getEntities().put(entity.getId(), entity);
        entity.invalidateAbilityLifecycle();
        assertEquals(AbilitySyncStateInfo.getDefaultInstance(), info(entity));
        entity.activateAbilityLifecycle();
        assertEquals(0, info(entity).getAppliedAbilitiesCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void uninitialized_entity_keeps_empty_state_and_does_not_create_roots(String type) {
        var entity = entity(type);
        assertEquals(AbilitySyncStateInfo.getDefaultInstance(), info(entity));
        assertTrue(entity.getInstancedAbilities().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void server_variables_are_sent_even_without_an_applied_root(String type) {
        var entity = entity(type);
        entity.getGlobalAbilityValues().put("SGV_Phase", 7f);
        var state = info(entity);
        assertFalse(state.getIsInited());
        assertEquals(0, state.getAppliedAbilitiesCount());
        assertEquals(1, state.getSgvDynamicValueMapCount());
        assertEquals("SGV_Phase", state.getSgvDynamicValueMap(0).getKey().getStr());
        assertEquals(7f, state.getSgvDynamicValueMap(0).getFloatValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadget", "client", "item", "vehicle", "npc", "monster"})
    void client_reported_unknown_roots_keep_original_override_and_unknown_fields(String type)
            throws Exception {
        var entity = entity(type);
        var raw =
                AbilityAppliedAbility.newBuilder()
                        .setInstancedAbilityId(3)
                        .setAbilityName(AbilityString.newBuilder().setStr("Unknown_Remote_Root"))
                        .setAbilityOverride(AbilityString.newBuilder().setStr("Original_Override"))
                        // The uninitialized native path consumes named parameter overrides.
                        .addOverrideMap(
                                AbilityScalarValueEntry.newBuilder()
                                        .setKey(
                                                AbilityString.newBuilder()
                                                        .setStr("remote_parameter"))
                                        .setFloatValue(4))
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(
                                                66,
                                                UnknownFieldSet.Field.newBuilder()
                                                        .addVarint(123)
                                                        .build())
                                        .build())
                        .build();
        assertTrue(
                fixture.player
                        .getAbilityManager()
                        .acceptAbilityInvoke(
                                AbilityInvokeEntry.newBuilder()
                                        .setEntityId(entity.getId())
                                        .setHead(
                                                AbilityInvokeEntryHead.newBuilder()
                                                        .setInstancedAbilityId(3))
                                        .setArgumentType(
                                                AbilityInvokeArgument
                                                        .AbilityInvokeArgument_ABILITY_META_ADD_NEW_ABILITY)
                                        .setAbilityData(
                                                AbilityMetaAddAbility.newBuilder()
                                                        .setAbility(raw)
                                                        .build()
                                                        .toByteString())
                                        .build()));
        assertEquals(List.of(raw), info(entity).getAppliedAbilitiesList());
        assertFalse(info(entity).getIsInited());
    }

    @Test
    void single_and_group_appearance_packets_keep_each_entitys_own_state() throws Exception {
        var entities = new ArrayList<GameEntity>();
        for (var type : List.of("gadget", "client", "item", "vehicle", "npc", "monster")) {
            var entity = entity(type);
            state(entity, type, entities.size() + 2);
            entities.add(entity);
            var single =
                    SceneEntityAppearNotify.parseFrom(
                            new PacketSceneEntityAppearNotify(entity).getData());
            assertState(
                    single.getEntityList(0).getEntityAuthorityInfo().getAbilityInfo(),
                    type,
                    entities.size() + 1);
        }
        var group =
                SceneEntityAppearNotify.parseFrom(
                        new PacketSceneEntityAppearNotify(
                                        entities, VisionType.VisionType_VISION_MEET)
                                .getData());
        assertEquals(6, group.getEntityListCount());
        for (int i = 0; i < entities.size(); i++)
            assertEquals(entities.get(i).toProto(), group.getEntityList(i));
    }

    @Test
    void monster_weapon_uses_weapon_roots_and_sgvs_without_inheriting_monster_state()
            throws Exception {
        var monster = monster(50001001);
        fixture.scene.getEntities().put(monster.getId(), monster);
        assertNotNull(monster.getWeaponEntity());
        state(monster, "Monster", 2);
        state(monster.getWeaponEntity(), "Weapon", 4);
        assertState(info(monster), "Monster", 2);
        var weapon = monster.toProto().getMonster().getWeaponList(0);
        assertEquals(monster.getWeaponEntity().getId(), weapon.getEntityId());
        assertState(weapon.getAbilityInfo(), "Weapon", 4);
        assertArrayEquals(weapon.getAbilityInfo().toByteArray(), field(weapon.toByteArray(), 7));
        monster.getWeaponEntity().invalidateAbilityLifecycle();
        assertEquals(
                AbilitySyncStateInfo.getDefaultInstance(),
                monster.toProto().getMonster().getWeaponList(0).getAbilityInfo());
        assertState(info(monster), "Monster", 2);
    }

    private byte[] field(byte[] bytes, int number) throws Exception {
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0; ) {
            if (tag >>> 3 == number) {
                assertEquals(2, tag & 7);
                return input.readByteArray();
            }
            input.skipField(tag);
        }
        fail("Missing field " + number);
        return null;
    }
}
