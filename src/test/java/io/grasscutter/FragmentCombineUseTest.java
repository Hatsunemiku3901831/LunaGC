package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class FragmentCombineUseTest {
    private AbilityTestFixture environment;
    private SelectableItemUseTest.ChoicePlayer player;
    private InventorySystem inventory;
    private GameItem source;
    private Field datastore;
    private Object previousDatastore;
    private int saves, failSave;
    private Map<Integer, ItemData> previousItems;
    private final Map<Map<?, ?>, Map<?, ?>> previousBookMaps = new IdentityHashMap<>();
    private final Gson gson = new Gson();

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new SelectableItemUseTest.ChoicePlayer();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        previousItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getItemDataMap().clear();
        item(100001, "ITEM_MATERIAL", 100);
        item(11101, "ITEM_WEAPON", 1);
        inventory = new InventorySystem(server);
        var systemField = server.getClass().getDeclaredField("inventorySystem");
        systemField.setAccessible(true);
        systemField.set(server, inventory);
        for (var name : List.of("getCodexBookDataMap", "getCodexBookDataItemIdMap")) {
            var map = bookMap(name);
            if (map != null) {
                previousBookMaps.put(map, new HashMap<>(map));
                map.clear();
            }
        }
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
        fragments("5", "100001", "3");
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, previousDatastore);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(previousItems);
        restoreBookMaps();
        environment.close();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, GameResource> bookMap(String name) throws Exception {
        try {
            return (Map<Integer, GameResource>) GameData.class.getMethod(name).invoke(null);
        } catch (NoSuchMethodException baseline) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void restoreBookMaps() {
        previousBookMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
    }

    private void item(int id, String type, int limit) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:" + limit + "}",
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    private void fragments(String required, String product, String amount) {
        var params = new JsonArray();
        params.add(required);
        params.add(product);
        if (amount != null) params.add(amount);
        var raw =
                JsonParser.parseString(
                                "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CONSUME_BATCH_USE',stackLimit:100,maxUseCount:99,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'ITEM_USE_COMBINE_ITEM'}]}")
                        .getAsJsonObject();
        raw.getAsJsonArray("itemUse").get(0).getAsJsonObject().add("useParam", params);
        var data = JsonUtils.decode(raw, ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(data.getId(), data);
        if (source == null) {
            assertTrue(player.getInventory().addItem(data.getId(), 12));
            source = player.getInventory().getItemById(data.getId());
        } else source.setItemData(data);
    }

    private GameItem use(int count) {
        return inventory.useItem(player, 0, source.getGuid(), count, 0, false);
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private void intact() {
        assertEquals(12, stock(60001));
        assertEquals(0, stock(100001));
    }

    @Test
    void confirmedFragmentsProduceTheConfiguredQuantity() {
        assertSame(source, use(5));
        assertEquals(7, stock(60001));
        assertEquals(3, stock(100001));
        assertNull(player.getPendingItemUse());
    }

    @Test
    void wrongRequiredQuantityIsRejected() {
        assertNull(use(4));
        intact();
    }

    @Test
    void unknownProductIsRejectedBeforeConsumption() {
        fragments("5", "999999", "1");
        assertNull(use(5));
        intact();
    }

    @Test
    void zeroProductQuantityIsRejectedBeforeConsumption() {
        fragments("5", "100001", "0");
        assertNull(use(5));
        intact();
    }

    @Test
    void malformedProductQuantityIsRejectedBeforeConsumption() {
        fragments("5", "100001", "broken");
        assertNull(use(5));
        intact();
    }

    @Test
    void malformedRequiredCountIsRejected() {
        fragments("broken", "100001", "3");
        assertNull(use(5));
        intact();
    }

    @Test
    void omittedProductQuantityDefaultsToOne() {
        fragments("5", "100001", null);
        assertSame(source, use(5));
        assertEquals(1, stock(100001));
    }

    @Test
    void paddedEmptyProductQuantityDefaultsToOne() {
        fragments("5", "100001", "");
        assertSame(source, use(5));
        assertEquals(1, stock(100001));
    }

    @Test
    void nullConfigurationLoadsWithoutThrowing() {
        assertDoesNotThrow(() -> new ItemUseCombineItem(null));
    }

    @Test
    void nullUseParametersAreRejectedWithoutThrowing() {
        assertFalse(
                assertDoesNotThrow(
                        () ->
                                new ItemUseCombineItem(new String[] {"5", "100001", "3"})
                                        .useItem(null)));
    }

    @Test
    void directActionCannotGrantWithoutAnOwnedSource() {
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.count = 5;
        assertFalse(source.getItemData().getItemUseActions().get(0).useItem(params));
        intact();
    }

    @Test
    void reservationFailureDoesNotPayOrGrant() {
        failSave = 1;
        assertNull(use(5));
        intact();
        var record = player.getPendingItemUse();
        assertNotNull(record);
        assertEquals(43, record.getOperation());
        assertEquals(5, record.getUsedCount());
        assertEquals(3, record.getProducts().get(0).getCount());
    }

    @Test
    void refusedConsumptionDoesNotGrantAProduct() {
        player.refusePayment = true;
        assertNull(use(5));
        intact();
        assertNotNull(player.getPendingItemUse());
    }

    @Test
    void pretendConsumptionDoesNotGrantAProduct() {
        player.pretendPayment = true;
        assertNull(use(5));
        intact();
        assertNotNull(player.getPendingItemUse());
    }

    @Test
    void failedGrantKeepsThePaidPlanAndPreventsRetry() {
        player.refuseGrant = true;
        assertNull(use(5));
        assertEquals(7, stock(60001));
        assertEquals(0, stock(100001));
        assertEquals(2, player.getPendingItemUse().getState());
        int grants = player.grants;
        assertNull(use(5));
        assertEquals(grants, player.grants);
        assertEquals(7, stock(60001));
    }

    @Test
    void pretendGrantCannotMarkTheUseComplete() {
        player.pretendGrant = true;
        assertNull(use(5));
        assertEquals(7, stock(60001));
        assertEquals(0, stock(100001));
        assertNotNull(player.getPendingItemUse());
    }

    @Test
    void outputStackLimitIsCheckedBeforePayment() {
        item(100001, "ITEM_MATERIAL", 2);
        assertNull(use(5));
        intact();
        assertNull(player.getPendingItemUse());
    }

    @Test
    void weaponProductsHaveSeparateIdentities() {
        fragments("5", "11101", "3");
        assertSame(source, use(5));
        var products =
                player.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemId() == 11101)
                        .toList();
        assertEquals(3, products.size());
        assertEquals(3, products.stream().map(GameItem::getGuid).distinct().count());
        assertTrue(products.stream().allMatch(item -> item.getCount() == 1));
    }

    @Test
    void nonChoiceProductsCannotAcceptASelectionList() throws Exception {
        var method =
                Arrays.stream(InventorySystem.class.getMethods())
                        .filter(m -> m.getName().equals("useItem") && m.getParameterCount() == 7)
                        .findFirst();
        assertTrue(method.isPresent());
        assertNull(
                method.get()
                        .invoke(
                                inventory,
                                player,
                                0L,
                                source.getGuid(),
                                5,
                                0,
                                false,
                                List.of(1, 1, 1, 1, 1)));
        intact();
    }

    @Test
    void nonChoiceProductsCannotAcceptAScalarSelection() {
        assertNull(inventory.useItem(player, 0, source.getGuid(), 5, 1, false));
        intact();
    }

    @Test
    void reentryCannotConsumeAnotherFragmentGroup() {
        player.afterGrant = () -> assertNull(use(5));
        assertSame(source, use(5));
        assertEquals(7, stock(60001));
        assertEquals(3, stock(100001));
        assertEquals(1, player.grants);
    }

    @Test
    void fixedProductsCannotBeMixedWithAnUnconfirmedAction() {
        var raw =
                JsonParser.parseString(
                                "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CONSUME_BATCH_USE',stackLimit:100,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'ITEM_USE_COMBINE_ITEM',useParam:['5','100001','3']},{useOp:'ITEM_USE_ADD_ITEM',useParam:['100001']}]}")
                        .getAsJsonObject();
        var data = JsonUtils.decode(raw, ItemData.class);
        data.onLoad();
        source.setItemData(data);
        assertNull(use(5));
        intact();
    }

    @Entity(value = "fragmentUses", useDiscriminator = false)
    public static class StoredUse {
        @Id public String id;
        public ItemUseReceipt receipt;
        public emu.grasscutter.game.player.PlayerCodex codex;
    }

    @Test
    void bookDeliveryAndCodexSurviveMongoAfterAWriteFault() throws Exception {
        var primary = bookMap("getCodexBookDataMap");
        if (primary != null) {
            var type = Class.forName("emu.grasscutter.data.excels.codex.CodexBookData");
            var definition = (GameResource) gson.fromJson("{Id:50001001,materialId:100001}", type);
            primary.put(definition.getId(), definition);
            definition.onLoad();
        }
        var book =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:100001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_QUEST',stackLimit:1,useOnGain:true,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'ITEM_USE_UNLOCK_CODEX',useParam:['50001001']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        book.onLoad();
        GameData.getItemDataMap().put(100001, book);
        fragments("5", "100001", "1");
        failSave = 4;
        assertNull(use(5));
        var receipt = player.getPendingItemUse();
        assertNotNull(receipt);
        assertEquals(7, stock(60001));
        assertTrue(player.getCodex().getUnlockedBook().contains(100001));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name =
                "lunagc_test_fragmentbook168_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredUse.class);
            try {
                var row = new StoredUse();
                row.id = "pending";
                row.receipt = receipt;
                row.codex = player.getCodex();
                store.save(row);
                var restored = store.find(StoredUse.class).first();
                assertTrue(restored.codex.getUnlockedBook().contains(100001));
                assertEquals(receipt.getOperationId(), restored.receipt.getOperationId());
                assertEquals(2, restored.receipt.getState());
                player.setPendingItemUse(restored.receipt);
                failSave = 0;
                assertNull(use(5));
                assertEquals(7, stock(60001));
            } finally {
                assertTrue(name.startsWith("lunagc_test_fragmentbook168_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void fixedProductPlanSurvivesMongoAndDoesNotReplay(int stage) throws Exception {
        failSave = stage;
        assertNull(use(5));
        var record = player.getPendingItemUse();
        assertNotNull(record);
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_fragments168_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredUse.class);
            try {
                var row = new StoredUse();
                row.id = "pending";
                row.receipt = record;
                store.save(row);
                var restored = store.find(StoredUse.class).first().receipt;
                assertEquals(record.getOperationId(), restored.getOperationId());
                assertEquals(source.getObjectId().toHexString(), restored.getSourceObjectId());
                assertEquals(43, restored.getOperation());
                assertEquals(5, restored.getUsedCount());
                assertEquals(100001, restored.getProducts().get(0).getId());
                assertEquals(3, restored.getProducts().get(0).getCount());
                player.setPendingItemUse(restored);
                failSave = 0;
                int before = stock(100001), grants = player.grants;
                assertNull(use(5));
                assertEquals(before, stock(100001));
                assertEquals(grants, player.grants);
            } finally {
                assertTrue(name.startsWith("lunagc_test_fragments168_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void allCurrentFragmentDefinitionsUseRealConsumptionAndOutputs() throws Exception {
        var books = bookMap("getCodexBookDataMap");
        if (books != null) {
            var type = Class.forName("emu.grasscutter.data.excels.codex.CodexBookData");
            var rows =
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    System.getenv()
                                                            .getOrDefault(
                                                                    "LUNAGC_RESOURCES",
                                                                    "/resources"),
                                                    "ExcelBinOutput/BooksCodexExcelConfigData.json")))
                            .getAsJsonArray();
            for (var row : rows) {
                var data = (GameResource) gson.fromJson(row, type);
                books.put(data.getId(), data);
                data.onLoad();
            }
        }
        var materials =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                System.getenv()
                                                        .getOrDefault(
                                                                "LUNAGC_RESOURCES", "/resources"),
                                                "ExcelBinOutput/MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        var fragmentRows = new ArrayList<JsonObject>();
        for (var row : materials) {
            var data = JsonUtils.decode(row.getAsJsonObject(), ItemData.class);
            data.onLoad();
            GameData.getItemDataMap().put(data.getId(), data);
            if (data.getItemUseActions() != null
                    && data.getItemUseActions().stream()
                            .anyMatch(action -> action instanceof ItemUseCombineItem))
                fragmentRows.add(row.getAsJsonObject());
        }
        int unlockedBooks = 0;
        for (var raw : fragmentRows) {
            var params =
                    raw.getAsJsonArray("itemUse")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("useParam");
            int count = params.get(0).getAsInt(), result = params.get(1).getAsInt();
            int amount =
                    params.size() > 2 && !params.get(2).getAsString().isBlank()
                            ? params.get(2).getAsInt()
                            : 1;
            int sourceId = raw.get("id").getAsInt();
            int before = stock(result);
            assertTrue(player.getInventory().addItem(sourceId, count));
            source = player.getInventory().getItemById(sourceId);
            assertSame(source, use(count), "fragments " + sourceId);
            if (GameData.getItemDataMap().get(result).isUseOnGain()) {
                assertTrue(player.getCodex().getUnlockedBook().contains(result), "book " + result);
                assertEquals(0, stock(result));
                unlockedBooks++;
            } else assertEquals(before + amount, stock(result));
            assertNull(player.getInventory().getItemByGuid(source.getGuid()));
            assertNull(player.getPendingItemUse());
        }
        assertEquals(19, fragmentRows.size());
        assertEquals(12, unlockedBooks);
    }
}
