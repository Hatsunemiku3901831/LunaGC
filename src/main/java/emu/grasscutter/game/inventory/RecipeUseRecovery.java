package emu.grasscutter.game.inventory;

import com.mongodb.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.bson.json.*;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Resume one idempotent learning flag only after checking its durable exact-item debit proof. */
final class RecipeUseRecovery {
    private final Player player;
    private final Consumer<Object> save;

    RecipeUseRecovery(Player player, Consumer<Object> save) {
        this.player = player;
        this.save = save;
    }

    private static void require(boolean value, String message) {
        RecipeItemUse.require(value, message);
    }

    static Document profile(Player player) {
        var row =
                DatabaseManager.getGameDatastore()
                        .getMapper()
                        .getCollection(Player.class)
                        .withDocumentClass(Document.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(new Document("_id", player.getUid()))
                        .projection(
                                new Document("pendingItemUse", 1)
                                        .append("unlockedCombines", 1)
                                        .append("unlockedForgingBlueprints", 1)
                                        .append("unlockedRecipies", 1)
                                        .append("unlockedFurniture", 1)
                                        .append("unlockedFurnitureSuite", 1))
                        .first();
        require(row != null, "玩家存档不存在。");
        return row;
    }

    static ItemUseReceipt receipt(Document profile) {
        var doc = profile.get("pendingItemUse", Document.class);
        return doc == null
                ? null
                : JsonUtils.decode(
                        doc.toJson(
                                JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()),
                        ItemUseReceipt.class);
    }

    static boolean savedKnown(Player player, ItemUseReceipt receipt, Document profile) {
        var op = RecipeItemUse.operation(receipt);
        String field =
                switch (op) {
                    case ITEM_USE_UNLOCK_COMBINE -> "unlockedCombines";
                    case ITEM_USE_UNLOCK_FORGE -> "unlockedForgingBlueprints";
                    case ITEM_USE_UNLOCK_COOK_RECIPE -> "unlockedRecipies";
                    case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> "unlockedFurniture";
                    case ITEM_USE_UNLOCK_FURNITURE_SUITE -> "unlockedFurnitureSuite";
                    case ITEM_USE_UNLOCK_HOME_BGM -> "unlockedHomeBgmList";
                    default -> throw new IllegalStateException("未知学习类型。");
                };
        Document target = profile;
        if (op == ItemUseOp.ITEM_USE_UNLOCK_HOME_BGM) {
            target =
                    DatabaseManager.getGameDatastore()
                            .getMapper()
                            .getCollection(GameHome.class)
                            .withDocumentClass(Document.class)
                            .withReadPreference(ReadPreference.primary())
                            .withReadConcern(ReadConcern.MAJORITY)
                            .find(new Document("_id", receipt.getRecipeHomeId()))
                            .projection(new Document("ownerUid", 1).append(field, 1))
                            .first();
            Object owner = target == null ? null : target.get("ownerUid");
            require(
                    (owner instanceof Integer || owner instanceof Long)
                            && ((Number) owner).longValue() == player.getUid(),
                    "家园音乐存档的归属不符。");
        }
        if (op == ItemUseOp.ITEM_USE_UNLOCK_COOK_RECIPE) {
            var recipes = target.get(field, Document.class);
            return recipes != null
                    && recipes.containsKey(Integer.toString(receipt.getRecipeTargetId()));
        }
        return target.getList(field, Integer.class, List.of())
                .contains(receipt.getRecipeTargetId());
    }

    private void flush() {
        try {
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)), "数据库仍有待写入操作，请稍后重试。");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("学习核对被中断。", error);
        }
    }

    private void validate(ItemUseReceipt receipt) {
        require(
                receipt != null
                        && "recipe".equals(receipt.getKind())
                        && receipt.getState() >= 1
                        && receipt.getState() <= 3
                        && receipt.getOperationId() != null
                        && UUID.fromString(receipt.getOperationId())
                                .toString()
                                .equals(receipt.getOperationId())
                        && receipt.getSourceId() > 0
                        && receipt.getSourceBefore() > 0
                        && receipt.getUsedCount() == 1
                        && receipt.getSourceObjectId() != null
                        && ObjectId.isValid(receipt.getSourceObjectId())
                        && receipt.getRecipeTargetId() > 0
                        && receipt.getOptionId() == 0
                        && receipt.getOptionIds().isEmpty()
                        && receipt.getProducts().isEmpty()
                        && receipt.getDeliveries().isEmpty()
                        && receipt.getTargetAvatarId() == 0
                        && receipt.getTargetGuid() == 0,
                "学习记录不完整或阶段无效。");
        RecipeItemUse.home(player, receipt);
    }

    private record Cost(GameItem source, boolean paid, int count) {}

    private Cost cost(ItemUseReceipt receipt) {
        var ds = DatabaseManager.getGameDatastore();
        var id = new ObjectId(receipt.getSourceObjectId());
        var live =
                player.getInventory().getItems().values().stream()
                        .filter(x -> id.equals(x.getObjectId()))
                        .toList();
        require(live.size() <= 1, "同一学习书实例出现多个库存条目。");
        var source = live.isEmpty() ? null : live.get(0);
        var row =
                ds.getMapper()
                        .getCollection(GameItem.class)
                        .withDocumentClass(Document.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(new Document("_id", id))
                        .projection(
                                new Document("ownerId", 1)
                                        .append("itemId", 1)
                                        .append("count", 1)
                                        .append("deleted", 1)
                                        .append("saveStamp", 1)
                                        .append("recipeUseOperationId", 1))
                        .first();
        require(
                row != null
                        && row.getInteger("ownerId", 0) == player.getUid()
                        && row.getInteger("itemId", 0) == receipt.getSourceId(),
                "学习书原实例不存在或归属不符。");
        int count = row.getInteger("count", 0);
        boolean deleted = row.getBoolean("deleted", false);
        require(count >= 0 && deleted == (count == 0), "学习书保存状态无效。");
        require(
                source == null
                        ? deleted
                        : !deleted
                                && source.getOwnerId() == player.getUid()
                                && source.getItemId() == receipt.getSourceId()
                                && source.getCount() == count
                                && player.getInventory().getItemByGuid(source.getGuid()) == source
                                && player.getInventory()
                                                .getInventoryTab(ItemType.ITEM_MATERIAL)
                                                .getItemById(source.getItemId())
                                        == source
                                && ItemSaveStamp.same(
                                        source.getSaveStamp(), ItemSaveStamp.from(row)),
                "学习书库存与存档不一致，请重新登录后核对。");
        boolean paid = receipt.getOperationId().equals(row.get("recipeUseOperationId"));
        if (paid) require(ItemSaveStamp.from(row) != null, "学习扣费凭证缺少版本。");
        else
            require(
                    receipt.getState() == 1 && source != null && count == receipt.getSourceBefore(),
                    "无法证明学习扣费结果，停止自动重放。");
        return new Cost(source, paid, count);
    }

    List<String> inspect() {
        synchronized (player.getInventory()) {
            flush();
            var receipt = player.getPendingItemUse();
            var rows = new ArrayList<String>();
            rows.add("uid=" + player.getUid());
            if (receipt == null) return List.of("无待核对的物品使用记录。");
            rows.add(
                    "operation="
                            + receipt.getOperationId()
                            + " kind=recipe state="
                            + receipt.getState()
                            + " source="
                            + receipt.getSourceId()
                            + " objectId="
                            + receipt.getSourceObjectId()
                            + " target="
                            + receipt.getRecipeTargetId()
                            + " useOp="
                            + receipt.getOperation());
            try {
                validate(receipt);
                var saved = receipt(profile(player));
                require(
                        saved == null || JsonUtils.toJson(receipt).equals(JsonUtils.toJson(saved)),
                        "学习记录与存档不符。");
                var cost = cost(receipt);
                rows.add("count=" + cost.count() + " debitConfirmed=" + cost.paid());
                rows.add("resume 确认原计划；cancel 仅取消尚未扣费且未解锁的预留。不会再次扣除已确认的学习书。");
            } catch (RuntimeException mismatch) {
                rows.add("不能处理：" + mismatch.getMessage());
            }
            return List.copyOf(rows);
        }
    }

    void resolve(String operationId, String action) {
        synchronized (player.getInventory()) {
            require("resume".equals(action) || "cancel".equals(action), "学习记录请使用 resume 或 cancel。");
            flush();
            var receipt = player.getPendingItemUse();
            validate(receipt);
            require(Objects.equals(operationId, receipt.getOperationId()), "操作 ID 不匹配。");
            player.getSaveGuard().resolvePending(DatabaseManager.getGameDatastore());
            var storedProfile = profile(player);
            var saved = receipt(storedProfile);
            require(
                    saved == null || JsonUtils.toJson(receipt).equals(JsonUtils.toJson(saved)),
                    "学习记录与存档不符，请重新登录后核对。");
            for (var source : player.getInventory().getItems().values())
                if (source.getObjectId() != null
                        && source.getObjectId().toHexString().equals(receipt.getSourceObjectId()))
                    source.getSaveGuard().resolvePending(DatabaseManager.getGameDatastore());
            var cost = cost(receipt);
            if (action.equals("cancel")) {
                require(
                        receipt.getState() == 1
                                && !cost.paid()
                                && !RecipeItemUse.known(player, receipt)
                                && !savedKnown(player, receipt, storedProfile),
                        "只能取消未扣费且未解锁的学习预留。");
                cost.source()
                        .getSaveGuard()
                        .confirmStoredVersion(DatabaseManager.getGameDatastore());
                RecipeItemUse.clear(player, receipt, save);
                return;
            }
            if (saved == null) {
                if (receipt.getState() == 3
                        && cost.paid()
                        && savedKnown(player, receipt, storedProfile)) {
                    RecipeItemUse.clear(player, receipt, save);
                    RecipeItemUse.publish(player, receipt);
                    return;
                }
                require(receipt.getState() == 1 && !cost.paid(), "学习记录已清除但结果不能确认，请重新登录后核对。");
                // A failed initial intent write may have been retired by our own save guard.
                // Re-establish it conditionally before any new debit; foreign versions still fail.
                save.accept(player);
            }
            require(
                    RecipeItemUse.matchesSource(
                            GameData.getItemDataMap().get(receipt.getSourceId()), receipt),
                    "学习书资源已改变。");
            if (!cost.paid()) {
                cost.source()
                        .getSaveGuard()
                        .confirmStoredVersion(DatabaseManager.getGameDatastore());
                RecipeItemUse.debit(player, cost.source(), receipt, save);
            }
            if (receipt.getState() == 1) {
                receipt.paid();
                save.accept(player);
            }
            RecipeItemUse.finish(player, receipt, save);
        }
    }
}
