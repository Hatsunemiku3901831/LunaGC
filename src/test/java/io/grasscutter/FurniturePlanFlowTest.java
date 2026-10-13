package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurniturePlanFlowTest {
    private AbilityTestFixture environment;
    private FurnitureTakeFlowTest.CraftPlayer player;
    private Field datastore;
    private Object previousDatastore;
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
        item(360101, "ITEM_FURNITURE");
        item(360102, "ITEM_FURNITURE");
        item(101311, "ITEM_MATERIAL");
        definition(360101, 2, 60);
        player.getUnlockedFurniture().add(360101);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) return args[0];
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
        datastore.set(null, previousDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    void item(int id, String type) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:9999}", ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void definition(int output, int count, int exp) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:"
                                        + output
                                        + ",count:"
                                        + count
                                        + ",exp:"
                                        + exp
                                        + ",makeTime:3600,materialItems:[{id:101311,count:4}]}",
                                FurnitureMakeConfigData.class));
    }

    FurnitureMakeSlotItem start() throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().startMake(38, 0);
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .toList();
        assertEquals(1, packets.size());
        assertEquals(0, FurnitureMakeStartRsp.parseFrom(packets.get(0).getData()).getRetcode());
        var slot =
                player.home
                        .getFurnitureMakeSlotItemList()
                        .get(player.home.getFurnitureMakeSlotItemList().size() - 1);
        slot.setBeginTime(Utils.getCurrentSeconds() - 7200);
        return slot;
    }

    TakeFurnitureMakeRsp take(FurnitureMakeSlotItem slot) throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().take(slot.getIndex(), slot.getMakeId(), false);
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .toList();
        assertEquals(1, packets.size());
        return TakeFurnitureMakeRsp.parseFrom(packets.get(0).getData());
    }

    Object plan(FurnitureMakeSlotItem slot) {
        try {
            return slot.getClass().getMethod("getPlan").invoke(slot);
        } catch (ReflectiveOperationException old) {
            return null;
        }
    }

    int scalar(Object plan, String name) throws Exception {
        return (Integer) plan.getClass().getMethod(name).invoke(plan);
    }

    void corrupt(FurnitureMakeSlotItem slot, String field, int value) throws Exception {
        Object plan = plan(slot);
        assertNotNull(plan);
        var declared = plan.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.setInt(plan, value);
    }

    @Test
    void completedJobUsesTheQuantityPaidFor() throws Exception {
        var slot = start();
        definition(360101, 99, 60);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
    }

    @Test
    void completedJobUsesTheOriginalProduct() throws Exception {
        var slot = start();
        definition(360102, 2, 60);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
    }

    @Test
    void originalTrustExpIsPreserved() throws Exception {
        var slot = start();
        definition(360101, 2, 0);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(60, player.home.getExp());
    }

    @Test
    void removedRecipeDoesNotEraseAPaidJob() throws Exception {
        var slot = start();
        GameData.getFurnitureMakeConfigDataMap().remove(38);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
    }

    @Test
    void corruptReplacementDoesNotOverrideASavedPlan() throws Exception {
        var slot = start();
        definition(360101, -1, -1);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(60, player.home.getExp());
    }

    @Test
    void originalCostsAreCopiedForFutureReconciliation() throws Exception {
        var slot = start();
        Object plan = plan(slot);
        assertNotNull(plan);
        assertEquals(38, scalar(plan, "getMakeId"));
        assertEquals(3600, scalar(plan, "getMakeTime"));
        assertEquals(2, scalar(plan, "getCount"));
        var materials = (List<?>) plan.getClass().getMethod("getMaterials").invoke(plan);
        assertEquals(1, materials.size());
        Object cost = materials.get(0);
        assertEquals(101311, scalar(cost, "getId"));
        assertEquals(4, scalar(cost, "getCount"));
        definition(360102, 50, 0);
        assertEquals(4, scalar(cost, "getCount"));
    }

    @Test
    void missingProductDefinitionKeepsThePaidJob() throws Exception {
        var slot = start();
        GameData.getItemDataMap().remove(360101);
        assertNotEquals(0, take(slot).getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
    }

    @Test
    void mismatchedSavedRecipeIsRejected() throws Exception {
        var slot = start();
        corrupt(slot, "makeId", 39);
        assertNotEquals(0, take(slot).getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void corruptSavedCountCannotFallbackToTheReplacement(int count) throws Exception {
        var slot = start();
        corrupt(slot, "count", count);
        assertNotEquals(0, take(slot).getRetcode());
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
    }

    @Test
    void unknownPlanVersionCannotFallbackToCurrentRecipe() throws Exception {
        var slot = start();
        corrupt(slot, "format", 2);
        assertNotEquals(0, take(slot).getRetcode());
    }

    @Test
    void savedNegativeTrustExpIsRejected() throws Exception {
        var slot = start();
        corrupt(slot, "trustExp", -1);
        assertNotEquals(0, take(slot).getRetcode());
        assertEquals(0, player.home.getExp());
    }

    @Test
    void savedDurationMatchesTheQueueDuration() throws Exception {
        var slot = start();
        slot.setDurTime(1);
        assertNotEquals(0, take(slot).getRetcode());
    }

    @Test
    void sourceMaterialsWerePaidOnlyOnce() throws Exception {
        var slot = start();
        assertEquals(96, player.inventory.getItemCountById(101311));
        definition(360101, 2, 60);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(96, player.inventory.getItemCountById(101311));
    }

    @Test
    void repeatedFurnitureStillAwardsTrustOnceAfterRecipeUpdate() throws Exception {
        var first = start();
        assertEquals(0, take(first).getRetcode());
        definition(360101, 2, 80);
        var second = start();
        assertEquals(0, take(second).getRetcode());
        assertEquals(60, player.home.getExp());
        assertEquals(4, player.inventory.getItemCountById(360101));
    }

    @Test
    void persistedPlanCanBeClaimedAfterRecipeRemoval() throws Exception {
        start();
        assertNotNull(plan(player.home.getFurnitureMakeSlotItemList().get(0)));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture173_" + UUID.randomUUID().toString().replace("-", "");
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
                assertNotNull(plan(slot));
                assertEquals(2, scalar(plan(slot), "getCount"));
                GameData.getFurnitureMakeConfigDataMap().remove(38);
                assertEquals(0, take(slot).getRetcode());
                assertEquals(60, player.home.getExp());
                assertEquals(2, player.inventory.getItemCountById(360101));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture173_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void legacyTaskWithoutAPlanStillUsesAValidDefinition() throws Exception {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(0)
                        .makeId(38)
                        .beginTime(Utils.getCurrentSeconds() - 7200)
                        .durTime(3600)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertEquals(0, take(slot).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
    }

    @Test
    void fastFinishUsesTheSavedProductAfterAResourceUpdate() throws Exception {
        var slot = start();
        slot.setBeginTime(Utils.getCurrentSeconds());
        item(107013, "ITEM_MATERIAL");
        assertTrue(player.inventory.addItem(107013, 1));
        definition(360102, 99, 0);
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().take(slot.getIndex(), slot.getMakeId(), true);
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .toList();
        assertEquals(1, packets.size());
        assertEquals(0, TakeFurnitureMakeRsp.parseFrom(packets.get(0).getData()).getRetcode());
        assertEquals(2, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
        assertEquals(0, player.inventory.getItemCountById(107013));
        assertEquals(60, player.home.getExp());
    }
}
