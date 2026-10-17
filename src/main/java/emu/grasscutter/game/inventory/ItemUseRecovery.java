package emu.grasscutter.game.inventory;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketPlayerStoreNotify;
import emu.grasscutter.utils.JsonUtils;

import org.bson.*;
import org.bson.json.*;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Resolve a food's inventory cost explicitly; no avatar effect or use callback is replayed. */
public final class ItemUseRecovery {
    private final Player player;
    private final Consumer<Object> save;

    public ItemUseRecovery(Player player) {
        this(player, DatabaseHelper::saveGameSync);
    }

    public ItemUseRecovery(Player player, Consumer<Object> save) {
        this.player = Objects.requireNonNull(player);
        this.save = Objects.requireNonNull(save);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    private void flush() {
        try {
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)), "数据库仍有待写入操作，请稍后重试。");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("物品使用核对被中断。", error);
        }
    }

    private ItemUseReceipt storedReceipt() {
        // Decode only the embedded intent: loading another Player can invoke unrelated lifecycle
        // writes.
        var raw =
                DatabaseManager.getGameDatastore()
                        .getMapper()
                        .getCollection(Player.class)
                        .withDocumentClass(BsonDocument.class)
                        .find(new BsonDocument("_id", new BsonInt32(player.getUid())))
                        .projection(new BsonDocument("pendingItemUse", new BsonInt32(1)))
                        .first();
        require(raw != null, "玩家存档不存在。");
        if (!raw.containsKey("pendingItemUse") || raw.isNull("pendingItemUse")) return null;
        return JsonUtils.decode(
                raw.getDocument("pendingItemUse")
                        .toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()),
                ItemUseReceipt.class);
    }

    private void agreeReceipt(ItemUseReceipt live, ItemUseReceipt stored) {
        require(JsonUtils.toJson(live).equals(JsonUtils.toJson(stored)), "使用记录与存档不一致，请重登后再核对。");
    }

    private record Cost(GameItem item, int count, boolean before) {}

    private Cost cost(ItemUseReceipt receipt) {
        require(
                receipt.getSourceId() > 0
                        && receipt.getSourceBefore() > 0
                        && receipt.getUsedCount() > 0
                        && receipt.getUsedCount() <= receipt.getSourceBefore()
                        && receipt.getSourceObjectId() != null
                        && ObjectId.isValid(receipt.getSourceObjectId()),
                "使用记录没有完整成本快照。");
        var inventory = player.getInventory();
        var live =
                inventory.getItems().values().stream()
                        .filter(
                                x ->
                                        x.getItemId() == receipt.getSourceId()
                                                || x.getObjectId() != null
                                                        && x.getObjectId()
                                                                .toHexString()
                                                                .equals(
                                                                        receipt
                                                                                .getSourceObjectId()))
                        .toList();
        var stored = new ArrayList<GameItem>();
        try (var cursor =
                DatabaseManager.getGameDatastore()
                        .find(GameItem.class)
                        .field("ownerId")
                        .equal(player.getUid())
                        .field("itemId")
                        .equal(receipt.getSourceId())
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.ne("deleted", true),
                                dev.morphia.query.experimental.filters.Filters.gt("count", 0))
                        .iterator()) {
            cursor.forEachRemaining(stored::add);
        }
        var byId =
                DatabaseManager.getGameDatastore()
                        .find(GameItem.class)
                        .field("id")
                        .equal(new ObjectId(receipt.getSourceObjectId()))
                        .first();
        if (byId != null
                && Objects.equals(byId.getFoodRefundOperationId(), receipt.getOperationId())) {
            require(
                    Set.of(5, 6).contains(receipt.getState())
                            && byId.getOwnerId() == player.getUid()
                            && byId.getItemId() == receipt.getSourceId(),
                    "已退款记录的归属或阶段不符。");
            return new Cost(null, byId.getCount(), true);
        }
        require(live.size() <= 1 && stored.size() == live.size(), "物品堆叠存在状态不同或同类堆叠不唯一，请重登后核对。");
        int count = 0;
        GameItem source = null;
        if (live.isEmpty())
            require(
                    byId == null
                            || byId.getOwnerId() == player.getUid()
                                    && byId.getItemId() == receipt.getSourceId()
                                    && byId.getCount() == 0,
                    "成本持久 ID 已被其他物品占用。");
        else {
            source = live.get(0);
            var saved = stored.get(0);
            var tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
            require(
                    source.getItemId() == receipt.getSourceId()
                            && source.getOwnerId() == player.getUid()
                            && source.getObjectId() != null
                            && source.getObjectId()
                                    .toHexString()
                                    .equals(receipt.getSourceObjectId())
                            && byId != null
                            && source.getObjectId().equals(saved.getObjectId())
                            && byId.getOwnerId() == player.getUid()
                            && byId.getItemId() == receipt.getSourceId()
                            && byId.getCount() == source.getCount()
                            && saved.getCount() == source.getCount()
                            && source.getCount() > 0
                            && inventory.getItemByGuid(source.getGuid()) == source
                            && tab != null
                            && tab.getItemById(source.getItemId()) == source,
                    "成本的归属、数量、库存索引或持久 ID 与存档不符。");
            count = source.getCount();
        }
        boolean before = count == receipt.getSourceBefore();
        boolean paid = count == receipt.getSourceBefore() - receipt.getUsedCount();
        require(before || paid, "成本数量不等于扣除或退款前后快照。");
        if (Set.of(2, 3, 4, 7).contains(receipt.getState())) require(paid, "已付款记录的成本数量与存档不符。");
        if (receipt.getState() == 6) require(before, "已退款记录的成本数量与存档不符。");
        return new Cost(source, count, before);
    }

    private static String stage(int value) {
        return switch (value) {
            case 1 -> "预留";
            case 2 -> "付款已确认，效果尚未开始";
            case 3 -> "效果已完成";
            case 4 -> "效果可能已部分生效";
            case 5 -> "已决定退款，退款未确认";
            case 6 -> "库存退款已确认";
            case 7 -> "已接受消耗，等待清除记录";
            default -> "未知阶段";
        };
    }

    public List<String> inspect() {
        if (player.getPendingItemUse() != null
                && "recipe".equals(player.getPendingItemUse().getKind()))
            return new RecipeUseRecovery(player, save).inspect();
        synchronized (player.getInventory()) {
            flush();
            var stored = storedReceipt();
            var receipt = player.getPendingItemUse();
            var rows = new ArrayList<String>();
            rows.add("uid=" + player.getUid());
            if (receipt == null && stored == null) rows.add("无待核对的物品使用记录。");
            else if (receipt == null) rows.add("存档有未确认记录，内存没有对应记录，请重登后核对。");
            else {
                rows.add(
                        "operation="
                                + receipt.getOperationId()
                                + " kind="
                                + receipt.getKind()
                                + " state="
                                + receipt.getState()
                                + " source="
                                + receipt.getSourceId()
                                + " objectId="
                                + receipt.getSourceObjectId()
                                + " before="
                                + receipt.getSourceBefore()
                                + " used="
                                + receipt.getUsedCount()
                                + " recordedGuid="
                                + receipt.getSourceGuid());
                rows.add(
                        "targetAvatar="
                                + receipt.getTargetAvatarId()
                                + " recordedTargetGuid="
                                + receipt.getTargetGuid());
                if (!"food".equals(receipt.getKind())) rows.add("非食物记录仅支持查看，不能通过本入口退款或结算。");
                else {
                    rows.add(stage(receipt.getState()) + "；核对只处理库存，不改饱食度、冷却或角色效果。");
                    try {
                        agreeReceipt(receipt, stored);
                        var cost = cost(receipt);
                        rows.add(
                                "live=stored="
                                        + cost.count()
                                        + " currentGuid="
                                        + (cost.item() == null ? "none" : cost.item().getGuid()));
                        rows.add(
                                Set.of(1, 2, 5, 6).contains(receipt.getState())
                                        ? "可使用 refund-cost 仅退库存成本；不会重放或撤销角色效果。"
                                        : Set.of(3, 4, 7).contains(receipt.getState())
                                                ? "只能使用 settle 接受当前效果和消耗；不会重新执行食物。"
                                                : "阶段无效，需要人工核对。");
                    } catch (IllegalStateException mismatch) {
                        rows.add("不能处理：" + mismatch.getMessage());
                    }
                }
            }
            return List.copyOf(rows);
        }
    }

    private void preflightRefund(ItemUseReceipt receipt, Cost cost) {
        if (cost.before()) return;
        var data = GameData.getItemDataMap().get(receipt.getSourceId());
        require(
                data != null
                        && data.getId() == receipt.getSourceId()
                        && FoodItemUse.isFood(data)
                        && !data.isUseOnGain()
                        && data.getStackLimit() >= receipt.getSourceBefore(),
                "食物资源缺失、类型已改变或退款超过堆叠上限。");
        var inventory = player.getInventory();
        var tab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
        require(
                tab != null && (cost.item() != null || tab.getSize() < tab.getMaxCapacity()),
                "背包没有退款所需的空位。");
        if (cost.item() != null)
            require(
                    cost.item().getItemData() != null
                            && cost.item().getItemData().getId() == receipt.getSourceId()
                            && FoodItemUse.isFood(cost.item().getItemData()),
                    "现存堆叠的食物资源不符。");
    }

    private void refund(ItemUseReceipt receipt, Cost cost) {
        if (receipt.getState() == 6) return;
        if (receipt.getState() != 5) {
            receipt.foodState(receipt.getState(), 5);
            save.accept(player);
        }
        if (cost.before() && cost.item() != null)
            cost.item().getSaveGuard().confirmStoredVersion(DatabaseManager.getGameDatastore());
        if (!cost.before()) {
            var source = cost.item();
            if (source == null) {
                source =
                        DatabaseManager.getGameDatastore()
                                .find(GameItem.class)
                                .field("id")
                                .equal(new ObjectId(receipt.getSourceObjectId()))
                                .first();
                if (source == null) {
                    source = new GameItem(GameData.getItemDataMap().get(receipt.getSourceId()), 1);
                    source.restorePersistenceId(new ObjectId(receipt.getSourceObjectId()));
                } else
                    require(
                            source.getOwnerId() == player.getUid()
                                    && source.getItemId() == receipt.getSourceId()
                                    && source.getCount() == 0,
                            "退款原物品的归属或数量已改变。");
                source.setOwner(
                        player); // Runtime GUIDs from a previous session must never be reused.
                source.setCount(0);
            }
            require(
                    player.getInventory()
                            .restoreUseReservation(
                                    source, receipt.getSourceBefore(), receipt.getUsedCount()),
                    "退款堆叠已改变，保留记录。");
            save.accept(source);
        }
        receipt.foodState(5, 6);
        save.accept(player);
    }

    private void clear(ItemUseReceipt receipt) {
        player.setPendingItemUse(null);
        try {
            save.accept(player);
        } catch (RuntimeException failure) {
            player.setPendingItemUse(receipt);
            throw failure;
        }
    }

    public void resolve(String operationId, String action) {
        if (player.getPendingItemUse() != null
                && "recipe".equals(player.getPendingItemUse().getKind())) {
            new RecipeUseRecovery(player, save).resolve(operationId, action);
            return;
        }
        synchronized (player.getInventory()) {
            require(
                    "refund-cost".equals(action) || "settle".equals(action),
                    "请使用 refund-cost 或 settle。");
            flush();
            var receipt = player.getPendingItemUse();
            require(
                    receipt != null
                            && operationId != null
                            && !operationId.isBlank()
                            && operationId.equals(receipt.getOperationId()),
                    "操作 ID 不匹配或记录已经处理。");
            require(
                    "food".equals(receipt.getKind())
                            && receipt.getOperation() == 0
                            && receipt.getProducts().isEmpty()
                            && receipt.getDeliveries().isEmpty()
                            && receipt.getOptionId() == 0
                            && receipt.getOptionIds().isEmpty(),
                    "本入口只处理完整的食物成本记录，不能覆盖礼包或旧的不完整记录。");
            agreeReceipt(receipt, storedReceipt());
            var cost = cost(receipt);
            if (action.equals("refund-cost")) {
                require(
                        Set.of(1, 2, 5, 6).contains(receipt.getState()),
                        "效果可能已生效，不能自动退款；请先核对，再明确使用 settle 接受消耗。");
                preflightRefund(receipt, cost);
                refund(receipt, cost);
            } else {
                require(
                        Set.of(3, 4, 7).contains(receipt.getState()),
                        "尚未确认效果或正在退款，请使用 refund-cost 处理库存。");
                if (receipt.getState() == 4) {
                    receipt.foodState(4, 7);
                    save.accept(player);
                }
            }
            clear(receipt);
            Grasscutter.getLogger()
                    .info(
                            "Item use cost recovery uid={} operation={} action={} item={} used={}",
                            player.getUid(),
                            operationId,
                            action,
                            receipt.getSourceId(),
                            receipt.getUsedCount());
            try {
                // A full snapshot avoids deleting another item with a recycled old runtime GUID.
                player.sendPacket(new PacketPlayerStoreNotify(player));
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Could not publish recovered inventory uid={} operation={}",
                                player.getUid(),
                                operationId,
                                unavailable);
            }
        }
    }
}
