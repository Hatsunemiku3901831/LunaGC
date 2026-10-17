package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CombineRspOuterClass.CombineRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

/** A durable combine attempt must block replay after any unconfirmed economic write. */
class CombineReceiptFlowTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private AbilityTestFixture environment;
    private ReceiptPlayer player;
    private CombineManger manager;
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private GuardedPlayerTestStore playerStore;
    private int saves, failSave;
    private boolean oldResin;

    static class ReceiptPlayer extends InvestigationRewardTest.ClaimPlayer {
        transient boolean pretendPayment, throwAfterPayment, pretendOutput, refusePayment;
        transient int refuseGrant, grants, payments;
        @dev.morphia.annotations.Transient Runnable afterPayment;

        @dev.morphia.annotations.Transient
        final transient Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean payItems(
                            Iterable<ItemParamData> costs, int count, ActionReason reason) {
                        payments++;
                        if (refusePayment) return false;
                        if (pretendPayment) return true;
                        boolean result = super.payItems(costs, count, reason);
                        if (result) {
                            var callback = afterPayment;
                            afterPayment = null;
                            if (callback != null) callback.run();
                            if (throwAfterPayment) throw new IllegalStateException("Payment fault");
                        }
                        return result;
                    }

                    @Override
                    public boolean addItem(int id, int count, ActionReason reason) {
                        if (reason == ActionReason.Combine
                                && GameData.getItemDataMap().get(id).getItemType()
                                        != ItemType.ITEM_WEAPON) {
                            if (++grants == refuseGrant) return false;
                            if (pretendOutput) return true;
                        }
                        return super.addItem(id, count, reason);
                    }

                    @Override
                    public boolean addItem(GameItem item, ActionReason reason) {
                        if (reason == ActionReason.Combine
                                && item.getItemType() == ItemType.ITEM_WEAPON) {
                            if (++grants == refuseGrant) return false;
                            if (pretendOutput) return true;
                        }
                        return super.addItem(item, reason);
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static class OtherReceiptPlayer extends ReceiptPlayer {
        @Override
        public int getUid() {
            return 10002;
        }
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new ReceiptPlayer();
        manager = new CombineManger(environment.scene.getWorld().getServer());
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.setMora(200);
        player.getUnlockedCombines().add(11001);
        oldResin = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        for (int id : List.of(112002, 112003))
            put(
                    GameData.getItemDataMap(),
                    id,
                    gson.fromJson(
                            "{id:"
                                    + id
                                    + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                            ItemData.class));
        put(
                GameData.getItemDataMap(),
                202,
                gson.fromJson("{id:202,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        put(
                GameData.getItemDataMap(),
                11101,
                gson.fromJson("{id:11101,itemType:'ITEM_WEAPON',rankLevel:3}", ItemData.class));
        var original = GameData.getCombineDataMap().get(11001);
        restore.add(
                () -> {
                    if (original == null) GameData.getCombineDataMap().remove(11001);
                    else GameData.getCombineDataMap().put(11001, original);
                });
        recipe(112003, 1, 3, 25);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        playerStore =
                new GuardedPlayerTestStore(
                        player,
                        () -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Confirmation fault");
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
                                    throw new IllegalStateException("Confirmation fault");
                                return method.getName().equals("delete")
                                        ? com.mongodb.client.result.DeleteResult.acknowledged(1)
                                        : args[0];
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
        assertTrue(player.getInventory().addItem(112002, 12));
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (datastore != null) datastore.set(null, oldDatastore);
        if (playerStore != null) playerStore.close();
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        environment.close();
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResin;
    }

    private void recipe(int output, int outputCount, int input, int mora) {
        GameData.getCombineDataMap()
                .put(
                        11001,
                        gson.fromJson(
                                "{combineId:11001,playerLevel:1,resultItemId:"
                                        + output
                                        + ",resultItemCount:"
                                        + outputCount
                                        + ",scoinCost:"
                                        + mora
                                        + ",materialItems:[{id:112002,count:"
                                        + input
                                        + "}],randomItems:[]}",
                                CombineData.class));
    }

    private CombineResult combine(int count) {
        return assertDoesNotThrow(() -> manager.combineItem(player, 11001, count));
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private Object pending() {
        return assertDoesNotThrow(
                () -> player.getClass().getMethod("getPendingCombine").invoke(player));
    }

    private int state(Object receipt) {
        return assertDoesNotThrow(
                () -> (int) receipt.getClass().getMethod("getState").invoke(receipt));
    }

    private int error() throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CombineRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return CombineRsp.parseFrom(packet.getData()).getRetcode();
    }

    @Test
    void successfulMaterialCombineConfirmsCostsAndClearsReceipt() {
        var result = combine(2);
        assertNotNull(result);
        assertEquals(6, stock(112002));
        assertEquals(2, stock(112003));
        assertEquals(150, player.getMora());
        assertNull(pending());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void everyConfirmationFailureBlocksReplayAndAnotherManager(int stage) throws Exception {
        failSave = stage;
        assertNull(combine(1));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, error());
        assertNotNull(pending());
        int input = stock(112002),
                output = stock(112003),
                mora = player.getMora(),
                payments = player.payments;
        failSave = 0;
        manager = new CombineManger(environment.scene.getWorld().getServer());
        assertNull(combine(1));
        assertEquals(input, stock(112002));
        assertEquals(output, stock(112003));
        assertEquals(mora, player.getMora());
        assertEquals(payments, player.payments);
    }

    @Test
    void paymentExceptionAfterDeductionRetainsReservedSnapshot() {
        player.throwAfterPayment = true;
        assertNull(combine(1));
        assertEquals(9, stock(112002));
        assertEquals(175, player.getMora());
        assertEquals(1, state(pending()));
        player.throwAfterPayment = false;
        assertNull(combine(1));
        assertEquals(9, stock(112002));
    }

    @Test
    void paymentClaimedButNotDeductedNeverProducesAnItem() {
        player.pretendPayment = true;
        assertNull(combine(1));
        assertEquals(12, stock(112002));
        assertEquals(0, stock(112003));
        assertNotNull(pending());
    }

    @Test
    void refusedOutputKeepsPaidSnapshotAndCannotSpendAgain() {
        player.refuseGrant = 1;
        assertNull(combine(1));
        assertEquals(2, state(pending()));
        int input = stock(112002);
        player.refuseGrant = 0;
        assertNull(combine(1));
        assertEquals(input, stock(112002));
        assertEquals(0, stock(112003));
    }

    @Test
    void pretendOutputSuccessIsDetectedAndBlocked() {
        player.pretendOutput = true;
        assertNull(combine(1));
        assertEquals(0, stock(112003));
        assertEquals(2, state(pending()));
    }

    @Test
    void callbackReentryDoesNotCreateASecondPayment() {
        var outcomes = new ArrayList<Boolean>();
        player.afterPayment = () -> outcomes.add(manager.combineItem(player, 11001, 1) != null);
        assertNotNull(combine(1));
        assertEquals(List.of(false), outcomes);
        assertEquals(9, stock(112002));
        assertEquals(1, stock(112003));
        assertEquals(1, player.payments);
    }

    @Test
    void missingMaterialDefinitionIsRejectedBeforeReservation() {
        GameData.getItemDataMap().remove(112002);
        assertNull(combine(1));
        assertNull(pending());
        assertEquals(200, player.getMora());
        assertEquals(0, player.payments);
    }

    @Test
    void unknownVirtualOutputIsRejectedBeforePayment() {
        put(
                GameData.getItemDataMap(),
                999999,
                gson.fromJson("{id:999999,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        recipe(999999, 1, 3, 25);
        assertNull(combine(1));
        assertEquals(12, stock(112002));
        assertEquals(200, player.getMora());
        assertNull(pending());
    }

    @Test
    void virtualMoraGrantIsConfirmedFromActualProperty() {
        recipe(202, 10, 3, 25);
        assertNotNull(combine(1));
        assertEquals(185, player.getMora());
        assertNull(pending());
    }

    @Test
    void virtualMoraPretendGrantDoesNotCountAsDelivery() {
        recipe(202, 10, 3, 25);
        player.pretendOutput = true;
        assertNull(combine(1));
        assertEquals(175, player.getMora());
        assertNotNull(pending());
    }

    @Test
    void weaponsConfirmDistinctOwnedIdentities() {
        recipe(11101, 3, 3, 25);
        assertNotNull(combine(1));
        var guids = new HashSet<Long>();
        for (var item : player.getInventory())
            if (item.getItemId() == 11101) {
                assertEquals(player.getUid(), item.getOwnerId());
                assertEquals(1, item.getCount());
                assertTrue(guids.add(item.getGuid()));
            }
        assertEquals(3, guids.size());
        assertNull(pending());
    }

    @Test
    void partialWeaponGrantCannotRestartTheBatch() {
        recipe(11101, 3, 3, 25);
        player.refuseGrant = 2;
        assertNull(combine(1));
        assertEquals(1, stock(11101));
        int input = stock(112002);
        player.refuseGrant = 0;
        assertNull(combine(1));
        assertEquals(1, stock(11101));
        assertEquals(input, stock(112002));
    }

    @Test
    void falseWeaponGrantCannotAdvanceToCompleted() {
        recipe(11101, 1, 3, 25);
        player.pretendOutput = true;
        assertNull(combine(1));
        assertNotNull(pending());
        assertEquals(0, stock(11101));
    }

    @Test
    void deletedIngredientStackIsAcknowledgedBeforeGrant() {
        recipe(112003, 1, 12, 25);
        assertNotNull(combine(1));
        assertEquals(0, stock(112002));
        assertEquals(1, stock(112003));
        assertNull(pending());
    }

    @Test
    void sameOutputStackAfterPaymentConfirmsNetStock() {
        recipe(112002, 3, 3, 0);
        assertNotNull(combine(1));
        assertEquals(12, stock(112002));
        assertNull(pending());
    }

    @Test
    void noCostRecipeStillConfirmsAndClearsReceipt() {
        recipe(112003, 1, 0, 0);
        assertNotNull(combine(1));
        assertEquals(12, stock(112002));
        assertEquals(1, stock(112003));
        assertNull(pending());
    }

    @Test
    void fullOutputStackLeavesNoEconomicReservation() {
        assertTrue(player.getInventory().addItem(112003, 1000));
        assertNull(combine(1));
        assertEquals(12, stock(112002));
        assertNull(pending());
    }

    @Test
    void insufficientStockLeavesNoReservation() {
        recipe(112003, 1, 13, 25);
        assertNull(combine(1));
        assertEquals(0, player.payments);
        assertNull(pending());
    }

    @Test
    void anotherPlayerCanCombineWhileThisPlayerHasAPendingReceipt() throws Exception {
        player.refuseGrant = 1;
        assertNull(combine(1));
        var other = new OtherReceiptPlayer();
        other.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), other));
        other.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        other.setMora(200);
        other.getUnlockedCombines().add(11001);
        try (var otherStore = new GuardedPlayerTestStore(other, () -> {})) {
            try {
                assertTrue(other.getInventory().addItem(112002, 12));
                assertNotNull(manager.combineItem(other, 11001, 1));
                assertEquals(1, other.getInventory().getItemCountById(112003));
                assertNotNull(pending());
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            }
        }
    }

    @Test
    void corruptNonemptyReceiptDoesNotPermitAnotherPayment() throws Exception {
        failSave = 1;
        assertNull(combine(1));
        var receipt = pending();
        assertNotNull(receipt);
        var field = receipt.getClass().getDeclaredField("state");
        field.setAccessible(true);
        field.setInt(receipt, 99);
        failSave = 0;
        int payments = player.payments;
        assertNull(combine(1));
        assertEquals(payments, player.payments);
        assertEquals(12, stock(112002));
    }

    @Test
    void virtualOverflowIsCheckedAfterEffectivePrice() {
        recipe(202, 25, 0, 25);
        player.setMora(Integer.MAX_VALUE);
        assertNotNull(combine(1));
        assertEquals(Integer.MAX_VALUE, player.getMora());
        assertNull(pending());
    }

    @Test
    void disabledResinOutputDoesNotConsumeIngredients() {
        put(
                GameData.getItemDataMap(),
                106,
                gson.fromJson("{id:106,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        recipe(106, 1, 3, 25);
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        assertNull(combine(1));
        assertEquals(12, stock(112002));
        assertNull(pending());
    }

    @Test
    void disabledResinCostIsOmittedFromEffectivePaymentAndReply() {
        put(
                GameData.getItemDataMap(),
                106,
                gson.fromJson("{id:106,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        GameData.getCombineDataMap()
                .put(
                        11001,
                        gson.fromJson(
                                "{combineId:11001,playerLevel:1,resultItemId:112003,resultItemCount:1,scoinCost:25,materialItems:[{id:112002,count:3},{id:106,count:10}],randomItems:[]}",
                                CombineData.class));
        var result = combine(1);
        assertNotNull(result);
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(175, player.getMora());
        assertEquals(9, stock(112002));
        assertTrue(result.getMaterial().stream().noneMatch(item -> item.getId() == 106));
        assertNull(pending());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATERIAL_AVATAR_TRACE", "MATERIAL_WEAPON_SKIN"})
    void cosmeticUnlockOutputsAreRejectedBeforePayment(String materialType) {
        put(
                GameData.getItemDataMap(),
                112003,
                gson.fromJson(
                        "{id:112003,itemType:'ITEM_MATERIAL',materialType:'"
                                + materialType
                                + "',stackLimit:1000}",
                        ItemData.class));
        assertNull(combine(1));
        assertEquals(12, stock(112002));
        assertEquals(200, player.getMora());
        assertNull(pending());
    }

    @dev.morphia.annotations.Entity(value = "combine_receipt_test", useDiscriminator = false)
    static class StoredCombine {
        @dev.morphia.annotations.Id String id;
        emu.grasscutter.game.combine.CombineReceipt receipt;
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void receiptWithCostsAndDeliveryIdentitiesSurvivesMongo(int stage) throws Exception {
        failSave = stage;
        assertNull(combine(1));
        var raw = pending();
        assertNotNull(raw);
        var receipt = (emu.grasscutter.game.combine.CombineReceipt) raw;
        assertEquals(11001, receipt.getRecipeId());
        assertEquals(1, receipt.getUnits());
        assertEquals(112003, receipt.getOutputId());
        assertEquals(2, receipt.getCosts().size());
        assertEquals(12, receipt.getCosts().get(0).getBefore());
        assertEquals(3, receipt.getCosts().get(0).getCount());
        assertEquals(200, receipt.getCosts().get(1).getBefore());
        assertEquals(stage == 2 ? 1 : 3, receipt.getState());
        if (stage == 6) {
            assertEquals(1, receipt.getDeliveries().size());
            assertEquals(
                    player.getInventory().getItemById(112003).getGuid(),
                    receipt.getDeliveries().get(0).getGuid());
        }
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_combine157_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredCombine.class);
            try {
                var row = new StoredCombine();
                row.id = "pending";
                row.receipt = receipt;
                store.save(row);
                var loaded = store.find(StoredCombine.class).first().receipt;
                assertEquals(receipt.getOperationId(), loaded.getOperationId());
                assertEquals(receipt.getState(), loaded.getState());
                assertEquals(receipt.getDeliveries().size(), loaded.getDeliveries().size());
                assertEquals(200, loaded.getCosts().get(1).getBefore());
                var setter = player.getClass().getMethod("setPendingCombine", loaded.getClass());
                setter.invoke(player, loaded);
                failSave = 0;
                manager = new CombineManger(environment.scene.getWorld().getServer());
                int input = stock(112002), result = stock(112003);
                assertNull(combine(1));
                assertEquals(input, stock(112002));
                assertEquals(result, stock(112003));
            } finally {
                assertTrue(name.startsWith("lunagc_test_combine157_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
