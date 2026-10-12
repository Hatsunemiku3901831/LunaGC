package emu.grasscutter.game.managers.forging;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ForgeQueueDataOuterClass.ForgeQueueData;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateTypeOuterClass.ForgeQueueManipulateType;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerForgeItemEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import java.util.*;

public final class ForgingManager extends BasePlayerManager {

    public ForgingManager(Player player) {
        super(player);
    }

    /**********
     * Blueprint unlocking.
     **********/
    public boolean unlockForgingBlueprint(int id) {
        // Tell the client that this blueprint is now unlocked and add the unlocked item to the
        // player.
        if (!this.player.getUnlockedForgingBlueprints().add(id)) {
            return false; // Already unlocked
        }
        this.player.sendPacket(new PacketForgeFormulaDataNotify(id));
        return true;
    }

    /**********
     * Communicate forging information to the client.
     **********/
    private synchronized int determineNumberOfQueues() {
        int adventureRank = player.getLevel();
        return (adventureRank >= 15) ? 4 : (adventureRank >= 10) ? 3 : (adventureRank >= 5) ? 2 : 1;
    }

    private synchronized Map<Integer, ForgeQueueData> determineCurrentForgeQueueData() {
        Map<Integer, ForgeQueueData> res = new HashMap<>();
        int currentTime = Utils.getCurrentSeconds();

        // Create queue information for all active forges.
        for (int i = 0; i < this.player.getActiveForges().size(); i++) {
            ActiveForgeData activeForge = this.player.getActiveForges().get(i);

            ForgeQueueData data =
                    ForgeQueueData.newBuilder()
                            .setQueueId(i + 1)
                            .setForgeId(activeForge.getForgeId())
                            .setFinishCount(activeForge.getFinishedCount(currentTime))
                            .setUnfinishCount(activeForge.getUnfinishedCount(currentTime))
                            .setTotalFinishTimestamp(activeForge.getTotalFinishTimestamp())
                            .setNextFinishTimestamp(activeForge.getNextFinishTimestamp(currentTime))
                            .setAvatarId(activeForge.getAvatarId())
                            .build();

            res.put(i + 1, data);
        }

        return res;
    }

    /**
     * The client clears its forge-recipe set and copies this list over it. Recipes marked
     * default-show in the excel are the ones a blacksmith offers before any diagram is used.
     */
    static List<Integer> visibleForgeIds(
            Collection<Integer> unlocked, Iterable<ForgeData> recipes, int adventureRank) {
        var ids = new LinkedHashSet<Integer>();
        if (unlocked != null) {
            ids.addAll(unlocked);
        }
        if (recipes != null) {
            for (ForgeData recipe : recipes) {
                if (recipe.isDefaultShow() && recipe.getPlayerLevel() <= adventureRank) {
                    ids.add(recipe.getId());
                }
            }
        }
        ids.remove(0);
        return List.copyOf(ids);
    }

    public synchronized void sendForgeDataNotify() {
        // Determine the number of queues and unlocked items.
        int numQueues = this.determineNumberOfQueues();
        var unlockedItems =
                visibleForgeIds(
                        this.player.getUnlockedForgingBlueprints(),
                        GameData.getForgeDataMap().values(),
                        this.player.getLevel());
        var queueData = this.determineCurrentForgeQueueData();

        // Send notification.
        this.player.sendPacket(new PacketForgeDataNotify(unlockedItems, numQueues, queueData));
    }

    public synchronized void handleForgeGetQueueDataReq() {
        // Determine the number of queues.
        int numQueues = this.determineNumberOfQueues();
        var queueData = this.determineCurrentForgeQueueData();

        // Reply.
        this.player.sendPacket(
                new PacketForgeGetQueueDataRsp(Retcode.RET_SUCC, numQueues, queueData));
    }

    /**********
     * Initiate forging process.
     **********/
    private synchronized void sendForgeQueueDataNotify() {
        var queueData = this.determineCurrentForgeQueueData();
        this.player.sendPacket(new PacketForgeQueueDataNotify(queueData, List.of()));
    }

    private synchronized void sendForgeQueueDataNotify(boolean hasRemoved) {
        var queueData = this.determineCurrentForgeQueueData();

        if (hasRemoved) {
            this.player.sendPacket(new PacketForgeQueueDataNotify(Map.of(), List.of(1, 2, 3, 4)));
        }

        this.player.sendPacket(new PacketForgeQueueDataNotify(queueData, List.of()));
    }

    public synchronized void handleForgeStartReq(ForgeStartReq req) {
        synchronized (player.getInventory()) {
            var active = player.getActiveForges();
            if (active.stream().anyMatch(queue -> queue == null || queue.isBusy())) {
                failStart(Retcode.RET_SVR_ERROR);
                return;
            }
            if (active.size() >= determineNumberOfQueues()) {
                failStart(Retcode.RET_FORGE_QUEUE_FULL);
                return;
            }
            int id = req.getForgeId(), count = req.getForgeCount(), avatar = req.getAvatarId();
            var definition = GameData.getForgeDataMap().get(id);
            if (definition == null
                    || definition.getId() != id
                    || count <= 0
                    || definition.getQueueNum() <= 0
                    || count > definition.getQueueNum()
                    || definition.getPlayerLevel() > player.getLevel()
                    || definition.getEffectiveWorldLevels() != null
                            && !definition.getEffectiveWorldLevels().isEmpty()
                            && !definition
                                    .getEffectiveWorldLevels()
                                    .contains(player.getWorldLevel())
                    || !definition.isDefaultShow()
                            && !player.getUnlockedForgingBlueprints().contains(id)
                    || definition.getForgeTime() <= 0
                    || definition.getForgePoint() < 0
                    || definition.getScoinCost() < 0
                    || definition.getResultItemCount() <= 0
                    || avatar < 0
                    || avatar > 0 && !player.getAvatars().hasAvatar(avatar)) {
                failStart(Retcode.RET_FAIL);
                return;
            }
            int resultId =
                    definition.getResultItemId() > 0
                            ? definition.getResultItemId()
                            : definition.getShowItemId();
            var result = GameData.getItemDataMap().get(resultId);
            int now = Utils.getCurrentSeconds();
            long points = (long) definition.getForgePoint() * count,
                    finish = (long) now + (long) definition.getForgeTime() * count;
            if (result == null
                    || result.getId() != resultId
                    || result.isUseOnGain()
                    || result.getItemType() != ItemType.ITEM_MATERIAL
                            && result.getItemType() != ItemType.ITEM_WEAPON
                    || (long) definition.getResultItemCount() * count > Integer.MAX_VALUE
                    || points > Integer.MAX_VALUE
                    || finish > Integer.MAX_VALUE) {
                failStart(Retcode.RET_NOT_FOUND_CONFIG);
                return;
            }
            List<ItemParamData> products;
            try {
                products = ForgeProducts.resolve(definition);
                for (var product : products) Math.multiplyExact(product.getCount(), count);
            } catch (RuntimeException invalid) {
                failStart(Retcode.RET_NOT_FOUND_CONFIG);
                return;
            }
            var costs = forgeCosts(definition, count);
            if (costs == null) {
                failStart(Retcode.RET_NOT_FOUND_CONFIG);
                return;
            }
            if (!emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage)
                costs = costs.stream().filter(cost -> cost.getId() != 106).toList();
            if (points > player.getForgePoints()) {
                failStart(Retcode.RET_FORGE_POINT_NOT_ENOUGH);
                return;
            }
            var before = new LinkedHashMap<Integer, Integer>();
            var stacks = new ArrayList<GameItem>();
            for (var cost : costs) {
                var item = GameData.getItemDataMap().get(cost.getId());
                if (item == null
                        || item.getId() != cost.getId()
                        || item.isUseOnGain()
                        || item.getItemType() != ItemType.ITEM_MATERIAL
                                && !(item.getItemType() == ItemType.ITEM_VIRTUAL
                                        && (cost.getId() == 202 || cost.getId() == 106))
                        || item.getItemType() == ItemType.ITEM_MATERIAL
                                && item.getStackLimit() <= 0) {
                    failStart(Retcode.RET_NOT_FOUND_CONFIG);
                    return;
                }
                int amount = forgeCostCount(cost.getId());
                if (amount < cost.getCount()) {
                    failStart(Retcode.RET_ITEM_COUNT_NOT_ENOUGH);
                    return;
                }
                before.put(cost.getId(), amount);
                if (item.getItemType() == ItemType.ITEM_MATERIAL)
                    stacks.add(player.getInventory().getItemById(cost.getId()));
            }
            var queue = new ActiveForgeData();
            queue.setForgeId(id);
            queue.setAvatarId(avatar);
            queue.setCount(count);
            queue.setStartTime(now);
            queue.setForgeTime(definition.getForgeTime());
            queue.capturePayment(costs, count, definition.getForgePoint());
            queue.captureBonus(ForgeBonuses.oreReturns(player, avatar, definition, costs, count));
            queue.captureProducts(products);
            queue.reserveStart();
            active.add(queue);
            int pointsBefore = player.getForgePoints();
            try {
                DatabaseHelper.saveGameSync(player);
                if (!player.getInventory().payItems(costs, 1, ActionReason.ForgeCost))
                    throw new IllegalStateException("Forge costs were refused after reservation");
                for (var cost : costs)
                    if ((long) before.get(cost.getId()) - forgeCostCount(cost.getId())
                            != cost.getCount())
                        throw new IllegalStateException("Forge cost count changed");
                for (var item : stacks) DatabaseHelper.saveGameSync(item);
                if (!player.setForgePoints(pointsBefore - (int) points)
                        || player.getForgePoints() != pointsBefore - points)
                    throw new IllegalStateException("Forge point cost was not applied");
                DatabaseHelper.saveGameSync(player);
                queue.acknowledgeStart();
                DatabaseHelper.saveGameSync(player);
            } catch (RuntimeException failure) {
                if (queue.getStartState() == 0) queue.reserveStart();
                Grasscutter.getLogger()
                        .error(
                                "Forge start needs reconciliation: UID {}, recipe {}, count {}",
                                player.getUid(),
                                id,
                                count,
                                failure);
                failStart(Retcode.RET_SVR_ERROR);
                return;
            }
            sendForgeQueueDataNotify();
            player.sendPacket(new PacketForgeStartRsp(Retcode.RET_SUCC));
        }
    }

    private void failStart(Retcode code) {
        player.sendPacket(new PacketForgeStartRsp(code));
    }

    private int forgeCostCount(int id) {
        return switch (id) {
            case 202 -> player.getMora();
            case 106 -> player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
            default -> player.getInventory().getItemCountById(id);
        };
    }

    private static List<ItemParamData> forgeCosts(ForgeData recipe, int count) {
        if (recipe.getMaterialItems() == null) return null;
        var totals = new LinkedHashMap<Integer, Long>();
        var rows = new ArrayList<>(recipe.getMaterialItems());
        rows.add(new ItemParamData(202, recipe.getScoinCost()));
        for (var row : rows) {
            if (row == null || row.getId() < 0 || row.getCount() < 0) return null;
            if (row.getCount() == 0) continue;
            if (row.getId() == 0) return null;
            long total = totals.merge(row.getId(), (long) row.getCount() * count, Long::sum);
            if (total > Integer.MAX_VALUE) return null;
        }
        return totals.entrySet().stream()
                .map(entry -> new ItemParamData(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    /**********
     * Forge queue manipulation (obtaining results and cancelling forges).
     **********/
    private synchronized void obtainItems(int queueId) {
        int type = ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT_VALUE;
        var active = player.getActiveForges();
        var forge = active.get(queueId - 1);
        var definition = GameData.getForgeDataMap().get(forge.getForgeId());
        if (definition == null
                || definition.getId() != forge.getForgeId()
                || definition.getResultItemCount() <= 0
                || definition.getQueueNum() <= 0
                || forge.getCount() <= 0
                || forge.getCount() > definition.getQueueNum()
                || forge.getForgeTime() <= 0
                || forge.getTotalFinishTimestamp() <= 0) {
            failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
            return;
        }
        int finished = forge.getFinishedCount(Utils.getCurrentSeconds());
        if (finished <= 0) {
            failManipulate(type, Retcode.RET_FORGE_NO_FINISH_CAN_TAKE);
            return;
        }
        List<ItemParamData> products;
        try {
            if (forge.getProductVersion() == 1) products = forge.getProducts(finished);
            else if (forge.getProductVersion() == 0)
                products =
                        ForgeProducts.resolve(definition).stream()
                                .map(
                                        item ->
                                                new ItemParamData(
                                                        item.getId(),
                                                        Math.multiplyExact(
                                                                item.getCount(), finished)))
                                .toList();
            else throw new IllegalArgumentException("Unknown forge product receipt");
        } catch (RuntimeException invalid) {
            failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
            return;
        }
        boolean confirmationStarted = false;
        try {
            // Block reentry before the plugin receives mutable output objects.
            forge.reserveOutput(finished);
            var outputs = new ArrayList<GameItem>();
            var experience = new ArrayList<ItemParamData>();
            for (var product : products) {
                var result = GameData.getItemDataMap().get(product.getId());
                if (result == null || result.getId() != product.getId() || result.isUseOnGain()) {
                    forge.releaseOutputReservation();
                    failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
                    return;
                }
                if (result.getItemType() == ItemType.ITEM_VIRTUAL) {
                    experience.add(product);
                    continue;
                }
                boolean weapon = result.getItemType() == ItemType.ITEM_WEAPON;
                var weaponTab = player.getInventory().getInventoryTab(ItemType.ITEM_WEAPON);
                if (!weapon && result.getItemType() != ItemType.ITEM_MATERIAL) {
                    forge.releaseOutputReservation();
                    failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
                    return;
                }
                if (weapon && (weaponTab == null || product.getCount() > weaponTab.getMaxCapacity())
                        || !weapon
                                && (result.getStackLimit() <= 0
                                        || product.getCount() > result.getStackLimit())) {
                    forge.releaseOutputReservation();
                    failManipulate(type, Retcode.RET_ITEM_EXCEED_LIMIT);
                    return;
                }
                for (int i = 0, entries = weapon ? product.getCount() : 1; i < entries; i++) {
                    var event =
                            new PlayerForgeItemEvent(
                                    player, new GameItem(result, weapon ? 1 : product.getCount()));
                    if (!event.call()) {
                        forge.releaseOutputReservation();
                        failManipulate(type, Retcode.RET_FAIL);
                        return;
                    }
                    outputs.add(event.getItemForged());
                }
            }
            var delivery = new ArrayList<>(outputs);
            for (var bonus : forge.getBonusReturns(finished)) {
                var data = GameData.getItemDataMap().get(bonus.getId());
                if (data == null
                        || data.getId() != bonus.getId()
                        || data.getItemType() != ItemType.ITEM_MATERIAL
                        || data.isUseOnGain()) {
                    forge.releaseOutputReservation();
                    failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
                    return;
                }
                if (bonus.getCount() > data.getStackLimit()) {
                    forge.releaseOutputReservation();
                    failManipulate(type, Retcode.RET_ITEM_EXCEED_LIMIT);
                    return;
                }
                delivery.add(new GameItem(data, bonus.getCount()));
            }
            int validation = ForgeOutputDelivery.validate(player, delivery);
            if (validation == 0) validation = ForgeExperienceDelivery.validate(player, experience);
            if (validation != 0) {
                forge.releaseOutputReservation();
                failManipulate(type, Retcode.forNumber(validation));
                return;
            }
            confirmationStarted = true;
            DatabaseHelper.saveGameSync(player);
            var hints = ForgeOutputDelivery.grant(player, delivery);
            var primaryHints = new ArrayList<>(hints.subList(0, outputs.size()));
            primaryHints.addAll(ForgeExperienceDelivery.grant(player, experience));
            forge.finishOutput();
            player.getBattlePassManager()
                    .triggerMission(WatcherTriggerType.TRIGGER_DO_FORGE, 0, finished);
            var rarityCounts = new LinkedHashMap<Integer, Integer>();
            for (var item : outputs)
                if (item.getItemData().getItemType() == ItemType.ITEM_WEAPON)
                    rarityCounts.merge(item.getItemData().getRankLevel(), 1, Math::addExact);
            rarityCounts.forEach(
                    (rarity, count) ->
                            player.getBattlePassManager()
                                    .triggerMission(
                                            WatcherTriggerType.TRIGGER_FORGE_WEAPON,
                                            rarity,
                                            count));
            DatabaseHelper.saveGameSync(player);
            boolean removed = forge.getCount() == 0;
            if (removed) active.remove(forge);
            else forge.acknowledgeOutput();
            DatabaseHelper.saveGameSync(player);
            sendForgeQueueDataNotify(removed);
            player.sendPacket(
                    new PacketForgeQueueManipulateRsp(
                            0,
                            type,
                            primaryHints,
                            hints.subList(outputs.size(), hints.size()),
                            List.of()));
        } catch (RuntimeException failure) {
            if (confirmationStarted) {
                forge.restoreOutputPending();
                if (!active.contains(forge))
                    active.add(Math.min(queueId - 1, active.size()), forge);
                Grasscutter.getLogger()
                        .error(
                                "Forge output needs reconciliation: UID {}, recipe {}, count {}",
                                player.getUid(),
                                forge.getForgeId(),
                                finished,
                                failure);
            } else if (forge.getOutputState() == 1) forge.releaseOutputReservation();
            failManipulate(type, Retcode.RET_SVR_ERROR);
        }
    }

    private void failManipulate(int type, Retcode code) {
        player.sendPacket(
                new PacketForgeQueueManipulateRsp(
                        code.getNumber(), type, List.of(), List.of(), List.of()));
    }

    private synchronized void cancelForge(int queueId) {
        int type = ForgeQueueManipulateType.ForgeQueueManipulateType_STOP_FORGE_VALUE;
        var active = player.getActiveForges();
        var forge = active.get(queueId - 1);
        if (forge.getCount() <= 0
                || forge.getForgeTime() <= 0
                || forge.getTotalFinishTimestamp() <= 0) {
            failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
            return;
        }
        if (forge.getFinishedCount(Utils.getCurrentSeconds()) > 0) {
            failManipulate(type, Retcode.RET_FAIL);
            return;
        }
        List<ItemParamData> costs;
        int points;
        try {
            if (forge.getPaymentVersion() == 1) {
                costs = forge.getRefundCosts();
                points = forge.getRefundPoints();
            } else if (forge.getPaymentVersion() == 0) {
                // Legacy queues did not record effective fees. A resin payment cannot be inferred.
                var definition = GameData.getForgeDataMap().get(forge.getForgeId());
                if (definition == null
                        || definition.getId() != forge.getForgeId()
                        || definition.getQueueNum() <= 0
                        || forge.getCount() > definition.getQueueNum()
                        || definition.getForgeTime() != forge.getForgeTime()
                        || definition.getForgePoint() < 0
                        || definition.getScoinCost() < 0)
                    throw new IllegalArgumentException("Invalid legacy forge refund");
                costs = forgeCosts(definition, forge.getCount());
                if (costs == null || costs.stream().anyMatch(cost -> cost.getId() == 106))
                    throw new IllegalArgumentException("Legacy forge payment needs reconciliation");
                points = Math.multiplyExact(definition.getForgePoint(), forge.getCount());
            } else throw new IllegalArgumentException("Unknown forge payment version");
        } catch (RuntimeException invalidReceipt) {
            failManipulate(type, Retcode.RET_NOT_FOUND_CONFIG);
            return;
        }
        int validation = ForgeRefundDelivery.validate(player, costs);
        int beforePoints = player.getForgePoints();
        if (validation != 0 || beforePoints < 0 || beforePoints > 300000) {
            failManipulate(
                    type, validation == 0 ? Retcode.RET_FAIL : Retcode.forNumber(validation));
            return;
        }
        int afterPoints = (int) Math.min(300000L, (long) beforePoints + points);
        try {
            forge.reserveRefund();
            DatabaseHelper.saveGameSync(player);
            var hints = ForgeRefundDelivery.grant(player, costs);
            if (!player.setForgePoints(afterPoints) || player.getForgePoints() != afterPoints)
                throw new IllegalStateException("Forge point refund was not applied");
            forge.finishRefund();
            DatabaseHelper.saveGameSync(player);
            active.remove(forge);
            DatabaseHelper.saveGameSync(player);
            sendForgeQueueDataNotify(true);
            player.sendPacket(
                    new PacketForgeQueueManipulateRsp(0, type, List.of(), hints, List.of()));
        } catch (RuntimeException failure) {
            if (!active.contains(forge)) active.add(Math.min(queueId - 1, active.size()), forge);
            Grasscutter.getLogger()
                    .error(
                            "Forge refund needs reconciliation: UID {}, recipe {}, count {}",
                            player.getUid(),
                            forge.getForgeId(),
                            forge.getCount(),
                            failure);
            failManipulate(type, Retcode.RET_SVR_ERROR);
        }
    }

    public synchronized void handleForgeQueueManipulateReq(ForgeQueueManipulateReq req) {
        synchronized (player.getInventory()) {
            // Get info from the request.
            int queueId = req.getForgeQueueId();
            var manipulateType = req.getManipulateType();
            if (queueId <= 0
                    || queueId > player.getActiveForges().size()
                    || player.getActiveForges().get(queueId - 1) == null
                    || player.getActiveForges().get(queueId - 1).isBusy()) {
                failManipulate(req.getManipulateTypeValue(), Retcode.RET_FAIL);
                return;
            }

            // Handle according to the manipulation type.
            switch (manipulateType) {
                case ForgeQueueManipulateType_RECEIVE_OUTPUT -> this.obtainItems(queueId);
                case ForgeQueueManipulateType_STOP_FORGE -> this.cancelForge(queueId);
                default -> failManipulate(req.getManipulateTypeValue(), Retcode.RET_FAIL);
            }
        }
    }

    /**********
     * Periodic forging updates.
     **********/
    public synchronized void sendPlayerForgingUpdate() {
        int currentTime = Utils.getCurrentSeconds();

        // Determine if sending an update is necessary.
        // We only send an update if there are forges in the forge queue
        // that have changed since the last notification.
        if (this.player.getActiveForges().size() <= 0) {
            return;
        }

        boolean hasChanges =
                this.player.getActiveForges().stream()
                        .anyMatch(forge -> forge.updateChanged(currentTime));

        if (!hasChanges) {
            return;
        }

        // Send notification.
        this.sendForgeQueueDataNotify();

        // Reset changed flags.
        this.player.getActiveForges().stream().forEach(forge -> forge.setChanged(false));
    }
}
