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
    private int version, sceneId, areaId;
    private String costObjectId;
    private List<Integer> forces = new ArrayList<>();

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

    /** Freeze identities before the intent is saved; old receipts retain version zero. */
    public void freeze(
            String costObjectId,
            int sceneId,
            int areaId,
            List<Integer> forces,
            List<Reward> rewards) {
        if (version != 0 || state != 1) throw new IllegalStateException("Offering already frozen");
        this.costObjectId = costObjectId;
        this.sceneId = sceneId;
        this.areaId = areaId;
        this.forces = new ArrayList<>(forces);
        this.rewards = new ArrayList<>(rewards);
        version = 1;
    }

    @Entity
    @Getter
    public static final class Reward {
        private int id, count;
        private int before, after, levelBefore, levelAfter;
        private String objectId;
        private boolean virtual;

        public Reward() {}

        Reward(int id, int count) {
            this.id = id;
            this.count = count;
        }

        public Reward(
                int id,
                int count,
                int before,
                int after,
                int levelBefore,
                int levelAfter,
                String objectId,
                boolean virtual) {
            this(id, count);
            this.before = before;
            this.after = after;
            this.levelBefore = levelBefore;
            this.levelAfter = levelAfter;
            this.objectId = objectId;
            this.virtual = virtual;
        }
    }
}
