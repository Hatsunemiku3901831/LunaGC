package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.game.ability.AbilityGlobalValueSnapshot;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Phlogiston;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.SyncTeamEntityNotifyOuterClass.SyncTeamEntityNotify;
import emu.grasscutter.server.packet.send.PacketSyncTeamEntityNotify;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Collectors;

class AbilityGlobalValueSnapshotTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private AbilitySyncStateInfo snapshot(GameEntity entity) {
        var info = AbilitySyncStateInfo.newBuilder();
        AbilityGlobalValueSnapshot.append(entity, info);
        return info.build();
    }

    private Map<String, Float> values(AbilitySyncStateInfo info) {
        return info.getSgvDynamicValueMapList().stream()
                .collect(
                        Collectors.toMap(
                                e ->
                                        e.getKey().hasStr()
                                                ? e.getKey().getStr()
                                                : "#"
                                                        + Integer.toUnsignedString(
                                                                e.getKey().getHash()),
                                AbilityScalarValueEntry::getFloatValue));
    }

    private AbilityScalarValueEntry value(String name, float value) {
        return AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder().setStr(name))
                .setFloatValue(value)
                .build();
    }

    @Test
    void current_runtime_sgvs_are_emitted_without_a_full_initialization_claim() {
        fixture.entity.getGlobalAbilityValues().put("SGV_PuzzlePhase", 3f);
        fixture.entity.getGlobalAbilityValues().put("SGV_Zero", 0f);
        fixture.entity.getGlobalAbilityValues().put("OrdinaryDynamic", 9f);
        var info = snapshot(fixture.entity);
        assertEquals(Map.of("SGV_PuzzlePhase", 3f, "SGV_Zero", 0f), values(info));
        assertFalse(info.getIsInited());
        assertTrue(info.getDynamicValueMapList().isEmpty());
        assertTrue(info.getAppliedModifiersList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void non_finite_values_do_not_poison_other_current_state(float value) {
        fixture.entity.getGlobalAbilityValues().put("SGV_Bad", value);
        fixture.entity.getGlobalAbilityValues().put("SGV_Good", -2f);
        assertEquals(Map.of("SGV_Good", -2f), values(snapshot(fixture.entity)));
    }

    @Test
    void empty_blank_and_non_server_names_do_not_become_sgvs() {
        fixture.entity.getGlobalAbilityValues().put("", 3f);
        fixture.entity.getGlobalAbilityValues().put(" ", 3f);
        fixture.entity.getGlobalAbilityValues().put("NotSGV_foo", 3f);
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
    }

    @Test
    void independent_native_field_six_fixture_contains_the_runtime_value() throws Exception {
        fixture.entity.getGlobalAbilityValues().put("SGV_A", 2.25f);
        byte[] wire = HexFormat.of().parseHex("320e0a070a055347565f411d00001040");
        assertArrayEquals(wire, snapshot(fixture.entity).toByteArray());
        assertEquals(Map.of("SGV_A", 2.25f), values(AbilitySyncStateInfo.parseFrom(wire)));
    }

    @Test
    void caller_computed_values_replace_runtime_aliases_without_duplicate_hashes() {
        fixture.entity.getGlobalAbilityValues().put(Phlogiston.TEAM_KEY, 999f);
        fixture.entity.getGlobalAbilityValues().put("SGV_Script", 8f);
        var authoritative =
                AbilityScalarValueEntry.newBuilder()
                        .setKey(
                                AbilityString.newBuilder()
                                        .setHash(Utils.abilityHash(Phlogiston.TEAM_KEY)))
                        .setFloatValue(47)
                        .build();
        var info = AbilitySyncStateInfo.newBuilder().addSgvDynamicValueMap(authoritative);
        AbilityGlobalValueSnapshot.append(fixture.entity, info);
        assertEquals(2, info.getSgvDynamicValueMapCount());
        assertEquals(
                Map.of(
                        "SGV_Script",
                        8f,
                        "#" + Integer.toUnsignedString(Utils.abilityHash(Phlogiston.TEAM_KEY)),
                        47f),
                values(info.build()));
        assertEquals(999f, fixture.entity.getGlobalAbilityValues().get(Phlogiston.TEAM_KEY));
    }

    @Test
    void existing_roots_dynamic_values_and_unknown_fields_are_preserved() {
        var unknown =
                UnknownFieldSet.newBuilder()
                        .addField(123, UnknownFieldSet.Field.newBuilder().addVarint(7).build())
                        .build();
        var existing = value("SGV_Existing", 5).toBuilder().mergeUnknownFields(unknown).build();
        var root = AbilityAppliedAbility.newBuilder().setInstancedAbilityId(9).build();
        var dynamic = value("OrdinaryDynamic", 4);
        var info =
                AbilitySyncStateInfo.newBuilder()
                        .setIsInited(true)
                        .addSgvDynamicValueMap(existing)
                        .addAppliedAbilities(root)
                        .addDynamicValueMap(dynamic)
                        .mergeUnknownFields(unknown);
        fixture.entity.getGlobalAbilityValues().put("SGV_New", 6f);
        AbilityGlobalValueSnapshot.append(fixture.entity, info);
        assertEquals(Map.of("SGV_Existing", 5f, "SGV_New", 6f), values(info.build()));
        assertTrue(info.getIsInited());
        assertEquals(List.of(root), info.getAppliedAbilitiesList());
        assertEquals(List.of(dynamic), info.getDynamicValueMapList());
        assertEquals(unknown, info.getUnknownFields());
        assertTrue(info.getSgvDynamicValueMapList().contains(existing));
    }

    @Test
    void ambiguous_names_are_not_chosen_by_map_iteration_order() {
        assertEquals(Utils.abilityHash("SGV_A£"), Utils.abilityHash("SGV_B "));
        fixture.entity.getGlobalAbilityValues().put("SGV_A£", 1f);
        fixture.entity.getGlobalAbilityValues().put("SGV_B ", 2f);
        fixture.entity.getGlobalAbilityValues().put("SGV_Good", 4f);
        assertEquals(Map.of("SGV_Good", 4f), values(snapshot(fixture.entity)));
    }

    @Test
    void filtering_one_bad_value_does_not_hide_a_name_collision() {
        fixture.entity.getGlobalAbilityValues().put("SGV_A£", 1f);
        fixture.entity.getGlobalAbilityValues().put("SGV_B ", Float.NaN);
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
    }

    @Test
    void removed_variables_do_not_reappear_from_an_old_snapshot() {
        fixture.entity.getGlobalAbilityValues().put("SGV_A", 2f);
        var before = snapshot(fixture.entity);
        fixture.entity.getGlobalAbilityValues().remove("SGV_A");
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
        assertEquals(Map.of("SGV_A", 2f), values(before));
    }

    @Test
    void reading_a_snapshot_does_not_update_values_or_emit_packets() {
        fixture.entity.getGlobalAbilityValues().put("SGV_A", 2f);
        var before = Map.copyOf(fixture.entity.getGlobalAbilityValues());
        fixture.player.packets.clear();
        fixture.scene.packets.clear();
        for (int i = 0; i < 10; i++) snapshot(fixture.entity);
        assertEquals(before, fixture.entity.getGlobalAbilityValues());
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void unregistered_and_replaced_entities_do_not_export_old_state() {
        fixture.entity.getGlobalAbilityValues().put("SGV_A", 2f);
        fixture.scene.getEntities().remove(fixture.entity.getId());
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
        var replacement = new AbilityTestFixture.TestEntity(fixture.scene);
        replacement.setId(fixture.entity.getId());
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
    }

    @Test
    void invalidated_lifecycle_does_not_export_state() {
        fixture.entity.getGlobalAbilityValues().put("SGV_A", 2f);
        fixture.entity.invalidateAbilityLifecycle();
        assertTrue(values(snapshot(fixture.entity)).isEmpty());
    }

    private EntityTeam team(AbilityTestFixture.TestPlayer player) {
        player.setWorld(fixture.scene.getWorld());
        player.setScene(fixture.scene);
        var team = new EntityTeam(player);
        player.getTeamManager().setEntity(team);
        if (!fixture.scene.getPlayers().contains(player)) fixture.scene.getPlayers().add(player);
        return team;
    }

    @Test
    void current_team_and_world_values_do_not_need_a_regular_entity_registration() {
        var team = team(fixture.player);
        team.getGlobalAbilityValues().put("SGV_Team", 2f);
        fixture.scene.getWorld().getEntity().getGlobalAbilityValues().put("SGV_World", 3f);
        assertEquals(Map.of("SGV_Team", 2f), values(snapshot(team)));
        assertEquals(
                Map.of("SGV_World", 3f), values(snapshot(fixture.scene.getWorld().getEntity())));
    }

    @Test
    void a_retired_team_cannot_export_values_into_its_replacement() {
        var old = team(fixture.player);
        old.getGlobalAbilityValues().put("SGV_Team", 2f);
        team(fixture.player);
        assertTrue(values(snapshot(old)).isEmpty());
    }

    private EntityAvatar avatar() {
        team(fixture.player);
        var model =
                new Avatar() {
                    @Override
                    public emu.grasscutter.game.player.Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public void save() {}
                };
        model.setFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP, 100);
        model.setFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP, 100);
        var avatar =
                new EntityAvatar(fixture.scene, model) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.player.getTeamManager().getActiveTeam().add(avatar);
        return avatar;
    }

    @Test
    void hidden_party_members_can_export_state_before_regular_registration() {
        var avatar = avatar();
        avatar.getGlobalAbilityValues().put("SGV_Avatar", 4f);
        assertEquals(Map.of("SGV_Avatar", 4f), values(snapshot(avatar)));
    }

    @Test
    void an_avatar_cannot_export_state_after_its_owner_changes_world() {
        var avatar = avatar();
        avatar.getGlobalAbilityValues().put("SGV_Avatar", 4f);
        assertEquals(Map.of("SGV_Avatar", 4f), values(snapshot(avatar)));
        fixture.player.setWorld(new World(fixture.scene.getWorld().getServer(), fixture.player));
        assertTrue(values(snapshot(avatar)).isEmpty());
    }

    @Test
    void a_weapon_cannot_export_state_after_its_caster_changes_world() {
        var avatar = avatar();
        var weapon = new EntityWeapon(fixture.scene, 5001, avatar.getAvatar());
        fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
        weapon.getGlobalAbilityValues().put("SGV_Weapon", 5f);
        assertEquals(Map.of("SGV_Weapon", 5f), values(snapshot(weapon)));
        fixture.player.setWorld(new World(fixture.scene.getWorld().getServer(), fixture.player));
        assertTrue(values(snapshot(weapon)).isEmpty());
    }

    @Test
    void multiplayer_sync_uses_each_owners_runtime_values_and_fuel() throws Exception {
        var ownerTeam = team(fixture.player);
        ownerTeam.getGlobalAbilityValues().put("SGV_Owner", 2f);
        fixture.player.setPhlogistonValue(47);
        var guest = new AbilityTestFixture.TestPlayer();
        var guestTeam = team(guest);
        guestTeam.getGlobalAbilityValues().put("SGV_Guest", 3f);
        guestTeam.getGlobalAbilityValues().put(Phlogiston.TEAM_KEY, 999f);
        guest.setPhlogistonValue(88);
        var world = fixture.scene.getWorld();
        var multiplayer = World.class.getDeclaredField("isMultiplayer");
        multiplayer.setAccessible(true);
        multiplayer.set(world, true);
        world.getPlayers().add(fixture.player);
        world.getPlayers().add(guest);
        var packet =
                SyncTeamEntityNotify.parseFrom(
                        new PacketSyncTeamEntityNotify(fixture.player).getData());
        assertEquals(1, packet.getTeamEntityInfoListCount());
        var entry = packet.getTeamEntityInfoList(0);
        assertEquals(guestTeam.getId(), entry.getTeamEntityId());
        var globals = values(entry.getTeamAbilityInfo());
        assertEquals(3f, globals.get("SGV_Guest"));
        assertFalse(globals.containsKey("SGV_Owner"));
        assertEquals(88f, globals.get(Phlogiston.TEAM_KEY));
        assertFalse(entry.getTeamAbilityInfo().getIsInited());
    }
}
