package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerInvestigationAllInfoNotifyOuterClass.PlayerInvestigationAllInfoNotify;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class InvestigationChapterRewardTest {
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private final Gson gson = new Gson();
    private Map<Integer, InvestigationData> chapters;
    private Map<Integer, InvestigationTargetData> targets;
    private Map<Integer, RewardData> rewards;
    private Map<Integer, ItemData> items;

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        chapters = new HashMap<>(GameData.getInvestigationDataMap());
        targets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        rewards = new HashMap<>(GameData.getRewardDataMap());
        items = new HashMap<>(GameData.getItemDataMap());
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getRewardDataMap().clear();
        GameData.getItemDataMap().clear();
        chapter(450001, false);
        target(60001, 3, false);
        target(60002, 1, false);
        player.getPlayerProgress().getInvestigation().raise(60001, 3, 3);
        player.getPlayerProgress().getInvestigation().raise(60002, 1, 1);
        var reward = new RewardData();
        reward.rewardId = 450001;
        reward.rewardItemList = List.of(new ItemParamData(202, 10000), new ItemParamData(201, 50));
        GameData.getRewardDataMap().put(450001, reward);
        for (int id : List.of(201, 202))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:" + id + ",itemType:'ITEM_VIRTUAL'}", ItemData.class));
    }

    @AfterEach
    void close() {
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(chapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(targets);
        GameData.getRewardDataMap().clear();
        GameData.getRewardDataMap().putAll(rewards);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(items);
        environment.close();
    }

    private void chapter(int reward, boolean empty) {
        GameData.getInvestigationDataMap()
                .put(
                        10001,
                        gson.fromJson(
                                "{id:10001,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE',rewardId:"
                                        + reward
                                        + ",isRewardEmpty:"
                                        + empty
                                        + "}",
                                InvestigationData.class));
    }

    private void target(int id, int progress, boolean disused) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationId:10001,progress:"
                                        + progress
                                        + ",isDisuse:"
                                        + disused
                                        + "}",
                                InvestigationTargetData.class));
    }

    private int take(int id, Runnable save, Consumer<List<ItemParamData>> grant) throws Exception {
        try {
            return (Integer)
                    InvestigationRewardsClass()
                            .getMethod(
                                    "takeChapter",
                                    Player.class,
                                    int.class,
                                    Runnable.class,
                                    Consumer.class)
                            .invoke(null, player, id, save, grant);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof Exception cause) throw cause;
            throw wrapped;
        }
    }

    private Class<?> InvestigationRewardsClass() throws Exception {
        return Class.forName("emu.grasscutter.game.player.InvestigationRewards");
    }

    private int state() throws Exception {
        var state = player.getPlayerProgress().getInvestigation();
        return (Integer)
                state.getClass().getMethod("getChapterRewardState", int.class).invoke(state, 10001);
    }

    @Test
    void completeChapterAcknowledgesItsReceiptAndOriginalRewardQuantities() throws Exception {
        var order = new ArrayList<String>();
        assertEquals(
                0,
                take(
                        10001,
                        () -> {
                            try {
                                order.add("save:" + state());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        list -> {
                            order.add("grant");
                            assertEquals(10000, list.get(0).getCount());
                            assertEquals(50, list.get(1).getCount());
                        }));
        assertEquals(List.of("save:1", "grant", "save:2"), order);
        assertEquals(2, state());
    }

    @Test
    void chapterIsNotClaimableWhenOneActiveTargetIsIncomplete() throws Exception {
        target(60002, 2, false);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
        assertEquals(0, state());
    }

    @Test
    void disusedTargetsDoNotBlockACompleteChapter() throws Exception {
        target(60003, 2, true);
        assertEquals(0, take(10001, () -> {}, list -> {}));
    }

    @Test
    void invalidActiveTargetDefinitionsDoNotMakeCompletionEasier() throws Exception {
        target(60003, 0, false);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void aChapterWithNoActiveTargetsCannotBeMarkedComplete() throws Exception {
        GameData.getInvestigationTargetDataMap().clear();
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void missingChapterOrRewardCannotReserveAnything() throws Exception {
        assertNotEquals(0, take(99999, () -> fail(), list -> fail()));
        GameData.getRewardDataMap().remove(450001);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
        assertEquals(0, state());
    }

    @Test
    void closedChapterAndInsufficientRankRemainUnclaimable() throws Exception {
        player.getOpenStates().put(1101, 0);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
        player.getOpenStates().put(1101, 1);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 0);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void resourceDeclaredEmptyChapterCommitsWithoutInventingItems() throws Exception {
        chapter(0, true);
        var saves = new AtomicInteger();
        assertEquals(
                0,
                take(10001, saves::incrementAndGet, list -> fail("Empty chapter must not grant")));
        assertEquals(2, saves.get());
        assertEquals(2, state());
    }

    @Test
    void zeroRewardWithoutTheEmptyDeclarationIsNotAccepted() throws Exception {
        chapter(0, false);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void contradictoryEmptyDeclarationRemainsBlocked() throws Exception {
        chapter(450001, true);
        assertNotEquals(0, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void repeatedChapterRequestsDoNotGrantAgain() throws Exception {
        assertEquals(0, take(10001, () -> {}, list -> {}));
        assertEquals(Retcode.RET_REWARD_HAS_TAKEN_VALUE, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void reservationFailureDoesNotDeliverAnyItems() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                10001,
                                () -> {
                                    throw new IllegalStateException("DB unavailable");
                                },
                                list -> fail()));
        assertEquals(0, state());
    }

    @Test
    void partialDeliveryAndFinalAckFailuresPreventReplay() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                10001,
                                () -> {},
                                list -> {
                                    throw new IllegalStateException("Partial grant");
                                }));
        assertEquals(1, state());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void finalChapterAcknowledgementFailureLeavesTheReservation() throws Exception {
        var saves = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                10001,
                                () -> {
                                    if (saves.incrementAndGet() == 2)
                                        throw new IllegalStateException("Lost final ack");
                                },
                                list -> {}));
        assertEquals(1, state());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(10001, () -> fail(), list -> fail()));
    }

    @Test
    void chapterAndTargetReceiptsStayIndependent() throws Exception {
        player.getPlayerProgress().getInvestigation().setTargetRewardState(10001, 2);
        assertEquals(0, take(10001, () -> {}, list -> {}));
        assertEquals(2, state());
        assertEquals(0, player.getPlayerProgress().getInvestigation().getTargetRewardState(60001));
    }

    @Test
    void concurrentChapterRequestsCommitOneGrant() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        var grants = new AtomicInteger();
        try {
            var jobs = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++)
                jobs.add(
                        pool.submit(() -> take(10001, () -> {}, list -> grants.incrementAndGet())));
            int successes = 0;
            for (var job : jobs) if (job.get(5, TimeUnit.SECONDS) == 0) successes++;
            assertEquals(1, successes);
            assertEquals(1, grants.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loginShowsTheChapterRewardAsTakenWithoutTakingTheTargets() throws Exception {
        assertEquals(0, take(10001, () -> {}, list -> {}));
        player.packets.clear();
        InvestigationHandbook.onPlayerLogin(player);
        var data = PlayerInvestigationAllInfoNotify.parseFrom(player.packets.get(0).getData());
        assertEquals(3, data.getInvestigationList(0).getStateValue());
        assertTrue(
                data.getInvestigationTargetListList().stream()
                        .allMatch(t -> t.getStateValue() == 2));
    }

    private BasePacket request(byte[] bytes, List<Integer> reasons) throws Exception {
        var field =
                emu.grasscutter.database.DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(
                null,
                java.lang.reflect.Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) return args[0];
                            return null;
                        }));
        try {
            var session =
                    new InvestigationRewardTest.ClaimSession(
                            environment.scene.getWorld().getServer(), player);
            var registry =
                    new emu.grasscutter.server.game.GameServerPacketHandler(PacketHandler.class);
            registry.handle(session, 8841, new byte[0], bytes);
            for (var packet : player.packets)
                if (packet.getOpcode() == PacketOpcodes.ItemAddHintNotify) {
                    var hint =
                            emu.grasscutter.net.proto.ItemAddHintNotifyOuterClass.ItemAddHintNotify
                                    .parseFrom(packet.getData());
                    reasons.add(hint.getReason());
                }
            return session.responses.get(0);
        } finally {
            assertTrue(
                    emu.grasscutter.database.DatabaseHelper.awaitIdle(
                            java.time.Duration.ofSeconds(5)));
            field.set(null, previous);
        }
    }

    @Test
    void nativeChapterGetterAndFieldAgreeWithTheHandbookSender() throws Exception {
        assertEquals(8841, PacketOpcodes.TakeInvestigationRewardReq);
        var type =
                Class.forName(
                        "emu.grasscutter.net.proto.TakeInvestigationRewardReqOuterClass$TakeInvestigationRewardReq");
        var message =
                (com.google.protobuf.Message)
                        type.getMethod("parseFrom", byte[].class)
                                .invoke(null, (Object) new byte[] {0x30, (byte) 0x91, 0x4e});
        assertEquals(10001, message.getField(message.getDescriptorForType().findFieldByName("id")));
        assertArrayEquals(new byte[] {0x30, (byte) 0x91, 0x4e}, message.toByteArray());
    }

    @Test
    void nativeRegisteredChapterRequestGrantsAndEchoesTheIdWithTheChapterReason() throws Exception {
        var reasons = new ArrayList<Integer>();
        var packet = request(new byte[] {0x30, (byte) 0x91, 0x4e}, reasons);
        assertEquals(PacketOpcodes.TakeInvestigationRewardRsp, packet.getOpcode());
        var response =
                emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass
                        .TakeInvestigationRewardRsp.parseFrom(packet.getData());
        assertEquals(10001, response.getId());
        assertEquals(0, response.getRetcode());
        assertEquals(10000, player.getMora());
        assertEquals(50, player.getPrimogems());
        assertEquals(2, state());
        assertFalse(reasons.isEmpty());
        assertTrue(
                reasons.stream()
                        .allMatch(
                                reason ->
                                        reason
                                                == emu.grasscutter.game.props.ActionReason
                                                        .InvestigationReward.getValue()));
    }

    @Test
    void obsoleteFieldFiveIsNotAcceptedAsTheChapterId() throws Exception {
        var packet = request(new byte[] {0x28, (byte) 0x91, 0x4e}, new ArrayList<>());
        var response =
                emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass
                        .TakeInvestigationRewardRsp.parseFrom(packet.getData());
        assertNotEquals(0, response.getRetcode());
        assertEquals(0, player.getMora());
        assertEquals(0, state());
    }

    @Test
    void aTruncatedChapterRequestDoesNotTouchTheReceipt() throws Exception {
        var packet = request(new byte[] {0x30, (byte) 0x80}, new ArrayList<>());
        var response =
                emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass
                        .TakeInvestigationRewardRsp.parseFrom(packet.getData());
        assertNotEquals(0, response.getRetcode());
        assertEquals(0, state());
    }

    @Test
    void chapterReceiptsSurviveTheRealMongoCodecAndRemainSeparateFromTargets() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database =
                "lunagc_test_investigation131_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(0, take(10001, () -> {}, list -> {}));
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(InvestigationProgressTest.StoredProgress.class);
            try {
                var saved = new InvestigationProgressTest.StoredProgress();
                saved.id = "chapter1";
                saved.progress = player.getPlayerProgress();
                store.save(saved);
                var loaded = store.find(InvestigationProgressTest.StoredProgress.class).first();
                assertNotNull(loaded);
                var field = Player.class.getDeclaredField("playerProgress");
                field.setAccessible(true);
                field.set(player, loaded.progress);
                assertEquals(2, state());
                assertEquals(
                        0,
                        player.getPlayerProgress().getInvestigation().getTargetRewardState(60001));
                assertEquals(
                        Retcode.RET_REWARD_HAS_TAKEN_VALUE,
                        take(10001, () -> fail(), list -> fail()));
                var history = player.getPlayerProgress().getInvestigation();
                history.getClass()
                        .getMethod("setChapterRewardState", int.class, int.class)
                        .invoke(history, 10001, 1);
                saved.progress = player.getPlayerProgress();
                store.save(saved);
                loaded = store.find(InvestigationProgressTest.StoredProgress.class).first();
                field.set(player, loaded.progress);
                assertEquals(
                        Retcode.RET_SVR_ERROR_VALUE, take(10001, () -> fail(), list -> fail()));
            } finally {
                assertTrue(database.startsWith("lunagc_test_investigation131_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void unknownSavedChapterStateCannotGrantAgain() throws Exception {
        var existing = player.getPlayerProgress().getInvestigation();
        var history =
                gson.fromJson(
                        "{chapterRewards:{10001:9},targets:{60001:3,60002:1}}",
                        existing.getClass());
        var field = PlayerProgress.class.getDeclaredField("investigation");
        field.setAccessible(true);
        field.set(player.getPlayerProgress(), history);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(10001, () -> fail(), list -> fail()));
    }
}
