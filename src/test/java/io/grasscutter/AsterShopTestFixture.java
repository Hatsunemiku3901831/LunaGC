package io.grasscutter;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.BasePacket;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

final class AsterShopTestFixture implements AutoCloseable {
    final AsterMidTestFixture event = new AsterMidTestFixture();
    final ShopPlayer player;
    final int now = (int) (event.now / 1000);
    final List<ShopInfo> goods;
    private final List<Runnable> restore = new ArrayList<>();
    private final Field configuration = ActivityManager.class.getDeclaredField("configuration");
    private final Object previous;

    @Entity(value = "players", useDiscriminator = false)
    static final class ShopPlayer extends Player {
        @Transient ActivityManager activity;
        @Transient final List<BasePacket> packets = new ArrayList<>();

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void save() {}

        @Override
        public boolean setProperty(PlayerProperty property, int value) {
            getProperties().put(property.getId(), value);
            return true;
        }
    }

    AsterShopTestFixture() throws Exception {
        configuration.setAccessible(true);
        previous = configuration.get(null);
        ActivityManager.installConfiguration(new ActivityManager.Configuration(Map.of(), Map.of()));
        player = new ShopPlayer();
        var team = Player.class.getDeclaredField("teamManager");
        team.setAccessible(true);
        team.set(player, new emu.grasscutter.game.player.TeamManager(player));
        player.setUid(event.data.getUid());
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        player.activity = new ActivityManager(player);
        load(
                "ActivityShopOverallExcelConfigData.json",
                ActivityShopData.class,
                GameData.getActivityShopDataMap());
        load(
                "ActivityShopSheetExcelConfigData.json",
                ActivityShopSheetData.class,
                GameData.getActivityShopSheetDataMap());
        goods =
                emu.grasscutter.utils.JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/ShopGoodsExcelConfigData.json"),
                                ShopGoodsData.class)
                        .stream()
                        .filter(row -> row.getShopType() == AsterShop.SHOP_TYPE)
                        .map(
                                row -> {
                                    row.onLoad();
                                    return new ShopInfo(row);
                                })
                        .toList();
        var itemIds = new HashSet<>(Set.of(109, 110, 201, 202));
        goods.forEach(good -> itemIds.add(good.getGoodsItem().getId()));
        load("MaterialExcelConfigData.json", ItemData.class, GameData.getItemDataMap(), itemIds);
        player.activity.getPlayerActivityDataMap().put(2001, event.data);
        event.data.setPlayer(player);
        install();
        var progress = AsterSchedule.progress(event.data);
        AsterFragments.areas()
                .values()
                .forEach(
                        area ->
                                area.configIds()
                                        .forEach(id -> progress.collect(area.groupId(), id)));
        event.data.setDetail(progress);
    }

    void install() {
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(2001, event.config),
                        Map.of(event.config.getScheduleId(), event.config)));
    }

    ActivityShop.Access access() {
        return ActivityShop.resolve(player, AsterShop.SHOP_TYPE, now * 1000L);
    }

    ShopInfo goods(int id) {
        return goods.stream().filter(g -> g.getGoodsId() == id).findFirst().orElseThrow();
    }

    void loadBigRewards() throws Exception {
        event.load(
                "MpPlayGroupExcelConfigData.json",
                MpPlayGroupData.class,
                GameData.getMpPlayGroupDataMap(),
                id -> id == 2);
        event.load(
                "../Server/DropTableExcelConfigData.json",
                DropTableData.class,
                GameData.getDropTableDataMap(),
                id -> id >= 210020000 && id <= 210020080);
        event.load(
                "MaterialExcelConfigData.json",
                ItemData.class,
                GameData.getItemDataMap(),
                id -> id == 102 || id == 105);
    }

    private <T extends GameResource> void load(String name, Class<T> type, Int2ObjectMap<T> map)
            throws Exception {
        load(name, type, map, Set.of());
    }

    private <T extends GameResource> void load(
            String name, Class<T> type, Int2ObjectMap<T> map, Set<Integer> ids) throws Exception {
        var before = new HashMap<>(map);
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(before);
                });
        map.clear();
        for (var row :
                emu.grasscutter.utils.JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput", name), type)) {
            if (!ids.isEmpty() && !ids.contains(row.getId())) continue;
            row.onLoad();
            map.put(row.getId(), row);
        }
    }

    @Override
    public void close() throws Exception {
        for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
        configuration.set(null, previous);
        event.close();
    }
}
