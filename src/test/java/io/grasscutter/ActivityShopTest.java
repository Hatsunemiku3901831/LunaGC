package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass.BuyGoodsReq;
import emu.grasscutter.net.proto.BuyGoodsRspOuterClass.BuyGoodsRsp;
import emu.grasscutter.net.proto.GetActivityShopSheetInfoRspOuterClass.GetActivityShopSheetInfoRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerBuyGoodsReq;
import emu.grasscutter.server.packet.send.*;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

class ActivityShopTest {
    private static final long START = Instant.parse("2026-10-01T02:00:00Z").toEpochMilli();
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private ActivityConfigItem config;
    private PlayerActivityData data;
    private ShopPlayer player;
    private Object previousConfiguration;
    private Field configuration;
    private List<ShopInfo> goods;

    static final class ShopPlayer extends Player {
        ActivityManager activity;
        int rank = 20;
        int payments;
        Runnable afterPayment = () -> {};
        final List<BasePacket> packets = new ArrayList<>();
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public synchronized boolean payItems(
                            Iterable<ItemParamData> costs, int quantity, ActionReason reason) {
                        payments++;
                        boolean paid = super.payItems(costs, quantity, reason);
                        if (paid) afterPayment.run();
                        return paid;
                    }
                };

        @Override
        public int getLevel() {
            return rank;
        }

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}
    }

    @BeforeEach
    void setup() throws Exception {
        Grasscutter.getConfig();
        configuration = ActivityManager.class.getDeclaredField("configuration");
        configuration.setAccessible(true);
        previousConfiguration = configuration.get(null);
        ActivityManager.installConfiguration(new ActivityManager.Configuration(Map.of(), Map.of()));
        player = new ShopPlayer();
        player.setUid(10001);
        player.activity = new ActivityManager(player);
        load(
                "ActivityShopOverallExcelConfigData.json",
                ActivityShopData.class,
                GameData.getActivityShopDataMap());
        load(
                "ActivityShopSheetExcelConfigData.json",
                ActivityShopSheetData.class,
                GameData.getActivityShopSheetDataMap());
        load(
                "AsterActivityPerviewExcelConfigData.json",
                AsterPreviewData.class,
                GameData.getAsterPreviewDataMap());
        var oldItem =
                GameData.getItemDataMap()
                        .put(
                                202,
                                gson.fromJson("{id:202,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        restore.add(
                () -> {
                    if (oldItem == null) GameData.getItemDataMap().remove(202);
                    else GameData.getItemDataMap().put(202, oldItem);
                });
        config = new ActivityConfigItem();
        config.setActivityId(2001);
        config.setActivityType(1100);
        config.setScheduleId(2001711);
        config.setBeginTime(new Date(START));
        config.setEndTime(new Date(START + 30 * 86400000L));
        config.onLoad();
        install();
        data =
                PlayerActivityData.of()
                        .uid(player.getUid())
                        .activityId(2001)
                        .scheduleId(config.getScheduleId())
                        .watcherInfoMap(Map.of())
                        .build();
        data.setPlayer(player);
        player.activity.getPlayerActivityDataMap().put(2001, data);
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/ShopGoodsExcelConfigData.json")),
                        ShopGoodsData[].class);
        goods =
                Arrays.stream(rows)
                        .filter(r -> r.getShopType() == 1037)
                        .map(
                                r -> {
                                    r.onLoad();
                                    return new ShopInfo(r);
                                })
                        .toList();
    }

    private <T extends GameResource> void load(String name, Class<T> type, Int2ObjectMap<T> map)
            throws Exception {
        var original = new Int2ObjectOpenHashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(original);
                });
        map.clear();
        for (var row :
                emu.grasscutter.utils.JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput", name), type)) {
            row.onLoad();
            map.put(row.getId(), row);
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (configuration != null) configuration.set(null, previousConfiguration);
    }

    private void install() {
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(2001, config), Map.of(config.getScheduleId(), config)));
    }

    private ShopSystem catalogue(GameServer server) {
        var manager = new ShopSystem(server);
        var source = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        source.put(1037, goods);
        manager.getCatalog().replaceBase(source);
        return manager;
    }

    private ShopInfo good(int sheet) {
        return goods.stream()
                .filter(g -> g.getSecondarySheetId() == sheet)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void rerunUsesTheActivityIdentityAndSendsUnixSecondsForBothResourceSheets() throws Exception {
        assertFalse(ActivityManager.getScheduleActivityConfigMap().containsKey(2001001));
        assertEquals(
                GameData.getActivityShopSheetDataMap(),
                GameData.getMapByResourceDef(ActivityShopSheetData.class));
        var packet = new PacketGetActivityShopSheetInfoRsp(player, 1037, START);
        var response = GetActivityShopSheetInfoRsp.parseFrom(packet.getData());
        assertEquals(0, response.getRetcode());
        assertEquals(1037, response.getShopType());
        assertEquals(
                List.of(1, 2),
                response.getSheetInfoListList().stream().map(s -> s.getSheetId()).toList());
        assertEquals(START / 1000, response.getSheetInfoList(0).getBeginTime());
        assertEquals(
                Instant.parse("2026-10-07T20:00:00Z").getEpochSecond(),
                response.getSheetInfoList(1).getBeginTime());
        assertEquals(
                config.getEndTime().getTime() / 1000, response.getSheetInfoList(1).getEndTime());
    }

    @Test
    void previewDoesNotPermitAnEarlyPurchaseAndUnlocksAtDayEightFourAm() {
        long opens = Instant.parse("2026-10-07T20:00:00Z").toEpochMilli();
        var before = ActivityShop.resolve(player, 1037, opens - 1);
        assertTrue(before.canDisplay(good(2)));
        assertFalse(before.canPurchase(good(2)));
        assertTrue(before.canPurchase(good(1)));
        assertTrue(ActivityShop.resolve(player, 1037, opens).canPurchase(good(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "before", "ended", "missing"})
    void closedShopsReturnNoSheetsOrGoodsAndDoNotCreateLimits(String state) throws Exception {
        long now = START;
        if (state.equals("disabled")) config.setDisabled(true);
        if (state.equals("before")) now--;
        if (state.equals("ended")) now = config.getEndTime().getTime();
        if (state.equals("missing"))
            ActivityManager.installConfiguration(
                    new ActivityManager.Configuration(Map.of(), Map.of()));
        var response =
                GetActivityShopSheetInfoRsp.parseFrom(
                        new PacketGetActivityShopSheetInfoRsp(player, 1037, now).getData());
        assertEquals(Retcode.RET_SHOP_NOT_OPEN_VALUE, response.getRetcode());
        assertEquals(0, response.getSheetInfoListCount());
        assertEquals(
                0,
                PacketGetShopRsp.buildShop(player, catalogue(null), 1037, (int) (now / 1000))
                        .getGoodsListCount());
        assertTrue(player.getShopLimit().isEmpty());
    }

    @Test
    void exchangeRemainsAvailableAfterGameplayClosesUntilTheScheduleEnds() {
        config.setCloseTime(new Date(START + 14 * 86400000L));
        long now = config.getCloseTime().getTime();
        assertFalse(config.isOpenAt(now));
        assertTrue(ActivityShop.resolve(player, 1037, now).canPurchase(good(2)));
        assertFalse(ActivityShop.resolve(player, 1037, config.getEndTime().getTime()).isOpen());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rank", "schedule", "owner", "player", "enrollment", "condition"})
    void playerEligibilityCannotBeBorrowedFromAnotherPlayerOrAnOldReplay(String failure) {
        switch (failure) {
            case "rank" -> player.rank = 19;
            case "schedule" -> data.setScheduleId(2001001);
            case "owner" -> data.setUid(10002);
            case "player" -> data.setPlayer(new ShopPlayer());
            case "enrollment" -> player.activity.getPlayerActivityDataMap().clear();
            case "condition" -> config.setMeetCondList(List.of(2001001));
        }
        assertFalse(ActivityShop.resolve(player, 1037, START).isOpen());
    }

    @Test
    void resourceSheetAndSubTabAreDifferentFieldsRegardlessOfJsonOrder() {
        for (String json :
                List.of("{secondarySheetId:2,subTabId:0}", "{subTabId:7,secondarySheetId:2}")) {
            var row = gson.fromJson(json, ShopGoodsData.class);
            row.onLoad();
            assertEquals(2, row.getSecondarySheetId());
            assertEquals(2, ShopCatalog.copy(new ShopInfo(row)).getSecondarySheetId());
        }
        assertEquals(25, goods.size());
        assertEquals(10, goods.stream().filter(g -> g.getSecondarySheetId() == 1).count());
        assertEquals(15, goods.stream().filter(g -> g.getSecondarySheetId() == 2).count());
        assertEquals(109, good(1).getCostItemList().get(0).getId());
        assertEquals(110, good(2).getCostItemList().get(0).getId());
        assertEquals(12, good(1).getCostItemList().get(0).getCount());
    }

    @Test
    void listingUsesReplayTimesAndHonorsPreviewWithoutMutatingTheSharedCatalogue() {
        var manager = catalogue(null);
        var shop = PacketGetShopRsp.buildShop(player, manager, 1037, (int) (START / 1000));
        assertEquals(25, shop.getGoodsListCount());
        assertEquals(START / 1000, shop.getGoodsList(0).getBeginTime());
        assertEquals(
                Instant.parse("2026-10-07T20:00:00Z").getEpochSecond(),
                shop.getGoodsList(10).getBeginTime());
        assertEquals(config.getEndTime().getTime() / 1000, shop.getGoodsList(10).getEndTime());
        assertEquals(0, goods.get(10).getBeginTime());
        assertEquals(
                config.getScheduleId(),
                player.getGoodsLimit(good(2).getGoodsId()).getActivityScheduleId());
    }

    @Test
    void unknownPhaseConditionsCannotBeBypassedByPreviewAndInvalidSheetsAreOmitted() {
        var map = GameData.getActivityShopSheetDataMap();
        map.put(
                2,
                gson.fromJson(
                        "{id:2,isAheadPreview:true,cond:["
                            + "{type:'ACTIVITY_SHOP_SHEET_COND_ALCHEMY_SIM_PHASE',param:[3]},{}]}",
                        ActivityShopSheetData.class));
        var access = ActivityShop.resolve(player, 1037, START + 10 * 86400000L);
        assertTrue(access.canDisplay(good(2)));
        assertFalse(access.canPurchase(good(2)));
        map.put(
                2,
                gson.fromJson(
                        "{id:2,cond:[{type:"
                                + "'ACTIVITY_SHOP_SHEET_COND_TIME_EQUAL_GREATER',param:[]}]}",
                        ActivityShopSheetData.class));
        assertEquals(1, ActivityShop.resolve(player, 1037, START).sheets().size());
        assertFalse(ActivityShop.resolve(player, 1037, START).canPurchase(good(2)));
        assertFalse(ActivityShop.resolve(player, 1037, START).canPurchase(new ShopInfo()));
    }

    @Test
    void aFutureSheetWithoutPreviewIsHiddenAndMissingResourcesKeepKnownActivityShopsClosed() {
        GameData.getActivityShopSheetDataMap()
                .put(
                        2,
                        gson.fromJson(
                                "{id:2,cond:[{type:"
                                    + "'ACTIVITY_SHOP_SHEET_COND_TIME_EQUAL_GREATER',param:[8]}]}",
                                ActivityShopSheetData.class));
        assertFalse(ActivityShop.resolve(player, 1037, START).canDisplay(good(2)));
        GameData.getActivityShopDataMap().remove(1037);
        assertFalse(ActivityShop.resolve(player, 1037, START).isOpen());
        assertTrue(ActivityShop.resolve(player, 1001, START).isOpen());
    }

    @Test
    void replayLimitSurvivesReloadAndOnlyResetsWhenTheScheduleChanges() {
        var limit = new ShopLimit();
        limit.setShopGoodId(1002001);
        limit.setHasBoughtInPeriod(3);
        limit.setHasBought(9);
        limit.bindActivitySchedule(config.getScheduleId());
        assertEquals(
                3, limit.getHasBoughtInPeriod(), "Legacy binding must not grant more purchases");
        var reloaded = gson.fromJson(gson.toJson(limit), ShopLimit.class);
        reloaded.bindActivitySchedule(config.getScheduleId());
        assertEquals(3, reloaded.getHasBoughtInPeriod());
        reloaded.bindActivitySchedule(config.getScheduleId() + 1);
        assertEquals(0, reloaded.getHasBoughtInPeriod());
        assertEquals(9, reloaded.getHasBought());
        assertThrows(IllegalArgumentException.class, () -> reloaded.bindActivitySchedule(0));
    }

    @Test
    void actualPurchaseHandlerRejectsClosedShopsAndEarlySheetsBeforePaying() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var server = environment.scene.getWorld().getServer();
            var manager = catalogue(server);
            var shopField = GameServer.class.getDeclaredField("shopSystem");
            shopField.setAccessible(true);
            shopField.set(server, manager);
            var replies = new ArrayList<BasePacket>();
            var session =
                    new GameSession(server) {
                        @Override
                        public Player getPlayer() {
                            return player;
                        }

                        @Override
                        public void send(BasePacket packet) {
                            replies.add(packet);
                        }
                    };
            config.setBeginTime(new Date(System.currentTimeMillis() - 1000));
            config.setEndTime(new Date(System.currentTimeMillis() + 30 * 86400000L));
            config.setDisabled(true);
            var request =
                    BuyGoodsReq.newBuilder()
                            .setShopType(1037)
                            .setBuyCount(1)
                            .setGoods(ShopGoods.newBuilder().setGoodsId(good(2).getGoodsId()))
                            .build();
            var handler = new HandlerBuyGoodsReq();
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(
                    Retcode.RET_SHOP_NOT_OPEN_VALUE,
                    BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            config.setDisabled(false);
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(
                    Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE,
                    BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            assertEquals(0, player.payments);
            assertTrue(player.getShopLimit().isEmpty());
        }
    }

    @Test
    void actualPurchaseChargesAndDeliversThenRejectsTheLimitUntilTheNextReplay() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var server = environment.scene.getWorld().getServer();
            var good = new ShopInfo();
            good.setGoodsId(1002001);
            good.setSecondarySheetId(1);
            good.setGoodsItem(new ItemParamData(202, 10));
            good.setScoin(5);
            good.setCostItemList(List.of());
            good.setBuyLimit(1);
            goods = List.of(good);
            var manager = catalogue(server);
            var shopField = GameServer.class.getDeclaredField("shopSystem");
            shopField.setAccessible(true);
            shopField.set(server, manager);
            var replies = new ArrayList<BasePacket>();
            var session =
                    new GameSession(server) {
                        @Override
                        public Player getPlayer() {
                            return player;
                        }

                        @Override
                        public void send(BasePacket packet) {
                            replies.add(packet);
                        }
                    };
            long now = System.currentTimeMillis();
            config.setBeginTime(new Date(now - 1000));
            config.setEndTime(new Date(now + 30 * 86400000L));
            player.setMora(20);
            var request =
                    BuyGoodsReq.newBuilder()
                            .setShopType(1037)
                            .setBuyCount(1)
                            .setGoods(ShopGoods.newBuilder().setGoodsId(good.getGoodsId()))
                            .build();
            var handler = new HandlerBuyGoodsReq();
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(0, BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            assertEquals(25, player.getMora());
            assertEquals(1, player.getGoodsLimit(good.getGoodsId()).getHasBoughtInPeriod());
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(
                    Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE,
                    BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            assertEquals(25, player.getMora());
            config.setScheduleId(config.getScheduleId() + 1);
            data.setScheduleId(config.getScheduleId());
            install();
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(0, BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            assertEquals(30, player.getMora());
            assertEquals(2, player.payments);
            var limit = player.getGoodsLimit(good.getGoodsId());
            assertEquals(1, limit.getHasBoughtInPeriod());
            assertEquals(2, limit.getHasBought());
            assertEquals(config.getScheduleId(), limit.getActivityScheduleId());
            config.setScheduleId(config.getScheduleId() + 1);
            data.setScheduleId(config.getScheduleId());
            install();
            player.afterPayment = () -> config.setDisabled(true);
            handler.handle(session, new byte[0], request.toByteArray());
            var accepted = BuyGoodsRsp.parseFrom(replies.remove(0).getData());
            assertEquals(
                    0,
                    accepted.getRetcode(),
                    "An accepted delivery still needs its response after closure");
            assertEquals(good.getGoodsId(), accepted.getGoodsList(0).getGoodsId());
            assertEquals(202, accepted.getGoodsList(0).getGoodsItem().getItemId());
            assertEquals(35, player.getMora());
            handler.handle(session, new byte[0], request.toByteArray());
            assertEquals(
                    Retcode.RET_SHOP_NOT_OPEN_VALUE,
                    BuyGoodsRsp.parseFrom(replies.remove(0).getData()).getRetcode());
            assertEquals(3, player.payments);
        }
    }
}
