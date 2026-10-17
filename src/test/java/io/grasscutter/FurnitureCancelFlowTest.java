package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeCancelReqOuterClass.FurnitureMakeCancelReq;
import emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurnitureCancelFlowTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;

    static class CraftPlayer extends InvestigationRewardTest.ClaimPlayer {
        transient GameHome home;
        transient boolean refund, pretend;
        transient int refuseId, grants, missions;
        transient Runnable afterGrant;
        final transient Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item, boolean skip, boolean notify) {
                        if (refund && item.getItemType() == ItemType.ITEM_MATERIAL) {
                            grants++;
                            if (item.getItemId() == refuseId) return false;
                            if (pretend) return true;
                        }
                        boolean result = super.addItem(item, skip, notify);
                        if (refund && afterGrant != null) {
                            var hook = afterGrant;
                            afterGrant = null;
                            hook.run();
                        }
                        return result;
                    }
                };
        final transient BattlePassManager battlepass =
                new BattlePassManager() {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {
                        if (type == WatcherTriggerType.TRIGGER_FURNITURE_MAKE) missions += progress;
                    }
                };

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return battlepass;
        }
    }

    private AbilityTestFixture environment;
    private CraftPlayer player;
    private Field datastore;
    private Object previousDatastore;
    private int saves, failSave;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new CraftPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .level(1)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        clear(GameData.getItemDataMap());
        clear(GameData.getFurnitureMakeConfigDataMap());
        clear(GameData.getHomeWorldLevelDataMap());
        item(360101, "ITEM_FURNITURE", 9999);
        item(101311, "ITEM_MATERIAL", 9999);
        item(101312, "ITEM_MATERIAL", 9999);
        recipe(4, 2);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        player.getUnlockedFurniture().add(360101);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        playerStore =
                new emu.grasscutter.game.player.GuardedPlayerTestStore(
                        player,
                        () -> {
                            if (emu.grasscutter.game.inventory.ObservedItemTestWrites
                                            .isSynchronousWrite()
                                    && ++saves == failSave)
                                throw new IllegalStateException("Write fault");
                        });
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMapper"))
                                return playerStore.datastore().getMapper();
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Write fault");
                                if (method.getName().equals("delete"))
                                    return com.mongodb.client.result.DeleteResult.acknowledged(1);
                                return method.invoke(playerStore.datastore(), args);
                            }
                            return null;
                        }));
        itemWrites =
                new emu.grasscutter.game.inventory.ObservedItemTestWrites(
                        player,
                        playerStore.datastore(),
                        DatabaseManager.getGameDatastore(),
                        doc -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Item write fault");
                        },
                        doc -> {});
        assertTrue(player.inventory.addItem(101311, 40));
        assertTrue(player.inventory.addItem(101312, 20));
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (playerStore != null) playerStore.close();
        datastore.set(null, previousDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    void item(int id, String type, int limit) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:" + limit + "}",
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(int a, int b) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[{id:101311,count:"
                                        + a
                                        + "},{id:101312,count:"
                                        + b
                                        + "}]}",
                                FurnitureMakeConfigData.class));
    }

    FurnitureMakeSlotItem start() throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.refund = false;
        player.getFurnitureManager().startMake(38, 0);
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .toList();
        assertEquals(1, packets.size());
        assertEquals(0, FurnitureMakeStartRsp.parseFrom(packets.get(0).getData()).getRetcode());
        saves = 0;
        player.refund = true;
        return player.home
                .getFurnitureMakeSlotItemList()
                .get(player.home.getFurnitureMakeSlotItemList().size() - 1);
    }

    FurnitureMakeCancelRsp cancel(FurnitureMakeSlotItem slot) throws Exception {
        return cancel(slot.getIndex(), slot.getMakeId());
    }

    FurnitureMakeCancelRsp cancel(int index, int make) throws Exception {
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        Method method = null;
        try {
            method =
                    player.getFurnitureManager()
                            .getClass()
                            .getMethod("cancel", int.class, int.class);
        } catch (NoSuchMethodException absent) {
        }
        assertNotNull(method, "Cancellation handler is not connected");
        try {
            method.invoke(player.getFurnitureManager(), index, make);
        } finally {
            player.setSession(old);
        }
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeCancelRsp)
                        .toList();
        assertEquals(1, packets.size());
        return FurnitureMakeCancelRsp.parseFrom(packets.get(0).getData());
    }

    Object pending() {
        try {
            return player.home
                    .getClass()
                    .getMethod("getPendingFurnitureCancel")
                    .invoke(player.home);
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    void intact(FurnitureMakeSlotItem slot) {
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
        assertEquals(36, player.inventory.getItemCountById(101311));
        assertEquals(18, player.inventory.getItemCountById(101312));
    }

    @Test
    void unfinishedPaidJobRefundsTheExactMaterialsOnce() throws Exception {
        var slot = start();
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertNull(pending());
        assertTrue(saves >= 8);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @Test
    void refundDoesNotCreateFurnitureOrCraftProgress() throws Exception {
        var slot = start();
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertEquals(0, player.home.getExp());
        assertEquals(0, player.missions);
    }

    @Test
    void removedRecipeCannotLoseTheCapturedRefund() throws Exception {
        var slot = start();
        GameData.getFurnitureMakeConfigDataMap().remove(38);
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @Test
    void replacementRecipeCannotIncreaseTheRefund() throws Exception {
        var slot = start();
        recipe(99, 99);
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
    }

    @Test
    void completedJobCannotRefundAndThenBeClaimed() throws Exception {
        var slot = start();
        slot.setBeginTime(Utils.getCurrentSeconds() - 7200);
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
    }

    @Test
    void foreignHomeCannotRefundItsOwnersMaterials() throws Exception {
        var slot = start();
        player.home.setOwnerUid(9);
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
    }

    @Test
    void missingHomeReturnsOneFailure() throws Exception {
        player.home = null;
        assertNotEquals(0, cancel(0, 38).getRetcode());
    }

    @Test
    void unknownSlotCannotRefund() throws Exception {
        var slot = start();
        assertNotEquals(0, cancel(9, 38).getRetcode());
        intact(slot);
    }

    @Test
    void mismatchedRecipeCannotRefund() throws Exception {
        var slot = start();
        assertNotEquals(0, cancel(slot.getIndex(), 39).getRetcode());
        intact(slot);
    }

    @Test
    void negativeIndexCannotRefund() throws Exception {
        var slot = start();
        assertNotEquals(0, cancel(-1, 38).getRetcode());
        intact(slot);
    }

    @Test
    void duplicateLegacyIndexesCannotRefundTwice() throws Exception {
        var slot = start();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(36, player.inventory.getItemCountById(101311));
    }

    @Test
    void legacyJobWithoutOriginalCostsIsNotGuessed() throws Exception {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(0)
                        .makeId(38)
                        .beginTime(Utils.getCurrentSeconds())
                        .durTime(3600)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @Test
    void everyRefundProductIsCheckedBeforeGrantingAny() throws Exception {
        var slot = start();
        GameData.getItemDataMap().remove(101312);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(36, player.inventory.getItemCountById(101311));
        assertEquals(0, player.grants);
    }

    @Test
    void fullRefundStackKeepsTheJob() throws Exception {
        var slot = start();
        item(101311, "ITEM_MATERIAL", 39);
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertEquals(0, player.grants);
    }

    @Test
    void refusedRefundPreservesTheJobAndBlocksReplay() throws Exception {
        var slot = start();
        player.refuseId = 101311;
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertNotNull(pending());
        int grants = player.grants;
        player.refuseId = 0;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(grants, player.grants);
    }

    @Test
    void pretendRefundCannotRemoveTheJob() throws Exception {
        var slot = start();
        player.pretend = true;
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertNotNull(pending());
    }

    @Test
    void partialRefundCannotBeRepeated() throws Exception {
        var slot = start();
        player.refuseId = 101312;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(18, player.inventory.getItemCountById(101312));
        assertNotNull(pending());
        player.refuseId = 0;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(18, player.inventory.getItemCountById(101312));
    }

    @Test
    void anotherJobRemainsInTheSuccessResponse() throws Exception {
        var first = start();
        var second = start();
        var response = cancel(first);
        assertEquals(0, response.getRetcode());
        assertEquals(1, response.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
        assertEquals(
                second.getIndex(),
                response.getFurnitureMakeSlot().getFurnitureMakeDataList(0).getIndex());
    }

    @Test
    void reentryDuringARefundCannotRefundAgain() throws Exception {
        var slot = start();
        final int[] ret = {0};
        player.afterGrant =
                () -> {
                    try {
                        ret[0] = cancel(slot).getRetcode();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, cancel(slot).getRetcode());
        assertNotEquals(0, ret[0]);
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void acknowledgedWriteFailureCannotReplayRefund(int stage) throws Exception {
        var slot = start();
        failSave = stage;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertNotNull(pending());
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
        int wood = player.inventory.getItemCountById(101311),
                other = player.inventory.getItemCountById(101312);
        failSave = 0;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(wood, player.inventory.getItemCountById(101311));
        assertEquals(other, player.inventory.getItemCountById(101312));
    }

    @Test
    void interruptedRefundBlocksStartingAndTakingJobs() throws Exception {
        var slot = start();
        failSave = 3;
        assertNotEquals(0, cancel(slot).getRetcode());
        failSave = 0;
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().startMake(38, 0);
        var start =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, FurnitureMakeStartRsp.parseFrom(start.getData()).getRetcode());
        slot.setBeginTime(Utils.getCurrentSeconds() - 7200);
        session.responses.clear();
        player.getFurnitureManager().take(slot.getIndex(), 38, false);
        var take =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, TakeFurnitureMakeRsp.parseFrom(take.getData()).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void pendingRefundAndOriginalCostsSurviveMongoReload() throws Exception {
        var slot = start();
        failSave = 3;
        assertNotEquals(0, cancel(slot).getRetcode());
        assertNotNull(pending());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture176_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                player.home.setPlayer(player);
                failSave = 0;
                assertNotNull(pending());
                var receipt = pending();
                assertEquals(
                        2,
                        ((List<?>) receipt.getClass().getMethod("getRefunds").invoke(receipt))
                                .size());
                assertNotEquals(
                        0, cancel(player.home.getFurnitureMakeSlotItemList().get(0)).getRetcode());
                assertEquals(40, player.inventory.getItemCountById(101311));
                assertEquals(18, player.inventory.getItemCountById(101312));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture176_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void nativeRequestHandlerRefundsTheRequestedSlot() throws Exception {
        var slot = start();
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.recv.HandlerFurnitureMakeCancelReq");
        } catch (ClassNotFoundException absent) {
        }
        assertNotNull(type);
        var handler =
                (emu.grasscutter.net.packet.PacketHandler) type.getConstructor().newInstance();
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        handler.handle(
                session,
                new byte[0],
                FurnitureMakeCancelReq.newBuilder()
                        .setIndex(slot.getIndex())
                        .setMakeId(38)
                        .build()
                        .toByteArray());
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeCancelRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, FurnitureMakeCancelRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @Test
    void malformedNativeRequestDoesNotRefund() throws Exception {
        start();
        Class<?> type = null;
        try {
            type =
                    Class.forName(
                            "emu.grasscutter.server.packet.recv.HandlerFurnitureMakeCancelReq");
        } catch (ClassNotFoundException absent) {
        }
        assertNotNull(type);
        var handler =
                (emu.grasscutter.net.packet.PacketHandler) type.getConstructor().newInstance();
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        handler.handle(session, new byte[0], new byte[] {0x58, (byte) 0x80});
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeCancelRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, FurnitureMakeCancelRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(36, player.inventory.getItemCountById(101311));
    }

    @Test
    void fullyConsumedMaterialsCanBeRestoredAsNewStacks() throws Exception {
        recipe(40, 20);
        var slot = start();
        assertEquals(0, player.inventory.getItemCountById(101311));
        assertEquals(0, player.inventory.getItemCountById(101312));
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
    }

    @Test
    void freeJobCanBeCancelledWithoutInventingMaterials() throws Exception {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[]}",
                                FurnitureMakeConfigData.class));
        var slot = start();
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(0, player.grants);
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void refundHintContainsRefundAmountsNotFinalStackCounts() throws Exception {
        var slot = start();
        player.packets.clear();
        assertEquals(0, cancel(slot).getRetcode());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ItemAddHintNotify)
                        .findFirst()
                        .orElseThrow();
        var hint =
                emu.grasscutter.net.proto.ItemAddHintNotifyOuterClass.ItemAddHintNotify.parseFrom(
                        packet.getData());
        assertEquals(1088, hint.getReason());
        var totals = new HashMap<Integer, Integer>();
        hint.getItemListList().forEach(i -> totals.put(i.getItemId(), i.getCount()));
        assertEquals(Map.of(101311, 4, 101312, 2), totals);
    }

    @Test
    void invalidRefundTypeDoesNotGrantOrRemoveTheJob() throws Exception {
        var slot = start();
        var original = player.inventory.getItemById(101311);
        item(101311, "ITEM_FURNITURE", 9999);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(36, original.getCount());
        assertEquals(0, player.grants);
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
    }

    @Test
    void foreignRefundStackCannotReceiveMaterials() throws Exception {
        var slot = start();
        var original = player.inventory.getItemById(101311);
        var field = GameItem.class.getDeclaredField("ownerId");
        field.setAccessible(true);
        field.setInt(original, 9);
        assertNotEquals(0, cancel(slot).getRetcode());
        assertEquals(36, original.getCount());
        assertEquals(0, player.grants);
    }

    @Test
    void nullRemainingJobIsRejectedBeforeAnyRefund() throws Exception {
        var slot = start();
        player.home.getFurnitureMakeSlotItemList().add(null);
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertEquals(0, player.grants);
    }

    @Test
    void duplicateOtherIndexesAreRejectedBeforeAnyRefund() throws Exception {
        var slot = start();
        var other =
                FurnitureMakeSlotItem.of()
                        .index(9)
                        .makeId(39)
                        .beginTime(Utils.getCurrentSeconds())
                        .durTime(3600)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(other);
        player.home.getFurnitureMakeSlotItemList().add(other);
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertEquals(0, player.grants);
    }

    @Test
    void negativeRemainingIndexCannotReachTheSuccessSnapshot() throws Exception {
        var slot = start();
        player.home
                .getFurnitureMakeSlotItemList()
                .add(
                        FurnitureMakeSlotItem.of()
                                .index(-1)
                                .makeId(39)
                                .beginTime(Utils.getCurrentSeconds())
                                .durTime(3600)
                                .build());
        assertNotEquals(0, cancel(slot).getRetcode());
        intact(slot);
        assertEquals(0, player.grants);
    }

    @Test
    void everyValidCurrentRecipeRefundsItsPaidMaterialAmounts() throws Exception {
        var root =
                java.nio.file.Path.of(
                        System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"),
                        "ExcelBinOutput");
        for (String table : List.of("Material", "HomeWorldFurniture"))
            for (var data :
                    gson.fromJson(
                            java.nio.file.Files.readString(
                                    root.resolve(table + "ExcelConfigData.json")),
                            ItemData[].class)) {
                data.onLoad();
                GameData.getItemDataMap().put(data.getId(), data);
            }
        var recipes =
                gson.fromJson(
                        java.nio.file.Files.readString(
                                root.resolve("FurnitureMakeExcelConfigData.json")),
                        FurnitureMakeConfigData[].class);
        int verified = 0;
        for (var data : recipes) {
            var product = GameData.getItemDataMap().get(data.getFurnitureItemID());
            if (product.getStackLimit() == 0) continue;
            data.onLoad();
            GameData.getFurnitureMakeConfigDataMap().put(data.getId(), data);
            player.getUnlockedFurniture().add(product.getId());
            player.refund = false;
            Map<Integer, Integer> before = new HashMap<>();
            for (var cost : data.getMaterialItems()) {
                int count = player.inventory.getItemCountById(cost.getId());
                if (count < cost.getCount() + 1)
                    assertTrue(player.inventory.addItem(cost.getId(), cost.getCount() + 1 - count));
                before.put(cost.getId(), player.inventory.getItemCountById(cost.getId()));
            }
            var session = (InvestigationRewardTest.ClaimSession) player.getSession();
            session.responses.clear();
            player.getFurnitureManager().startMake(data.getId(), 0);
            var response =
                    session.responses.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    0,
                    FurnitureMakeStartRsp.parseFrom(response.getData()).getRetcode(),
                    "recipe " + data.getId());
            var slot = player.home.getFurnitureMakeSlotItemList().get(0);
            player.refund = true;
            assertEquals(0, cancel(slot).getRetcode(), "recipe " + data.getId());
            for (var cost : data.getMaterialItems())
                assertEquals(
                        before.get(cost.getId()), player.inventory.getItemCountById(cost.getId()));
            assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
            verified++;
        }
        assertEquals(1396, verified);
    }
}
