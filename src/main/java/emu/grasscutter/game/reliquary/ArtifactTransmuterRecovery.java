package emu.grasscutter.game.reliquary;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;

import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Explicit recovery of a frozen intent; mismatching storage is never guessed or refunded. */
final class ArtifactTransmuterRecovery {
    private final Player player;
    private final Runnable save;
    private final Consumer<GameItem> saveItem;

    ArtifactTransmuterRecovery(Player player, Runnable save, Consumer<GameItem> saveItem) {
        this.player = player;
        this.save = save;
        this.saveItem = saveItem;
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }

    List<String> inspect() {
        synchronized (player.getInventory()) {
            var data = player.getArtifactTransmuterData();
            var result = new ArrayList<String>();
            result.add(
                    "uid="
                            + player.getUid()
                            + " schedule="
                            + data.getScheduleId()
                            + " progressUnits="
                            + data.getProgressUnits()
                            + " extracted="
                            + data.getExtractedCount()
                            + " defined="
                            + data.getDefinedCounts());
            var receipt = data.getPending();
            if (receipt == null) result.add("无待核对的萃取／制作记录。");
            else {
                result.add(
                        "operation="
                                + receipt.getOperationId()
                                + " state="
                                + receipt.getState()
                                + " kind="
                                + (receipt.getPurchaseId() == 0 ? "extract" : "define")
                                + " reward="
                                + receipt.getRewardId()
                                + " x"
                                + receipt.getRewardCount()
                                + " output="
                                + receipt.getOutputObjectId());
                for (var cost : receipt.getCosts()) {
                    var item = live(cost.getObjectId());
                    var stored = stored(cost.getObjectId());
                    result.add(
                            "cost="
                                    + cost.getItemId()
                                    + " objectId="
                                    + cost.getObjectId()
                                    + " before="
                                    + cost.getBefore()
                                    + " consume="
                                    + cost.getConsumed()
                                    + " live="
                                    + (item == null ? 0 : item.getCount())
                                    + " stored="
                                    + (stored == null ? 0 : stored.getCount()));
                }
            }
            for (var entry : new TreeMap<>(data.getReshapeCandidates()).entrySet())
                result.add("reshape=" + entry.getKey() + " phase=" + entry.getValue().getPhase());
            return List.copyOf(result);
        }
    }

    private GameItem live(String id) {
        var matches =
                player.getInventory().getItems().values().stream()
                        .filter(
                                x ->
                                        x.getObjectId() != null
                                                && x.getObjectId().toHexString().equals(id))
                        .limit(2)
                        .toList();
        require(matches.size() <= 1, "同一持久 ID 对应多个背包条目，需先核对。");
        return matches.isEmpty() ? null : matches.get(0);
    }

    private GameItem stored(String id) {
        require(id != null && ObjectId.isValid(id), "记录中的物品持久 ID 无效。");
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .field("id")
                .equal(new ObjectId(id))
                .first();
    }

    private void agree(GameItem live, GameItem stored, int itemId) {
        require((live == null) == (stored == null), "背包与存档的物品存在状态不同，请重新登录后核对。");
        if (live == null) return;
        require(
                live.getOwnerId() == player.getUid()
                        && stored.getOwnerId() == player.getUid()
                        && live.getItemId() == itemId
                        && stored.getItemId() == itemId
                        && live.getCount() > 0
                        && live.getCount() == stored.getCount()
                        && live.getObjectId().equals(stored.getObjectId()),
                "物品归属、数量或持久 ID 与记录不符。");
        if (live.getItemData() != null && live.getItemType() == ItemType.ITEM_RELIQUARY)
            require(
                    live.getLevel() == stored.getLevel()
                            && live.getExp() == stored.getExp()
                            && live.getTotalExp() == stored.getTotalExp()
                            && live.getMainPropId() == stored.getMainPropId()
                            && Objects.equals(
                                    live.getAppendPropIdList(), stored.getAppendPropIdList())
                            && live.isLocked() == stored.isLocked()
                            && live.getEquipCharacter() == stored.getEquipCharacter()
                            && live.getPurchasedAppendPropIdList()
                                    .equals(stored.getPurchasedAppendPropIdList())
                            && live.getDefiniteAppendPropIdList()
                                    .equals(stored.getDefiniteAppendPropIdList())
                            && live.getDefinedInitialAppendCount()
                                    == stored.getDefinedInitialAppendCount()
                            && live.getDefinedUpgradeGuarantee()
                                    == stored.getDefinedUpgradeGuarantee(),
                    "圣遗物属性与存档不同，请重新登录后核对。");
    }

    private record Payment(ArtifactTransmuterReceipt.Cost cost, GameItem item, boolean paid) {}

    private List<Payment> payments(ArtifactTransmuterReceipt receipt, boolean resume) {
        require(receipt.getCosts() != null && !receipt.getCosts().isEmpty(), "记录没有完整成本。");
        var unique = new HashSet<String>();
        var payments = new ArrayList<Payment>();
        for (var cost : receipt.getCosts()) {
            require(
                    cost.getItemId() > 0
                            && cost.getBefore() > 0
                            && cost.getConsumed() > 0
                            && cost.getConsumed() <= cost.getBefore()
                            && unique.add(cost.getObjectId()),
                    "记录的成本数量或持久 ID 无效。");
            var item = live(cost.getObjectId());
            agree(item, stored(cost.getObjectId()), cost.getItemId());
            int count = item == null ? 0 : item.getCount();
            boolean paid = count == cost.getBefore() - cost.getConsumed();
            require(paid || count == cost.getBefore(), "成本现存数量不等于支付前后快照，停止自动核对。");
            if (!paid && resume) {
                require(item.getItemData() != null, "待支付物品资源缺失。");
                if (item.getItemType() == ItemType.ITEM_RELIQUARY)
                    require(item.isDestroyable(), "待支付圣遗物已装备、锁定或被其他操作占用。");
            }
            require(receipt.getState() == 1 || paid, "已确认支付的成本与存档不符。");
            payments.add(new Payment(cost, item, paid));
        }
        return payments;
    }

    private record Output(GameItem item, boolean delivered) {}

    private Output output(
            ArtifactTransmuterReceipt receipt, boolean resume, List<Payment> payments) {
        if (receipt.getPurchaseId() > 0) return definition(receipt, resume);
        require(
                receipt.getPurchaseId() == 0
                        && receipt.getRewardCount() >= 0
                        && receipt.getRewardBefore() >= 0,
                "萃取奖励记录无效。");
        if (receipt.getRewardCount() == 0) return new Output(null, true);
        long after = (long) receipt.getRewardBefore() + receipt.getRewardCount();
        var inventory = player.getInventory();
        var item = inventory.getItemById(receipt.getRewardId());
        var saved =
                DatabaseHelper.getInventoryItems(player).stream()
                        .filter(x -> x.getItemId() == receipt.getRewardId())
                        .toList();
        require(
                saved.size() <= 1
                        && inventory.getItems().values().stream()
                                        .filter(x -> x.getItemId() == receipt.getRewardId())
                                        .count()
                                <= 1,
                "萃取奖励存在多个堆叠，需先核对。");
        agree(item, saved.isEmpty() ? null : saved.get(0), receipt.getRewardId());
        int count = item == null ? 0 : item.getCount();
        require(count == receipt.getRewardBefore() || count == after, "萃取奖励现存数量不等于发放前后快照。");
        if (resume && count != after) {
            var definition = GameData.getItemDataMap().get(receipt.getRewardId());
            require(
                    definition != null
                            && definition.getItemType() == ItemType.ITEM_MATERIAL
                            && !definition.isUseOnGain()
                            && after <= definition.getStackLimit(),
                    "萃取奖励资源缺失或超过堆叠上限。");
            if (item == null) {
                var tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
                long freed =
                        payments.stream()
                                .filter(
                                        x ->
                                                !x.paid()
                                                        && x.item().getItemType()
                                                                == ItemType.ITEM_MATERIAL
                                                        && x.item().getCount()
                                                                == x.cost().getConsumed())
                                .count();
                require(tab != null && tab.getSize() - freed < tab.getMaxCapacity(), "材料背包已满。");
            }
        }
        return new Output(item, count == after);
    }

    private boolean matches(GameItem item, ArtifactTransmuterReceipt receipt) {
        return item.getItemType() == ItemType.ITEM_RELIQUARY
                && item.getCount() == 1
                && item.getLevel() == 1
                && item.getExp() == 0
                && item.getTotalExp() == 0
                && item.getMainPropId() == receipt.getMainPropId()
                && item.getAppendPropIdList().equals(receipt.getAppendPropIds())
                && item.getPurchasedAppendPropIdList().equals(receipt.getSelectedPropIds())
                && item.getDefinedUpgradeGuarantee() > 0
                && (receipt.getDefinedUpgradeGuarantee() == 0
                        || item.getDefinedUpgradeGuarantee()
                                == receipt.getDefinedUpgradeGuarantee());
    }

    private Output definition(ArtifactTransmuterReceipt receipt, boolean resume) {
        require(
                receipt.getRewardCount() == 1
                        && receipt.getSetId() > 0
                        && receipt.getDefinedAfter() == receipt.getDefinedBefore() + 1,
                "定向制作记录无效。");
        var item = live(receipt.getOutputObjectId());
        agree(item, stored(receipt.getOutputObjectId()), receipt.getRewardId());
        if (item != null) {
            require(matches(item, receipt), "制作产物已改变，不能重新发放或覆盖。");
            return new Output(item, true);
        }
        if (!resume) return new Output(null, false);
        var definition = GameData.getItemDataMap().get(receipt.getRewardId());
        var main = GameData.getReliquaryMainPropDataMap().get(receipt.getMainPropId());
        require(
                definition != null
                        && definition.getItemType() == ItemType.ITEM_RELIQUARY
                        && definition.getSetId() == receipt.getSetId()
                        && main != null
                        && main.getFightProp() != null
                        && main.getFightProp().getId() > 0
                        && main.getPropDepotId() == definition.getMainPropDepotId()
                        && receipt.getAppendPropIds() != null
                        && receipt.getSelectedPropIds() != null
                        && receipt.getAppendPropIds().size() == definition.getAppendPropNum()
                        && receipt.getSelectedPropIds().size() == 2
                        && receipt.getDefinedUpgradeGuarantee() > 0,
                "制作快照或资源不完整；旧记录缺少强化约束时不能重建产物。");
        var properties = new HashSet<Integer>();
        properties.add(main.getFightProp().getId());
        for (int id : receipt.getAppendPropIds()) {
            var affix = GameData.getReliquaryAffixDataMap().get(id);
            require(
                    affix != null
                            && affix.getDepotId() == definition.getAppendPropDepotId()
                            && affix.getFightProp() != null
                            && properties.add(affix.getFightProp().getId()),
                    "制作快照中的副属性无效。");
        }
        var tab = player.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
        require(tab != null && tab.getSize() < tab.getMaxCapacity(), "圣遗物背包已满。");
        item = new GameItem(definition);
        item.restorePersistenceId(new ObjectId(receipt.getOutputObjectId()));
        item.setMainPropId(receipt.getMainPropId());
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(receipt.getAppendPropIds());
        item.setLocked(true);
        item.markAsDefinedReliquary(
                receipt.getSelectedPropIds(), receipt.getDefinedUpgradeGuarantee());
        return new Output(item, false);
    }

    private void clear(ArtifactTransmuterData data, ArtifactTransmuterReceipt receipt) {
        data.setPending(null);
        try {
            save.run();
        } catch (RuntimeException failure) {
            data.setPending(receipt);
            throw failure;
        }
    }

    void reconcile(String operationId, boolean resume) {
        synchronized (player.getInventory()) {
            try {
                require(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)), "数据库仍有待写入操作，请稍后重试。");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("核对被中断。", interrupted);
            }
            var data = player.getArtifactTransmuterData();
            var receipt = data.getPending();
            require(
                    receipt != null
                            && operationId != null
                            && operationId.equals(receipt.getOperationId()),
                    "未找到对应操作 ID 的待核对记录。");
            var stored =
                    DatabaseManager.getGameDatastore()
                            .find(Player.class)
                            .field("id")
                            .equal(player.getUid())
                            .first();
            require(stored != null, "玩家存档不存在。");
            var saved = stored.getArtifactTransmuterData();
            require(
                    JsonUtils.toJson(receipt).equals(JsonUtils.toJson(saved.getPending()))
                            && data.getScheduleId() == saved.getScheduleId()
                            && data.getCycleEndTime() == saved.getCycleEndTime()
                            && data.getProgressUnits() == saved.getProgressUnits()
                            && data.getExtractedCount() == saved.getExtractedCount()
                            && data.getDefinedCounts().equals(saved.getDefinedCounts()),
                    "内存记录与存档不同，请重新登录后核对。");
            require(
                    receipt.getState() >= 1
                            && receipt.getState() <= 3
                            && data.getScheduleId() == receipt.getScheduleId()
                            && data.getCycleEndTime() == receipt.getCycleEndTime(),
                    "记录阶段或周期不符。");
            boolean applied = receipt.getState() == 3;
            require(
                    data.getProgressUnits()
                                    == (applied
                                            ? receipt.getProgressAfter()
                                            : receipt.getProgressBefore())
                            && data.getExtractedCount()
                                    == (applied
                                            ? receipt.getExtractedAfter()
                                            : receipt.getExtractedBefore())
                            && (receipt.getPurchaseId() == 0
                                    || data.getDefinedCounts().getOrDefault(receipt.getSetId(), 0)
                                            == (applied
                                                    ? receipt.getDefinedAfter()
                                                    : receipt.getDefinedBefore())),
                    "玩家进度与操作前后快照不符。");
            var payments = payments(receipt, resume);
            var output = output(receipt, resume, payments);
            if (!resume) {
                require(
                        receipt.getState() == 1
                                && payments.stream().noneMatch(Payment::paid)
                                && (receipt.getRewardCount() == 0 || !output.delivered()),
                        "只能取消材料和奖励均未改变的预留；已扣费记录请使用 resume。");
                clear(data, receipt);
            } else {
                require(!applied || output.delivered(), "已结算记录的产物与存档不符。");
                if (!applied) {
                    for (var payment : payments) {
                        if (payment.paid()) continue;
                        int before = payment.item().getCount();
                        require(
                                player.getInventory()
                                                .removeItem(
                                                        payment.item(),
                                                        payment.cost().getConsumed())
                                        && before - payment.item().getCount()
                                                == payment.cost().getConsumed(),
                                "成本扣除未得到准确确认，保留记录。");
                        saveItem.accept(payment.item());
                    }
                    receipt.paid();
                    save.run();
                    if (!output.delivered()) {
                        GameItem delivered;
                        if (receipt.getPurchaseId() > 0) {
                            require(player.getInventory().addItem(output.item()), "重建产物发放未确认。");
                            delivered = live(receipt.getOutputObjectId());
                            require(
                                    delivered == output.item() && matches(delivered, receipt),
                                    "重建产物与快照不同。");
                        } else {
                            require(
                                    player.getInventory()
                                            .addItem(
                                                    receipt.getRewardId(),
                                                    receipt.getRewardCount()),
                                    "萃取奖励发放未确认。");
                            delivered = player.getInventory().getItemById(receipt.getRewardId());
                            require(
                                    delivered != null
                                            && delivered.getCount()
                                                    == (long) receipt.getRewardBefore()
                                                            + receipt.getRewardCount(),
                                    "萃取奖励数量未准确确认。");
                        }
                        saveItem.accept(delivered);
                    }
                    data.setProgressUnits(receipt.getProgressAfter());
                    data.setExtractedCount(receipt.getExtractedAfter());
                    if (receipt.getPurchaseId() > 0) {
                        var counts = new HashMap<>(data.getDefinedCounts());
                        counts.put(receipt.getSetId(), receipt.getDefinedAfter());
                        data.setDefinedCounts(counts);
                    }
                    receipt.applied();
                    save.run();
                }
                clear(data, receipt);
            }
            Grasscutter.getLogger()
                    .info(
                            "Artifact intent recovery uid={} operation={} action={}",
                            player.getUid(),
                            operationId,
                            resume ? "resume" : "cancel");
        }
    }
}
