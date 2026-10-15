package emu.grasscutter.game.reliquary;

import dev.morphia.annotations.Entity;

import emu.grasscutter.game.inventory.GameItem;

import lombok.Getter;

import java.util.*;

/**
 * Preserve uncertain payments and rewards for inspection, instead of retrying them automatically.
 */
@Entity
@Getter
public final class ArtifactTransmuterReceipt {
    private String operationId;
    private int state,
            scheduleId,
            extractedBefore,
            extractedAfter,
            rewardId,
            rewardBefore,
            rewardCount;
    private long cycleEndTime, progressBefore, progressAfter;
    private List<Cost> costs = new ArrayList<>();
    private int purchaseId, setId, definedBefore, definedAfter, mainPropId, definedUpgradeGuarantee;
    private String outputObjectId;
    private List<Integer> appendPropIds = new ArrayList<>();
    private List<Integer> selectedPropIds = new ArrayList<>();

    public ArtifactTransmuterReceipt() {}

    ArtifactTransmuterReceipt(
            ArtifactTransmuterData data,
            long after,
            int extractedAfter,
            int rewardId,
            int rewardBefore,
            int rewardCount,
            Map<GameItem, Integer> costs) {
        operationId = UUID.randomUUID().toString();
        state = 1;
        scheduleId = data.getScheduleId();
        cycleEndTime = data.getCycleEndTime();
        progressBefore = data.getProgressUnits();
        progressAfter = after;
        extractedBefore = data.getExtractedCount();
        this.extractedAfter = extractedAfter;
        this.rewardId = rewardId;
        this.rewardBefore = rewardBefore;
        this.rewardCount = rewardCount;
        costs.forEach((item, count) -> this.costs.add(new Cost(item, count)));
    }

    void paid() {
        state = 2;
    }

    static ArtifactTransmuterReceipt definition(
            ArtifactTransmuterData data,
            ArtifactDefinition.Plan plan,
            Map<GameItem, Integer> costs) {
        var item = plan.item();
        var receipt =
                new ArtifactTransmuterReceipt(
                        data,
                        data.getProgressUnits(),
                        data.getExtractedCount(),
                        item.getItemId(),
                        0,
                        1,
                        costs);
        receipt.purchaseId = plan.purchaseId();
        receipt.setId = plan.setId();
        receipt.definedBefore = data.getDefinedCounts().getOrDefault(plan.setId(), 0);
        receipt.definedAfter = receipt.definedBefore + 1;
        receipt.outputObjectId = item.ensurePersistenceId().toHexString();
        receipt.mainPropId = item.getMainPropId();
        receipt.definedUpgradeGuarantee = item.getDefinedUpgradeGuarantee();
        receipt.appendPropIds = new ArrayList<>(item.getAppendPropIdList());
        receipt.selectedPropIds = new ArrayList<>(item.getPurchasedAppendPropIdList());
        return receipt;
    }

    void applied() {
        state = 3;
    }

    @Entity(useDiscriminator = false)
    @Getter
    public static final class Cost {
        private String objectId;
        private long guid;
        private int itemId, before, consumed;

        public Cost() {}

        Cost(GameItem item, int count) {
            objectId = item.ensurePersistenceId().toHexString();
            guid = item.getGuid();
            itemId = item.getItemId();
            before = item.getCount();
            consumed = count;
        }
    }
}
