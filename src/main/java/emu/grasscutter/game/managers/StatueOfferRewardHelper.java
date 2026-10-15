package emu.grasscutter.game.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;

import java.util.*;
import java.util.function.IntFunction;

/** AstaPS reward policy, rebuilt for each offering from current resources and configuration. */
public final class StatueOfferRewardHelper {
    private StatueOfferRewardHelper() {}

    private static final Set<Integer> PROTECTED =
            Set.of(
                    100533, 912, 913, 914, 915, 916, 917, 918, 107008, 107018, 107025, 107027,
                    107031, 107037);

    /** Freeze the effective policy and, when redistributed, the whole city's reward schedule. */
    public static IntFunction<List<ItemParamData>> forCity(int cityId) {
        var options = Grasscutter.getConfig().server.game.gameOptions.statueRewards;
        var multipliers = new TreeMap<Integer, Integer>();
        if (options != null && options.referenceMultipliers) multipliers.putAll(reference(cityId));
        if (options != null
                && options.cityItemMultipliers != null
                && options.cityItemMultipliers.containsKey(cityId)) {
            var overrides = options.cityItemMultipliers.get(cityId);
            if (overrides == null) throw new IllegalArgumentException("Null statue multiplier map");
            for (var entry : overrides.entrySet()) {
                if (entry.getKey() == null
                        || entry.getKey() <= 0
                        || entry.getValue() == null
                        || entry.getValue() <= 0)
                    throw new IllegalArgumentException(
                            "Statue multipliers must be positive integers");
                multipliers.put(entry.getKey(), entry.getValue());
            }
        }
        multipliers.entrySet().removeIf(x -> x.getValue() == 1 || PROTECTED.contains(x.getKey()));
        if (multipliers.isEmpty()) return level -> original(cityId, level);
        if (!options.evenlyDistribute)
            return level -> {
                var items = new TreeMap<Integer, Long>();
                for (var item : original(cityId, level))
                    items.put(
                            item.getId(),
                            multiply(item.getCount(), multipliers.getOrDefault(item.getId(), 1)));
                return items(items);
            };

        var levels = new TreeSet<Integer>();
        for (var row : GameData.getStatuePromoteDataMap().values())
            if (row != null && row.getCityId() == cityId && row.getLevel() > 1)
                levels.add(row.getLevel());
        for (var row : GameData.getCityLevelupDataMap().values())
            if (row != null && row.getCityId() == cityId && row.getLevel() > 1)
                levels.add(row.getLevel());
        if (levels.isEmpty()) return level -> List.of();
        var originals = new TreeMap<Integer, List<ItemParamData>>();
        var totals = new TreeMap<Integer, Long>();
        for (int level : levels) {
            var rewards = original(cityId, level);
            originals.put(level, rewards);
            for (var reward : rewards)
                if (multipliers.containsKey(reward.getId()))
                    totals.merge(
                            reward.getId(), (long) reward.getCount(), StatueOfferRewardHelper::add);
        }
        totals.replaceAll((item, total) -> multiply(total, multipliers.get(item)));
        var distributed = new HashMap<Integer, List<ItemParamData>>();
        int index = 0;
        for (int level : levels) {
            var items = new TreeMap<Integer, Long>();
            for (var reward : originals.get(level))
                items.put(reward.getId(), (long) reward.getCount());
            for (var reward : totals.entrySet()) {
                long amount =
                        reward.getValue() / levels.size()
                                + (index < reward.getValue() % levels.size() ? 1 : 0);
                if (amount > 0) items.put(reward.getKey(), amount);
                else items.remove(reward.getKey());
            }
            distributed.put(level, items(items));
            index++;
        }
        var snapshot = Map.copyOf(distributed);
        return level -> snapshot.getOrDefault(level, List.of());
    }

    private static List<ItemParamData> original(int city, int level) {
        var old = GameData.getStatuePromoteData(city, level);
        if (old != null && (old.getCityId() != city || old.getLevel() != level)) old = null;
        var current = GameData.getCityLevelupData(city, level);
        if (current != null && (current.getCityId() != city || current.getLevel() != level))
            current = null;
        int[] rewards =
                old != null
                        ? old.getRewardIdList()
                        : current != null && current.getRewardId() > 0
                                ? new int[] {current.getRewardId()}
                                : new int[0];
        var items = new TreeMap<Integer, Long>();
        if (rewards != null)
            for (int id : rewards) {
                var data = GameData.getRewardDataMap().get(id);
                if (id <= 0 || data == null || data.getRewardItemList() == null)
                    throw new IllegalArgumentException("Missing statue reward definition: " + id);
                var products = new ArrayList<>(data.getRewardItemList());
                products.addAll(data.getAdditionalRewards());
                for (var product : products) {
                    if (product == null || product.getId() <= 0 || product.getCount() <= 0)
                        throw new IllegalArgumentException("Invalid statue reward");
                    items.merge(
                            product.getId(),
                            (long) product.getCount(),
                            StatueOfferRewardHelper::add);
                }
            }
        return items(items);
    }

    private static List<ItemParamData> items(Map<Integer, Long> amounts) {
        var result = new ArrayList<ItemParamData>();
        for (var entry : amounts.entrySet()) {
            long count = entry.getValue();
            if (count <= 0 || count > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Statue reward quantity overflow");
            result.add(new ItemParamData(entry.getKey(), (int) count));
        }
        return List.copyOf(result);
    }

    private static long add(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Statue reward total overflow", overflow);
        }
    }

    private static long multiply(long value, int multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Statue reward multiplier overflow", overflow);
        }
    }

    private static Map<Integer, Integer> reference(int city) {
        return switch (city) {
            case 1 -> Map.of(201, 55, 305, 10, 102, 3);
            case 2 -> Map.of(201, 55, 307, 10, 102, 3);
            case 3 -> Map.of(201, 25, 304, 10, 102, 3);
            case 4 -> Map.of(201, 25, 303, 10, 102, 3);
            case 5 -> Map.of(201, 30, 302, 20, 102, 5);
            case 6 -> Map.of(201, 25, 301, 20, 224, 5, 102, 5);
            case 7 -> Map.of(201, 25, 308, 20, 224, 10, 102, 5);
            case 8 -> Map.of(201, 25, 306, 20, 102, 5);
            default -> Map.of();
        };
    }
}
