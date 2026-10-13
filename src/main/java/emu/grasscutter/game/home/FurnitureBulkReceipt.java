package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

@Entity
@Getter
public final class FurnitureBulkReceipt {
    private String operationId;
    private int state;
    private List<Entry> entries;

    @Entity
    @Getter
    public static final class Entry {
        private int index, makeId, beginTime, durTime, itemId, count, trustExp;
        private boolean fast, completed;
        private List<FurnitureMakePlan.Material> returns;

        public Entry() {}

        Entry(FurnitureMakeSlotItem slot, boolean fast, int itemId, int count, int exp) {
            index = slot.getIndex();
            makeId = slot.getMakeId();
            beginTime = slot.getBeginTime();
            durTime = slot.getDurTime();
            this.fast = fast;
            this.itemId = itemId;
            this.count = count;
            trustExp = exp;
            returns = new ArrayList<>();
            if (slot.getPlan() != null)
                slot.getPlan()
                        .getReturns()
                        .forEach(
                                value ->
                                        returns.add(
                                                new FurnitureMakePlan.Material(
                                                        value.getId(), value.getCount())));
        }

        public List<FurnitureMakePlan.Material> getReturns() {
            return returns == null ? List.of() : Collections.unmodifiableList(returns);
        }

        boolean matches(FurnitureMakeSlotItem slot) {
            return slot.getIndex() == index
                    && slot.getMakeId() == makeId
                    && slot.getBeginTime() == beginTime
                    && slot.getDurTime() == durTime;
        }
    }

    public FurnitureBulkReceipt() {}

    FurnitureBulkReceipt(List<Entry> entries) {
        operationId = UUID.randomUUID().toString();
        state = 1;
        this.entries = new ArrayList<>(entries);
    }

    boolean canRun(Entry entry) {
        return state == 1 && entry != null && !entry.completed && entries.contains(entry);
    }

    void completed(Entry entry, int itemId, int count) {
        if (!canRun(entry) || entry.itemId != itemId || entry.count != count)
            throw new IllegalStateException("Furniture batch output changed");
        entry.completed = true;
    }

    void finished() {
        if (state != 1 || entries.stream().anyMatch(e -> !e.completed))
            throw new IllegalStateException("Furniture batch is incomplete");
        state = 2;
    }
}
