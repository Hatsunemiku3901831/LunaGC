package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;

class PhlogistonBallInvocationTest extends PhlogistonMixinInvocationTest {
    EntityItem ball;

    @Test
    void unavailableTeamPreservesBall() {
        setupPickup();
        var team = env.fixture.player.getTeamManager().getEntity();
        try {
            env.fixture.player.getTeamManager().setEntity(null);
            ball.onInteract(
                    env.fixture.player,
                    emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                            .getDefaultInstance());
            assertEquals(50, fuel());
            assertSame(ball, env.fixture.scene.getEntities().get(ball.getId()));
        } finally {
            env.fixture.player.getTeamManager().setEntity(team);
        }
    }

    @Test
    void unavailableTeamRejectsDirectItemUse() {
        var team = env.fixture.player.getTeamManager().getEntity();
        try {
            env.fixture.player.getTeamManager().setEntity(null);
            var action =
                    new emu.grasscutter.game.props.ItemUseAction.ItemUseAddPhlogiston(
                            new String[] {"20"});
            var params =
                    new emu.grasscutter.game.props.ItemUseAction.UseItemParams(
                            env.fixture.player,
                            emu.grasscutter.game.props.ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM);
            assertFalse(action.useItem(params));
            assertEquals(50, fuel());
        } finally {
            env.fixture.player.getTeamManager().setEntity(team);
        }
    }

    static final String CONFIG =
            "{$type:'HumanDragonCollabPickPhlogistonBallMixin',KNEIAADHCBH:70331081,attachPoint:'ElementAbsorb'}";

    void ball(boolean share, int ownerUid, int itemId, int gadget, float amount) {
        var item =
                JsonUtils.decode(
                        "{id:"
                                + itemId
                                + ",gadgetId:"
                                + gadget
                                + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_ADSORBATE',useOnGain:true,useTarget:'ITEM_USE_TARGET_CUR_TEAM',itemUse:[{useOp:'ITEM_USE_ADD_PHLOGISTON',useParam:['"
                                + amount
                                + "']}]}",
                        ItemData.class);
        item.onLoad();
        var dropOwner =
                ownerUid == env.fixture.player.getUid()
                        ? env.fixture.player
                        : new AbilityTestFixture.TestPlayer();
        dropOwner.setUid(ownerUid);
        ball = new EntityItem(env.fixture.scene, dropOwner, item, new Position(1, 2, 3), 1, share);
        env.fixture.scene.getEntities().put(ball.getId(), ball);
    }

    ByteString message(int field, int id) {
        return assertDoesNotThrow(
                () -> {
                    var bytes = new ByteArrayOutputStream();
                    var wire = CodedOutputStream.newInstance(bytes);
                    wire.writeUInt32(field, id);
                    wire.flush();
                    return ByteString.copyFrom(bytes.toByteArray());
                });
    }

    void pickup() {
        tick(101, message(9, ball.getId()), 136);
    }

    void setupPickup() {
        install(CONFIG, true);
        ball(true, 10001, 2030, 70331081, 20);
    }

    @Test
    void humanPickupAddsResourceAmountAndDespawns() {
        setupPickup();
        pickup();
        assertEquals(70, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void duplicateCannotRefillTwice() {
        setupPickup();
        pickup();
        pickup();
        assertEquals(70, fuel());
    }

    @Test
    void dragonPickupRestoresDragonPool() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(10);
        setupPickup();
        pickup();
        assertEquals(30, ((EntityVehicle) owner).getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void zeroDragonPoolStillRestoresDragon() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        setupPickup();
        pickup();
        assertEquals(20, ((EntityVehicle) owner).getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void fullPoolConsumesBall() {
        setupPickup();
        env.fixture.player.setPhlogistonValue(100);
        pickup();
        assertEquals(100, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void wrongGadgetDoesNotConsume() {
        install(CONFIG, true);
        ball(true, 10001, 2030, 70331082, 20);
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void anotherPlayersPrivateBallDoesNotConsume() {
        install(CONFIG, true);
        ball(false, 20001, 2031, 70331081, 20);
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void removedParentDoesNotConsume() {
        setupPickup();
        ability.removeModifier(owner, "Parent");
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void wrongArgumentDoesNotConsume() {
        setupPickup();
        tick(101, message(9, ball.getId()), 135);
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void unknownFieldDoesNotInventBall() {
        setupPickup();
        tick(101, message(1, ball.getId()), 136);
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void truncatedMessageDoesNotConsume() {
        setupPickup();
        tick(101, ByteString.copyFrom(new byte[] {0x48, (byte) 0x80}), 136);
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void invalidResourceAmountDoesNotConsume() {
        install(CONFIG, true);
        ball(true, 10001, 2030, 70331081, -20);
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void ownPrivateBallCanBePicked() {
        install(CONFIG, true);
        ball(false, 10001, 2031, 70331081, 20);
        pickup();
        assertEquals(70, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void failedPredicateDoesNotConsume() {
        install(
                CONFIG.substring(0, CONFIG.length() - 1)
                        + ",predicates:[{$type:'ByTargetGlobalValue',key:'notReady',value:1}]}",
                true);
        ball(true, 10001, 2030, 70331081, 20);
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void disabledMixinDoesNotConsume() {
        install(CONFIG.substring(0, CONFIG.length() - 1) + ",enable:false}", true);
        ball(true, 10001, 2030, 70331081, 20);
        pickup();
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void absentParentIdDoesNotConsume() {
        setupPickup();
        tick(0, message(9, ball.getId()), 136);
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void rawCurrentMixinTypeWorks() {
        install(CONFIG.replace("HumanDragonCollabPickPhlogistonBallMixin", "NILDEPAIECE"), true);
        ball(true, 10001, 2030, 70331081, 20);
        pickup();
        assertEquals(70, fuel());
    }

    @Test
    void regularInteractionAndCallbackShareOneClaim() {
        setupPickup();
        ball.onInteract(
                env.fixture.player,
                emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                        .getDefaultInstance());
        pickup();
        assertEquals(70, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    EntityVehicle ride(int gadget, float points) {
        var vehicle = env.environment.candle(gadget);
        emu.grasscutter.game.ability.VehicleInteraction.execute(
                env.fixture.player.getSession(), vehicle.getId(), 1);
        assertTrue(
                vehicle.getVehicleMembers().stream()
                        .anyMatch(member -> member.getUid() == env.fixture.player.getUid()));
        vehicle.setCurPhlogiston(points);
        return vehicle;
    }

    void interact() {
        ball.onInteract(
                env.fixture.player,
                emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                        .getDefaultInstance());
    }

    @Test
    void ordinaryPickupWhileRidingReplenishesTheSaurian() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        setupPickup();
        interact();
        assertEquals(30, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void ordinaryPickupRefillsAnEmptySaurianInsteadOfTheTeam() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 0);
        setupPickup();
        interact();
        assertEquals(20, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void ordinaryPickupAtSaurianCapacityCannotSpillIntoTheTeam() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 50);
        setupPickup();
        interact();
        assertEquals(50, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
        assertNull(env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void dismountRestoresHumanPickupRouting() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        emu.grasscutter.game.ability.VehicleInteraction.execute(
                env.fixture.player.getSession(), vehicle.getId(), 2);
        setupPickup();
        interact();
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, fuel());
    }

    @Test
    void nearbyUnriddenSaurianCannotCaptureHumanFuel() {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(10);
        setupPickup();
        interact();
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, fuel());
    }

    @Test
    void skiffDoesNotUseSaurianFuelRouting() {
        var vehicle = ride(NatsaurusPossessionTest.BOAT, 10);
        setupPickup();
        interact();
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(70, fuel());
    }

    @Test
    void invalidRiddenPoolPreservesTheBallAndTheTeam() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, Float.NaN);
        setupPickup();
        interact();
        assertTrue(Float.isNaN(vehicle.getCurPhlogiston()));
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void ordinaryPickupAndDragonCallbackUseOneClaim() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        owner = vehicle;
        setupPickup();
        interact();
        pickup();
        assertEquals(30, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void dragonCallbackAndOrdinaryPickupUseOneClaim() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        owner = vehicle;
        setupPickup();
        pickup();
        interact();
        assertEquals(30, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void staleDeadRiderStateCannotRedirectIntoTheTeam() {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        assertDoesNotThrow(
                () -> {
                    var dead =
                            emu.grasscutter.game.entity.GameEntity.class.getDeclaredField("isDead");
                    dead.setAccessible(true);
                    dead.setBoolean(vehicle, true);
                });
        setupPickup();
        interact();
        assertEquals(10, vehicle.getCurPhlogiston());
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void ambiguousRiderStatePreservesTheBall() {
        var first = ride(NatsaurusPossessionTest.DRILL, 10);
        var second = env.environment.candle(NatsaurusPossessionTest.HOOK);
        second.getVehicleMembers().add(first.getVehicleMembers().get(0));
        second.setCurPhlogiston(5);
        setupPickup();
        interact();
        assertEquals(10, first.getCurPhlogiston());
        assertEquals(5, second.getCurPhlogiston());
        assertEquals(50, fuel());
        assertSame(ball, env.fixture.scene.getEntityById(ball.getId()));
    }

    @Test
    void simultaneousDragonCallbackAndOrdinaryPickupCannotDoubleClaim() throws Exception {
        var vehicle = ride(NatsaurusPossessionTest.DRILL, 10);
        owner = vehicle;
        setupPickup();
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var normal =
                    executor.submit(
                            () -> {
                                gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                interact();
                                return null;
                            });
            var callback =
                    executor.submit(
                            () -> {
                                gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                pickup();
                                return null;
                            });
            normal.get(6, java.util.concurrent.TimeUnit.SECONDS);
            callback.get(6, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(30, vehicle.getCurPhlogiston());
            assertEquals(50, fuel());
            assertNull(env.fixture.scene.getEntityById(ball.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void itemUseRespectsCount() {
        var action =
                new emu.grasscutter.game.props.ItemUseAction.ItemUseAddPhlogiston(
                        new String[] {"20"});
        var params =
                new emu.grasscutter.game.props.ItemUseAction.UseItemParams(
                        env.fixture.player,
                        emu.grasscutter.game.props.ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM);
        params.count = 2;
        assertTrue(action.useItem(params));
        assertEquals(90, fuel());
    }

    @Test
    void realResourcePickupsHaveSupportedUseAction() throws Exception {
        var rows =
                JsonUtils.loadToList(
                        java.nio.file.Path.of(
                                "resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                        ItemData.class);
        for (int id : new int[] {2030, 2031}) {
            var item = rows.stream().filter(row -> row.getId() == id).findFirst().orElseThrow();
            item.onLoad();
            assertTrue(
                    item.getItemUseActions().stream()
                            .anyMatch(
                                    action ->
                                            action.getItemUseOp()
                                                    .name()
                                                    .equals("ITEM_USE_ADD_PHLOGISTON")));
        }
    }
}
