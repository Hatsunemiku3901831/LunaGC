package emu.grasscutter.game.combine;

import dev.morphia.annotations.Entity;

import emu.grasscutter.game.inventory.GameItem;

import lombok.Getter;

import java.util.*;

/** Frozen ingredients and random relic profiles; unresolved operations are for reconciliation. */
@Entity
@Getter
public final class StrongboxReceipt {
    private String operationId;
    private int configId, state;
    private List<Relic> ingredients = new ArrayList<>();
    private List<Relic> outputs = new ArrayList<>();

    public StrongboxReceipt() {}

    StrongboxReceipt(int configId, List<GameItem> ingredients, List<GameItem> outputs) {
        this.operationId = UUID.randomUUID().toString();
        this.configId = configId;
        ingredients.forEach(item -> this.ingredients.add(new Relic(item)));
        outputs.forEach(item -> this.outputs.add(new Relic(item)));
        this.state = 1;
    }

    public List<Relic> getIngredients() {
        return Collections.unmodifiableList(ingredients);
    }

    public List<Relic> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    @Entity
    @Getter
    public static final class Relic {
        private int itemId, ownerId, level, exp, totalExp, mainPropId;
        private long guid, deliveredGuid;
        private String objectId = "", deliveredObjectId = "";
        private List<Integer> appendPropIdList = new ArrayList<>();
        private boolean removed, delivered;

        public Relic() {}

        Relic(GameItem item) {
            itemId = item.getItemId();
            ownerId = item.getOwnerId();
            guid = item.getGuid();
            objectId = item.ensurePersistenceId().toHexString();
            level = item.getLevel();
            exp = item.getExp();
            totalExp = item.getTotalExp();
            mainPropId = item.getMainPropId();
            if (item.getAppendPropIdList() != null)
                appendPropIdList.addAll(item.getAppendPropIdList());
        }

        public List<Integer> getAppendPropIdList() {
            return Collections.unmodifiableList(appendPropIdList);
        }

        boolean matches(GameItem item) {
            return item != null
                    && item.getItemId() == itemId
                    && objectId.equals(identity(item))
                    && item.getCount() == 1
                    && item.getLevel() == level
                    && item.getExp() == exp
                    && item.getTotalExp() == totalExp
                    && item.getMainPropId() == mainPropId
                    && appendPropIdList.equals(item.getAppendPropIdList());
        }

        private static String identity(GameItem item) {
            return item.getObjectId() == null ? "" : item.getObjectId().toHexString();
        }
    }

    void removed(int index) {
        var item = ingredients.get(index);
        if (state != 1 || item.removed)
            throw new IllegalStateException("Invalid strongbox removal confirmation");
        item.removed = true;
    }

    void paid() {
        if (state != 1 || ingredients.stream().anyMatch(item -> !item.removed))
            throw new IllegalStateException("Strongbox ingredients are not fully confirmed");
        state = 2;
    }

    void delivered(int index, GameItem item) {
        var output = outputs.get(index);
        if (state != 2 || output.delivered || !output.matches(item))
            throw new IllegalStateException("Strongbox output differs from reserved profile");
        output.deliveredGuid = item.getGuid();
        output.deliveredObjectId = Relic.identity(item);
        output.delivered = true;
    }

    void completed() {
        if (state != 2 || outputs.stream().anyMatch(item -> !item.delivered))
            throw new IllegalStateException("Strongbox outputs are not fully confirmed");
        state = 3;
    }
}
