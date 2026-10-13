package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.FurnitureMakeTakeInfoOuterClass.FurnitureMakeTakeInfo;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllRspOuterClass.TakeFurnitureMakeAllRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurnitureAccelerationFlowTest {
    final Gson gson = new Gson();
    final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    AbilityTestFixture environment;
    FurnitureTakeFlowTest.CraftPlayer player;
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
        player = new FurnitureTakeFlowTest.CraftPlayer();
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
        for (var data :
                gson.fromJson(
                        "[{id:360101,itemType:'ITEM_FURNITURE',stackLimit:100},{id:107013,itemType:'ITEM_MATERIAL',stackLimit:100}]",
                        ItemData[].class)) {
            data.onLoad();
            GameData.getItemDataMap().put(data.getId(), data);
        }
        var recipe =
                gson.fromJson(
                        "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[]}",
                        FurnitureMakeConfigData.class);
        recipe.onLoad();
        GameData.getFurnitureMakeConfigDataMap().put(38, recipe);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) {
                                if (args.length == 2) {
                                    saves++;
                                    if (player.home.getPendingFurnitureTake() != null
                                            && reserved == null)
                                        reserved =
                                                gson.toJsonTree(
                                                                player.home
                                                                        .getPendingFurnitureTake())
                                                        .getAsJsonObject();
                                    if (saves == failSave)
                                        throw new IllegalStateException("Write fault");
                                }
                                return args[0];
                            }
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
                            return null;
                        }));
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, oldStore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    FurnitureMakeSlotItem job(int index, int acceleration, int elapsed, boolean plan) {
        var row = new JsonObject();
        row.addProperty("index", index);
        row.addProperty("makeId", 38);
        row.addProperty("beginTime", Utils.getCurrentSeconds() - elapsed);
        row.addProperty("durTime", 3600);
        row.addProperty("accelerateTime", acceleration);
        if (plan)
            row.add(
                    "plan",
                    JsonParser.parseString(
                            "{format:1,makeId:38,itemId:360101,count:1,makeTime:3600,trustExp:60,materials:[]}"));
        var slot = gson.fromJson(row, FurnitureMakeSlotItem.class);
        player.home.getFurnitureMakeSlotItemList().add(slot);
        return slot;
    }

    int accelerated(FurnitureMakeSlotItem slot) {
        return gson.toJsonTree(slot).getAsJsonObject().has("accelerateTime")
                ? gson.toJsonTree(slot).getAsJsonObject().get("accelerateTime").getAsInt()
                : 0;
    }

    TakeFurnitureMakeRsp take(int index, boolean fast) throws Exception {
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        try {
            player.getFurnitureManager().take(index, 38, fast);
        } finally {
            player.setSession(old);
        }
        var replies =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .toList();
        assertEquals(1, replies.size());
        return TakeFurnitureMakeRsp.parseFrom(replies.get(0).getData());
    }

    FurnitureMakeCancelRsp cancel() throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().cancel(0, 38);
        var replies =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeCancelRsp)
                        .toList();
        assertEquals(1, replies.size());
        return FurnitureMakeCancelRsp.parseFrom(replies.get(0).getData());
    }

    TakeFurnitureMakeAllRsp bulk(boolean fast) throws Exception {
        var choices =
                player.home.getFurnitureMakeSlotItemList().stream()
                        .map(
                                s ->
                                        FurnitureMakeTakeInfo.newBuilder()
                                                .setIndex(s.getIndex())
                                                .setMakeId(38)
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
        var replies =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeAllRsp)
                        .toList();
        assertEquals(1, replies.size());
        return TakeFurnitureMakeAllRsp.parseFrom(replies.get(0).getData());
    }

    @Test
    void assistedTaskStoresAndSerializesNativeFieldFour() {
        var slot = job(0, 14400, 0, true);
        assertEquals(14400, accelerated(slot));
        assertEquals(14400, slot.toProto().getAccelerateTime());
    }

    @Test
    void statusReturnsSavedAccelerationUnchanged() throws Exception {
        job(0, 1200, 0, true);
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        player.getFurnitureManager().queryStatus();
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                        .findFirst()
                        .orElseThrow();
        var rsp = FurnitureMakeRsp.parseFrom(packet.getData());
        assertEquals(
                1200, rsp.getFurnitureMakeSlot().getFurnitureMakeDataList(0).getAccelerateTime());
    }

    @Test
    void assistedFinishedPlanCanBeClaimedWithoutVial() throws Exception {
        job(0, 2400, 1201, true);
        assertEquals(0, take(0, false).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(1, player.missions);
    }

    @Test
    void assistedLegacyTaskCanBeClaimedWithoutVial() throws Exception {
        job(0, 2400, 1201, false);
        assertEquals(0, take(0, false).getRetcode());
    }

    @Test
    void exactAcceleratedBoundaryIsClaimable() throws Exception {
        job(0, 2400, 1200, true);
        assertEquals(0, take(0, false).getRetcode());
    }

    @Test
    void accelerationBeyondDurationFinishesShortTask() throws Exception {
        job(0, 14400, 0, true);
        assertEquals(0, take(0, false).getRetcode());
    }

    @Test
    void staleFastFlagOnAssistedFinishedTaskSpendsNoVial() throws Exception {
        job(0, 2400, 1201, true);
        player.inventory.addItem(107013, 2);
        assertEquals(0, take(0, true).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(107013));
    }

    @Test
    void partiallyAssistedTaskIsStillUnfinished() throws Exception {
        var slot = job(0, 1200, 0, true);
        assertNotEquals(0, take(0, false).getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
        assertEquals(0, player.grants);
    }

    @Test
    void unfinishedAssistedTaskCanUseOneVial() throws Exception {
        job(0, 1200, 0, true);
        player.inventory.addItem(107013, 2);
        assertEquals(0, take(0, true).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(107013));
    }

    @Test
    void alreadyFinishedAssistedTaskCannotBeCancelled() throws Exception {
        job(0, 2400, 1201, true);
        assertNotEquals(0, cancel().getRetcode());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertEquals(0, saves);
    }

    @Test
    void partiallyAssistedTaskMayBeCancelled() throws Exception {
        job(0, 1200, 0, true);
        assertEquals(0, cancel().getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void bulkAssistedTasksAreClaimedWithoutVials() throws Exception {
        job(0, 2400, 1201, true);
        job(1, 3600, 0, true);
        assertEquals(0, bulk(false).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
        assertEquals(2, player.missions);
    }

    @Test
    void bulkMixedAccelerationPaysOnlyUnfinishedJob() throws Exception {
        job(0, 3600, 0, true);
        job(1, 1200, 0, true);
        player.inventory.addItem(107013, 1);
        assertEquals(0, bulk(true).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(107013));
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk", "status"})
    void negativeAccelerationRejectsCorruptTask(String operation) throws Exception {
        job(0, -1, 7200, true);
        int ret;
        if (operation.equals("take")) ret = take(0, false).getRetcode();
        else if (operation.equals("cancel")) ret = cancel().getRetcode();
        else if (operation.equals("bulk")) ret = bulk(false).getRetcode();
        else {
            var session = (InvestigationRewardTest.ClaimSession) player.getSession();
            player.getFurnitureManager().queryStatus();
            var p =
                    player.packets.stream()
                            .filter(v -> v.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                            .findFirst()
                            .orElseThrow();
            ret = FurnitureMakeRsp.parseFrom(p.getData()).getRetcode();
        }
        assertNotEquals(0, ret);
        assertEquals(0, player.grants);
        assertEquals(0, saves);
    }

    @Test
    void ordinaryOldTaskDefaultsToZeroAcceleration() {
        var slot =
                gson.fromJson(
                        "{index:0,makeId:38,beginTime:1,durTime:3600}",
                        FurnitureMakeSlotItem.class);
        assertEquals(0, slot.toProto().getAccelerateTime());
    }

    @Test
    void singleReceiptCapturesAccelerationBeforeGrant() throws Exception {
        job(0, 2400, 1201, true);
        failSave = 1;
        assertNotEquals(0, take(0, false).getRetcode());
        assertNotNull(reserved);
        assertTrue(reserved.has("accelerateTime"));
        assertEquals(2400, reserved.get("accelerateTime").getAsInt());
    }

    @Test
    void laterBulkAccelerationChangedDuringGrantIsNotSilentlyClaimed() throws Exception {
        job(0, 3600, 0, true);
        var later = job(1, 3600, 0, true);
        player.afterGrant =
                () -> {
                    var row = gson.toJsonTree(later).getAsJsonObject();
                    row.addProperty("accelerateTime", 0);
                    player.home
                            .getFurnitureMakeSlotItemList()
                            .set(1, gson.fromJson(row, FurnitureMakeSlotItem.class));
                };
        assertNotEquals(0, bulk(false).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertNotNull(player.home.getPendingFurnitureBulk());
    }

    @Test
    void acceleratedTaskAndReceiptSurviveMongo() throws Exception {
        job(0, 2400, 1201, true);
        failSave = 1;
        assertNotEquals(0, take(0, false).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture187_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                var home = store.find(GameHome.class).first();
                assertNotNull(home);
                assertEquals(
                        2400,
                        home.getFurnitureMakeSlotItemList().get(0).toProto().getAccelerateTime());
                var receipt = gson.toJsonTree(home.getPendingFurnitureTake()).getAsJsonObject();
                assertTrue(receipt.has("accelerateTime"));
                assertEquals(2400, receipt.get("accelerateTime").getAsInt());
                player.home = home;
                home.setPlayer(player);
                failSave = 0;
                assertNotEquals(0, take(0, false).getRetcode());
                assertEquals(0, player.grants);
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture187_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
