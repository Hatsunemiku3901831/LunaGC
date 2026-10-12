package emu.grasscutter.game.inventory;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.props.ItemUseOp;

/** Shared eligibility checks for learning one real, non-default recipe once. */
public final class RecipeUnlocks {
    private RecipeUnlocks() {}

    public static boolean canUse(UseItemParams params, ItemUseOp operation, int id) {
        return params != null && params.count == 1 && canUnlock(params.player, operation, id);
    }

    public static boolean canUnlock(Player player, ItemUseOp operation, int id) {
        if (player == null || operation == null || id <= 0) return false;
        return switch (operation) {
            case ITEM_USE_UNLOCK_COMBINE -> {
                var recipe = GameData.getCombineDataMap().get(id);
                yield recipe != null
                        && recipe.getId() == id
                        && !recipe.isDefaultShow()
                        && !player.getUnlockedCombines().contains(id);
            }
            case ITEM_USE_UNLOCK_FORGE -> {
                var recipe = GameData.getForgeDataMap().get(id);
                yield recipe != null
                        && recipe.getId() == id
                        && !recipe.isDefaultShow()
                        && !player.getUnlockedForgingBlueprints().contains(id);
            }
            case ITEM_USE_UNLOCK_COOK_RECIPE -> {
                var recipe = GameData.getCookRecipeDataMap().get(id);
                yield recipe != null
                        && recipe.getId() == id
                        && !recipe.isDefaultUnlocked()
                        && !player.getUnlockedRecipies().containsKey(id);
            }
            default -> false;
        };
    }
}
