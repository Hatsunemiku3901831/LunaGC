package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

class InfinitePhlogistonTest {
    PhlogistonMutationTest env;
    Ability ability;
    EntityTeam team;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        team = env.fixture.player.getTeamManager().getEntity();
    }

    @AfterEach
    void close() {
        env.close();
    }

    AbilityModifierController install(GameEntity target, String config) {
        var data =
                JsonUtils.decode(
                        "{abilityName:'InfinitePhlogiston_Test',modifiers:{Infinite:{modifierMixins:["
                                + config
                                + "]}}}",
                        AbilityData.class);
        ability = new Ability(data, target, env.fixture.player);
        var controller =
                new AbilityModifierController(ability, data, data.modifiers.get("Infinite"));
        ability.registerModifier(target, controller);
        return controller;
    }

    @Test
    void liveTeamModifierSuppressesCostWithoutFillingTank() {
        install(team, "{$type:'InfinitePhlogistonMixin'}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void removingInfiniteModifierRestoresNormalCosts() {
        install(team, "{$type:'InfinitePhlogistonMixin'}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        ability.removeModifier(team, "Infinite");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void disabledInfiniteModifierHasNoEffect() {
        install(team, "{$type:'InfinitePhlogistonMixin',enable:false}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void avatarBearerDoesNotCreateAnUnverifiedTeamBlocker() {
        install(env.caster, "{$type:'InfinitePhlogistonMixin'}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void multipleLiveInstancesKeepCostBlockedUntilLastRemoval() {
        var first = install(team, "{$type:'InfinitePhlogistonMixin'}");
        team.getInstancedModifiers().put(201, first);
        var second =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Infinite"));
        team.getInstancedModifiers().put(202, second);
        ability.registerModifier(team, second);
        team.getInstancedModifiers().remove(201);
        ability.unregisterModifier(team, first);
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        team.getInstancedModifiers().remove(202);
        ability.unregisterModifier(team, second);
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void teamInfiniteAlsoPreventsItsSaurianCost() {
        install(team, "{$type:'InfinitePhlogistonMixin'}");
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(20);
        Phlogiston.change(vehicle, -3);
        assertEquals(20, vehicle.getCurPhlogiston());
    }

    @Test
    void restoreAndExplicitSetStillWork() {
        install(team, "{$type:'InfinitePhlogistonMixin'}");
        Phlogiston.change(env.fixture.player, 3);
        assertEquals(53, env.fixture.player.getPhlogistonValue());
        Phlogiston.set(env.fixture.player, 5);
        assertEquals(5, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void failedConditionDoesNotBlockFuel() {
        install(
                team,
                "{$type:'InfinitePhlogistonMixin',predicates:[{$type:'ByTargetGlobalValue',key:'inactive',value:1}]}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void anotherPlayersInfiniteModifierDoesNotBlockCurrentPlayer() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        other.setScene(env.fixture.scene);
        other.setWorld(env.fixture.scene.getWorld());
        var otherTeam = new EntityTeam(other);
        other.getTeamManager().setEntity(otherTeam);
        install(otherTeam, "{$type:'InfinitePhlogistonMixin'}");
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void realActivityInfiniteResourceSuppressesTeamCost() throws Exception {
        var path =
                java.nio.file.Path.of(
                        "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Activity_V5_2_CharAmuse.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        var rows =
                com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                        .getAsJsonArray();
        for (var row : rows) {
            var mods =
                    row.getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers");
            if (mods == null || !mods.has("Phlogiston_Infinite")) continue;
            install(
                    team,
                    mods.getAsJsonObject("Phlogiston_Infinite")
                            .getAsJsonArray("modifierMixins")
                            .get(0)
                            .toString());
            Phlogiston.change(env.fixture.player, -3);
            assertEquals(50, env.fixture.player.getPhlogistonValue());
            return;
        }
        fail("Infinite phlogiston resource missing");
    }

    @Test
    void rootInfiniteAbilityStopsBlockingAfterRemoval() {
        var source =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'InfiniteRoot',modifiers:{},abilityMixins:[{$type:'InfinitePhlogistonMixin'}]}",
                                AbilityData.class),
                        team,
                        env.fixture.player);
        team.getInstancedAbilities().add(source);
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        team.getInstancedAbilities().remove(source);
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void rootInfiniteAbilityRespectsItsCondition() {
        var source =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'InfiniteRoot',modifiers:{},abilityMixins:[{$type:'InfinitePhlogistonMixin',predicates:[{$type:'ByTargetGlobalValue',key:'inactive',value:1}]}]}",
                                AbilityData.class),
                        team,
                        env.fixture.player);
        team.getInstancedAbilities().add(source);
        Phlogiston.change(env.fixture.player, -3);
        assertEquals(47, env.fixture.player.getPhlogistonValue());
    }
}
