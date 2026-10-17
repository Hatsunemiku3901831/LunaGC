package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeTakeInfoOuterClass.FurnitureMakeTakeInfo;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllRspOuterClass.TakeFurnitureMakeAllRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class FurnitureReturnFlowTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;

    static class ReturnPlayer extends InvestigationRewardTest.ClaimPlayer {
        transient GameHome home;
        transient int rejectId, pretendId, partialId, attempts, missions, materialCapacity = 100;
        transient Runnable afterProduct;
        final transient Inventory inventory =
                new Inventory(this) {
                    @Override
                    public InventoryTab getInventoryTab(ItemType type) {
                        var real = super.getInventoryTab(type);
                        if (type != ItemType.ITEM_MATERIAL || real == null) return real;
                        return new InventoryTab() {
                            public GameItem getItemById(int id) {
                                return real.getItemById(id);
                            }

                            public int getItemCountById(int id) {
                                return real.getItemCountById(id);
                            }

                            public int getSize() {
                                return real.getSize();
                            }

                            public int getMaxCapacity() {
                                return materialCapacity;
                            }

                            public void onAddItem(GameItem item) {
                                real.onAddItem(item);
                            }

                            public void onRemoveItem(GameItem item) {
                                real.onRemoveItem(item);
                            }
                        };
                    }

                    @Override
                    public boolean addItem(GameItem item, boolean skip, boolean notify) {
                        attempts++;
                        if (item.getItemId() == rejectId) return false;
                        if (item.getItemId() == pretendId) return true;
                        if (item.getItemId() == partialId) item.setCount(1);
                        boolean result = super.addItem(item, skip, notify);
                        if (item.getItemType() == ItemType.ITEM_FURNITURE && afterProduct != null) {
                            var hook = afterProduct;
                            afterProduct = null;
                            hook.run();
                        }
                        return result;
                    }
                };
        final transient BattlePassManager bp =
                new BattlePassManager() {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int amount) {
                        if (type == WatcherTriggerType.TRIGGER_FURNITURE_MAKE) missions += amount;
                    }
                };

        public Inventory getInventory() {
            return inventory;
        }

        public GameHome getHome() {
            return home;
        }

        public BattlePassManager getBattlePassManager() {
            return bp;
        }
    }

    final Gson gson = new Gson();
    final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    AbilityTestFixture environment;
    ReturnPlayer player;
    Field datastore;
    Object oldStore;
    int saves, failSave;
    JsonObject reserved;

    @SuppressWarnings({"rawtypes", "unchecked"})
    void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new ReturnPlayer();
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
        item(360101, "ITEM_FURNITURE", 100);
        item(101311, "ITEM_MATERIAL", 100);
        item(101312, "ITEM_MATERIAL", 100);
        item(107013, "ITEM_MATERIAL", 100);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
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
                            if (method.getName().equals("save")) {
                                if (args.length == 2) {
                                    saves++;
                                    if (args[0] == player.home
                                            && reserved == null
                                            && player.home.getPendingFurnitureTake() != null)
                                        reserved =
                                                gson.toJsonTree(
                                                                player.home
                                                                        .getPendingFurnitureTake())
                                                        .getAsJsonObject();
                                    if (saves == failSave)
                                        throw new IllegalStateException("Write fault");
                                }
                                return method.invoke(playerStore.datastore(), args);
                            }
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
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
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (playerStore != null) playerStore.close();
        datastore.set(null, oldStore);
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

    FurnitureMakeSlotItem job(int index, boolean finished) {
        var plan =
                gson.fromJson(
                        "{format:2,makeId:38,itemId:360101,count:1,makeTime:3600,trustExp:60,materials:[{id:101311,count:8},{id:101312,count:4}],returns:[{id:101311,count:2},{id:101312,count:1}]}",
                        FurnitureMakePlan.class);
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(index)
                        .makeId(38)
                        .beginTime(Utils.getCurrentSeconds() - (finished ? 7200 : 0))
                        .durTime(3600)
                        .plan(plan)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        return slot;
    }

    TakeFurnitureMakeRsp take(boolean fast) throws Exception {
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        try {
            player.getFurnitureManager().take(0, 38, fast);
        } finally {
            player.setSession(old);
        }
        var rows =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .toList();
        assertEquals(1, rows.size());
        return TakeFurnitureMakeRsp.parseFrom(rows.get(0).getData());
    }

    TakeFurnitureMakeAllRsp bulk(boolean fast) throws Exception {
        var choices =
                player.home.getFurnitureMakeSlotItemList().stream()
                        .map(
                                s ->
                                        FurnitureMakeTakeInfo.newBuilder()
                                                .setIndex(s.getIndex())
                                                .setMakeId(s.getMakeId())
                                                .setIsFastFinish(fast)
                                                .build())
                        .toList();
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        try {
            player.getFurnitureManager().takeAll(choices);
        } finally {
            player.setSession(old);
        }
        assertTrue(
                session.responses.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp));
        var rows =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeAllRsp)
                        .toList();
        assertEquals(1, rows.size());
        return TakeFurnitureMakeAllRsp.parseFrom(rows.get(0).getData());
    }

    Map<Integer, Integer> outputs(
            List<emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam> rows) {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (var row : rows) assertNull(values.put(row.getItemId(), row.getCount()));
        return values;
    }

    void noGrant() {
        assertEquals(0, player.attempts);
        assertEquals(0, player.missions);
        assertEquals(0, player.home.getExp());
        assertEquals(0, saves);
    }

    void assertReturns(int factor) {
        assertEquals(2 * factor, player.inventory.getItemCountById(101311));
        assertEquals(factor, player.inventory.getItemCountById(101312));
    }

    @Test
    void finishedSingleDeliversFurnitureAndAllReturnsOnce() throws Exception {
        job(0, true);
        var response = take(false);
        assertEquals(0, response.getRetcode());
        assertReturns(1);
        assertEquals(
                Map.of(360101, 1, 101311, 2, 101312, 1), outputs(response.getOutputItemListList()));
        assertEquals(60, player.home.getExp());
        assertEquals(1, player.missions);
        assertNull(player.home.getPendingFurnitureTake());
        assertNotEquals(0, take(false).getRetcode());
        assertReturns(1);
    }

    @Test
    void returnsAreSavedBeforeAnyGrant() throws Exception {
        job(0, true);
        failSave = 1;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(reserved);
        assertTrue(reserved.has("returns"));
        assertEquals(2, reserved.getAsJsonArray("returns").size());
        assertEquals(0, player.attempts);
    }

    @Test
    void completedJobDoesNotDependOnCurrentRecipeOrAssistant() throws Exception {
        job(0, true);
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, take(false).getRetcode());
        assertReturns(1);
    }

    @Test
    void returnsMergeWithOwnedStacks() throws Exception {
        player.inventory.addItem(101311, 3);
        player.inventory.addItem(101312, 2);
        player.attempts = 0;
        job(0, true);
        assertEquals(0, take(false).getRetcode());
        assertEquals(5, player.inventory.getItemCountById(101311));
        assertEquals(3, player.inventory.getItemCountById(101312));
    }

    @Test
    void fastClaimPaysOneVialAndReturnsMaterials() throws Exception {
        player.inventory.addItem(107013, 2);
        job(0, false);
        assertEquals(0, take(true).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(107013));
        assertReturns(1);
    }

    @Test
    void zeroDurationCapturedJobReturnsMaterialsWithoutVials() throws Exception {
        var slot = job(0, false);
        slot.setDurTime(0);
        var json = gson.toJsonTree(slot.getPlan()).getAsJsonObject();
        json.addProperty("makeTime", 0);
        slot.setPlan(gson.fromJson(json, FurnitureMakePlan.class));
        assertEquals(0, take(false).getRetcode());
        assertReturns(1);
    }

    @Test
    void legacyPlanWithoutReturnsRemainsClaimable() throws Exception {
        var slot = job(0, true);
        var json = gson.toJsonTree(slot.getPlan()).getAsJsonObject();
        json.addProperty("format", 1);
        json.remove("returns");
        slot.setPlan(gson.fromJson(json, FurnitureMakePlan.class));
        assertEquals(0, take(false).getRetcode());
        assertReturns(0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "absent",
                "wrongKey",
                "furniture",
                "virtual",
                "useOnGain",
                "zeroLimit",
                "foreign",
                "limit"
            })
    void badReturnDefinitionRefusesBeforeProductOrVial(String kind) throws Exception {
        job(0, false);
        player.inventory.addItem(107013, 2);
        player.attempts = 0;
        if (kind.equals("absent")) GameData.getItemDataMap().remove(101311);
        if (kind.equals("wrongKey"))
            GameData.getItemDataMap().put(101311, GameData.getItemDataMap().get(101312));
        if (kind.equals("furniture")) item(101311, "ITEM_FURNITURE", 100);
        if (kind.equals("virtual")) item(101311, "ITEM_VIRTUAL", 100);
        if (kind.equals("useOnGain")) {
            var json = gson.toJsonTree(GameData.getItemDataMap().get(101311)).getAsJsonObject();
            json.addProperty("useOnGain", true);
            var data = gson.fromJson(json, ItemData.class);
            data.onLoad();
            GameData.getItemDataMap().put(101311, data);
        }
        if (kind.equals("zeroLimit")) item(101311, "ITEM_MATERIAL", 0);
        if (kind.equals("foreign")) {
            player.inventory.addItem(101311, 1);
            var owner = GameItem.class.getDeclaredField("ownerId");
            owner.setAccessible(true);
            owner.setInt(player.inventory.getItemById(101311), 10002);
            player.attempts = 0;
        }
        if (kind.equals("limit")) item(101311, "ITEM_MATERIAL", 1);
        assertNotEquals(0, take(true).getRetcode());
        noGrant();
        assertEquals(2, player.inventory.getItemCountById(107013));
        assertNull(player.home.getPendingFurnitureTake());
    }

    @Test
    void materialTabCapacityIncludesEveryReturnedStack() throws Exception {
        job(0, true);
        player.materialCapacity = 1;
        assertNotEquals(0, take(false).getRetcode());
        noGrant();
    }

    @Test
    void lastVialCanFreeCapacityForReturns() throws Exception {
        job(0, false);
        player.inventory.addItem(107013, 1);
        player.attempts = 0;
        player.materialCapacity = 2;
        assertEquals(0, take(true).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(107013));
        assertReturns(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"refused", "pretend", "partial"})
    void unconfirmedReturnKeepsSourceAndPreventsProgressOrReplay(String kind) throws Exception {
        job(0, true);
        if (kind.equals("refused")) player.rejectId = 101311;
        if (kind.equals("pretend")) player.pretendId = 101311;
        if (kind.equals("partial")) player.partialId = 101311;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(player.home.getPendingFurnitureTake());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(0, player.missions);
        assertEquals(0, player.home.getExp());
        assertTrue(player.home.getCraftedFurnitureIds().isEmpty());
        int attempts = player.attempts;
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(attempts, player.attempts);
    }

    @Test
    void secondReturnFailureRecordsFirstConfirmedReturn() throws Exception {
        job(0, true);
        player.rejectId = 101312;
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(101311));
        var json = gson.toJsonTree(player.home.getPendingFurnitureTake()).getAsJsonObject();
        assertTrue(json.has("returns"));
        var rows = json.getAsJsonArray("returns");
        assertTrue(rows.get(0).getAsJsonObject().get("delivered").getAsBoolean());
        assertFalse(rows.get(1).getAsJsonObject().get("delivered").getAsBoolean());
        assertEquals(0, player.missions);
        assertEquals(0, player.home.getExp());
    }

    @Test
    void returnReservationCannotBeChangedByProductDeliveryHook() throws Exception {
        var slot = job(0, true);
        player.afterProduct =
                () -> {
                    var json = gson.toJsonTree(slot.getPlan()).getAsJsonObject();
                    json.getAsJsonArray("returns").get(0).getAsJsonObject().addProperty("count", 8);
                    slot.setPlan(gson.fromJson(json, FurnitureMakePlan.class));
                };
        assertEquals(0, take(false).getRetcode());
        assertReturns(1);
    }

    @Test
    void reentryDuringFurnitureGrantCannotClaimReturnsTwice() throws Exception {
        job(0, true);
        player.afterProduct =
                () -> assertDoesNotThrow(() -> assertNotEquals(0, take(false).getRetcode()));
        assertEquals(0, take(false).getRetcode());
        assertReturns(1);
        assertEquals(1, player.missions);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void everySingleAcknowledgedWriteFaultBlocksReplay(int stage) throws Exception {
        job(0, true);
        failSave = stage;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(player.home.getPendingFurnitureTake());
        int attempts = player.attempts,
                m1 = player.inventory.getItemCountById(101311),
                m2 = player.inventory.getItemCountById(101312),
                missions = player.missions;
        failSave = 0;
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(attempts, player.attempts);
        assertEquals(m1, player.inventory.getItemCountById(101311));
        assertEquals(m2, player.inventory.getItemCountById(101312));
        assertEquals(missions, player.missions);
    }

    @Test
    void twoJobsReturnSharedMaterialsAndReportConfirmedTotals() throws Exception {
        job(0, true);
        job(1, true);
        var reply = bulk(false);
        assertEquals(0, reply.getRetcode());
        assertReturns(2);
        assertEquals(
                Map.of(360101, 2, 101311, 4, 101312, 2), outputs(reply.getOutputItemListList()));
        assertEquals(2, player.missions);
        assertEquals(60, player.home.getExp());
        assertNull(player.home.getPendingFurnitureBulk());
    }

    @Test
    void bulkPreflightsCombinedReturnStackBeforeFirstProduct() throws Exception {
        job(0, true);
        job(1, true);
        item(101311, "ITEM_MATERIAL", 3);
        assertNotEquals(0, bulk(false).getRetcode());
        noGrant();
        assertNull(player.home.getPendingFurnitureBulk());
    }

    @Test
    void bulkPreflightsDifferentRefundStacksBeforeFirstProduct() throws Exception {
        job(0, true);
        var slot = job(1, true);
        item(101313, "ITEM_MATERIAL", 100);
        slot.setPlan(
                gson.fromJson(
                        "{format:2,makeId:38,itemId:360101,count:1,makeTime:3600,trustExp:60,materials:[{id:101313,count:8}],returns:[{id:101313,count:2}]}",
                        FurnitureMakePlan.class));
        player.materialCapacity = 2;
        assertNotEquals(0, bulk(false).getRetcode());
        noGrant();
    }

    @Test
    void bulkFreeingVialLateDoesNotHideEarlierCapacityFailure() throws Exception {
        job(0, true);
        job(1, false);
        player.inventory.addItem(107013, 1);
        player.attempts = 0;
        player.materialCapacity = 2;
        assertNotEquals(0, bulk(true).getRetcode());
        noGrant();
        assertEquals(1, player.inventory.getItemCountById(107013));
    }

    @Test
    void bulkCanFreeItsLastVialBeforeFirstReturn() throws Exception {
        job(0, false);
        job(1, true);
        player.inventory.addItem(107013, 1);
        player.attempts = 0;
        player.materialCapacity = 2;
        assertEquals(0, bulk(true).getRetcode());
        assertReturns(2);
    }

    @Test
    void bulkFreezesLaterReturnPlanBeforeAnyGrant() throws Exception {
        job(0, true);
        var later = job(1, true);
        player.afterProduct =
                () -> {
                    var json = gson.toJsonTree(later.getPlan()).getAsJsonObject();
                    json.getAsJsonArray("returns").get(0).getAsJsonObject().addProperty("count", 8);
                    later.setPlan(gson.fromJson(json, FurnitureMakePlan.class));
                };
        assertEquals(0, bulk(false).getRetcode());
        assertReturns(2);
    }

    @Test
    void bulkPartialReturnFailureKeepsBothJournalsAndNoProgress() throws Exception {
        job(0, true);
        job(1, true);
        player.rejectId = 101312;
        assertNotEquals(0, bulk(false).getRetcode());
        assertNotNull(player.home.getPendingFurnitureBulk());
        assertNotNull(player.home.getPendingFurnitureTake());
        assertEquals(0, player.missions);
        assertEquals(0, player.home.getExp());
        int attempts = player.attempts;
        assertNotEquals(0, bulk(false).getRetcode());
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(attempts, player.attempts);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27
            })
    void everyBulkAcknowledgedWriteFaultBlocksReplay(int stage) throws Exception {
        job(0, true);
        job(1, true);
        failSave = stage;
        assertNotEquals(0, bulk(false).getRetcode());
        assertNotNull(player.home.getPendingFurnitureBulk());
        int attempts = player.attempts,
                m1 = player.inventory.getItemCountById(101311),
                m2 = player.inventory.getItemCountById(101312);
        failSave = 0;
        assertNotEquals(0, bulk(false).getRetcode());
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(attempts, player.attempts);
        assertEquals(m1, player.inventory.getItemCountById(101311));
        assertEquals(m2, player.inventory.getItemCountById(101312));
    }

    @Test
    void confirmedAndOutstandingReturnsSurviveMongoAndBlockRetry() throws Exception {
        job(0, true);
        player.rejectId = 101312;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(player.home.getPendingFurnitureTake());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture185_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                var restored = store.find(GameHome.class).first();
                assertNotNull(restored);
                player.home = restored;
                restored.setPlayer(player);
                var json = gson.toJsonTree(restored.getPendingFurnitureTake()).getAsJsonObject();
                assertTrue(json.has("returns"));
                assertEquals(2, json.getAsJsonArray("returns").size());
                assertTrue(
                        json.getAsJsonArray("returns")
                                .get(0)
                                .getAsJsonObject()
                                .get("delivered")
                                .getAsBoolean());
                int attempts = player.attempts;
                assertNotEquals(0, take(false).getRetcode());
                assertEquals(attempts, player.attempts);
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture185_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void actualTalentStartCostsAndBulkClaimReturnsAreConnected() throws Exception {
        clear(GameData.getProudSkillDataMap());
        clear(GameData.getAvatarSkillDepotDataMap());
        clear(GameData.getFurnitureTypeDataMap());
        var root = Path.of("/resources/ExcelBinOutput");
        var skill =
                Arrays.stream(
                                gson.fromJson(
                                        Files.readString(
                                                root.resolve("ProudSkillExcelConfigData.json")),
                                        ProudSkillData[].class))
                        .filter(value -> value.getId() == 492301)
                        .findFirst()
                        .orElseThrow();
        skill.onLoad();
        GameData.getProudSkillDataMap().put(skill.getId(), skill);
        var depot =
                Arrays.stream(
                                gson.fromJson(
                                        Files.readString(
                                                root.resolve(
                                                        "AvatarSkillDepotExcelConfigData.json")),
                                        AvatarSkillDepotData[].class))
                        .filter(value -> value.getId() == 4901)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        GameData.getAvatarSkillDepotDataMap().put(depot.getId(), depot);
        for (var type :
                gson.fromJson(
                        Files.readString(
                                root.resolve("HomeWorldFurnitureTypeExcelConfigData.json")),
                        FurnitureTypeData[].class))
            GameData.getFurnitureTypeDataMap().put(type.getId(), type);
        var row = gson.toJsonTree(GameData.getItemDataMap().get(360101)).getAsJsonObject();
        row.add("furnType", gson.toJsonTree(List.of(1001)));
        var product = gson.fromJson(row, ItemData.class);
        product.onLoad();
        GameData.getItemDataMap().put(360101, product);
        var recipe =
                gson.fromJson(
                        "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[{id:101311,count:8},{id:101312,count:4}]}",
                        FurnitureMakeConfigData.class);
        recipe.onLoad();
        GameData.getFurnitureMakeConfigDataMap().put(38, recipe);
        player.getUnlockedFurniture().add(360101);
        var assistant = new SkillTalentLevelTest.RecordingAvatar(player, 10000049, depot);
        assertTrue(player.getAvatars().addAvatar(assistant));
        player.inventory.addItem(101311, 16);
        player.inventory.addItem(101312, 8);
        for (int i = 0; i < 2; i++) player.getFurnitureManager().startMake(38, 10000049);
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(0, player.inventory.getItemCountById(101311));
        assertEquals(0, player.inventory.getItemCountById(101312));
        player.home.getFurnitureMakeSlotItemList().forEach(slot -> slot.setBeginTime(1));
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, bulk(false).getRetcode());
        assertReturns(2);
        assertEquals(2, player.inventory.getItemCountById(360101));
        assertEquals(2, player.missions);
    }

    @Test
    void bulkParentAndPartialReturnJournalSurviveMongo() throws Exception {
        job(0, true);
        job(1, true);
        player.rejectId = 101312;
        assertNotEquals(0, bulk(false).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name =
                "lunagc_test_furniture185_bulk_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                var restored = store.find(GameHome.class).first();
                assertNotNull(restored);
                player.home = restored;
                restored.setPlayer(player);
                var parent = gson.toJsonTree(restored.getPendingFurnitureBulk()).getAsJsonObject();
                assertEquals(2, parent.getAsJsonArray("entries").size());
                var entry = parent.getAsJsonArray("entries").get(1).getAsJsonObject();
                assertTrue(entry.has("returns"));
                assertEquals(2, entry.getAsJsonArray("returns").size());
                assertNotNull(restored.getPendingFurnitureTake());
                int attempts = player.attempts;
                assertNotEquals(0, bulk(false).getRetcode());
                assertNotEquals(0, take(false).getRetcode());
                assertEquals(attempts, player.attempts);
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture185_bulk_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
