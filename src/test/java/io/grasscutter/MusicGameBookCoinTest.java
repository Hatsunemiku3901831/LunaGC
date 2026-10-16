package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerPropNotifyOuterClass.PlayerPropNotify;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Resource item 207 is a Player currency, including both inventory payment overloads. */
class MusicGameBookCoinTest extends QuestTestFixture {
    static final int COIN = 207, PROP = 10058;
    static ItemData coinData;
    Player actual;
    GuardedPlayerTestStore guarded;
    ItemData oldCoin;
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void resource() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        coinData =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == COIN)
                        .findFirst()
                        .orElseThrow();
        coinData.onLoad();
        assertEquals(ItemType.ITEM_VIRTUAL, coinData.getItemType());
    }

    @BeforeEach
    void currencyPlayer() throws Exception {
        oldCoin = GameData.getItemDataMap().put(COIN, coinData);
        actual = new Player();
        actual.setUid(10002);
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket p) {
                        packets.add(p);
                    }
                };
        session.setPlayer(actual);
        session.setState(GameSession.SessionState.ACTIVE);
        actual.setSession(session);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        guarded = new GuardedPlayerTestStore(actual, () -> {});
        packets.clear();
    }

    @AfterEach
    void restoreCurrency() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (guarded != null) guarded.close();
        if (oldCoin == null) GameData.getItemDataMap().remove(COIN);
        else GameData.getItemDataMap().put(COIN, oldCoin);
    }

    void balance(int amount) {
        actual.getProperties().put(PROP, amount);
        packets.clear();
    }

    int balance() {
        return actual.getProperties().getOrDefault(PROP, 0);
    }

    boolean pay(boolean array, List<ItemParamData> costs, int quantity) {
        return array
                ? actual.getInventory().payItems(costs.toArray(ItemParamData[]::new), quantity)
                : actual.getInventory().payItems(costs, quantity);
    }

    @Test
    void nativePropertyIsRecognizedAndNonnegative() {
        var property = PlayerProperty.getPropById(PROP);
        assertNotNull(property);
        assertEquals("PROP_PLAYER_MUSIC_GAME_BOOK_COIN", property.name());
        assertEquals(0, property.getMin());
    }

    @Test
    void grantingResourceCoinsUpdatesTheVirtualBalanceAndNativePropertyNotification()
            throws Exception {
        assertTrue(actual.getInventory().addItem(new GameItem(coinData, 20)));
        assertEquals(20, balance());
        assertEquals(20, actual.getInventory().getItemCountById(COIN));
        assertNull(actual.getInventory().getItemById(COIN));
        var prop =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerPropNotify)
                        .map(
                                p -> {
                                    try {
                                        return PlayerPropNotify.parseFrom(p.getData());
                                    } catch (Exception e) {
                                        throw new AssertionError(e);
                                    }
                                })
                        .filter(p -> p.containsPropMap(PROP))
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(20, prop.getPropMapOrThrow(PROP).getIval());
        assertEquals(PROP, prop.getPropMapOrThrow(PROP).getType());
    }

    @Test
    void coinBalanceSurvivesConfirmedPlayerSaveAndColdRead() throws Exception {
        actual.getInventory().addItem(new GameItem(coinData, 60));
        DatabaseHelper.saveGameSync(actual);
        var cold =
                guarded.datastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", actual.getUid()))
                        .first();
        assertNotNull(cold);
        assertEquals(60, cold.getProperties().getOrDefault(PROP, 0));
    }

    @Test
    void singlePaymentDebitsCoinsAndRejectsOverdraft() {
        balance(40);
        assertTrue(actual.getInventory().payItem(COIN, 30));
        assertEquals(10, balance());
        assertFalse(actual.getInventory().payItem(COIN, 11));
        assertEquals(10, balance());
        assertTrue(actual.getInventory().payItem(COIN, 10));
        assertEquals(0, balance());
    }

    @Test
    void negativeSinglePaymentCannotMintCoins() {
        balance(40);
        assertFalse(actual.getInventory().payItem(COIN, -10));
        assertEquals(40, balance());
    }

    @Test
    void freePriceDoesNotChangeTheCurrencyBalance() {
        balance(40);
        assertTrue(actual.getInventory().payItem(COIN, 0));
        assertEquals(40, balance());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bulkPaymentUsesTheVirtualBalance(boolean array) {
        balance(100);
        assertTrue(pay(array, List.of(new ItemParamData(COIN, 20)), 3));
        assertEquals(40, balance());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateCostsAreAddedBeforeAnyPayment(boolean array) {
        balance(100);
        var costs = List.of(new ItemParamData(COIN, 60), new ItemParamData(COIN, 60));
        assertFalse(pay(array, costs, 1));
        assertEquals(100, balance());
        balance(120);
        assertTrue(pay(array, costs, 1));
        assertEquals(0, balance());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidBulkCountsAndOverflowCannotMintCoins(boolean array) {
        balance(100);
        for (var input :
                List.of(
                        new int[] {-20, 1},
                        new int[] {20, -1},
                        new int[] {Integer.MAX_VALUE, 2},
                        new int[] {20, 0})) {
            assertFalse(pay(array, List.of(new ItemParamData(COIN, input[0])), input[1]));
            assertEquals(100, balance());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void freeBulkCostsRemainSupported(boolean array) {
        balance(100);
        assertTrue(pay(array, List.of(new ItemParamData(COIN, 0)), 3));
        assertEquals(100, balance());
    }

    @Test
    void paymentConsumesASinglePassIterableOnce() {
        balance(100);
        var iterator = List.of(new ItemParamData(COIN, 40)).iterator();
        Iterable<ItemParamData> costs = () -> iterator;
        assertTrue(actual.getInventory().payItems(costs));
        assertEquals(60, balance());
    }

    @Test
    void creditOverflowDoesNotWrapTheBalance() {
        balance(Integer.MAX_VALUE - 1);
        assertThrows(
                ArithmeticException.class,
                () -> actual.getInventory().addItem(new GameItem(coinData, 5)));
        assertEquals(Integer.MAX_VALUE - 1, balance());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedPhysicalCostsCannotPartiallySpendAnInsufficientStack(boolean array)
            throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 100001)
                        .findFirst()
                        .orElseThrow();
        data.onLoad();
        var old = GameData.getItemDataMap().put(data.getId(), data);
        try {
            var item = new GameItem(data, 100);
            item.setOwner(actual);
            actual.getInventory().getItems().put(item.getGuid(), item);
            actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
            store.save(item);
            assertFalse(
                    pay(
                            array,
                            List.of(
                                    new ItemParamData(data.getId(), 60),
                                    new ItemParamData(data.getId(), 60)),
                            1));
            assertEquals(100, item.getCount());
            assertSame(item, actual.getInventory().getItemById(data.getId()));
            assertTrue(
                    pay(
                            array,
                            List.of(
                                    new ItemParamData(data.getId(), 30),
                                    new ItemParamData(data.getId(), 20)),
                            1));
            assertEquals(50, item.getCount());
        } finally {
            if (old == null) GameData.getItemDataMap().remove(data.getId());
            else GameData.getItemDataMap().put(data.getId(), old);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 202, 203, 204})
    void existingCurrencyQueriesReadTheirActualWallet(int itemId) throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == itemId)
                        .findFirst()
                        .orElseThrow();
        data.onLoad();
        var old = GameData.getItemDataMap().put(itemId, data);
        try {
            var property =
                    switch (itemId) {
                        case 201 -> PlayerProperty.PROP_PLAYER_HCOIN;
                        case 202 -> PlayerProperty.PROP_PLAYER_SCOIN;
                        case 203 -> PlayerProperty.PROP_PLAYER_MCOIN;
                        default -> PlayerProperty.PROP_PLAYER_HOME_COIN;
                    };
            assertTrue(actual.setProperty(property, 123, false));
            assertEquals(123, actual.getInventory().getItemCountById(itemId));
        } finally {
            if (old == null) GameData.getItemDataMap().remove(itemId);
            else GameData.getItemDataMap().put(itemId, old);
        }
    }
}
