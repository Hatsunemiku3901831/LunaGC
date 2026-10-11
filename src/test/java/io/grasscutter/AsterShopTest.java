package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

class AsterShopTest {
    private AsterShopTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterShopTestFixture();
    }

    @AfterEach
    void cleanup() throws Exception {
        fixture.close();
    }

    private AsterShop.Result buy(
            ShopInfo goods,
            int count,
            int previous,
            ToIntFunction<ItemParamData> validate,
            Consumer<ItemParamData> grant,
            Runnable save) {
        return AsterShop.buy(
                fixture.event.data,
                fixture.access(),
                20,
                fixture.now,
                goods,
                count,
                previous,
                validate,
                grant,
                save);
    }

    private AsterShop.Result buy(int id, int count) {
        return buy(fixture.goods(id), count, 0, item -> 0, item -> {}, () -> {});
    }

    private AsterProgress progress() {
        return AsterSchedule.progress(fixture.event.data);
    }

    @Test
    void allTenCreditGoodsKeepTheirResourcePricesAndDoNotEraseGatheredProgress() {
        var delivered = new ArrayList<ItemParamData>();
        int original = progress().credit();
        var gathered = progress().collected();
        int total = 0;
        for (var good : fixture.goods.stream().filter(g -> g.getSecondarySheetId() == 1).toList()) {
            assertEquals(0, buy(good, 1, 0, item -> 0, delivered::add, () -> {}).retcode());
            total += good.getCostItemList().get(0).getCount();
            assertEquals(1, progress().shopBought(good.getGoodsId()));
        }
        assertEquals(10, delivered.size());
        assertEquals(82, total);
        assertEquals(original, progress().credit());
        assertEquals(original - total, progress().creditBalance());
        assertEquals(gathered, progress().collected());
        assertNull(progress().pendingShopPurchase());
        assertEquals(10000, delivered.get(8).getCount());
        assertEquals(500, delivered.get(9).getCount());
    }

    @Test
    void legacyDetailKeepsAllEarnedCreditAvailableAndMalformedLedgersFailClosed() {
        var gson = new Gson();
        String original = fixture.event.data.getDetail();
        var json = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
        json.remove("spentCredit");
        json.remove("shopPurchases");
        json.remove("pendingShopPurchase");
        fixture.event.data.setDetailJson(gson.toJson(json));
        assertEquals(118, progress().creditBalance());
        for (String ledger :
                List.of(
                        "{spentCredit:-1}",
                        "{spentCredit:2147483647}",
                        "{shopPurchases:null}",
                        "{shopPurchases:{1002001:-1}}",
                        "{pendingShopPurchase:{goodsId:1002001,count:1,cost:1,itemId:202,itemCount:500}}")) {
            fixture.event.data.setDetailJson(ledger);
            assertThrows(IllegalArgumentException.class, this::progress);
            assertEquals(ledger, fixture.event.data.getDetail());
        }
        fixture.event.data.setDetailJson(original);
    }

    @Test
    void persistedCountsProtectTheLimitEvenWhenThePlayerMirrorIsStale() {
        assertEquals(0, buy(1002001, 3).retcode());
        var data = fixture.event.data;
        data.setDetailJson(new Gson().toJson(progress()));
        var limit = fixture.access().prepareLimit(fixture.player, fixture.goods(1002001));
        assertEquals(3, limit.getHasBoughtInPeriod());
        limit.setHasBoughtInPeriod(0);
        var grants = new AtomicInteger();
        assertEquals(
                Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE,
                buy(
                                fixture.goods(1002001),
                                1,
                                0,
                                item -> {
                                    fail("No preflight after a limit rejection");
                                    return 0;
                                },
                                item -> grants.incrementAndGet(),
                                () -> fail("No reservation after a limit rejection"))
                        .retcode());
        assertEquals(0, grants.get());
        assertEquals(82, progress().creditBalance());
        assertEquals(
                3,
                fixture.access()
                        .prepareLimit(fixture.player, fixture.goods(1002001))
                        .getHasBoughtInPeriod());
    }

    @Test
    void legacyBuyCountsAreImportedConservativelyAndANewReplayResetsTheWalletAndLimits() {
        assertEquals(
                0, buy(fixture.goods(1002001), 1, 2, item -> 0, item -> {}, () -> {}).retcode());
        assertEquals(3, progress().shopBought(1002001));
        var limit = fixture.access().prepareLimit(fixture.player, fixture.goods(1002001));
        assertEquals(3, limit.getHasBoughtInPeriod());
        fixture.event.config.setScheduleId(fixture.event.config.getScheduleId() + 1);
        fixture.install();
        assertEquals(Retcode.RET_SHOP_NOT_OPEN_VALUE, buy(1002001, 1).retcode());
        fixture.event.data.setScheduleId(fixture.event.config.getScheduleId());
        fixture.event.data.setDetail(new AsterProgress());
        assertEquals(0, progress().creditBalance());
        assertEquals(
                0,
                fixture.access()
                        .prepareLimit(fixture.player, fixture.goods(1002001))
                        .getHasBoughtInPeriod());
        assertNotEquals(0, buy(1002001, 1).retcode());
    }

    @Test
    void insufficientCreditAndInventoryPreflightDoNotChargeOrCountAPurchase() {
        String before = fixture.event.data.getDetail();
        assertEquals(Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE, buy(1002008, 30).retcode());
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                buy(
                                fixture.goods(1002001),
                                1,
                                0,
                                item -> Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                                item -> fail("Rejected delivery"),
                                () -> fail("Rejected reservation"))
                        .retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @Test
    void duplicateCostEntriesAreAggregatedBeforeSpendingCredit() {
        var good = fixture.goods(1002001);
        good.setCostItemList(List.of(new ItemParamData(109, 8), new ItemParamData(109, 4)));
        assertEquals(0, buy(1002001, 2).retcode());
        assertEquals(94, progress().creditBalance());
        assertEquals(2, progress().shopBought(1002001));
    }

    @Test
    void actualInventoryPreflightRejectsAFullBagAnOverflowingStackAndOverflowingMora() {
        var inventory = fixture.player.getInventory();
        inventory.createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(0));
        String before = fixture.event.data.getDetail();
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002001), 1, fixture.now)
                        .retcode());
        inventory.createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(1));
        inventory.getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(new GameItem(104112, 2000));
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002001), 1, fixture.now)
                        .retcode());
        fixture.player.setMora(Integer.MAX_VALUE - 499);
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002010), 1, fixture.now)
                        .retcode());
        assertEquals(before, fixture.event.data.getDetail());
        assertEquals(
                2000, inventory.getInventoryTab(ItemType.ITEM_MATERIAL).getItemCountById(104112));
        assertEquals(Integer.MAX_VALUE - 499, fixture.player.getMora());
    }

    @Test
    void exchangeRemainsOpenAfterGameplayButNotAfterTheReplayOrForAnotherPlayer() {
        fixture.event.config.setBeginTime(new Date(fixture.event.now - 20 * 86400000L));
        assertEquals(0, buy(1002010, 1).retcode());
        String before = fixture.event.data.getDetail();
        fixture.event.data.setUid(fixture.player.getUid() + 1);
        assertEquals(
                Retcode.RET_SHOP_NOT_OPEN_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002010), 1, fixture.now)
                        .retcode());
        fixture.event.data.setUid(fixture.player.getUid());
        fixture.event.config.setEndTime(new Date(fixture.now * 1000L));
        assertEquals(Retcode.RET_SHOP_NOT_OPEN_VALUE, buy(1002010, 1).retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 3, 4, 5, 6})
    void invalidCountsCostsRefreshRulesAndOverflowLeaveTheWalletUntouched(int kind) {
        var good = fixture.goods(1002010);
        int count = 1;
        switch (kind) {
            case -1, 0 -> count = kind;
            case 1 -> good.setCostItemList(List.of(new ItemParamData(109, -1)));
            case 2 -> good.setCostItemList(List.of(new ItemParamData(109, 0)));
            case 3 -> good.setCostItemList(
                    List.of(new ItemParamData(109, Integer.MAX_VALUE), new ItemParamData(109, 1)));
            case 4 -> {
                good.setCostItemList(List.of(new ItemParamData(109, 2)));
                count = Integer.MAX_VALUE;
            }
            case 5 -> count = Integer.MAX_VALUE;
            case 6 -> good.setShopRefreshType(ShopInfo.ShopRefreshType.SHOP_REFRESH_DAILY);
        }
        String before = fixture.event.data.getDetail();
        assertNotEquals(0, buy(1002010, count).retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @Test
    void chapterThreeTokensCannotBePaidWithCreditOrAPlayerMaterialStack() {
        fixture.event.config.setBeginTime(new Date(fixture.event.now - 8 * 86400000L));
        assertTrue(fixture.access().sheet(fixture.goods(1002011)).open());
        for (var good : fixture.goods.stream().filter(g -> g.getSecondarySheetId() == 2).toList()) {
            assertTrue(AsterShop.usesCurrency(good));
            assertEquals(
                    Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE,
                    buy(
                                    good,
                                    1,
                                    0,
                                    item -> {
                                        fail("Tokens are not credit");
                                        return 0;
                                    },
                                    item -> fail("No token delivery"),
                                    () -> fail("No token reservation"))
                            .retcode());
        }
        assertEquals(118, progress().creditBalance());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void reservationsSurviveFailuresAndPreventASecondDelivery(int failure) {
        var saved = new AtomicReference<String>();
        var saves = new AtomicInteger();
        var grants = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        buy(
                                fixture.goods(1002001),
                                1,
                                0,
                                item -> 0,
                                item -> {
                                    grants.incrementAndGet();
                                    if (failure == 2)
                                        throw new IllegalStateException(
                                                "Item acknowledgement lost");
                                },
                                () -> {
                                    saved.set(fixture.event.data.getDetail());
                                    int n = saves.incrementAndGet();
                                    if (failure == 1 && n == 1 || failure == 3 && n == 2)
                                        throw new IllegalStateException(
                                                "Activity acknowledgement lost");
                                }));
        assertNotNull(progress().pendingShopPurchase());
        assertEquals(106, progress().creditBalance());
        assertEquals(1, progress().shopBought(1002001));
        assertEquals(failure == 1 ? 0 : 1, grants.get());
        assertNotEquals(
                0,
                buy(
                                fixture.goods(1002001),
                                1,
                                0,
                                item -> 0,
                                item -> fail("Must not repeat an uncertain delivery"),
                                () -> fail("Must not replace its reservation"))
                        .retcode());
        if (failure < 3) {
            fixture.event.data.setDetailJson(saved.get());
            assertNotNull(progress().pendingShopPurchase());
            assertNotEquals(0, buy(1002001, 1).retcode());
        } else {
            fixture.event.data.setDetailJson(saved.get());
            assertNull(progress().pendingShopPurchase(), "The final save may already be durable");
            assertEquals(1, progress().shopBought(1002001));
        }
    }

    @Test
    void pendingShopReservationsAlsoBlockWatcherRecoveryAndFragmentWrites() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        buy(
                                fixture.goods(1002001),
                                1,
                                0,
                                item -> 0,
                                item -> {
                                    throw new IllegalStateException("Uncertain delivery");
                                },
                                () -> {}));
        var pending = progress();
        assertThrows(IllegalStateException.class, () -> pending.beginWatcherReward(1200110));
        assertThrows(
                IllegalStateException.class,
                () ->
                        pending.beginSharedMidClaim(
                                fixture.event.offer(0, 1), UUID.randomUUID().toString()));
        var area =
                AsterFragments.areas().values().stream()
                        .filter(a -> a.stageId() == 1)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                AsterGather.collect(
                        fixture.event.data,
                        fixture.event.config,
                        20,
                        fixture.event.now,
                        area.groupId(),
                        area.configIds().iterator().next(),
                        () -> fail("No pending-write overwrite")));
    }

    @Test
    void concurrentRequestsCannotBothPassASinglePurchaseLimit() throws Exception {
        var good = fixture.goods(1002001);
        good.setBuyLimit(1);
        var start = new CountDownLatch(1);
        var grants = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call =
                    () -> {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return buy(
                                        good,
                                        1,
                                        0,
                                        item -> 0,
                                        item -> grants.incrementAndGet(),
                                        () -> {})
                                .retcode();
                    };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            assertEquals(
                    Set.of(0, Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE),
                    Set.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)));
            assertEquals(1, grants.get());
            assertEquals(106, progress().creditBalance());
        } finally {
            pool.shutdownNow();
        }
    }
}
