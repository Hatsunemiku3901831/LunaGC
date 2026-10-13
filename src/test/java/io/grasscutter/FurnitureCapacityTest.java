package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
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

class FurnitureCapacityTest {
    private final Gson gson = new Gson();

    HomeWorldLevelData row(String json) {
        return gson.fromJson(json, HomeWorldLevelData.class);
    }

    String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/furniture-levels/" + name + ".json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void currentResourceProvidesRealCapacity(int level) throws Exception {
        var path =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"),
                        "ExcelBinOutput/HomeworldLevelExcelConfigData.json");
        var rows = gson.fromJson(Files.readString(path), HomeWorldLevelData[].class);
        var definition =
                Arrays.stream(rows).filter(r -> r.getId() == level).findFirst().orElseThrow();
        assertEquals(level, definition.getFurnitureMakeSlotCount());
    }

    @Test
    void legacyRenameMatchesTheCompleteEarlierTable() throws Exception {
        var old = JsonParser.parseString(fixture("5.4")).getAsJsonArray();
        var next = JsonParser.parseString(fixture("6.0")).getAsJsonArray();
        assertEquals(10, old.size());
        assertEquals(old.size(), next.size());
        for (int i = 0; i < old.size(); i++) {
            var a = old.get(i).getAsJsonObject().deepCopy();
            var b = next.get(i).getAsJsonObject().deepCopy();
            assertEquals(a.get("furnitureMakeSlotCount"), b.get("HMEPEMLDHII"));
            b.add("furnitureMakeSlotCount", b.remove("HMEPEMLDHII"));
            b.remove("HKPNMEHOIGF");
            if (!a.has("exp")) a.addProperty("exp", 0);
            assertEquals(a, b);
            assertEquals(
                    row(a.toString()).getFurnitureMakeSlotCount(),
                    row(next.get(i).toString()).getFurnitureMakeSlotCount());
        }
    }

    @Test
    void current71TableKeepsFifteenSlotsAtRankTen() throws Exception {
        var definitions = gson.fromJson(fixture("7.1"), HomeWorldLevelData[].class);
        assertEquals(10, definitions.length);
        for (var definition : definitions)
            assertEquals(
                    definition.getId() == 10 ? 15 : definition.getId(),
                    definition.getFurnitureMakeSlotCount());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{level:1,HMEPEMLDHII:8,furnitureMakeSlotCount:3}",
                "{level:1,furnitureMakeSlotCount:3,HMEPEMLDHII:8}"
            })
    void explicitCanonicalValueWinsInBothJsonOrders(String json) {
        assertEquals(3, row(json).getFurnitureMakeSlotCount());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{level:10,HMEPEMLDHII:10,HALLBIKHGGJ:15}",
                "{level:10,HALLBIKHGGJ:15,HMEPEMLDHII:10}"
            })
    void modernAliasWinsOverLegacyRegardlessOfOrder(String json) {
        assertEquals(15, row(json).getFurnitureMakeSlotCount());
    }

    @Test
    void explicitCanonicalZeroIsNotReplacedByAnAlias() {
        assertEquals(
                0,
                row("{level:1,furnitureMakeSlotCount:0,HMEPEMLDHII:4,HALLBIKHGGJ:5}")
                        .getFurnitureMakeSlotCount());
    }

    @Test
    void invalidCanonicalValueIsNotHiddenByAnAlias() {
        assertEquals(
                -1,
                row("{level:1,furnitureMakeSlotCount:-1,HMEPEMLDHII:4}")
                        .getFurnitureMakeSlotCount());
    }

    @Test
    void absentCapacityRemainsUnknown() {
        assertEquals(0, row("{level:1}").getFurnitureMakeSlotCount());
    }

    @Test
    void unrelatedObfuscatedFieldIsNotATemplateCapacity() {
        assertEquals(0, row("{level:1,HKPNMEHOIGF:15}").getFurnitureMakeSlotCount());
    }

    @Test
    void realLegacyTableCanStartItsRankOneQueue() throws Exception {
        var path =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"),
                        "ExcelBinOutput/HomeworldLevelExcelConfigData.json");
        runQueue(Files.readString(path), 1, 1);
    }

    @Test
    void realLegacyRankTenHonorsTenSlots() throws Exception {
        var path =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_TEST_RESOURCES", "/resources"),
                        "ExcelBinOutput/HomeworldLevelExcelConfigData.json");
        runQueue(Files.readString(path), 10, 10);
    }

    @Test
    void modernRankTenHonorsFifteenSlots() throws Exception {
        runQueue(fixture("7.1"), 10, 15);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void runQueue(String definitions, int level, int capacity) throws Exception {
        var environment = new AbilityTestFixture();
        var player = new FurnitureTakeFlowTest.CraftPlayer();
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        player.setSession(session);
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .level(level)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        var levels = new HashMap<>(GameData.getHomeWorldLevelDataMap());
        var items = new HashMap<>(GameData.getItemDataMap());
        var recipes = new HashMap<>(GameData.getFurnitureMakeConfigDataMap());
        var store = DatabaseManager.class.getDeclaredField("gameDatastore");
        store.setAccessible(true);
        var previous = store.get(null);
        try {
            GameData.getHomeWorldLevelDataMap().clear();
            for (var data : gson.fromJson(definitions, HomeWorldLevelData[].class))
                GameData.getHomeWorldLevelDataMap().put(data.getId(), data);
            var item =
                    gson.fromJson(
                            "{id:360101,itemType:ITEM_FURNITURE,stackLimit:9999}",
                            emu.grasscutter.data.excels.ItemData.class);
            item.onLoad();
            GameData.getItemDataMap().put(360101, item);
            player.getUnlockedFurniture().add(360101);
            GameData.getFurnitureMakeConfigDataMap()
                    .put(
                            38,
                            gson.fromJson(
                                    "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[]}",
                                    FurnitureMakeConfigData.class));
            store.set(
                    null,
                    Proxy.newProxyInstance(
                            dev.morphia.Datastore.class.getClassLoader(),
                            new Class<?>[] {dev.morphia.Datastore.class},
                            (proxy, method, args) ->
                                    method.getName().equals("save") ? args[0] : null));
            for (int i = 0; i <= capacity; i++) {
                session.responses.clear();
                player.getFurnitureManager().startMake(38, 0);
                var packets =
                        session.responses.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                                .toList();
                assertEquals(1, packets.size());
                assertEquals(
                        i == capacity ? Retcode.RET_FURNITURE_MAKE_SLOT_FULL_VALUE : 0,
                        FurnitureMakeStartRsp.parseFrom(packets.get(0).getData()).getRetcode());
            }
            assertEquals(capacity, player.home.getFurnitureMakeSlotItemList().size());
            assertEquals(
                    capacity,
                    player.home.getFurnitureMakeSlotItemList().stream()
                            .map(FurnitureMakeSlotItem::getIndex)
                            .distinct()
                            .count());
        } finally {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            store.set(null, previous);
            GameData.getHomeWorldLevelDataMap().clear();
            GameData.getHomeWorldLevelDataMap().putAll(levels);
            GameData.getItemDataMap().clear();
            GameData.getItemDataMap().putAll(items);
            GameData.getFurnitureMakeConfigDataMap().clear();
            GameData.getFurnitureMakeConfigDataMap().putAll(recipes);
            environment.close();
        }
    }
}
