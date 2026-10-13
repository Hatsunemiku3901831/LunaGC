package emu.grasscutter.game.inventory;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.common.ItemParamData;

import lombok.Getter;

import java.util.*;

@Entity
@Getter
public final class ItemUseReceipt {
    private String operationId, sourceObjectId;
    private long sourceGuid;
    private int sourceId, sourceBefore, usedCount, optionId, operation, state;
    private List<Integer> optionIds = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private List<Delivery> deliveries = new ArrayList<>();

    public ItemUseReceipt() {}

    ItemUseReceipt(
            GameItem source,
            int usedCount,
            int optionId,
            int operation,
            List<ItemParamData> products) {
        this(source, usedCount, optionId, operation, products, List.of());
    }

    ItemUseReceipt(
            GameItem source,
            int usedCount,
            int optionId,
            int operation,
            List<ItemParamData> products,
            List<Integer> choices) {
        operationId = UUID.randomUUID().toString();
        sourceId = source.getItemId();
        sourceGuid = source.getGuid();
        sourceObjectId = source.ensurePersistenceId().toHexString();
        sourceBefore = source.getCount();
        this.usedCount = usedCount;
        this.optionId = optionId;
        this.operation = operation;
        this.optionIds = new ArrayList<>(choices);
        for (var product : products)
            this.products.add(new Product(product.getId(), product.getCount()));
        state = 1;
    }

    public List<Integer> getOptionIds() {
        return optionIds == null ? List.of() : Collections.unmodifiableList(optionIds);
    }

    public List<Product> getProducts() {
        return Collections.unmodifiableList(products);
    }

    public List<Delivery> getDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    @Entity
    @Getter
    public static final class Product {
        private int id, count;

        public Product() {}

        Product(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    @Entity
    @Getter
    public static final class Delivery {
        private int id, count;
        private long guid;
        private String objectId = "";

        public Delivery() {}

        Delivery(int id, int count, GameItem item) {
            this.id = id;
            this.count = count;
            if (item != null) {
                guid = item.getGuid();
                objectId = item.ensurePersistenceId().toHexString();
            }
        }
    }

    void paid() {
        if (state != 1) throw new IllegalStateException("Item use payment was not reserved");
        state = 2;
    }

    void delivered(int id, int count, GameItem item) {
        long planned =
                products.stream().filter(p -> p.getId() == id).mapToLong(Product::getCount).sum();
        long given =
                deliveries.stream().filter(p -> p.id == id).mapToLong(Delivery::getCount).sum();
        if (state != 2 || count <= 0 || given + count > planned)
            throw new IllegalStateException("Invalid selected item delivery");
        deliveries.add(new Delivery(id, count, item));
    }

    void completed() {
        if (state != 2
                || products.stream()
                        .anyMatch(
                                p ->
                                        deliveries.stream()
                                                        .filter(d -> d.id == p.getId())
                                                        .mapToLong(Delivery::getCount)
                                                        .sum()
                                                != p.getCount()))
            throw new IllegalStateException("Selected items are not fully confirmed");
        state = 3;
    }
}
