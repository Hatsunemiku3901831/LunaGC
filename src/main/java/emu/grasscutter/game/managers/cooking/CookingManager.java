package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.RecipeUnlocks;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass;
import emu.grasscutter.net.proto.PlayerCookArgsReqOuterClass.PlayerCookArgsReq;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;

import io.netty.util.internal.ThreadLocalRandom;

import java.util.*;

public class CookingManager extends BasePlayerManager {
    private static final int MANUAL_PERFECT_COOK_QUALITY = 3;
    private static Set<Integer> defaultUnlockedRecipies;

    public CookingManager(Player player) {
        super(player);
    }

    public static void initialize() {
        // The GameServer constructor calls this before ResourceLoader.loadAll() runs, so the
        // excel map is still empty at that point and nothing would be collected here.  The set
        // is therefore rebuilt on demand until it actually holds recipe ids -- see
        // getDefaultUnlockedRecipies().
        computeDefaultUnlockedRecipies();
    }

    /**
     * Recipe ids every account starts with.
     *
     * <p>Recomputed on first use (and again while it is still empty) because the game data is
     * loaded after the {@code GameServer} constructor runs {@link #initialize()}, so an eager scan
     * there sees an empty {@link GameData#getCookRecipeDataMap()} and every player would otherwise
     * begin with an empty recipe list -- the client then shows no recipes at all.
     */
    public static synchronized Set<Integer> getDefaultUnlockedRecipies() {
        if (defaultUnlockedRecipies == null || defaultUnlockedRecipies.isEmpty()) {
            computeDefaultUnlockedRecipies();
        }
        return defaultUnlockedRecipies;
    }

    private static synchronized void computeDefaultUnlockedRecipies() {
        var map = GameData.getCookRecipeDataMap();
        if (map == null || map.isEmpty()) {
            // Nothing loaded yet; hand back an empty set rather than null (callers iterate it
            // during login) and let the isEmpty() check above retry on the next call.
            defaultUnlockedRecipies = new HashSet<>();
            return;
        }
        defaultUnlockedRecipies = defaultUnlockedRecipeIds(map.values());
        Grasscutter.getLogger()
                .info(
                        "Loaded {} default unlocked cooking recipes ({} recipes known).",
                        defaultUnlockedRecipies.size(),
                        map.size());
    }

    /** Select the ids of the recipes flagged default-unlocked in the excel data. */
    public static Set<Integer> defaultUnlockedRecipeIds(
            Collection<emu.grasscutter.data.excels.CookRecipeData> recipes) {
        var ids = new HashSet<Integer>();
        for (var recipe : recipes) {
            if (recipe.isDefaultUnlocked()) {
                ids.add(recipe.getId());
            }
        }
        return ids;
    }

    /********************
     * Unlocking for recipies.
     ********************/
    public boolean unlockRecipe(int id) {
        if (!RecipeUnlocks.canUnlock(player, ItemUseOp.ITEM_USE_UNLOCK_COOK_RECIPE, id))
            return false;
        if (this.player.getUnlockedRecipies().containsKey(id)) {
            return false; // Recipe already unlocked
        }
        // Tell the client that this blueprint is now unlocked and add the unlocked item to the
        // player.
        this.player.getUnlockedRecipies().put(id, 0);
        this.player.sendPacket(new PacketCookRecipeDataNotify(id));

        return true;
    }

    /********************
     * Perform cooking.
     ********************/
    protected double getSpecialtyChance(ItemData cookedItem) {
        // Chances taken from the Wiki.
        return switch (cookedItem.getRankLevel()) {
            case 1 -> 0.25;
            case 2 -> 0.2;
            case 3 -> 0.15;
            default -> 0;
        };
    }

    protected double nextCookingRoll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** Hold both monitors through the durable receipt and actual ingredient/dish writes. */
    public synchronized void handlePlayerCookReq(PlayerCookReq req) {
        synchronized (player.getInventory()) {
            if (player.getPendingCook() != null) {
                player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_SVR_ERROR));
                return;
            }
            int recipeId = req.getRecipeId(), count = req.getKLACBPCPCMJ();
            int quality = req.getJJPABEHGMCH(), assistant = req.getAssistAvatar();
            var recipe = GameData.getCookRecipeDataMap().get(recipeId);
            if (recipe == null
                    || recipe.getId() != recipeId
                    || count < 1
                    || count > 99
                    || quality < 0
                    || quality > 3
                    || quality != 0 && count != 1
                    || recipe.getMaxProficiency() < 0
                    || !recipe.isDefaultUnlocked()
                            && !player.getUnlockedRecipies().containsKey(recipeId)
                    || assistant < 0
                    || assistant > 0 && !player.getAvatars().hasAvatar(assistant)
                    || !CookBonuses.canCook(player, assistant)) {
                player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
                return;
            }
            int proficiency = player.getUnlockedRecipies().getOrDefault(recipeId, 0);
            if (proficiency < 0
                    || proficiency > recipe.getMaxProficiency()
                    || quality == 0 && proficiency != recipe.getMaxProficiency()) {
                player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
                return;
            }
            List<ItemParamData> costs, products, primaryProducts, extraProducts;
            try {
                costs = CookDelivery.costs(recipe.getInputVec(), count);
                if (costs.stream()
                        .anyMatch(
                                cost ->
                                        player.getInventory().getItemCountById(cost.getId())
                                                < cost.getCount())) {
                    player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_ITEM_COUNT_NOT_ENOUGH));
                    return;
                }
                var slots = recipe.getQualityOutputVec();
                if (slots == null || slots.size() != 3)
                    throw new IllegalArgumentException("Invalid cooking tiers");
                var output = slots.get(quality == 0 ? 2 : quality - 1);
                if (output == null || output.getId() <= 0 || output.getCount() <= 0)
                    output =
                            slots.stream()
                                    .filter(
                                            row ->
                                                    row != null
                                                            && row.getId() > 0
                                                            && row.getCount() > 0)
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Cooking has no filled tier"));
                var data = GameData.getItemDataMap().get(output.getId());
                if (data == null
                        || data.getId() != output.getId()
                        || data.isUseOnGain()
                        || data.getItemType() != ItemType.ITEM_MATERIAL)
                    throw new IllegalArgumentException("Missing cooking output");
                var amounts = new LinkedHashMap<Integer, Integer>();
                int specialties = 0;
                var bonus = GameData.getCookBonusDataMap().get(assistant);
                if (bonus != null
                        && bonus.getAvatarId() == assistant
                        && bonus.getRecipeId() == recipeId) {
                    if (bonus.getParamVec() == null || bonus.getParamVec().length == 0)
                        throw new IllegalArgumentException("Missing cooking specialty");
                    var replacement = GameData.getItemDataMap().get(bonus.getReplacementItemId());
                    if (replacement == null
                            || replacement.getId() != bonus.getReplacementItemId()
                            || replacement.isUseOnGain()
                            || replacement.getItemType() != ItemType.ITEM_MATERIAL)
                        throw new IllegalArgumentException("Invalid cooking specialty");
                    double chance = getSpecialtyChance(data);
                    if (!Double.isFinite(chance) || chance < 0 || chance > 1)
                        throw new IllegalArgumentException("Invalid specialty chance");
                    for (int i = 0; i < count; i++)
                        if (ThreadLocalRandom.current().nextDouble() < chance) specialties++;
                    if (specialties > 0)
                        amounts.merge(
                                replacement.getId(),
                                Math.multiplyExact(output.getCount(), specialties),
                                Math::addExact);
                }
                if (count > specialties)
                    amounts.merge(
                            data.getId(),
                            Math.multiplyExact(output.getCount(), count - specialties),
                            Math::addExact);
                primaryProducts =
                        amounts.entrySet().stream()
                                .map(e -> new ItemParamData(e.getKey(), e.getValue()))
                                .toList();
                var extras = new LinkedHashMap<Integer, Integer>();
                for (var extra : CookBonuses.extras(player, assistant, recipe, quality)) {
                    var target = slots.get(extra.quality() - 1);
                    if (target == null || target.getId() <= 0 || target.getCount() <= 0)
                        throw new IllegalArgumentException("Missing cooking passive output");
                    var targetData = GameData.getItemDataMap().get(target.getId());
                    if (targetData == null
                            || targetData.getId() != target.getId()
                            || targetData.getItemType() != ItemType.ITEM_MATERIAL
                            || targetData.isUseOnGain())
                        throw new IllegalArgumentException("Invalid cooking passive output");
                    int extraUnits = 0;
                    for (int i = 0; i < count; i++)
                        if (nextCookingRoll() < extra.chance() / 10000.0)
                            extraUnits = Math.addExact(extraUnits, extra.count());
                    if (extraUnits > 0)
                        extras.merge(
                                target.getId(),
                                Math.multiplyExact(target.getCount(), extraUnits),
                                Math::addExact);
                }
                extraProducts =
                        extras.entrySet().stream()
                                .map(e -> new ItemParamData(e.getKey(), e.getValue()))
                                .toList();
                extras.forEach((id, amount) -> amounts.merge(id, amount, Math::addExact));
                products =
                        amounts.entrySet().stream()
                                .map(e -> new ItemParamData(e.getKey(), e.getValue()))
                                .toList();
                int validation = InvestigationRewardDelivery.validate(player, products);
                if (validation != 0) {
                    player.sendPacket(new PacketPlayerCookRsp(Retcode.forNumber(validation)));
                    return;
                }
            } catch (RuntimeException invalid) {
                player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_NOT_FOUND_CONFIG));
                return;
            }
            int nextProficiency =
                    quality == MANUAL_PERFECT_COOK_QUALITY
                            ? (int) Math.min((long) proficiency + 1, recipe.getMaxProficiency())
                            : proficiency;
            var receipt =
                    new CookReceipt(
                            recipeId,
                            count,
                            quality,
                            assistant,
                            nextProficiency,
                            costs,
                            products,
                            extraProducts);
            player.setPendingCook(receipt);
            try {
                DatabaseHelper.saveGameSync(player);
                CookDelivery.pay(player, receipt);
                CookDelivery.grant(player, products);
                player.getUnlockedRecipies().put(recipeId, nextProficiency);
                player.getBattlePassManager()
                        .triggerMission(WatcherTriggerType.TRIGGER_DO_COOK, 1, count);
                receipt.delivered();
                DatabaseHelper.saveGameSync(player);
                player.setPendingCook(null);
                DatabaseHelper.saveGameSync(player);
                player.sendPacket(
                        new PacketPlayerCookRsp(
                                primaryProducts,
                                extraProducts,
                                quality == 0 ? MANUAL_PERFECT_COOK_QUALITY : quality,
                                count,
                                recipeId,
                                nextProficiency));
            } catch (RuntimeException failure) {
                player.setPendingCook(receipt);
                Grasscutter.getLogger()
                        .error(
                                "Cooking needs reconciliation: UID {}, recipe {}, count {}",
                                player.getUid(),
                                recipeId,
                                count,
                                failure);
                player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_SVR_ERROR));
            }
        }
    }

    /********************
     * Cooking arguments.
     ********************/
    public void handleCookArgsReq(PlayerCookArgsReq req) {
        this.player.sendPacket(new PacketPlayerCookArgsRsp(req.getRecipeId()));
    }

    /********************
     * Notify unlocked recipies.
     ********************/
    private void addDefaultUnlocked() {
        // Get recipies that are already unlocked.
        var unlockedRecipies = this.player.getUnlockedRecipies();

        // Get recipies that should be unlocked by default but aren't.
        var additionalRecipies = new HashSet<>(getDefaultUnlockedRecipies());
        additionalRecipies.removeAll(unlockedRecipies.keySet());

        // Add them to the player.
        for (int id : additionalRecipies) {
            unlockedRecipies.put(id, 0);
        }
    }

    public void sendCookDataNotify() {
        // Default unlocked recipes to player if they don't have them yet.
        this.addDefaultUnlocked();

        // Get unlocked recipes.
        var unlockedRecipes = this.player.getUnlockedRecipies();

        // Construct CookRecipeData protos.
        List<CookRecipeDataOuterClass.CookRecipeData> data = new ArrayList<>();
        unlockedRecipes.forEach(
                (recipeId, proficiency) ->
                        data.add(
                                CookRecipeDataOuterClass.CookRecipeData.newBuilder()
                                        .setRecipeId(recipeId)
                                        .setProficiency(proficiency)
                                        .build()));

        // Send packet.
        this.player.sendPacket(new PacketCookDataNotify(data));
    }
}
