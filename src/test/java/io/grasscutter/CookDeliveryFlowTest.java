package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.PlayerCookRspOuterClass.PlayerCookRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Cooking must validate and confirm the actual dish batch before progress or success. */
class CookDeliveryFlowTest {
    private static CookRecipeData actualRecipe;
    private static List<ItemData> actualItems;
    private AbilityTestFixture environment;
    private CookPlayer player;
    private GameServerPacketHandler dispatcher;
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private int saves, failSave;

    static class CookPlayer extends InvestigationRewardTest.ClaimPlayer {
        int progress, payments, grants, refuseNumber;
        boolean refusePay, pretend, throwAfterPay;
        Runnable afterPay;
        final BattlePassManager pass =
                new BattlePassManager(this) {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int amount) {
                        if (type == WatcherTriggerType.TRIGGER_DO_COOK) progress += amount;
                    }
                };
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean payItems(
                            Iterable<ItemParamData> costs, int quantity, ActionReason reason) {
                        payments++;
                        if (refusePay) return false;
                        boolean result = super.payItems(costs, quantity, reason);
                        if (result) {
                            var callback = afterPay;
                            afterPay = null;
                            if (callback != null) callback.run();
                            if (throwAfterPay) throw new IllegalStateException("Payment fault");
                        }
                        return result;
                    }

                    @Override
                    public boolean addItem(GameItem item) {
                        if (item.getItemId() >= 108011 && item.getItemId() <= 108014) {
                            if (++grants == refuseNumber) return false;
                            if (pretend) return true;
                        }
                        return super.addItem(item);
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return pass;
        }
    }

    @BeforeAll
    static void resources() throws Exception {
        var excel = Path.of("/resources/ExcelBinOutput");
        actualRecipe =
                JsonUtils.loadToList(
                                excel.resolve("CookRecipeExcelConfigData.json"),
                                CookRecipeData.class)
                        .stream()
                        .filter(row -> row.getId() == 1001)
                        .findFirst()
                        .orElseThrow();
        actualRecipe.onLoad();
        actualItems =
                JsonUtils.loadToList(excel.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(
                                row ->
                                        Set.of(100011, 100064, 108011, 108012, 108013, 108014)
                                                .contains(row.getId()))
                        .toList();
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
        player = new CookPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        put(
                GameData.getCookRecipeDataMap(),
                1001,
                gson.fromJson(gson.toJson(actualRecipe), CookRecipeData.class));
        for (var item : actualItems) put(GameData.getItemDataMap(), item.getId(), item);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
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
        assertTrue(player.getInventory().addItem(100011, 100));
        assertTrue(player.getInventory().addItem(100064, 100));
        player.getUnlockedRecipies().put(1001, 5);
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        environment.close();
    }

    private PlayerCookRsp cook(int quantity, int quality, int assistant) throws Exception {
        int before = player.packets.size();
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.PlayerCookReq,
                new byte[0],
                PlayerCookReq.newBuilder()
                        .setRecipeId(1001)
                        .setKLACBPCPCMJ(quantity)
                        .setJJPABEHGMCH(quality)
                        .setAssistAvatar(assistant)
                        .build()
                        .toByteArray());
        return responseAfter(before);
    }

    private PlayerCookRsp responseAfter(int before) throws Exception {
        var packet =
                player.packets.subList(before, player.packets.size()).stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCookRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow(() -> new AssertionError("Cooking must always answer"));
        return PlayerCookRsp.parseFrom(packet.getData());
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private void recipe(String overrides) {
        var json = gson.toJsonTree(actualRecipe).getAsJsonObject();
        JsonParser.parseString(overrides)
                .getAsJsonObject()
                .entrySet()
                .forEach(e -> json.add(e.getKey(), e.getValue()));
        GameData.getCookRecipeDataMap().put(1001, gson.fromJson(json, CookRecipeData.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void manualQualityGivesItsActualTierAndCountsProgress(int quality) throws Exception {
        player.getUnlockedRecipies().put(1001, 0);
        var result = cook(1, quality, 0);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getItemListCount());
        assertEquals(108010 + quality, result.getItemList(0).getItemId());
        assertEquals(1, result.getItemList(0).getCount());
        assertEquals(99, stock(100011));
        assertEquals(99, stock(100064));
        assertEquals(1, player.progress);
        assertEquals(quality == 3 ? 1 : 0, player.getUnlockedRecipies().get(1001));
    }

    @Test
    void autoCookReportsPerfectQualityAndActualSliderCount() throws Exception {
        var result = cook(7, 0, 0);
        assertEquals(0, result.getRetcode());
        assertEquals(3, result.getQteQuality());
        assertEquals(7, result.getCookCount());
        assertEquals(7, result.getItemList(0).getCount());
        assertEquals(93, stock(100011));
        assertEquals(7, player.progress);
        assertEquals(5, player.getUnlockedRecipies().get(1001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100, Integer.MAX_VALUE})
    void invalidQuantityDoesNotSpendOrCreateProgress(int quantity) throws Exception {
        assertNotEquals(0, cook(quantity, 0, 0).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, player.progress);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4, Integer.MAX_VALUE})
    void invalidQualityAlwaysAnswersBeforePayment(int quality) throws Exception {
        assertNotEquals(0, cook(1, quality, 0).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, player.progress);
    }

    @Test
    void manualBatchIsRejectedBeforePayment() throws Exception {
        assertNotEquals(0, cook(2, 3, 0).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void autoCookRequiresCompletedProficiency() throws Exception {
        player.getUnlockedRecipies().put(1001, 4);
        assertNotEquals(0, cook(2, 0, 0).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void lockedRecipeCannotBeCooked() throws Exception {
        recipe("{isDefaultUnlocked:false}");
        player.getUnlockedRecipies().clear();
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void defaultRecipeIsAvailableBeforeFirstCookingNotify() throws Exception {
        player.getUnlockedRecipies().clear();
        assertEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(1, player.getUnlockedRecipies().get(1001));
    }

    @Test
    void unknownAssistantIsRejectedBeforePayment() throws Exception {
        assertNotEquals(0, cook(1, 3, 10000015).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void missingOutputDoesNotConsumeIngredients() throws Exception {
        GameData.getItemDataMap().remove(108013);
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void fullOutputStackPreservesCostsAndProficiency() throws Exception {
        assertTrue(player.getInventory().addItem(108013, 2000));
        player.grants = 0;
        assertNotEquals(0, cook(2, 0, 0).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, player.progress);
        assertEquals(2000, stock(108013));
    }

    @Test
    void overflowingOutputQuantityDoesNotCharge() throws Exception {
        recipe(
                "{qualityOutputVec:[{id:108011,count:1},{id:108012,count:1},{id:108013,count:2147483647}]}");
        assertNotEquals(0, cook(2, 0, 0).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void duplicateIngredientRowsAreSummedBeforeCheckingStock() throws Exception {
        recipe("{inputVec:[{id:100011,count:70},{id:100011,count:40},{id:0,count:0}]}");
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, stock(108013));
    }

    @Test
    void missingQualitySlotUsesExistingFilledTier() throws Exception {
        recipe("{qualityOutputVec:[{id:108011,count:1},{id:0,count:0},{id:0,count:0}]}");
        assertEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(1, stock(108011));
    }

    @Test
    void refusedPaymentDoesNotGrantDish() throws Exception {
        player.refusePay = true;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(0, stock(108013));
        assertEquals(100, stock(100011));
    }

    @Test
    void refusedOutputCannotAdvanceProficiencyOrBeRetried() throws Exception {
        player.getUnlockedRecipies().put(1001, 0);
        player.refuseNumber = 1;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(0, player.progress);
        assertEquals(0, player.getUnlockedRecipies().get(1001));
        int ingredients = stock(100011);
        player.refuseNumber = 0;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(ingredients, stock(100011));
        assertEquals(0, stock(108013));
    }

    @Test
    void pretendOutputSuccessIsDetected() throws Exception {
        player.pretend = true;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(0, player.progress);
        assertEquals(0, stock(108013));
    }

    @Test
    void paymentExceptionAfterDeductionBlocksRepeat() throws Exception {
        player.throwAfterPay = true;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        int remaining = stock(100011);
        player.throwAfterPay = false;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(remaining, stock(100011));
    }

    @Test
    void reentrantCookingCannotSpendOrGrantTwice() throws Exception {
        var codes = new ArrayList<Integer>();
        player.afterPay =
                () -> {
                    try {
                        codes.add(cook(1, 3, 0).getRetcode());
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                };
        assertEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(1, codes.size());
        assertNotEquals(0, codes.get(0));
        assertEquals(99, stock(100011));
        assertEquals(1, stock(108013));
        assertEquals(1, player.progress);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void acknowledgementFailureBlocksFurtherCookingWithoutReplay(int stage) throws Exception {
        failSave = stage;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        int before = stock(100011), dish = stock(108013), progress = player.progress;
        failSave = 0;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        assertEquals(before, stock(100011));
        assertEquals(dish, stock(108013));
        assertEquals(progress, player.progress);
    }

    @Test
    void malformedRequestStillGetsAnErrorResponse() throws Exception {
        int before = player.packets.size();
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.PlayerCookReq,
                new byte[0],
                new byte[] {(byte) 0x80});
        assertNotEquals(0, responseAfter(before).getRetcode());
        assertEquals(100, stock(100011));
    }

    private void assistant(double chance) throws Exception {
        // Keep the previous implementation's private rank-based chance at zero as well;
        // the old-package comparison must not depend on an uncontrolled random draw.
        var dish = gson.toJsonTree(GameData.getItemDataMap().get(108013)).getAsJsonObject();
        dish.addProperty("rankLevel", 4);
        GameData.getItemDataMap().put(108013, gson.fromJson(dish, ItemData.class));
        var depot =
                gson.fromJson(
                        "{id:1501,skills:[],talents:[],inherentProudSkillOpens:[]}",
                        emu.grasscutter.data.excels.avatar.AvatarSkillDepotData.class);
        depot.onLoad();
        Avatar actor = new SkillTalentLevelTest.RecordingAvatar(player, 10000015, depot);
        assertTrue(player.getAvatars().addAvatar(actor));
        put(
                GameData.getCookBonusDataMap(),
                10000015,
                gson.fromJson(
                        "{avatarId:10000015,recipeId:1001,paramVec:[108014,0]}",
                        CookBonusData.class));
        var field = emu.grasscutter.game.player.Player.class.getDeclaredField("cookingManager");
        field.setAccessible(true);
        field.set(
                player,
                new CookingManager(player) {
                    protected double getSpecialtyChance(ItemData item) {
                        return chance;
                    }
                });
    }

    @Test
    void allSpecialtyRollsDoNotCreateAZeroCountNormalDish() throws Exception {
        assistant(1);
        var result = cook(3, 0, 10000015);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getItemListCount());
        assertEquals(108014, result.getItemList(0).getItemId());
        assertEquals(3, result.getItemList(0).getCount());
        assertEquals(0, stock(108013));
        assertEquals(3, stock(108014));
    }

    @Test
    void zeroSpecialtyChanceKeepsAllNormalDishes() throws Exception {
        assistant(0);
        var result = cook(3, 0, 10000015);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getItemListCount());
        assertEquals(3, stock(108013));
        assertEquals(0, stock(108014));
    }

    @Test
    void missingSpecialtyDefinitionIsRejectedBeforePayment() throws Exception {
        assistant(1);
        GameData.getItemDataMap().remove(108014);
        assertNotEquals(0, cook(3, 0, 10000015).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void fullSpecialtyStackKeepsIngredients() throws Exception {
        assistant(1);
        assertTrue(player.getInventory().addItem(108014, 2000));
        player.grants = 0;
        assertNotEquals(0, cook(3, 0, 10000015).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, stock(108013));
    }

    private Object pending() {
        return assertDoesNotThrow(
                () -> player.getClass().getMethod("getPendingCook").invoke(player));
    }

    @dev.morphia.annotations.Entity(value = "cook_receipt_test", useDiscriminator = false)
    static class StoredCook {
        @dev.morphia.annotations.Id String id;
        emu.grasscutter.game.managers.cooking.CookReceipt receipt;
    }

    @Test
    void failedCookSnapshotSurvivesMongoAndManagerReplacement() throws Exception {
        failSave = 5;
        assertNotEquals(0, cook(1, 3, 0).getRetcode());
        var receipt = pending();
        assertNotNull(receipt);
        var value = (emu.grasscutter.game.managers.cooking.CookReceipt) receipt;
        assertEquals(1001, value.getRecipeId());
        assertEquals(1, value.getCount());
        assertEquals(2, value.getState());
        assertEquals(2, value.getCosts().size());
        assertEquals(108013, value.getProducts().get(0).getId());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_cook153_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredCook.class);
            try {
                var row = new StoredCook();
                row.id = "pending";
                row.receipt = value;
                store.save(row);
                var loaded = store.find(StoredCook.class).first().receipt;
                assertEquals(2, loaded.getState());
                assertEquals(1, loaded.getProducts().get(0).getCount());
                var setter = player.getClass().getMethod("setPendingCook", loaded.getClass());
                setter.invoke(player, loaded);
                var manager =
                        emu.grasscutter.game.player.Player.class.getDeclaredField("cookingManager");
                manager.setAccessible(true);
                manager.set(player, new CookingManager(player));
                int stock = stock(100011), dish = stock(108013);
                failSave = 0;
                assertNotEquals(0, cook(1, 3, 0).getRetcode());
                assertEquals(stock, stock(100011));
                assertEquals(dish, stock(108013));
            } finally {
                assertTrue(name.startsWith("lunagc_test_cook153_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
