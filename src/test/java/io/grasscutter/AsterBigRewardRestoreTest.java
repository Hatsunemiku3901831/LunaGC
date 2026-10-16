package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cold activity records recover real original points without generating a new reward offer. */
class AsterBigRewardRestoreTest {
    private AsterBigEntranceTest fixture;
    private PlayerActivityData owner;
    private String token;
    private boolean oldResinUsage;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterBigEntranceTest();
        fixture.setup();
        token = fixture.resources.offer();
        owner = fixture.resources.host;
        oldResinUsage = Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        fixture.host.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        var inventory = new InventorySystem(fixture.host.getServer());
        fixture.host.inventorySystem = inventory;
        AsterBigEntranceTest.field(GameServer.class, "inventorySystem")
                .set(fixture.host.getServer(), inventory);
    }

    @AfterEach
    void close() throws Exception {
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResinUsage;
        if (fixture != null) fixture.cleanup();
    }

    private void attach(PlayerActivityData reloaded) {
        owner = reloaded;
        owner.setPlayer(fixture.host);
        fixture.host.getActivityManager().getPlayerActivityDataMap().put(2001, owner);
    }

    private void cold() {
        emu.grasscutter.database.DatabaseHelper.saveGameSync(owner);
        var reloaded = DatabaseManager.getGameDatastore().find(PlayerActivityData.class)
                .filter(Filters.eq("uid", owner.getUid())).first();
        assertNotNull(reloaded);
        attach(reloaded);
    }

    private EntityGadget restore() throws Exception {
        fixture.update(fixture.resources.now);
        fixture.update(fixture.resources.now);
        return (EntityGadget)
                fixture.scene.getEntityByConfigId(AsterBigRewards.CONFIG, AsterBigRewards.GROUP);
    }

    private boolean prepare() {
        return fixture.scene
                .getScenePlayBattleManager()
                .prepare(
                        GameData.getMpPlayGroupDataMap().get(2),
                        GameData.getMpPlayMatchDataMap().get(2),
                        2,
                        1,
                        List.of(fixture.host));
    }

    private int interact(EntityGadget point, InterOpType type) throws Exception {
        fixture.host.getPosition().set(point.getPosition());
        fixture.scene
                .getScenePlayBattleManager()
                .getRewards()
                .interact(
                        fixture.host,
                        point,
                        GadgetInteractReq.newBuilder()
                                .setGadgetEntityId(point.getId())
                                .setGadgetId(point.getGadgetId())
                                .setOpType(type)
                                .setResinCostType(ResinCostType.ResinCostType_NORMAL)
                                .build());
        return GadgetInteractRsp.parseFrom(
                        fixture.host.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.GadgetInteractRsp)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData())
                .getRetcode();
    }

    @Test
    void mongoColdReloadRecoversTheOriginalTokenMembershipAndFrozenDrops() throws Exception {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        var database = "lunagc_big_restore_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            var store = Morphia.createDatastore(client, database);
            store.getMapper().map(PlayerActivityData.class);
            try {
                store.save(owner);
                var reloaded =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", owner.getUid()))
                                .first();
                assertNotNull(reloaded);
                assertNotSame(owner, reloaded);
                attach(reloaded);
                String before = owner.getDetail();
                var point = restore();
                assertNotNull(point);
                assertEquals(AsterBigRewards.GADGET, point.getGadgetId());
                var info = point.toProto().getGadget().getGeneralReward();
                assertEquals(List.of(10001, 10002), info.getQualifyUidListList());
                assertEquals(List.of(10001, 10002), info.getRemainUidListList());
                assertEquals(40, info.getResin());
                assertEquals(0, info.getDeadTime());
                assertEquals(before, owner.getDetail());
                var offer = AsterSchedule.progress(owner).bigReward();
                assertEquals(token, offer.token());
                assertEquals(8, offer.rewards().get(10002).worldLevel());
                assertEquals(50, offer.rewards().get(10002).essence());
                AsterBigEntranceTest.field(DatabaseManager.class, "gameDatastore").set(null, store);
                assertTrue(prepare());
                fixture.scripts.awaitAll();
                var retired =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", owner.getUid()))
                                .first();
                assertNotNull(retired);
                assertNull(AsterSchedule.progress(retired).bigReward());
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void repeatedTicksAndSceneReloadsKeepOnePointAndTheOriginalOffer() throws Exception {
        cold();
        var point = restore();
        assertNotNull(point);
        assertSame(point, restore());
        fixture.scene.getAsterBigSceneController().close();
        assertNull(fixture.scene.getEntityById(point.getId()));
        var replacement = restore();
        assertNotNull(replacement);
        assertNotSame(point, replacement);
        assertEquals(token, AsterSchedule.progress(owner).bigReward().token());
        assertEquals(
                1,
                fixture.scene.getEntities().values().stream()
                        .filter(
                                e ->
                                        e.getGroupId() == AsterBigRewards.GROUP
                                                && e.getConfigId() == AsterBigRewards.CONFIG)
                        .count());
    }

    @Test
    void anAlreadyClaimedOwnerCannotTakeTheOfflineGuestsRemainingReward() throws Exception {
        var progress = AsterSchedule.progress(owner);
        progress.reserveBigReward(10001);
        progress.acknowledgeBigReward(10001);
        owner.setDetail(progress);
        cold();
        var point = restore();
        assertNotNull(point);
        assertEquals(
                List.of(10002),
                point.toProto().getGadget().getGeneralReward().getRemainUidListList());
        assertNotEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(100, fixture.host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void fullyClaimedOffersDoNotCreateAnotherPoint() throws Exception {
        var progress = AsterSchedule.progress(owner);
        for (var uid : List.of(10001, 10002)) {
            progress.reserveBigReward(uid);
            progress.acknowledgeBigReward(uid);
        }
        owner.setDetail(progress);
        cold();
        assertNull(restore());
    }

    @Test
    void anUnconfirmedDeliveryRemainsReservedAndBlocksBothClaimAndReplacement() throws Exception {
        var progress = AsterSchedule.progress(owner);
        progress.reserveBigReward(10001);
        owner.setDetail(progress);
        cold();
        var point = restore();
        assertNotNull(point);
        assertNotEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_START));
        assertFalse(prepare());
        assertEquals(Set.of(10001), AsterSchedule.progress(owner).bigReward().reserved());
        assertEquals(100, fixture.host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void closedOrNotYetOpenChaptersDoNotRecoverRewards() throws Exception {
        cold();
        fixture.resources.config.setDisabled(true);
        assertNull(restore());
        fixture.resources.config.setDisabled(false);
        fixture.resources.config.setBeginTime(new Date(fixture.resources.now));
        assertNull(restore());
    }

    @Test
    void aMismatchedScheduleCannotPublishItsOldOffer() throws Exception {
        cold();
        owner.setScheduleId(owner.getScheduleId() + 1);
        assertNull(restore());
        assertEquals(token, AsterSchedule.progress(owner).bigReward().token());
    }

    @Test
    void noncanonicalFrozenRewardsCannotBecomeAVisiblePoint() throws Exception {
        var json = com.google.gson.JsonParser.parseString(owner.getDetail()).getAsJsonObject();
        json.getAsJsonObject("bigReward")
                .getAsJsonObject("rewards")
                .getAsJsonObject("10002")
                .addProperty("essence", 51);
        owner.setDetailJson(json.toString());
        cold();
        assertNull(restore());
    }

    @Test
    void startingANewBattleDurablyRetiresTheRecoveredOfferAndItsOldEntity() throws Exception {
        var receipt = UUID.randomUUID().toString();
        var progress = AsterSchedule.progress(owner);
        progress.beginBigClaim(receipt);
        progress.finishBigClaim(7);
        owner.setDetail(progress);
        cold();
        var point = restore();
        assertNotNull(point);
        assertTrue(prepare());
        fixture.scripts.awaitAll();
        assertNull(AsterSchedule.progress(owner).bigReward());
        assertEquals(7, AsterSchedule.progress(owner).bigCredit());
        assertTrue(AsterSchedule.progress(owner).hasBigClaim(receipt));
        assertNull(fixture.scene.getEntityById(point.getId()));
        fixture.scene.getAsterBigSceneController().close();
        assertNull(restore());
        assertNotEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_START));
    }

    @Test
    void aFailedRetirementWriteLeavesTheOldPointAndDoesNotStartAnotherBattle() throws Exception {
        cold();
        var point = restore();
        assertNotNull(point);
        String before = owner.getDetail();
        ActivitySaveFaults.failNextWrite(owner, false);
        assertFalse(prepare());
        assertEquals(before, owner.getDetail());
        assertSame(point, fixture.scene.getEntityById(point.getId()));
        assertEquals(
                0,
                fixture.scene
                        .getScenePlayBattleManager()
                        .getInfo(AsterBigRewards.GROUP)
                        .getState());
    }

    @Test
    void recoveringAndColdReloadingAfterAClaimCannotDebitResinOrGrantAgain() throws Exception {
        cold();
        var point = restore();
        assertNotNull(point);
        assertEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, fixture.host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(14, AsterSchedule.progress(owner).bigCredit());
        fixture.scene.getAsterBigSceneController().close();
        cold();
        var replacement = restore();
        assertNotNull(replacement);
        assertNotEquals(0, interact(replacement, InterOpType.InterOpType_INTER_OP_START));
        assertNotEquals(0, interact(point, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, fixture.host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(14, AsterSchedule.progress(owner).bigCredit());
    }

    @Test
    void mongoAcceptedRetirementWithLostAcknowledgementRestoresTheOriginalOffer() throws Exception {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        var database = "lunagc_big_retire_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            var store = Morphia.createDatastore(client, database);
            store.getMapper().map(PlayerActivityData.class);
            try {
                store.save(owner);
                AsterBigEntranceTest.field(DatabaseManager.class, "gameDatastore").set(null, store);
                attach(store.find(PlayerActivityData.class).filter(Filters.eq("uid", owner.getUid())).first());
                cold();
                var point = restore();
                assertNotNull(point);
                var failedOnce = new AtomicBoolean();
                GuardedActivityTestStore.bind(owner, store, store, true, ignored -> {}, document -> {
                    if (!failedOnce.getAndSet(true)) throw new IllegalStateException("retirement stored; acknowledgement lost");
                });
                assertFalse(prepare());
                assertSame(point, fixture.scene.getEntityById(point.getId()));
                var reloaded =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", owner.getUid()))
                                .first();
                assertNotNull(reloaded);
                assertNotNull(AsterSchedule.progress(reloaded).bigReward());
                assertEquals(token, AsterSchedule.progress(reloaded).bigReward().token());
                assertEquals(
                        Set.of(10001, 10002),
                        AsterSchedule.progress(reloaded).bigReward().remaining());
                assertEquals(
                        0,
                        fixture.scene
                                .getScenePlayBattleManager()
                                .getInfo(AsterBigRewards.GROUP)
                                .getState());
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}
