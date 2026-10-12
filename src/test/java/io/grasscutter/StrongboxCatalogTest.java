package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ReliquaryDecomposeRspOuterClass.ReliquaryDecomposeRsp;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Current resource joins must cover every client strongbox, including the newest sets. */
class StrongboxCatalogTest {
    private AbilityTestFixture environment;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, RewardPreviewData> oldPreviews;
    private Map<Integer, List<Integer>> catalog;
    private Map<Integer, List<Integer>> oldCatalog;
    private final Map<Map<Integer, Object>, Map<Integer, Object>> extraMaps =
            new IdentityHashMap<>();
    private final Gson gson = new Gson();
    private JsonArray resourceRules, displays;
    private Map<Integer, JsonObject> relicRows;
    private Field datastore;
    private Object oldDatastore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldPreviews = new HashMap<>(GameData.getRewardPreviewDataMap());
        GameData.getItemDataMap().clear();
        GameData.getRewardPreviewDataMap().clear();
        relicRows = new HashMap<>();
        for (var row : rows("ReliquaryExcelConfigData.json")) {
            var data = JsonUtils.decode(row, ItemData.class);
            GameData.getItemDataMap().put(data.getId(), data);
            relicRows.put(data.getId(), row.getAsJsonObject());
        }
        for (var row : rows("RewardPreviewExcelConfigData.json")) {
            var data = JsonUtils.decode(row, RewardPreviewData.class);
            data.onLoad();
            GameData.getRewardPreviewDataMap().put(data.getId(), data);
        }
        resourceRules = rows("ReliquaryDecomposeExcelConfigData.json");
        displays = rows("DisplayItemExcelConfigData.json");
        loadOptional("ReliquaryDecomposeData", resourceRules);
        loadOptional("DisplayItemData", displays);
        var field = CombineManger.class.getDeclaredField("reliquaryDecomposeData");
        field.setAccessible(true);
        catalog = (Map<Integer, List<Integer>>) field.get(null);
        oldCatalog = new HashMap<>(catalog);
        catalog.clear();
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
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
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        catalog.clear();
        catalog.putAll(oldCatalog);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getRewardPreviewDataMap().clear();
        GameData.getRewardPreviewDataMap().putAll(oldPreviews);
        extraMaps.forEach(
                (map, old) -> {
                    map.clear();
                    map.putAll(old);
                });
        environment.close();
    }

    private JsonArray rows(String file) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("resources/ExcelBinOutput", file)))
                .getAsJsonArray();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> resourceMap(String type) {
        return assertDoesNotThrow(
                () -> {
                    var resource = Class.forName("emu.grasscutter.data.excels." + type);
                    var map = (Map<Integer, Object>) GameData.getMapByResourceDef(resource);
                    assertNotNull(map);
                    return map;
                });
    }

    @SuppressWarnings("unchecked")
    private void loadOptional(String type, JsonArray rows) throws Exception {
        Class<?> resource;
        try {
            resource = Class.forName("emu.grasscutter.data.excels." + type);
        } catch (ClassNotFoundException oldImplementation) {
            return;
        }
        var map = (Map<Integer, Object>) GameData.getMapByResourceDef(resource);
        assertNotNull(map);
        extraMaps.put(map, new HashMap<>(map));
        map.clear();
        for (var row : rows) {
            var data = (GameResource) JsonUtils.decode(row, resource);
            data.onLoad();
            map.put(data.getId(), data);
        }
    }

    private JsonObject rule(int id) {
        return java.util.stream.StreamSupport.stream(resourceRules.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(row -> row.get("id").getAsInt() == id)
                .findFirst()
                .orElseThrow();
    }

    private int setFor(int id) {
        int preview = rule(id).get("rewardPreviewId").getAsInt();
        int displayId =
                Arrays.stream(GameData.getRewardPreviewDataMap().get(preview).getRawPreviewItems())
                        .filter(item -> item.getId() > 0)
                        .findFirst()
                        .orElseThrow()
                        .getId();
        return java.util.stream.StreamSupport.stream(displays.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(row -> row.get("id").getAsInt() == displayId)
                .findFirst()
                .orElseThrow()
                .get("param")
                .getAsInt();
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40
            })
    void eachCurrentBoxContainsTenNormalFiveStarRelicsFromTheAdvertisedSet(int id) {
        CombineManger.initialize();
        var items = catalog.get(id);
        assertNotNull(items, "Missing resource strongbox " + id);
        assertEquals(10, items.size());
        assertEquals(10, new HashSet<>(items).size());
        int set = setFor(id);
        var slots = new HashMap<EquipType, Set<Integer>>();
        for (int itemId : items) {
            var data = GameData.getItemDataMap().get(itemId);
            assertEquals(set, data.getSetId());
            assertEquals(ItemType.ITEM_RELIQUARY, data.getItemType());
            assertEquals(5, data.getRankLevel());
            assertFalse(relicRows.get(itemId).get("dropable").getAsBoolean());
            slots.computeIfAbsent(data.getEquipType(), ignored -> new HashSet<>())
                    .add(data.getAppendPropNum());
        }
        assertEquals(5, slots.size());
        slots.values().forEach(variants -> assertEquals(Set.of(3, 4), variants));
    }

    @Test
    void allFortyResourceConfigurationsAreInstalled() {
        CombineManger.initialize();
        assertEquals(40, catalog.size());
        assertEquals(
                Set.of(1, 40),
                Set.of(Collections.min(catalog.keySet()), Collections.max(catalog.keySet())));
    }

    @Test
    void resourceSchemasUseTheRegisteredTableLoader() throws Exception {
        for (String type : List.of("ReliquaryDecomposeData", "DisplayItemData")) {
            var map = resourceMap(type);
            map.clear();
            var schema = Class.forName("emu.grasscutter.data.excels." + type);
            var annotation = schema.getAnnotation(ResourceType.class);
            assertNotNull(annotation);
            assertEquals(
                    type.substring(0, type.length() - 4) + "ExcelConfigData.json",
                    annotation.name()[0]);
            var loader =
                    ResourceLoader.class.getDeclaredMethod(
                            "loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
            loader.setAccessible(true);
            loader.invoke(
                    null, schema, Path.of("resources/ExcelBinOutput", annotation.name()[0]), map);
            assertEquals(type.equals("ReliquaryDecomposeData") ? 40 : displays.size(), map.size());
        }
        CombineManger.initialize();
        assertEquals(40, catalog.size());
    }

    @Test
    void oldTwentyTwoBoxesKeepTheirExactCandidateIds() throws Exception {
        CombineManger.initialize();
        try (var input =
                CombineManger.class.getResourceAsStream("/defaults/data/ReliquaryDecompose.json")) {
            assertNotNull(input);
            var old =
                    JsonParser.parseReader(
                                    new java.io.InputStreamReader(
                                            input, java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonArray();
            assertEquals(22, old.size());
            for (var raw : old) {
                var row = raw.getAsJsonObject();
                var expected = new HashSet<Integer>();
                row.getAsJsonArray("items").forEach(id -> expected.add(id.getAsInt()));
                assertEquals(expected, new HashSet<>(catalog.get(row.get("configId").getAsInt())));
            }
        }
    }

    @Test
    void reinitializationDropsRemovedResourceConfiguration() {
        CombineManger.initialize();
        resourceMap("ReliquaryDecomposeData").remove(40);
        CombineManger.initialize();
        assertEquals(39, catalog.size());
        assertFalse(catalog.containsKey(40));
    }

    @Test
    void missingRewardPreviewDisablesOnlyItsBox() {
        GameData.getRewardPreviewDataMap().remove(40140);
        CombineManger.initialize();
        assertEquals(39, catalog.size());
        assertFalse(catalog.containsKey(40));
        assertTrue(catalog.containsKey(39));
    }

    @Test
    void missingDisplaySetDisablesOnlyItsBox() {
        resourceMap("DisplayItemData").remove(419039);
        CombineManger.initialize();
        assertEquals(39, catalog.size());
        assertFalse(catalog.containsKey(40));
    }

    @Test
    void incompleteFiveSlotPoolDisablesOnlyItsBox() {
        GameData.getItemDataMap().remove(42554);
        CombineManger.initialize();
        assertEquals(39, catalog.size());
        assertFalse(catalog.containsKey(40));
    }

    @Test
    void displayOfAnotherKindCannotSelectASet() {
        var map = resourceMap("DisplayItemData");
        var row = gson.toJsonTree(map.get(419039)).getAsJsonObject();
        row.addProperty("displayType", "BARTENDER_ITEM");
        map.put(419039, gson.fromJson(row, map.get(419039).getClass()));
        CombineManger.initialize();
        assertEquals(39, catalog.size());
        assertFalse(catalog.containsKey(40));
    }

    private InvestigationRewardTest.ClaimPlayer playerWithInputs(int count) {
        var player = new InvestigationRewardTest.ClaimPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        for (int i = 0; i < count; i++)
            assertTrue(player.getInventory().addItem(new GameItem(75513, 1)));
        player.packets.clear();
        return player;
    }

    private List<Long> ids(InvestigationRewardTest.ClaimPlayer player) {
        return player.getInventory().getItems().values().stream().map(GameItem::getGuid).toList();
    }

    private ReliquaryDecomposeRsp response(InvestigationRewardTest.ClaimPlayer player)
            throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ReliquaryDecomposeRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ReliquaryDecomposeRsp.parseFrom(packet.getData());
    }

    @Test
    void newestBoxCanExchangeThreeOwnedRelics() throws Exception {
        CombineManger.initialize();
        var player = playerWithInputs(3);
        new CombineManger(environment.scene.getWorld().getServer())
                .decomposeReliquaries(player, 40, 1, ids(player));
        var reply = response(player);
        assertEquals(0, reply.getRetcode());
        assertEquals(1, reply.getGuidListCount());
        var item = player.getInventory().getItemByGuid(reply.getGuidList(0));
        assertNotNull(item);
        assertEquals(15042, item.getItemData().getSetId());
        assertEquals(player.getUid(), item.getOwnerId());
    }

    @Test
    void ingredientCountIsReadFromTheCurrentRule() throws Exception {
        var map = resourceMap("ReliquaryDecomposeData");
        var row = gson.toJsonTree(map.get(1)).getAsJsonObject();
        row.addProperty("needReliquaryCount", 4);
        map.put(1, gson.fromJson(row, map.get(1).getClass()));
        CombineManger.initialize();
        var player = playerWithInputs(3);
        var before = ids(player);
        new CombineManger(environment.scene.getWorld().getServer())
                .decomposeReliquaries(player, 1, 1, before);
        assertNotEquals(0, response(player).getRetcode());
        assertEquals(3, player.getInventory().getItems().size());
        assertTrue(player.getInventory().getItems().keySet().containsAll(before));
    }

    @Test
    void exchangeLimitIsReadFromTheCurrentRule() throws Exception {
        var map = resourceMap("ReliquaryDecomposeData");
        var row = gson.toJsonTree(map.get(1)).getAsJsonObject();
        row.addProperty("maxReliquaryNum", 1);
        map.put(1, gson.fromJson(row, map.get(1).getClass()));
        CombineManger.initialize();
        var player = playerWithInputs(6);
        new CombineManger(environment.scene.getWorld().getServer())
                .decomposeReliquaries(player, 1, 2, ids(player));
        assertNotEquals(0, response(player).getRetcode());
        assertEquals(6, player.getInventory().getItems().size());
    }
}
