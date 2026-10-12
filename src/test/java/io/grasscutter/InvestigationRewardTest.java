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
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Reward reservation boundaries and actual protocol state, independent of the delivery double. */
class InvestigationRewardTest {
    private AbilityTestFixture environment;
    private ClaimPlayer player;
    private final Gson gson = new Gson();
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Map<Integer, RewardData> oldRewards;
    private Map<Integer, ItemData> oldItems;
    private java.lang.reflect.Field datastoreField;
    private Object oldDatastore;
    private final List<String> writes = new java.util.concurrent.CopyOnWriteArrayList<>();

    static class ClaimPlayer extends AbilityTestFixture.TestPlayer {
        final Inventory inventory = new Inventory(this);

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value);
            return true;
        }

        @Override
        public int getUid() {
            return 10001;
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new ClaimPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        oldRewards = new HashMap<>(GameData.getRewardDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getRewardDataMap().clear();
        GameData.getItemDataMap().clear();
        var chapter =
                gson.fromJson(
                        "{id:10001,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE',rewardId:450001}",
                        InvestigationData.class);
        GameData.getInvestigationDataMap().put(10001, chapter);
        target(60001, 3, 450101);
        reward(450101, List.of(new ItemParamData(104003, 2), new ItemParamData(202, 5000)));
        item(104003, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 9999);
        item(202, "ITEM_VIRTUAL", "MATERIAL_NONE", 0);
        player.getPlayerProgress().getInvestigation().raise(60001, 3, 3);
        datastoreField =
                emu.grasscutter.database.DatabaseManager.class.getDeclaredField("gameDatastore");
        datastoreField.setAccessible(true);
        oldDatastore = datastoreField.get(null);
        datastoreField.set(
                null,
                java.lang.reflect.Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) {
                                if (args.length > 1) {
                                    var concern =
                                            ((dev.morphia.InsertOneOptions) args[1]).writeConcern();
                                    assertEquals("majority", concern.getWString());
                                    assertEquals(Boolean.TRUE, concern.getJournal());
                                    if (args[0] instanceof Player)
                                        writes.add("player:" + state(60001));
                                    else if (args[0] instanceof GameItem item)
                                        writes.add("item:" + item.getItemId());
                                }
                                return args[0];
                            }
                            return null;
                        }));
    }

    @AfterEach
    void close() throws Exception {
        assertTrue(
                emu.grasscutter.database.DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        if (datastoreField != null) datastoreField.set(null, oldDatastore);
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        GameData.getRewardDataMap().clear();
        GameData.getRewardDataMap().putAll(oldRewards);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        environment.close();
    }

    private void target(int id, int total, int reward) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationId:10001,progress:"
                                        + total
                                        + ",rewardId:"
                                        + reward
                                        + "}",
                                InvestigationTargetData.class));
    }

    private void reward(int id, List<ItemParamData> items) {
        var data = new RewardData();
        data.rewardId = id;
        data.rewardItemList = items;
        GameData.getRewardDataMap().put(id, data);
    }

    private void item(int id, String type, String material, int limit) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'"
                                        + type
                                        + "',materialType:'"
                                        + material
                                        + "',stackLimit:"
                                        + limit
                                        + "}",
                                ItemData.class));
    }

    private int take(int id, Runnable save, Consumer<List<ItemParamData>> grant) throws Exception {
        try {
            return (Integer)
                    Class.forName("emu.grasscutter.game.player.InvestigationRewards")
                            .getMethod(
                                    "takeTarget",
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

    private int state(int id) throws Exception {
        var data = player.getPlayerProgress().getInvestigation();
        return (Integer)
                data.getClass().getMethod("getTargetRewardState", int.class).invoke(data, id);
    }

    @Test
    void aCompleteTargetReservesBeforeGrantAndAcknowledgesBeforeClaimed() throws Exception {
        var order = new ArrayList<String>();
        assertEquals(
                0,
                take(
                        60001,
                        () -> {
                            try {
                                order.add("save:" + state(60001));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        items -> {
                            try {
                                assertEquals(1, state(60001));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            order.add("grant");
                            assertEquals(2, items.get(0).getCount());
                            assertEquals(5000, items.get(1).getCount());
                        }));
        assertEquals(List.of("save:1", "grant", "save:2"), order);
        assertEquals(2, state(60001));
    }

    @Test
    void repeatedClaimCannotReplayTheRewards() throws Exception {
        var grants = new AtomicInteger();
        assertEquals(0, take(60001, () -> {}, items -> grants.incrementAndGet()));
        assertEquals(
                Retcode.RET_REWARD_HAS_TAKEN_VALUE,
                take(60001, () -> fail("Duplicate save"), items -> fail("Duplicate grant")));
        assertEquals(1, grants.get());
    }

    @Test
    void incompleteTargetCannotReserveOrGrant() throws Exception {
        target(60001, 4, 450101);
        assertNotEquals(
                0, take(60001, () -> fail("Incomplete save"), items -> fail("Incomplete grant")));
        assertEquals(0, state(60001));
    }

    @Test
    void closedManualCannotTakeAPreviouslyCompletedTarget() throws Exception {
        player.getOpenStates().put(1100, 0);
        assertEquals(
                Retcode.RET_OPEN_STATE_NOT_OPEN_VALUE, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void insufficientRankCannotTakeACompletedTarget() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 0);
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void missingOrDisusedTargetDoesNotFallBackToAnotherReward() throws Exception {
        assertNotEquals(0, take(6000001, () -> fail(), items -> fail()));
        GameData.getInvestigationTargetDataMap()
                .put(
                        60001,
                        gson.fromJson(
                                "{id:60001,investigationId:10001,progress:1,rewardId:450101,isDisuse:true}",
                                InvestigationTargetData.class));
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void missingRewardResourceDoesNotMarkTheTargetClaimed() throws Exception {
        GameData.getRewardDataMap().remove(450101);
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
        assertEquals(0, state(60001));
    }

    @Test
    void preflightChecksTheEntireRewardBeforeAnyWrite() throws Exception {
        reward(450101, List.of(new ItemParamData(202, 5000), new ItemParamData(999999, 1)));
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
        assertEquals(0, state(60001));
    }

    @Test
    void materialCapacityRejectsTheWholeReward() throws Exception {
        player.inventory.createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(0));
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void duplicateRowsCannotBypassStackLimitOrOverflowChecks() throws Exception {
        reward(450101, List.of(new ItemParamData(104003, 6000), new ItemParamData(104003, 6000)));
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, take(60001, () -> fail(), items -> fail()));
        reward(
                450101,
                List.of(new ItemParamData(202, Integer.MAX_VALUE), new ItemParamData(202, 1)));
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void invalidCountOrUseOnGainDefinitionRemainsUnclaimable() throws Exception {
        reward(450101, List.of(new ItemParamData(202, 0)));
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
        reward(450101, List.of(new ItemParamData(202, 1)));
        GameData.getItemDataMap()
                .put(
                        202,
                        gson.fromJson(
                                "{id:202,itemType:'ITEM_VIRTUAL',useOnGain:true}", ItemData.class));
        assertNotEquals(0, take(60001, () -> fail(), items -> fail()));
    }

    @Test
    void reservationFailureNeverInvokesTheGrant() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                60001,
                                () -> {
                                    throw new IllegalStateException("Reservation failed");
                                },
                                items -> fail("Unacknowledged grant")));
        assertNotEquals(2, state(60001));
    }

    @Test
    void aPartiallyGrantedRewardStaysReservedAndCannotBeRetried() throws Exception {
        var grants = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                60001,
                                () -> {},
                                items -> {
                                    grants.incrementAndGet();
                                    throw new IllegalStateException("Second item failed");
                                }));
        assertEquals(1, state(60001));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(60001, () -> fail(), items -> fail()));
        assertEquals(1, grants.get());
    }

    @Test
    void finalAcknowledgementFailureDoesNotAllowADuplicateGrant() throws Exception {
        var writes = new AtomicInteger();
        var grants = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                60001,
                                () -> {
                                    if (writes.incrementAndGet() == 2)
                                        throw new IllegalStateException("Final ack lost");
                                },
                                items -> grants.incrementAndGet()));
        assertEquals(1, state(60001));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(60001, () -> fail(), items -> fail()));
        assertEquals(1, grants.get());
    }

    @Test
    void concurrentRequestsForOneTargetGrantOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        var grants = new AtomicInteger();
        try {
            var jobs = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++)
                jobs.add(
                        pool.submit(
                                () -> take(60001, () -> {}, items -> grants.incrementAndGet())));
            var results = new ArrayList<Integer>();
            for (var job : jobs) results.add(job.get(5, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 0).count());
            assertEquals(1, grants.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loginNotificationPreservesTheClaimedState() throws Exception {
        assertEquals(0, take(60001, () -> {}, items -> {}));
        player.packets.clear();
        InvestigationHandbook.onPlayerLogin(player);
        var packet =
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == PacketOpcodes.PlayerInvestigationAllInfoNotify)
                        .findFirst()
                        .orElseThrow();
        var data = PlayerInvestigationAllInfoNotify.parseFrom(packet.getData());
        assertEquals(3, data.getInvestigationTargetList(0).getStateValue());
    }

    private int actualTake() throws Exception {
        return (Integer)
                Class.forName("emu.grasscutter.game.player.InvestigationRewards")
                        .getMethod("takeTarget", Player.class, int.class)
                        .invoke(null, player, 60001);
    }

    @Test
    void realInventoryReceivesOriginalMaterialAndMoraCountsWithAcknowledgedWrites()
            throws Exception {
        assertEquals(0, actualTake());
        assertEquals(2, player.inventory.getItemCountById(104003));
        assertEquals(5000, player.getMora());
        assertEquals(2, state(60001));
        assertEquals("player:1", writes.get(0));
        assertTrue(writes.contains("item:104003"));
        assertEquals("player:2", writes.get(writes.size() - 1));
    }

    @Test
    void realAdventureExpUsesTheInventoryPathAndKeepsTheConfiguredAmount() throws Exception {
        item(102, "ITEM_VIRTUAL", "MATERIAL_NONE", 0);
        reward(450101, List.of(new ItemParamData(102, 100), new ItemParamData(202, 5000)));
        assertEquals(0, actualTake());
        assertEquals(100, player.getExp());
        assertEquals(5000, player.getMora());
    }

    @Test
    void multipleRelicsAreIndividualOwnedItemsRatherThanOneTruncatedStack() throws Exception {
        GameData.getItemDataMap()
                .put(
                        74432,
                        gson.fromJson(
                                "{id:74432,itemType:'ITEM_RELIQUARY',rankLevel:4,appendPropNum:0}",
                                ItemData.class));
        reward(450101, List.of(new ItemParamData(74432, 2)));
        assertEquals(0, actualTake());
        var relics =
                player.inventory.getItems().values().stream()
                        .filter(i -> i.getItemId() == 74432)
                        .toList();
        assertEquals(2, relics.size());
        assertNotEquals(relics.get(0).getGuid(), relics.get(1).getGuid());
        for (var relic : relics) {
            assertEquals(1, relic.getCount());
            assertEquals(10001, relic.getOwnerId());
        }
    }

    @Test
    void allRelicSlotsAreCheckedBeforeReservation() throws Exception {
        GameData.getItemDataMap()
                .put(
                        74432,
                        gson.fromJson(
                                "{id:74432,itemType:'ITEM_RELIQUARY',rankLevel:4}",
                                ItemData.class));
        reward(450101, List.of(new ItemParamData(74432, 2)));
        player.inventory.createInventoryTab(ItemType.ITEM_RELIQUARY, new EquipInventoryTab(1));
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, actualTake());
        assertEquals(0, state(60001));
        assertEquals(0, player.inventory.getItems().size());
    }

    @Test
    void actualGrantFailureLeavesAReceiptThatCannotBeClaimedAgain() throws Exception {
        datastoreField.set(
                null,
                java.lang.reflect.Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    && args[0] instanceof GameItem
                                    && args.length > 1)
                                throw new IllegalStateException("Item acknowledgement failed");
                            return args[0];
                        }));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, actualTake());
        assertEquals(1, state(60001));
        int held = player.inventory.getItemCountById(104003);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, actualTake());
        assertEquals(held, player.inventory.getItemCountById(104003));
    }

    static class ClaimSession extends emu.grasscutter.server.game.GameSession {
        final Player current;
        final List<BasePacket> responses = new ArrayList<>();

        ClaimSession(emu.grasscutter.server.game.GameServer server, Player player) {
            super(server);
            current = player;
            setState(SessionState.ACTIVE);
        }

        @Override
        public Player getPlayer() {
            return current;
        }

        @Override
        public void send(BasePacket packet) {
            responses.add(packet);
        }
    }

    private BasePacket request(byte[] payload) throws Exception {
        var session = new ClaimSession(environment.scene.getWorld().getServer(), player);
        var registry = new emu.grasscutter.server.game.GameServerPacketHandler(PacketHandler.class);
        registry.handle(session, 2703, new byte[0], payload);
        return session.responses.get(0);
    }

    @Test
    void nativeTargetRequestIsRegisteredAndRespondsWithTheSameResourceId() throws Exception {
        var type =
                Class.forName(
                        "emu.grasscutter.server.packet.recv.HandlerTakeInvestigationTargetRewardReq");
        assertEquals(2703, type.getAnnotation(Opcodes.class).value());
        var bytes = new java.io.ByteArrayOutputStream();
        var output = com.google.protobuf.CodedOutputStream.newInstance(bytes);
        output.writeUInt32(9, 60001);
        output.flush();
        var packet = request(bytes.toByteArray());
        assertEquals(PacketOpcodes.TakeInvestigationTargetRewardRsp, packet.getOpcode());
        var response =
                emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass
                        .TakeInvestigationTargetRewardRsp.parseFrom(packet.getData());
        assertEquals(60001, response.getQuestId());
        assertEquals(0, response.getRetcode());
    }

    @Test
    void truncatedNativeRequestReturnsFailureWithoutAReward() throws Exception {
        var packet = request(new byte[] {0x48, (byte) 0x80});
        var response =
                emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass
                        .TakeInvestigationTargetRewardRsp.parseFrom(packet.getData());
        assertNotEquals(0, response.getRetcode());
        assertEquals(0, state(60001));
        assertEquals(0, player.getMora());
        assertTrue(writes.isEmpty());
    }

    @Test
    void reentrantRequestDuringAnItemCallbackCannotTakeTheSameReceiptTwice() throws Exception {
        assertEquals(
                0,
                take(
                        60001,
                        () -> {},
                        items -> {
                            try {
                                assertEquals(
                                        Retcode.RET_SVR_ERROR_VALUE,
                                        take(60001, () -> fail(), again -> fail()));
                            } catch (Exception failure) {
                                throw new RuntimeException(failure);
                            }
                        }));
        assertEquals(2, state(60001));
    }

    @Test
    void receiptsPersistThroughMongoAndPreventReplayAfterReload() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database =
                "lunagc_test_investigation130_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(0, take(60001, () -> {}, items -> {}));
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(InvestigationProgressTest.StoredProgress.class);
            try {
                var record = new InvestigationProgressTest.StoredProgress();
                record.id = "claimant";
                record.progress = player.getPlayerProgress();
                store.save(record);
                var loaded = store.find(InvestigationProgressTest.StoredProgress.class).first();
                assertNotNull(loaded);
                var field = Player.class.getDeclaredField("playerProgress");
                field.setAccessible(true);
                field.set(player, loaded.progress);
                assertEquals(2, state(60001));
                assertEquals(
                        Retcode.RET_REWARD_HAS_TAKEN_VALUE,
                        take(60001, () -> fail(), items -> fail()));
                player.getPlayerProgress()
                        .getInvestigation()
                        .getClass()
                        .getMethod("setTargetRewardState", int.class, int.class)
                        .invoke(player.getPlayerProgress().getInvestigation(), 60001, 1);
                record.progress = player.getPlayerProgress();
                store.save(record);
                loaded = store.find(InvestigationProgressTest.StoredProgress.class).first();
                field.set(player, loaded.progress);
                assertEquals(1, state(60001));
                assertEquals(
                        Retcode.RET_SVR_ERROR_VALUE, take(60001, () -> fail(), items -> fail()));
            } finally {
                assertTrue(database.startsWith("lunagc_test_investigation130_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void everyResourceRewardInTheSupportedChaptersPassesTheActualPreflight() throws Exception {
        var directory = java.nio.file.Path.of("resources/ExcelBinOutput");
        var chapters =
                gson.fromJson(
                        java.nio.file.Files.readString(
                                directory.resolve("InvestigationConfigData.json")),
                        InvestigationData[].class);
        var targets =
                gson.fromJson(
                        java.nio.file.Files.readString(
                                directory.resolve("InvestigationTargetConfigData.json")),
                        InvestigationTargetData[].class);
        var parents = new HashSet<Integer>();
        var ids = new HashSet<Integer>();
        for (var chapter : chapters)
            if ("INVESTIGATION_ADVENTURE".equals(chapter.getInvestigationType())
                    || "INVESTIGATION_EDCATION".equals(chapter.getInvestigationType())) {
                parents.add(chapter.getId());
                ids.add(chapter.getRewardId());
            }
        for (var target : targets)
            if (parents.contains(target.getInvestigationId()) && !target.isDisuse())
                ids.add(target.getRewardId());
        ids.remove(0); // The four education chapters explicitly have no chapter reward.
        var rewards =
                gson.fromJson(
                        java.nio.file.Files.readString(
                                directory.resolve("RewardExcelConfigData.json")),
                        RewardData[].class);
        var needed = new HashSet<Integer>();
        var selected = new ArrayList<RewardData>();
        for (var reward : rewards)
            if (ids.contains(reward.getId())) {
                reward.onLoad();
                selected.add(reward);
                for (var item : reward.getRewardItemList()) needed.add(item.getId());
            }
        for (String kind : List.of("Material", "Reliquary"))
            for (var definition :
                    gson.fromJson(
                            java.nio.file.Files.readString(
                                    directory.resolve(kind + "ExcelConfigData.json")),
                            ItemData[].class))
                if (needed.contains(definition.getId()))
                    GameData.getItemDataMap().put(definition.getId(), definition);
        assertEquals(141, selected.size());
        assertEquals(84, needed.size());
        var validator =
                Class.forName("emu.grasscutter.game.player.InvestigationRewardDelivery")
                        .getMethod("validate", Player.class, List.class);
        for (var reward : selected)
            assertEquals(
                    0,
                    validator.invoke(null, player, reward.getRewardItemList()),
                    "Reward " + reward.getId());
    }

    @Test
    void unknownPersistedReceiptStatesCannotBeReplayed() throws Exception {
        var corrupted =
                gson.fromJson(
                        "{targetRewards:{60001:9}}",
                        player.getPlayerProgress().getInvestigation().getClass());
        var field = PlayerProgress.class.getDeclaredField("investigation");
        field.setAccessible(true);
        field.set(player.getPlayerProgress(), corrupted);
        player.getPlayerProgress().getInvestigation().raise(60001, 3, 3);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, take(60001, () -> fail(), items -> fail()));
    }
}
