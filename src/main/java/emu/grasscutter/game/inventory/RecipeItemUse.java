package emu.grasscutter.game.inventory;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.function.*;

/** Persist a learning decision and its exact book debit before publishing the learned flag. */
public final class RecipeItemUse {
    private final Player player;
    private final GameItem source;
    private final UseItemParams params;
    private final Consumer<Object> save;
    private ItemUseReceipt receipt;

    public RecipeItemUse(
            Player player, GameItem source, UseItemParams params, Consumer<Object> save) {
        this.player = player;
        this.source = source;
        this.params = params;
        this.save = save;
    }

    static boolean learning(ItemUseOp op) {
        return switch (op) {
            case ITEM_USE_UNLOCK_COMBINE,
                    ITEM_USE_UNLOCK_FORGE,
                    ITEM_USE_UNLOCK_COOK_RECIPE,
                    ITEM_USE_UNLOCK_FURNITURE_FORMULA,
                    ITEM_USE_UNLOCK_FURNITURE_SUITE,
                    ITEM_USE_UNLOCK_HOME_BGM -> true;
            default -> false;
        };
    }

    public static boolean hasLearningAction(ItemData data) {
        return data != null
                && data.getItemUseActions() != null
                && data.getItemUseActions().stream()
                        .anyMatch(a -> a != null && learning(a.getItemUseOp()));
    }

    static boolean matchesSource(ItemData data, ItemUseReceipt receipt) {
        if (data == null
                || data.getId() != receipt.getSourceId()
                || data.isUseOnGain()
                || data.getItemType() != ItemType.ITEM_MATERIAL
                || data.getItemUseActions() == null
                || data.getItemUseActions().size() != 1) return false;
        var action = data.getItemUseActions().get(0);
        return action instanceof ItemUseInt number
                && learning(action.getItemUseOp())
                && action.getItemUseOp().getValue() == receipt.getOperation()
                && number.getI() == receipt.getRecipeTargetId()
                && number.getI() > 0;
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    static ItemUseOp operation(ItemUseReceipt receipt) {
        var op = ItemUseOp.getTypeByValue(receipt.getOperation());
        require(learning(op), "学习类型无效。");
        return op;
    }

    static void home(Player player, ItemUseReceipt receipt) {
        if (operation(receipt) != ItemUseOp.ITEM_USE_UNLOCK_HOME_BGM) {
            require(receipt.getRecipeHomeId() == null, "非家园音乐记录不应带有家园 ID。");
            return;
        }
        var home = player.getHome();
        require(
                home != null
                        && home.getOwnerUid() == player.getUid()
                        && home.getId() != null
                        && home.getId().equals(receipt.getRecipeHomeId()),
                "家园音乐记录的主人或持久 ID 不符。");
    }

    static boolean known(Player player, ItemUseReceipt receipt) {
        int id = receipt.getRecipeTargetId();
        return switch (operation(receipt)) {
            case ITEM_USE_UNLOCK_COMBINE -> player.getUnlockedCombines().contains(id);
            case ITEM_USE_UNLOCK_FORGE -> player.getUnlockedForgingBlueprints().contains(id);
            case ITEM_USE_UNLOCK_COOK_RECIPE -> player.getUnlockedRecipies().containsKey(id);
            case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> player.getUnlockedFurniture().contains(id);
            case ITEM_USE_UNLOCK_FURNITURE_SUITE -> player.getUnlockedFurnitureSuite().contains(id);
            case ITEM_USE_UNLOCK_HOME_BGM -> player.getHome().getUnlockedHomeBgmList().contains(id);
            default -> false;
        };
    }

    private boolean pay() {
        require(player.getPendingItemUse() == null && receipt == null, "已有待核对物品使用记录。");
        var data = source.getItemData();
        var actions = data == null ? null : data.getItemUseActions();
        var tab = player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
        if (data == null
                || data.getId() != source.getItemId()
                || data.isUseOnGain()
                || data.getItemType() != ItemType.ITEM_MATERIAL
                || tab == null
                || params.count != 1
                || params.optionId != 0
                || !params.optionIds.isEmpty()
                || params.itemUseTarget != ItemUseTarget.ITEM_USE_TARGET_NONE
                || source.getOwnerId() != player.getUid()
                || source.getCount() <= 0
                || player.getInventory().getItemByGuid(source.getGuid()) != source
                || tab.getItemById(source.getItemId()) != source
                || actions == null
                || actions.size() != 1
                || !(actions.get(0) instanceof ItemUseInt action)
                || !learning(action.getItemUseOp())
                || !RecipeUnlocks.canUse(params, action.getItemUseOp(), action.getI()))
            return false;
        synchronized (source) {
            // A newly given book may still have its first ordinary save queued.
            if (source.getSaveStamp() == null) save.accept(source);
            String homeId = null;
            if (action.getItemUseOp() == ItemUseOp.ITEM_USE_UNLOCK_HOME_BGM) {
                var home = player.getHome();
                require(home != null && home.getOwnerUid() == player.getUid(), "只能学习自己的家园音乐。");
                if (home.getId() == null) save.accept(home);
                homeId = home.getId();
                require(homeId != null, "家园尚未持久化。");
            }
            receipt =
                    ItemUseReceipt.recipe(
                            source, action.getItemUseOp().getValue(), action.getI(), homeId);
            player.setPendingItemUse(receipt);
            save.accept(player);
            debit(player, source, receipt, save);
        }
        receipt.paid();
        save.accept(player);
        return true;
    }

    static void debit(
            Player player, GameItem source, ItemUseReceipt receipt, Consumer<Object> save) {
        synchronized (source) {
            source.getSaveGuard().authorizeRecipeDebit(player, receipt);
            require(
                    player.getInventory().removeItem(source, 1)
                            && source.getCount() == receipt.getSourceBefore() - 1,
                    "学习书没有确认扣除，保留记录。");
            save.accept(source);
            require(
                    receipt.getOperationId().equals(source.getRecipeUseOperationId()),
                    "学习书扣除凭证未确认。");
        }
    }

    static void clear(Player player, ItemUseReceipt receipt, Consumer<Object> save) {
        player.setPendingItemUse(null);
        try {
            save.accept(player);
            require(
                    RecipeUseRecovery.receipt(RecipeUseRecovery.profile(player)) == null,
                    "学习记录清除尚未确认。");
        } catch (RuntimeException failure) {
            player.setPendingItemUse(receipt);
            throw failure;
        }
    }

    static void finish(Player player, ItemUseReceipt receipt, Consumer<Object> save) {
        home(player, receipt);
        require(
                matchesSource(GameData.getItemDataMap().get(receipt.getSourceId()), receipt),
                "学习书资源已改变。");
        int id = receipt.getRecipeTargetId();
        var op = operation(receipt);
        require(
                known(player, receipt) || RecipeUnlocks.canUnlock(player, op, id),
                "配方资源缺失或已成为默认配方。");
        // These six native learning actions change only their corresponding unlock collection.
        // Send their normal notifications after the acknowledged target and cleared receipt.
        switch (op) {
            case ITEM_USE_UNLOCK_COMBINE -> player.getUnlockedCombines().add(id);
            case ITEM_USE_UNLOCK_FORGE -> player.getUnlockedForgingBlueprints().add(id);
            case ITEM_USE_UNLOCK_COOK_RECIPE -> player.getUnlockedRecipies().putIfAbsent(id, 0);
            case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> player.getUnlockedFurniture().add(id);
            case ITEM_USE_UNLOCK_FURNITURE_SUITE -> player.getUnlockedFurnitureSuite().add(id);
            case ITEM_USE_UNLOCK_HOME_BGM -> player.getHome().getUnlockedHomeBgmList().add(id);
            default -> throw new IllegalStateException("未知学习类型。");
        }
        save.accept(op == ItemUseOp.ITEM_USE_UNLOCK_HOME_BGM ? player.getHome() : player);
        require(
                RecipeUseRecovery.savedKnown(player, receipt, RecipeUseRecovery.profile(player)),
                "学习解锁尚未确认保存。");
        if (receipt.getState() == 2) {
            receipt.completed();
            save.accept(player);
        }
        clear(player, receipt, save);
        publish(player, receipt);
    }

    static void publish(Player player, ItemUseReceipt receipt) {
        int id = receipt.getRecipeTargetId();
        var op = operation(receipt);
        try {
            switch (op) {
                case ITEM_USE_UNLOCK_COMBINE -> player.sendPacket(
                        new PacketCombineFormulaDataNotify(id));
                case ITEM_USE_UNLOCK_FORGE -> player.sendPacket(
                        new PacketForgeFormulaDataNotify(id));
                case ITEM_USE_UNLOCK_COOK_RECIPE -> player.sendPacket(
                        new PacketCookRecipeDataNotify(id));
                case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> player.getFurnitureManager()
                        .notifyUnlockFurniture();
                case ITEM_USE_UNLOCK_FURNITURE_SUITE -> player.getFurnitureManager()
                        .notifyUnlockFurnitureSuite();
                case ITEM_USE_UNLOCK_HOME_BGM -> {
                    player.sendPacket(new PacketHomeNewUnlockedBgmIdListNotify(id));
                    player.sendPacket(new PacketHomeAllUnlockedBgmIdListNotify(player));
                }
                default -> {}
            }
        } catch (RuntimeException unavailable) {
            Grasscutter.getLogger()
                    .debug(
                            "Could not publish learned recipe for UID {}",
                            player.getUid(),
                            unavailable);
        }
    }

    public boolean use(Function<BooleanSupplier, Boolean> execute) {
        synchronized (player.getInventory()) {
            if (player.getPendingItemUse() != null) return false;
            try {
                if (!execute.apply(this::pay)) return false;
                require(receipt != null && receipt.getState() == 2, "学习付款尚未确认。");
                finish(player, receipt, save);
                return true;
            } catch (RuntimeException uncertain) {
                Grasscutter.getLogger()
                        .error(
                                "Recipe learning needs reconciliation: UID {}, item {}",
                                player.getUid(),
                                source.getItemId(),
                                uncertain);
                return false;
            }
        }
    }
}
