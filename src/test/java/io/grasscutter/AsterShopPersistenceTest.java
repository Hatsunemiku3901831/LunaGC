package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass.BuyGoodsReq;
import emu.grasscutter.net.proto.BuyGoodsRspOuterClass.BuyGoodsRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerBuyGoodsReq;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Actual shop requests, item/player documents and activity reservations in an isolated Mongo. */
class AsterShopPersistenceTest {
    @ParameterizedTest
    @CsvSource({"109,0", "109,1", "109,2", "109,3", "110,0", "110,1", "110,2", "110,3"})
    void actualPurchasesAndUncertainWritesSurviveMongoReload(int currency, int failure)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_aster_shop_" + UUID.randomUUID().toString().replace("-", "");
        try (var fixture = new AsterShopTestFixture();
                var scene = new AbilityTestFixture();
                var client = MongoClients.create(uri)) {
            int goodsId = currency == 109 ? 1002008 : 1002011;
            int itemId = currency == 109 ? 104003 : 104302;
            if (currency == 110) {
                fixture.loadBigRewards();
                fixture.event.config.setBeginTime(new Date(fixture.event.now - 8 * 86400000L));
                var progress = AsterSchedule.progress(fixture.event.data);
                progress.openBigReward(Map.of(fixture.player.getUid(), AsterBigRewards.reward(8)));
                fixture.event.data.setDetail(progress);
                assertEquals(
                        0,
                        AsterBigRewards.takeShared(
                                fixture.event.data,
                                fixture.event.data,
                                fixture.event.config,
                                20,
                                fixture.event.now,
                                progress.bigReward().token(),
                                () -> true,
                                reward -> assertEquals(50, reward.essence()),
                                () -> {},
                                () -> {}));
            }
            var field = DatabaseManager.class.getDeclaredField("gameDatastore");
            field.setAccessible(true);
            var original = field.get(null);
            try {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                var store =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder()
                                        .storeEmpties(true)
                                        .storeNulls(false)
                                        .build());
                store.getMapper()
                        .map(
                                PlayerActivityData.class,
                                AsterShopTestFixture.ShopPlayer.class,
                                GameItem.class);
                store.save(fixture.event.data);
                store.save(fixture.player);
                var activitySaves = new AtomicInteger();
                var itemSaves = new AtomicInteger();
                var fault = new AtomicBoolean();
                field.set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                    if (method.getName().equals("save")
                                            && args.length == 2
                                            && args[1] instanceof InsertOneOptions options) {
                                        assertEquals(
                                                "majority", options.writeConcern().getWString());
                                        assertEquals(
                                                Boolean.TRUE, options.writeConcern().getJournal());
                                        if (args[0] instanceof PlayerActivityData) {
                                            int n = activitySaves.incrementAndGet();
                                            if (failure == 1 && n == 1 || failure == 3 && n == 2) {
                                                fault.set(true);
                                                throw new IllegalStateException(
                                                        "Activity write accepted, acknowledgement"
                                                                + " lost");
                                            }
                                        }
                                        if (args[0] instanceof GameItem
                                                && itemSaves.incrementAndGet() == 1
                                                && failure == 2) {
                                            fault.set(true);
                                            throw new IllegalStateException(
                                                    "Item write accepted, acknowledgement lost");
                                        }
                                    }
                                    return result;
                                }));
                var server = scene.scene.getWorld().getServer();
                var manager = new ShopSystem(server);
                var catalogue = new Int2ObjectOpenHashMap<List<ShopInfo>>();
                catalogue.put(1037, fixture.goods);
                manager.getCatalog().replaceBase(catalogue);
                var shopField = GameServer.class.getDeclaredField("shopSystem");
                shopField.setAccessible(true);
                shopField.set(server, manager);
                var replies = new ArrayList<BasePacket>();
                var session =
                        new GameSession(server) {
                            @Override
                            public emu.grasscutter.game.player.Player getPlayer() {
                                return fixture.player;
                            }

                            @Override
                            public void send(BasePacket packet) {
                                replies.add(packet);
                            }
                        };
                var handler = new HandlerBuyGoodsReq();
                // Supplied item and price are deliberately forged; the server catalogue must win.
                var request =
                        BuyGoodsReq.newBuilder()
                                .setShopType(1037)
                                .setBuyCount(2)
                                .setGoods(
                                        ShopGoods.newBuilder()
                                                .setGoodsId(goodsId)
                                                .setScoin(1)
                                                .setGoodsItem(
                                                        ItemParam.newBuilder()
                                                                .setItemId(201)
                                                                .setCount(999999)))
                                .build();
                handler.handle(session, new byte[0], request.toByteArray());
                var answer = BuyGoodsRsp.parseFrom(replies.remove(0).getData());
                assertEquals(failure == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE, answer.getRetcode());
                assertEquals(failure != 0, fault.get());
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                var reloaded =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", fixture.player.getUid()))
                                .first();
                assertNotNull(reloaded);
                var ledger = AsterSchedule.progress(reloaded);
                assertEquals(currency == 109 ? 110 : 118, ledger.creditBalance());
                assertEquals(currency == 109 ? 0 : 42, ledger.bigCreditBalance());
                assertEquals(118, ledger.credit());
                assertEquals(currency == 109 ? 0 : 50, ledger.bigCredit());
                assertEquals(currency == 109 ? 0 : 1, ledger.bigCollectCount());
                assertEquals(2, ledger.shopBought(goodsId));
                assertEquals(failure == 1 || failure == 2, ledger.pendingShopPurchase() != null);
                if (ledger.pendingShopPurchase() != null)
                    assertEquals(currency, ledger.pendingShopPurchase().currencyId());
                var items =
                        store.find(GameItem.class)
                                .filter(
                                        Filters.eq("ownerId", fixture.player.getUid()),
                                        Filters.eq("itemId", itemId))
                                .iterator()
                                .toList();
                assertEquals(failure == 1 ? 0 : 1, items.size());
                if (!items.isEmpty()) assertEquals(2, items.get(0).getCount());
                assertNotNull(AsterSchedule.progress(fixture.event.data));
                if (failure != 0) {
                    handler.handle(session, new byte[0], request.toByteArray());
                    assertEquals(
                            Retcode.RET_SVR_ERROR_VALUE,
                            BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
                    assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                    assertEquals(
                            failure == 1 ? 0 : 2,
                            fixture.player.getInventory().getItemCountById(itemId));
                    assertEquals(
                            currency == 109 ? 110 : 118,
                            AsterSchedule.progress(fixture.event.data).creditBalance());
                    assertEquals(
                            currency == 109 ? 0 : 42,
                            AsterSchedule.progress(fixture.event.data).bigCreditBalance());
                } else {
                    assertEquals(2, answer.getGoodsList(0).getBoughtNum());
                    assertEquals(itemId, answer.getGoodsList(0).getGoodsItem().getItemId());
                    assertEquals(
                            1,
                            answer.getGoodsList(0).getGoodsItem().getCount(),
                            "The response describes one unit, not the delivered batch");
                    assertEquals(4, answer.getGoodsList(0).getCostItemList(0).getCount());
                    assertEquals(currency, answer.getGoodsList(0).getCostItemList(0).getItemId());
                    assertEquals(
                            2,
                            store.find(AsterShopTestFixture.ShopPlayer.class)
                                    .first()
                                    .getGoodsLimit(goodsId)
                                    .getHasBoughtInPeriod());
                    fixture.event.data.setDetailJson(reloaded.getDetail());
                    fixture.player.getGoodsLimit(goodsId).setHasBoughtInPeriod(0);
                    assertEquals(
                            2,
                            fixture.access()
                                    .prepareLimit(fixture.player, fixture.goods(goodsId))
                                    .getHasBoughtInPeriod());
                    var mora =
                            request.toBuilder()
                                    .setBuyCount(1)
                                    .setGoods(ShopGoods.newBuilder().setGoodsId(1002009))
                                    .build();
                    handler.handle(session, new byte[0], mora.toByteArray());
                    assertEquals(
                            0, BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
                    assertEquals(10000, fixture.player.getMora());
                    assertEquals(
                            10000,
                            store.find(AsterShopTestFixture.ShopPlayer.class).first().getMora());
                    assertEquals(
                            currency == 109 ? 106 : 114,
                            AsterSchedule.progress(store.find(PlayerActivityData.class).first())
                                    .creditBalance());
                    assertEquals(
                            currency == 109 ? 0 : 42,
                            AsterSchedule.progress(store.find(PlayerActivityData.class).first())
                                    .bigCreditBalance());
                }
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, original);
                client.getDatabase(database).drop();
            }
        }
    }
}
