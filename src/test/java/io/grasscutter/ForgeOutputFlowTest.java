package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.forging.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueDataNotifyOuterClass.ForgeQueueDataNotify;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.*;
import emu.grasscutter.server.event.player.PlayerForgeItemEvent;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Real inventory, handbook bridge, registered requests, plugins, and acknowledgement faults. */
class ForgeOutputFlowTest {
    private AbilityTestFixture environment;
    private OutputPlayer player;
    private GameServerPacketHandler dispatcher;
    private java.lang.reflect.Field datastore, pluginField;
    private Object oldDatastore, oldPlugin;
    private PluginManager plugins;
    private final Gson gson = new Gson();
    private Map<Integer, ForgeData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Map<Integer, BattlePassMissionData> oldMissions;
    private int saves, failSave;

    static class OutputPlayer extends InvestigationRewardTest.ClaimPlayer {
        final transient BattlePassManager pass = new BattlePassManager(this);
        transient Runnable afterGrant;
        transient boolean refuseGrant, pretendGrant;
        transient int rejectGrantNumber, grants;
        final transient Inventory outputInventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item) {
                        if (item.getItemId() == 104011
                                || item.getItemId() == 104012
                                || item.getItemId() == 11401) {
                            if (refuseGrant || ++grants == rejectGrantNumber) return false;
                            if (pretendGrant) return true;
                        }
                        boolean result = super.addItem(item);
                        if (result) {
                            var callback = afterGrant;
                            afterGrant = null;
                            if (callback != null) callback.run();
                        }
                        return result;
                    }
                };

        @Override
        public Inventory getInventory() {
            return outputInventory;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return pass;
        }
    }

    private emu.grasscutter.game.player.GuardedPlayerTestStore guardedPlayer;
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldRecipes = new HashMap<>(GameData.getForgeDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        oldMissions = new HashMap<>(GameData.getBattlePassMissionDataMap());
        GameData.getForgeDataMap().clear();
        GameData.getItemDataMap().clear();
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getBattlePassMissionDataMap().clear();
        recipe(104011, 1);
        item(104011, "ITEM_MATERIAL", 1000, 0);
        item(104012, "ITEM_MATERIAL", 1000, 0);
        item(101001, "ITEM_MATERIAL", 1000, 0);
        item(202, "ITEM_VIRTUAL", 0, 0);
        item(11401, "ITEM_WEAPON", 1, 4);
        for (int id : List.of(10003, 10007))
            GameData.getInvestigationDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                    InvestigationData.class));
        target(60016, 10003, 1, "TRIGGER_DO_FORGE", "");
        target(60058, 10007, 2, "TRIGGER_FORGE_WEAPON", "4");
        player = new OutputPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        var passSystem =
                environment
                        .scene
                        .getWorld()
                        .getServer()
                        .getClass()
                        .getDeclaredField("battlePassSystem");
        passSystem.setAccessible(true);
        passSystem.set(
                environment.scene.getWorld().getServer(),
                new BattlePassSystem(environment.scene.getWorld().getServer()));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        guardedPlayer =
                new emu.grasscutter.game.player.GuardedPlayerTestStore(
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
                                return guardedPlayer.datastore().getMapper();
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
        var pluginPath = FileUtils.getPluginPath("");
        java.nio.file.Files.createDirectories(pluginPath);
        try (var files = java.nio.file.Files.list(pluginPath)) {
            assertTrue(
                    files.noneMatch(path -> path.toString().endsWith(".jar")),
                    "Isolated fixture must not load external plugins");
        }
        pluginField = Grasscutter.class.getDeclaredField("pluginManager");
        pluginField.setAccessible(true);
        oldPlugin = pluginField.get(null);
        plugins = new PluginManager();
        pluginField.set(null, plugins);
        itemWrites =
                new emu.grasscutter.game.inventory.ObservedItemTestWrites(
                        player,
                        guardedPlayer.datastore(),
                        DatabaseManager.getGameDatastore(),
                        doc -> {
                            if (++saves == failSave)
                                throw new IllegalStateException("Item confirmation fault");
                        },
                        doc -> {});
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (guardedPlayer != null) guardedPlayer.close();
        if (pluginField != null) pluginField.set(null, oldPlugin);
        if (datastore != null) datastore.set(null, oldDatastore);
        GameData.getForgeDataMap().clear();
        GameData.getForgeDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        GameData.getBattlePassMissionDataMap().clear();
        GameData.getBattlePassMissionDataMap().putAll(oldMissions);
        environment.close();
    }

    private void recipe(int product, int count) {
        GameData.getForgeDataMap()
                .put(
                        11001,
                        gson.fromJson(
                                "{id:11001,playerLevel:2,isDefaultShow:true,queueNum:40,forgeTime:3,forgePoint:400,scoinCost:5,materialItems:[{id:101001,count:2}],resultItemId:"
                                        + product
                                        + ",showItemId:"
                                        + product
                                        + ",resultItemCount:"
                                        + count
                                        + "}",
                                ForgeData.class));
    }

    private void item(int id, String type, int limit, int rank) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'"
                                        + type
                                        + "',materialType:'MATERIAL_NONE',stackLimit:"
                                        + limit
                                        + ",rankLevel:"
                                        + rank
                                        + ",skillAffix:[]}",
                                ItemData.class));
    }

    private void target(int id, int chapter, int total, String type, String filter) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationId:"
                                        + chapter
                                        + ",progress:"
                                        + total
                                        + ",triggerConfig:{triggerType:'"
                                        + type
                                        + "',paramList:['"
                                        + filter
                                        + "','','']}}",
                                InvestigationTargetData.class));
    }

    private ActiveForgeData queue(int count, int elapsed) {
        var queue = new ActiveForgeData();
        queue.setForgeId(11001);
        queue.setCount(count);
        queue.setForgeTime(3);
        queue.setStartTime(Utils.getCurrentSeconds() - elapsed);
        player.getActiveForges().add(queue);
        return queue;
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private int progress(int id) {
        return player.getPlayerProgress().getInvestigation().get(id);
    }

    private void request(int opcode, byte[] body) {
        dispatcher.handle(player.getSession(), opcode, new byte[0], body);
    }

    private void sendTake(int id, int type) {
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder()
                        .setForgeQueueId(id)
                        .setManipulateTypeValue(type)
                        .build()
                        .toByteArray());
    }

    private ForgeQueueManipulateRsp last() throws Exception {
        return ForgeQueueManipulateRsp.parseFrom(
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    private ForgeQueueManipulateRsp take(int id) throws Exception {
        sendTake(id, 0);
        return last();
    }

    private static int receipt(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getOutputState").invoke(queue));
    }

    private void plugin(Consumer<PlayerForgeItemEvent> callback) {
        plugins.registerListener(
                new EventHandler<>(PlayerForgeItemEvent.class)
                        .priority(HandlerPriority.NORMAL)
                        .listener(callback::accept));
    }

    @Test
    void completedMaterialIsStoredBeforeQueueAndHandbookAdvance() throws Exception {
        queue(2, 7);
        var result = take(1);
        assertEquals(0, result.getRetcode());
        assertEquals(2, stock(104011));
        assertTrue(player.getActiveForges().isEmpty());
        assertEquals(1, progress(60016));
        assertEquals(0, progress(60058));
        assertEquals(2, result.getOutputItemList(0).getCount());
        assertEquals(
                player.getInventory().getItemById(104011).getGuid(),
                result.getOutputItemList(0).getGuid());
        assertEquals(4, saves);
    }

    @Test
    void partialCollectionKeepsRemainingQueueWithExactTiming() throws Exception {
        var queue = queue(3, 4);
        int start = queue.getStartTime();
        assertEquals(0, take(1).getRetcode());
        assertEquals(1, stock(104011));
        assertEquals(2, player.getActiveForges().get(0).getCount());
        assertEquals(start + 3, player.getActiveForges().get(0).getStartTime());
        assertEquals(0, receipt(player.getActiveForges().get(0)));
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var notice = ForgeQueueDataNotify.parseFrom(packet.getData());
        assertEquals(2, notice.getForgeQueueMapOrThrow(1).getUnfinishCount());
    }

    @Test
    void unfinishedQueueGetsTypedFailureWithoutClearing() throws Exception {
        var queue = queue(2, 0);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
        assertEquals(0, progress(60016));
    }

    @Test
    void bagFullKeepsReadyQueueForLaterCollection() throws Exception {
        var queue = queue(2, 7);
        assertTrue(player.getInventory().addItem(104011, 999));
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(999, stock(104011));
        assertEquals(0, progress(60016));
        assertEquals(0, receipt(queue));
        assertTrue(player.getInventory().removeItem(104011, 1));
        assertEquals(0, take(1).getRetcode());
        assertEquals(1000, stock(104011));
        assertEquals(1, progress(60016));
    }

    @Test
    void multipleWeaponsHaveDistinctGuidsAndCorrectHandbookCount() throws Exception {
        recipe(11401, 1);
        queue(3, 10);
        var result = take(1);
        assertEquals(0, result.getRetcode());
        assertEquals(3, stock(11401));
        assertEquals(3, result.getOutputItemListCount());
        assertEquals(
                3,
                result.getOutputItemListList().stream().map(h -> h.getGuid()).distinct().count());
        assertTrue(result.getOutputItemListList().stream().allMatch(h -> h.getCount() == 1));
        assertEquals(2, progress(60058));
        assertEquals(1, progress(60016));
    }

    @Test
    void actualWeaponRecipeAdvancesActualHandbookTargets() throws Exception {
        var root = java.nio.file.Path.of("/resources/ExcelBinOutput");
        var recipes =
                JsonUtils.loadToList(root.resolve("ForgeExcelConfigData.json"), ForgeData.class);
        var definition =
                recipes.stream().filter(row -> row.getId() == 13001).findFirst().orElseThrow();
        var weapons =
                JsonUtils.loadToList(root.resolve("WeaponExcelConfigData.json"), ItemData.class);
        var weapon =
                weapons.stream()
                        .filter(row -> row.getId() == definition.getResultItemId())
                        .findFirst()
                        .orElseThrow();
        assertEquals(ItemType.ITEM_WEAPON, weapon.getItemType());
        assertEquals(4, weapon.getRankLevel());
        GameData.getForgeDataMap().put(definition.getId(), definition);
        GameData.getItemDataMap().put(weapon.getId(), weapon);
        var chapters =
                JsonUtils.loadToList(
                        root.resolve("InvestigationConfigData.json"), InvestigationData.class);
        for (var chapter : chapters)
            if (chapter.getId() == 10003 || chapter.getId() == 10007)
                GameData.getInvestigationDataMap().put(chapter.getId(), chapter);
        var targets =
                JsonUtils.loadToList(
                        root.resolve("InvestigationTargetConfigData.json"),
                        InvestigationTargetData.class);
        for (var target : targets)
            if (target.getId() == 60016 || target.getId() == 60058)
                GameData.getInvestigationTargetDataMap().put(target.getId(), target);
        for (int i = 0; i < 2; i++) {
            var queue = queue(1, definition.getForgeTime() + 1);
            queue.setForgeId(definition.getId());
            queue.setForgeTime(definition.getForgeTime());
            assertEquals(0, take(1).getRetcode());
        }
        assertEquals(2, stock(weapon.getId()));
        assertEquals(1, progress(60016));
        assertEquals(2, progress(60058));
    }

    @Test
    void weaponCapacityIsCheckedBeforeAnyGrant() throws Exception {
        recipe(11401, 1);
        queue(2, 7);
        player.getInventory().createInventoryTab(ItemType.ITEM_WEAPON, new EquipInventoryTab(1));
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(0, stock(11401));
        assertEquals(1, player.getActiveForges().size());
        assertEquals(0, progress(60058));
    }

    @Test
    void outputQuantityCountsForgingUnitsRatherThanProductUnits() throws Exception {
        target(60016, 10003, 20, "TRIGGER_DO_FORGE", "");
        recipe(104011, 3);
        queue(2, 7);
        assertEquals(0, take(1).getRetcode());
        assertEquals(6, stock(104011));
        assertEquals(2, progress(60016));
    }

    @Test
    void onlyFourStarWeaponTargetAdvancesForMatchingActualWeapons() throws Exception {
        item(11401, "ITEM_WEAPON", 1, 3);
        recipe(11401, 1);
        queue(2, 7);
        assertEquals(0, take(1).getRetcode());
        assertEquals(2, stock(11401));
        assertEquals(0, progress(60058));
        assertEquals(1, progress(60016));
    }

    @Test
    void missingRecipeCannotGrantOrClear() throws Exception {
        var queue = queue(2, 7);
        GameData.getForgeDataMap().clear();
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
    }

    @Test
    void missingProductCannotGrantOrClear() throws Exception {
        var queue = queue(2, 7);
        GameData.getItemDataMap().remove(104011);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
    }

    @Test
    void overflowingProductCannotGrantOrClear() throws Exception {
        recipe(104011, Integer.MAX_VALUE);
        var queue = queue(2, 7);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidProductAmountDoesNotClear(int count) throws Exception {
        recipe(104011, count);
        var queue = queue(2, 7);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
    }

    @Test
    void overLimitStoredQueueIsRejected() throws Exception {
        var queue = queue(41, 124);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
    }

    @Test
    void corruptProcessingTimeIsRejectedWithoutCrash() throws Exception {
        var queue = queue(2, 7);
        queue.setForgeTime(0);
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(1, player.getActiveForges().size());
    }

    @Test
    void repeatedCollectionNeverGrantsAgain() throws Exception {
        queue(2, 7);
        assertEquals(0, take(1).getRetcode());
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(2, stock(104011));
        assertEquals(1, progress(60016));
    }

    @Test
    void simultaneousCollectionHasOnlyOneSuccessfulDelivery() throws Exception {
        queue(2, 7);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs =
                    List.of(pool.submit(() -> sendTake(1, 0)), pool.submit(() -> sendTake(1, 0)));
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        var responses =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .toList();
        assertEquals(2, responses.size());
        int successes = 0;
        for (var response : responses)
            if (ForgeQueueManipulateRsp.parseFrom(response.getData()).getRetcode() == 0)
                successes++;
        assertEquals(1, successes);
        assertEquals(2, stock(104011));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void acknowledgementFaultPreservesReceiptAndPreventsReplay(int stage) throws Exception {
        queue(2, 7);
        failSave = stage;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(1, player.getActiveForges().size());
        assertNotEquals(0, receipt(player.getActiveForges().get(0)));
        int amount = stock(104011), book = progress(60016);
        failSave = 0;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(amount, stock(104011));
        assertEquals(book, progress(60016));
        assertEquals(stage == 1 ? 0 : 2, amount);
    }

    @Test
    void refusedGrantKeepsReservationAndDoesNotAdvance() throws Exception {
        var queue = queue(2, 7);
        player.refuseGrant = true;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(0, stock(104011));
        assertEquals(0, progress(60016));
        assertEquals(1, receipt(queue));
        player.refuseGrant = false;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(0, stock(104011));
    }

    @Test
    void nominalSuccessWithoutInventoryChangeIsNotAcknowledged() throws Exception {
        var queue = queue(2, 7);
        player.pretendGrant = true;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(0, stock(104011));
        assertEquals(1, receipt(queue));
        assertEquals(0, progress(60016));
    }

    @Test
    void partialWeaponGrantCannotRepeatTheAlreadyStoredWeapon() throws Exception {
        recipe(11401, 1);
        var queue = queue(2, 7);
        player.rejectGrantNumber = 2;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(1, stock(11401));
        assertEquals(0, progress(60058));
        assertEquals(1, receipt(queue));
        player.rejectGrantNumber = 0;
        assertNotEquals(0, take(1).getRetcode());
        assertEquals(1, stock(11401));
    }

    @Test
    void grantReentryCannotCollectOrCancelReservedQueue() throws Exception {
        queue(2, 7);
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, take(1).getRetcode());
                        sendTake(1, 1);
                        assertNotEquals(0, last().getRetcode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, take(1).getRetcode());
        assertEquals(2, stock(104011));
        assertEquals(1, progress(60016));
    }

    @Test
    void grantReentryCannotStartAnotherQueueDuringUnconfirmedDelivery() throws Exception {
        queue(2, 7);
        assertTrue(player.getInventory().addItem(101001, 20));
        player.setMora(1000);
        player.setForgePoints(300000);
        player.afterGrant =
                () ->
                        request(
                                PacketOpcodes.ForgeStartReq,
                                ForgeStartReq.newBuilder()
                                        .setForgeId(11001)
                                        .setForgeCount(1)
                                        .build()
                                        .toByteArray());
        assertEquals(0, take(1).getRetcode());
        assertTrue(player.getActiveForges().isEmpty());
        assertEquals(20, stock(101001));
        assertEquals(1000, player.getMora());
    }

    @Test
    void pluginCanReplaceOutputAndReturnItsActualQuantity() throws Exception {
        queue(2, 7);
        plugin(event -> event.setItemForged(new GameItem(104012, 5)));
        var result = take(1);
        assertEquals(0, result.getRetcode());
        assertEquals(0, stock(104011));
        assertEquals(5, stock(104012));
        assertEquals(104012, result.getOutputItemList(0).getItemId());
        assertEquals(5, result.getOutputItemList(0).getCount());
    }

    @Test
    void canceledPluginOutputLeavesReadyQueueAvailable() throws Exception {
        var queue = queue(2, 7);
        plugin(PlayerForgeItemEvent::cancel);
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
        assertEquals(0, receipt(queue));
    }

    @Test
    void nullPluginOutputCannotClearOrGrant() throws Exception {
        var queue = queue(2, 7);
        plugin(event -> event.setItemForged(null));
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104011));
        assertEquals(0, receipt(queue));
    }

    @Test
    void preownedPluginOutputCannotReuseExistingGuid() throws Exception {
        var queue = queue(2, 7);
        plugin(
                event -> {
                    var item = new GameItem(104012, 2);
                    item.setOwner(player);
                    event.setItemForged(item);
                });
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104012));
    }

    @Test
    void persistedPluginOutputWithoutRuntimeGuidCannotBeReused() throws Exception {
        var queue = queue(2, 7);
        plugin(
                event -> {
                    var item = new GameItem(104012, 2);
                    try {
                        var field = GameItem.class.getDeclaredField("id");
                        field.setAccessible(true);
                        field.set(item, new org.bson.types.ObjectId());
                    } catch (ReflectiveOperationException error) {
                        throw new RuntimeException(error);
                    }
                    event.setItemForged(item);
                });
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(0, stock(104012));
        assertEquals(0, receipt(queue));
    }

    @Test
    void pluginReentryCannotCollectBeforeReservationSave() throws Exception {
        queue(2, 7);
        boolean[] reentered = {false};
        int[] nestedRetcode = {Integer.MAX_VALUE};
        plugin(
                event -> {
                    if (reentered[0]) return;
                    reentered[0] = true;
                    try {
                        nestedRetcode[0] = take(1).getRetcode();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        assertEquals(0, take(1).getRetcode());
        assertTrue(reentered[0]);
        assertNotEquals(0, nestedRetcode[0]);
        assertEquals(2, stock(104011));
    }

    @Test
    void pluginBagOverflowIsRejectedBeforeAnyGrant() throws Exception {
        var queue = queue(2, 7);
        assertTrue(player.getInventory().addItem(104012, 999));
        plugin(event -> event.setItemForged(new GameItem(104012, 2)));
        assertNotEquals(0, take(1).getRetcode());
        assertSame(queue, player.getActiveForges().get(0));
        assertEquals(999, stock(104012));
        assertEquals(0, receipt(queue));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void invalidQueueGetsFailure(int id) throws Exception {
        queue(2, 7);
        assertNotEquals(0, take(id).getRetcode());
        assertEquals(0, stock(104011));
        assertEquals(1, player.getActiveForges().size());
    }

    @Test
    void unknownOperationGetsFailureWithoutMutation() throws Exception {
        queue(2, 7);
        sendTake(1, 2);
        assertNotEquals(0, last().getRetcode());
        assertEquals(0, stock(104011));
        assertEquals(1, player.getActiveForges().size());
    }

    @Test
    void malformedRequestGetsTypedFailure() throws Exception {
        queue(2, 7);
        request(PacketOpcodes.ForgeQueueManipulateReq, new byte[] {120, (byte) 0x80});
        assertNotEquals(0, last().getRetcode());
        assertEquals(0, stock(104011));
    }

    @Test
    void removingFirstQueuePreservesTheOtherQueueAndReindexesNotice() throws Exception {
        queue(2, 7);
        var other = queue(3, 0);
        assertEquals(0, take(1).getRetcode());
        assertEquals(1, player.getActiveForges().size());
        assertSame(other, player.getActiveForges().get(0));
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(
                3,
                ForgeQueueDataNotify.parseFrom(packet.getData())
                        .getForgeQueueMapOrThrow(1)
                        .getUnfinishCount());
    }

    @dev.morphia.annotations.Entity(value = "forge_output_test", useDiscriminator = false)
    static class StoredForge {
        @dev.morphia.annotations.Id String id;
        ActiveForgeData queue;
    }

    @Test
    void pendingOutputReceiptSurvivesMongoReload() throws Exception {
        var queue = queue(2, 7);
        player.refuseGrant = true;
        assertNotEquals(0, take(1).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgeoutput149_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "pending";
                row.queue = queue;
                store.save(row);
                var loaded = store.find(StoredForge.class).first();
                assertEquals(1, receipt(loaded.queue));
                player.getActiveForges().set(0, loaded.queue);
                player.refuseGrant = false;
                assertNotEquals(0, take(1).getRetcode());
                assertEquals(0, stock(104011));
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgeoutput149_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void oldQueueWithoutOutputReceiptCanBeCollected() throws Exception {
        var queue = queue(2, 7);
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgeoutput149_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "old";
                row.queue = queue;
                store.save(row);
                client.getDatabase(name)
                        .getCollection("forge_output_test")
                        .updateOne(
                                com.mongodb.client.model.Filters.eq("_id", "old"),
                                com.mongodb.client.model.Updates.unset("queue.outputState"));
                var loaded = store.find(StoredForge.class).first();
                assertEquals(0, receipt(loaded.queue));
                player.getActiveForges().set(0, loaded.queue);
                assertEquals(0, take(1).getRetcode());
                assertEquals(2, stock(104011));
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgeoutput149_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
