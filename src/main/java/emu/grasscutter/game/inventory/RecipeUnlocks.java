package emu.grasscutter.game.inventory;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.props.ItemUseOp;

/** Shared eligibility checks for learning one real recipe or home blueprint once. */
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
            case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> {
                var furniture = GameData.getItemDataMap().get(id);
                yield furniture != null
                        && furniture.getId() == id
                        && furniture.getItemType() == ItemType.ITEM_FURNITURE
                        && !player.getUnlockedFurniture().contains(id)
                        && GameData.getFurnitureMakeConfigDataMap().values().stream()
                                .anyMatch(
                                        recipe ->
                                                recipe != null
                                                        && recipe.getId() > 0
                                                        && recipe.getFurnitureItemID() == id
                                                        && recipe.getCount() > 0
                                                        && GameData.getFurnitureMakeConfigDataMap()
                                                                        .get(recipe.getId())
                                                                == recipe);
            }
            case ITEM_USE_UNLOCK_FURNITURE_SUITE -> {
                var suite = GameData.getFurnitureSuiteDataMap().get(id);
                yield suite != null
                        && suite.getId() == id
                        && suite.getJsonName() != null
                        && !suite.getJsonName().isBlank()
                        && !player.getUnlockedFurnitureSuite().contains(id);
            }
            case ITEM_USE_UNLOCK_HOME_BGM -> canUnlockHomeBgm(player.getHome(), id);
            default -> false;
        };
    }

    public static boolean canUnlockHomeBgm(GameHome home, int id) {
        if (home == null || id <= 0) return false;
        var bgm = GameData.getHomeWorldBgmDataMap().get(id);
        return bgm != null
                && bgm.getId() == id
                && !bgm.isDefaultUnlock()
                && !home.getUnlockedHomeBgmList().contains(id);
    }
}
