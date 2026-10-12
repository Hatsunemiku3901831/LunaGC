package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CompoundBoostTakeStatusTypeOuterClass.CompoundBoostTakeStatusType;
import emu.grasscutter.net.proto.CompoundQueueDataOuterClass.CompoundQueueData;
import emu.grasscutter.net.proto.GetCompoundDataReqOuterClass.GetCompoundDataReq;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PlayerCompoundMaterialBoostReqOuterClass.PlayerCompoundMaterialBoostReq;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import java.util.*;

public class CookingCompoundManager extends BasePlayerManager {
    private static Set<Integer> defaultUnlockedCompounds;
    private static Map<Integer, Set<Integer>> compoundGroups;

    // Per-player. This used to be static, so every account shared one unlock set and
    // any unlock by one player leaked to all of them (and it was never persisted, so a
    // restart reset everyone to defaults at once instead of per account).
    private final Set<Integer> unlocked;

    public CookingCompoundManager(Player player) {
        super(player);
        ensureCompoundTables();
        var defaults =
                defaultUnlockedCompounds != null ? defaultUnlockedCompounds : Set.<Integer>of();
        // (An unloaded resource set yields empty tables; ensureCompoundTables() retries above.)
        this.unlocked = new HashSet<>(defaults);
        // TODO:Because we haven't implemented fishing feature,unlock all compounds related to
        // fish.
        if (compoundGroups != null
                && compoundGroups.containsKey(3)) // Avoid NPE from Resources error
        this.unlocked.addAll(compoundGroups.get(3));
    }

    public static void initialize() {
        // The GameServer constructor calls this before ResourceLoader.loadAll() runs, so the
        // excel map is usually still empty here -- the tables are rebuilt on demand instead.
        loadCompoundTables();
    }

    /** Compound tables, rebuilt on demand while the excel data has not been loaded yet. */
    private static synchronized void loadCompoundTables() {
        var map = GameData.getCompoundDataMap();
        if (map == null || map.isEmpty()) {
            // Nothing loaded yet; hand back empty tables rather than null (the constructor
            // copies them for every login) and let ensureCompoundTables() retry on the next call.
            defaultUnlockedCompounds = new HashSet<>();
            compoundGroups = new HashMap<>();
            return;
        }
        defaultUnlockedCompounds = new HashSet<>();
        compoundGroups = new HashMap<>();
        map.forEach(
                (id, compound) -> {
                    if (compound.isDefaultUnlocked()) {
                        defaultUnlockedCompounds.add(id);
                    }
                    compoundGroups
                            .computeIfAbsent(compound.getGroupId(), gid -> new HashSet<>())
                            .add(id);
                });
    }

    private static synchronized void ensureCompoundTables() {
        if (compoundGroups == null || compoundGroups.isEmpty()) {
            loadCompoundTables();
        }
    }

    private synchronized List<CompoundQueueData> getCompoundQueueData() {
        List<CompoundQueueData> compoundQueueData =
                new ArrayList<>(player.getActiveCookCompounds().size());
        int currentTime = Utils.getCurrentSeconds();
        for (var item : player.getActiveCookCompounds().values()) {
            compoundQueueData.add(queueData(item, currentTime));
        }
        return compoundQueueData;
    }

    private static CompoundQueueData queueData(ActiveCookCompoundData queue, int currentTime) {
        return CompoundQueueData.newBuilder()
                .setCompoundId(queue.getCompoundId())
                .setOutputCount(queue.getOutputCount(currentTime))
                .setWaitCount(queue.getWaitCount(currentTime))
                .setOutputTime(queue.getOutputTime(currentTime))
                .build();
    }

    public synchronized void handleGetCompoundDataReq(GetCompoundDataReq req) {
        player.sendPacket(new PacketGetCompoundDataRsp(unlocked, getCompoundQueueData()));
    }

    public synchronized void handlePlayerCompoundMaterialReq(PlayerCompoundMaterialReq req) {
        synchronized (player.getInventory()) {
            int id = req.getCompoundId(), count = req.getCount();
            var compound = GameData.getCompoundDataMap().get(id);
            var active = player.getActiveCookCompounds();
            var queue = active.get(id);
            if (compound == null
                    || compound.getId() != id
                    || count <= 0
                    || !unlocked.contains(id)
                    || compound.getCostTime() < 0
                    || compound.getQueueSize() <= 0
                    || queue != null
                            && (queue.getCompoundId() != id
                                    || queue.getTotalCount() < 0
                                    || queue.isBusy())) {
                player.sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_FAIL_VALUE));
                return;
            }
            if ((long) (queue == null ? 0 : queue.getTotalCount()) + count
                    > compound.getQueueSize()) {
                player.sendPacket(
                        new PacketPlayerCompoundMaterialRsp(Retcode.RET_COMPOUND_QUEUE_FULL_VALUE));
                return;
            }
            var costs = scale(compound.getInputVec(), count);
            if (costs == null) {
                player.sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_FAIL_VALUE));
                return;
            }
            int now = Utils.getCurrentSeconds();
            ActiveCookCompoundData next;
            try {
                next =
                        queue == null
                                ? new ActiveCookCompoundData(id, compound.getCostTime(), count, now)
                                : queue.copyWithAppend(count, compound.getCostTime(), now);
            } catch (ArithmeticException | IllegalArgumentException invalidQueue) {
                player.sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_FAIL_VALUE));
                return;
            }
            if (!player.getInventory().payItems(costs, 1, ActionReason.Compound)) {
                player.sendPacket(
                        new PacketPlayerCompoundMaterialRsp(
                                Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE));
                return;
            }
            active.put(id, next);
            player.save();
            player.sendPacket(new PacketPlayerCompoundMaterialRsp(queueData(next, now)));
        }
    }

    public synchronized void handleTakeCompoundOutputReq(TakeCompoundOutputReq req) {
        synchronized (player.getInventory()) {
            var result =
                    claimOutput(req.getCompoundGroupId(), req.getCompoundId(), req.getIsClaimAll());
            player.sendPacket(
                    new PackageTakeCompoundOutputRsp(itemParams(result.items()), result.retcode()));
            if (result.retcode() == 0)
                player.sendPacket(new PacketCompoundDataNotify(unlocked, getCompoundQueueData()));
        }
    }

    private record ClaimResult(int retcode, List<ItemParamData> items) {
        static ClaimResult failure(int code) {
            return new ClaimResult(code, List.of());
        }
    }

    /** Caller holds the manager and inventory monitors, including during automatic collection. */
    private ClaimResult claimOutput(int group, int recipe, boolean all) {
        int now = Utils.getCurrentSeconds();
        if (!all && group <= 0 && recipe <= 0) {
            return ClaimResult.failure(Retcode.RET_FAIL_VALUE);
        }
        var active = player.getActiveCookCompounds();
        var selected = new LinkedHashMap<Integer, ActiveCookCompoundData>();
        var amounts = new LinkedHashMap<Integer, Integer>();
        var rewards = new ArrayList<ItemParamData>();
        for (var entry : active.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            int id = entry.getKey();
            var queue = entry.getValue();
            var definition = GameData.getCompoundDataMap().get(id);
            if (!all && recipe > 0 && id != recipe) continue;
            if (!all && recipe == 0 && (definition == null || definition.getGroupId() != group))
                continue;
            if (definition == null
                    || definition.getId() != id
                    || queue == null
                    || queue.getCompoundId() != id
                    || queue.getTotalCount() < 0
                    || !all && recipe > 0 && group > 0 && definition.getGroupId() != group) {
                return ClaimResult.failure(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
            }
            if (queue.isBusy()) {
                return ClaimResult.failure(Retcode.RET_SVR_ERROR_VALUE);
            }
            int count = queue.getOutputCount(now);
            if (count <= 0) continue;
            var items = scale(definition.getOutputVec(), count);
            if (items == null || items.isEmpty()) {
                return ClaimResult.failure(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
            }
            selected.put(id, queue);
            amounts.put(id, count);
            rewards.addAll(items);
        }
        if (selected.isEmpty()) {
            return ClaimResult.failure(Retcode.RET_COMPOUND_NOT_FINISH_VALUE);
        }
        var combined = scale(rewards, 1);
        if (combined == null) {
            return ClaimResult.failure(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
        }
        int validation = InvestigationRewardDelivery.validate(player, combined);
        if (validation != 0) {
            return ClaimResult.failure(validation);
        }
        try {
            selected.forEach((id, queue) -> queue.reserveClaim(amounts.get(id)));
            DatabaseHelper.saveGameSync(player);
            InvestigationRewardDelivery.grant(player, combined, ActionReason.Compound);
            selected.values().forEach(ActiveCookCompoundData::finishClaim);
            amounts.forEach(
                    (id, count) ->
                            InvestigationHandbook.trigger(
                                    player, "TRIGGER_DO_COMPOUND", id, count));
            DatabaseHelper.saveGameSync(player);
            selected.forEach(
                    (id, queue) -> {
                        if (queue.getTotalCount() == 0) active.remove(id, queue);
                        else queue.acknowledgeClaim();
                    });
            DatabaseHelper.saveGameSync(player);
            return new ClaimResult(0, combined);
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .error(
                            "Compound output needs reconciliation: UID {}",
                            player.getUid(),
                            failure);
            return ClaimResult.failure(Retcode.RET_SVR_ERROR_VALUE);
        }
    }

    private static List<ItemParam> itemParams(List<ItemParamData> items) {
        return items.stream()
                .map(
                        item ->
                                ItemParam.newBuilder()
                                        .setItemId(item.getId())
                                        .setCount(item.getCount())
                                        .build())
                .toList();
    }

    public synchronized void handleCompoundBoost(PlayerCompoundMaterialBoostReq req) {
        synchronized (player.getInventory()) {
            ensureCompoundTables();
            int group = req.getTargetCompoundGroupId(),
                    ore = req.getConsumeMaterialId(),
                    count = req.getConsumeMaterialCount(),
                    now = Utils.getCurrentSeconds();
            var inventory = player.getInventory();
            var rate = GameData.getCompoundBoostDataMap().get(ore);
            var oreData = GameData.getItemDataMap().get(ore);
            if (group <= 0
                    || !compoundGroups.containsKey(group)
                    || count <= 0
                    || inventory.getItemCountById(220060) < 1
                    || rate == null
                    || rate.getId() != ore
                    || rate.getBoostTime() <= 0
                    || oreData == null
                    || oreData.getId() != ore
                    || oreData.getItemType() != ItemType.ITEM_MATERIAL
                    || oreData.getStackLimit() <= 0
                    || oreData.isUseOnGain()) {
                failBoost(Retcode.RET_FAIL_VALUE);
                return;
            }
            var active = player.getActiveCookCompounds();
            var selected = new LinkedHashMap<Integer, ActiveCookCompoundData>();
            var shifted = new LinkedHashMap<Integer, ActiveCookCompoundData>();
            var rewards = new ArrayList<ItemParamData>();
            long longest = 0, total = 0, budget = (long) count * rate.getBoostTime();
            try {
                for (int id : compoundGroups.get(group).stream().sorted().toList()) {
                    var queue = active.get(id);
                    if (queue == null) continue;
                    var definition = GameData.getCompoundDataMap().get(id);
                    if (definition == null
                            || definition.getId() != id
                            || definition.getGroupId() != group
                            || !unlocked.contains(id)
                            || definition.getQueueSize() <= 0
                            || definition.getCostTime() < 0
                            || queue.getCompoundId() != id
                            || queue.getTotalCount() <= 0
                            || queue.getTotalCount() > definition.getQueueSize()
                            || queue.isBusy()) {
                        failBoost(Retcode.RET_FAIL_VALUE);
                        return;
                    }
                    long remaining = queue.getRemainingSeconds(now);
                    longest = Math.max(longest, remaining);
                    total = Math.addExact(total, remaining);
                    selected.put(id, queue);
                    var items = scale(definition.getOutputVec(), queue.getTotalCount());
                    if (items == null || items.isEmpty()) {
                        failBoost(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                        return;
                    }
                    rewards.addAll(items);
                }
                if (longest == 0 || req.getIsBoostAll() && budget < longest) {
                    failBoost(Retcode.RET_COMPOUND_NOT_FINISH_VALUE);
                    return;
                }
                // Client partial previews share a budget; full mode checks the latest finish.
                // The multi-recipe policy still needs an in-game comparison.
                long remainingBudget = budget;
                for (var entry : selected.entrySet()) {
                    var queue = entry.getValue();
                    long seconds =
                            req.getIsBoostAll()
                                    ? queue.getRemainingSeconds(now)
                                    : Math.min(remainingBudget, queue.getRemainingSeconds(now));
                    shifted.put(
                            entry.getKey(),
                            seconds == 0 ? queue : queue.copyWithBoost(seconds, now));
                    if (!req.getIsBoostAll()) remainingBudget -= seconds;
                }
            } catch (ArithmeticException | IllegalArgumentException invalidQueue) {
                failBoost(Retcode.RET_FAIL_VALUE);
                return;
            }
            var combined = scale(rewards, 1);
            if (combined == null) {
                failBoost(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
                return;
            }
            int validation = InvestigationRewardDelivery.validate(player, combined);
            if (validation != 0 && validation != Retcode.RET_ITEM_EXCEED_LIMIT_VALUE) {
                failBoost(validation);
                return;
            }
            long seconds = req.getIsBoostAll() ? longest : Math.min(budget, total);
            int paid = Math.toIntExact((seconds + rate.getBoostTime() - 1) / rate.getBoostTime());
            int before = inventory.getItemCountById(ore);
            var oreItem = inventory.getItemById(ore);
            if (before < paid || oreItem == null) {
                failBoost(Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE);
                return;
            }
            try {
                selected.values().forEach(ActiveCookCompoundData::reserveBoost);
                DatabaseHelper.saveGameSync(player);
                if (!inventory.payItems(
                                List.of(new ItemParamData(ore, paid)), 1, ActionReason.Compound)
                        || (long) before - inventory.getItemCountById(ore) != paid)
                    throw new IllegalStateException("Compound boost minerals were not confirmed");
                DatabaseHelper.saveGameSync(oreItem);
                shifted.forEach(
                        (id, queue) -> {
                            if (queue != selected.get(id)) queue.reserveBoost();
                            active.put(id, queue);
                        });
                DatabaseHelper.saveGameSync(player);
                shifted.values().forEach(ActiveCookCompoundData::acknowledgeBoost);
                DatabaseHelper.saveGameSync(player);
            } catch (RuntimeException failure) {
                // A failed cleanup acknowledgement remains blocked in memory as well as on disk.
                selected.keySet()
                        .forEach(
                                id -> {
                                    var queue = active.get(id);
                                    if (queue != null && !queue.isBusy()) queue.reserveBoost();
                                });
                Grasscutter.getLogger()
                        .error(
                                "Compound boost needs reconciliation: UID {}, group {}, ore {},"
                                        + " count {}",
                                player.getUid(),
                                group,
                                ore,
                                paid,
                                failure);
                failBoost(Retcode.RET_SVR_ERROR_VALUE);
                return;
            }
            int
                    status =
                            CompoundBoostTakeStatusType
                                    .COMPOUND_BOOST_TAKE_STATUS_TYPE_BOOST_ONLY_VALUE,
                    retcode = 0;
            List<ItemParamData> taken = List.of();
            if (req.getIsBoostAll()) {
                var result = claimOutput(group, 0, false);
                if (result.retcode() == Retcode.RET_ITEM_EXCEED_LIMIT_VALUE)
                    status =
                            CompoundBoostTakeStatusType
                                    .COMPOUND_BOOST_TAKE_STATUS_TYPE_BAG_FULL_VALUE;
                else {
                    retcode = result.retcode();
                    status =
                            retcode == 0
                                    ? CompoundBoostTakeStatusType
                                            .COMPOUND_BOOST_TAKE_STATUS_TYPE_BOOST_AND_TAKE_VALUE
                                    : CompoundBoostTakeStatusType
                                            .COMPOUND_BOOST_TAKE_STATUS_TYPE_NONE_VALUE;
                    taken = result.items();
                }
            }
            var queues =
                    selected.keySet().stream()
                            .map(
                                    id ->
                                            active.containsKey(id)
                                                    ? queueData(active.get(id), now)
                                                    : CompoundQueueData.newBuilder()
                                                            .setCompoundId(id)
                                                            .build())
                            .toList();
            player.sendPacket(
                    new PacketPlayerCompoundMaterialBoostRsp(
                            retcode, status, queues, itemParams(taken)));
            player.sendPacket(new PacketCompoundDataNotify(unlocked, getCompoundQueueData()));
        }
    }

    private void failBoost(int code) {
        player.sendPacket(new PacketPlayerCompoundMaterialBoostRsp(code, 0, List.of(), List.of()));
    }

    private static List<ItemParamData> scale(List<ItemParamData> rows, int count) {
        if (rows == null || count <= 0) return null;
        var totals = new LinkedHashMap<Integer, Long>();
        for (var item : rows) {
            if (item == null || item.getCount() < 0 || item.getId() < 0) return null;
            if (item.getCount() == 0) continue;
            if (item.getId() == 0) return null;
            long amount = (long) item.getCount() * count,
                    total = totals.merge(item.getId(), amount, Long::sum);
            if (amount > Integer.MAX_VALUE || total > Integer.MAX_VALUE) return null;
        }
        return totals.entrySet().stream()
                .map(e -> new ItemParamData(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    public void onPlayerLogin() {
        player.sendPacket(new PacketCompoundDataNotify(unlocked, getCompoundQueueData()));
    }
}
