package emu.grasscutter.game.city;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.common.ItemParamData;

import lombok.Getter;

import java.util.*;

/** An unresolved offering is retained on its city instead of being paid or granted twice. */
@Entity
@Getter
public final class StatueOfferReceipt {
    private String operationId;
    private int itemId,
            itemBefore,
            consumed,
            levelBefore,
            crystalsBefore,
            levelAfter,
            crystalsAfter,
            staminaGain,
            state;
    private int staminaBefore, staminaAfter;
    private List<Reward> rewards = new ArrayList<>();

    public StatueOfferReceipt() {}

    public StatueOfferReceipt(
            int itemId,
            int itemBefore,
            int consumed,
            int levelBefore,
            int crystalsBefore,
            int levelAfter,
            int crystalsAfter,
            int staminaGain,
            int staminaBefore,
            int staminaAfter,
            List<ItemParamData> rewards) {
        operationId = UUID.randomUUID().toString();
        this.itemId = itemId;
        this.itemBefore = itemBefore;
        this.consumed = consumed;
        this.levelBefore = levelBefore;
        this.crystalsBefore = crystalsBefore;
        this.levelAfter = levelAfter;
        this.crystalsAfter = crystalsAfter;
        this.staminaGain = staminaGain;
        this.staminaBefore = staminaBefore;
        this.staminaAfter = staminaAfter;
        for (var item : rewards) this.rewards.add(new Reward(item.getId(), item.getCount()));
        state = 1;
    }

    public void paid() {
        state = 2;
    }

    public void applied() {
        state = 3;
    }

    @Entity
    @Getter
    public static final class Reward {
        private int id, count;

        public Reward() {}

        Reward(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }
}
