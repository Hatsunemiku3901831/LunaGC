package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class FurnitureTakeFlowTest {
    static class CraftPlayer extends InvestigationRewardTest.ClaimPlayer {
        GameHome home;
        boolean refusePay, pretendPay, refuseGrant, pretendGrant;
        int missions, grants;
        Runnable afterGrant;
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean payItem(int id, int count) {
                        if (refusePay) return false;
                        if (pretendPay) return true;
                        return super.payItem(id, count);
                    }

                    @Override
                    public boolean addItem(GameItem item, boolean skip, boolean notify) {
                        if (item.getItemType() == ItemType.ITEM_FURNITURE) {
                            grants++;
                            if (refuseGrant) return false;
                            if (pretendGrant) return true;
                        }
                        boolean result = super.addItem(item, skip, notify);
                        if (item.getItemType() == ItemType.ITEM_FURNITURE && afterGrant != null) {
                            var hook = afterGrant;
                            afterGrant = null;
                            hook.run();
                        }
                        return result;
                    }
                };
        final BattlePassManager battlepass =
                new BattlePassManager() {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {
                        if (type == WatcherTriggerType.TRIGGER_FURNITURE_MAKE) missions += progress;
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return battlepass;
        }
    }

    private AbilityTestFixture environment;
    private CraftPlayer player;
    private InvestigationRewardTest.ClaimSession session;
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
        player = new CraftPlayer();
        session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        player.setSession(session);
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .level(1)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .sceneMap(new ConcurrentHashMap<>())
                        .mainHouseMap(new ConcurrentHashMap<>())
                        .unlockedHomeBgmList(new HashSet<>())
                        .finishedTalkIdMap(new HashMap<>())
                        .finishedRewardEventIdSet(new HashSet<>())
                        .build();
        clear(GameData.getItemDataMap());
        clear(GameData.getFurnitureMakeConfigDataMap());
        clear(GameData.getHomeWorldLevelDataMap());
        item(360101, "ITEM_FURNITURE", 100);
        item(107013, "ITEM_MATERIAL", 100);
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:38,furnitureItemID:360101,count:1,exp:60,makeTime:3600,materialItems:[]}",
                                FurnitureMakeConfigData.class));
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        GameData.getHomeWorldLevelDataMap()
                .put(
                        2,
                        gson.fromJson(
                                "{level:2,exp:600,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
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
        session.responses.clear();
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, previousDatastore);
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

    private FurnitureMakeSlotItem slot(boolean finished) {
        var slot =
                FurnitureMakeSlotItem.of()
                        .index(0)
                        .makeId(38)
                        .avatarId(0)
                        .beginTime(Utils.getCurrentSeconds() - (finished ? 7200 : 0))
                        .durTime(3600)
                        .build();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        return slot;
    }

    private TakeFurnitureMakeRsp take(boolean fast) throws Exception {
        return take(0, 38, fast);
    }

    private TakeFurnitureMakeRsp take(int index, int makeId, boolean fast) throws Exception {
        var previousSession = player.getSession();
        var requestSession =
                new InvestigationRewardTest.ClaimSession(previousSession.getServer(), player);
        player.setSession(requestSession);
        try {
            player.getFurnitureManager().take(index, makeId, fast);
        } finally {
            player.setSession(previousSession);
        }
        var responses =
                requestSession.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeFurnitureMakeRsp)
                        .toList();
        assertEquals(1, responses.size());
        return TakeFurnitureMakeRsp.parseFrom(responses.get(0).getData());
    }

    private Object pending() {
        try {
            return player.home.getClass().getMethod("getPendingFurnitureTake").invoke(player.home);
        } catch (ReflectiveOperationException baseline) {
            return null;
        }
    }

    private void intact(FurnitureMakeSlotItem slot) {
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
        assertEquals(0, player.getInventory().getItemCountById(360101));
        assertEquals(0, player.missions);
    }

    @Test
    void exactFinishBoundaryIsClaimable() throws Exception {
        var slot = slot(false);
        slot.setBeginTime(Utils.getCurrentSeconds() - 3600);
        assertEquals(0, take(false).getRetcode());
        assertEquals(1, player.getInventory().getItemCountById(360101));
    }

    @Test
    void pendingClaimAlsoBlocksNewCrafting() throws Exception {
        slot(true);
        failSave = 1;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(pending());
        session.responses.clear();
        player.getFurnitureManager().startMake(38, 0);
        var packet =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(
                0,
                emu.grasscutter.net.proto.FurnitureMakeStartRspOuterClass.FurnitureMakeStartRsp
                        .parseFrom(packet.getData())
                        .getRetcode());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void everyCurrentFurnitureDefinitionCanBeClaimedAfterCompletion() throws Exception {
        var root =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_RESOURCES", "/resources"),
                        "ExcelBinOutput");
        GameData.getFurnitureMakeConfigDataMap().clear();
        for (var row :
                JsonParser.parseString(
                                Files.readString(root.resolve("FurnitureMakeExcelConfigData.json")))
                        .getAsJsonArray()) {
            var definition = gson.fromJson(row, FurnitureMakeConfigData.class);
            GameData.getFurnitureMakeConfigDataMap().put(definition.getId(), definition);
        }
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        root.resolve("HomeWorldFurnitureExcelConfigData.json")))
                        .getAsJsonArray()) {
            var data =
                    emu.grasscutter.utils.JsonUtils.decode(row.getAsJsonObject(), ItemData.class);
            data.onLoad();
            GameData.getItemDataMap().put(data.getId(), data);
        }
        GameData.getHomeWorldLevelDataMap().clear();
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        root.resolve("HomeworldLevelExcelConfigData.json")))
                        .getAsJsonArray()) {
            var data = gson.fromJson(row, HomeWorldLevelData.class);
            GameData.getHomeWorldLevelDataMap().put(data.getId(), data);
        }
        int checked = 0, rejected = 0;
        for (var definition : GameData.getFurnitureMakeConfigDataMap().values()) {
            var job =
                    FurnitureMakeSlotItem.of()
                            .index(0)
                            .makeId(definition.getId())
                            .beginTime(1)
                            .durTime(definition.getMakeTime())
                            .build();
            player.home.getFurnitureMakeSlotItemList().add(job);
            int before = player.getInventory().getItemCountById(definition.getFurnitureItemID());
            var reply = take(0, definition.getId(), false);
            var product = GameData.getItemDataMap().get(definition.getFurnitureItemID());
            if (product == null || product.getStackLimit() <= 0) {
                assertNotEquals(0, reply.getRetcode());
                assertEquals(
                        before,
                        player.getInventory().getItemCountById(definition.getFurnitureItemID()));
                assertTrue(player.home.getFurnitureMakeSlotItemList().contains(job));
                player.home.getFurnitureMakeSlotItemList().remove(job);
                rejected++;
                checked++;
                continue;
            }
            assertEquals(0, reply.getRetcode(), "recipe " + definition.getId());
            assertEquals(
                    before + definition.getCount(),
                    player.getInventory().getItemCountById(definition.getFurnitureItemID()));
            assertFalse(player.home.getFurnitureMakeSlotItemList().contains(job));
            assertNull(pending());
            player.packets.clear();
            checked++;
        }
        assertEquals(1405, checked);
        assertEquals(9, rejected);
    }

    @Test
    void unfinishedFurnitureCannotBeClaimed() throws Exception {
        var slot = slot(false);
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
        assertNull(pending());
    }

    @Test
    void completedFurnitureIsDeliveredAndRemovedOnce() throws Exception {
        slot(true);
        assertEquals(0, take(false).getRetcode());
        assertEquals(1, player.getInventory().getItemCountById(360101));
        assertTrue(player.home.getFurnitureMakeSlotItemList().isEmpty());
        assertEquals(1, player.missions);
        assertNull(pending());
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(1, player.grants);
    }

    @Test
    void firstCraftAddsTrustExp() throws Exception {
        slot(true);
        assertEquals(0, take(false).getRetcode());
        assertEquals(60, player.home.getExp());
    }

    @Test
    void repeatedFurnitureDoesNotAddTrustExpAgain() throws Exception {
        slot(true);
        assertEquals(0, take(false).getRetcode());
        slot(true);
        assertEquals(0, take(false).getRetcode());
        assertEquals(60, player.home.getExp());
        assertEquals(2, player.missions);
    }

    @Test
    void finishedFurnitureDoesNotSpendAVialForAStaleFastFlag() throws Exception {
        slot(true);
        assertTrue(player.getInventory().addItem(107013, 2));
        assertEquals(0, take(true).getRetcode());
        assertEquals(2, player.getInventory().getItemCountById(107013));
    }

    @Test
    void unfinishedFurnitureMayUseOneConfirmedVial() throws Exception {
        slot(false);
        assertTrue(player.getInventory().addItem(107013, 2));
        assertEquals(0, take(true).getRetcode());
        assertEquals(1, player.getInventory().getItemCountById(107013));
    }

    @Test
    void noVialKeepsTheUnfinishedSlot() throws Exception {
        var slot = slot(false);
        assertNotEquals(0, take(true).getRetcode());
        intact(slot);
    }

    @Test
    void refusedVialPaymentCannotGrantFurniture() throws Exception {
        var slot = slot(false);
        player.getInventory().addItem(107013, 2);
        player.refusePay = true;
        assertNotEquals(0, take(true).getRetcode());
        intact(slot);
        assertNotNull(pending());
    }

    @Test
    void pretendVialPaymentCannotGrantFurniture() throws Exception {
        var slot = slot(false);
        player.getInventory().addItem(107013, 2);
        player.pretendPay = true;
        assertNotEquals(0, take(true).getRetcode());
        intact(slot);
        assertNotNull(pending());
    }

    @Test
    void refusedFurnitureGrantKeepsTheSlotAndBlocksRetry() throws Exception {
        var slot = slot(true);
        player.refuseGrant = true;
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
        assertNotNull(pending());
        int grants = player.grants;
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(grants, player.grants);
    }

    @Test
    void pretendFurnitureGrantCannotRemoveTheSlot() throws Exception {
        var slot = slot(true);
        player.pretendGrant = true;
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
        assertNotNull(pending());
    }

    @Test
    void completeOutputStackIsCheckedBeforeSpendingAVial() throws Exception {
        item(360101, "ITEM_FURNITURE", 1);
        player.getInventory().addItem(360101, 1);
        player.getInventory().addItem(107013, 2);
        var slot = slot(false);
        assertNotEquals(0, take(true).getRetcode());
        assertEquals(2, player.getInventory().getItemCountById(107013));
        assertTrue(player.home.getFurnitureMakeSlotItemList().contains(slot));
        assertNull(pending());
    }

    @Test
    void aForeignHomeCannotBeClaimedOrModified() throws Exception {
        var slot = slot(true);
        player.home.setOwnerUid(10002);
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
    }

    @Test
    void duplicateLegacySlotKeysAreRejectedWithoutGranting() throws Exception {
        var slot = slot(true);
        slot(true);
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void invalidOutputDefinitionKeepsTheSlot() throws Exception {
        var slot = slot(true);
        item(360101, "ITEM_MATERIAL", 100);
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
    }

    @Test
    void mismatchedRecipeMapKeyKeepsTheSlot() throws Exception {
        var slot = slot(true);
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        38,
                        gson.fromJson(
                                "{configID:39,furnitureItemID:360101,count:1,exp:60,makeTime:3600}",
                                FurnitureMakeConfigData.class));
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
    }

    @Test
    void corruptedNegativeDurationKeepsTheSlot() throws Exception {
        var slot = slot(true);
        slot.setDurTime(-1);
        assertNotEquals(0, take(false).getRetcode());
        intact(slot);
    }

    @Test
    void emptyLegacySlotListReturnsFailure() throws Exception {
        player.home.setFurnitureMakeSlotItemList(null);
        assertNotEquals(0, assertDoesNotThrow(() -> take(false)).getRetcode());
    }

    @Test
    void reentryCannotClaimTheSameSlotAgain() throws Exception {
        slot(true);
        player.afterGrant =
                () -> assertDoesNotThrow(() -> assertNotEquals(0, take(false).getRetcode()));
        assertEquals(0, take(false).getRetcode());
        assertEquals(1, player.grants);
        assertEquals(1, player.getInventory().getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void acknowledgedWriteFailureKeepsARecordAndCannotReplay(int stage) throws Exception {
        slot(true);
        failSave = stage;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(pending());
        int before = player.getInventory().getItemCountById(360101),
                grants = player.grants,
                missions = player.missions;
        failSave = 0;
        assertNotEquals(0, take(false).getRetcode());
        assertEquals(before, player.getInventory().getItemCountById(360101));
        assertEquals(grants, player.grants);
        assertEquals(missions, player.missions);
    }

    @Test
    void pendingReceiptAndFirstCraftStateSurviveMongo() throws Exception {
        slot(true);
        failSave = 6;
        assertNotEquals(0, take(false).getRetcode());
        assertNotNull(pending());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_furniture170_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("home");
                store.save(player.home);
                var restored = store.find(GameHome.class).first();
                assertNotNull(restored);
                player.home = restored;
                restored.setPlayer(player);
                failSave = 0;
                assertNotNull(pending());
                assertEquals(60, restored.getExp());
                int before = player.getInventory().getItemCountById(360101);
                assertNotEquals(0, take(false).getRetcode());
                assertEquals(before, player.getInventory().getItemCountById(360101));
            } finally {
                assertTrue(name.startsWith("lunagc_test_furniture170_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
