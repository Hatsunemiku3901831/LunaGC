package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

class SceneEntityLookupTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.scene.getPlayers().add(fixture.player);
        assertNull(fixture.player.getTeamManager().getEntity());
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void player_arrival_does_not_hide_an_already_registered_entity() {
        assertSame(fixture.entity, fixture.scene.getEntityById(fixture.entity.getId()));
    }

    @Test
    void an_unknown_entity_stays_absent_during_team_initialization() {
        assertNull(fixture.scene.getEntityById(7654321));
    }

    @Test
    void weapon_roots_are_readable_before_the_players_team_entity_exists() {
        var weapon = new EntityWeapon(fixture.scene, 50016001);
        fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
        var data =
                JsonUtils.decode(
                        "{abilityName:'Scene_Lookup_Weapon',abilitySpecials:{rate:1}}",
                        AbilityData.class);
        var ability = new Ability(data, weapon, fixture.player);
        weapon.getInstancedAbilities().add(ability);
        ability.getAbilitySpecials().put("rate", 3f);
        assertSame(weapon, fixture.scene.getEntityById(weapon.getId()));
        var roots = weapon.getAbilityRoots().toSyncState();
        assertEquals(1, roots.getAppliedAbilitiesCount());
        assertEquals(3f, roots.getAppliedAbilities(0).getOverrideMap(0).getFloatValue());
        assertFalse(roots.getIsInited());
    }

    @Test
    void an_initializing_player_does_not_hide_another_players_live_team() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setWorld(fixture.scene.getWorld());
        other.setScene(fixture.scene);
        var team = new EntityTeam(other);
        other.getTeamManager().setEntity(team);
        fixture.scene.getPlayers().add(other);
        assertSame(team, fixture.scene.getEntityById(team.getId()));
        other.getTeamManager().setEntity(null);
        assertNull(fixture.scene.getEntityById(team.getId()));
    }

    @Test
    void world_entity_remains_available_during_player_arrival() {
        var world = fixture.scene.getWorld();
        assertSame(world.getEntity(), fixture.scene.getEntityById(world.getLevelEntityId()));
    }
}
