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
            if (active.stream().anyMatch(queue -> queue == null || queue.getStartState() != 0)) {
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
        // Determine how many items are finished.
        int currentTime = Utils.getCurrentSeconds();
        ActiveForgeData forge = this.player.getActiveForges().get(queueId - 1);

        int finished = forge.getFinishedCount(currentTime);
        int unfinished = forge.getUnfinishedCount(currentTime);

        // Sanity check: Are any items finished?
        if (finished <= 0) {
            return;
        }

        // Give finished items to the player.
        ForgeData data = GameData.getForgeDataMap().get(forge.getForgeId());

        int resultId = data.getResultItemId() > 0 ? data.getResultItemId() : data.getShowItemId();
        ItemData resultItemData = GameData.getItemDataMap().get(resultId);
        GameItem addItem = new GameItem(resultItemData, data.getResultItemCount() * finished);

        // Call the PlayerForgeItemEvent.
        var event = new PlayerForgeItemEvent(this.player, addItem);
        if (!event.call()) return;

        addItem = event.getItemForged();
        this.player.getInventory().addItem(addItem);

        // Battle pass trigger handler
        this.player
                .getBattlePassManager()
                .triggerMission(WatcherTriggerType.TRIGGER_DO_FORGE, 0, finished);

        // Replace active forge with a new one for the unfinished items, if there are any.
        if (unfinished > 0) {
            ActiveForgeData remainingForge = new ActiveForgeData();

            remainingForge.setForgeId(forge.getForgeId());
            remainingForge.setAvatarId(forge.getAvatarId());
            remainingForge.setCount(unfinished);
            remainingForge.setForgeTime(forge.getForgeTime());
            remainingForge.setStartTime(forge.getStartTime() + finished * forge.getForgeTime());

            this.player.getActiveForges().set(queueId - 1, remainingForge);
            this.sendForgeQueueDataNotify();
        }
        // Otherwise, completely remove it.
        else {
            this.player.getActiveForges().remove(queueId - 1);
            // this.sendForgeQueueDataNotify(queueId);
            this.sendForgeQueueDataNotify(true);
        }

        // Send response.
        this.player.sendPacket(
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT,
                        List.of(addItem),
                        List.of(),
                        List.of()));
    }

    private synchronized void cancelForge(int queueId) {
        // Make sure there are no unfinished items.
        int currentTime = Utils.getCurrentSeconds();
        ActiveForgeData forge = this.player.getActiveForges().get(queueId - 1);

        if (forge.getFinishedCount(currentTime) > 0) {
            return;
        }

        // Return material items to the player.
        ForgeData data = GameData.getForgeDataMap().get(forge.getForgeId());

        var returnItems = new ArrayList<GameItem>();
        for (var material : data.getMaterialItems()) {
            if (material.getItemId() == 0) {
                continue;
            }

            ItemData resultItemData = GameData.getItemDataMap().get(material.getItemId());
            GameItem returnItem =
                    new GameItem(resultItemData, material.getItemCount() * forge.getCount());

            this.player.getInventory().addItem(returnItem);
            returnItems.add(returnItem);
        }

        // Return Mora to the player.
        this.player.setMora(this.player.getMora() + data.getScoinCost() * forge.getCount());

        ItemData moraItem = GameData.getItemDataMap().get(202);
        GameItem returnMora = new GameItem(moraItem, data.getScoinCost() * forge.getCount());
        returnItems.add(returnMora);

        // Return forge points to the player.
        int requiredPoints = data.getForgePoint() * forge.getCount();
        int newPoints = Math.min(this.player.getForgePoints() + requiredPoints, 300_000);

        this.player.setForgePoints(newPoints);

        // Remove the forge queue.
        this.player.getActiveForges().remove(queueId - 1);
        this.sendForgeQueueDataNotify(true);

        // Send response.
        this.player.sendPacket(
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_STOP_FORGE,
                        List.of(),
                        returnItems,
                        List.of()));
    }

    public synchronized void handleForgeQueueManipulateReq(ForgeQueueManipulateReq req) {
        // Get info from the request.
        int queueId = req.getForgeQueueId();
        var manipulateType = req.getManipulateType();
        if (queueId <= 0
                || queueId > player.getActiveForges().size()
                || player.getActiveForges().get(queueId - 1) == null
                || player.getActiveForges().get(queueId - 1).getStartState() != 0) {
            player.sendPacket(
                    new PacketForgeQueueManipulateRsp(
                            Retcode.RET_FAIL, manipulateType, List.of(), List.of(), List.of()));
            return;
        }

        // Handle according to the manipulation type.
        switch (manipulateType) {
            case ForgeQueueManipulateType_RECEIVE_OUTPUT -> this.obtainItems(queueId);
            case ForgeQueueManipulateType_STOP_FORGE -> this.cancelForge(queueId);
            default -> {} // Should never happen.
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
