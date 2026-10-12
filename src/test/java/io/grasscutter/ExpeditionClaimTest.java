package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.expedition.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionAllDataRspOuterClass.AvatarExpeditionAllDataRsp;
import emu.grasscutter.net.proto.AvatarExpeditionCallBackReqOuterClass.AvatarExpeditionCallBackReq;
import emu.grasscutter.net.proto.AvatarExpeditionCallBackRspOuterClass.AvatarExpeditionCallBackRsp;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.AvatarExpeditionStartReqOuterClass.AvatarExpeditionStartReq;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Real inventory and registered packets; explicit persistence/fault boundaries. */
class ExpeditionClaimTest {
    private AbilityTestFixture environment;
    private ExpeditionPlayer player;
    private ExpeditionSystem system;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private Object previousDatastore;
    private java.lang.reflect.Field datastore;
    private int confirmedSaves, failSaveAt;
    private final List<String> snapshots = new ArrayList<>();

    static class ExpeditionPlayer extends InvestigationRewardTest.ClaimPlayer {
        boolean throwGrant, refuseGrant;
        Runnable afterGrant;
        final Inventory rewards =
                new Inventory(this) {
                    @Override
                    public boolean addItem(ItemParamData item, ActionReason reason) {
                        if (throwGrant) throw new IllegalStateException("Grant failed");
                        if (refuseGrant) return false;
                        boolean result = super.addItem(item, reason);
                        var callback = afterGrant;
                        afterGrant = null;
                        if (result && callback != null) callback.run();
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return rewards;
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new ExpeditionPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        var server = environment.scene.getWorld().getServer();
        system = new ExpeditionSystem(server);
        var systemField = GameServer.class.getDeclaredField("expeditionSystem");
        systemField.setAccessible(true);
        systemField.set(server, system);
        system.getExpeditionRewardDataList().clear();
        reward(101, 4, 100001, 2, 2);
        for (int id : List.of(100001, 100002))
            put(
                    GameData.getItemDataMap(),
                    id,
                    gson.fromJson(
                            "{id:"
                                    + id
                                    + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                            ItemData.class));
        put(
                GameData.getItemDataMap(),
                202,
                gson.fromJson("{id:202,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        var excel = Path.of("resources/ExcelBinOutput");
        var target =
                JsonUtils.loadToList(
                                excel.resolve("InvestigationTargetConfigData.json"),
                                InvestigationTargetData.class)
                        .stream()
                        .filter(t -> t.getId() == 60024)
                        .findFirst()
                        .orElseThrow();
        assertEquals("TRIGGER_AVATAR_EXPEDITION", target.getTriggerConfig().getTriggerType());
        var chapter =
                JsonUtils.loadToList(
                                excel.resolve("InvestigationConfigData.json"),
                                InvestigationData.class)
                        .stream()
                        .filter(c -> c.getId() == target.getInvestigationId())
                        .findFirst()
                        .orElseThrow();
        put(GameData.getInvestigationTargetDataMap(), target.getId(), target);
        put(GameData.getInvestigationDataMap(), chapter.getId(), chapter);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (!method.getName().equals("save")) return null;
                            if (args.length == 2) {
                                confirmedSaves++;
                                snapshots.add(gson.toJson(player.getExpeditionInfo()));
                                if (confirmedSaves == failSaveAt)
                                    throw new IllegalStateException("Confirmation failed");
                            }
                            return args[0];
                        }));
        expedition(111, 101, 4, 1, Utils.getCurrentSeconds() - 14401);
        player.packets.clear();
    }

    @AfterEach
    void close() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, previousDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (environment != null) environment.close();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private void reward(int id, int hours, int item, int min, int max) {
        system.getExpeditionRewardDataList()
                .put(
                        id,
                        List.of(
                                gson.fromJson(
                                        "{hourTime:"
                                                + hours
                                                + ",expeditionRewardData:[{itemId:"
                                                + item
                                                + ",minCount:"
                                                + min
                                                + ",maxCount:"
                                                + max
                                                + "}]}",
                                        ExpeditionRewardDataList.class)));
    }

    private void expedition(long guid, int id, int hours, int state, int start) {
        var info = new ExpeditionInfo();
        info.setState(state);
        info.setExpId(id);
        info.setHourTime(hours);
        info.setStartTime(start);
        player.getExpeditionInfo().put(guid, info);
    }

    private InvestigationRewardTest.ClaimSession request(int opcode, byte[] payload, byte[] header)
            throws Exception {
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new GameServerPacketHandler(PacketHandler.class).handle(session, opcode, header, payload);
        return session;
    }

    private AvatarExpeditionGetRewardRsp claim(long guid, boolean all) throws Exception {
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionGetRewardReq,
                        AvatarExpeditionGetRewardReq.newBuilder()
                                .setAvatarGuid(guid)
                                .setIsClaimAll(all)
                                .build()
                                .toByteArray(),
                        new byte[0]);
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarExpeditionGetRewardRsp)
                        .findFirst()
                        .orElseThrow();
        return AvatarExpeditionGetRewardRsp.parseFrom(packet.getData());
    }

    private int count(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private int progress() {
        return player.getPlayerProgress().getInvestigation().get(60024);
    }

    @Test
    void finishedTripReturnsActualRewardEntryAndAdvancesHandbook() throws Exception {
        var result = claim(111, false);
        assertEquals(0, result.getRetcode());
        assertEquals(2, count(100001));
        assertEquals(1, progress());
        assertFalse(player.getExpeditionInfo().containsKey(111L));
        assertEquals(1, result.getExpeditionRewardListCount());
        var reward = result.getExpeditionRewardList(0);
        assertEquals(111, reward.getBasicInfo().getAvatarGuid());
        assertEquals(101, reward.getBasicInfo().getExpId());
        assertEquals(4, reward.getBasicInfo().getHourTime());
        assertEquals(2, reward.getItemList(0).getCount());
    }

    @Test
    void earlyTripCannotPayOrDisappear() throws Exception {
        player.getExpeditionInfo(111).setStartTime(Utils.getCurrentSeconds());
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
        assertEquals(0, progress());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
    }

    @Test
    void staleFinishedFlagCannotBypassElapsedTime() throws Exception {
        var info = player.getExpeditionInfo(111);
        info.setState(2);
        info.setStartTime(Utils.getCurrentSeconds() + 86400);
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
    }

    @Test
    void missingTripGetsFailureResponse() throws Exception {
        assertNotEquals(0, claim(999, false).getRetcode());
        assertEquals(0, progress());
    }

    @Test
    void replayCannotGrantAgain() throws Exception {
        assertEquals(0, claim(111, false).getRetcode());
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
        assertEquals(1, progress());
    }

    @Test
    void claimAllSelectsOnlyActuallyFinishedTripsAndIncludesEachEntry() throws Exception {
        expedition(112, 101, 4, 2, Utils.getCurrentSeconds() - 14401);
        expedition(113, 101, 4, 1, Utils.getCurrentSeconds());
        var result = claim(0, true);
        assertEquals(0, result.getRetcode());
        assertEquals(4, count(100001));
        assertEquals(2, result.getExpeditionRewardListCount());
        assertEquals(Set.of(113L), player.getExpeditionInfo().keySet());
        assertEquals(1, progress());
    }

    @Test
    void claimAllWithoutCompletedTripsIsAnError() throws Exception {
        player.getExpeditionInfo(111).setStartTime(Utils.getCurrentSeconds());
        assertNotEquals(0, claim(0, true).getRetcode());
        assertEquals(0, count(100001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void invalidOrOverflowingDurationCannotGrant(int hours) throws Exception {
        player.getExpeditionInfo(111).setHourTime(hours);
        reward(101, hours, 100001, 2, 2);
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    void invalidStateCannotGrant(int state) throws Exception {
        player.getExpeditionInfo(111).setState(state);
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
    }

    @Test
    void unknownRewardDefinitionKeepsTripAndReturnsError() throws Exception {
        system.getExpeditionRewardDataList().clear();
        assertNotEquals(0, claim(111, false).getRetcode());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
        assertEquals(0, progress());
    }

    @Test
    void fullMaterialBagCannotConsumeTripOrReportSuccess() throws Exception {
        player.getInventory()
                .getInventoryTypes()
                .put(ItemType.ITEM_MATERIAL.getValue(), new MaterialInventoryTab(0));
        assertNotEquals(0, claim(111, false).getRetcode());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
        assertEquals(0, progress());
    }

    @Test
    void aggregatedBatchRewardsCannotOverfillStack() throws Exception {
        assertTrue(player.getInventory().addItem(100001, 997));
        expedition(112, 101, 4, 2, Utils.getCurrentSeconds() - 14401);
        assertNotEquals(0, claim(0, true).getRetcode());
        assertEquals(997, count(100001));
        assertEquals(2, player.getExpeditionInfo().size());
    }

    @Test
    void overflowingCurrencyCannotConsumeTrip() throws Exception {
        reward(101, 4, 202, 2, 2);
        player.setMora(Integer.MAX_VALUE);
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(Integer.MAX_VALUE, player.getMora());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
    }

    @Test
    void missingItemDefinitionCannotConsumeTrip() throws Exception {
        reward(101, 4, 999999, 2, 2);
        assertNotEquals(0, claim(111, false).getRetcode());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
    }

    @Test
    void malformedRequestStillGetsFailureResponse() throws Exception {
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionGetRewardReq,
                        new byte[] {8, (byte) 0x80},
                        new byte[0]);
        assertEquals(1, session.responses.size());
        assertNotEquals(
                0,
                AvatarExpeditionGetRewardRsp.parseFrom(session.responses.get(0).getData())
                        .getRetcode());
        assertTrue(player.getExpeditionInfo().containsKey(111L));
    }

    @Test
    void grantCallbackCannotReplayReservedTrip() throws Exception {
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, claim(111, false).getRetcode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
        assertEquals(1, progress());
    }

    @Test
    void failedReservationNeverGrantsAndDoesNotBecomeRetryable() throws Exception {
        failSaveAt = 1;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
        failSaveAt = 0;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, progress());
    }

    @Test
    void refusedDeliveryRemainsPendingAndCannotRepeat() throws Exception {
        player.refuseGrant = true;
        assertNotEquals(0, claim(111, false).getRetcode());
        player.refuseGrant = false;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(0, count(100001));
        assertEquals(0, progress());
    }

    @Test
    void closedHandbookStillAllowsFinishedTrip() throws Exception {
        player.getOpenStates().put(1100, 0);
        assertEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
        assertEquals(0, progress());
    }

    @Test
    void legacyAllDataHandlerRefreshesFinishedStatesWithoutGranting() throws Exception {
        // This legacy request still has a negative placeholder opcode and is not registered.
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new emu.grasscutter.server.packet.recv.HandlerAvatarExpeditionAllDataReq()
                .handle(session, new byte[0], new byte[0]);
        var reply = AvatarExpeditionAllDataRsp.parseFrom(session.responses.get(0).getData());
        assertEquals(2, reply.getExpeditionInfoMapOrThrow(111).getStateValue());
        assertEquals(0, count(100001));
        assertEquals(0, progress());
    }

    @Test
    void parallelClaimsGrantTripOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<AvatarExpeditionGetRewardRsp>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> claim(111, false)));
            int successes = 0;
            for (var job : jobs) if (job.get(15, TimeUnit.SECONDS).getRetcode() == 0) successes++;
            assertEquals(1, successes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, count(100001));
        assertEquals(1, progress());
    }

    @Test
    void pendingDeliveryCannotBeResetByStartingAnotherTrip() throws Exception {
        player.refuseGrant = true;
        assertNotEquals(0, claim(111, false).getRetcode());
        var old = player.getExpeditionInfo(111);
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionStartReq,
                        AvatarExpeditionStartReq.newBuilder()
                                .addBasicInfoList(
                                        _AvatarExpeditionBasicInfo.newBuilder()
                                                .setAvatarGuid(111)
                                                .setExpId(101)
                                                .setHourTime(4))
                                .build()
                                .toByteArray(),
                        new byte[0]);
        assertNotEquals(
                0,
                AvatarExpeditionStartRsp.parseFrom(session.responses.get(0).getData())
                        .getRetcode());
        assertSame(old, player.getExpeditionInfo(111));
    }

    @Test
    void pendingDeliveryCannotBeErasedByCallingBackBatch() throws Exception {
        player.refuseGrant = true;
        assertNotEquals(0, claim(111, false).getRetcode());
        expedition(112, 101, 4, 1, Utils.getCurrentSeconds());
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionCallBackReq,
                        AvatarExpeditionCallBackReq.newBuilder()
                                .addAvatarGuid(112)
                                .addAvatarGuid(111)
                                .build()
                                .toByteArray(),
                        new byte[0]);
        assertNotEquals(
                0,
                AvatarExpeditionCallBackRsp.parseFrom(session.responses.get(0).getData())
                        .getRetcode());
        assertEquals(Set.of(111L, 112L), player.getExpeditionInfo().keySet());
    }

    @Test
    void partiallyGrantedRewardsCannotBeGrantedAgain() throws Exception {
        system.getExpeditionRewardDataList()
                .put(
                        101,
                        List.of(
                                gson.fromJson(
                                        "{hourTime:4,expeditionRewardData:[{itemId:100001,minCount:2,maxCount:2},{itemId:100002,minCount:3,maxCount:3}]}",
                                        ExpeditionRewardDataList.class)));
        player.afterGrant = () -> player.refuseGrant = true;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
        assertEquals(0, count(100002));
        assertEquals(0, progress());
        player.refuseGrant = false;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
    }

    @Test
    void failedFinalAcknowledgementDoesNotGrantAgain() throws Exception {
        failSaveAt = 4;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
        failSaveAt = 0;
        assertNotEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
    }

    @Test
    void reservationIsConfirmedBeforeAnyOutput() throws Exception {
        player.afterGrant =
                () -> {
                    assertTrue(confirmedSaves > 0);
                    assertTrue(snapshots.get(0).contains("\"rewardState\":1"));
                };
        assertEquals(0, claim(111, false).getRetcode());
        assertTrue(snapshots.stream().anyMatch(s -> s.contains("\"rewardState\":2")));
    }

    @Test
    void oldSaveWithoutRewardStateStillAllowsOneClaim() throws Exception {
        player.getExpeditionInfo()
                .put(
                        111L,
                        gson.fromJson(
                                "{state:2,expId:101,hourTime:4,startTime:"
                                        + (Utils.getCurrentSeconds() - 14401)
                                        + "}",
                                ExpeditionInfo.class));
        assertEquals(0, claim(111, false).getRetcode());
        assertEquals(2, count(100001));
    }

    @dev.morphia.annotations.Entity(value = "expedition_claim_test", useDiscriminator = false)
    static class StoredTrips {
        @dev.morphia.annotations.Id String id;
        Map<Long, ExpeditionInfo> trips;
    }

    @Test
    void pendingStateSurvivesRealMongoCodecAndPreventsGrantAfterReload() throws Exception {
        player.refuseGrant = true;
        assertNotEquals(0, claim(111, false).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database =
                "lunagc_test_expedition138_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredTrips.class);
            try {
                var record = new StoredTrips();
                record.id = "player1";
                record.trips = player.getExpeditionInfo();
                store.save(record);
                var loaded = store.find(StoredTrips.class).first();
                assertNotNull(loaded);
                player.getExpeditionInfo().clear();
                player.getExpeditionInfo().putAll(loaded.trips);
                player.refuseGrant = false;
                assertNotEquals(0, claim(111, false).getRetcode());
                assertEquals(0, count(100001));
            } finally {
                assertTrue(database.startsWith("lunagc_test_expedition138_"));
                client.getDatabase(database).drop();
            }
        }
    }
}
