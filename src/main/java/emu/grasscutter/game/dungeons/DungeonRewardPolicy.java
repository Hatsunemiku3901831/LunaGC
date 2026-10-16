package emu.grasscutter.game.dungeons;

import emu.grasscutter.config.ConfigContainer.GameOptions.DungeonRewardOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Optional reference policies, applied to the complete roll before admission and payment. */
final class DungeonRewardPolicy {
    private DungeonRewardPolicy() {}

    private static final Set<Integer> UNSCALED = Set.of(101, 102, 104, 105, 201, 202, 203, 204);

    static void apply(DungeonData dungeon, List<GameItem> rewards, DungeonRewardOptions options) {
        if (options == null || rewards.isEmpty()) return;
        var subtype = dungeon.getSubType();
        int multiplier = 1;
        if (subtype == DungeonSubType.DUNGEON_SUB_TALENT)
            multiplier = options.talentMaterialMultiplier;
        else if (subtype == DungeonSubType.DUNGEON_SUB_WEAPON)
            multiplier = options.weaponMaterialMultiplier;
        if (multiplier <= 0)
            throw new IllegalArgumentException("Dungeon material multipliers must be positive");

        var counts = new int[rewards.size()];
        for (int i = 0; i < rewards.size(); i++) {
            var item = rewards.get(i);
            counts[i] = item.getCount();
            if (!UNSCALED.contains(item.getItemId())
                    && item.getItemData().getItemType() == ItemType.ITEM_MATERIAL)
                counts[i] = Math.multiplyExact(counts[i], multiplier);
        }
        List<GameItem> bonus = List.of();
        if (options.relicEnhancementBonus
                && subtype == DungeonSubType.DUNGEON_SUB_RELIQUARY
                && rewards.stream()
                        .noneMatch(
                                item -> item.getItemId() == 105002 || item.getItemId() == 105003)) {
            // One bonus per claim, after the normal/condensed roll; preserve original elixir
            // rewards.
            var random = ThreadLocalRandom.current();
            bonus =
                    List.of(
                            bonus(105003, random.nextInt(6, 13)),
                            bonus(105002, random.nextInt(7, 11)));
        }
        // All arithmetic and bonus definitions must succeed before modifying the private roll.
        for (int i = 0; i < rewards.size(); i++) rewards.get(i).setCount(counts[i]);
        rewards.addAll(bonus);
    }

    private static GameItem bonus(int itemId, int count) {
        var data = GameData.getItemDataMap().get(itemId);
        if (data == null || data.getItemType() != ItemType.ITEM_MATERIAL || data.isUseOnGain())
            throw new IllegalArgumentException("Missing dungeon bonus material " + itemId);
        var item = new GameItem(data, count);
        // Keep the full requested quantity for the batch capacity check; never silently clamp it.
        item.setCount(count);
        return item;
    }
}
