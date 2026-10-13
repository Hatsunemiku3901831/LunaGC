package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;

/** Native choice vectors contain one one-based choice for each box, including repeated choices. */
class ChoiceListUseTest {
    private AbilityTestFixture environment;
    private SelectableItemUseTest.ChoicePlayer player;
    private GameServerPacketHandler registry;
    private GameItem source;
    private Field datastore;
    private Object previousDatastore;
    private int saves, failSave;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new SelectableItemUseTest.ChoicePlayer();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        clear(GameData.getItemDataMap());
        clear(GameData.getRewardDataMap());
        clear(GameData.getCombineDataMap());
        item(100001, "ITEM_MATERIAL", 100);
        item(100002, "ITEM_MATERIAL", 100);
        item(11101, "ITEM_WEAPON", 1);
        var field = server.getClass().getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(server, new InventorySystem(server));
        registry = new GameServerPacketHandler(PacketHandler.class);
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
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:5,100002:7", 3);
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, previousDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private void item(int id, String type, int limit) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",itemType:'" + type + "',stackLimit:" + limit + "}",
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    private void box(String operation, String options, int count) {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CHEST_BATCH_USE',stackLimit:100,maxUseCount:99,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'"
                                                + operation
                                                + "',useParam:['"
                                                + options
                                                + "']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(data.getId(), data);
        if (source == null) {
            assertTrue(player.getInventory().addItem(data.getId(), count));
            source = player.getInventory().getItemById(data.getId());
        } else {
            source.setItemData(data);
        }
    }

    private UseItemRsp use(int count, int scalar, List<Integer> choices) throws Exception {
        var request =
                UseItemReq.newBuilder()
                        .setGuid(source.getGuid())
                        .setCount(count)
                        .setOptionIdx(scalar)
                        .addAllOptionIdxList(choices)
                        .build();
        var session =
                new InvestigationRewardTest.ClaimSession(player.getSession().getServer(), player);
        registry.handle(session, PacketOpcodes.UseItemReq, new byte[0], request.toByteArray());
        assertEquals(1, session.responses.size());
        assertEquals(PacketOpcodes.UseItemRsp, session.responses.get(0).getOpcode());
        return UseItemRsp.parseFrom(session.responses.get(0).getData());
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    @SuppressWarnings("unchecked")
    private List<Integer> choices(ItemUseReceipt receipt) {
        assertNotNull(receipt);
        return (List<Integer>)
                assertDoesNotThrow(
                        () -> receipt.getClass().getMethod("getOptionIds").invoke(receipt));
    }

    private void untouched(UseItemRsp reply) {
        assertNotEquals(0, reply.getRetcode());
        assertEquals(3, stock(60001));
        assertEquals(0, stock(100001));
        assertEquals(0, stock(100002));
        assertNull(player.getPendingItemUse());
    }

    @Test
    void nativeSingleChoiceNeedsNoLegacyScalar() throws Exception {
        var reply = use(1, 0, List.of(2));
        assertEquals(0, reply.getRetcode());
        assertEquals(2, stock(60001));
        assertEquals(7, stock(100002));
        assertEquals(List.of(2), reply.getOptionIdxListList());
        assertEquals(source.getGuid(), reply.getGuid());
        assertEquals(source.getItemId(), reply.getItemId());
    }

    @Test
    void mixedChoicesGrantTheirFullAggregatedAmounts() throws Exception {
        var reply = use(3, 0, List.of(1, 2, 1));
        assertEquals(0, reply.getRetcode());
        assertEquals(0, stock(60001));
        assertEquals(10, stock(100001));
        assertEquals(7, stock(100002));
        assertEquals(List.of(1, 2, 1), reply.getOptionIdxListList());
        assertNull(player.getPendingItemUse());
    }

    @Test
    void repeatedChoicesRepresentRepeatedBoxes() throws Exception {
        assertEquals(0, use(3, 0, List.of(2, 2, 2)).getRetcode());
        assertEquals(21, stock(100002));
        assertEquals(0, stock(100001));
    }

    @Test
    void choiceCountMustEqualUsedBoxCount() throws Exception {
        untouched(use(3, 1, List.of(1, 1)));
    }

    @Test
    void zeroChoiceRejectsTheWholeBatch() throws Exception {
        untouched(use(2, 1, List.of(1, 0)));
    }

    @Test
    void unsignedOutOfRangeChoiceRejectsTheWholeBatch() throws Exception {
        untouched(use(2, 1, List.of(1, -1)));
    }

    @Test
    void absentOptionRejectsTheWholeBatch() throws Exception {
        untouched(use(2, 1, List.of(1, 3)));
    }

    @Test
    void conflictingScalarChoiceRejectsTheWholeBatch() throws Exception {
        untouched(use(2, 2, List.of(1, 1)));
    }

    @Test
    void matchingLegacyScalarAndListRemainCompatible() throws Exception {
        var reply = use(2, 1, List.of(1, 1));
        assertEquals(0, reply.getRetcode());
        assertEquals(10, stock(100001));
        assertEquals(1, reply.getOptionIdx());
        assertEquals(List.of(1, 1), reply.getOptionIdxListList());
    }

    @Test
    void ordinaryRecipeCannotIgnoreAChoiceList() throws Exception {
        GameData.getCombineDataMap()
                .put(200, gson.fromJson("{combineId:200,isDefaultShow:false}", CombineData.class));
        box("ITEM_USE_UNLOCK_COMBINE", "200", 3);
        untouched(use(1, 0, List.of(1)));
        assertFalse(player.getUnlockedCombines().contains(200));
    }

    @Test
    void reservationFailureKeepsFrozenChoicesWithoutPayment() throws Exception {
        failSave = 1;
        assertNotEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        assertEquals(3, stock(60001));
        assertEquals(List.of(1, 2, 1), choices(player.getPendingItemUse()));
        assertEquals(0, player.grants);
    }

    @Test
    void refusedDeletionCannotGrantTheBatch() throws Exception {
        player.refusePayment = true;
        assertNotEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        assertEquals(3, stock(60001));
        assertEquals(0, player.grants);
        assertEquals(List.of(1, 2, 1), choices(player.getPendingItemUse()));
    }

    @Test
    void pretendDeletionCannotGrantTheBatch() throws Exception {
        player.pretendPayment = true;
        assertNotEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        assertEquals(3, stock(60001));
        assertEquals(0, player.grants);
        assertEquals(List.of(1, 2, 1), choices(player.getPendingItemUse()));
    }

    @Test
    void partialGrantKeepsWholePlanAndBlocksReplay() throws Exception {
        player.refuseAt = 2;
        assertNotEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        var receipt = player.getPendingItemUse();
        assertEquals(List.of(1, 2, 1), choices(receipt));
        assertEquals(10, stock(100001));
        assertEquals(0, stock(100002));
        assertEquals(2, receipt.getProducts().size());
        assertEquals(1, receipt.getDeliveries().size());
        assertNotEquals(0, use(1, 0, List.of(2)).getRetcode());
        assertEquals(2, player.grants);
    }

    @Test
    void aggregateStackLimitIsCheckedBeforePayment() throws Exception {
        item(100002, "ITEM_MATERIAL", 10);
        untouched(use(3, 0, List.of(2, 2, 2)));
    }

    @Test
    void missingSecondRewardRejectsTheWholeBatch() throws Exception {
        GameData.getRewardDataMap()
                .put(
                        750000,
                        gson.fromJson(
                                "{rewardId:750000,rewardItemList:[{itemId:100001,itemCount:5}]}",
                                RewardData.class));
        GameData.getRewardDataMap().get(750000).onLoad();
        box("ITEM_USE_GRANT_SELECT_REWARD", "750000,750001", 3);
        untouched(use(2, 0, List.of(1, 2)));
    }

    @Test
    void completeMultiRowRewardsAreCombinedAcrossChoices() throws Exception {
        var first =
                gson.fromJson(
                        "{rewardId:750000,rewardItemList:[{itemId:100001,itemCount:5},{itemId:100002,itemCount:2}]}",
                        RewardData.class);
        var second =
                gson.fromJson(
                        "{rewardId:750001,rewardItemList:[{itemId:100001,itemCount:3}]}",
                        RewardData.class);
        first.onLoad();
        second.onLoad();
        GameData.getRewardDataMap().put(first.getId(), first);
        GameData.getRewardDataMap().put(second.getId(), second);
        box("ITEM_USE_GRANT_SELECT_REWARD", "750000,750001", 3);
        assertEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        assertEquals(13, stock(100001));
        assertEquals(4, stock(100002));
    }

    @Test
    void weaponChoicesCreateIndependentEquipment() throws Exception {
        box("ITEM_USE_ADD_SELECT_ITEM", "11101", 3);
        assertEquals(0, use(3, 0, List.of(1, 1, 1)).getRetcode());
        var weapons =
                player.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemId() == 11101)
                        .toList();
        assertEquals(3, weapons.size());
        assertEquals(3, weapons.stream().map(GameItem::getGuid).distinct().count());
        assertTrue(weapons.stream().allMatch(item -> item.getCount() == 1));
    }

    @Test
    void overflowingAggregateRejectsBeforePayment() throws Exception {
        box("ITEM_USE_CHEST_SELECT_ITEM", "100001:2147483647", 3);
        untouched(use(2, 0, List.of(1, 1)));
    }

    @Test
    void pendingChoicesCannotBeChangedThroughTheirGetter() throws Exception {
        failSave = 1;
        use(3, 0, List.of(1, 2, 1));
        var choices = choices(player.getPendingItemUse());
        assertThrows(UnsupportedOperationException.class, () -> choices.set(0, 2));
        assertEquals(List.of(1, 2, 1), choices);
    }

    @Test
    void reentryCannotStartASecondBatch() throws Exception {
        player.afterGrant =
                () ->
                        assertDoesNotThrow(
                                () -> assertNotEquals(0, use(1, 0, List.of(2)).getRetcode()));
        assertEquals(0, use(2, 0, List.of(1, 2)).getRetcode());
        assertEquals(1, stock(60001));
        assertEquals(5, stock(100001));
        assertEquals(7, stock(100002));
        assertEquals(2, player.grants);
    }

    @Test
    void legacyScalarStillUsesTheExistingReceiptFormat() throws Exception {
        failSave = 1;
        assertNotEquals(0, use(1, 1, List.of()).getRetcode());
        assertEquals(1, player.getPendingItemUse().getOptionId());
        assertEquals(List.of(), choices(player.getPendingItemUse()));
    }

    @Entity(value = "choiceListUses", useDiscriminator = false)
    public static class StoredUse {
        @Id public String id;
        public ItemUseReceipt receipt;
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void groupedChoicesSurviveMongoAndCannotReplay(int stage) throws Exception {
        failSave = stage;
        assertNotEquals(0, use(3, 0, List.of(1, 2, 1)).getRetcode());
        var receipt = player.getPendingItemUse();
        assertEquals(List.of(1, 2, 1), choices(receipt));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_choices167_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredUse.class);
            try {
                var row = new StoredUse();
                row.id = "pending";
                row.receipt = receipt;
                store.save(row);
                var restored = store.find(StoredUse.class).first().receipt;
                assertEquals(List.of(1, 2, 1), choices(restored));
                assertEquals(receipt.getOperationId(), restored.getOperationId());
                assertEquals(source.getObjectId().toHexString(), restored.getSourceObjectId());
                assertEquals(3, restored.getUsedCount());
                assertEquals(2, restored.getProducts().size());
                assertEquals(10, restored.getProducts().get(0).getCount());
                assertEquals(7, restored.getProducts().get(1).getCount());
                player.setPendingItemUse(restored);
                failSave = 0;
                int before = player.grants;
                assertNotEquals(0, use(1, 0, List.of(2)).getRetcode());
                assertEquals(before, player.grants);
            } finally {
                assertTrue(name.startsWith("lunagc_test_choices167_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {115016, 115017, 115024, 115025})
    void allGroupedResourceChoicesWorkAtTheNativeBatchLimit(int boxId) throws Exception {
        var root =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_RESOURCES", "/resources"),
                        "ExcelBinOutput");
        var materials =
                JsonParser.parseString(
                                Files.readString(root.resolve("MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        JsonObject raw = null;
        for (var row : materials) {
            var data = JsonUtils.decode(row.getAsJsonObject(), ItemData.class);
            data.onLoad();
            GameData.getItemDataMap().put(data.getId(), data);
            if (data.getId() == boxId) raw = row.getAsJsonObject();
        }
        assertNotNull(raw);
        var original = source;
        var boxData = GameData.getItemDataMap().get(boxId);
        assertTrue(player.getInventory().addItem(boxId, 99));
        source = player.getInventory().getItemById(boxId);
        var options =
                raw.getAsJsonArray("itemUse")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("useParam")
                        .get(0)
                        .getAsString()
                        .split(",");
        var choices = IntStream.range(0, 99).map(i -> i % options.length + 1).boxed().toList();
        var expected = new HashMap<Integer, Integer>();
        for (int choice : choices) {
            var parts = options[choice - 1].split(":");
            expected.merge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer::sum);
        }
        var reply = use(99, 0, choices);
        assertEquals(0, reply.getRetcode());
        assertEquals(choices, reply.getOptionIdxListList());
        assertNull(player.getInventory().getItemByGuid(source.getGuid()));
        expected.forEach(
                (id, amount) -> assertEquals(amount.intValue(), stock(id), "product " + id));
        assertEquals(3, original.getCount());
        assertNull(player.getPendingItemUse());
    }
}
