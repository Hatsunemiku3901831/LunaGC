package emu.grasscutter.game.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.city.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;
import java.util.function.Consumer;

/** Plan an entire offering before payment, then keep an auditable record until it is confirmed. */
final class StatueOffering {
    private StatueOffering() {}

    record Result(
            int retcode,
            int cityId,
            int level,
            int crystals,
            boolean leveled,
            List<Integer> forces) {}

    private record Definition(int item, int cost, int stamina, int[] rewards) {}

    private record Plan(
            int item,
            int consumed,
            int level,
            int crystals,
            int stamina,
            List<ItemParamData> rewards,
            List<Integer> forces) {}

    static Result offer(
            SotSManager manager,
            int area,
            int sceneId,
            int count,
            Runnable save,
            Consumer<List<ItemParamData>> grant,
            Consumer<GameItem> saveCost) {
        var player = manager.getPlayer();
        CityData city = manager.getCityByAreaId(area);
        if (city == null)
            return new Result(Retcode.RET_INVALID_CITY_ID_VALUE, 0, 1, 0, false, List.of());
        synchronized (player.getInventory()) {
            var scene = player.getScene();
            var first = GameData.getCityLevelupData(city.getCityId(), 1);
            int expectedScene =
                    city.getSceneId() > 0
                            ? city.getSceneId()
                            : first == null ? 0 : first.getSceneId();
            if (count <= 0
                    || scene == null
                    || scene.getId() != sceneId
                    || expectedScene != sceneId
                    || scene.getWorld() != player.getWorld()
                    || scene.getWorld().getHost() != player)
                return new Result(Retcode.RET_FAIL_VALUE, city.getCityId(), 1, 0, false, List.of());
            var info = manager.getCityInfo(city.getCityId());
            if (info.getPendingOffer() != null)
                return result(Retcode.RET_SVR_ERROR_VALUE, info, false, List.of());
            try {
                int max =
                        Math.max(
                                GameData.getStatuePromoteDataMap().values().stream()
                                        .filter(x -> x != null && x.getCityId() == city.getCityId())
                                        .mapToInt(StatuePromoteData::getLevel)
                                        .max()
                                        .orElse(0),
                                GameData.getCityLevelupDataMap().values().stream()
                                        .filter(x -> x != null && x.getCityId() == city.getCityId())
                                        .mapToInt(CityLevelupData::getLevel)
                                        .max()
                                        .orElse(0));
                if (max == 0
                        || info.getLevel() < 1
                        || info.getLevel() > max
                        || info.getNumCrystal() < 0)
                    return result(Retcode.RET_NOT_FOUND_CONFIG_VALUE, info, false, List.of());
                if (info.getLevel() == max)
                    return result(Retcode.RET_CITY_MAX_LEVEL_VALUE, info, false, List.of());
                var plan = plan(player, info, count, max);
                if (plan.consumed() == 0 && plan.level() == info.getLevel())
                    return result(Retcode.RET_ITEM_NOT_EXIST_VALUE, info, false, List.of());
                var inventory = player.getInventory();
                var source = inventory.getItemById(plan.item());
                int before = source == null ? 0 : source.getCount();
                if (plan.consumed() > 0
                        && (source == null
                                || source.getOwnerId() != player.getUid()
                                || before < plan.consumed()
                                || source.getItemData() == null
                                || source.getItemId() != plan.item()
                                || source.getItemData().getId() != plan.item()
                                || inventory.getItemByGuid(source.getGuid()) != source
                                || source.getItemData().getItemType() != ItemType.ITEM_MATERIAL
                                || source.getItemData().isUseOnGain()))
                    return result(Retcode.RET_ITEM_NOT_EXIST_VALUE, info, false, List.of());
                int freed =
                        source != null
                                        && plan.consumed() == before
                                        && before > 0
                                        && plan.rewards().stream()
                                                .noneMatch(x -> x.getId() == plan.item())
                                ? 1
                                : 0;
                int preflight =
                        plan.rewards().isEmpty()
                                ? 0
                                : InvestigationRewardDelivery.validate(
                                        player, plan.rewards(), freed);
                if (preflight != 0) return result(preflight, info, false, List.of());
                int staminaBefore = player.getProperty(PlayerProperty.PROP_MAX_STAMINA);
                int staminaAfter =
                        (int)
                                Math.min(
                                        PlayerProperty.PROP_MAX_STAMINA.getMax(),
                                        (long) staminaBefore + (long) plan.stamina() * 100);
                var receipt =
                        new StatueOfferReceipt(
                                plan.item(),
                                before,
                                plan.consumed(),
                                info.getLevel(),
                                info.getNumCrystal(),
                                plan.level(),
                                plan.crystals(),
                                plan.stamina(),
                                staminaBefore,
                                staminaAfter,
                                plan.rewards());
                info.setPendingOffer(receipt);
                save.run();
                if (plan.consumed() > 0) {
                    if (!inventory.removeItem(source, plan.consumed())
                            || before - source.getCount() != plan.consumed())
                        throw new IllegalStateException("Statue cost was not deducted exactly");
                    saveCost.accept(source);
                }
                receipt.paid();
                save.run();
                if (!plan.rewards().isEmpty()) grant.accept(plan.rewards());
                boolean leveled = plan.level() > info.getLevel();
                if (staminaAfter > staminaBefore
                        && (player.getProperty(PlayerProperty.PROP_MAX_STAMINA) != staminaBefore
                                || !player.setProperty(
                                        PlayerProperty.PROP_MAX_STAMINA, staminaAfter, true)
                                || player.getProperty(PlayerProperty.PROP_MAX_STAMINA)
                                        != staminaAfter))
                    throw new IllegalStateException("Statue stamina update was not confirmed");
                info.setLevel(plan.level());
                info.setNumCrystal(plan.crystals());
                receipt.applied();
                save.run();
                info.setPendingOffer(null);
                try {
                    save.run();
                } catch (RuntimeException uncertain) {
                    info.setPendingOffer(receipt);
                    throw uncertain;
                }
                return result(0, info, leveled, plan.forces());
            } catch (IllegalArgumentException malformed) {
                if (info.getPendingOffer() == null)
                    return result(Retcode.RET_NOT_FOUND_CONFIG_VALUE, info, false, List.of());
                return failed(player, info, malformed);
            } catch (RuntimeException uncertain) {
                return failed(player, info, uncertain);
            }
        }
    }

    private static Result failed(Player player, CityInfoData info, RuntimeException error) {
        Grasscutter.getLogger()
                .error(
                        "Statue offering needs reconciliation: UID {}, city {}, operation {}",
                        player.getUid(),
                        info.getCityId(),
                        info.getPendingOffer() == null
                                ? "none"
                                : info.getPendingOffer().getOperationId(),
                        error);
        return result(Retcode.RET_SVR_ERROR_VALUE, info, false, List.of());
    }

    private static Result result(
            int code, CityInfoData info, boolean leveled, List<Integer> forces) {
        return new Result(
                code, info.getCityId(), info.getLevel(), info.getNumCrystal(), leveled, forces);
    }

    private static Plan plan(Player player, CityInfoData info, int requested, int max) {
        int level = info.getLevel(),
                crystals = info.getNumCrystal(),
                remaining = requested,
                consumed = 0,
                stamina = 0,
                item = 0;
        var rewards = new LinkedHashMap<Integer, Long>();
        var forces = new LinkedHashSet<Integer>();
        while (remaining > 0 && level < max) {
            var definition = definition(info.getCityId(), level + 1);
            if (definition == null
                    || definition.item() <= 0
                    || definition.cost() <= 0
                    || definition.stamina() < 0
                    || crystals > definition.cost())
                throw new IllegalArgumentException("Invalid statue level definition");
            if (item == 0) item = definition.item();
            if (item != definition.item())
                throw new IllegalArgumentException("A statue offering changed cost item");
            int have = player.getInventory().getItemCountById(item) - consumed;
            int use =
                    Math.min(remaining, Math.min(definition.cost() - crystals, Math.max(0, have)));
            crystals += use;
            remaining -= use;
            consumed += use;
            if (crystals < definition.cost()) break;
            crystals -= definition.cost();
            level++;
            stamina = Math.addExact(stamina, definition.stamina());
            if (definition.rewards() != null)
                for (int id : definition.rewards()) {
                    var reward = GameData.getRewardDataMap().get(id);
                    if (reward == null || reward.getRewardItemList() == null)
                        throw new IllegalArgumentException("Missing statue reward definition");
                    var products = new ArrayList<>(reward.getRewardItemList());
                    products.addAll(reward.getAdditionalRewards());
                    for (var product : products) {
                        if (product == null || product.getId() <= 0 || product.getCount() <= 0)
                            throw new IllegalArgumentException("Invalid statue reward");
                        long total =
                                rewards.merge(
                                        product.getId(), (long) product.getCount(), Long::sum);
                        if (total > Integer.MAX_VALUE)
                            throw new IllegalArgumentException("Statue reward overflow");
                    }
                }
            var current = GameData.getCityLevelupData(info.getCityId(), level);
            if (current != null && current.getActionVec() != null)
                for (var action : current.getActionVec())
                    if (action != null
                            && "WORLD_AREA_ACTION_UNLOCK_FORCE".equals(action.getType())
                            && action.getParam1Vec() != null)
                        for (int force : action.getParam1Vec()) if (force > 0) forces.add(force);
        }
        return new Plan(
                item,
                consumed,
                level,
                crystals,
                stamina,
                rewards.entrySet().stream()
                        .map(e -> new ItemParamData(e.getKey(), e.getValue().intValue()))
                        .toList(),
                List.copyOf(forces));
    }

    private static Definition definition(int city, int level) {
        var old = GameData.getStatuePromoteData(city, level);
        if (old != null) {
            if (old.getCostItems() == null
                    || old.getCostItems().length != 1
                    || old.getCostItems()[0] == null)
                throw new IllegalArgumentException("Unsupported statue cost row");
            return new Definition(
                    old.getCostItems()[0].getId(),
                    old.getCostItems()[0].getCount(),
                    old.getStamina(),
                    old.getRewardIdList());
        }
        var current = GameData.getCityLevelupData(city, level);
        return current == null
                ? null
                : new Definition(
                        current.getCostItemId(),
                        current.getCostItemCount(),
                        current.getStaminaGain(),
                        current.getRewardId() > 0 ? new int[] {current.getRewardId()} : new int[0]);
    }
}
