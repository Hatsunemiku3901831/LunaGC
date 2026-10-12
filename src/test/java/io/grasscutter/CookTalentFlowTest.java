package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.props.PlayerProperty;
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

/** Actual skill/depot/recipe rows, with controlled roll boundaries and durable delivery. */
class CookTalentFlowTest {
    private static List<CookRecipeData> recipes;
    private static List<ItemData> items;
    private static List<ProudSkillData> skills;
    private static List<AvatarSkillDepotData> depots;
    private AbilityTestFixture environment;
    private CookDeliveryFlowTest.CookPlayer player;
    private Avatar avatar;
    private GameServerPacketHandler dispatcher;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private double roll;
    private int rollCalls, saves, failSave;

    @BeforeAll
    static void resources() throws Exception {
        var excel = Path.of("/resources/ExcelBinOutput");
        recipes =
                JsonUtils.loadToList(
                                excel.resolve("CookRecipeExcelConfigData.json"),
                                CookRecipeData.class)
                        .stream()
                        .filter(r -> Set.of(1001, 2006, 2003, 2008).contains(r.getId()))
                        .toList();
        recipes.forEach(CookRecipeData::onLoad);
        var ids = new HashSet<Integer>();
        for (var recipe : recipes) {
            recipe.getInputVec().forEach(r -> ids.add(r.getId()));
            recipe.getQualityOutputVec().forEach(r -> ids.add(r.getId()));
        }
        items =
                JsonUtils.loadToList(excel.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(r -> ids.contains(r.getId()))
                        .toList();
        skills =
                JsonUtils.loadToList(
                                excel.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(
                                r ->
                                        r.getLifeEffectType() != null
                                                && r.getLifeEffectType().contains("COOK"))
                        .toList();
        depots =
                JsonUtils.loadToList(
                        excel.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
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
        player = new CookDeliveryFlowTest.CookPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        for (var recipe : recipes) {
            put(
                    GameData.getCookRecipeDataMap(),
                    recipe.getId(),
                    gson.fromJson(gson.toJson(recipe), CookRecipeData.class));
            player.getUnlockedRecipies().put(recipe.getId(), recipe.getMaxProficiency());
        }
        for (var item : items) put(GameData.getItemDataMap(), item.getId(), item);
        for (var original : skills) {
            var json = gson.toJsonTree(original).getAsJsonObject();
            json.remove("paramListMap");
            var skill = gson.fromJson(json, ProudSkillData.class);
            skill.onLoad();
            put(GameData.getProudSkillDataMap(), skill.getId(), skill);
        }
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
        var ingredientIds = new HashSet<Integer>();
        for (var recipe : recipes)
            recipe.getInputVec().stream()
                    .filter(r -> r.getId() > 0)
                    .forEach(r -> ingredientIds.add(r.getId()));
        for (int id : ingredientIds) assertTrue(player.getInventory().addItem(id, 100));
        var manager = emu.grasscutter.game.player.Player.class.getDeclaredField("cookingManager");
        manager.setAccessible(true);
        manager.set(
                player,
                new CookingManager(player) {
                    protected double getSpecialtyChance(ItemData data) {
                        return 0;
                    }

                    protected double nextCookingRoll() {
                        rollCalls++;
                        return roll;
                    }
                });
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

    private void actor(int id) throws Exception {
        var depot =
                depots.stream()
                        .filter(d -> d.getId() == (id - 10000000) * 100 + 1)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        avatar = new SkillTalentLevelTest.RecordingAvatar(player, id, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
        put(
                GameData.getCookBonusDataMap(),
                id,
                null); // Specialty conversion is a separate, unchanged rule.
    }

    private int recipeFor(int actor) {
        return switch (actor) {
            case 10000023 -> 2006;
            case 10000034, 10000044 -> 2008;
            case 10000064, 10000070 -> 2003;
            default -> 1001;
        };
    }

    private PlayerCookRsp cook(int recipe, int count, int quality, int actor) throws Exception {
        int before = player.packets.size();
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.PlayerCookReq,
                new byte[0],
                PlayerCookReq.newBuilder()
                        .setRecipeId(recipe)
                        .setKLACBPCPCMJ(count)
                        .setJJPABEHGMCH(quality)
                        .setAssistAvatar(actor)
                        .build()
                        .toByteArray());
        var packet =
                player.packets.subList(before, player.packets.size()).stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCookRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return PlayerCookRsp.parseFrom(packet.getData());
    }

    private int item(int recipe, int quality) {
        return GameData.getCookRecipeDataMap()
                .get(recipe)
                .getQualityOutputVec()
                .get(quality - 1)
                .getId();
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {10000003, 10000014, 10000023, 10000034, 10000039, 10000044, 10000064, 10000070})
    void everyActualPerfectCookPassiveMatchesItsFoodCategory(int actor) throws Exception {
        actor(actor);
        int recipe = recipeFor(actor);
        var result = cook(recipe, 1, 3, actor);
        assertEquals(0, result.getRetcode());
        assertEquals(2, stock(item(recipe, 3)));
        assertEquals(1, result.getItemList(0).getCount());
        assertEquals(1, result.getExtralItemListCount());
        assertEquals(item(recipe, 3), result.getExtralItemList(0).getItemId());
        assertEquals(1, result.getExtralItemList(0).getCount());
        assertEquals(1, player.progress);
        assertEquals(1, rollCalls);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void ordinaryAndSuspiciousQualityDoNotTriggerPerfectBonus(int quality) throws Exception {
        actor(10000003);
        assertEquals(0, cook(1001, 1, quality, 10000003).getRetcode());
        assertEquals(1, stock(item(1001, quality)));
        assertEquals(0, rollCalls);
    }

    @Test
    void mismatchedFoodCategoryDoesNotRoll() throws Exception {
        actor(10000003);
        assertEquals(0, cook(2006, 1, 3, 10000003).getRetcode());
        assertEquals(1, stock(item(2006, 3)));
        assertEquals(0, rollCalls);
    }

    @Test
    void exactProbabilityBoundaryDoesNotGrantExtra() throws Exception {
        actor(10000003);
        roll = 0.12;
        assertEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(1, rollCalls);
    }

    @Test
    void justBelowProbabilityBoundaryGrantsExtra() throws Exception {
        actor(10000003);
        roll = Math.nextDown(0.12);
        assertEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(2, stock(item(1001, 3)));
    }

    @Test
    void automaticCookingRollsPerDishAndOnlyCountsRequestedUnitsForProgress() throws Exception {
        actor(10000003);
        assertEquals(0, cook(1001, 7, 0, 10000003).getRetcode());
        assertEquals(14, stock(item(1001, 3)));
        assertEquals(7, player.progress);
        assertEquals(7, rollCalls);
    }

    @ParameterizedTest
    @ValueSource(ints = {10000046, 10000066})
    void actualOddQualityPassivesGiveOneSuspiciousDishInAddition(int actor) throws Exception {
        actor(actor);
        var result = cook(1001, 1, 3, actor);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getItemListCount());
        assertEquals(1, result.getExtralItemListCount());
        assertEquals(item(1001, 1), result.getExtralItemList(0).getItemId());
        assertEquals(1, result.getExtralItemList(0).getCount());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(1, stock(item(1001, 1)));
        assertEquals(1, player.progress);
        assertEquals(1, rollCalls);
    }

    @ParameterizedTest
    @ValueSource(ints = {1001, 2006, 2003, 2008})
    void oddQualityBonusAcceptsEveryConfiguredFoodCategory(int recipe) throws Exception {
        actor(10000046);
        assertEquals(0, cook(recipe, 1, 3, 10000046).getRetcode());
        assertEquals(1, stock(item(recipe, 1)));
        assertEquals(1, stock(item(recipe, 3)));
    }

    @Test
    void oddQualityBonusStillRequiresPerfectResult() throws Exception {
        actor(10000046);
        assertEquals(0, cook(1001, 1, 2, 10000046).getRetcode());
        assertEquals(0, stock(item(1001, 1)));
        assertEquals(0, rollCalls);
    }

    @Test
    void raidenCannotCookBeforeAnyIngredientPayment() throws Exception {
        actor(10000052);
        assertTrue(avatar.getProudSkillList().contains(522301));
        assertNotEquals(0, cook(1001, 1, 3, 10000052).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, player.payments);
    }

    @Test
    void passiveAbsentFromActiveListDoesNotGrantBonus() throws Exception {
        actor(10000003);
        avatar.getProudSkillList().remove(32301);
        assertEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(0, rollCalls);
    }

    @Test
    void forgedForeignSkillDoesNotGrantBonus() throws Exception {
        actor(10000046);
        avatar.getProudSkillList().clear();
        avatar.getProudSkillList().add(32301);
        assertEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(0, rollCalls);
    }

    @Test
    void trialAssistantCannotBeUsedForCooking() throws Exception {
        actor(10000003);
        avatar.setTrialAvatarId(1);
        assertNotEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(100, stock(100011));
    }

    @Test
    void malformedBonusParamsHaveNoEffect() throws Exception {
        actor(10000003);
        var skill = GameData.getProudSkillDataMap().get(32301);
        skill.getLifeEffectParams().set(0, "bad");
        assertEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(0, rollCalls);
    }

    @Test
    void invalidExtraItemTypeIsRejectedBeforeIngredientPayment() throws Exception {
        actor(10000046);
        int id = item(1001, 1);
        var data = gson.toJsonTree(GameData.getItemDataMap().get(id)).getAsJsonObject();
        data.addProperty("itemType", "ITEM_RELIQUARY");
        data.addProperty("appendPropNum", 0);
        GameData.getItemDataMap().put(id, gson.fromJson(data, ItemData.class));
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, player.payments);
    }

    @Test
    void fullBonusStackPreventsPrimaryItemAndIngredientDeduction() throws Exception {
        actor(10000046);
        assertTrue(player.getInventory().addItem(item(1001, 1), 2000));
        player.grants = 0;
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(0, stock(item(1001, 3)));
        assertEquals(0, player.progress);
    }

    @Test
    void insufficientRoomForDoubleOutputKeepsIngredients() throws Exception {
        actor(10000003);
        assertTrue(player.getInventory().addItem(item(1001, 3), 1999));
        player.grants = 0;
        assertNotEquals(0, cook(1001, 1, 3, 10000003).getRetcode());
        assertEquals(100, stock(100011));
        assertEquals(1999, stock(item(1001, 3)));
    }

    @Test
    void refusedBonusAfterPrimaryDeliveryCannotRerollOrReplay() throws Exception {
        actor(10000046);
        player.refuseNumber = 2;
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(1, stock(item(1001, 3)));
        assertEquals(0, player.progress);
        int rolls = rollCalls, ingredients = stock(100011);
        player.refuseNumber = 0;
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(rolls, rollCalls);
        assertEquals(ingredients, stock(100011));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 6})
    void bonusReceiptFailureNeverRerolls(int stage) throws Exception {
        actor(10000046);
        failSave = stage;
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        int rolls = rollCalls, primary = stock(item(1001, 3)), extra = stock(item(1001, 1));
        failSave = 0;
        assertNotEquals(0, cook(1001, 1, 3, 10000046).getRetcode());
        assertEquals(rolls, rollCalls);
        assertEquals(primary, stock(item(1001, 3)));
        assertEquals(extra, stock(item(1001, 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {10000003, 10000046})
    void extraClassificationSurvivesMongoAndOldMissingField(int actor) throws Exception {
        actor(actor);
        failSave = 5;
        assertNotEquals(0, cook(1001, 1, 3, actor).getRetcode());
        var receipt = player.getPendingCook();
        assertNotNull(receipt);
        @SuppressWarnings("unchecked")
        var extras =
                assertDoesNotThrow(
                        () ->
                                (List<emu.grasscutter.data.common.ItemParamData>)
                                        receipt.getClass()
                                                .getMethod("getExtraProducts")
                                                .invoke(receipt));
        assertEquals(1, extras.size());
        int extraId = item(1001, actor == 10000003 ? 3 : 1);
        assertEquals(extraId, extras.get(0).getId());
        assertEquals(1, extras.get(0).getCount());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_cookextra155_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(CookDeliveryFlowTest.StoredCook.class);
            try {
                var row = new CookDeliveryFlowTest.StoredCook();
                row.id = "pending";
                row.receipt = receipt;
                store.save(row);
                var loaded = store.find(CookDeliveryFlowTest.StoredCook.class).first().receipt;
                assertEquals(1, loaded.getExtraProducts().size());
                assertEquals(extraId, loaded.getExtraProducts().get(0).getId());
                assertEquals(2, loaded.getProducts().stream().mapToInt(r -> r.getCount()).sum());
                client.getDatabase(name)
                        .getCollection("cook_receipt_test")
                        .updateOne(
                                new org.bson.Document("_id", "pending"),
                                new org.bson.Document(
                                        "$unset",
                                        new org.bson.Document("receipt.extraProducts", "")));
                var old = store.find(CookDeliveryFlowTest.StoredCook.class).first().receipt;
                assertTrue(old.getExtraProducts().isEmpty());
                player.setPendingCook(old);
                failSave = 0;
                int rolls = rollCalls;
                assertNotEquals(0, cook(1001, 1, 3, actor).getRetcode());
                assertEquals(rolls, rollCalls);
            } finally {
                assertTrue(name.startsWith("lunagc_test_cookextra155_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
