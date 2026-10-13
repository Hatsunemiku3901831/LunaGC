package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

@Entity
@Getter
public final class FurnitureStartReceipt {
    private String operationId;
    private int index, makeId, avatarId, beginTime, durTime, state;
    private List<Cost> costs;

    @Entity
    @Getter
    public static final class Cost {
        private int itemId, count, before;
        private String objectId;

        public Cost() {}

        Cost(int itemId, int count, int before, String objectId) {
            this.itemId = itemId;
            this.count = count;
            this.before = before;
            this.objectId = objectId;
        }
    }

    public FurnitureStartReceipt() {}

    FurnitureStartReceipt(FurnitureMakeSlotItem slot, List<Cost> costs) {
        operationId = UUID.randomUUID().toString();
        index = slot.getIndex();
        makeId = slot.getMakeId();
        avatarId = slot.getAvatarId();
        beginTime = slot.getBeginTime();
        durTime = slot.getDurTime();
        this.costs = new ArrayList<>(costs);
        state = 1;
    }

    void paid() {
        if (state != 1) throw new IllegalStateException("Furniture start not reserved");
        state = 2;
    }

    void queued() {
        if (state != 2) throw new IllegalStateException("Furniture start not paid");
        state = 3;
    }
}
