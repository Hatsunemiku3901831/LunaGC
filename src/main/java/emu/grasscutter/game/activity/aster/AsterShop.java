package emu.grasscutter.game.activity.aster;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.salesman.SalesmanRewardDelivery;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;
import java.util.function.*;

/** Event credit is part of the replay document, rather than a material stack in the bag. */
public final class AsterShop {
    private AsterShop() {}

    public static final int SHOP_TYPE = 1037;

    public record Result(int retcode, int bought, ItemParamData item, ActivityShop.Access access) {
        private static Result reject(int code) {
            return new Result(code, 0, null, null);
        }
    }

    public static boolean usesCurrency(ShopInfo goods) {
        return goods.getCostItemList() != null
                && goods.getCostItemList().stream()
                        .anyMatch(
                                cost ->
                                        cost != null
                                                && (cost.getId() == 109 || cost.getId() == 110));
    }

    public static Result buy(
            Player player, int shopType, ShopInfo goods, int count, int timestamp) {
        if (player == null || shopType != SHOP_TYPE)
            return Result.reject(Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE);
        synchronized (player.getInventory()) {
            var access = ActivityShop.resolve(player, shopType, timestamp * 1000L);
            if (!access.isOpen() || access.config() == null)
                return Result.reject(Retcode.RET_SHOP_NOT_OPEN_VALUE);
            var data =
                    player.getActivityManager()
                            .getPlayerActivityDataMap()
                            .get(AsterSchedule.ACTIVITY_ID);
            if (data == null) return Result.reject(Retcode.RET_SHOP_NOT_OPEN_VALUE);
            synchronized (data) {
                if (data.getPlayer() != player || data.getUid() != player.getUid())
                    return Result.reject(Retcode.RET_SHOP_NOT_OPEN_VALUE);
                try {
                    var limit = access.prepareLimit(player, goods);
                    if (limit == null
                            || limit.getHasBought() < 0
                            || count <= 0
                            || (long) limit.getHasBought() + count > Integer.MAX_VALUE)
                        return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
                    return buy(
                            data,
                            access,
                            player.getLevel(),
                            timestamp,
                            goods,
                            count,
                            limit.getHasBoughtInPeriod(),
                            item -> SalesmanRewardDelivery.validate(player, List.of(item)),
                            item -> {
                                // The event document owns the period count; this is its
                                // client-facing mirror.
                                limit.setHasBoughtInPeriod(
                                        AsterSchedule.progress(data)
                                                .shopBought(goods.getGoodsId()));
                                limit.setHasBought(Math.addExact(limit.getHasBought(), count));
                                limit.setNextRefreshTime(0);
                                SalesmanRewardDelivery.grant(
                                        player, List.of(item), ActionReason.Shop);
                            },
                            data::saveSync);
                } catch (RuntimeException uncertain) {
                    Grasscutter.getLogger()
                            .error(
                                    "Aster shop requires reconciliation: uid={}, schedule={},"
                                        + " goods={}",
                                    player.getUid(),
                                    data.getScheduleId(),
                                    goods.getGoodsId(),
                                    uncertain);
                    return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
                }
            }
        }
    }

    /** The caller holds the inventory monitor throughout preflight, reservation and delivery. */
    public static Result buy(
            PlayerActivityData data,
            ActivityShop.Access access,
            int rank,
            int timestamp,
            ShopInfo goods,
            int count,
            int previouslyBought,
            ToIntFunction<ItemParamData> validate,
            Consumer<ItemParamData> grant,
            Runnable save) {
        var config = access == null ? null : access.config();
        var preview = GameData.getAsterPreviewDataMap().get(AsterSchedule.ACTIVITY_ID);
        if (data == null
                || config == null
                || !access.activityShop()
                || !access.isOpen()
                || config.getActivityId() != AsterSchedule.ACTIVITY_ID
                || data.getActivityId() != AsterSchedule.ACTIVITY_ID
                || data.getScheduleId() != config.getScheduleId()
                || !config.isActiveAt(timestamp * 1000L)
                || preview == null
                || rank < preview.getUnlockLevel())
            return Result.reject(Retcode.RET_SHOP_NOT_OPEN_VALUE);
        var sheet = goods == null ? null : access.sheet(goods);
        if (sheet == null
                || !access.canPurchase(goods)
                || timestamp < sheet.beginTime()
                || timestamp >= sheet.endTime()
                || timestamp < goods.getBeginTime()
                || timestamp >= goods.getEndTime()
                || rank < goods.getMinLevel()
                || rank > goods.getMaxLevel())
            return Result.reject(Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE);
        if (count <= 0
                || previouslyBought < 0
                || goods.getGoodsId() <= 0
                || goods.getBuyLimit() < 0
                || goods.getShopRefreshType() != ShopInfo.ShopRefreshType.NONE
                || goods.getRotateId() != 0
                || goods.getScoin() != 0
                || goods.getHcoin() != 0
                || goods.getMcoin() != 0
                || goods.getBeyondMcoin() != 0) return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
        var costs = goods.getCostItemList();
        if (costs == null
                || costs.isEmpty()
                || costs.stream()
                        .anyMatch(
                                cost ->
                                        cost == null
                                                || cost.getId() != 109
                                                || cost.getCount() <= 0))
            return Result.reject(Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE);
        // Chapter-three tokens (110) have no implemented earning path yet. Never spend credit for
        // them.
        var unit = goods.resolveGoodsItem(timestamp);
        if (unit == null || unit.getId() <= 0 || unit.getCount() <= 0)
            return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
        final ItemParamData item;
        final int cost;
        try {
            int price = 0;
            for (var entry : costs) price = Math.addExact(price, entry.getCount());
            cost = Math.multiplyExact(price, count);
            item = new ItemParamData(unit.getId(), Math.multiplyExact(unit.getCount(), count));
        } catch (ArithmeticException overflow) {
            return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
        }
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            if (progress.hasPendingReward()) return Result.reject(Retcode.RET_SVR_ERROR_VALUE);
            int bought = Math.max(progress.shopBought(goods.getGoodsId()), previouslyBought);
            if ((long) bought + count > Integer.MAX_VALUE
                    || goods.getBuyLimit() != 0 && (long) bought + count > goods.getBuyLimit())
                return Result.reject(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT_VALUE);
            if (progress.creditBalance() < cost)
                return Result.reject(Retcode.RET_SHOP_CONTENT_NOT_MATCH_VALUE);
            int invalid = validate.applyAsInt(item);
            if (invalid != 0) return Result.reject(invalid);

            progress.beginShopPurchase(
                    new AsterProgress.ShopPurchase(
                            goods.getGoodsId(), count, cost, item.getId(), item.getCount()),
                    bought);
            data.setDetail(progress);
            // A lost acknowledgement may mean this write was accepted. Keep the reservation even on
            // failure.
            save.run();
            String reserved = data.getDetail();
            grant.accept(item);
            progress.finishShopPurchase();
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException uncertain) {
                data.setDetailJson(reserved);
                throw uncertain;
            }
            return new Result(
                    0,
                    progress.shopBought(goods.getGoodsId()),
                    new ItemParamData(unit.getId(), unit.getCount()),
                    access);
        }
    }
}
