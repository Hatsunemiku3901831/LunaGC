package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.forging.ActiveForgeData;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.ForgeStartRspOuterClass.ForgeStartRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Actual resin forge drop row, virtual EXP delivery and durable queue confirmation. */
class ForgeDropRewardFlowTest {
    private static ForgeData recipe;
    private static DropTableData drop;
    private static List<ItemData> items;
    private static List<PlayerLevelData> levels;
    private static List<emu.grasscutter.data.excels.avatar.AvatarFetterLevelData> fetterLevels;
    private AbilityTestFixture environment;
    private RewardPlayer player;
    private Avatar avatar;
    private GameServerPacketHandler dispatcher;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private boolean oldResin;
    private int saves, failSave;

    static class RewardPlayer extends ForgeStartFlowTest.ForgePlayer {
        boolean multiplayer, refuseProduct;
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item) {
                        return !(refuseProduct && item.getItemId() == 104013)
                                && super.addItem(item);
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public boolean isInMultiplayer() {
            return multiplayer;
        }
    }

    @BeforeAll
    static void resources() throws Exception {
        var excel = Path.of("/resources/ExcelBinOutput");
        recipe =
                JsonUtils.loadToList(excel.resolve("ForgeExcelConfigData.json"), ForgeData.class)
                        .stream()
                        .filter(r -> r.getId() == 12001)
                        .findFirst()
                        .orElseThrow();
        drop =
                JsonUtils.loadToList(
                                Path.of("/resources/Server/DropTableExcelConfigData.json"),
                                DropTableData.class)
                        .stream()
                        .filter(r -> r.getId() == 208001500)
                        .findFirst()
                        .orElseThrow();
        items =
                JsonUtils.loadToList(excel.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(r -> Set.of(102, 105, 106, 202, 101004, 104013).contains(r.getId()))
                        .toList();
        levels =
                JsonUtils.loadToList(
                        excel.resolve("PlayerLevelExcelConfigData.json"), PlayerLevelData.class);
        fetterLevels =
                JsonUtils.loadToList(
                        excel.resolve("AvatarFettersLevelExcelConfigData.json"),
                        emu.grasscutter.data.excels.avatar.AvatarFetterLevelData.class);
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
        var server = environment.scene.getWorld().getServer();
        var inventorySystem = server.getClass().getDeclaredField("inventorySystem");
        inventorySystem.setAccessible(true);
        inventorySystem.set(server, new emu.grasscutter.game.systems.InventorySystem(server));
        player = new RewardPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setScene(environment.scene);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 3);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        player.setMora(1000);
        oldResin = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        put(GameData.getForgeDataMap(), 12001, gson.fromJson(gson.toJson(recipe), ForgeData.class));
        put(
                GameData.getDropTableDataMap(),
                208001500,
                gson.fromJson(gson.toJson(drop), DropTableData.class));
        for (var item : items) put(GameData.getItemDataMap(), item.getId(), item);
        for (var level : levels) put(GameData.getPlayerLevelDataMap(), level.getId(), level);
        for (var level : fetterLevels)
            put(GameData.getAvatarFetterLevelDataMap(), level.getId(), level);
        var depot =
                gson.fromJson(
                        "{id:7301,skills:[],talents:[],inherentProudSkillOpens:[]}",
                        emu.grasscutter.data.excels.avatar.AvatarSkillDepotData.class);
        depot.onLoad();
        avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot);
        avatar.setFetterLevel(1);
        avatar.setFetterExp(0);
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(avatar));
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
        assertTrue(player.getInventory().addItem(101004, 30));
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
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResin;
    }

    private void request(int opcode, byte[] body) {
        dispatcher.handle(player.getSession(), opcode, new byte[0], body);
    }

    private int startCode() throws Exception {
        request(
                PacketOpcodes.ForgeStartReq,
                ForgeStartReq.newBuilder()
                        .setForgeId(12001)
                        .setForgeCount(1)
                        .build()
                        .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ForgeStartRsp.parseFrom(packet.getData()).getRetcode();
    }

    private ActiveForgeData start() throws Exception {
        assertEquals(0, startCode());
        saves = 0;
        var queue = player.getActiveForges().get(0);
        queue.setStartTime(Utils.getCurrentSeconds() - queue.getForgeTime() - 1);
        return queue;
    }

    private ForgeQueueManipulateRsp take() throws Exception {
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder().setForgeQueueId(1).build().toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ForgeQueueManipulateRsp.parseFrom(packet.getData());
    }

    private void replaceDrop(String overrides) {
        var row = gson.toJsonTree(drop).getAsJsonObject();
        JsonParser.parseString(overrides)
                .getAsJsonObject()
                .entrySet()
                .forEach(e -> row.add(e.getKey(), e.getValue()));
        GameData.getDropTableDataMap().put(208001500, gson.fromJson(row, DropTableData.class));
    }

    private int outputVersion(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getProductVersion").invoke(queue));
    }

    @Test
    void actualRowDeliversAllThreeOutputsWithActualHints() throws Exception {
        start();
        var response = take();
        assertEquals(0, response.getRetcode());
        assertEquals(6, player.getInventory().getItemCountById(104013));
        assertEquals(50, player.getExp());
        assertEquals(10, avatar.getFetterExp());
        var quantities = new HashMap<Integer, Integer>();
        response.getOutputItemListList().forEach(h -> quantities.put(h.getItemId(), h.getCount()));
        assertEquals(Map.of(104013, 6, 102, 50, 105, 10), quantities);
        assertEquals(0, response.getReturnItemListCount());
        assertEquals(0, response.getExtraOutputItemListCount());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void storedProductReceiptDoesNotFollowLaterDropEdits() throws Exception {
        var queue = start();
        assertEquals(1, outputVersion(queue));
        replaceDrop("{dropVec:[{itemId:104013,countRange:'99',weight:10000}]}");
        assertEquals(0, take().getRetcode());
        assertEquals(50, player.getExp());
        assertEquals(10, avatar.getFetterExp());
        assertEquals(6, player.getInventory().getItemCountById(104013));
    }

    @Test
    void fullOreStackBlocksEveryVirtualRewardUntilSpaceIsFreed() throws Exception {
        var queue = start();
        assertTrue(player.getInventory().addItem(104013, 99999));
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getExp());
        assertEquals(0, avatar.getFetterExp());
        assertEquals(0, queue.getOutputState());
        assertTrue(player.getInventory().removeItem(104013, 6));
        assertEquals(0, take().getRetcode());
        assertEquals(50, player.getExp());
        assertEquals(99999, player.getInventory().getItemCountById(104013));
    }

    @Test
    void missingVirtualDefinitionBlocksPrimaryItem() throws Exception {
        var queue = start();
        GameData.getItemDataMap().remove(105);
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getInventory().getItemCountById(104013));
        assertEquals(0, queue.getOutputState());
        assertEquals(0, player.getExp());
    }

    @Test
    void adventureExperienceCrossingLevelIsConfirmed() throws Exception {
        start();
        int required = GameData.getPlayerLevelDataMap().get(30).getExp();
        player.setProperty(PlayerProperty.PROP_PLAYER_EXP, required - 25);
        assertEquals(0, take().getRetcode());
        assertEquals(31, player.getLevel());
        assertEquals(25, player.getExp());
    }

    @Test
    void friendshipExperienceCrossingLevelIsConfirmed() throws Exception {
        start();
        int required = GameData.getAvatarFetterLevelExpRequired(1);
        avatar.setFetterExp(required - 5);
        assertEquals(0, take().getRetcode());
        assertEquals(2, avatar.getFetterLevel());
        assertEquals(5, avatar.getFetterExp());
    }

    @Test
    void maxFriendshipRemainsCappedAndStillAllowsClaim() throws Exception {
        start();
        avatar.setFetterLevel(10);
        assertEquals(0, take().getRetcode());
        assertEquals(10, avatar.getFetterLevel());
        assertEquals(0, avatar.getFetterExp());
        assertEquals(50, player.getExp());
    }

    @Test
    void multiplayerUsesExistingDoubleFriendshipRule() throws Exception {
        start();
        player.multiplayer = true;
        assertEquals(0, take().getRetcode());
        assertEquals(20, avatar.getFetterExp());
        assertEquals(50, player.getExp());
    }

    @Test
    void emptyTeamDoesNotPreventAdventureReward() throws Exception {
        start();
        player.getTeamManager().getActiveTeam().clear();
        assertEquals(0, take().getRetcode());
        assertEquals(50, player.getExp());
    }

    @Test
    void duplicateTeamEntriesDoNotDoubleFriendship() throws Exception {
        start();
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(avatar));
        assertEquals(0, take().getRetcode());
        assertEquals(10, avatar.getFetterExp());
    }

    @Test
    void missingFriendshipLevelPreventsAllDelivery() throws Exception {
        var queue = start();
        GameData.getAvatarFetterLevelDataMap().remove(1);
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getExp());
        assertEquals(0, player.getInventory().getItemCountById(104013));
        assertEquals(0, queue.getOutputState());
    }

    @Test
    void overflowingAdventureExperiencePreservesQueueAndProduct() throws Exception {
        var queue = start();
        player.setProperty(PlayerProperty.PROP_PLAYER_EXP, Integer.MAX_VALUE);
        assertNotEquals(0, take().getRetcode());
        assertEquals(Integer.MAX_VALUE, player.getExp());
        assertEquals(0, player.getInventory().getItemCountById(104013));
        assertEquals(0, queue.getOutputState());
    }

    @Test
    void invalidStoredProductVersionRefusesDelivery() throws Exception {
        var queue = start();
        try {
            var field = ActiveForgeData.class.getDeclaredField("productVersion");
            field.setAccessible(true);
            field.setInt(queue, 99);
        } catch (NoSuchFieldException oldImplementation) {
        }
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getInventory().getItemCountById(104013));
        assertEquals(0, queue.getOutputState());
    }

    @Test
    void oldQueueUsesItsGuaranteedDropTableWithoutInventingReceipt() throws Exception {
        var queue = start();
        try {
            var field = ActiveForgeData.class.getDeclaredField("productVersion");
            field.setAccessible(true);
            field.setInt(queue, 0);
        } catch (NoSuchFieldException oldImplementation) {
        }
        assertEquals(0, take().getRetcode());
        assertEquals(50, player.getExp());
        assertEquals(10, avatar.getFetterExp());
    }

    @Test
    void refusedProductDoesNotGrantExperienceAndCannotReplay() throws Exception {
        var queue = start();
        player.refuseProduct = true;
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getExp());
        assertEquals(0, avatar.getFetterExp());
        assertNotEquals(0, queue.getOutputState());
        player.refuseProduct = false;
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, player.getExp());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void confirmationFailureNeverDuplicatesAnyReward(int stage) throws Exception {
        var queue = start();
        failSave = stage;
        assertNotEquals(0, take().getRetcode());
        int exp = player.getExp(),
                fetter = avatar.getFetterExp(),
                ore = player.getInventory().getItemCountById(104013);
        failSave = 0;
        assertNotEquals(0, take().getRetcode());
        assertEquals(exp, player.getExp());
        assertEquals(fetter, avatar.getFetterExp());
        assertEquals(ore, player.getInventory().getItemCountById(104013));
        assertNotEquals(0, queue.getOutputState());
    }

    @Test
    void missingDropRefusesStartBeforePayment() throws Exception {
        GameData.getDropTableDataMap().remove(208001500);
        assertNotEquals(0, startCode());
        assertEquals(30, player.getInventory().getItemCountById(101004));
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{randomType:0}",
                "{dropVec:[{itemId:104013,countRange:'6',weight:5000}]}",
                "{dropVec:[{itemId:104013,countRange:'1;6',weight:10000}]}",
                "{dropVec:[{itemId:104013,countRange:'2147483648',weight:10000}]}",
                "{dropVec:[{itemId:999999,countRange:'1',weight:10000}]}",
                "{dropVec:[{itemId:106,countRange:'1',weight:10000}]}",
                "{dropVec:[]}"
            })
    void unsupportedOrInvalidDropsRefuseStartWithoutCharging(String row) throws Exception {
        replaceDrop(row);
        assertNotEquals(0, startCode());
        assertEquals(30, player.getInventory().getItemCountById(101004));
        assertEquals(1000, player.getMora());
        assertTrue(player.getActiveForges().isEmpty());
    }

    @dev.morphia.annotations.Entity(value = "forge_drop_test", useDiscriminator = false)
    static class StoredForge {
        @dev.morphia.annotations.Id String id;
        ActiveForgeData queue;
    }

    @Test
    void recordedProductsSurviveMongoAndDropRemoval() throws Exception {
        var queue = start();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgedrop152_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "queue";
                row.queue = queue;
                store.save(row);
                var loaded = store.find(StoredForge.class).first().queue;
                assertEquals(1, outputVersion(loaded));
                assertEquals(0, loaded.getStartState(), "Stored start receipt");
                assertEquals(0, loaded.getOutputState(), "Stored output receipt");
                assertEquals(0, loaded.getRefundState(), "Stored refund receipt");
                assertFalse(loaded.isBusy(), "Stored queue must be available");
                player.getActiveForges().set(0, loaded);
                GameData.getDropTableDataMap().remove(208001500);
                assertEquals(1, player.getActiveForges().size());
                assertEquals(12001, loaded.getForgeId());
                assertEquals(1, loaded.getCount());
                assertEquals(1, loaded.getFinishedCount(Utils.getCurrentSeconds()));
                assertEquals(3, loaded.getProducts(1).size());
                assertEquals(0, take().getRetcode());
                assertEquals(50, player.getExp());
                assertEquals(10, avatar.getFetterExp());
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgedrop152_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
