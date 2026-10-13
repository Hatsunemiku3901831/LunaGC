package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class FurnitureTalentPlanTest {
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private AbilityTestFixture environment;
    private FurnitureStartFlowTest.CraftPlayer player;
    private Avatar avatar;
    private Field datastore;
    private Object oldStore;
    private Map types;
    private static final Path EXCEL = Path.of("/resources/ExcelBinOutput");
    private static List<ProudSkillData> skills;
    private static List<AvatarSkillDepotData> depots;
    private static JsonArray typeRows;

    @BeforeAll
    static void sourceRows() throws Exception {
        var gson = new Gson();
        skills =
                Arrays.stream(
                                gson.fromJson(
                                        Files.readString(
                                                EXCEL.resolve("ProudSkillExcelConfigData.json")),
                                        ProudSkillData[].class))
                        .filter(s -> s.getId() == 492301 || s.getId() == 812301)
                        .toList();
        skills.forEach(ProudSkillData::onLoad);
        depots =
                Arrays.asList(
                        gson.fromJson(
                                Files.readString(
                                        EXCEL.resolve("AvatarSkillDepotExcelConfigData.json")),
                                AvatarSkillDepotData[].class));
        typeRows =
                JsonParser.parseString(
                                Files.readString(
                                        EXCEL.resolve(
                                                "HomeWorldFurnitureTypeExcelConfigData.json")))
                        .getAsJsonArray();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new FurnitureStartFlowTest.CraftPlayer();
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
        clear(GameData.getProudSkillDataMap());
        clear(GameData.getAvatarSkillDepotDataMap());
        try {
            types = (Map) GameData.class.getMethod("getFurnitureTypeDataMap").invoke(null);
            clear(types);
        } catch (NoSuchMethodException old) {
            types = new HashMap<>();
        }
        for (var skill : skills) GameData.getProudSkillDataMap().put(skill.getId(), skill);
        for (var row : typeRows) categoryRow(row.toString());
        item(360101, "ITEM_FURNITURE", List.of(1001, 0));
        item(101311, "ITEM_MATERIAL", null);
        item(101312, "ITEM_MATERIAL", null);
        recipe(8, 4);
        player.getUnlockedFurniture().add(360101);
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
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
        assertTrue(player.inventory.addItem(101312, 100));
        assistant(492301);
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

    @SuppressWarnings("unchecked")
    void categoryRow(String json) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("emu.grasscutter.data.excels.FurnitureTypeData");
        } catch (ClassNotFoundException old) {
            return;
        }
        var data = (GameResource) gson.fromJson(json, type);
        types.put(data.getId(), data);
    }

    void assistant(int passive) {
        var existing = player.getAvatars().getAvatarById(passive == 492301 ? 10000049 : 10000081);
        if (existing != null) {
            avatar = existing;
            return;
        }
        var depot =
                depots.stream()
                        .filter(d -> d.getId() == (passive == 492301 ? 4901 : 8101))
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        GameData.getAvatarSkillDepotDataMap().put(depot.getId(), depot);
        avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        player, 10000000 + depot.getId() / 100, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
        assertTrue(avatar.getProudSkillList().contains(passive));
    }

    void item(int id, String kind, List<Integer> categories) {
        var row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("itemType", kind);
        row.addProperty("stackLimit", 9999);
        if (categories != null) row.add("furnType", gson.toJsonTree(categories));
        var data = gson.fromJson(row, ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(id, data);
    }

    void recipe(int a, int b) {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[{id:101311,count:"
                                        + a
                                        + "},{id:101312,count:"
                                        + b
                                        + "}]}",
                                FurnitureMakeConfigData.class));
    }

    FurnitureMakeSlotItem start(int actor) throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        player.getFurnitureManager().startMake(38, actor);
        var replies = session.responses.stream().filter(p -> p.getOpcode() == 21056).toList();
        assertEquals(1, replies.size());
        assertEquals(0, FurnitureMakeStartRsp.parseFrom(replies.get(0).getData()).getRetcode());
        var jobs = player.home.getFurnitureMakeSlotItemList();
        return jobs.get(jobs.size() - 1);
    }

    Map<Integer, Integer> returns(FurnitureMakeSlotItem slot) throws Exception {
        List<?> values = List.of();
        try {
            values =
                    (List<?>)
                            slot.getPlan()
                                    .getClass()
                                    .getMethod("getReturns")
                                    .invoke(slot.getPlan());
        } catch (NoSuchMethodException old) {
        }
        var result = new LinkedHashMap<Integer, Integer>();
        for (var value : values)
            result.put(
                    (Integer) value.getClass().getMethod("getId").invoke(value),
                    (Integer) value.getClass().getMethod("getCount").invoke(value));
        return result;
    }

    void params(String chance, String categories, String rate) {
        var row = gson.toJsonTree(GameData.getProudSkillDataMap().get(492301)).getAsJsonObject();
        row.remove("paramListMap");
        row.add(
                "lifeEffectParams",
                gson.toJsonTree(Arrays.asList(chance, categories, rate, "", "")));
        GameData.getProudSkillDataMap().put(492301, gson.fromJson(row, ProudSkillData.class));
    }

    @Test
    void actualYoimiyaPassiveFreezesTwoMaterialReturns() throws Exception {
        assertEquals(Map.of(101311, 2, 101312, 1), returns(start(avatar.getAvatarId())));
    }

    @Test
    void actualKavehPassiveMatchesExteriorBuildings() throws Exception {
        assistant(812301);
        item(360101, "ITEM_FURNITURE", List.of(6005, 0));
        assertEquals(Map.of(101311, 2, 101312, 1), returns(start(avatar.getAvatarId())));
    }

    @Test
    void nonmatchingAssistantCategoryHasNoReturns() throws Exception {
        assistant(812301);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void zeroAssistantHasNoReturns() throws Exception {
        assertTrue(returns(start(0)).isEmpty());
    }

    @Test
    void inactivePassiveCannotGrantReturns() throws Exception {
        avatar.getProudSkillList().remove(492301);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void trialAvatarCannotGrantPermanentReturns() throws Exception {
        avatar.setTrialAvatarId(1);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void missingSkillDefinitionHasNoReturns() throws Exception {
        GameData.getProudSkillDataMap().remove(492301);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void forgedForeignProudGroupCannotGrantReturns() throws Exception {
        var row = gson.toJsonTree(GameData.getProudSkillDataMap().get(492301)).getAsJsonObject();
        row.remove("paramListMap");
        row.addProperty("proudSkillGroupId", 8123);
        GameData.getProudSkillDataMap().put(492301, gson.fromJson(row, ProudSkillData.class));
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void effectAbovePromoteLevelHasNoReturns() throws Exception {
        var row = gson.toJsonTree(GameData.getProudSkillDataMap().get(492301)).getAsJsonObject();
        row.remove("paramListMap");
        row.addProperty("breakLevel", 1);
        GameData.getProudSkillDataMap().put(492301, gson.fromJson(row, ProudSkillData.class));
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void missingFurnitureCategoriesHaveNoReturns() throws Exception {
        item(360101, "ITEM_FURNITURE", null);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void unknownCategoryDoesNotUseIdDivisionAsAFallback() throws Exception {
        item(360101, "ITEM_FURNITURE", List.of(1999));
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void explicitCategoryMappingOverridesNumericIdAppearance() throws Exception {
        categoryRow("{typeID:1999,typeCategoryID:9}");
        item(360101, "ITEM_FURNITURE", List.of(1999));
        assertEquals(Map.of(101311, 2, 101312, 1), returns(start(avatar.getAvatarId())));
    }

    @Test
    void categoriesCanBeSharedByBothAssistants() throws Exception {
        item(360101, "ITEM_FURNITURE", List.of(7001));
        assertFalse(returns(start(avatar.getAvatarId())).isEmpty());
        assistant(812301);
        assertFalse(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void returnedMaterialsAreNotGrantedAtStart() throws Exception {
        assertEquals(Map.of(101311, 2, 101312, 1), returns(start(avatar.getAvatarId())));
        assertEquals(92, player.inventory.getItemCountById(101311));
        assertEquals(96, player.inventory.getItemCountById(101312));
    }

    @Test
    void ratesRoundDownPerMaterialAndPerJob() throws Exception {
        recipe(7, 3);
        assertEquals(Map.of(101311, 1), returns(start(avatar.getAvatarId())));
    }

    @Test
    void duplicateMaterialCostsAreAggregatedBeforeRounding() throws Exception {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[{id:101311,count:3},{id:101311,count:1}]}",
                                FurnitureMakeConfigData.class));
        assertEquals(Map.of(101311, 1), returns(start(avatar.getAvatarId())));
    }

    @Test
    void duplicateActivePassiveDoesNotStack() throws Exception {
        avatar.getProudSkillList().add(492301);
        assertEquals(Map.of(101311, 2, 101312, 1), returns(start(avatar.getAvatarId())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-0.25", "1.01", "0.25001", ""})
    void invalidReturnRateHasNoEffect(String rate) throws Exception {
        params("10000", "1", rate);
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9999", "10001", "bad"})
    void nonGuaranteedOrInvalidChanceIsNotTreatedAsGuaranteed(String chance) throws Exception {
        params(chance, "1", "0.25");
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,bad", "bad,1", "1,-1", "1,0", ""})
    void completeCategoryListMustBeValid(String categories) throws Exception {
        params("10000", categories, "0.25");
        assertTrue(returns(start(avatar.getAvatarId())).isEmpty());
    }

    @Test
    void plannedReturnsSurviveResourceAndAvatarChanges() throws Exception {
        var slot = start(avatar.getAvatarId());
        avatar.getProudSkillList().clear();
        params("10000", "1", "1");
        recipe(80, 40);
        assertEquals(Map.of(101311, 2, 101312, 1), returns(slot));
    }

    @Test
    void everyActualCategoryAndRecipeMatchesEachPassive() throws Exception {
        var definitions =
                gson.fromJson(
                        Files.readString(EXCEL.resolve("HomeWorldFurnitureExcelConfigData.json")),
                        ItemData[].class);
        var recipes =
                gson.fromJson(
                        Files.readString(EXCEL.resolve("FurnitureMakeExcelConfigData.json")),
                        FurnitureMakeConfigData[].class);
        var itemMap = new HashMap<Integer, ItemData>();
        for (var data : definitions) {
            data.onLoad();
            itemMap.put(data.getId(), data);
        }
        var categories = new HashMap<Integer, Integer>();
        for (var row : typeRows) {
            var r = row.getAsJsonObject();
            categories.put(r.get("typeID").getAsInt(), r.get("typeCategoryID").getAsInt());
        }
        int yoimiya = 0, kaveh = 0;
        for (int passive : List.of(492301, 812301)) {
            assistant(passive);
            var filters = passive == 492301 ? Set.of(1, 4, 9) : Set.of(6, 7, 9);
            for (var recipe : recipes) {
                recipe.onLoad();
                var product = itemMap.get(recipe.getFurnitureItemID());
                if (product.getStackLimit() == 0) continue;
                Map<Integer, Integer> costs = new LinkedHashMap<>();
                for (var cost : recipe.getMaterialItems())
                    if (cost.getId() > 0) costs.merge(cost.getId(), cost.getCount(), Integer::sum);
                for (var cost : costs.entrySet()) {
                    item(cost.getKey(), "ITEM_MATERIAL", null);
                    int add =
                            cost.getValue() + 1 - player.inventory.getItemCountById(cost.getKey());
                    if (add > 0) assertTrue(player.inventory.addItem(cost.getKey(), add));
                }
                GameData.getItemDataMap().put(product.getId(), product);
                GameData.getFurnitureMakeConfigDataMap()
                        .put(
                                38,
                                gson.fromJson(
                                        gson.toJson(recipe)
                                                .replace(
                                                        "\"configID\":" + recipe.getId(),
                                                        "\"configID\":38"),
                                        FurnitureMakeConfigData.class));
                player.getUnlockedFurniture().add(product.getId());
                player.home.getFurnitureMakeSlotItemList().clear();
                boolean matches =
                        product.getFurnType().stream()
                                .anyMatch(id -> filters.contains(categories.getOrDefault(id, 0)));
                Map<Integer, Integer> expected = new LinkedHashMap<>();
                if (matches)
                    costs.forEach(
                            (id, count) -> {
                                if (count >= 4) expected.put(id, count / 4);
                            });
                assertEquals(
                        expected,
                        returns(start(avatar.getAvatarId())),
                        "passive " + passive + " recipe " + recipe.getId());
                if (matches) {
                    if (passive == 492301) yoimiya++;
                    else kaveh++;
                }
            }
        }
        assertEquals(566, yoimiya);
        assertEquals(797, kaveh);
    }

    @Test
    void returnPlanPersistsThroughMongoReload() throws Exception {
        assertFalse(returns(start(avatar.getAvatarId())).isEmpty());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture184_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                var home = store.find(GameHome.class).first();
                assertNotNull(home);
                assertEquals(
                        Map.of(101311, 2, 101312, 1),
                        returns(home.getFurnitureMakeSlotItemList().get(0)));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture184_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
