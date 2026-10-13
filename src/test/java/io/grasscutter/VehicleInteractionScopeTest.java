package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.VehicleInteractReqOuterClass.VehicleInteractReq;
import emu.grasscutter.net.proto.VehicleInteractRspOuterClass.VehicleInteractRsp;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerVehicleInteractReq;
import emu.grasscutter.server.packet.send.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

class VehicleInteractionScopeTest {
    NatsaurusPossessionTest environment;
    Field currentVehicle;

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        currentVehicle = StaminaManager.class.getDeclaredField("vehicleId");
        currentVehicle.setAccessible(true);
    }

    @AfterEach
    void close() {
        environment.close();
    }

    VehicleMember member(int uid, long guid) {
        return VehicleMember.newBuilder().setUid(uid).setAvatarGuid(guid).build();
    }

    VehicleInteractRsp invoke(int entityId, int type) {
        return invoke(environment.fixture.player.getSession(), entityId, type);
    }

    VehicleInteractRsp invoke(GameSession session, int entityId, int type) {
        environment.fixture.player.packets.clear();
        assertDoesNotThrow(
                () ->
                        new HandlerVehicleInteractReq()
                                .handle(
                                        session,
                                        new byte[0],
                                        VehicleInteractReq.newBuilder()
                                                .setEntityId(entityId)
                                                .setInteractTypeValue(type)
                                                .build()
                                                .toByteArray()));
        var packet =
                environment.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.VehicleInteractRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return assertDoesNotThrow(() -> VehicleInteractRsp.parseFrom(packet.getData()));
    }

    void vehicleContext(int id) {
        assertDoesNotThrow(
                () -> currentVehicle.setInt(environment.fixture.player.getStaminaManager(), id));
    }

    int vehicleContext() {
        return assertDoesNotThrow(
                () -> currentVehicle.getInt(environment.fixture.player.getStaminaManager()));
    }

    void rejected(VehicleInteractRsp response) {
        assertNotEquals(0, response.getRetcode());
    }

    @Test
    void absentEntityDoesNotCreateStaminaContextOrSuccessReply() {
        vehicleContext(123);
        rejected(invoke(555555, 1));
        assertEquals(123, vehicleContext());
    }

    @Test
    void ordinaryEntityDoesNotBecomeVehicleOrHaveHealthOverwritten() {
        var entity = environment.fixture.entity;
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 60);
        vehicleContext(123);
        rejected(invoke(entity.getId(), 1));
        assertEquals(60, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(123, vehicleContext());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 99})
    void invalidInteractionCannotClearCurrentRide(int operation) {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.getVehicleMembers().add(member(10001, 0));
        vehicleContext(vehicle.getId());
        rejected(invoke(vehicle.getId(), operation));
        assertEquals(vehicle.getId(), vehicleContext());
        assertEquals(1, vehicle.getVehicleMembers().size());
    }

    @Test
    void cannotJoinSaurianOccupiedByAnotherPlayer() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.getVehicleMembers().add(member(20001, 77));
        vehicleContext(123);
        rejected(invoke(vehicle.getId(), 1));
        assertEquals(1, vehicle.getVehicleMembers().size());
        assertEquals(20001, vehicle.getVehicleMembers().get(0).getUid());
        assertEquals(123, vehicleContext());
    }

    @Test
    void cannotJoinSaurianOwnedByAnotherPlayer() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        var vehicle =
                new EntityVehicle(
                        environment.fixture.scene,
                        other,
                        NatsaurusPossessionTest.DRILL,
                        0,
                        new Position(),
                        new Position());
        environment.fixture.scene.getEntities().put(vehicle.getId(), vehicle);
        rejected(invoke(vehicle.getId(), 1));
        assertTrue(vehicle.getVehicleMembers().isEmpty());
    }

    @Test
    void leavingWithoutMembershipCannotClearStamina() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        vehicleContext(vehicle.getId());
        rejected(invoke(vehicle.getId(), 2));
        assertEquals(vehicle.getId(), vehicleContext());
    }

    @Test
    void cannotEnterVehicleWhoseDeathSettlementHasStarted() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        rejected(invoke(vehicle.getId(), 1));
        assertTrue(vehicle.getVehicleMembers().isEmpty());
    }

    @Test
    void repeatedBoardingPreservesMemberAvatarAndDoesNotRefillStamina() throws Exception {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        var existing = member(10001, 111);
        vehicle.getVehicleMembers().add(existing);
        vehicleContext(vehicle.getId());
        var stamina = StaminaManager.class.getDeclaredField("vehicleStamina");
        stamina.setAccessible(true);
        assertDoesNotThrow(
                () -> stamina.setInt(environment.fixture.player.getStaminaManager(), 123));
        var response = invoke(vehicle.getId(), 1);
        assertEquals(0, response.getRetcode());
        assertEquals(existing, response.getMember());
        assertEquals(1, vehicle.getVehicleMembers().size());
        assertEquals(
                123,
                assertDoesNotThrow(
                        () -> stamina.getInt(environment.fixture.player.getStaminaManager())));
    }

    @Test
    void leavingUsesStoredMemberEvenAfterAvatarChanges() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        var stored = member(10001, 5555);
        vehicle.getVehicleMembers().add(stored);
        vehicleContext(vehicle.getId());
        var response = invoke(vehicle.getId(), 2);
        assertEquals(0, response.getRetcode());
        assertEquals(stored, response.getMember());
        assertTrue(vehicle.getVehicleMembers().isEmpty());
        assertEquals(-1, vehicleContext());
    }

    @Test
    void staleSessionCannotMutateCurrentPlayerVehicle() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        var active = environment.fixture.player.getSession();
        var stale =
                new GameSession(null) {
                    @Override
                    public void send(BasePacket packet) {
                        environment.fixture.player.packets.add(packet);
                    }
                };
        stale.setPlayer(environment.fixture.player);
        environment.fixture.player.setSession(active);
        rejected(invoke(stale, vehicle.getId(), 1));
        assertTrue(vehicle.getVehicleMembers().isEmpty());
    }

    @Test
    void staleSceneRegistrationCannotMutateVehicle() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        environment.fixture.scene.getWorld().getScenes().remove(environment.fixture.scene.getId());
        rejected(invoke(vehicle.getId(), 1));
        assertTrue(vehicle.getVehicleMembers().isEmpty());
    }

    @Test
    void freshOwnSaurianBoardAndExitUseActualPositionAndHealth() {
        var vehicle = environment.candle(NatsaurusPossessionTest.DRILL);
        var response = invoke(vehicle.getId(), 1);
        assertEquals(0, response.getRetcode());
        assertEquals(vehicle.getPosition().toProto(), response.getVehiclePos());
        assertEquals(10000, vehicle.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(vehicle.getId(), vehicleContext());
        assertEquals(1, vehicle.getVehicleMembers().size());
        assertEquals(0, invoke(vehicle.getId(), 2).getRetcode());
        assertTrue(vehicle.getVehicleMembers().isEmpty());
    }

    @Test
    void ordinaryBoatStillAcceptsRiderWithoutTakingOwnership() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        var boat =
                new EntityVehicle(
                        environment.fixture.scene,
                        other,
                        NatsaurusPossessionTest.BOAT,
                        0,
                        new Position(),
                        new Position());
        environment.fixture.scene.getEntities().put(boat.getId(), boat);
        assertEquals(0, invoke(boat.getId(), 1).getRetcode());
        assertSame(other, boat.getOwner());
        assertEquals(1, boat.getVehicleMembers().size());
    }

    @Test
    void switchingFromBoatToSaurianReleasesOnlyThisPlayersOldSeat() {
        var boat = environment.candle(NatsaurusPossessionTest.BOAT);
        boat.getVehicleMembers().add(member(10001, 11));
        boat.getVehicleMembers().add(member(20001, 22));
        vehicleContext(boat.getId());
        var dragon = environment.candle(NatsaurusPossessionTest.DRILL);
        assertEquals(0, invoke(dragon.getId(), 1).getRetcode());
        assertEquals(1, boat.getVehicleMembers().size());
        assertEquals(20001, boat.getVehicleMembers().get(0).getUid());
        assertEquals(dragon.getId(), vehicleContext());
        assertEquals(1, dragon.getVehicleMembers().size());
    }

    @Test
    void rejectedBoardingKeepsExistingRidingMembership() {
        var original = environment.candle(NatsaurusPossessionTest.BOAT);
        original.getVehicleMembers().add(member(10001, 11));
        vehicleContext(original.getId());
        var occupied = environment.candle(NatsaurusPossessionTest.DRILL);
        occupied.getVehicleMembers().add(member(20001, 22));
        rejected(invoke(occupied.getId(), 1));
        assertEquals(1, original.getVehicleMembers().size());
        assertEquals(original.getId(), vehicleContext());
    }

    @Test
    void replacingBoatRemovesOldMembershipAndReleasesItsStaminaContext() {
        var original = environment.candle(NatsaurusPossessionTest.BOAT);
        original.getVehicleMembers().add(member(10001, 11));
        vehicleContext(original.getId());
        new PacketCreateVehicleRsp(
                environment.fixture.player,
                NatsaurusPossessionTest.BOAT,
                0,
                new Position(),
                new Position());
        assertTrue(original.getVehicleMembers().isEmpty());
        assertEquals(-1, vehicleContext());
    }

    @Test
    void constructingVehicleResponseDoesNotChangeMembershipOrHealth() {
        var vehicle = environment.candle(NatsaurusPossessionTest.BOAT);
        float before = vehicle.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        new PacketVehicleInteractRsp(
                environment.fixture.player,
                vehicle.getId(),
                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN);
        assertTrue(vehicle.getVehicleMembers().isEmpty());
        assertEquals(before, vehicle.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        vehicle.getVehicleMembers().add(member(10001, 42));
        new PacketVehicleInteractRsp(
                vehicle,
                vehicle.getVehicleMembers().get(0),
                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT);
        assertEquals(1, vehicle.getVehicleMembers().size());
    }
}
