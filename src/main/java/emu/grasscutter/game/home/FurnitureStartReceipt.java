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
    private List<FurnitureMakeSlotItem> jobs;

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
        this(List.of(slot), costs);
    }

    FurnitureStartReceipt(List<FurnitureMakeSlotItem> jobs, List<Cost> costs) {
        var slot = jobs.get(0);
        operationId = UUID.randomUUID().toString();
        index = slot.getIndex();
        makeId = slot.getMakeId();
        avatarId = slot.getAvatarId();
        beginTime = slot.getBeginTime();
        durTime = slot.getDurTime();
        this.costs = new ArrayList<>(costs);
        this.jobs = new ArrayList<>(jobs);
        state = 1;
    }

    public List<FurnitureMakeSlotItem> getJobs() {
        return jobs == null ? List.of() : Collections.unmodifiableList(jobs);
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
