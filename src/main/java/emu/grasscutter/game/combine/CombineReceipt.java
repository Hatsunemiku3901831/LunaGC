package emu.grasscutter.game.combine;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.common.ItemParamData;

import java.util.*;

/** Economic intent, effective costs, and confirmed output identities for a single combine. */
@Entity
public final class CombineReceipt {
    private String operationId;
    private int recipeId, units, outputId, outputCount, state;
    private List<Cost> costs = new ArrayList<>();
    private List<Delivery> deliveries = new ArrayList<>();

    public CombineReceipt() {}

    CombineReceipt(
            int recipeId,
            int units,
            int outputId,
            int outputCount,
            List<ItemParamData> costs,
            Map<Integer, Integer> before) {
        this.operationId = UUID.randomUUID().toString();
        this.recipeId = recipeId;
        this.units = units;
        this.outputId = outputId;
        this.outputCount = outputCount;
        for (var cost : costs)
            this.costs.add(new Cost(cost.getId(), cost.getCount(), before.get(cost.getId())));
        this.state = 1;
    }

    @Entity
    public static final class Cost {
        private int id, count, before;

        public Cost() {}

        Cost(int id, int count, int before) {
            this.id = id;
            this.count = count;
            this.before = before;
        }

        public int getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public int getBefore() {
            return before;
        }
    }

    @Entity
    public static final class Delivery {
        private int id, count;
        private long guid;

        public Delivery() {}

        Delivery(int id, int count, long guid) {
            this.id = id;
            this.count = count;
            this.guid = guid;
        }

        public int getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public long getGuid() {
            return guid;
        }
    }

    public String getOperationId() {
        return operationId;
    }

    public int getRecipeId() {
        return recipeId;
    }

    public int getUnits() {
        return units;
    }

    public int getOutputId() {
        return outputId;
    }

    public int getOutputCount() {
        return outputCount;
    }

    public int getState() {
        return state;
    }

    public List<Cost> getCosts() {
        return Collections.unmodifiableList(costs);
    }

    public List<Delivery> getDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    void paid() {
        if (state != 1) throw new IllegalStateException("Combine payment is not reserved");
        state = 2;
    }

    void recordDelivery(int id, int amount, long guid) {
        if (state != 2 || id != outputId || amount <= 0)
            throw new IllegalStateException("Invalid combine delivery record");
        long delivered = deliveries.stream().mapToLong(Delivery::getCount).sum();
        if (delivered + amount > outputCount)
            throw new IllegalStateException("Combine output exceeds receipt");
        deliveries.add(new Delivery(id, amount, guid));
    }

    void completed() {
        if (state != 2 || deliveries.stream().mapToLong(Delivery::getCount).sum() != outputCount)
            throw new IllegalStateException("Combine output is not fully confirmed");
        state = 3;
    }
}
