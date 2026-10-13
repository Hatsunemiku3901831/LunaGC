package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp;
import emu.grasscutter.net.proto.FurnitureMakeTakeInfoOuterClass.FurnitureMakeTakeInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllRspOuterClass.TakeFurnitureMakeAllRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

class FurnitureDurationTest {
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private FurnitureTakeFlowTest.CraftPlayer player;
    private AbilityTestFixture environment;
    private Field datastore;
    private Object oldStore;
    private int writes;

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
                        .level(10)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        clear(GameData.getItemDataMap());
        clear(GameData.getFurnitureMakeConfigDataMap());
        clear(GameData.getHomeWorldLevelDataMap());
        item(360101, "ITEM_FURNITURE");
        item(101311, "ITEM_MATERIAL");
        item(107013, "ITEM_MATERIAL");
        recipe(57600);
        player.getUnlockedFurniture().add(360101);
        for (var row : gson.fromJson(fixture("7.1"), HomeWorldLevelData[].class))
            GameData.getHomeWorldLevelDataMap().put(row.getId(), row);
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
                                if (args.length == 2) writes++;
                                return args[0];
                            }
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
                            return null;
                        }));
        assertTrue(player.inventory.addItem(101311, 100));
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

    String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/furniture-levels/" + name + ".json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    void item(int id, String type) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:9999}", ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(int seconds) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:"
                                        + seconds
                                        + ",materialItems:[{id:101311,count:4}]}",
                                FurnitureMakeConfigData.class));
    }

    void level(String fields) {
        GameData.getHomeWorldLevelDataMap()
                .put(
                        10,
                        gson.fromJson(
                                "{level:10,furnitureMakeSlotCount:15," + fields + "}",
                                HomeWorldLevelData.class));
    }

    int reduction(HomeWorldLevelData row) {
        try {
            return (Integer) row.getClass().getMethod("getFurnitureMakeTimeReduction").invoke(row);
        } catch (ReflectiveOperationException absent) {
            return -999;
        }
    }

    InvestigationRewardTest.ClaimSession session() {
        return (InvestigationRewardTest.ClaimSession) player.getSession();
    }

    byte[] reply(int opcode) {
        var packets = session().responses.stream().filter(p -> p.getOpcode() == opcode).toList();
        assertEquals(1, packets.size());
        return packets.get(0).getData();
    }

    FurnitureMakeStartRsp startReply() throws Exception {
        session().responses.clear();
        player.getFurnitureManager().startMake(38, 0);
        return FurnitureMakeStartRsp.parseFrom(reply(PacketOpcodes.FurnitureMakeStartRsp));
    }

    FurnitureMakeSlotItem start() throws Exception {
        assertEquals(0, startReply().getRetcode());
        var slots = player.home.getFurnitureMakeSlotItemList();
        return slots.get(slots.size() - 1);
    }

    TakeFurnitureMakeRsp take(FurnitureMakeSlotItem slot, boolean fast) throws Exception {
        session().responses.clear();
        player.getFurnitureManager().take(slot.getIndex(), slot.getMakeId(), fast);
        return TakeFurnitureMakeRsp.parseFrom(reply(PacketOpcodes.TakeFurnitureMakeRsp));
    }

    TakeFurnitureMakeAllRsp bulk(FurnitureMakeSlotItem slot, boolean fast) throws Exception {
        session().responses.clear();
        player.getFurnitureManager()
                .takeAll(
                        List.of(
                                FurnitureMakeTakeInfo.newBuilder()
                                        .setIndex(slot.getIndex())
                                        .setMakeId(slot.getMakeId())
                                        .setIsFastFinish(fast)
                                        .build()));
        return TakeFurnitureMakeAllRsp.parseFrom(reply(PacketOpcodes.TakeFurnitureMakeAllRsp));
    }

    FurnitureMakeCancelRsp cancel(FurnitureMakeSlotItem slot) throws Exception {
        session().responses.clear();
        player.getFurnitureManager().cancel(slot.getIndex(), slot.getMakeId());
        return FurnitureMakeCancelRsp.parseFrom(reply(PacketOpcodes.FurnitureMakeCancelRsp));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void pinnedModernTableProvidesReductionOnlyAtRankTen(int rank) {
        assertEquals(
                rank == 10 ? 14400 : 0, reduction(GameData.getHomeWorldLevelDataMap().get(rank)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.4", "6.0"})
    void olderCompleteTablesKeepTheirOriginalTimes(String name) throws Exception {
        for (var row : gson.fromJson(fixture(name), HomeWorldLevelData[].class))
            assertEquals(0, reduction(row));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{level:10,furnitureMakeTimeReduction:7,MBABBBPOFFM:14400}",
                "{level:10,MBABBBPOFFM:14400,furnitureMakeTimeReduction:7}"
            })
    void canonicalReductionWinsIndependentOfOrder(String json) {
        assertEquals(7, reduction(gson.fromJson(json, HomeWorldLevelData.class)));
    }

    @Test
    void explicitZeroDoesNotFallBackToModernReduction() {
        level("furnitureMakeTimeReduction:0,MBABBBPOFFM:14400");
        assertEquals(0, reduction(GameData.getHomeWorldLevelDataMap().get(10)));
    }

    @Test
    void invalidExplicitReductionIsNotHidden() {
        level("furnitureMakeTimeReduction:-1,MBABBBPOFFM:14400");
        assertEquals(-1, reduction(GameData.getHomeWorldLevelDataMap().get(10)));
    }

    @Test
    void rankTenStartsTwelveHourInsteadOfSixteenHourJob() throws Exception {
        var slot = start();
        assertEquals(43200, slot.getDurTime());
        assertEquals(43200, slot.getPlan().getMakeTime());
        assertTrue(slot.getPlan().isValidFor(slot));
        assertEquals(96, player.inventory.getItemCountById(101311));
        assertEquals(57600, GameData.getFurnitureMakeConfigDataMap().get(38).getMakeTime());
    }

    @Test
    void rankNineDoesNotReceiveRankTenReduction() throws Exception {
        player.home.setLevel(9);
        assertEquals(57600, start().getDurTime());
    }

    @Test
    void lowerRankCanUseAnExplicitResourceReduction() throws Exception {
        player.home.setLevel(1);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,furnitureMakeSlotCount:1,furnitureMakeTimeReduction:1800}",
                                HomeWorldLevelData.class));
        assertEquals(55800, start().getDurTime());
    }

    @Test
    void reachingRankTenDoesNotRewriteAlreadyPaidJobs() throws Exception {
        player.home.setLevel(9);
        var old = start();
        player.home.setLevel(10);
        var next = start();
        assertEquals(57600, old.getDurTime());
        assertEquals(43200, next.getDurTime());
    }

    @Test
    void instantJobStillPaysMaterialsAndFreezesAValidPlan() throws Exception {
        recipe(14400);
        var slot = start();
        assertEquals(0, slot.getDurTime());
        assertEquals(0, slot.getPlan().getMakeTime());
        assertTrue(slot.getPlan().isValidFor(slot));
        assertEquals(96, player.inventory.getItemCountById(101311));
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void instantSingleClaimNeverConsumesAVial(boolean fast) throws Exception {
        recipe(14400);
        var slot = start();
        assertEquals(0, slot.getDurTime());
        assertEquals(0, take(slot, fast).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(107013));
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertNotEquals(0, take(slot, fast).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void instantBatchClaimNeverConsumesAVial(boolean fast) throws Exception {
        recipe(14400);
        var slot = start();
        assertEquals(0, slot.getDurTime());
        assertEquals(0, bulk(slot, fast).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(107013));
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void instantJobIsCompletedAndCannotRefundPaidMaterials() throws Exception {
        recipe(14400);
        var slot = start();
        assertEquals(0, slot.getDurTime());
        assertEquals(Retcode.RET_FURNITURE_MAKE_IS_FINISH_VALUE, cancel(slot).getRetcode());
        assertEquals(96, player.inventory.getItemCountById(101311));
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void unfinishedReducedJobRefundsTheOriginalCosts() throws Exception {
        var slot = start();
        assertEquals(43200, slot.getDurTime());
        assertEquals(0, cancel(slot).getRetcode());
        assertEquals(100, player.inventory.getItemCountById(101311));
    }

    @Test
    void reducedTimeIsNotAppliedTwiceAtClaim() throws Exception {
        var slot = start();
        assertEquals(43200, slot.getDurTime());
        slot.setBeginTime(Utils.getCurrentSeconds() - 36000);
        assertEquals(Retcode.RET_FURNITURE_MAKE_UNFINISH_VALUE, take(slot, false).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void finishedReducedJobUsesFrozenTimeAfterConfigReplacement() throws Exception {
        var slot = start();
        assertEquals(43200, slot.getDurTime());
        slot.setBeginTime(Utils.getCurrentSeconds() - 43201);
        level("furnitureMakeTimeReduction:0");
        recipe(Integer.MAX_VALUE);
        assertEquals(0, take(slot, false).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
    }

    @Test
    void reductionGreaterThanBaseTimeDoesNotProduceUnsignedWraparound() throws Exception {
        recipe(3600);
        level("furnitureMakeTimeReduction:2147483647");
        assertEquals(0, start().getDurTime());
    }

    @Test
    void invalidReductionStopsBeforeAnyPaymentOrReservation() throws Exception {
        level("furnitureMakeTimeReduction:-1,MBABBBPOFFM:14400");
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, startReply().getRetcode());
        assertEquals(100, player.inventory.getItemCountById(101311));
        assertEquals(0, writes);
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void zeroBaseDurationIsStillInvalidDespiteReduction() throws Exception {
        recipe(0);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, startReply().getRetcode());
        assertEquals(0, writes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void corruptLegacyZeroDurationIsNotMistakenForAnInstantPaidJob(boolean batch) throws Exception {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(0)
                        .makeId(38)
                        .beginTime(Utils.getCurrentSeconds())
                        .durTime(0)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE,
                batch ? bulk(slot, false).getRetcode() : take(slot, false).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @Test
    void persistedInstantPlanCanBeClaimedAfterRecipeRemoval() throws Exception {
        recipe(14400);
        assertEquals(0, start().getDurTime());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture179_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                player.home.setPlayer(player);
                var slot = player.home.getFurnitureMakeSlotItemList().get(0);
                assertEquals(0, slot.getDurTime());
                assertTrue(slot.getPlan().isValidFor(slot));
                GameData.getFurnitureMakeConfigDataMap().remove(38);
                assertEquals(0, take(slot, false).getRetcode());
                assertEquals(1, player.inventory.getItemCountById(360101));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture179_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
