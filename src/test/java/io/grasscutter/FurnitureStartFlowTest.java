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
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class FurnitureStartFlowTest {
    static class CraftPlayer extends InvestigationRewardTest.ClaimPlayer {
        GameHome home;
        boolean refuse, pretend;
        Runnable afterPay;
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean payItem(int id, int count) {
                        if (refuse) return false;
                        if (pretend) return true;
                        boolean result = super.payItem(id, count);
                        if (afterPay != null) {
                            var hook = afterPay;
                            afterPay = null;
                            hook.run();
                        }
                        return result;
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
    }

    private CraftPlayer player;
    private AbilityTestFixture environment;
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
        item(360101, "ITEM_FURNITURE", 100);
        item(101311, "ITEM_MATERIAL", 9999);
        item(101312, "ITEM_MATERIAL", 9999);
        recipe("[{id:101311,count:4},{id:101312,count:2}]");
        level(3);
        player.getUnlockedFurniture().add(360101);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Write fault");
                                if (method.getName().equals("delete"))
                                    return com.mongodb.client.result.DeleteResult.acknowledged(1);
                                return args[0];
                            }
                            return null;
                        }));
        assertTrue(player.inventory.addItem(101311, 40));
        assertTrue(player.inventory.addItem(101312, 20));
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

    void item(int id, String type, int limit) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:" + limit + "}",
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(String costs) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:"
                                        + costs
                                        + "}",
                                FurnitureMakeConfigData.class));
    }

    void level(int slots) {
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:" + slots + "}",
                                HomeWorldLevelData.class));
    }

    FurnitureMakeStartRsp start() throws Exception {
        return start(38, 0);
    }

    FurnitureMakeStartRsp start(int make, int avatar) throws Exception {
        var old = player.getSession();
        var session = new InvestigationRewardTest.ClaimSession(old.getServer(), player);
        player.setSession(session);
        try {
            player.getFurnitureManager().startMake(make, avatar);
        } finally {
            player.setSession(old);
        }
        var packets =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .toList();
        assertEquals(1, packets.size());
        return FurnitureMakeStartRsp.parseFrom(packets.get(0).getData());
    }

    Object pending() {
        try {
            return player.home.getClass().getMethod("getPendingFurnitureStart").invoke(player.home);
        } catch (ReflectiveOperationException old) {
            return null;
        }
    }

    void unchanged() {
        assertEquals(40, player.inventory.getItemCountById(101311));
        assertEquals(20, player.inventory.getItemCountById(101312));
        assertTrue(
                player.home.getFurnitureMakeSlotItemList() == null
                        || player.home.getFurnitureMakeSlotItemList().isEmpty());
    }

    @Test
    void ownedLearnedRecipeCreatesOnePaidQueue() throws Exception {
        assertEquals(0, start().getRetcode());
        assertEquals(36, player.inventory.getItemCountById(101311));
        assertEquals(18, player.inventory.getItemCountById(101312));
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        assertTrue(saves >= 6);
        assertNull(pending());
    }

    @Test
    void unlearnedRecipeCannotSpendMaterials() throws Exception {
        player.getUnlockedFurniture().clear();
        assertEquals(Retcode.RET_FURNITURE_MAKE_LOCKED_VALUE, start().getRetcode());
        unchanged();
    }

    @Test
    void foreignHomeCannotBeModified() throws Exception {
        player.home.setOwnerUid(999);
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void noHomeReturnsTypedFailure() throws Exception {
        player.home = null;
        assertNotEquals(0, start().getRetcode());
    }

    @Test
    void negativeCapacityIsConfigurationFailure() throws Exception {
        level(-1);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, start().getRetcode());
        unchanged();
    }

    @Test
    void unknownCapacityIsNotPretendedToBeFull() throws Exception {
        level(0);
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, start().getRetcode());
        unchanged();
    }

    @Test
    void missingLevelDefinitionReturnsFailure() throws Exception {
        GameData.getHomeWorldLevelDataMap().clear();
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void unknownAvatarIsRejectedBeforePayment() throws Exception {
        assertNotEquals(0, start(38, 10000073).getRetcode());
        unchanged();
    }

    @Test
    void wrongRecipeKeyIsRejected() throws Exception {
        GameData.getFurnitureMakeConfigDataMap()
                .put(39, GameData.getFurnitureMakeConfigDataMap().get(38));
        assertNotEquals(0, start(39, 0).getRetcode());
        unchanged();
    }

    @Test
    void zeroDurationIsRejected() throws Exception {
        var data =
                gson.fromJson(
                        "{configID:38,furnitureItemID:360101,count:1,makeTime:0,materialItems:[]}",
                        FurnitureMakeConfigData.class);
        GameData.getFurnitureMakeConfigDataMap().put(38, data);
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void invalidProductIsRejected() throws Exception {
        item(360101, "ITEM_MATERIAL", 100);
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void nullLegacyQueueCanBeInitialized() throws Exception {
        player.home.setFurnitureMakeSlotItemList(null);
        assertEquals(0, start().getRetcode());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void duplicateLegacyIndexesAreRejected() throws Exception {
        var slot =
                FurnitureMakeSlotItem.of().index(0).makeId(38).beginTime(1).durTime(3600).build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertNotEquals(0, start().getRetcode());
        assertEquals(40, player.inventory.getItemCountById(101311));
    }

    @Test
    void subsequentJobsHaveDistinctIndexes() throws Exception {
        assertEquals(0, start().getRetcode());
        assertEquals(0, start().getRetcode());
        assertEquals(
                2,
                player.home.getFurnitureMakeSlotItemList().stream()
                        .map(FurnitureMakeSlotItem::getIndex)
                        .distinct()
                        .count());
    }

    @Test
    void fullQueueDoesNotSpendAgain() throws Exception {
        level(1);
        assertEquals(0, start().getRetcode());
        assertEquals(Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE, start().getRetcode());
        assertEquals(36, player.inventory.getItemCountById(101311));
    }

    @Test
    void duplicateCostIdsAreAggregatedBeforeAvailabilityCheck() throws Exception {
        recipe("[{id:101311,count:25},{id:101311,count:25}]");
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonpositiveCostIsRejected(int count) throws Exception {
        recipe("[{id:101311,count:" + count + "}]");
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void overflowedDuplicateCostsAreRejected() throws Exception {
        recipe("[{id:101311,count:2147483647},{id:101311,count:1}]");
        assertNotEquals(0, start().getRetcode());
        unchanged();
    }

    @Test
    void invalidCostTypeIsRejected() throws Exception {
        item(101311, "ITEM_FURNITURE", 9999);
        assertNotEquals(0, start().getRetcode());
    }

    @Test
    void refusedPaymentDoesNotCreateAQueue() throws Exception {
        player.refuse = true;
        assertNotEquals(0, start().getRetcode());
        unchanged();
        assertNotNull(pending());
    }

    @Test
    void pretendPaymentDoesNotCreateAQueue() throws Exception {
        player.pretend = true;
        assertNotEquals(0, start().getRetcode());
        unchanged();
        assertNotNull(pending());
    }

    @Test
    void reentryDuringPaymentCannotStartASecondJob() throws Exception {
        final int[] ret = {0};
        player.afterPay =
                () -> {
                    try {
                        ret[0] = start().getRetcode();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, start().getRetcode());
        assertNotEquals(0, ret[0]);
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void interruptedWritesBlockAnotherPayment(int stage) throws Exception {
        failSave = stage;
        assertNotEquals(0, start().getRetcode());
        assertNotNull(pending());
        int wood = player.inventory.getItemCountById(101311);
        int other = player.inventory.getItemCountById(101312);
        failSave = 0;
        assertNotEquals(0, start().getRetcode());
        assertEquals(wood, player.inventory.getItemCountById(101311));
        assertEquals(other, player.inventory.getItemCountById(101312));
    }

    @Test
    void paidReservationSurvivesMongoReload() throws Exception {
        failSave = 5;
        assertNotEquals(0, start().getRetcode());
        assertNotNull(pending());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture171_" + UUID.randomUUID().toString().replace("-", "");
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
                failSave = 0;
                assertNotNull(pending());
                assertEquals(1, restored.getFurnitureMakeSlotItemList().size());
                var receipt = pending();
                var costs = (List<?>) receipt.getClass().getMethod("getCosts").invoke(receipt);
                assertEquals(2, costs.size());
                assertEquals(3, receipt.getClass().getMethod("getState").invoke(receipt));
                assertNotEquals(0, start().getRetcode());
                assertEquals(36, player.inventory.getItemCountById(101311));
                assertEquals(18, player.inventory.getItemCountById(101312));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture171_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void currentRecipesStartWithConfirmedCanonicalCapacity() throws Exception {
        Path root = Path.of(System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"));
        for (String kind : List.of("Material", "HomeWorldFurniture")) {
            var definitions =
                    gson.fromJson(
                            Files.readString(
                                    root.resolve(
                                            "ExcelBinOutput/" + kind + "ExcelConfigData.json")),
                            ItemData[].class);
            for (var data : definitions) {
                data.onLoad();
                GameData.getItemDataMap().put(data.getId(), data);
            }
        }
        var definitions =
                gson.fromJson(
                        Files.readString(
                                root.resolve("ExcelBinOutput/FurnitureMakeExcelConfigData.json")),
                        FurnitureMakeConfigData[].class);
        int valid = 0, invalid = 0;
        for (var recipe : definitions) {
            recipe.onLoad();
            GameData.getFurnitureMakeConfigDataMap().put(recipe.getId(), recipe);
            var output = GameData.getItemDataMap().get(recipe.getFurnitureItemID());
            player.getUnlockedFurniture().add(output.getId());
            player.home.getFurnitureMakeSlotItemList().clear();
            for (var cost : recipe.getMaterialItems()) {
                int before = player.inventory.getItemCountById(cost.getId());
                int add = cost.getCount() + 1 - before;
                if (add > 0) assertTrue(player.inventory.addItem(cost.getId(), add));
            }
            Map<Integer, Integer> before = new HashMap<>();
            recipe.getMaterialItems()
                    .forEach(
                            cost ->
                                    before.put(
                                            cost.getId(),
                                            player.inventory.getItemCountById(cost.getId())));
            var response = start(recipe.getId(), 0);
            if (output.getStackLimit() == 0) {
                invalid++;
                assertNotEquals(0, response.getRetcode());
                assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
            } else {
                valid++;
                assertEquals(0, response.getRetcode(), "recipe " + recipe.getId());
                assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
            }
            for (var cost : recipe.getMaterialItems())
                assertEquals(
                        before.get(cost.getId())
                                - (output.getStackLimit() == 0 ? 0 : cost.getCount()),
                        player.inventory.getItemCountById(cost.getId()));
        }
        assertEquals(1396, valid);
        assertEquals(9, invalid);
    }
}
