package emu.grasscutter.game.activity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ShopGoodsData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.ActivityShop;
import emu.grasscutter.game.shop.BeyondCurrency;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.server.packet.send.PacketActivityInfoNotify;

import java.util.*;

/** Resource-owned virtual currencies live in their activity document and replay schedule. */
public final class ActivityCurrencies {
    private ActivityCurrencies() {}

    private static final Set<Integer> GLOBAL =
            Set.of(101, 102, 105, 106, 107, 121, 201, 202, 203, 204, 207);

    /** Read modern declarations and the original exchange prices of older activities. */
    static Map<Integer, Integer> catalog() {
        var owners = new HashMap<Integer, Integer>();
        for (var activity : GameData.getActivityDataMap().values()) {
            var ids = activity.getActivityCoinIdList();
            if (ids != null) for (var id : ids) declare(owners, id, activity.getActivityId());
        }
        var shops = new HashMap<Integer, Integer>();
        for (var shop : GameData.getActivityShopDataMap().values()) {
            var owner = ActivityShop.sourceActivityId(shop.getScheduleId());
            if (owner != null && GameData.getActivityDataMap().containsKey(owner.intValue()))
                shops.put(shop.getShopTypeId(), owner);
        }
        for (var entry : GameData.getMapByResourceDef(ShopGoodsData.class).values()) {
            var goods = (ShopGoodsData) entry;
            var owner = shops.get(goods.getShopType());
            if (owner == null || owner == 2001 || goods.getCostItems() == null) continue;
            for (var cost : goods.getCostItems()) {
                if (cost == null || cost.getCount() <= 0 || global(cost.getId())) continue;
                var item = GameData.getItemDataMap().get(cost.getId());
                if (item != null && item.getItemType() == ItemType.ITEM_VIRTUAL)
                    declare(owners, cost.getId(), owner);
            }
        }
        return Map.copyOf(owners);
    }

    private static boolean global(int id) {
        return GLOBAL.contains(id) || BeyondCurrency.propertyForItem(id) != null;
    }

    private static void declare(Map<Integer, Integer> owners, Integer id, int owner) {
        var item = id == null ? null : GameData.getItemDataMap().get(id.intValue());
        if (id == null || id <= 0 || owner <= 0 || global(id) || id == 109 || id == 110)
            throw new IllegalArgumentException(
                    "Invalid activity currency declaration: " + owner + "/" + id);
        // An unavailable item cannot be received; unrelated activities can still load.
        if (item == null || item.getItemType() != ItemType.ITEM_VIRTUAL) return;
        var prior = owners.putIfAbsent(id, owner);
        if (prior != null && prior != owner)
            throw new IllegalArgumentException("Activity currency has conflicting owners: " + id);
    }

    public static boolean contains(int id) {
        return ActivityManager.configurationSnapshot().currencyActivities().containsKey(id);
    }

    static boolean belongsTo(int activityId) {
        return ActivityManager.configurationSnapshot()
                .currencyActivities()
                .containsValue(activityId);
    }

    private record Binding(
            ActivityManager.Configuration snapshot,
            ActivityConfigItem config,
            PlayerActivityData data) {}

    private static Binding current(Player player, int id) {
        var snapshot = ActivityManager.configurationSnapshot();
        var owner = snapshot.currencyActivities().get(id);
        var config = owner == null ? null : snapshot.activities().get(owner);
        var manager = player == null ? null : player.getActivityManager();
        var data =
                manager == null || owner == null
                        ? null
                        : manager.getPlayerActivityDataMap().get(owner);
        if (config == null
                || data == null
                || !config.isActiveAt(System.currentTimeMillis())
                || data.getActivityId() != owner
                || data.getUid() != player.getUid()
                || data.getPlayer() != player
                || data.getScheduleId() != config.getScheduleId()) return null;
        return new Binding(snapshot, config, data);
    }

    private static int stored(PlayerActivityData data, int id) {
        var values = data.getActivityCoins();
        if (values == null || !values.containsKey(id)) return 0;
        var value = values.get(id);
        return value == null ? -1 : value;
    }

    public static int balance(Player player, int id) {
        var binding = current(player, id);
        if (binding == null) return 0;
        synchronized (binding.data()) {
            return binding.data().hasUnconfirmedActivityCoins()
                    ? 0
                    : Math.max(0, stored(binding.data(), id));
        }
    }

    /**
     * The caller's inventory monitor also serializes activity refresh with this admission check.
     */
    public static boolean canCredit(Player player, int id, long amount) {
        var binding = current(player, id);
        if (binding == null || amount <= 0) return false;
        synchronized (binding.data()) {
            int before = stored(binding.data(), id);
            return !binding.data().hasUnconfirmedActivityCoins()
                    && before >= 0
                    && amount <= Integer.MAX_VALUE - (long) before;
        }
    }

    public static boolean credit(Player player, int id, int amount) {
        if (player == null) return false;
        synchronized (player.getInventory()) {
            var binding = current(player, id);
            if (binding == null) return false;
            synchronized (binding.data()) {
                if (!canCredit(player, id, amount)) return false;
                binding.data().getCoinTransaction().commit(Map.of(id, amount), true);
            }
            notifyBalance(player, binding);
            return true;
        }
    }

    /** A resource shop pays its activity's currencies in one acknowledged document write. */
    public static boolean pay(Player player, Map<Integer, Integer> costs) {
        if (costs.isEmpty()) return true;
        if (player == null) return false;
        synchronized (player.getInventory()) {
            Binding binding = null;
            for (var cost : costs.entrySet()) {
                var candidate = current(player, cost.getKey());
                if (candidate == null
                        || cost.getValue() <= 0
                        || binding != null && binding.data() != candidate.data()) return false;
                binding = candidate;
            }
            synchronized (binding.data()) {
                if (binding.data().hasUnconfirmedActivityCoins()) return false;
                for (var cost : costs.entrySet()) {
                    int before = stored(binding.data(), cost.getKey());
                    if (before < cost.getValue()) return false;
                }
                binding.data().getCoinTransaction().commit(costs, false);
            }
            notifyBalance(player, binding);
            return true;
        }
    }

    static void notifyRecovered(PlayerActivityData data) {
        var player = data.getPlayer();
        var snapshot = ActivityManager.configurationSnapshot();
        var config = snapshot.activities().get(data.getActivityId());
        if (player == null
                || config == null
                || !config.isActiveAt(System.currentTimeMillis())
                || data.getScheduleId() != config.getScheduleId()
                || player.getActivityManager() == null
                || player.getActivityManager().getPlayerActivityDataMap().get(data.getActivityId())
                        != data) return;
        notifyBalance(player, new Binding(snapshot, config, data));
    }

    private static void notifyBalance(Player player, Binding binding) {
        if (ActivityManager.configurationSnapshot() != binding.snapshot()) return;
        try {
            player.sendPacket(
                    new PacketActivityInfoNotify(
                            player.getActivityManager()
                                    .getInfoProtoByActivityId(binding.config().getActivityId())));
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .warn(
                            "Cannot notify activity currency for UID {}, activity {}",
                            player.getUid(),
                            binding.config().getActivityId(),
                            failure);
        }
    }

    static void appendInfo(
            ActivityConfigItem config, PlayerActivityData data, ActivityInfo.Builder info) {
        if (data == null
                || data.getActivityId() != config.getActivityId()
                || data.getScheduleId() != config.getScheduleId()) return;
        synchronized (data) {
            if (data.hasUnconfirmedActivityCoins()) return;
            new TreeMap<>(ActivityManager.configurationSnapshot().currencyActivities())
                    .forEach(
                            (id, owner) -> {
                                if (owner == config.getActivityId())
                                    info.putActivityCoinMap(id, Math.max(0, stored(data, id)));
                            });
        }
    }
}
