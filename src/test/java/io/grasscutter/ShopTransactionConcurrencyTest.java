package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.ReliquarySetData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass.BuyGoodsReq;
import emu.grasscutter.net.proto.BuyGoodsRspOuterClass.BuyGoodsRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerBuyGoodsReq;
import emu.grasscutter.server.packet.send.PacketGetShopRsp;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShopTransactionConcurrencyTest {
    private static final int ITEM = 990071;
    private static final int GOODS = 990072;
    private ItemData previous;

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        previous = GameData.getItemDataMap().put(ITEM, new Gson().fromJson(
                "{\"id\":" + ITEM + ",\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_EXCHANGE\",\"stackLimit\":9999}",
                ItemData.class));
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getItemDataMap().remove(ITEM);
        else GameData.getItemDataMap().put(ITEM, previous);
    }

    private static ShopInfo good() {
        var good = new ShopInfo();
        good.setGoodsId(GOODS);
        good.setGoodsItem(new ItemParamData(ITEM, 1));
        good.setBuyLimit(1);
        good.setScoin(10);
        good.setCostItemList(List.of());
        return good;
    }

    private static Int2ObjectMap<List<ShopInfo>> catalogue() {
        var map = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        map.put(1001, List.of(good()));
        return map;
    }

    private static class ShopPlayer extends Player {
        final AtomicInteger payments = new AtomicInteger();
        final AtomicInteger delivered = new AtomicInteger();
        final List<Integer> deliveredIds = new CopyOnWriteArrayList<>();
        final CountDownLatch paymentEntered = new CountDownLatch(1);
        final CountDownLatch releasePayment = new CountDownLatch(1);
        final Inventory inventory = new Inventory(this) {
            @Override public boolean payItems(Iterable<ItemParamData> costs, int quantity) {
                int call = payments.incrementAndGet();
                if (call == 1) {
                    paymentEntered.countDown();
                    try {
                        assertTrue(releasePayment.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return true;
            }

            @Override public boolean addItem(GameItem item, ActionReason reason, boolean skip, boolean notify) {
                delivered.addAndGet(item.getCount());
                deliveredIds.add(item.getItemId());
                return true;
            }

            @Override public void addItems(Collection<GameItem> items, ActionReason reason) {
                for (var item : items) addItem(item, reason, true, true);
            }
        };
        @Override public Inventory getInventory() { return inventory == null ? super.getInventory() : inventory; }
        @Override public void save() {}
        @Override public void sendPacket(BasePacket packet) {}
    }

    @Test
    void concurrentRequestsCannotBothBuyTheLastLimitedGood() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            var system = new ShopSystem(server);
            system.getCatalog().replaceBase(catalogue());
            var field = GameServer.class.getDeclaredField("shopSystem");
            field.setAccessible(true);
            field.set(server, system);
            var player = new ShopPlayer();
            player.addShopLimit(GOODS, 0, 0);
            var replies = new CopyOnWriteArrayList<BasePacket>();
            var session = new GameSession(server) {
                @Override public Player getPlayer() { return player; }
                @Override public void send(BasePacket packet) { replies.add(packet); }
            };
            var request = BuyGoodsReq.newBuilder().setShopType(1001).setBuyCount(1)
                    .setGoods(ShopGoods.newBuilder().setGoodsId(GOODS)).build().toByteArray();
            var failure = new AtomicReference<Throwable>();
            Runnable buy = () -> {
                try { new HandlerBuyGoodsReq().handle(session, new byte[0], request); }
                catch (Throwable e) { failure.compareAndSet(null, e); }
            };
            var first = new Thread(buy, "first-shop-purchase");
            var second = new Thread(buy, "second-shop-purchase");
            first.setDaemon(true);
            second.setDaemon(true);
            try {
                first.start();
                assertTrue(player.paymentEntered.await(5, TimeUnit.SECONDS));
                second.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (second.isAlive() && second.getState() != Thread.State.BLOCKED
                        && player.payments.get() < 2 && System.nanoTime() < deadline) Thread.yield();
            } finally {
                player.releasePayment.countDown();
                first.join(5000);
                second.join(5000);
            }
            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertNull(failure.get(), () -> String.valueOf(failure.get()));
            assertEquals(1, player.delivered.get(), "Only one unit remains in this refresh period");
            assertEquals(1, player.payments.get(), "The rejected request must not pay");
            assertEquals(1, player.getGoodsLimit(GOODS).getHasBoughtInPeriod());
            var codes = new ArrayList<Integer>();
            for (var reply : replies) codes.add(BuyGoodsRsp.parseFrom(reply.getData()).getRetcode());
            assertEquals(2, codes.size());
            assertTrue(codes.contains(0));
            assertTrue(codes.contains(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE));
        }
    }

    @Test
    void aConcurrentCatalogueReloadCannotSplitOneShopResponseAcrossSnapshots() {
        var reads = new AtomicInteger();
        var source = catalogue();
        var manager = new ShopSystem(null) {
            @Override public Int2ObjectMap<List<ShopInfo>> getShopData() {
                return reads.getAndIncrement() == 0 ? source : Int2ObjectMaps.emptyMap();
            }
        };
        var shop = PacketGetShopRsp.buildShop(new ShopPlayer(), manager, 1001);
        assertEquals(1, shop.getGoodsListCount());
        assertEquals(GOODS, shop.getGoodsList(0).getGoodsId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void highNumberedOrdinaryGoodsKeepTheirNormalDelivery(boolean ordinaryArtifact) throws Exception {
        if (ordinaryArtifact) GameData.getItemDataMap().put(ITEM, new Gson().fromJson(
                "{\"id\":" + ITEM + ",\"itemType\":\"ITEM_RELIQUARY\",\"materialType\":\"MATERIAL_EXCHANGE\",\"rankLevel\":5}", ItemData.class));
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            var manager = new ShopSystem(server);
            var row = good();
            row.setGoodsId(ArtifactShop.GOODS_ID_BASE + 9999);
            var source = new Int2ObjectOpenHashMap<List<ShopInfo>>();
            source.put(1001, List.of(row));
            manager.getCatalog().replaceBase(source);
            var artifactField = ShopSystem.class.getDeclaredField("artifactShop");
            artifactField.setAccessible(true);
            artifactField.set(manager, new ArtifactShop() {
                @Override public GameItem roll(ItemData piece) {
                    fail("An ordinary artifact listing must not receive the generated shop's special rolls");
                    return null;
                }
            });
            var serverField = GameServer.class.getDeclaredField("shopSystem");
            serverField.setAccessible(true);
            serverField.set(server, manager);
            var player = new ShopPlayer();
            player.releasePayment.countDown();
            var replies = new ArrayList<BasePacket>();
            var session = new GameSession(server) {
                @Override public Player getPlayer() { return player; }
                @Override public void send(BasePacket packet) { replies.add(packet); }
            };
            new HandlerBuyGoodsReq().handle(session, new byte[0], BuyGoodsReq.newBuilder()
                    .setShopType(1001).setBuyCount(1)
                    .setGoods(ShopGoods.newBuilder().setGoodsId(row.getGoodsId())).build().toByteArray());
            assertEquals(0, BuyGoodsRsp.parseFrom(replies.get(0).getData()).getRetcode());
            assertEquals(List.of(ITEM), player.deliveredIds);
            assertEquals(1, player.payments.get());
        }
    }

    @Test
    void artifactReloadCannotChangeThePieceAlreadySelectedFromTheCatalogue() throws Exception {
        var itemsBefore = new HashMap<>(GameData.getItemDataMap());
        var setsBefore = new HashMap<>(GameData.getReliquarySetDataMap());
        var options = emu.grasscutter.config.Configuration.GAME_OPTIONS.artifactShop;
        boolean enabledBefore = options.enabled;
        var gson = new Gson();
        var older = gson.fromJson("{\"id\":990075,\"itemType\":\"ITEM_RELIQUARY\","
                + "\"materialType\":\"MATERIAL_EXCHANGE\",\"rankLevel\":5,\"setId\":2,"
                + "\"equipType\":\"EQUIP_BRACER\",\"appendPropNum\":4,\"appendPropDepotId\":501,\"mainPropDepotId\":4000}", ItemData.class);
        var newer = gson.fromJson(gson.toJson(older).replace("990075", "990076").replace("\"setId\":2", "\"setId\":1"), ItemData.class);
        try (var fixture = new AbilityTestFixture()) {
            GameData.getItemDataMap().clear();
            GameData.getItemDataMap().put(older.getId(), older);
            for (int id : List.of(1, 2)) GameData.getReliquarySetDataMap().put(id,
                    gson.fromJson("{\"setId\":" + id + ",\"equipAffixId\":1}", ReliquarySetData.class));
            options.enabled = true;
            var server = fixture.scene.getWorld().getServer();
            var armed = new AtomicBoolean();
            var manager = new ShopSystem(server) {
                @Override public ArtifactShop getArtifactShop() {
                    // The request already obtained its immutable ShopInfo. An online reload now
                    // inserts an earlier-sorted set, reassigning the same generated goods ID.
                    if (armed.compareAndSet(true, false)) {
                        GameData.getItemDataMap().put(newer.getId(), newer);
                        loadArtifactShop();
                    }
                    return super.getArtifactShop();
                }
            };
            var artifact = new ArtifactShop() {
                @Override public GameItem roll(ItemData piece) {
                    var result = new GameItem();
                    result.setItemId(piece.getId());
                    result.setItemData(piece);
                    result.setCount(1);
                    return result;
                }
            };
            var artifactField = ShopSystem.class.getDeclaredField("artifactShop");
            artifactField.setAccessible(true);
            artifactField.set(manager, artifact);
            manager.loadArtifactShop();
            assertEquals(older.getId(), artifact.getPiece(ArtifactShop.GOODS_ID_BASE).getId());
            var serverField = GameServer.class.getDeclaredField("shopSystem");
            serverField.setAccessible(true);
            serverField.set(server, manager);
            var player = new ShopPlayer();
            player.getProperties().put(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_LEVEL.getId(), 20);
            player.releasePayment.countDown();
            var replies = new ArrayList<BasePacket>();
            var session = new GameSession(server) {
                @Override public Player getPlayer() { return player; }
                @Override public void send(BasePacket packet) { replies.add(packet); }
            };
            armed.set(true);
            new HandlerBuyGoodsReq().handle(session, new byte[0], BuyGoodsReq.newBuilder()
                    .setShopType(options.shopId).setBuyCount(1)
                    .setGoods(ShopGoods.newBuilder().setGoodsId(ArtifactShop.GOODS_ID_BASE)).build().toByteArray());
            assertFalse(armed.get(), "The request reached the forced reload interleaving");
            assertEquals(newer.getId(), artifact.getPiece(ArtifactShop.GOODS_ID_BASE).getId());
            assertEquals(List.of(older.getId()), player.deliveredIds,
                    "Price, reply and delivered artifact must all refer to the request's same row");
            assertEquals(0, BuyGoodsRsp.parseFrom(replies.get(0).getData()).getRetcode());
        } finally {
            options.enabled = enabledBefore;
            GameData.getItemDataMap().clear();
            GameData.getItemDataMap().putAll(itemsBefore);
            GameData.getReliquarySetDataMap().clear();
            GameData.getReliquarySetDataMap().putAll(setsBefore);
        }
    }
}
