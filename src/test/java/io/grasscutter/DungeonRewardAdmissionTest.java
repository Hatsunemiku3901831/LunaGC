package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.ConfigContainer.GameOptions.DungeonRewardOptions;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.DungeonDropCatalog;
import emu.grasscutter.data.DungeonDropReferenceCatalog;
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
    final Map<Integer, DungeonData> oldDungeons = new HashMap<>();
    Consumer<BasePacket> packetAction = ignored -> {};
    boolean resinUsage;
    DungeonRewardOptions oldDungeonRewards;
    CodexBookData oldBook, oldBookMaterial;

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void rewardPlayer() throws Exception {
        var options = emu.grasscutter.Grasscutter.getConfig().server.game.gameOptions;
        oldDungeonRewards = options.dungeonRewards;
        options.dungeonRewards = new DungeonRewardOptions();
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
            if (!Set.of(
                            128, 141, 202, 220007, 104002, 104003, 100215, 104303, 114001, 105002,
                            105003, 105005)
                    .contains(data.getId())) continue;
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
        emu.grasscutter.Grasscutter.getConfig().server.game.gameOptions.dungeonRewards =
                oldDungeonRewards;
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
        oldDungeons.forEach(
                (id, data) -> {
                    if (data == null) GameData.getDungeonDataMap().remove(id.intValue());
                    else GameData.getDungeonDataMap().put(id.intValue(), data);
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

    @Test
    void aChangedCatalogIsAppliedBeforeTheNextRealRewardClaim(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var previous =
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(
                        GameData.getDungeonDropDataMap());
        var filesField = DungeonDropCatalog.class.getDeclaredField("FILES");
        filesField.setAccessible(true);
        var files = filesField.get(null);
        var sourceField = files.getClass().getDeclaredField("source");
        var stateField = files.getClass().getDeclaredField("state");
        sourceField.setAccessible(true);
        stateField.setAccessible(true);
        var source = sourceField.get(files);
        var state = stateField.get(files);
        Path path = directory.resolve("DungeonDrop.json");
        try {
            java.nio.file.Files.writeString(
                    path, "[{dungeonId:5128,drops:[{counts:[17],items:[202]}]}]");
            sourceField.set(files, (java.util.function.Supplier<Path>) () -> path);
            var load = files.getClass().getDeclaredMethod("reload");
            load.setAccessible(true);
            load.invoke(files);
            var modified = java.nio.file.Files.getLastModifiedTime(path).toMillis();
            java.nio.file.Files.writeString(
                    path, "[{dungeonId:5128,drops:[{counts:[23],items:[202]}]}]");
            java.nio.file.Files.setLastModifiedTime(
                    path, java.nio.file.attribute.FileTime.fromMillis(modified + 2000));
            assertTrue(dungeon(5128).getStatueDrops(actual, false, 0));
            assertEquals(23, actual.getMora());
            assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        } finally {
            sourceField.set(files, source);
            stateField.set(files, state);
            GameData.getDungeonDropDataMap().clear();
            GameData.getDungeonDropDataMap().putAll(previous);
        }
    }

    @Test
    void aCatalogReloadBetweenRoutingAndRollingKeepsOneClaimOnTheSameSnapshot() throws Exception {
        var previous =
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(
                        GameData.getDungeonDropDataMap());
        var filesField = DungeonDropCatalog.class.getDeclaredField("FILES");
        filesField.setAccessible(true);
        var files = filesField.get(null);
        var stateField = files.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        var state = stateField.get(files);
        var changed = new AtomicBoolean();
        try {
            DungeonDropCatalog.reload(
                    new java.io.StringReader(
                            "[{dungeonId:4668,drops:[{counts:[17],items:[202]}]}]"));
            var routing =
                    new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<List<DungeonDropEntry>>() {
                        void update() {
                            if (!changed.compareAndSet(false, true)) return;
                            try {
                                DungeonDropCatalog.reload(
                                        new java.io.StringReader(
                                                "[{dungeonId:999,drops:[{counts:[23],items:[202]}]}]"));
                            } catch (java.io.IOException failed) {
                                throw new AssertionError(failed);
                            }
                        }

                        @Override
                        public boolean containsKey(int key) {
                            boolean value = super.containsKey(key);
                            if (key == 4668) update();
                            return value;
                        }

                        @Override
                        public List<DungeonDropEntry> get(int key) {
                            var value = super.get(key);
                            if (key == 4668) update();
                            return value;
                        }
                    };
            routing.put(4668, List.of(drop(202, 17)));
            replaceField(GameData.class, null, "dungeonDropDataMap", routing);
            assertTrue(dungeon(4668).getStatueDrops(actual, false, 0));
            assertTrue(changed.get());
            assertFalse(GameData.getDungeonDropDataMap().containsKey(4668));
            assertEquals(17, actual.getMora());
            assertEquals(180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        } finally {
            stateField.set(files, state);
            GameData.getDungeonDropDataMap().clear();
            GameData.getDungeonDropDataMap().putAll(previous);
        }
    }

    static Stream<Arguments> fallbackRewards() {
        return java.util.stream.IntStream.rangeClosed(5060, 5067)
                .boxed()
                .flatMap(id -> Stream.of(Arguments.of(id, false), Arguments.of(id, true)));
    }

    static Stream<Arguments> missingTalentRewards() {
        return java.util.stream.IntStream.concat(
                        java.util.stream.IntStream.rangeClosed(4651, 4662),
                        java.util.stream.IntStream.rangeClosed(4691, 4702))
                .boxed()
                .flatMap(id -> Stream.of(Arguments.of(id, false), Arguments.of(id, true)));
    }

    DungeonManager talentDungeon(int id) throws Exception {
        int sourceId = 4210 + (id - (id < 4691 ? 4651 : 4691)) % 4;
        return materialDungeon(id, sourceId);
    }

    DungeonManager materialDungeon(int id, int sourceId) throws Exception {
        var manager = dungeon(id);
        var source =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/DungeonExcelConfigData.json"),
                                DungeonData.class)
                        .stream()
                        .filter(row -> row.getId() == sourceId)
                        .findFirst()
                        .orElseThrow();
        oldDungeons.put(sourceId, GameData.getDungeonDataMap().put(sourceId, source));
        var previews =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/RewardPreviewExcelConfigData.json"),
                        RewardPreviewData.class);
        for (var data : List.of(source, manager.getDungeonData())) {
            var preview =
                    previews.stream()
                            .filter(row -> row.getId() == data.getPassRewardPreviewID())
                            .findFirst()
                            .orElseThrow();
            preview.onLoad();
            replaceField(DungeonData.class, data, "rewardPreviewData", preview);
        }
        for (var item :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                        ItemData.class)) {
            if (item.getMaterialType() != MaterialType.MATERIAL_AVATAR_MATERIAL
                    && !Set.of(102, 105, 202).contains(item.getId())) continue;
            item.onLoad();
            if (!oldItems.containsKey(item.getId()))
                oldItems.put(item.getId(), GameData.getItemDataMap().get(item.getId()));
            GameData.getItemDataMap().put(item.getId(), item);
        }
        var tables = new HashMap<Integer, DropTableData>();
        for (var name :
                List.of("DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"))
            JsonUtils.loadToList(Path.of("resources/Server/" + name), DropTableData.class)
                    .forEach(row -> tables.put(row.getId(), row));
        var pending = new ArrayDeque<Integer>();
        var seen = new HashSet<Integer>();
        pending.add(source.getStatueDrop());
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
        assertFalse(
                GameData.getDropTableDataMap()
                        .containsKey(manager.getDungeonData().getStatueDrop()));
        assertFalse(GameData.getDungeonDropDataMap().containsKey(id));
        absentDrops.add(id);
        return manager;
    }

    @ParameterizedTest
    @MethodSource("missingTalentRewards")
    void missingTalentTablesPreserveTheOriginalRollAndGrantTheCurrentRegionsMaterials(
            int id, boolean condensed) throws Exception {
        int sourceId = 4210 + (id - (id < 4691 ? 4651 : 4691)) % 4;
        assertMaterialRoll(id, sourceId, condensed);
    }

    static Stream<Arguments> missingWeaponRewards() {
        return Stream.of(4671, 4707)
                .flatMap(
                        first ->
                                java.util.stream.IntStream.range(0, 12)
                                        .boxed()
                                        .flatMap(
                                                offset ->
                                                        Stream.of(
                                                                Arguments.of(
                                                                        first + offset,
                                                                        4310 + offset % 4,
                                                                        false),
                                                                Arguments.of(
                                                                        first + offset,
                                                                        4310 + offset % 4,
                                                                        true))));
    }

    @ParameterizedTest
    @MethodSource("missingWeaponRewards")
    void missingWeaponTablesPreserveTheOriginalRollAndGrantTheCurrentRegionsMaterials(
            int id, int sourceId, boolean condensed) throws Exception {
        assertMaterialRoll(id, sourceId, condensed);
    }

    void assertMaterialRoll(int id, int sourceId, boolean condensed) throws Exception {
        var manager = materialDungeon(id, sourceId);
        var targetByRank = new HashMap<Integer, Integer>();
        for (var entry : manager.getDungeonData().getRewardPreviewData().getRawPreviewItems()) {
            var item = GameData.getItemDataMap().get(entry.getId());
            if (item != null && item.getMaterialType() == MaterialType.MATERIAL_AVATAR_MATERIAL)
                targetByRank.put(item.getRankLevel(), item.getId());
        }
        var generator = actual.getServer().getDropSystem();
        var randomField = DropSystem.class.getDeclaredField("rand");
        randomField.setAccessible(true);
        var random = (Random) randomField.get(generator);
        random.setSeed(id);
        var original =
                generator.handleDungeonRewardDrop(
                        GameData.getDungeonDataMap().get(sourceId).getStatueDrop(), condensed);
        var expected = new TreeMap<Integer, Integer>();
        for (var item : original) {
            int target =
                    item.getItemData().getMaterialType() == MaterialType.MATERIAL_AVATAR_MATERIAL
                            ? targetByRank.get(item.getItemData().getRankLevel())
                            : item.getItemId();
            expected.merge(target, item.getCount(), Integer::sum);
        }
        random.setSeed(id);
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(expected.get(102).intValue(), actual.getExp());
        assertEquals(expected.get(202).intValue(), actual.getMora());
        for (int target : targetByRank.values())
            assertEquals(
                    expected.getOrDefault(target, 0).intValue(),
                    actual.getInventory().getItemCountById(target),
                    "Dungeon " + id + " material " + target + " differs from its source roll");
        assertTrue(
                actual.getInventory().getItems().values().stream()
                        .filter(
                                item ->
                                        item.getItemData().getMaterialType()
                                                == MaterialType.MATERIAL_AVATAR_MATERIAL)
                        .allMatch(item -> targetByRank.containsValue(item.getItemId())));
        assertEquals(condensed ? 200 : 180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
        assertTrue(manager.hasClaimedDomainReward(actual.getUid()));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "source-item",
                "target-item",
                "preview-count",
                "root",
                "cycle",
                "unmapped-leaf",
                "duplicate-slot"
            })
    void anIncompleteBorrowedTalentPoolDoesNotChargeOrConsumeTheClaim(String fault)
            throws Exception {
        assertIncompleteMaterialPool(talentDungeon(4654), 4213, 104303, 104358, fault, false);
    }

    static Stream<Arguments> weaponPoolFaults() {
        return Stream.of(
                        "source-item",
                        "target-item",
                        "preview-count",
                        "root",
                        "cycle",
                        "unmapped-leaf",
                        "duplicate-slot")
                .flatMap(fault -> Stream.of(Arguments.of(fault, false), Arguments.of(fault, true)));
    }

    @ParameterizedTest
    @MethodSource("weaponPoolFaults")
    void anIncompleteBorrowedWeaponPoolDoesNotChargeOrConsumeTheClaim(
            String fault, boolean condensed) throws Exception {
        assertIncompleteMaterialPool(
                materialDungeon(4674, 4313), 4313, 114004, 114076, fault, condensed);
    }

    void assertIncompleteMaterialPool(
            DungeonManager manager,
            int sourceId,
            int sourceItem,
            int targetItem,
            String fault,
            boolean condensed)
            throws Exception {
        var source = GameData.getDungeonDataMap().get(sourceId);
        switch (fault) {
            case "source-item" -> GameData.getItemDataMap().remove(sourceItem);
            case "target-item" -> GameData.getItemDataMap().remove(targetItem);
            case "preview-count" -> {
                var mora =
                        Arrays.stream(
                                        manager.getDungeonData()
                                                .getRewardPreviewData()
                                                .getRawPreviewItems())
                                .filter(item -> item.getId() == 202)
                                .findFirst()
                                .orElseThrow();
                replaceField(
                        emu.grasscutter.data.common.ItemParamStringData.class,
                        mora,
                        "count",
                        "999");
            }
            case "root" -> GameData.getDropTableDataMap().remove(source.getStatueDrop());
            case "cycle", "unmapped-leaf" -> GameData.getDropTableDataMap()
                    .get(source.getStatueDrop())
                    .getDropVec()
                    .add(
                            gson.fromJson(
                                    "{itemId:"
                                            + (fault.equals("cycle")
                                                    ? source.getStatueDrop()
                                                    : 1999999999)
                                            + ",countRange:'1',weight:1}",
                                    emu.grasscutter.data.common.DropItemData.class));
            case "duplicate-slot" -> {
                var preview = manager.getDungeonData().getRewardPreviewData();
                var duplicate =
                        Arrays.copyOf(
                                preview.getRawPreviewItems(),
                                preview.getRawPreviewItems().length + 1);
                duplicate[duplicate.length - 1] =
                        Arrays.stream(preview.getRawPreviewItems())
                                .filter(item -> item.getId() == targetItem)
                                .findFirst()
                                .orElseThrow();
                replaceField(RewardPreviewData.class, preview, "previewItems", duplicate);
            }
        }
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getMora());
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @ParameterizedTest
    @CsvSource({
        "4654,4213,false,false",
        "4654,4213,false,true",
        "4654,4213,true,false",
        "4654,4213,true,true",
        "4674,4313,false,false",
        "4674,4313,false,true",
        "4674,4313,true,false",
        "4674,4313,true,true"
    })
    void explicitMaterialRewardsKeepPriorityOverBorrowedTemplates(
            int dungeonId, int sourceId, boolean ownRoot, boolean condensed) throws Exception {
        var manager = materialDungeon(dungeonId, sourceId);
        if (ownRoot) {
            int id = manager.getDungeonData().getStatueDrop();
            var table =
                    gson.fromJson(
                            "{id:"
                                    + id
                                    + ",randomType:1,dropVec:[{itemId:202,countRange:'17',weight:10000}]}",
                            DropTableData.class);
            oldTables.put(id, GameData.getDropTableDataMap().put(id, table));
        } else {
            GameData.getDungeonDropDataMap().put(dungeonId, List.of(drop(202, 17)));
        }
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 34 : 17, actual.getMora());
        assertEquals(0, actual.getExp());
        assertTrue(
                actual.getInventory().getItems().values().stream()
                        .noneMatch(
                                item ->
                                        item.getItemData().getMaterialType()
                                                == MaterialType.MATERIAL_AVATAR_MATERIAL));
        assertEquals(condensed ? 200 : 180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
    }

    @ParameterizedTest
    @MethodSource("fallbackRewards")
    void fallbackDomainsGrantOnlyTheRelicSetsAdvertisedForTheirOwnDifficulty(
            int id, boolean condensed) throws Exception {
        assertRelicRoll(
                id,
                condensed,
                emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager
                        .getStatueDropOverride(id));
    }

    static Stream<Arguments> missingRelicRewards() {
        int[] roots = {85333500, 85334000, 85334500, 85335500};
        return Stream.of(4665, 5022, 4683, 4687, 4703)
                .flatMap(
                        first ->
                                java.util.stream.IntStream.range(0, 4)
                                        .boxed()
                                        .flatMap(
                                                level ->
                                                        Stream.of(
                                                                Arguments.of(
                                                                        first + level,
                                                                        false,
                                                                        roots[level]),
                                                                Arguments.of(
                                                                        first + level,
                                                                        true,
                                                                        roots[level]))));
    }

    @ParameterizedTest
    @MethodSource("missingRelicRewards")
    void missingRelicTablesPreserveTheOriginalRollAndGrantTheAdvertisedSets(
            int id, boolean condensed, int sourceRoot) throws Exception {
        assertRelicRoll(id, condensed, sourceRoot);
    }

    DungeonManager relicDungeon(int id, int sourceRoot) throws Exception {
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
        pending.add(sourceRoot);
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
        return manager;
    }

    void assertRelicRoll(int id, boolean condensed, int sourceRoot) throws Exception {
        var manager = relicDungeon(id, sourceRoot);
        var preview = manager.getDungeonData().getRewardPreviewData();
        var allowed = new HashMap<Integer, Set<Integer>>();
        for (var item : preview.getRawPreviewItems()) {
            var data = GameData.getDisplayItemDataMap().get(item.getId());
            if (data != null && data.getParam() > 0 && data.getRankLevel() > 0)
                allowed.computeIfAbsent(data.getRankLevel(), ignored -> new HashSet<>())
                        .add(data.getParam());
        }
        var generator = actual.getServer().getDropSystem();
        var randomField = DropSystem.class.getDeclaredField("rand");
        randomField.setAccessible(true);
        var random = (Random) randomField.get(generator);
        random.setSeed(id);
        var original = generator.handleDungeonRewardDrop(sourceRoot, condensed);
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

    static Stream<Arguments> relicPoolFaults() {
        return Stream.of("root", "cycle", "unknown-source", "target-item", "display", "currency")
                .flatMap(fault -> Stream.of(Arguments.of(fault, false), Arguments.of(fault, true)));
    }

    @ParameterizedTest
    @MethodSource("relicPoolFaults")
    void anIncompleteBorrowedRelicPoolDoesNotChargeOrConsumeTheClaim(
            String fault, boolean condensed) throws Exception {
        var manager = relicDungeon(4668, 85335500);
        var root = GameData.getDropTableDataMap().get(85335500);
        switch (fault) {
            case "root" -> GameData.getDropTableDataMap().remove(85335500);
            case "cycle", "unknown-source" -> root.getDropVec()
                    .add(
                            gson.fromJson(
                                    "{itemId:"
                                            + (fault.equals("cycle") ? 85335500 : 1999999999)
                                            + ",countRange:'1',weight:1}",
                                    emu.grasscutter.data.common.DropItemData.class));
            case "currency" -> replaceField(
                    emu.grasscutter.data.common.DropItemData.class,
                    root.getDropVec().stream()
                            .filter(item -> item.getId() == 202)
                            .findFirst()
                            .orElseThrow(),
                    "countRange",
                    "999");
            case "display", "target-item" -> {
                int set =
                        Arrays.stream(
                                        manager.getDungeonData()
                                                .getRewardPreviewData()
                                                .getRawPreviewItems())
                                .map(item -> GameData.getDisplayItemDataMap().get(item.getId()))
                                .filter(Objects::nonNull)
                                .filter(item -> item.getRankLevel() == 5)
                                .findFirst()
                                .orElseThrow()
                                .getParam();
                if (fault.equals("display")) {
                    Arrays.stream(
                                    manager.getDungeonData()
                                            .getRewardPreviewData()
                                            .getRawPreviewItems())
                            .map(item -> GameData.getDisplayItemDataMap().get(item.getId()))
                            .filter(Objects::nonNull)
                            .filter(item -> item.getParam() == set)
                            .forEach(item -> GameData.getDisplayItemDataMap().remove(item.getId()));
                } else {
                    var source =
                            actual
                                    .getServer()
                                    .getDropSystem()
                                    .handleDungeonRewardDrop(85335500, false)
                                    .stream()
                                    .map(GameItem::getItemData)
                                    .filter(
                                            item ->
                                                    item.getItemType() == ItemType.ITEM_RELIQUARY
                                                            && item.getRankLevel() == 5)
                                    .findFirst()
                                    .orElseThrow();
                    int item =
                            GameData.getItemDataMap().values().stream()
                                    .filter(
                                            data ->
                                                    data.getSetId() == set
                                                            && data.getRankLevel() == 5
                                                            && data.getEquipType()
                                                                    == source.getEquipType()
                                                            && data.getMainPropDepotId()
                                                                    == source.getMainPropDepotId()
                                                            && data.getAppendPropDepotId()
                                                                    == source.getAppendPropDepotId()
                                                            && data.getAppendPropNum()
                                                                    == source.getAppendPropNum()
                                                            && data.getMaxLevel()
                                                                    == source.getMaxLevel())
                                    .findFirst()
                                    .orElseThrow()
                                    .getId();
                    GameData.getItemDataMap().remove(item);
                }
            }
        }
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getMora());
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
    }

    @ParameterizedTest
    @CsvSource({
        "4668,false,false",
        "4668,false,true",
        "4668,true,false",
        "4668,true,true",
        "5063,false,false",
        "5063,false,true",
        "5063,true,false",
        "5063,true,true"
    })
    void explicitRelicRewardsKeepPriorityOverBorrowedTemplates(
            int id, boolean ownRoot, boolean condensed) throws Exception {
        var manager = relicDungeon(id, 85335500);
        if (ownRoot) {
            int rootId = manager.getDungeonData().getStatueDrop();
            oldTables.put(
                    rootId,
                    GameData.getDropTableDataMap()
                            .put(
                                    rootId,
                                    gson.fromJson(
                                            "{id:"
                                                    + rootId
                                                    + ",randomType:1,dropVec:[{itemId:202,countRange:'17',weight:10000}]}",
                                            DropTableData.class)));
        } else {
            absentDrops.add(id);
            GameData.getDungeonDropDataMap().put(id, List.of(drop(202, 17)));
        }
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 34 : 17, actual.getMora());
        assertEquals(0, actual.getExp());
        assertTrue(
                actual.getInventory().getItems().values().stream()
                        .noneMatch(
                                item ->
                                        item.getItemData().getItemType()
                                                == ItemType.ITEM_RELIQUARY));
        assertEquals(condensed ? 200 : 180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
    }

    DungeonRewardOptions enhancementOptions() {
        return emu.grasscutter.Grasscutter.getConfig().server.game.gameOptions.dungeonRewards;
    }

    DungeonManager configuredRewardDungeon(int id, List<DungeonDropEntry> groups) throws Exception {
        if (!oldDrops.containsKey(id) && !absentDrops.contains(id)) {
            var old = GameData.getDungeonDropDataMap().get(id);
            if (old == null) absentDrops.add(id);
            else oldDrops.put(id, old);
        }
        GameData.getDungeonDropDataMap().put(id, groups);
        var manager = dungeon(id);
        int root = manager.getDungeonData().getStatueDrop();
        if (!oldTables.containsKey(root))
            oldTables.put(root, GameData.getDropTableDataMap().get(root));
        GameData.getDropTableDataMap().remove(root);
        return manager;
    }

    @Test
    void oldConfigurationKeepsResourceRewardsByDefault() {
        var options = gson.fromJson("{}", emu.grasscutter.config.ConfigContainer.GameOptions.class);
        assertEquals(1, options.dungeonRewards.talentMaterialMultiplier);
        assertEquals(1, options.dungeonRewards.weaponMaterialMultiplier);
        assertFalse(options.dungeonRewards.relicEnhancementBonus);
        assertFalse(options.dungeonRewards.referenceDropTables);
    }

    @ParameterizedTest
    @CsvSource({
        "4210,3,false",
        "4210,3,true",
        "4310,5,false",
        "4310,5,true",
        "5125,1,false",
        "5125,1,true",
        "103,1,false",
        "103,1,true"
    })
    void optionalMaterialMultipliersFollowTheSubtype(int id, int multiplier, boolean condensed)
            throws Exception {
        enhancementOptions().talentMaterialMultiplier = 3;
        enhancementOptions().weaponMaterialMultiplier = 5;
        var manager =
                configuredRewardDungeon(
                        id,
                        List.of(drop(104303, 2), drop(114001, 3), drop(202, 10), drop(20002, 1)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        int rolls = condensed ? 2 : 1;
        assertEquals(2 * rolls * multiplier, actual.getInventory().getItemCountById(104303));
        assertEquals(3 * rolls * multiplier, actual.getInventory().getItemCountById(114001));
        assertEquals(10 * rolls, actual.getMora());
        assertEquals(
                rolls,
                actual.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemId() == 20002)
                        .count());
        assertEquals(0, actual.getInventory().getItemCountById(105002));
        assertEquals(0, actual.getInventory().getItemCountById(105003));
        assertEquals(
                condensed ? 200 : id == 103 ? 170 : 180,
                actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(10 * rolls, actual.getMora());
    }

    static Stream<Arguments> optionalWeightedRoutes() {
        return Stream.of(Arguments.of(4654, 4213), Arguments.of(4674, 4313))
                .flatMap(
                        row ->
                                Stream.of(false, true)
                                        .flatMap(
                                                own ->
                                                        Stream.of(false, true)
                                                                .map(
                                                                        condensed ->
                                                                                Arguments.of(
                                                                                        row.get()[
                                                                                                0],
                                                                                        row.get()[
                                                                                                1],
                                                                                        own,
                                                                                        condensed))));
    }

    @ParameterizedTest
    @MethodSource("optionalWeightedRoutes")
    void optionalMaterialMultipliersAlsoCoverWeightedRootsAndTemplates(
            int id, int sourceId, boolean ownRoot, boolean condensed) throws Exception {
        var manager = materialDungeon(id, sourceId);
        int sourceRoot = GameData.getDungeonDataMap().get(sourceId).getStatueDrop();
        if (ownRoot)
            replaceField(DungeonData.class, manager.getDungeonData(), "statueDrop", sourceRoot);
        enhancementOptions().talentMaterialMultiplier = 3;
        enhancementOptions().weaponMaterialMultiplier = 3;
        var targets = new HashMap<Integer, Integer>();
        for (var row : manager.getDungeonData().getRewardPreviewData().getRawPreviewItems()) {
            var item = GameData.getItemDataMap().get(row.getId());
            if (item != null && item.getMaterialType() == MaterialType.MATERIAL_AVATAR_MATERIAL)
                targets.put(item.getRankLevel(), item.getId());
        }
        var field = DropSystem.class.getDeclaredField("rand");
        field.setAccessible(true);
        var random = (Random) field.get(actual.getServer().getDropSystem());
        random.setSeed(id);
        var original =
                actual.getServer().getDropSystem().handleDungeonRewardDrop(sourceRoot, condensed);
        var expected = new TreeMap<Integer, Integer>();
        for (var item : original) {
            boolean material = item.getItemData().getItemType() == ItemType.ITEM_MATERIAL;
            int target =
                    material && !ownRoot
                            ? targets.get(item.getItemData().getRankLevel())
                            : item.getItemId();
            expected.merge(target, item.getCount() * (material ? 3 : 1), Integer::sum);
        }
        random.setSeed(id);
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(expected.get(102).intValue(), actual.getExp());
        assertEquals(expected.get(202).intValue(), actual.getMora());
        for (var row : expected.entrySet())
            if (GameData.getItemDataMap().get(row.getKey().intValue()).getItemType()
                    == ItemType.ITEM_MATERIAL)
                assertEquals(
                        row.getValue().intValue(),
                        actual.getInventory().getItemCountById(row.getKey()));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void optionalRelicBonusIsPartOfTheSingleClaim(boolean condensed) throws Exception {
        enhancementOptions().relicEnhancementBonus = true;
        enhancementOptions().talentMaterialMultiplier = 10;
        enhancementOptions().weaponMaterialMultiplier = 20;
        var manager =
                configuredRewardDungeon(
                        5125, List.of(drop(20002, 1), drop(202, 10), drop(105005, 1)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        int essence = actual.getInventory().getItemCountById(105003);
        int unction = actual.getInventory().getItemCountById(105002);
        assertTrue(essence >= 6 && essence <= 12);
        assertTrue(unction >= 7 && unction <= 10);
        assertEquals(condensed ? 2 : 1, actual.getInventory().getItemCountById(105005));
        assertEquals(condensed ? 20 : 10, actual.getMora());
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(essence, actual.getInventory().getItemCountById(105003));
        assertEquals(unction, actual.getInventory().getItemCountById(105002));
    }

    @ParameterizedTest
    @CsvSource({"105002,false", "105002,true", "105003,false", "105003,true"})
    void existingBlessingMaterialsAreNotDuplicated(int itemId, boolean condensed) throws Exception {
        enhancementOptions().relicEnhancementBonus = true;
        var manager = configuredRewardDungeon(5125, List.of(drop(20002, 1), drop(itemId, 7)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 14 : 7, actual.getInventory().getItemCountById(itemId));
        assertEquals(0, actual.getInventory().getItemCountById(itemId == 105002 ? 105003 : 105002));
    }

    @ParameterizedTest
    @CsvSource({
        "overflow,false",
        "overflow,true",
        "zero,false",
        "zero,true",
        "negative,false",
        "negative,true"
    })
    void invalidEnhancedAmountsDoNotSpendResinAndCanRetry(String fault, boolean condensed)
            throws Exception {
        enhancementOptions().talentMaterialMultiplier =
                fault.equals("zero") ? 0 : fault.equals("negative") ? -1 : 3;
        var manager =
                configuredRewardDungeon(
                        4210,
                        fault.equals("overflow")
                                ? List.of(drop(104303, 1), drop(114001, Integer.MAX_VALUE / 3 + 1))
                                : List.of(drop(104303, 2)));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getInventory().getItemCountById(104303));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        enhancementOptions().talentMaterialMultiplier = 3;
        GameData.getDungeonDropDataMap().put(4210, List.of(drop(104303, 2)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 12 : 6, actual.getInventory().getItemCountById(104303));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void enhancedQuantitiesAreAdmittedAgainstTheRealStackLimit(boolean condensed) throws Exception {
        enhancementOptions().talentMaterialMultiplier = 3;
        var manager = configuredRewardDungeon(4210, List.of(drop(104303, 800)));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertEquals(0, actual.getInventory().getItemCountById(104303));
        enhancementOptions().talentMaterialMultiplier = 1;
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 1600 : 800, actual.getInventory().getItemCountById(104303));
    }

    @ParameterizedTest
    @CsvSource({
        "slots,false",
        "slots,true",
        "missing,false",
        "missing,true",
        "empty,false",
        "empty,true",
        "zero,false",
        "zero,true"
    })
    void bonusSlotsMissingDefinitionsAndEmptyRollsCannotSpendResin(String fault, boolean condensed)
            throws Exception {
        enhancementOptions().relicEnhancementBonus = true;
        var manager =
                configuredRewardDungeon(
                        5125,
                        fault.equals("empty")
                                ? List.of()
                                : List.of(
                                        drop(
                                                fault.equals("zero") ? 202 : 20002,
                                                fault.equals("zero") ? 0 : 1)));
        var essence = GameData.getItemDataMap().get(105003);
        if (fault.equals("slots")) materialCapacity(1);
        if (fault.equals("missing")) GameData.getItemDataMap().remove(105003);
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        if (fault.equals("slots")) materialCapacity(3);
        GameData.getItemDataMap().put(105003, essence);
        GameData.getDungeonDropDataMap().put(5125, List.of(drop(20002, 1)));
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertTrue(actual.getInventory().getItemCountById(105003) >= 6);
        assertTrue(actual.getInventory().getItemCountById(105002) >= 7);
    }

    void referenceRewards(int itemId) throws Exception {
        DungeonDropReferenceCatalog.reload(
                new java.io.StringReader(
                        "[{dungeonId:4654,drops:[{counts:[7],items:["
                                + itemId
                                + "]},{counts:[13],items:[202]}]}]"));
    }

    @ParameterizedTest
    @CsvSource({
        "own,false,1",
        "own,true,3",
        "legacy,false,3",
        "legacy,true,1",
        "template,false,1",
        "template,true,3"
    })
    void referenceRewardsOverrideEachNormalRouteWhenEnabled(
            String route, boolean condensed, int multiplier) throws Exception {
        var manager = materialDungeon(4654, 4213);
        if (route.equals("own"))
            replaceField(
                    DungeonData.class,
                    manager.getDungeonData(),
                    "statueDrop",
                    GameData.getDungeonDataMap().get(4213).getStatueDrop());
        if (route.equals("legacy"))
            GameData.getDungeonDropDataMap().put(4654, List.of(drop(202, 17)));
        referenceRewards(104358);
        enhancementOptions().referenceDropTables = true;
        enhancementOptions().talentMaterialMultiplier = multiplier;
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(
                7 * (condensed ? 2 : 1) * multiplier,
                actual.getInventory().getItemCountById(104358));
        assertEquals(condensed ? 26 : 13, actual.getMora());
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnlistedReferenceDomainKeepsTheNormalWeightedTemplate(boolean condensed)
            throws Exception {
        DungeonDropReferenceCatalog.reload(
                new java.io.StringReader("[{dungeonId:1,drops:[{counts:[1],items:[202]}]}]"));
        enhancementOptions().referenceDropTables = true;
        assertMaterialRoll(4654, 4213, condensed);
    }

    @ParameterizedTest
    @CsvSource({
        "114001,false",
        "114001,true",
        "20002,false",
        "20002,true",
        "999999999,false",
        "999999999,true",
        "128,false",
        "128,true",
        "currency,false",
        "currency,true"
    })
    void invalidReferenceCandidatesLeavePaymentAndTheClaimAvailable(String fault, boolean condensed)
            throws Exception {
        var manager = materialDungeon(4654, 4213);
        var groups =
                fault.equals("currency")
                        ? "[{counts:[13],items:[202]}]"
                        : "[{counts:[1],items:[104358," + fault + "],itemProbabilities:[1,0]}]";
        DungeonDropReferenceCatalog.reload(
                new java.io.StringReader("[{dungeonId:4654,drops:" + groups + "}]"));
        enhancementOptions().referenceDropTables = true;
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(200, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(1, actual.getInventory().getItemCountById(220007));
        assertFalse(manager.hasClaimedDomainReward(actual.getUid()));
        referenceRewards(104358);
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        assertEquals(condensed ? 14 : 7, actual.getInventory().getItemCountById(104358));
    }

    @ParameterizedTest
    @CsvSource({
        "4654,4213,false",
        "4654,4213,true",
        "4674,4313,false",
        "4674,4313,true",
        "4668,85335500,false",
        "4668,85335500,true",
        "4510,85333500,false",
        "4510,85333500,true",
        "5128,85335500,false",
        "5128,85335500,true"
    })
    void bundledReferenceRowsReachTheRealInventory(int id, int source, boolean condensed)
            throws Exception {
        var manager =
                id == 4654 || id == 4674 ? materialDungeon(id, source) : relicDungeon(id, source);
        DungeonDropReferenceCatalog.reload();
        enhancementOptions().referenceDropTables = true;
        var profile =
                emu.grasscutter.data.DataLoader.loadList(
                                "DungeonDropReference.json", DungeonDrop.class)
                        .stream()
                        .filter(row -> row.getDungeonId() == id)
                        .findFirst()
                        .orElseThrow();
        assertTrue(manager.getStatueDrops(actual, condensed, 0));
        int rolls = condensed ? 2 : 1;
        var physicalIds = new HashSet<Integer>();
        for (var group : profile.getDrops()) {
            var metadata = GameData.getItemDataMap().get(group.getItems().get(0).intValue());
            if (metadata.getItemType() == ItemType.ITEM_VIRTUAL) {
                if (metadata.getId() == 102)
                    assertEquals(group.getCounts().get(0) * rolls, actual.getExp());
                if (metadata.getId() == 202)
                    assertEquals(group.getCounts().get(0) * rolls, actual.getMora());
                continue;
            }
            physicalIds.addAll(group.getItems());
            int quantity =
                    actual.getInventory().getItems().values().stream()
                            .filter(item -> group.getItems().contains(item.getItemId()))
                            .mapToInt(GameItem::getCount)
                            .sum();
            assertTrue(quantity >= group.getCounts().get(0) * rolls);
            assertTrue(quantity <= group.getCounts().get(group.getCounts().size() - 1) * rolls);
        }
        assertTrue(
                actual.getInventory().getItems().values().stream()
                        .allMatch(
                                item ->
                                        item.getItemId() == 220007
                                                || physicalIds.contains(item.getItemId())));
        assertTrue(
                actual.getInventory().getItems().values().stream()
                        .filter(item -> item.getItemData().isEquip())
                        .allMatch(item -> item.getCount() == 1));
        assertEquals(condensed ? 200 : 180, actual.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(condensed ? 0 : 1, actual.getInventory().getItemCountById(220007));
        assertFalse(manager.getStatueDrops(actual, condensed, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aRejectedReferenceRowKeepsItsCorrectedRelicTemplate(boolean condensed) throws Exception {
        DungeonDropReferenceCatalog.reload();
        enhancementOptions().referenceDropTables = true;
        assertRelicRoll(4665, condensed, 85333500);
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
