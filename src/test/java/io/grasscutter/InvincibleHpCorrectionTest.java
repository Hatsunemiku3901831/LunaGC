package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.State;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarFightPropNotifyOuterClass.AvatarFightPropNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

class InvincibleHpCorrectionTest {
    private AbilityTestFixture fixture;
    private Victim victim;

    private static class Victim extends AbilityTestFixture.TestEntity {
        int callbacks;

        Victim(AbilityTestFixture.TestScene scene) {
            super(scene);
        }

        @Override
        public void runLuaCallbacks(EntityDamageEvent event) {
            callbacks++;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        victim = new Victim(fixture.scene);
        victim.setId(43);
        fixture.scene.getEntities().put(victim.getId(), victim);
        victim.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 75.5f);
        protect(victim, "Invincible");
        fixture.scene.packets.clear();
        fixture.player.packets.clear();
    }

    @AfterEach
    void restore() {
        fixture.close();
    }

    private void protect(GameEntity entity, String state) {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Protection',modifiers:{Guard:{state:'" + state + "'}}}",
                        AbilityData.class);
        protect(entity, data, "Guard");
    }

    private void protect(GameEntity entity, AbilityData data, String name) {
        var ability = new Ability(data, fixture.entity, fixture.player);
        entity.getInstancedModifiers()
                .put(71, new AbilityModifierController(ability, data, data.modifiers.get(name)));
    }

    private List<EntityFightPropUpdateNotify> corrections() throws Exception {
        var result = new ArrayList<EntityFightPropUpdateNotify>();
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.EntityFightPropUpdateNotify)
                result.add(EntityFightPropUpdateNotify.parseFrom(packet.getData()));
        return result;
    }

    private void corrected(GameEntity entity, float hp) throws Exception {
        var messages = corrections();
        assertEquals(1, messages.size());
        assertEquals(entity.getId(), messages.get(0).getEntityId());
        assertEquals(
                Map.of(FightProperty.FIGHT_PROP_CUR_HP.getId(), hp),
                messages.get(0).getFightPropMapMap());
        assertEquals(hp, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    private void noDamageSideEffects() {
        assertEquals(0, victim.callbacks);
        assertEquals(
                0,
                fixture.scene
                        .getWorld()
                        .getDamageLog()
                        .snapshot()
                        .get("totalDamage")
                        .getAsDouble());
        assertTrue(victim.isAlive());
        assertTrue(
                fixture.scene.packets.stream()
                        .noneMatch(
                                packet ->
                                        packet.getOpcode()
                                                == PacketOpcodes
                                                        .EntityFightPropChangeReasonNotify));
    }

    @Test
    void a_protected_hit_returns_the_unchanged_authoritative_hp() throws Exception {
        victim.damage(5);
        corrected(victim, 75.5f);
        noDamageSideEffects();
    }

    @Test
    void even_a_lethal_prediction_receives_the_unchanged_hp() throws Exception {
        victim.damage(500);
        corrected(victim, 75.5f);
        noDamageSideEffects();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ability_hp_loss_still_respects_invincibility(boolean respectLockHp) throws Exception {
        victim.loseHpByAbility(20, respectLockHp);
        corrected(victim, 75.5f);
        noDamageSideEffects();
    }

    @Test
    void removing_protection_restores_the_normal_single_damage_update() throws Exception {
        victim.damage(5);
        corrected(victim, 75.5f);
        victim.getInstancedModifiers().clear();
        fixture.scene.packets.clear();
        victim.damage(5);
        corrected(victim, 70.5f);
        assertEquals(1, victim.callbacks);
    }

    @Test
    void a_zero_damage_entry_does_not_create_a_correction_stream() throws Exception {
        victim.damage(0);
        assertTrue(corrections().isEmpty());
        noDamageSideEffects();
    }

    @ParameterizedTest
    @ValueSource(floats = {-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void invalid_damage_does_not_emit_state(float amount) throws Exception {
        victim.damage(amount);
        assertTrue(corrections().isEmpty());
        noDamageSideEffects();
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void nonfinite_stored_hp_is_not_sent_as_a_correction(float hp) throws Exception {
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        victim.damage(5);
        assertTrue(corrections().isEmpty());
        noDamageSideEffects();
    }

    @Test
    void an_unloaded_entity_cannot_emit_an_old_hp_snapshot() throws Exception {
        fixture.scene.removeEntity(victim);
        fixture.scene.packets.clear();
        victim.damage(5);
        assertTrue(corrections().isEmpty());
    }

    @Test
    void a_reused_entity_id_cannot_receive_its_old_objects_hp() throws Exception {
        var replacement = new Victim(fixture.scene);
        replacement.setId(victim.getId());
        replacement.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 12);
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        victim.damage(5);
        assertTrue(corrections().isEmpty());
        assertEquals(12, replacement.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void lock_hp_already_emits_one_update_and_does_not_get_a_duplicate() throws Exception {
        protect(victim, "LockHP");
        victim.damage(500);
        corrected(victim, 75.5f);
        assertTrue(victim.isAlive());
    }

    @Test
    void an_unprotected_hit_keeps_its_existing_single_update() throws Exception {
        victim.getInstancedModifiers().clear();
        victim.damage(5);
        corrected(victim, 70.5f);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Electric", "Fire", "Grass", "Ice", "Rock", "Water", "Wind"})
    void current_effigy_invincible_states_return_hp_without_a_family_heuristic(String family)
            throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Effigy_"
                                                + family
                                                + ".json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(row -> row.Default)
                        .filter(row -> row.modifiers != null)
                        .filter(
                                row ->
                                        row.modifiers.values().stream()
                                                .anyMatch(
                                                        modifier ->
                                                                modifier.state == State.Invincible))
                        .findFirst()
                        .orElseThrow();
        String name =
                data.modifiers.entrySet().stream()
                        .filter(entry -> entry.getValue().state == State.Invincible)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        protect(victim, data, name);
        victim.damage(500);
        corrected(victim, 75.5f);
        noDamageSideEffects();
    }

    private EntityAvatar avatar(boolean visible) {
        var data =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return 1001;
                    }

                    @Override
                    public void save() {}
                };
        data.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        data.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 40);
        var entity = new EntityAvatar(fixture.scene, data);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        if (visible) fixture.scene.getEntities().put(entity.getId(), entity);
        protect(entity, "Invincible");
        return entity;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void current_party_members_also_receive_their_personal_avatar_snapshot(boolean visible)
            throws Exception {
        var entity = avatar(visible);
        entity.damage(10);
        corrected(entity, 40);
        var messages =
                fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarFightPropNotify)
                        .toList();
        assertEquals(1, messages.size());
        var body = AvatarFightPropNotify.parseFrom(messages.get(0).getData());
        assertEquals(1001, body.getAvatarGuid());
        assertEquals(40, body.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
    }

    @Test
    void a_removed_party_member_cannot_emit_a_personal_snapshot() throws Exception {
        var entity = avatar(false);
        fixture.player.getTeamManager().getActiveTeam().remove(entity);
        entity.damage(10);
        assertTrue(corrections().isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }
}
