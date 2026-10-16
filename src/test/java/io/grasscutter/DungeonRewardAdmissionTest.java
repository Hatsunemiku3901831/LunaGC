package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassSystem;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.GameItem;
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
    Consumer<BasePacket> packetAction = ignored -> {};
    boolean resinUsage;

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
            if (data.getId() != 202 && data.getId() != 220007) continue;
            data.onLoad();
            oldItems.put(data.getId(), GameData.getItemDataMap().put(data.getId(), data));
        }
        for (int id : List.of(103, 5125)) {
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
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = resinUsage;
        for (var entry : oldItems.entrySet()) {
            if (entry.getValue() == null)
                GameData.getItemDataMap().remove(entry.getKey().intValue());
            else GameData.getItemDataMap().put(entry.getKey().intValue(), entry.getValue());
        }
        for (int id : absentDrops) GameData.getDungeonDropDataMap().remove(id);
        oldDrops.forEach((id, data) -> GameData.getDungeonDropDataMap().put(id.intValue(), data));
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
}
