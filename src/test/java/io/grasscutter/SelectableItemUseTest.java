package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.shop.SpecialCosmeticShop;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Choice boxes must resolve real reward rows and deliver complete quantities after confirmed
 * payment.
 */
class SelectableItemUseTest {
    private static final List<ItemData> resourceItems = new ArrayList<>();
    private static List<RewardData> resourceRewards;
    private static final Map<Integer, JsonObject> resourceBoxes = new HashMap<>();
    private static List<AvatarCostumeData> resourceCostumes;
    private static List<BydMaterialData> resourceBeyondItems;
    private static List<BeyondCostumeData> resourceBeyondCostumes;

    @BeforeAll
    static void loadResourceFixtures() throws Exception {
        emu.grasscutter.Grasscutter.getLogger();
        for (String name :
                List.of(
                        "MaterialExcelConfigData.json",
                        "WeaponExcelConfigData.json",
                        "HomeWorldFurnitureExcelConfigData.json")) {
            var rows =
                    JsonUtils.loadToList(Path.of("resources/ExcelBinOutput", name), ItemData.class);
            rows.forEach(ItemData::onLoad);
            resourceItems.addAll(rows);
        }
        resourceRewards =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/RewardExcelConfigData.json"),
                        RewardData.class);
        resourceRewards.forEach(RewardData::onLoad);
        resourceCostumes =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarCostumeExcelConfigData.json"),
                        AvatarCostumeData.class);
        resourceBeyondItems =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/BydMaterialExcelConfigData.json"),
                        BydMaterialData.class);
        resourceBeyondCostumes =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/BeyondCostumeExcelConfigData.json"),
                        BeyondCostumeData.class);
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/MaterialExcelConfigData.json")))
                        .getAsJsonArray()) {
            var data = row.getAsJsonObject();
            resourceBoxes.put(data.get("id").getAsInt(), data);
        }
    }

    private AbilityTestFixture environment;
    private ChoicePlayer player;
    private InventorySystem system;
    private Field datastore;
    private Object oldDatastore;
    private GuardedPlayerTestStore playerStore;
    private ObservedItemTestWrites itemWrites;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private int saves, failSave;
    private GameItem source;

    static class ChoicePlayer extends InvestigationRewardTest.ClaimPlayer {
        transient boolean refusePayment, pretendPayment, refuseGrant, pretendGrant;
        transient int grants, refuseAt;
        transient Runnable afterGrant;
        final transient Inventory choices =
                new Inventory(this) {
                    @Override
                    public boolean removeItem(GameItem item, int count) {
                        if (refusePayment) return false;
                        if (pretendPayment) return true;
                        return super.removeItem(item, count);
                    }

                    @Override
                    public boolean addItem(GameItem item, ActionReason reason) {
                        if (reason == ActionReason.Shop) {
                            if (refuseGrant || ++grants == refuseAt) return false;
                            if (pretendGrant) return true;
                        }
                        boolean result = super.addItem(item, reason);
                        if (reason == ActionReason.Shop && afterGrant != null) {
                            var callback = afterGrant;
                            afterGrant = null;
                            callback.run();
                        }
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return choices;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void preserveAndClear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new ChoicePlayer();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        system = new InventorySystem(server);
        preserveAndClear(GameData.getItemDataMap());
        preserveAndClear(GameData.getRewardDataMap());
        preserveAndClear(GameData.getAvatarCostumeDataMap());
        preserveAndClear(GameData.getAvatarCostumeDataItemIdMap());
        preserveAndClear(GameData.getBydMaterialDataMap());
        preserveAndClear(GameData.getBeyondCostumeDataMap());
        item(100001, "ITEM_MATERIAL", 100);
        item(100002, "ITEM_MATERIAL", 100);
        item(11101, "ITEM_WEAPON", 1);
        item(202, "ITEM_VIRTUAL", 1);
        reward(750000, List.of(new ItemParamData(100001, 5)));
        reward(750001, List.of(new ItemParamData(100002, 3)));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        preparePlayerStore();
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
                                return args[0];
                            }
                            return null;
                        }));
        observeItems();
        box("ITEM_USE_GRANT_SELECT_REWARD", "750000,750001");
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (datastore != null) datastore.set(null, oldDatastore);
        if (playerStore != null) playerStore.close();
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private void item(int id, String type, int limit) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'"
                                        + type
                                        + "',materialType:'MATERIAL_NONE',rankLevel:3,stackLimit:"
                                        + limit
                                        + "}",
                                ItemData.class));
    }

    private void preparePlayerStore() throws Exception {
        boolean observing = itemWrites != null;
        if (observing) {
            itemWrites.close();
            itemWrites = null;
        }
        if (playerStore != null) {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            playerStore.close();
        }
        playerStore =
                new GuardedPlayerTestStore(
                        player,
                        () -> {
                            if (++saves == failSave) throw new IllegalStateException("Write fault");
                        });
        if (observing) observeItems();
    }

    private void observeItems() throws Exception {
        itemWrites =
                new ObservedItemTestWrites(
                        player,
                        playerStore.datastore(),
                        DatabaseManager.getGameDatastore(),
                        doc -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Item write fault");
                        },
                        doc -> {});
    }

    private void reward(int id, List<ItemParamData> items) {
        var row = new RewardData();
        row.rewardId = id;
        row.rewardItemList = items;
        GameData.getRewardDataMap().put(id, row);
    }

    private void box(String operation, String options) {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_SELECTABLE_CHEST',stackLimit:99,maxUseCount:99,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'"
                                                + operation
                                                + "',useParam:['"
                                                + options
                                                + "']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(60001, data);
        if (player.getInventory().getItemById(60001) == null)
            assertTrue(player.getInventory().addItem(60001, 3));
        source = player.getInventory().getItemById(60001);
        source.setItemData(data);
    }

    private GameItem use(int count, int option) {
        return assertDoesNotThrow(
                () -> system.useItem(player, 0, source.getGuid(), count, option, false));
    }

    private Object pending() {
        return assertDoesNotThrow(
                () -> player.getClass().getMethod("getPendingItemUse").invoke(player));
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    @Test
    void rewardIdSelectionUsesRewardContentsAndMultipliesCounts() {
        assertSame(source, use(2, 1));
        assertEquals(1, stock(60001));
        assertEquals(10, stock(100001));
        assertEquals(0, stock(750000));
        assertNull(pending());
    }

    @Test
    void secondRewardOptionIsHonoured() {
        assertNotNull(use(1, 2));
        assertEquals(3, stock(100002));
        assertEquals(0, stock(100001));
    }

    @Test
    void batchWeaponSelectionCreatesSeparateOwnedInstances() {
        box("ITEM_USE_ADD_SELECT_ITEM", "11101");
        assertNotNull(use(2, 1));
        var weapons =
                player.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemType() == ItemType.ITEM_WEAPON)
                        .toList();
        assertEquals(2, weapons.size());
        assertNotEquals(weapons.get(0).getGuid(), weapons.get(1).getGuid());
        weapons.forEach(item -> assertEquals(player.getUid(), item.getOwnerId()));
        assertEquals(1, stock(60001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 3, Integer.MAX_VALUE})
    void invalidRewardChoicePreservesTheBox(int option) {
        assertNull(use(1, option));
        assertEquals(3, stock(60001));
        assertEquals(0, player.grants);
        assertNull(pending());
    }

    @Test
    void missingRewardRowPreservesTheBox() {
        GameData.getRewardDataMap().remove(750000);
        assertNull(use(1, 1));
        assertEquals(3, stock(60001));
    }

    @Test
    void wrongRewardKeyCannotGrant() {
        reward(750002, List.of(new ItemParamData(100001, 5)));
        GameData.getRewardDataMap().put(750000, GameData.getRewardDataMap().get(750002));
        assertNull(use(1, 1));
        assertEquals(3, stock(60001));
    }

    @Test
    void quantityOverflowCannotCreateAClampedStack() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:2147483647");
        assertNull(use(2, 1));
        assertEquals(3, stock(60001));
        assertEquals(0, stock(100001));
    }

    @Test
    void entireRewardIsPreflightedBeforePayment() {
        reward(750000, List.of(new ItemParamData(100001, 5), new ItemParamData(999999, 1)));
        assertNull(use(1, 1));
        assertEquals(3, stock(60001));
        assertEquals(0, stock(100001));
    }

    @Test
    void allRewardRowsAreGrantedAfterOnePayment() {
        reward(750000, List.of(new ItemParamData(100001, 5), new ItemParamData(100002, 3)));
        assertNotNull(use(2, 1));
        assertEquals(1, stock(60001));
        assertEquals(10, stock(100001));
        assertEquals(6, stock(100002));
    }

    @Test
    void refusedPaymentDoesNotGrantFreeProducts() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        player.refusePayment = true;
        assertNull(use(1, 1));
        assertEquals(0, stock(100001));
        assertEquals(3, stock(60001));
        assertNotNull(pending());
    }

    @Test
    void pretendPaymentDoesNotGrantFreeProducts() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        player.pretendPayment = true;
        assertNull(use(1, 1));
        assertEquals(0, stock(100001));
        assertEquals(3, stock(60001));
    }

    @Test
    void pretendGrantKeepsTheUnconfirmedAttempt() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        player.pretendGrant = true;
        assertNull(use(1, 1));
        assertEquals(0, stock(100001));
        assertNotNull(pending());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void everyConfirmationFailureBlocksReplay(int stage) {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        failSave = stage;
        assertNull(use(1, 1));
        assertNotNull(pending());
        int boxes = stock(60001), products = stock(100001), grants = player.grants;
        failSave = 0;
        assertNull(use(1, 2));
        assertEquals(boxes, stock(60001));
        assertEquals(products, stock(100001));
        assertEquals(grants, player.grants);
    }

    @Test
    void partialWeaponDeliveryIsNotAcknowledgedOrReplayed() {
        box("ITEM_USE_ADD_SELECT_ITEM", "11101");
        player.refuseAt = 2;
        assertNull(use(2, 1));
        assertEquals(1, stock(11101));
        assertEquals(1, stock(60001));
        player.refuseAt = 0;
        assertNull(use(1, 1));
        assertEquals(1, stock(11101));
    }

    @Test
    void inventoryCallbackCannotConsumeOrGrantAgain() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        player.afterGrant = () -> system.useItem(player, 0, source.getGuid(), 1, 1, false);
        assertNotNull(use(1, 1));
        assertEquals(2, stock(60001));
        assertEquals(5, stock(100001));
        assertEquals(1, player.grants);
    }

    @Test
    void freedBoxSlotAllowsAFinalBoxToOpenInAFullBag() {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        source.setCount(1);
        var tab = new MaterialInventoryTab(1);
        tab.onAddItem(source);
        player.getInventory().getInventoryTypes().put(ItemType.ITEM_MATERIAL.getValue(), tab);
        assertNotNull(use(1, 1));
        assertEquals(0, stock(60001));
        assertEquals(5, stock(100001));
        assertEquals(1, tab.getSize());
    }

    @Test
    void malformedOptionShapesNeverThrowDuringResourceLoad() {
        assertDoesNotThrow(() -> new ItemUseChestSelectItem(new String[] {"100001"}));
        assertDoesNotThrow(() -> new ItemUseChestSelectItem(null));
        assertDoesNotThrow(() -> new ItemUseAddSelectItem(null));
        assertDoesNotThrow(() -> new ItemUseGrantSelectReward(null));
    }

    @Test
    void grantRewardReportsItsCorrectOperation() {
        assertEquals(
                ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD,
                new ItemUseGrantSelectReward(new String[] {"750000"}).getItemUseOp());
    }

    @Test
    void rewardCurrencySupplementIsMultipliedAndConfirmed() {
        player.setMora(200);
        GameData.getRewardDataMap()
                .put(
                        750000,
                        gson.fromJson(
                                "{rewardId:750000,scoin:7,rewardItemList:[{id:100001,count:5}]}",
                                RewardData.class));
        assertNotNull(use(2, 1));
        assertEquals(214, player.getMora());
        assertEquals(10, stock(100001));
        assertEquals(1, stock(60001));
    }

    @Test
    void undecodedRewardSupplementCannotBeDiscardedSilently() {
        GameData.getRewardDataMap()
                .put(
                        750000,
                        gson.fromJson(
                                "{rewardId:750000,MEBGMPIJIDE:1,rewardItemList:[{id:100001,count:5}]}",
                                RewardData.class));
        assertNull(use(1, 1));
        assertEquals(3, stock(60001));
        assertEquals(0, stock(100001));
        assertNull(pending());
    }

    @Test
    void alreadyOwnedCostumePreservesItsChoiceBox() {
        resourceCostumes.forEach(
                row -> {
                    GameData.getAvatarCostumeDataMap().put(row.getId(), row);
                    row.onLoad();
                });
        var skin =
                resourceItems.stream()
                        .filter(row -> row.getId() == 340000)
                        .findFirst()
                        .orElseThrow();
        GameData.getItemDataMap().put(skin.getId(), skin);
        player.addCostume(201401);
        box("ITEM_USE_CHEST_SELECT_ITEM", "340000:1");
        assertNull(use(1, 1));
        assertEquals(3, stock(60001));
        assertNull(pending());
    }

    @Test
    void aSelectorCannotBypassTheSourcePaymentThroughDirectUse() {
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.optionId = 1;
        assertFalse(new ItemUseChestSelectItem(new String[] {"100001:5"}).useItem(params));
        assertEquals(0, stock(100001));
        assertEquals(3, stock(60001));
    }

    @Test
    void targetRestrictedChoiceBoxCannotSkipTheTargetCheck() {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_SELECTABLE_CHEST',stackLimit:99,maxUseCount:99,useTarget:'ITEM_USE_TARGET_SPECIFY_AVATAR',itemUse:[{useOp:'ITEM_USE_CHEST_SELECT_ITEM',useParam:['100001:5']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(60001, data);
        source.setItemData(data);
        assertNull(use(1, 1));
        assertEquals(0, stock(100001));
        assertEquals(3, stock(60001));
        assertNull(pending());
    }

    @dev.morphia.annotations.Entity(value = "item_use_receipt_test", useDiscriminator = false)
    static class StoredUse {
        @dev.morphia.annotations.Id String id;
        ItemUseReceipt receipt;
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void selectedRewardAndStableSourceSurviveMongoWithoutReplay(int stage) throws Exception {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5");
        failSave = stage;
        assertNull(use(1, 1));
        var receipt = (ItemUseReceipt) pending();
        assertNotNull(receipt);
        assertEquals(stage == 2 ? 1 : 3, receipt.getState());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_selection164_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredUse.class);
            try {
                var row = new StoredUse();
                row.id = "pending";
                row.receipt = receipt;
                store.save(row);
                var restored = store.find(StoredUse.class).first().receipt;
                assertEquals(receipt.getOperationId(), restored.getOperationId());
                assertEquals(source.getObjectId().toHexString(), restored.getSourceObjectId());
                assertEquals(source.getGuid(), restored.getSourceGuid());
                assertEquals(3, restored.getSourceBefore());
                assertEquals(1, restored.getUsedCount());
                assertEquals(1, restored.getOptionId());
                assertEquals(100001, restored.getProducts().get(0).getId());
                assertEquals(5, restored.getProducts().get(0).getCount());
                assertEquals(stage == 6 ? 1 : 0, restored.getDeliveries().size());
                player.setPendingItemUse(restored);
                failSave = 0;
                int boxes = stock(60001), products = stock(100001), grants = player.grants;
                assertNull(use(1, 2));
                assertEquals(boxes, stock(60001));
                assertEquals(products, stock(100001));
                assertEquals(grants, player.grants);
            } finally {
                assertTrue(name.startsWith("lunagc_test_selection164_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                115013, 115016, 115017, 115024, 115025, 115026, 115027, 115031, 115035, 115040,
                115042, 115043, 115045, 115047, 116002, 116004, 116005, 116006, 116007, 116010,
                116011, 116012, 116013, 116014, 116015, 116016, 116017, 116018
            })
    void everyCurrentChoiceBoxCanDeliverEachDeclaredOption(int boxId) throws Exception {
        resourceItems.forEach(item -> GameData.getItemDataMap().put(item.getId(), item));
        resourceRewards.forEach(reward -> GameData.getRewardDataMap().put(reward.getId(), reward));
        resourceCostumes.forEach(
                row -> {
                    GameData.getAvatarCostumeDataMap().put(row.getId(), row);
                    row.onLoad();
                });
        resourceBeyondItems.forEach(row -> GameData.getBydMaterialDataMap().put(row.getId(), row));
        resourceBeyondCostumes.forEach(
                row -> GameData.getBeyondCostumeDataMap().put(row.getId(), row));
        var data = GameData.getItemDataMap().get(boxId);
        assertNotNull(data, "Missing box " + boxId);
        var raw = resourceBoxes.get(boxId);
        var action = raw.getAsJsonArray("itemUse").get(0).getAsJsonObject();
        var options = action.getAsJsonArray("useParam").get(0).getAsString().split(",");
        for (int index = 0; index < options.length; index++) {
            player = new ChoicePlayer();
            player.setSession(
                    new InvestigationRewardTest.ClaimSession(
                            environment.scene.getWorld().getServer(), player));
            preparePlayerStore();
            assertTrue(player.getInventory().addItem(boxId, 1));
            source = player.getInventory().getItemById(boxId);
            assertNotNull(use(1, index + 1), "Box " + boxId + " option " + (index + 1));
            assertNull(player.getInventory().getItemByGuid(source.getGuid()));
            assertNull(pending());
            int id = Integer.parseInt(options[index].split(":")[0]);
            List<ItemParamData> expected =
                    switch (action.get("useOp").getAsString()) {
                        case "ITEM_USE_GRANT_SELECT_REWARD" -> GameData.getRewardDataMap()
                                .get(id)
                                .getRewardItemList();
                        case "ITEM_USE_CHEST_SELECT_ITEM" -> List.of(
                                new ItemParamData(
                                        id, Integer.parseInt(options[index].split(":")[1])));
                        default -> List.of(new ItemParamData(id, 1));
                    };
            for (var product : expected) {
                if (!SpecialCosmeticShop.kind(product.getId()).isEmpty())
                    assertTrue(
                            SpecialCosmeticShop.owned(player, product.getId()),
                            "cosmetic " + product.getId());
                else
                    assertEquals(
                            product.getCount(),
                            stock(product.getId()),
                            "product " + product.getId());
            }
        }
    }
}
