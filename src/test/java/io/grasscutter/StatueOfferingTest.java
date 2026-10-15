package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.LevelupCityRspOuterClass.LevelupCityRsp;
import emu.grasscutter.server.event.*;
import emu.grasscutter.server.event.player.PlayerLevelStatueEvent;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual city/cost/reward tables through the offering entry point; no invented reward amounts. */
class StatueOfferingTest extends QuestTestFixture {
    static List<CityData> cities;
    static List<CityLevelupData> levels;
    static List<StatuePromoteData> legacy;
    static List<RewardData> rewards;
    static List<ItemData> items;
    SotSManager manager;
    final List<ItemParamData> granted = new ArrayList<>();
    final AtomicInteger saves = new AtomicInteger(), events = new AtomicInteger();
    boolean rejectRemoval, grantThrows;
    InventoryTab restrictedMaterialTab;
    int failSaveAt;
    final List<Runnable> dataRestores = new ArrayList<>();

    <K, V> void put(Map<K, V> map, K key, V value) {
        V previous = map.put(key, value);
        dataRestores.add(
                () -> {
                    if (previous == null) map.remove(key);
                    else map.put(key, previous);
                });
    }

    @AfterEach
    void restoreStatueData() {
        Collections.reverse(dataRestores);
        dataRestores.forEach(Runnable::run);
    }

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources/ExcelBinOutput");
        cities = JsonUtils.loadToList(root.resolve("CityConfigData.json"), CityData.class);
        levels =
                JsonUtils.loadToList(
                        root.resolve("CityLevelupConfigData.json"), CityLevelupData.class);
        legacy =
                JsonUtils.loadToList(
                        root.resolve("StatuePromoteExcelConfigData.json"), StatuePromoteData.class);
        var ids = new HashSet<Integer>();
        levels.forEach(row -> ids.add(row.getRewardId()));
        rewards =
                JsonUtils.loadToList(root.resolve("RewardExcelConfigData.json"), RewardData.class)
                        .stream()
                        .filter(row -> ids.contains(row.getId()))
                        .toList();
        rewards.forEach(RewardData::onLoad);
        ids.clear();
        levels.forEach(row -> ids.add(row.getCostItemId()));
        rewards.forEach(row -> row.getRewardItemList().forEach(item -> ids.add(item.getId())));
        items =
                JsonUtils.loadToList(root.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(row -> ids.contains(row.getId()))
                        .toList();
        items.forEach(ItemData::onLoad);
        assertEquals(80, levels.size());
        assertEquals(36, legacy.size());
    }

    @BeforeEach
    void statue() throws Exception {
        player.getSession().setPlayer(player);
        cities.forEach(row -> put(GameData.getCityDataMap(), row.getId(), row));
        levels.forEach(row -> put(GameData.getCityLevelupDataMap(), row.getId(), row));
        legacy.forEach(row -> put(GameData.getStatuePromoteDataMap(), row.getId(), row));
        rewards.forEach(row -> put(GameData.getRewardDataMap(), row.getId(), row));
        items.forEach(row -> put(GameData.getItemDataMap(), row.getId(), row));
        replaceField(
                emu.grasscutter.game.player.Player.class,
                player,
                "inventory",
                new Inventory(player) {
                    @Override
                    public InventoryTab getInventoryTab(ItemType type) {
                        return type == ItemType.ITEM_MATERIAL && restrictedMaterialTab != null
                                ? restrictedMaterialTab
                                : super.getInventoryTab(type);
                    }

                    @Override
                    public void addItemParamDatas(
                            Collection<ItemParamData> data, ActionReason reason) {
                        granted.addAll(data);
                    }

                    @Override
                    public synchronized boolean removeItem(GameItem item, int count) {
                        return !rejectRemoval && super.removeItem(item, count);
                    }
                });
        manager =
                new SotSManager(
                        player,
                        () -> {
                            if (saves.incrementAndGet() == failSaveAt)
                                throw new IllegalStateException("Injected statue save failure");
                        },
                        data -> {
                            granted.addAll(data);
                            if (grantThrows)
                                throw new IllegalStateException("Injected reward failure");
                        },
                        item -> {});
        replaceField(emu.grasscutter.game.player.Player.class, player, "sotsManager", manager);
        var plugins = new emu.grasscutter.plugin.PluginManager();
        replaceField(Grasscutter.class, null, "pluginManager", plugins);
        plugins.registerListener(
                new EventHandler<>(PlayerLevelStatueEvent.class)
                        .priority(HandlerPriority.NORMAL)
                        .listener(event -> events.incrementAndGet()));
        player.setProperty(PlayerProperty.PROP_MAX_STAMINA, 10000);
        player.packets.clear();
    }

    GameItem stock(int id, int count) {
        var item =
                new GameItem(GameData.getItemDataMap().get(id), count) {
                    @Override
                    public void save() {}
                };
        item.setOwner(player);
        player.getInventory().getItems().put(item.getGuid(), item);
        player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        return item;
    }

    LevelupCityRsp response() throws Exception {
        var packets =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.LevelupCityRsp)
                        .toList();
        assertFalse(packets.isEmpty(), "Every request must finish with a city response");
        return LevelupCityRsp.parseFrom(packets.get(packets.size() - 1).getData());
    }

    int reward(int id) {
        return granted.stream()
                .filter(x -> x.getId() == id)
                .mapToInt(ItemParamData::getCount)
                .sum();
    }

    @Test
    void absentOculiCannotBecomeAnUpgrade() throws Exception {
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(0, manager.getCityInfo(1).getNumCrystal());
        assertTrue(granted.isEmpty());
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void currentCityTableIsRegisteredWithTheActualResourceLoader() {
        assertTrue(ResourceLoader.getResourceDefClasses().contains(CityLevelupData.class));
        assertSame(
                GameData.getCityLevelupDataMap(),
                GameData.getMapByResourceDef(CityLevelupData.class));
        assertEquals(107030, GameData.getCityLevelupData(7, 2).getCostItemId());
    }

    @Test
    void realOfferingRequestReachesTheCurrentRegionUpgradePath() throws Exception {
        stock(107023, 20);
        var request =
                emu.grasscutter.net.proto.LevelupCityReqOuterClass.LevelupCityReq.newBuilder()
                        .setSceneId(3)
                        .setAreaId(33)
                        .setItemNum(20)
                        .build();
        new emu.grasscutter.server.packet.recv.HandlerLevelupCityReq()
                .handle(player.getSession(), new byte[0], request.toByteArray());
        assertEquals(0, response().getRetcode());
        assertEquals(2, response().getCityInfo().getLevel());
        assertEquals(60, reward(201));
    }

    @Test
    void offerAllCrossesLevelsAndRetainsThePartialNextLevel() throws Exception {
        stock(107001, 6);
        manager.levelUpSotS(1, 3, 6);
        assertEquals(3, manager.getCityInfo(1).getLevel());
        assertEquals(3, manager.getCityInfo(1).getNumCrystal());
        assertEquals(0, player.getInventory().getItemCountById(107001));
        assertEquals(20, reward(201));
        assertEquals(200, reward(102));
        assertEquals(11400, player.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        assertEquals(0, response().getRetcode());
    }

    @Test
    void offersCannotSpendMoreThanTheActualStack() throws Exception {
        manager.getCityInfo(1).setLevel(3);
        stock(107001, 2);
        manager.levelUpSotS(1, 3, 100);
        assertEquals(3, manager.getCityInfo(1).getLevel());
        assertEquals(2, manager.getCityInfo(1).getNumCrystal());
        assertTrue(granted.isEmpty());
    }

    @Test
    void fontaineUsesItsActualCostAndProtectedRewardItems() throws Exception {
        stock(107023, 20);
        manager.levelUpSotS(33, 3, 20);
        assertEquals(2, manager.getCityInfo(5).getLevel());
        assertEquals(0, player.getInventory().getItemCountById(107023));
        assertEquals(60, reward(201));
        assertEquals(1, reward(107025));
        assertEquals(0, response().getRetcode());
    }

    @Test
    void currentCityEightUsesResourceRewardsRatherThanAPlaceholder() throws Exception {
        stock(107035, 20);
        manager.levelUpSotS(55, 3, 20);
        assertEquals(2, manager.getCityInfo(8).getLevel());
        assertEquals(100, reward(201));
        assertEquals(5, reward(306));
        assertEquals(1, reward(224));
        assertEquals(0, response().getRetcode());
    }

    @Test
    void wrongSceneCannotSpendOculi() throws Exception {
        stock(107001, 2);
        manager.levelUpSotS(1, 99, 1);
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(2, player.getInventory().getItemCountById(107001));
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void aCompletedRequestFiresOneLevelEventAndSavesItsProgress() {
        stock(107001, 1);
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, events.get());
        assertTrue(saves.get() > 0);
    }

    @Test
    void missingRewardDefinitionRejectsBeforeSpending() throws Exception {
        put(GameData.getRewardDataMap(), 210312, null);
        stock(107001, 1);
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(1, player.getInventory().getItemCountById(107001));
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void refusedInventoryRemovalCannotCreateProgressOrRewards() throws Exception {
        stock(107001, 1);
        rejectRemoval = true;
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(1, player.getInventory().getItemCountById(107001));
        assertTrue(granted.isEmpty());
        assertNotEquals(0, response().getRetcode());
        assertNotNull(manager.getCityInfo(1).getPendingOffer());
    }

    @Test
    void uncertainReservationDoesNotSpendAndCannotBeRetriedAutomatically() throws Exception {
        stock(107001, 1);
        failSaveAt = 1;
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, player.getInventory().getItemCountById(107001));
        assertTrue(granted.isEmpty());
        assertNotNull(manager.getCityInfo(1).getPendingOffer());
        failSaveAt = 0;
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, saves.get());
        assertTrue(granted.isEmpty());
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void interruptedRewardDeliveryCannotPayTheSameRewardTwice() throws Exception {
        stock(107001, 1);
        grantThrows = true;
        manager.levelUpSotS(1, 3, 1);
        int delivered = reward(201);
        assertEquals(10, delivered);
        assertEquals(2, manager.getCityInfo(1).getPendingOffer().getState());
        grantThrows = false;
        manager.levelUpSotS(1, 3, 1);
        assertEquals(delivered, reward(201));
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void uncertainFinalSaveRetainsAnAppliedReceiptWithoutGrantingAgain() throws Exception {
        stock(107001, 1);
        failSaveAt = 4;
        manager.levelUpSotS(1, 3, 1);
        assertEquals(2, manager.getCityInfo(1).getLevel());
        assertEquals(3, manager.getCityInfo(1).getPendingOffer().getState());
        manager.levelUpSotS(1, 3, 1);
        assertEquals(10, reward(201));
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void staminaRemainsCappedAndMaxLevelLeavesUnusedOculiInInventory() throws Exception {
        player.setProperty(PlayerProperty.PROP_MAX_STAMINA, 23900);
        manager.getCityInfo(1).setLevel(9);
        stock(107001, 20);
        manager.levelUpSotS(1, 3, 20);
        assertEquals(10, manager.getCityInfo(1).getLevel());
        assertEquals(5, player.getInventory().getItemCountById(107001));
        assertEquals(24000, player.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        int size = granted.size();
        manager.levelUpSotS(1, 3, 20);
        assertEquals(size, granted.size());
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void zeroRequestAndUnknownAreaAlwaysReceiveAnErrorResponse() throws Exception {
        stock(107001, 1);
        manager.levelUpSotS(1, 3, 0);
        assertNotEquals(0, response().getRetcode());
        manager.levelUpSotS(Integer.MAX_VALUE, 3, 1);
        assertNotEquals(0, response().getRetcode());
        assertEquals(1, player.getInventory().getItemCountById(107001));
    }

    @Test
    void concurrentOfferRequestsShareOneInventoryAndCannotCreateExtraRewards() throws Exception {
        stock(107001, 1);
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a =
                    executor.submit(
                            () -> {
                                gate.await();
                                manager.levelUpSotS(1, 3, 1);
                                return null;
                            });
            var b =
                    executor.submit(
                            () -> {
                                gate.await();
                                manager.levelUpSotS(1, 3, 1);
                                return null;
                            });
            a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            b.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(2, manager.getCityInfo(1).getLevel());
            assertEquals(10, reward(201));
            assertEquals(0, player.getInventory().getItemCountById(107001));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void malformedWireRequestStillFinishesWithATypedResponse() throws Exception {
        assertDoesNotThrow(
                () ->
                        new emu.grasscutter.server.packet.recv.HandlerLevelupCityReq()
                                .handle(
                                        player.getSession(),
                                        new byte[0],
                                        new byte[] {0x2a, (byte) 0x80}));
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void aConsumedOculusStackFreesTheSlotNeededForTheSigilReward() throws Exception {
        restrictedMaterialTab = new MaterialInventoryTab(1);
        stock(107001, 1);
        manager.levelUpSotS(1, 3, 1);
        assertEquals(2, manager.getCityInfo(1).getLevel());
        assertEquals(5, reward(305));
        assertEquals(0, response().getRetcode());
    }

    @Test
    void rewardCapacityIsCheckedBeforeTheOfferingIsPaid() throws Exception {
        restrictedMaterialTab = new MaterialInventoryTab(1);
        stock(107001, 2);
        manager.levelUpSotS(1, 3, 1);
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(2, player.getInventory().getItemCountById(107001));
        assertTrue(granted.isEmpty());
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void cancelledStaminaUpdateRetainsAReceiptInsteadOfReportingComplete() throws Exception {
        Grasscutter.getPluginManager()
                .registerListener(
                        new EventHandler<>(
                                        emu.grasscutter.server.event.player
                                                .PlayerPropertyChangeEvent.class)
                                .priority(HandlerPriority.NORMAL)
                                .listener(
                                        event -> {
                                            if (event.getProperty()
                                                    == PlayerProperty.PROP_MAX_STAMINA)
                                                event.cancel();
                                        }));
        stock(107001, 1);
        manager.levelUpSotS(1, 3, 1);
        assertNotEquals(0, response().getRetcode());
        assertNotNull(manager.getCityInfo(1).getPendingOffer());
        assertEquals(10000, player.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        assertEquals(1, manager.getCityInfo(1).getLevel());
        assertEquals(0, events.get());
    }
}
