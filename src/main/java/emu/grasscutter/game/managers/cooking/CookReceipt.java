package emu.grasscutter.game.managers.cooking;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.common.ItemParamData;

import java.util.*;

/** Durable evidence for cooking whose payment, delivery or acknowledgement was interrupted. */
@Entity
public final class CookReceipt {
    private int recipeId, count, quality, assistant, proficiency, state;
    private List<Row> costs = new ArrayList<>();
    private List<Row> products = new ArrayList<>();
    private List<Row> extraProducts = new ArrayList<>();

    public CookReceipt() {}

    CookReceipt(
            int recipeId,
            int count,
            int quality,
            int assistant,
            int proficiency,
            List<ItemParamData> costs,
            List<ItemParamData> products) {
        this(recipeId, count, quality, assistant, proficiency, costs, products, List.of());
    }

    CookReceipt(
            int recipeId,
            int count,
            int quality,
            int assistant,
            int proficiency,
            List<ItemParamData> costs,
            List<ItemParamData> products,
            List<ItemParamData> extraProducts) {
        this.recipeId = recipeId;
        this.count = count;
        this.quality = quality;
        this.assistant = assistant;
        this.proficiency = proficiency;
        this.costs = copy(costs);
        this.products = copy(products);
        this.extraProducts = copy(extraProducts);
        this.state = 1;
    }

    private static List<Row> copy(List<ItemParamData> source) {
        var result = new ArrayList<Row>();
        for (var item : source) result.add(new Row(item.getId(), item.getCount()));
        return result;
    }

    @Entity
    public static final class Row {
        private int id, count;

        public Row() {}

        Row(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public int getRecipeId() {
        return recipeId;
    }

    public int getCount() {
        return count;
    }

    public int getQuality() {
        return quality;
    }

    public int getAssistant() {
        return assistant;
    }

    public int getProficiency() {
        return proficiency;
    }

    public int getState() {
        return state;
    }

    public List<ItemParamData> getCosts() {
        return costs.stream().map(row -> new ItemParamData(row.id, row.count)).toList();
    }

    public List<ItemParamData> getProducts() {
        return products.stream().map(row -> new ItemParamData(row.id, row.count)).toList();
    }

    public List<ItemParamData> getExtraProducts() {
        return extraProducts.stream().map(row -> new ItemParamData(row.id, row.count)).toList();
    }

    void paid() {
        if (state != 1) throw new IllegalStateException("Cooking payment is not reserved");
        state = 2;
    }

    void delivered() {
        if (state != 2) throw new IllegalStateException("Cooking payment is not confirmed");
        state = 3;
    }
}
