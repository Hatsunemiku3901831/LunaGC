package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

@Entity
@Getter
public final class FurnitureTakeReceipt {
    private String operationId, productObjectId, parentOperationId;
    private FurnitureMakeSlotItem sourceSlot;
    private int index, makeId, avatarId, beginTime, durTime, accelerateTime;
    private int itemId, count, itemBefore, vialBefore, trustExp;
    private int levelBefore, expBefore, levelAfter, expAfter, state;
    private boolean needsVial, firstCraft, missionApplied;
    private List<Return> returns;

    @Entity
    @Getter
    public static final class Return {
        private int itemId, count, before;
        private String objectId;
        private boolean delivered;

        public Return() {}

        Return(int itemId, int count, int before) {
            this.itemId = itemId;
            this.count = count;
            this.before = before;
        }
    }

    public List<Return> getReturns() {
        return returns == null ? List.of() : Collections.unmodifiableList(returns);
    }

    public FurnitureTakeReceipt() {}

    FurnitureTakeReceipt(
            FurnitureMakeSlotItem slot,
            int itemId,
            int count,
            int itemBefore,
            int vialBefore,
            boolean needsVial,
            boolean firstCraft,
            int trustExp,
            int levelBefore,
            int expBefore,
            int levelAfter,
            int expAfter,
            List<Return> returns) {
        operationId = UUID.randomUUID().toString();
        sourceSlot = slot.snapshot();
        index = slot.getIndex();
        makeId = slot.getMakeId();
        avatarId = slot.getAvatarId();
        beginTime = slot.getBeginTime();
        durTime = slot.getDurTime();
        accelerateTime = slot.getAccelerateTime();
        this.itemId = itemId;
        this.count = count;
        this.itemBefore = itemBefore;
        this.vialBefore = vialBefore;
        this.needsVial = needsVial;
        this.firstCraft = firstCraft;
        this.trustExp = trustExp;
        this.levelBefore = levelBefore;
        this.expBefore = expBefore;
        this.levelAfter = levelAfter;
        this.expAfter = expAfter;
        this.returns = new ArrayList<>(returns);
        state = 1;
    }

    void bindParent(FurnitureBulkReceipt owner) {
        if (state != 1 || parentOperationId != null || owner == null)
            throw new IllegalStateException("Furniture parent cannot be rebound");
        parentOperationId = owner.getOperationId();
    }

    void paid() {
        if (state != 1) throw new IllegalStateException("Furniture claim not reserved");
        state = 2;
    }

    void delivered(String objectId) {
        if (state != 2) throw new IllegalStateException("Furniture claim not paid");
        productObjectId = objectId;
        state = 3;
    }

    void progressed() {
        if (state != 3 || getReturns().stream().anyMatch(value -> !value.delivered))
            throw new IllegalStateException("Furniture and material returns not delivered");
        state = 4;
    }

    void returned(Return value, String objectId) {
        if (state != 3 || value.delivered || !getReturns().contains(value))
            throw new IllegalStateException("Furniture return is not reserved");
        value.objectId = objectId;
        value.delivered = true;
    }

    void missionApplied() {
        if (state != 4) throw new IllegalStateException("Furniture progress not confirmed");
        missionApplied = true;
    }

    void completed() {
        if (state != 4 || !missionApplied)
            throw new IllegalStateException("Furniture mission not confirmed");
        state = 5;
    }
}
