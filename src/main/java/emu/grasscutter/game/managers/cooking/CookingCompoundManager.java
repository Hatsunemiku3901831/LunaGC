package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CompoundQueueDataOuterClass.CompoundQueueData;
import emu.grasscutter.net.proto.GetCompoundDataReqOuterClass.GetCompoundDataReq;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
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
            var data =
                    CompoundQueueData.newBuilder()
                            .setCompoundId(item.getCompoundId())
                            .setWaitCount(item.getWaitCount(currentTime))
                            .build();
            compoundQueueData.add(data);
        }
        return compoundQueueData;
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
                                    || queue.getClaimState() != 0)) {
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
            player.sendPacket(
                    new PacketPlayerCompoundMaterialRsp(
                            CompoundQueueData.newBuilder()
                                    .setCompoundId(id)
                                    .setWaitCount(next.getWaitCount(now))
                                    .build()));
        }
    }

    public synchronized void handleTakeCompoundOutputReq(TakeCompoundOutputReq req) {
        synchronized (player.getInventory()) {
            int group = req.getCompoundGroupId(),
                    recipe = req.getCompoundId(),
                    now = Utils.getCurrentSeconds();
            boolean all = req.getIsClaimAll();
            if (!all && group <= 0 && recipe <= 0) {
                failTake(Retcode.RET_FAIL_VALUE);
                return;
            }
            var active = player.getActiveCookCompounds();
            var selected = new LinkedHashMap<Integer, ActiveCookCompoundData>();
            var amounts = new LinkedHashMap<Integer, Integer>();
            var rewards = new ArrayList<ItemParamData>();
            for (var entry :
                    active.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
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
                    failTake(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                    return;
                }
                if (queue.getClaimState() != 0) {
                    failTake(Retcode.RET_SVR_ERROR_VALUE);
                    return;
                }
                int count = queue.getOutputCount(now);
                if (count <= 0) continue;
                var items = scale(definition.getOutputVec(), count);
                if (items == null || items.isEmpty()) {
                    failTake(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                    return;
                }
                selected.put(id, queue);
                amounts.put(id, count);
                rewards.addAll(items);
            }
            if (selected.isEmpty()) {
                failTake(Retcode.RET_COMPOUND_NOT_FINISH_VALUE);
                return;
            }
            var combined = scale(rewards, 1);
            if (combined == null) {
                failTake(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
                return;
            }
            int validation = InvestigationRewardDelivery.validate(player, combined);
            if (validation != 0) {
                failTake(validation);
                return;
            }
            try {
                selected.forEach((id, queue) -> queue.reserveClaim(amounts.get(id)));
                DatabaseHelper.saveGameSync(player);
                InvestigationRewardDelivery.grant(player, combined, ActionReason.Compound);
                selected.values().forEach(ActiveCookCompoundData::finishClaim);
                DatabaseHelper.saveGameSync(player);
                selected.forEach(
                        (id, queue) -> {
                            if (queue.getTotalCount() == 0) active.remove(id, queue);
                            else queue.acknowledgeClaim();
                        });
                DatabaseHelper.saveGameSync(player);
                player.sendPacket(
                        new PackageTakeCompoundOutputRsp(
                                combined.stream()
                                        .map(
                                                item ->
                                                        ItemParam.newBuilder()
                                                                .setItemId(item.getId())
                                                                .setCount(item.getCount())
                                                                .build())
                                        .toList(),
                                0));
                player.sendPacket(new PacketCompoundDataNotify(unlocked, getCompoundQueueData()));
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "Compound output needs reconciliation: UID {}",
                                player.getUid(),
                                failure);
                failTake(Retcode.RET_SVR_ERROR_VALUE);
            }
        }
    }

    private void failTake(int code) {
        player.sendPacket(new PackageTakeCompoundOutputRsp(List.of(), code));
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
