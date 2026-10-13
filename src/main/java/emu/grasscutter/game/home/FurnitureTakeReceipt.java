package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.UUID;

@Entity
@Getter
public final class FurnitureTakeReceipt {
    private String operationId, productObjectId;
    private int index, makeId, avatarId, beginTime, durTime;
    private int itemId, count, itemBefore, vialBefore, trustExp;
    private int levelBefore, expBefore, levelAfter, expAfter, state;
    private boolean needsVial, firstCraft, missionApplied;

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
            int expAfter) {
        operationId = UUID.randomUUID().toString();
        index = slot.getIndex();
        makeId = slot.getMakeId();
        avatarId = slot.getAvatarId();
        beginTime = slot.getBeginTime();
        durTime = slot.getDurTime();
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
        state = 1;
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
        if (state != 3) throw new IllegalStateException("Furniture not delivered");
        state = 4;
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
