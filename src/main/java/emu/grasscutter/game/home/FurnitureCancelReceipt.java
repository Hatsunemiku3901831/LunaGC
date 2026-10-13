package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

@Entity
@Getter
public final class FurnitureCancelReceipt {
    private String operationId;
    private int index, makeId, state;
    private List<Refund> refunds;

    @Entity
    @Getter
    public static final class Refund {
        private int itemId, count, before;
        private String objectId;
        private boolean delivered;

        public Refund() {}

        Refund(int itemId, int count, int before) {
            this.itemId = itemId;
            this.count = count;
            this.before = before;
        }
    }

    public FurnitureCancelReceipt() {}

    FurnitureCancelReceipt(FurnitureMakeSlotItem slot, List<Refund> refunds) {
        operationId = UUID.randomUUID().toString();
        index = slot.getIndex();
        makeId = slot.getMakeId();
        state = 1;
        this.refunds = new ArrayList<>(refunds);
    }

    void delivered(Refund refund, String objectId) {
        if (state != 1 || refund.delivered || !refunds.contains(refund))
            throw new IllegalStateException("Furniture refund is not reserved");
        refund.objectId = objectId;
        refund.delivered = true;
    }

    void refunded() {
        if (state != 1 || refunds.stream().anyMatch(r -> !r.delivered))
            throw new IllegalStateException("Furniture refunds not confirmed");
        state = 2;
    }

    void completed() {
        if (state != 2) throw new IllegalStateException("Furniture refund not confirmed");
        state = 3;
    }
}
