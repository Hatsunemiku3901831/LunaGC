package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ReliquaryDecomposeRspOuterClass.ReliquaryDecomposeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

/** Economic interruption may never be reported as a successful strongbox exchange or replayed. */
class StrongboxDeliveryTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;
    private AbilityTestFixture environment;
    private ExchangePlayer player;
    private CombineManger manager;
    private Field datastore;
    private Object oldDatastore;
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private Map<Integer, List<Integer>> definitions;
    private final Gson gson = new Gson();
    private final List<GameItem> inputs = new ArrayList<>();
    private int saves, failSave, rolls;
    private boolean invalidPlan;

    static class ExchangePlayer extends InvestigationRewardTest.ClaimPlayer {
        transient int deletes, grants, refuseDelete, pretendDelete, refuseGrant, throwAfterDelete;
        transient boolean pretendGrant, alterProfile;
        transient Runnable afterDelete, afterGrant;
        final transient Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean removeItem(long guid) {
                        int attempt = ++deletes;
                        if (attempt == refuseDelete) return false;
                        if (attempt == pretendDelete) return true;
                        boolean result = super.removeItem(guid);
                        var callback = afterDelete;
                        afterDelete = null;
                        if (callback != null) callback.run();
                        if (attempt == throwAfterDelete)
                            throw new IllegalStateException("Delete fault");
                        return result;
                    }

                    @Override
                    public boolean addItem(GameItem item) {
                        if (item.getItemId() != 77513) return super.addItem(item);
                        if (++grants == refuseGrant) return false;
                        if (pretendGrant) return true;
                        boolean result = super.addItem(item);
                        if (alterProfile) item.setMainPropId(999);
                        var callback = afterGrant;
                        afterGrant = null;
                        if (callback != null) callback.run();
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static class OtherExchangePlayer extends ExchangePlayer {
        @Override
        public int getUid() {
            return 10002;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void preserveAndClear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new ExchangePlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        manager =
                new CombineManger(environment.scene.getWorld().getServer()) {
                    protected GameItem createStrongboxItem(int id) {
                        rolls++;
                        if (invalidPlan) throw new IllegalStateException("Generation fault");
                        var item = new GameItem(id, 1);
                        item.setMainPropId(321);
                        item.getAppendPropIdList().addAll(List.of(7, 8));
                        return item;
                    }
                };
        preserveAndClear(GameData.getItemDataMap());
        preserveAndClear(GameData.getReliquaryDecomposeDataMap());
        for (int id : List.of(75513, 77513))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",itemType:'ITEM_RELIQUARY',equipType:'EQUIP_RING',rankLevel:5,appendPropNum:0}",
                                    ItemData.class));
        var field = CombineManger.class.getDeclaredField("reliquaryDecomposeData");
        field.setAccessible(true);
        definitions = (Map<Integer, List<Integer>>) field.get(null);
        preserveAndClear(definitions);
        definitions.put(1, List.of(77513));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
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
                                if (args[0] instanceof GameItem item
                                        && item.getObjectId() == null) {
                                    var identity = GameItem.class.getDeclaredField("id");
                                    identity.setAccessible(true);
                                    identity.set(item, new ObjectId());
                                }
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
        seed(player, 3);
        player.packets.clear();
    }

    private void seed(ExchangePlayer current, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            var item = new GameItem(75513, 1);
            var identity = GameItem.class.getDeclaredField("id");
            identity.setAccessible(true);
            identity.set(item, new ObjectId());
            assertTrue(current.getInventory().addItem(item));
            if (current == player) inputs.add(item);
        }
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (playerStore != null) playerStore.close();
        datastore.set(null, oldDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private List<Long> ids() {
        return inputs.stream().map(GameItem::getGuid).toList();
    }

    private void exchange(int count) {
        assertDoesNotThrow(() -> manager.decomposeReliquaries(player, 1, count, ids()));
    }

    private Object pending() {
        return assertDoesNotThrow(
                () -> player.getClass().getMethod("getPendingStrongbox").invoke(player));
    }

    private Object get(Object object, String method) {
        return assertDoesNotThrow(() -> object.getClass().getMethod(method).invoke(object));
    }

    private int state(Object receipt) {
        return (int) get(receipt, "getState");
    }

    private ReliquaryDecomposeRsp response() {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ReliquaryDecomposeRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return assertDoesNotThrow(() -> ReliquaryDecomposeRsp.parseFrom(packet.getData()));
    }

    private void failed() {
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, response().getRetcode());
        assertEquals(0, response().getGuidListCount());
    }

    private int outputs() {
        return player.getInventory().getItemCountById(77513);
    }

    @Test
    void successfulExchangeConfirmsDeletionAndIndividualOutputIdentity() {
        exchange(1);
        assertEquals(0, response().getRetcode());
        assertEquals(1, response().getGuidListCount());
        var result = player.getInventory().getItemByGuid(response().getGuidList(0));
        assertNotNull(result);
        assertEquals(player.getUid(), result.getOwnerId());
        assertEquals(321, result.getMainPropId());
        assertEquals(List.of(7, 8), result.getAppendPropIdList());
        assertNotNull(result.getObjectId());
        assertNull(pending());
        assertEquals(1, rolls);
        assertEquals(3, player.deletes);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void eachAcknowledgementFailureKeepsTheAttemptAndBlocksReplay(int stage) {
        failSave = stage;
        exchange(1);
        failed();
        var receipt = pending();
        assertNotNull(receipt);
        assertEquals(1, get(receipt, "getConfigId"));
        assertEquals(3, ((List<?>) get(receipt, "getIngredients")).size());
        assertEquals(1, ((List<?>) get(receipt, "getOutputs")).size());
        var output = ((List<?>) get(receipt, "getOutputs")).get(0);
        assertEquals(321, get(output, "getMainPropId"));
        int deletes = player.deletes,
                grants = player.grants,
                rollsBefore = rolls,
                stored = outputs();
        failSave = 0;
        manager = new CombineManger(environment.scene.getWorld().getServer());
        exchange(1);
        failed();
        assertEquals(deletes, player.deletes);
        assertEquals(grants, player.grants);
        assertEquals(rollsBefore, rolls);
        assertEquals(stored, outputs());
    }

    @Test
    void refusedRemovalCannotGenerateFreeOutputs() {
        player.refuseDelete = 1;
        exchange(1);
        failed();
        assertEquals(0, outputs());
        assertEquals(3, player.getInventory().getItemCountById(75513));
        assertEquals(1, state(pending()));
    }

    @Test
    void pretendRemovalCannotGenerateFreeOutputs() {
        player.pretendDelete = 1;
        exchange(1);
        failed();
        assertEquals(0, outputs());
        assertEquals(3, player.getInventory().getItemCountById(75513));
    }

    @Test
    void deletionExceptionKeepsOriginalStableItemIdentity() {
        player.throwAfterDelete = 2;
        exchange(1);
        failed();
        assertEquals(1, player.getInventory().getItemCountById(75513));
        var costs = (List<?>) get(pending(), "getIngredients");
        assertEquals(inputs.get(0).getObjectId().toHexString(), get(costs.get(0), "getObjectId"));
        assertEquals(inputs.get(0).getGuid(), get(costs.get(0), "getGuid"));
        assertEquals(0, outputs());
    }

    @Test
    void refusedOutputDoesNotProduceASuccessWithAnUnownedGuid() {
        player.refuseGrant = 1;
        exchange(1);
        failed();
        assertEquals(0, outputs());
        assertEquals(2, state(pending()));
        assertEquals(3, player.deletes);
    }

    @Test
    void pretendOutputDoesNotProduceASuccess() {
        player.pretendGrant = true;
        exchange(1);
        failed();
        assertEquals(0, outputs());
        assertEquals(2, state(pending()));
    }

    @Test
    void partialOutputKeepsEachFrozenPlanAndConfirmedGuid() throws Exception {
        seed(player, 3);
        player.refuseGrant = 2;
        exchange(2);
        failed();
        assertEquals(1, outputs());
        var outputs = (List<?>) get(pending(), "getOutputs");
        assertEquals(2, outputs.size());
        assertEquals(true, get(outputs.get(0), "isDelivered"));
        assertEquals(false, get(outputs.get(1), "isDelivered"));
        long guid = (long) get(outputs.get(0), "getDeliveredGuid");
        var stored = player.getInventory().getItemByGuid(guid);
        assertNotNull(stored);
        assertEquals(
                stored.getObjectId().toHexString(), get(outputs.get(0), "getDeliveredObjectId"));
        assertEquals(2, rolls);
        player.refuseGrant = 0;
        exchange(2);
        failed();
        assertEquals(1, outputs());
        assertEquals(2, rolls);
    }

    @Test
    void modifiedOutputProfileCannotBeConfirmed() {
        player.alterProfile = true;
        exchange(1);
        failed();
        assertEquals(1, outputs());
        assertEquals(2, state(pending()));
    }

    @Test
    void removalCallbackCannotReenterOrReroll() {
        player.afterDelete = () -> manager.decomposeReliquaries(player, 1, 1, ids());
        exchange(1);
        assertEquals(0, response().getRetcode());
        assertEquals(1, outputs());
        assertEquals(3, player.deletes);
        assertEquals(1, rolls);
    }

    @Test
    void outputCallbackCannotReenterOrReroll() {
        player.afterGrant = () -> manager.decomposeReliquaries(player, 1, 1, ids());
        exchange(1);
        assertEquals(0, response().getRetcode());
        assertEquals(1, outputs());
        assertEquals(3, player.deletes);
        assertEquals(1, rolls);
    }

    @Test
    void generationFailureIsReportedBeforeDeletingAnyIngredient() {
        invalidPlan = true;
        exchange(1);
        failed();
        assertEquals(0, player.deletes);
        assertEquals(3, player.getInventory().getItemCountById(75513));
        assertNull(pending());
    }

    @Test
    void invalidInputsDoNotCreateAnEconomicReceiptOrRoll() {
        inputs.get(0).setLocked(true);
        exchange(1);
        assertEquals(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR_VALUE, response().getRetcode());
        assertEquals(0, rolls);
        assertEquals(0, player.deletes);
        assertNull(pending());
    }

    @Test
    void aCorruptUnclearedReceiptStillBlocksEconomics() {
        assertDoesNotThrow(
                () -> {
                    var type = Class.forName("emu.grasscutter.game.combine.StrongboxReceipt");
                    player.getClass()
                            .getMethod("setPendingStrongbox", type)
                            .invoke(player, type.getConstructor().newInstance());
                });
        exchange(1);
        failed();
        assertEquals(0, rolls);
        assertEquals(0, player.deletes);
    }

    @Test
    void pendingCombineStopsArtifactExchangeUntilReconciled() {
        player.setPendingCombine(new emu.grasscutter.game.combine.CombineReceipt());
        exchange(1);
        failed();
        assertEquals(0, player.deletes);
        assertEquals(0, rolls);
    }

    @Test
    void reservationContainsStableIdsEvenBeforeTheFirstItemWrite() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var field = GameItem.class.getDeclaredField("id");
        field.setAccessible(true);
        for (var input : inputs) field.set(input, null);
        failSave = 1;
        exchange(1);
        failed();
        var receipt = pending();
        var cost = ((List<?>) get(receipt, "getIngredients")).get(0);
        var output = ((List<?>) get(receipt, "getOutputs")).get(0);
        assertTrue(ObjectId.isValid((String) get(cost, "getObjectId")));
        assertTrue(ObjectId.isValid((String) get(output, "getObjectId")));
        assertEquals(inputs.get(0).getObjectId().toHexString(), get(cost, "getObjectId"));
        assertEquals(0, player.deletes);
        assertEquals(0, player.grants);
    }

    @Test
    void anotherPlayerCanExchangeWhileThisPlayerIsPending() throws Exception {
        player.refuseGrant = 1;
        exchange(1);
        failed();
        var other = new OtherExchangePlayer();
        other.setUid(other.getUid());
        other.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), other));
        seed(other, 3);
        var ids = other.getInventory().getItems().values().stream().map(GameItem::getGuid).toList();
        new CombineManger(environment.scene.getWorld().getServer())
                .decomposeReliquaries(other, 1, 1, ids);
        assertEquals(1, other.getInventory().getItemCountById(77513));
        assertEquals(0, other.getInventory().getItemCountById(75513));
        assertNotNull(pending());
    }

    @Test
    void pendingStrongboxAlsoBlocksNormalCombine() {
        player.refuseGrant = 1;
        exchange(1);
        failed();
        assertNull(manager.combineItem(player, 999, 1));
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CombineRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var reply =
                assertDoesNotThrow(
                        () ->
                                emu.grasscutter.net.proto.CombineRspOuterClass.CombineRsp.parseFrom(
                                        packet.getData()));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, reply.getRetcode());
        assertEquals(1, rolls);
    }

    @dev.morphia.annotations.Entity(value = "strongbox_receipt_test", useDiscriminator = false)
    static class StoredStrongbox {
        @dev.morphia.annotations.Id String id;
        emu.grasscutter.game.combine.StrongboxReceipt receipt;
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 8})
    void frozenProfilesStableIdsAndConfirmationSurviveMongoReload(int stage) throws Exception {
        inputs.get(0).setLevel(21);
        inputs.get(0).setExp(123);
        inputs.get(0).setTotalExp(999);
        inputs.get(0).setMainPropId(111);
        inputs.get(0).getAppendPropIdList().add(42);
        failSave = stage;
        exchange(1);
        failed();
        var receipt = (emu.grasscutter.game.combine.StrongboxReceipt) pending();
        assertEquals(stage == 3 ? 1 : 3, receipt.getState());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String databaseName =
                "lunagc_test_strongbox161_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, databaseName);
            store.getMapper().map(StoredStrongbox.class);
            try {
                var row = new StoredStrongbox();
                row.id = "pending";
                row.receipt = receipt;
                store.save(row);
                var restored = store.find(StoredStrongbox.class).first().receipt;
                assertEquals(receipt.getOperationId(), restored.getOperationId());
                assertEquals(receipt.getState(), restored.getState());
                var original = restored.getIngredients().get(0);
                assertEquals(inputs.get(0).getObjectId().toHexString(), original.getObjectId());
                assertEquals(inputs.get(0).getGuid(), original.getGuid());
                assertEquals(21, original.getLevel());
                assertEquals(123, original.getExp());
                assertEquals(999, original.getTotalExp());
                assertEquals(111, original.getMainPropId());
                assertEquals(List.of(42), original.getAppendPropIdList());
                assertTrue(original.isRemoved());
                var output = restored.getOutputs().get(0);
                assertEquals(321, output.getMainPropId());
                assertEquals(List.of(7, 8), output.getAppendPropIdList());
                assertEquals(stage == 8, output.isDelivered());
                if (stage == 8) {
                    var actual = player.getInventory().getItemByGuid(output.getDeliveredGuid());
                    assertEquals(actual.getObjectId().toHexString(), output.getObjectId());
                    assertEquals(output.getObjectId(), output.getDeliveredObjectId());
                }
                player.setPendingStrongbox(restored);
                failSave = 0;
                int deletes = player.deletes, grants = player.grants, stored = outputs();
                manager = new CombineManger(environment.scene.getWorld().getServer());
                exchange(1);
                failed();
                assertEquals(deletes, player.deletes);
                assertEquals(grants, player.grants);
                assertEquals(stored, outputs());
            } finally {
                assertTrue(databaseName.startsWith("lunagc_test_strongbox161_"));
                client.getDatabase(databaseName).drop();
            }
        }
    }
}
