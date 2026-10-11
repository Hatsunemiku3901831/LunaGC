package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.shop.ShopInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

class AsterBigShopTest {
    private AsterShopTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterShopTestFixture();
        fixture.event.config.setBeginTime(new Date(fixture.event.now - 8 * 86400000L));
        var progress = AsterSchedule.progress(fixture.event.data);
        progress.beginBigClaim(UUID.randomUUID().toString());
        progress.finishBigClaim(1000);
        fixture.event.data.setDetail(progress);
    }

    @AfterEach
    void cleanup() throws Exception {
        fixture.close();
    }

    @Test
    void allFifteenOriginalEssenceGoodsCanBeExchangedWithTheirOwnCurrency() {
        var delivered = new ArrayList<ItemParamData>();
        int[] items = {
            104302, 104303, 104305, 104306, 104308, 104309, 104311, 104312, 104314, 104315, 104317,
            104318, 104319, 104013, 104012
        };
        int[] prices = {4, 12, 4, 12, 4, 12, 4, 12, 4, 12, 4, 12, 110, 1, 2};
        int[] limits = {12, 4, 12, 4, 12, 4, 12, 4, 12, 4, 12, 4, 1, 100, 0};
        for (int i = 0; i < items.length; i++) {
            ShopInfo goods = fixture.goods(1002011 + i);
            assertEquals(2, goods.getSecondarySheetId());
            assertEquals(items[i], goods.getGoodsItem().getId());
            assertEquals(limits[i], goods.getBuyLimit());
            assertEquals(110, goods.getCostItemList().get(0).getId());
            assertEquals(prices[i], goods.getCostItemList().get(0).getCount());
            assertEquals(
                    0,
                    AsterShop.buy(
                                    fixture.event.data,
                                    fixture.access(),
                                    20,
                                    fixture.now,
                                    goods,
                                    1,
                                    0,
                                    item -> 0,
                                    delivered::add,
                                    () -> {})
                            .retcode(),
                    "Original essence good " + goods.getGoodsId());
        }
        assertEquals(15, delivered.size());
        assertEquals(3, delivered.get(14).getCount());
        var progress = AsterSchedule.progress(fixture.event.data);
        assertEquals(118, progress.creditBalance());
        assertEquals(1000, progress.bigCredit());
        assertEquals(791, progress.bigCreditBalance());
        assertEquals(1, progress.bigCollectCount());
        assertNull(progress.pendingShopPurchase());
    }

    private AsterProgress progress() {
        return AsterSchedule.progress(fixture.event.data);
    }

    private AsterShop.Result buy(
            ShopInfo goods, int count, Consumer<ItemParamData> grant, Runnable save) {
        return AsterShop.buy(
                fixture.event.data,
                fixture.access(),
                20,
                fixture.now,
                goods,
                count,
                0,
                item -> 0,
                grant,
                save);
    }

    private AsterShop.Result buy(int id, int count) {
        return buy(fixture.goods(id), count, item -> {}, () -> {});
    }

    @Test
    void bothWalletsRemainIndependentAndKeepEarnedTotalsAndClaims() {
        var claims =
                JsonParser.parseString(fixture.event.data.getDetail())
                        .getAsJsonObject()
                        .get("bigClaims")
                        .deepCopy();
        assertEquals(0, buy(1002008, 2).retcode());
        assertEquals(110, progress().creditBalance());
        assertEquals(1000, progress().bigCreditBalance());
        assertEquals(0, buy(1002011, 2).retcode());
        assertEquals(110, progress().creditBalance());
        assertEquals(992, progress().bigCreditBalance());
        assertEquals(118, progress().credit());
        assertEquals(1000, progress().bigCredit());
        assertEquals(1, progress().bigCollectCount());
        assertEquals(
                claims,
                JsonParser.parseString(fixture.event.data.getDetail())
                        .getAsJsonObject()
                        .get("bigClaims"));
    }

    @Test
    void confirmedOriginalLargeRewardCanPayForTheSecondSheetWithoutARepeatClaim() throws Exception {
        fixture.loadBigRewards();
        var p = new AsterProgress();
        p.openBigReward(Map.of(fixture.player.getUid(), AsterBigRewards.reward(0)));
        fixture.event.data.setDetail(p);
        String token = p.bigReward().token();
        var grants = new AtomicInteger();
        assertEquals(
                0,
                AsterBigRewards.takeShared(
                        fixture.event.data,
                        fixture.event.data,
                        fixture.event.config,
                        20,
                        fixture.event.now,
                        token,
                        () -> true,
                        reward -> {
                            assertEquals(14, reward.essence());
                            grants.incrementAndGet();
                        },
                        () -> {},
                        () -> {}));
        assertEquals(0, buy(1002011, 1).retcode());
        assertEquals(10, progress().bigCreditBalance());
        assertEquals(14, progress().bigCredit());
        assertEquals(0, progress().creditBalance());
        assertNotEquals(
                0,
                AsterBigRewards.takeShared(
                        fixture.event.data,
                        fixture.event.data,
                        fixture.event.config,
                        20,
                        fixture.event.now,
                        token,
                        () -> fail("Claim must not debit twice"),
                        reward -> fail("No repeat grant"),
                        () -> fail("No repeat save"),
                        () -> fail("No repeat save")));
        assertEquals(1, grants.get());
        assertEquals(10, progress().bigCreditBalance());
    }

    @Test
    void legacyWalletsAndCreditReservationsRetainTheirOriginalCurrency() {
        var p = progress();
        p.beginShopPurchase(new AsterProgress.ShopPurchase(1002001, 1, 12, 104112, 1), 0);
        fixture.event.data.setDetail(p);
        var json = JsonParser.parseString(fixture.event.data.getDetail()).getAsJsonObject();
        json.remove("spentBigCredit");
        json.getAsJsonObject("pendingShopPurchase").remove("currencyId");
        fixture.event.data.setDetailJson(json.toString());
        assertEquals(109, progress().pendingShopPurchase().currencyId());
        assertEquals(106, progress().creditBalance());
        assertEquals(1000, progress().bigCreditBalance());
        assertNotEquals(0, buy(1002011, 1).retcode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1001", "2147483647"})
    void invalidEssenceSpendingCannotAlterEitherWallet(String spent) {
        var json = JsonParser.parseString(fixture.event.data.getDetail()).getAsJsonObject();
        json.addProperty("spentBigCredit", Integer.parseInt(spent));
        String before = json.toString();
        fixture.event.data.setDetailJson(before);
        assertThrows(IllegalArgumentException.class, this::progress);
        assertThrows(IllegalArgumentException.class, () -> buy(1002011, 1));
        assertEquals(before, fixture.event.data.getDetail());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 108, 111, 202})
    void invalidReservationCurrencyIsNotMappedToAnotherWallet(int currency) {
        var p = progress();
        p.beginShopPurchase(new AsterProgress.ShopPurchase(1002011, 1, 4, 104302, 1, 110), 0);
        fixture.event.data.setDetail(p);
        var json = JsonParser.parseString(fixture.event.data.getDetail()).getAsJsonObject();
        json.getAsJsonObject("pendingShopPurchase").addProperty("currencyId", currency);
        fixture.event.data.setDetailJson(json.toString());
        assertThrows(IllegalArgumentException.class, this::progress);
    }

    @Test
    void creditOrABagStackCannotPayForAnEmptyEssenceWallet() {
        var json = JsonParser.parseString(fixture.event.data.getDetail()).getAsJsonObject();
        json.addProperty("bigCredit", 0);
        fixture.event.data.setDetailJson(json.toString());
        fixture.player
                .getInventory()
                .createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(10));
        fixture.player
                .getInventory()
                .getInventoryTab(ItemType.ITEM_MATERIAL)
                .onAddItem(new GameItem(110, 9999));
        String before = fixture.event.data.getDetail();
        assertEquals(
                Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002011), 1, fixture.now)
                        .retcode());
        assertEquals(before, fixture.event.data.getDetail());
        assertEquals(118, progress().creditBalance());
        assertEquals(
                9999,
                fixture.player
                        .getInventory()
                        .getInventoryTab(ItemType.ITEM_MATERIAL)
                        .getItemCountById(110));
    }

    @Test
    void duplicateSameCurrencyCostsAreSummedAndMixedCurrenciesAreRejected() {
        var goods = fixture.goods(1002011);
        goods.setCostItemList(List.of(new ItemParamData(110, 2), new ItemParamData(110, 2)));
        assertEquals(0, buy(1002011, 2).retcode());
        assertEquals(992, progress().bigCreditBalance());
        String before = fixture.event.data.getDetail();
        goods.setCostItemList(List.of(new ItemParamData(110, 2), new ItemParamData(109, 2)));
        assertNotEquals(0, buy(1002011, 1).retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void timeLevelReplayAndContentGuardsPrecedeTheEssenceDebit(int guard) {
        var goods = fixture.goods(1002011);
        int rank = 20;
        switch (guard) {
            case 0 -> fixture.event.config.setBeginTime(
                    new Date(fixture.event.now - 6 * 86400000L));
            case 1 -> fixture.event.config.setDisabled(true);
            case 2 -> fixture.event.config.setEndTime(new Date(fixture.now * 1000L));
            case 3 -> rank = 19;
            case 4 -> fixture.event.data.setScheduleId(fixture.event.config.getScheduleId() + 1);
            case 5 -> goods.setEndTime(fixture.now);
        }
        String before = fixture.event.data.getDetail();
        assertNotEquals(
                0,
                AsterShop.buy(
                                fixture.event.data,
                                fixture.access(),
                                rank,
                                fixture.now,
                                goods,
                                1,
                                0,
                                item -> fail("No rejected preflight"),
                                item -> fail("No rejected delivery"),
                                () -> fail("No rejected save"))
                        .retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @Test
    void limitMirrorRecoversFromActivityAndANewReplayStartsAnEmptyWallet() {
        assertEquals(0, buy(1002023, 1).retcode());
        assertEquals(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE, buy(1002023, 1).retcode());
        var limit = fixture.access().prepareLimit(fixture.player, fixture.goods(1002023));
        limit.setHasBoughtInPeriod(0);
        assertEquals(
                1,
                fixture.access()
                        .prepareLimit(fixture.player, fixture.goods(1002023))
                        .getHasBoughtInPeriod());
        fixture.event.config.setScheduleId(fixture.event.config.getScheduleId() + 1);
        fixture.event.data.setScheduleId(fixture.event.config.getScheduleId());
        fixture.event.data.setDetail(new AsterProgress());
        fixture.install();
        assertEquals(
                0,
                fixture.access()
                        .prepareLimit(fixture.player, fixture.goods(1002023))
                        .getHasBoughtInPeriod());
        assertEquals(0, progress().bigCreditBalance());
        assertEquals(0, progress().bigCollectCount());
        assertNotEquals(0, buy(1002023, 1).retcode());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void unconfirmedEssencePurchasesKeepTheirCurrencyAndBlockBothWallets(int failure) {
        var saves = new AtomicInteger();
        var granted = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        buy(
                                fixture.goods(1002011),
                                2,
                                item -> {
                                    granted.incrementAndGet();
                                    if (failure == 2)
                                        throw new IllegalStateException("Delivery uncertain");
                                },
                                () -> {
                                    int n = saves.incrementAndGet();
                                    if (failure == 1 && n == 1 || failure == 3 && n == 2)
                                        throw new IllegalStateException(
                                                "Save acknowledgement lost");
                                }));
        assertEquals(110, progress().pendingShopPurchase().currencyId());
        assertEquals(992, progress().bigCreditBalance());
        assertEquals(118, progress().creditBalance());
        assertEquals(failure == 1 ? 0 : 1, granted.get());
        assertNotEquals(0, buy(1002008, 1).retcode());
        assertNotEquals(0, buy(1002011, 1).retcode());
        assertThrows(
                IllegalStateException.class,
                () -> progress().beginBigClaim(UUID.randomUUID().toString()));
    }

    @Test
    void emptyInventorySpacePreflightPreventsEssenceSpending() {
        var inventory = fixture.player.getInventory();
        inventory.createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(1));
        inventory.getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(new GameItem(104112, 1));
        String before = fixture.event.data.getDetail();
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                AsterShop.buy(fixture.player, 1037, fixture.goods(1002011), 1, fixture.now)
                        .retcode());
        assertEquals(before, fixture.event.data.getDetail());
    }

    @Test
    void concurrentRequestsRespectTheOriginalOneCrownLimit() throws Exception {
        var start = new CountDownLatch(1);
        var grants = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> purchase =
                    () -> {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return buy(
                                        fixture.goods(1002023),
                                        1,
                                        item -> grants.incrementAndGet(),
                                        () -> {})
                                .retcode();
                    };
            var first = pool.submit(purchase);
            var second = pool.submit(purchase);
            start.countDown();
            assertEquals(
                    Set.of(0, Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE),
                    Set.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)));
            assertEquals(1, grants.get());
            assertEquals(890, progress().bigCreditBalance());
            assertEquals(118, progress().creditBalance());
        } finally {
            pool.shutdownNow();
        }
    }
}
