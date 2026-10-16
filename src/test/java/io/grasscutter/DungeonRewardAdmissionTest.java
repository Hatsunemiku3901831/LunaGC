package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.DungeonDropCatalog;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.codex.CodexBookData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassSystem;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

class DungeonRewardAdmissionTest extends QuestTestFixture {
    Player actual;
    GuardedPlayerTestStore playerStore;
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    final Map<Integer, ItemData> oldItems = new HashMap<>();
    final Map<Integer, List<DungeonDropEntry>> oldDrops = new HashMap<>();
    final Set<Integer> absentDrops = new HashSet<>();
    final Map<Integer, DropTableData> oldTables = new HashMap<>();
    final Map<Integer, DisplayItemData> oldDisplays = new HashMap<>();
    Consumer<BasePacket> packetAction = ignored -> {};
    boolean resinUsage;
    CodexBookData oldBook, oldBookMaterial;

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void rewardPlayer() throws Exception {
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new BattlePassSystem(player.server));
        replaceField(
                GameServer.class,
                player.server,
                "inventorySystem",
                new InventorySystem(player.server));
        replaceField(GameServer.class, player.server, "dropSystem", new DropSystem(player.server));
        resinUsage = Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        for (var data :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                        ItemData.class)) {
            if (!Set.of(128, 141, 202, 220007, 104002, 104003, 100215).contains(data.getId()))
                continue;
            data.onLoad();
            oldItems.put(data.getId(), GameData.getItemDataMap().put(data.getId(), data));
        }
        var relic =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/ReliquaryExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(data -> data.getId() == 20002)
                        .findFirst()
                        .orElseThrow();
        relic.onLoad();
        oldItems.put(relic.getId(), GameData.getItemDataMap().put(relic.getId(), relic));
        var book =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/BooksCodexExcelConfigData.json"),
                                CodexBookData.class)
                        .stream()
                        .filter(data -> data.getId() == 50022001)
                        .findFirst()
                        .orElseThrow();
        oldBook = GameData.getCodexBookDataMap().put(book.getId(), book);
        oldBookMaterial = GameData.getCodexBookDataItemIdMap().get(book.getMaterialId());
        book.onLoad();
        for (int id : List.of(103, 5125, 1002)) {
            var old = GameData.getDungeonDropDataMap().get(id);
            if (old == null) absentDrops.add(id);
            else oldDrops.put(id, old);
            GameData.getDungeonDropDataMap().put(id, List.of());
        }
        actual = new Player();
        actual.setUid(10002);
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        packetAction.accept(packet);
                        packets.add(packet);
                    }
                };
        session.setPlayer(actual);
        actual.setSession(session);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        replaceField(Player.class, actual, "battlePassManager", new BattlePassManager(actual));
        playerStore = new GuardedPlayerTestStore(actual, () -> {});
        replaceField(DatabaseManager.class, null, "gameDatastore", playerStore.datastore());
        actual.getProperties().put(PlayerProperty.PROP_PLAYER_RESIN.getId(), 200);
        assertTrue(actual.getInventory().addItem(new GameItem(220007, 1)));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        packets.clear();
    }

    @AfterEach
    void restoreRewards() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        if (oldBook == null) GameData.getCodexBookDataMap().remove(50022001);
        else GameData.getCodexBookDataMap().put(50022001, oldBook);
        if (oldBookMaterial == null) GameData.getCodexBookDataItemIdMap().remove(100215);
        else GameData.getCodexBookDataItemIdMap().put(100215, oldBookMaterial);
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = resinUsage;
        for (var entry : oldItems.entrySet()) {
            if (entry.getValue() == null)
                GameData.getItemDataMap().remove(entry.getKey().intValue());
            else GameData.getItemDataMap().put(entry.getKey().intValue(), entry.getValue());
        }
        for (int id : absentDrops) GameData.getDungeonDropDataMap().remove(id);
        oldDrops.forEach((id, data) -> GameData.getDungeonDropDataMap().put(id.intValue(), data));
        oldTables.forEach(
                (id, data) -> {
                    if (data == null) GameData.getDropTableDataMap().remove(id.intValue());
                    else GameData.getDropTableDataMap().put(id.intValue(), data);
                });
        oldDisplays.forEach(
                (id, data) -> {
                    if (data == null) GameData.getDisplayItemDataMap().remove(id.intValue());
                    else GameData.getDisplayItemDataMap().put(id.intValue(), data);
                });
    }

    DungeonManager dungeon(int id) throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/DungeonExcelConfigData.json"),
                                DungeonData.class)
                        .stream()
                        .filter(row -> row.getId() == id)
                        .findFirst()
                        .orElseThrow();
        replaceField(DungeonData.class, data, "passCond", 0);
        var preview =
                gson.fromJson(
                        "{id:15011,previewItems:[{id:202,count:'10'}]}", RewardPreviewData.class);
        preview.onLoad();
        replaceField(DungeonData.class, data, "rewardPreviewData", preview);
        return new DungeonManager(actual.getScene(), data);
    }

    DungeonDropEntry drop(int item, int count) {
        var row = new DungeonDropEntry();
        row.setCounts(List.of(count));
        row.setProbabilities(List.of(1));
        row.setItems(List.of(item));
        row.setItemProbabilities(List.of(1));
        return row;
    }

    static Stream<Arguments> costs() {
        return Stream.of(
                Arguments.of(103, false),
                Arguments.of(103, true),
                Arguments.of(5125, false),
                Arguments.of(5125, true));
    }

    @ParameterizedTest
    @MethodSource("costs")
    void emptyDropsLeaveResinCondensedResinAndTheClaimAvailable(int id, boolean condensed)
            throws Exception {
        var manager = dungeon(id);
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getWeeklyBossChestNum());
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        assertEquals(0, actual.getNextResinRefresh());
        assertTrue(
                packets.stream()
                        .noneMatch(
                                p -> p.getOpcode() == PacketOpcodes.GadgetAutoPickDropInfoNotify));
        GameData.getDungeonDropDataMap().put(id, List.of(drop(202, 10)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 20 : 10, actual.getMora());
        assertEquals(
                condensed ? 200 : id == 103 ? 170 : 180,
                actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
        assertTrue(manager.hasClaimedDomainReward(actual.getUid()));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 20 : 10, actual.getMora());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonpositiveDropsDoNotChargeOrConsumeAClaim(int count) throws Exception {
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(202, count)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        assertEquals(0, actual.getMora());
    }

    @Test
    void missingItemDefinitionsDoNotChargeForAnUnusableReward() throws Exception {
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(1999999999, 1)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @Test
    void aFailedRollLeavesPaymentAndClaimStateUntouched() throws Exception {
        var invalid = drop(202, 10);
        invalid.setCounts(List.of());
        GameData.getDungeonDropDataMap().put(5125, List.of(invalid));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @Test
    void aZeroOptionalRollDoesNotDiscardOtherPositiveRewards() throws Exception {
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(202, 0), drop(202, 10)));
        var manager = dungeon(5125);
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertEquals(10, actual.getMora());
        assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @ParameterizedTest
    @MethodSource("costs")
    void aFailedPickupNoticeCannotReopenAnAwardedClaim(int id, boolean condensed) throws Exception {
        GameData.getDungeonDropDataMap().put(id, List.of(drop(202, 10)));
        var manager = dungeon(id);
        packetAction =
                packet -> {
                    if (packet.getOpcode() == PacketOpcodes.GadgetAutoPickDropInfoNotify)
                        throw new IllegalStateException("Pickup notification failed");
                };
        assertTrue(assertDoesNotThrow(() -> manager.getStatueDrops(actual, condensed, 0)));
        assertTrue(manager.hasClaimedDomainReward(actual.getUid()));
        assertEquals(id == 103 ? 1 : 0, actual.getWeeklyBossChestNum());
        assertEquals(condensed ? 20 : 10, actual.getMora());
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 20 : 10, actual.getMora());
        assertEquals(
                condensed ? 200 : id == 103 ? 170 : 180,
                actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
    }

    @ParameterizedTest
    @MethodSource("costs")
    void concurrentRequestsAtPickupOnlyAwardOneClaim(int id, boolean condensed) throws Exception {
        // Both competing requests can afford payment; a failed debit must not mask the race.
        assertTrue(actual.getInventory().addItem(new GameItem(220007, 1)));
        GameData.getDungeonDropDataMap().put(id, List.of(drop(202, 10)));
        var manager = dungeon(id);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstNotice = new AtomicBoolean(true);
        packetAction =
                packet -> {
                    if (packet.getOpcode() != PacketOpcodes.GadgetAutoPickDropInfoNotify
                            || !firstNotice.compareAndSet(true, false)) return;
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("Pickup barrier timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> manager.getStatueDrops(actual, condensed, 0));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> manager.getStatueDrops(actual, condensed, 0));
            boolean duplicate;
            try {
                duplicate = second.get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertTrue(first.get(3, TimeUnit.SECONDS));
            assertFalse(duplicate);
            assertTrue(manager.hasClaimedDomainReward(actual.getUid()));
            assertEquals(condensed ? 20 : 10, actual.getMora());
            assertEquals(id == 103 ? 1 : 0, actual.getWeeklyBossChestNum());
            assertEquals(
                    condensed ? 200 : id == 103 ? 170 : 180,
                    actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
            assertEquals(condensed ? 1 : 2, actual.getInventory().getItemCountById(220007));
            assertEquals(
                    1,
                    packets.stream()
                            .filter(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes.GadgetAutoPickDropInfoNotify)
                            .count());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void aReentrantClaimDuringPaymentCannotSpendOrAwardTwice() throws Exception {
        GameData.getDungeonDropDataMap().put(103, List.of(drop(202, 10)));
        var manager = dungeon(103);
        var attempted = new AtomicBoolean();
        var nested = new AtomicReference<Boolean>();
        packetAction =
                packet -> {
                    if (packet.getOpcode() == PacketOpcodes.ResinChangeNotify
                            && attempted.compareAndSet(false, true))
                        nested.set(manager.getStatueDrops(actual, false, 0));
                };
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertEquals(Boolean.FALSE, nested.get());
        assertEquals(10, actual.getMora());
        assertEquals(170, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getWeeklyBossChestNum());
    }

    @Test
    void aRejectedPaymentReleasesTheClaimForAFundedRetry() throws Exception {
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(202, 10)));
        var manager = dungeon(5125);
        actual.getProperties().put(PlayerProperty.PROP_PLAYER_RESIN.getId(), 0);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        assertEquals(0, actual.getMora());
        actual.getProperties().put(PlayerProperty.PROP_PLAYER_RESIN.getId(), 200);
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertEquals(10, actual.getMora());
        assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    void modernDrops(DungeonManager manager, int item, int count, boolean separateRows)
            throws Exception {
        int id = 2100000001;
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < (separateRows ? count : 1); i++)
            rows.add(
                    Map.of(
                            "itemId",
                            item,
                            "countRange",
                            Integer.toString(separateRows ? 1 : count),
                            "weight",
                            10000));
        var table =
                gson.fromJson(
                        gson.toJson(Map.of("id", id, "randomType", 1, "dropVec", rows)),
                        DropTableData.class);
        oldTables.put(id, GameData.getDropTableDataMap().put(id, table));
        replaceField(DungeonData.class, manager.getDungeonData(), "statueDrop", id);
    }

    void materialCapacity(int capacity) throws Exception {
        replaceField(
                MaterialInventoryTab.class,
                actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL),
                "maxCapacity",
                capacity);
    }

    @ParameterizedTest
    @MethodSource("costs")
    void aFullMaterialBagRejectsTheWholeBatchBeforeEitherPayment(int id, boolean condensed)
            throws Exception {
        assertTrue(actual.getInventory().addItem(new GameItem(220007, 1)));
        materialCapacity(1);
        GameData.getDungeonDropDataMap().put(id, List.of(drop(202, 10), drop(104003, 1)));
        var manager = dungeon(id);
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(2, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getMora());
        assertEquals(0, actual.getInventory().getItemCountById(104003));
        assertEquals(0, actual.getWeeklyBossChestNum());
        assertEquals(0, actual.getNextResinRefresh());
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        assertTrue(
                packets.stream()
                        .noneMatch(
                                packet ->
                                        packet.getOpcode()
                                                == PacketOpcodes.GadgetAutoPickDropInfoNotify));
        materialCapacity(2);
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 2 : 1, actual.getInventory().getItemCountById(104003));
        assertEquals(condensed ? 20 : 10, actual.getMora());
        assertEquals(condensed ? 1 : 2, actual.getInventory().getItemCountById(220007));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBatchCannotPartiallyOverflowAnExistingMaterialStack(boolean condensed) throws Exception {
        int limit = GameData.getItemDataMap().get(104003).getStackLimit();
        assertTrue(actual.getInventory().addItem(new GameItem(104003, limit - 1)));
        GameData.getDungeonDropDataMap()
                .put(5125, List.of(drop(202, 10), drop(104003, 1), drop(104003, 1)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(limit - 1, actual.getInventory().getItemCountById(104003));
        assertEquals(0, actual.getMora());
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        actual.getInventory().getItemById(104003).setCount(limit - (condensed ? 4 : 2));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(limit, actual.getInventory().getItemCountById(104003));
    }

    @Test
    void consumingTheLastCondensedResinCanFreeTheRequiredMaterialSlot() throws Exception {
        materialCapacity(1);
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(104003, 1)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertTrue(manager.getStatueDrops(actual, true, 0));
        assertEquals(0, actual.getInventory().getItemCountById(220007));
        assertEquals(2, actual.getInventory().getItemCountById(104003));
        assertEquals(1, actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).getSize());
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void disabledResinUsageDoesNotPretendToFreeACondensedResinSlot() throws Exception {
        materialCapacity(1);
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(104003, 1)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, true, 0));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getInventory().getItemCountById(104003));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @Test
    void distinctMaterialsNeedSeparateSlotsButDuplicateRowsShareOneStack() throws Exception {
        materialCapacity(2);
        var manager = dungeon(5125);
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(104002, 1), drop(104003, 1)));
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(0, actual.getInventory().getItemCountById(104002));
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(104003, 1), drop(104003, 1)));
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertEquals(2, actual.getInventory().getItemCountById(104003));
        assertEquals(2, actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).getSize());
    }

    @Test
    void aFullRelicBagCannotConsumePaymentOrAwardOnlyTheCurrency() throws Exception {
        actual.getInventory().createInventoryTab(ItemType.ITEM_RELIQUARY, new EquipInventoryTab(0));
        GameData.getDungeonDropDataMap().put(103, List.of(drop(202, 10), drop(20002, 1)));
        var manager = dungeon(103);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(0, actual.getMora());
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, actual.getWeeklyBossChestNum());
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void modernRelicQuantitiesNeedIndividualSlotsAndIndividualItems(boolean separateRows)
            throws Exception {
        var manager = dungeon(103);
        modernDrops(manager, 20002, 2, separateRows);
        actual.getInventory().createInventoryTab(ItemType.ITEM_RELIQUARY, new EquipInventoryTab(1));
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(0, actual.getInventory().getItemCountById(20002));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        actual.getInventory().createInventoryTab(ItemType.ITEM_RELIQUARY, new EquipInventoryTab(2));
        assertTrue(manager.getStatueDrops(actual, false, 0));
        var relics =
                actual.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemId() == 20002)
                        .toList();
        assertEquals(2, relics.size());
        assertEquals(2, relics.stream().map(GameItem::getGuid).distinct().count());
        assertTrue(relics.stream().allMatch(item -> item.getCount() == 1));
    }

    @Test
    void aRewardLargerThanANewStackLimitCannotBeSilentlyClampedDuringRoll() throws Exception {
        int limit = GameData.getItemDataMap().get(104003).getStackLimit();
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(104003, limit + 1)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(0, actual.getInventory().getItemCountById(104003));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void aModernRewardLargerThanTheStackLimitIsNotClampedBeforeAdmission() throws Exception {
        int limit = GameData.getItemDataMap().get(104003).getStackLimit();
        var manager = dungeon(103);
        modernDrops(manager, 104003, limit + 1, false);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(0, actual.getInventory().getItemCountById(104003));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void overflowingMoraCannotWrapTheBalanceAfterPayment() throws Exception {
        actual.getProperties().put(PlayerProperty.PROP_PLAYER_SCOIN.getId(), Integer.MAX_VALUE - 5);
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(202, 10)));
        var manager = dungeon(5125);
        assertFalse(manager.getStatueDrops(actual, false, 0));
        assertEquals(Integer.MAX_VALUE - 5, actual.getMora());
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anAutomaticPlotBookDoesNotNeedAMaterialSlot(boolean alreadyOwned) throws Exception {
        materialCapacity(1);
        if (alreadyOwned) actual.getCodex().getUnlockedBook().add(100215);
        GameData.getDungeonDropDataMap().put(1002, List.of(drop(202, 10), drop(100215, 1)));
        var manager = dungeon(1002);
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertTrue(actual.getCodex().getUnlockedBook().contains(100215));
        assertEquals(0, actual.getInventory().getItemCountById(100215));
        assertEquals(1, actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).getSize());
        assertEquals(10, actual.getMora());
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(
                alreadyOwned ? 0 : 1,
                packets.stream()
                        .filter(packet -> packet.getOpcode() == PacketOpcodes.CodexDataUpdateNotify)
                        .count());
    }

    @Test
    void virtualActivityPreviewItemsDoNotConsumeSlotsOrCancelOtherRewards() throws Exception {
        materialCapacity(1);
        GameData.getDungeonDropDataMap()
                .put(5125, List.of(drop(128, 1), drop(141, 1), drop(202, 10)));
        var manager = dungeon(5125);
        assertTrue(manager.getStatueDrops(actual, false, 0));
        assertEquals(10, actual.getMora());
        assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).getSize());
    }

    @Test
    void theLoadedLostValleyCatalogGrantsItsActualRewardsInsteadOfTheDisplayPreview()
            throws Exception {
        var previous =
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(
                        GameData.getDungeonDropDataMap());
        try {
            DungeonDropCatalog.reload();
            var groups = GameData.getDungeonDropDataMap().get(5128);
            assertNotNull(groups);
            var ids = new HashSet<Integer>();
            groups.forEach(group -> ids.addAll(group.getItems()));
            for (var name : List.of("Material", "Reliquary")) {
                for (var data :
                        JsonUtils.loadToList(
                                Path.of(
                                        "resources/ExcelBinOutput/"
                                                + name
                                                + "ExcelConfigData.json"),
                                ItemData.class)) {
                    if (!ids.contains(data.getId())) continue;
                    data.onLoad();
                    if (!oldItems.containsKey(data.getId()))
                        oldItems.put(data.getId(), GameData.getItemDataMap().get(data.getId()));
                    GameData.getItemDataMap().put(data.getId(), data);
                }
            }
            assertTrue(dungeon(5128).getStatueDrops(actual, false, 0));
            int mora =
                    groups.stream()
                            .filter(group -> group.getItems().equals(List.of(202)))
                            .findFirst()
                            .orElseThrow()
                            .getCounts()
                            .get(0);
            assertNotEquals(10, mora); // This fixture's display-only preview contains ten Mora.
            assertEquals(mora, actual.getMora());
            assertEquals(100, actual.getExp());
            assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
            long fiveStar =
                    actual.getInventory().getItems().values().stream()
                            .filter(
                                    item ->
                                            item.getItemData().getItemType()
                                                            == ItemType.ITEM_RELIQUARY
                                                    && item.getItemData().getRankLevel() == 5)
                            .count();
            assertTrue(fiveStar >= 1 && fiveStar <= 2);
        } finally {
            GameData.getDungeonDropDataMap().clear();
            GameData.getDungeonDropDataMap().putAll(previous);
        }
    }

    static Stream<Arguments> fallbackRewards() {
        return java.util.stream.IntStream.rangeClosed(5060, 5067)
                .boxed()
                .flatMap(id -> Stream.of(Arguments.of(id, false), Arguments.of(id, true)));
    }

    @ParameterizedTest
    @MethodSource("fallbackRewards")
    void fallbackDomainsGrantOnlyTheRelicSetsAdvertisedForTheirOwnDifficulty(
            int id, boolean condensed) throws Exception {
        var manager = dungeon(id);
        var preview =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/ExcelBinOutput/RewardPreviewExcelConfigData.json"),
                                RewardPreviewData.class)
                        .stream()
                        .filter(
                                row ->
                                        row.getId()
                                                == manager.getDungeonData()
                                                        .getPassRewardPreviewID())
                        .findFirst()
                        .orElseThrow();
        preview.onLoad();
        replaceField(DungeonData.class, manager.getDungeonData(), "rewardPreviewData", preview);
        var displays = new HashMap<Integer, DisplayItemData>();
        JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/DisplayItemExcelConfigData.json"),
                        DisplayItemData.class)
                .forEach(row -> displays.put(row.getId(), row));
        var allowed = new HashMap<Integer, Set<Integer>>();
        for (var item : preview.getRawPreviewItems()) {
            var data = displays.get(item.getId());
            if (data == null || data.getParam() <= 0 || data.getRankLevel() <= 0) continue;
            oldDisplays.put(data.getId(), GameData.getDisplayItemDataMap().put(data.getId(), data));
            allowed.computeIfAbsent(data.getRankLevel(), ignored -> new HashSet<>())
                    .add(data.getParam());
        }
        assertFalse(allowed.isEmpty());
        for (var name : List.of("Material", "Reliquary")) {
            for (var data :
                    JsonUtils.loadToList(
                            Path.of("resources/ExcelBinOutput/" + name + "ExcelConfigData.json"),
                            ItemData.class)) {
                if (!data.isEquip() && !Set.of(102, 105, 202).contains(data.getId())) continue;
                data.onLoad();
                if (!oldItems.containsKey(data.getId()))
                    oldItems.put(data.getId(), GameData.getItemDataMap().get(data.getId()));
                GameData.getItemDataMap().put(data.getId(), data);
            }
        }
        var tables = new HashMap<Integer, DropTableData>();
        for (var name :
                List.of("DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"))
            JsonUtils.loadToList(Path.of("resources/Server/" + name), DropTableData.class)
                    .forEach(row -> tables.put(row.getId(), row));
        var pending = new ArrayDeque<Integer>();
        pending.add(
                emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager
                        .getStatueDropOverride(id));
        var seen = new HashSet<Integer>();
        while (!pending.isEmpty()) {
            int tableId = pending.remove();
            if (!seen.add(tableId)) continue;
            var table = tables.get(tableId);
            assertNotNull(table);
            oldTables.put(tableId, GameData.getDropTableDataMap().put(tableId, table));
            table.getDropVec().stream()
                    .filter(row -> row.getWeight() > 0 && tables.containsKey(row.getId()))
                    .forEach(row -> pending.add(row.getId()));
        }
        var generator = actual.getServer().getDropSystem();
        var randomField = DropSystem.class.getDeclaredField("rand");
        randomField.setAccessible(true);
        var random = (Random) randomField.get(generator);
        random.setSeed(id);
        var original =
                generator.handleDungeonRewardDrop(
                        emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager
                                .getStatueDropOverride(id),
                        condensed);
        random.setSeed(id);
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        var relics =
                actual.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemData().getItemType() == ItemType.ITEM_RELIQUARY)
                        .toList();
        assertFalse(relics.isEmpty());
        assertEquals(relicShape(original), relicShape(relics));
        for (var relic : relics) {
            var data = relic.getItemData();
            assertTrue(
                    allowed.getOrDefault(data.getRankLevel(), Set.of()).contains(data.getSetId()),
                    "Dungeon "
                            + id
                            + " dropped unrelated set "
                            + data.getSetId()
                            + " at rarity "
                            + data.getRankLevel());
            assertEquals(1, relic.getCount());
        }
        int mora =
                Arrays.stream(preview.getPreviewItems())
                        .filter(item -> item.getId() == 202)
                        .findFirst()
                        .orElseThrow()
                        .getCount();
        assertEquals(mora * (condensed ? 2 : 1), actual.getMora());
        assertEquals(condensed ? 200 : 180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
    }

    Map<String, Long> relicShape(Collection<GameItem> items) {
        return items.stream()
                .filter(item -> item.getItemData().getItemType() == ItemType.ITEM_RELIQUARY)
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                item -> {
                                    var data = item.getItemData();
                                    return data.getRankLevel()
                                            + ":"
                                            + data.getEquipType()
                                            + ":"
                                            + data.getMainPropDepotId()
                                            + ":"
                                            + data.getAppendPropDepotId()
                                            + ":"
                                            + data.getAppendPropNum()
                                            + ":"
                                            + data.getMaxLevel();
                                },
                                TreeMap::new,
                                java.util.stream.Collectors.counting()));
    }
}
