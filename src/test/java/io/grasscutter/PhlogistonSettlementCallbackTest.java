package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.PhlogistonBallPickup;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.props.ItemUseAction.ItemUseAddPhlogiston;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A refresh failure after the fuel write cannot turn a committed award into a failed claim. */
class PhlogistonSettlementCallbackTest {
    PhlogistonBallInvocationTest env;
    int refreshes;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonBallInvocationTest();
        env.setup();
        env.env
                .fixture
                .player
                .getTeamManager()
                .setEntity(
                        new EntityTeam(env.env.fixture.player) {
                            @Override
                            public void onAbilityValueUpdate() {
                                refreshes++;
                                throw new IllegalStateException("injected fuel watcher failure");
                            }
                        });
    }

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void ordinaryPickupStillAcknowledgesTheConsumedBall() {
        env.setupPickup();
        assertDoesNotThrow(
                () ->
                        env.ball.onInteract(
                                env.env.fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertEquals(70, env.fuel());
        assertNull(env.env.fixture.scene.getEntities().get(env.ball.getId()));
        assertTrue(
                env.env.fixture.player.packets.stream()
                        .anyMatch(PacketGadgetInteractRsp.class::isInstance));
        assertEquals(1, refreshes);
        assertFalse(PhlogistonBallPickup.pickup(env.env.fixture.player, env.ball));
        assertEquals(70, env.fuel());
        assertEquals(1, refreshes);
    }

    @Test
    void collaborationCallbackFinishesAndCannotAwardTwice() {
        env.setupPickup();
        env.pickup();
        env.pickup();
        assertEquals(70, env.fuel());
        assertNull(env.env.fixture.scene.getEntities().get(env.ball.getId()));
        assertEquals(1, refreshes);
    }

    @Test
    void directResourceAwardStillReportsItsCommittedResult() {
        var action = new ItemUseAddPhlogiston(new String[] {"20"});
        var params =
                new UseItemParams(env.env.fixture.player, ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM);
        assertTrue(assertDoesNotThrow(() -> action.useItem(params)));
        assertEquals(70, env.fuel());
        assertEquals(1, refreshes);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "value:0.1,costType:'Cost',useRatio:true",
                "value:0.1,costType:'Cost',useRatio:true,targetType:'Team'"
            })
    void ratioCostStillReturnsTheCommittedResult(String config) {
        var action =
                JsonUtils.decode(
                        "{$type:'ChangePhlogiston'," + config + "}", AbilityModifierAction.class);
        assertTrue(
                assertDoesNotThrow(
                        () ->
                                new ActionChangePhlogiston()
                                        .execute(
                                                env.env.ability,
                                                action,
                                                ByteString.EMPTY,
                                                env.owner)));
        assertEquals(45, env.fuel());
        assertEquals(1, refreshes);
    }

    @Test
    void zeroAndFullPoolsDoNotCreateRefreshes() {
        assertTrue(PhlogistonBallPickup.replenish(env.env.fixture.player, 50));
        assertEquals(1, refreshes);
        assertTrue(PhlogistonBallPickup.replenish(env.env.fixture.player, 20));
        assertEquals(100, env.fuel());
        assertEquals(1, refreshes);
    }
}
