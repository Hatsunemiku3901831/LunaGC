package emu.grasscutter.game.inventory;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;

import java.util.function.*;

/** A food effect can run only after its exact owned stack debit is acknowledged. */
public final class FoodItemUse {
    private final Player player;
    private final GameItem source;
    private final UseItemParams params;
    private final Consumer<Object> save;
    private ItemUseReceipt receipt;

    public static boolean isFood(emu.grasscutter.data.excels.ItemData data) {
        if (data == null || data.getItemType() != ItemType.ITEM_MATERIAL) return false;
        return switch (data.getMaterialType()) {
            case MATERIAL_FOOD, MATERIAL_NOTICE_ADD_HP, MATERIAL_SPICE_FOOD -> true;
            default -> false;
        };
    }

    public FoodItemUse(
            Player player, GameItem source, UseItemParams params, Consumer<Object> save) {
        this.player = player;
        this.source = source;
        this.params = params;
        this.save = save;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private void clear() {
        var before = receipt;
        player.setPendingItemUse(null);
        try {
            save.accept(player);
        } catch (RuntimeException failure) {
            player.setPendingItemUse(before);
            throw failure;
        }
    }

    private boolean pay() {
        var inventory = player.getInventory();
        require(
                player.getPendingItemUse() == null && receipt == null,
                "Food intent already exists");
        if (source.getOwnerId() != player.getUid()
                || source.getCount() < params.count
                || inventory.getItemByGuid(source.getGuid()) != source
                || source.getItemData() == null
                || source.getItemData().getId() != source.getItemId()) return false;
        receipt = ItemUseReceipt.food(source, params.count, params.targetAvatar);
        player.setPendingItemUse(receipt);
        save.accept(player);
        if (!inventory.reserveForUse(source, params.count)) {
            require(
                    source.getCount() == receipt.getSourceBefore()
                            && inventory.getItemByGuid(source.getGuid()) == source,
                    "Food payment rejected with an uncertain stack");
            clear();
            receipt = null;
            return false;
        }
        require(
                receipt.getSourceBefore() - source.getCount() == params.count
                        && (source.getCount() > 0
                                ? inventory.getItemByGuid(source.getGuid()) == source
                                : inventory.getItemByGuid(source.getGuid()) == null),
                "Food payment was not deducted exactly");
        save.accept(source);
        receipt.paid();
        save.accept(player);
        // State 4 is durable before the first action: a crash cannot make an uncertain effect
        // retryable.
        receipt.foodState(2, 4);
        save.accept(player);
        return true;
    }

    private void saveAvatars() {
        if (params.targetAvatar != null) saveAvatar(params.targetAvatar);
        else
            for (var entity : player.getTeamManager().getActiveTeamSnapshot())
                saveAvatar(entity.getAvatar());
    }

    private void saveAvatar(emu.grasscutter.game.avatar.Avatar avatar) {
        // Match DatabaseHelper.saveAvatar: trial instances must never become durable owned avatars.
        if (avatar.getTrialAvatarId() == 0) save.accept(avatar);
    }

    public boolean use(Function<BooleanSupplier, Boolean> execute) {
        synchronized (player.getInventory()) {
            if (player.getPendingItemUse() != null) return false;
            var actions = source.getItemData().getItemUseActions();
            if (actions == null || actions.isEmpty()) return false;
            try {
                boolean used = execute.apply(this::pay);
                if (receipt == null) return false; // Target, cooldown, satiation or debit rejected.
                require(
                        player.getPendingItemUse() == receipt && receipt.getState() == 4,
                        "Food intent changed during effects");
                if (!used) {
                    // All actions returned normally and none claimed an effect. A throwing action
                    // is never refunded here.
                    require(!params.anyActionApplied, "Food action outcomes disagree");
                    receipt.foodState(4, 5);
                    save.accept(player);
                    require(
                            player.getInventory()
                                    .restoreUseReservation(
                                            source, receipt.getSourceBefore(), params.count),
                            "Food refund identity changed");
                    save.accept(source);
                    saveAvatars();
                    receipt.foodState(5, 6);
                    save.accept(player);
                    clear();
                    return false;
                }
                for (var action : actions) action.postUseItem(params);
                saveAvatars();
                receipt.foodState(4, 3);
                save.accept(player);
                player.getInventory()
                        .announceUseReservation(source, receipt.getSourceBefore(), params.count);
                clear();
                return true;
            } catch (RuntimeException uncertain) {
                if (receipt != null && receipt.getState() == 4) {
                    // Keep observed partial effects with the blocking record; never execute an
                    // action again here.
                    try {
                        saveAvatars();
                        save.accept(player);
                    } catch (RuntimeException unavailable) {
                        if (unavailable != uncertain) uncertain.addSuppressed(unavailable);
                    }
                }
                Grasscutter.getLogger()
                        .error(
                                "Food use needs reconciliation: UID {}, item {}, operation {},"
                                        + " state {}",
                                player.getUid(),
                                source.getItemId(),
                                receipt == null ? "none" : receipt.getOperationId(),
                                receipt == null ? 0 : receipt.getState(),
                                uncertain);
                return false;
            }
        }
    }
}
