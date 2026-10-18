package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.net.proto.ShopOuterClass.Shop;
import emu.grasscutter.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class PacketGetShopRsp extends BasePacket {
    public PacketGetShopRsp(Player player, int shopType) {
        super(PacketOpcodes.GetShopRsp);

        this.setData(
                GetShopRspOuterClass.GetShopRsp.newBuilder()
                        .setShop(
                                buildShop(
                                        player,
                                        Grasscutter.getGameServer().getShopSystem(),
                                        shopType))
                        .build());
        player.save();
    }

    public static Shop buildShop(Player player, ShopSystem manager, int shopType) {
        return buildShop(player, manager, shopType, Utils.getCurrentSeconds());
    }

    public static Shop buildShop(Player player, ShopSystem manager, int shopType, int currentTs) {
        // Viewing a shop also creates and refreshes limits; serialize it with purchases.
        synchronized (player.getInventory()) {
            return buildShopLocked(player, manager, shopType, currentTs);
        }
    }

    private static Shop buildShopLocked(
            Player player, ShopSystem manager, int shopType, int currentTs) {
        Shop.Builder shop =
                Shop.newBuilder().setShopType(shopType).setCityId(1).setCityReputationLevel(10);
        var access = ActivityShop.resolve(player, shopType, currentTs * 1000L);
        if (!access.isOpen()) return shop.build();

        // One response must use one catalogue snapshot even when GM reloads it concurrently.
        List<ShopInfo> list = manager.getShopData().get(shopType);
        if (list != null) {
            List<ShopGoods> goodsList = new ArrayList<>();

            for (ShopInfo info : list) {
                if (!access.canDisplay(info)) continue;
                var item = info.resolveGoodsItem(currentTs);
                if (currentTs < info.getBeginTime()
                        || currentTs >= info.getEndTime()
                        || item == null
                        || item.getId() <= 0
                        || item.getCount() <= 0) continue;
                ShopGoods.Builder goods = describeGoods(info, item, access);

                // Native clients append scalar currency costs themselves. Duplicating those
                // currencies in cost_item_list displays them twice in the UI.
                ShopLimit currentShopLimit = access.prepareLimit(player, info);
                int nextRefreshTime = ShopSystem.getShopNextRefreshTime(info);

                if (currentShopLimit != null) {
                    if (currentShopLimit.getNextRefreshTime() > 0
                            && currentShopLimit.getNextRefreshTime() <= currentTs) {
                        currentShopLimit.setHasBoughtInPeriod(0);
                        currentShopLimit.setNextRefreshTime(nextRefreshTime);
                    }
                    goods.setBoughtNum(currentShopLimit.getHasBoughtInPeriod());
                    goods.setNextRefreshTime(currentShopLimit.getNextRefreshTime());
                } else {
                    player.addShopLimit(goods.getGoodsId(), 0, nextRefreshTime);
                    goods.setNextRefreshTime(nextRefreshTime);
                }

                var itemData = GameData.getItemDataMap().get(item.getId());
                if (!SpecialCosmeticShop.kind(item.getId()).isEmpty()) {
                    goods.setSingleLimit(1);
                    if (SpecialCosmeticShop.owned(player, item.getId())) {
                        goods.setBoughtNum(Math.max(1, goods.getBoughtNum()));
                    }
                }

                goodsList.add(goods.build());
            }

            shop.addAllGoodsList(goodsList);
        }

        manager.getFreeStore().addProducts(shop, player);
        return shop.build();
    }

    /** Describe the accepted server good even if its shop closes during delivery. */
    public static ShopGoods.Builder describeGoods(
            ShopInfo info, ItemParamData item, ActivityShop.Access access) {
        var goods =
                ShopGoods.newBuilder()
                        .setGoodsId(info.getGoodsId())
                        .setGoodsItem(
                                ItemParamOuterClass.ItemParam.newBuilder()
                                        .setItemId(item.getId())
                                        .setCount(item.getCount()))
                        .setScoin(info.getScoin())
                        .setHcoin(info.getHcoin())
                        .setBeginTime(info.getBeginTime())
                        .setEndTime(info.getEndTime())
                        .setMinLevel(info.getMinLevel())
                        .setMaxLevel(info.getMaxLevel())
                        .setMcoin(info.getMcoin())
                        .setBeyondMcoin(info.getBeyondMcoin())
                        .setSingleLimit(info.getBuyLimit());
        var sheet = access.sheet(info);
        if (sheet != null) {
            goods.setBeginTime(Math.max(info.getBeginTime(), sheet.beginTime()));
            goods.setEndTime(Math.min(info.getEndTime(), sheet.endTime()));
        }
        if (info.getCostItemList() != null) {
            goods.addAllCostItemList(
                    info.getCostItemList().stream()
                            .map(
                                    cost ->
                                            ItemParamOuterClass.ItemParam.newBuilder()
                                                    .setItemId(cost.getId())
                                                    .setCount(cost.getCount())
                                                    .build())
                            .collect(Collectors.toList()));
        }
        if (!SpecialCosmeticShop.kind(item.getId()).isEmpty()) goods.setSingleLimit(1);
        return goods;
    }
}
