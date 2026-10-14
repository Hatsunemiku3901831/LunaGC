package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.ability.mixins.PhlogistonCostMixin;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

class PhlogistonMutationTest {
    NatsaurusPossessionTest environment;
    AbilityTestFixture fixture;
    EntityAvatar caster;
    Ability ability;

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        fixture = environment.fixture;
        fixture.player.getTeamManager().getActiveTeam().clear();
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000103;
                    }

                    @Override
                    public void save() {}
                };
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), 100f);
        caster =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public Position getPosition() {
                        return new Position();
                    }

                    @Override
                    public void initAbilities() {}
                };
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        fixture.scene.getEntities().put(caster.getId(), caster);
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'FuelTest',modifiers:{},abilitySpecials:{fuel:2}}",
                                AbilityData.class),
                        caster,
                        fixture.player);
        fixture.player.setPhlogistonValue(50);
        fixture.player.packets.clear();
    }

    @AfterEach
    void close() {
        environment.close();
    }

    AbilityMixinData cost(String value) {
        return JsonUtils.decode(
                "{$type:'PhlogistonCostMixin',speed:" + value + "}", AbilityMixinData.class);
    }

    void drain() throws Exception {
        var gate = new CyclicBarrier(4);
        var jobs = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            jobs.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                gate.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var job : jobs) job.get(6, TimeUnit.SECONDS);
    }

    @Test
    void dispatcherChargesConfiguredAmountOnlyOnce() throws Exception {
        fixture.player
                .getAbilityManager()
                .executeMixin(ability, cost("2"), ByteString.EMPTY, caster);
        drain();
        assertEquals(48, fixture.player.getPhlogistonValue());
        assertEquals(1, fixture.player.packets.size());
    }

    @Test
    void dispatcherDoesNotChargeBeforeRejectingRemovedOwner() throws Exception {
        fixture.scene.removeEntity(caster);
        fixture.player.packets.clear();
        fixture.player
                .getAbilityManager()
                .executeMixin(ability, cost("2"), ByteString.EMPTY, caster);
        drain();
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void dispatcherDoesNotMutatePlayerWithoutARegisteredCostHandler() throws Exception {
        var old = AbilityManager.mixinHandlers.remove(AbilityMixinData.Type.PhlogistonCostMixin);
        try {
            fixture.player
                    .getAbilityManager()
                    .executeMixin(ability, cost("2"), ByteString.EMPTY, caster);
            drain();
            assertEquals(50, fixture.player.getPhlogistonValue());
            assertTrue(fixture.player.packets.isEmpty());
        } finally {
            if (old != null)
                AbilityManager.mixinHandlers.put(AbilityMixinData.Type.PhlogistonCostMixin, old);
        }
    }

    @Test
    void explicitZeroDoesNotBecomeAnInventedFivePointCost() {
        assertTrue(new PhlogistonCostMixin().execute(ability, cost("0"), ByteString.EMPTY, caster));
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void missingSpeedDoesNotCreateAnInventedCost() {
        new PhlogistonCostMixin()
                .execute(
                        ability,
                        JsonUtils.decode("{$type:'PhlogistonCostMixin'}", AbilityMixinData.class),
                        ByteString.EMPTY,
                        caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void malformedExpressionCannotSpendFuelThroughFallback() {
        new PhlogistonCostMixin().execute(ability, cost("'1 0 /'"), ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void negativeCostCannotAddFuel() {
        new PhlogistonCostMixin().execute(ability, cost("-2"), ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void dynamicConfiguredCostUsesCurrentAbilityParameters() {
        new PhlogistonCostMixin().execute(ability, cost("'fuel'"), ByteString.EMPTY, caster);
        assertEquals(48, fixture.player.getPhlogistonValue());
    }

    @Test
    void directHandlerCannotChargeRemovedOwner() {
        fixture.scene.removeEntity(caster);
        fixture.player.packets.clear();
        new PhlogistonCostMixin().execute(ability, cost("2"), ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void staleTargetCannotChargeCurrentOwner() {
        fixture.scene.removeEntity(fixture.entity);
        fixture.player.packets.clear();
        new PhlogistonCostMixin().execute(ability, cost("2"), ByteString.EMPTY, fixture.entity);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void vehicleCostDoesNotChargeThePartyEvenWhenVehicleIsEmpty() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(0);
        var source = new Ability(ability.getData(), vehicle, fixture.player);
        fixture.player.packets.clear();
        new PhlogistonCostMixin().execute(source, cost("2"), ByteString.EMPTY, vehicle);
        assertEquals(0, vehicle.getCurPhlogiston());
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void anotherPlayersVehicleCannotChargeItsOwnerThroughThisPlayerAbility() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        other.setScene(fixture.scene);
        other.setWorld(fixture.scene.getWorld());
        var vehicle =
                new EntityVehicle(
                        fixture.scene,
                        other,
                        NatsaurusPossessionTest.DRILL,
                        0,
                        new Position(),
                        new Position());
        fixture.scene.getEntities().put(vehicle.getId(), vehicle);
        var source = new Ability(ability.getData(), vehicle, fixture.player);
        new PhlogistonCostMixin().execute(source, cost("2"), ByteString.EMPTY, vehicle);
        assertEquals(50, vehicle.getCurPhlogiston());
        assertTrue(other.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void invalidDeltaDoesNotCorruptPartyFuel(float delta) {
        Phlogiston.change(fixture.player, delta);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void invalidDeltaDoesNotCorruptVehicleFuel(float delta) {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        fixture.player.packets.clear();
        Phlogiston.change(vehicle, delta);
        assertEquals(50, vehicle.getCurPhlogiston());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void upperClampDoesNotSendAChangeWhenAlreadyFull() {
        fixture.player.setPhlogistonValue(100);
        Phlogiston.change(fixture.player, 10);
        assertEquals(100, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void explicitZeroChangeDoesNotGainFuel() {
        var action =
                JsonUtils.decode(
                        "{$type:'ChangePhlogiston',ratio:0,determineType:'Add'}",
                        AbilityModifierAction.class);
        new ActionChangePhlogiston().execute(ability, action, ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void unknownChangeModeCannotSpendFuel() {
        var action =
                JsonUtils.decode(
                        "{$type:'ChangePhlogiston',ratio:2,determineType:'Unknown'}",
                        AbilityModifierAction.class);
        new ActionChangePhlogiston().execute(ability, action, ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void malformedChangeExpressionDoesNotUseDefaultStep() {
        var action =
                JsonUtils.decode(
                        "{$type:'ChangePhlogiston',ratio:'1 0 /',determineType:'Add'}",
                        AbilityModifierAction.class);
        new ActionChangePhlogiston().execute(ability, action, ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void configuredPositiveAndNegativeChangesRetainTheirDirection() {
        var action =
                JsonUtils.decode(
                        "{$type:'ChangePhlogiston',ratio:2,determineType:'Add'}",
                        AbilityModifierAction.class);
        new ActionChangePhlogiston().execute(ability, action, ByteString.EMPTY, caster);
        assertEquals(52, fixture.player.getPhlogistonValue());
        action.determineType = "Sub";
        new ActionChangePhlogiston().execute(ability, action, ByteString.EMPTY, caster);
        assertEquals(50, fixture.player.getPhlogistonValue());
    }
}
