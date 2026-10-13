package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

/** Definition and actual material costs frozen for one paid furniture job. */
@Entity
@Getter
public final class FurnitureMakePlan {
    private int format, makeId, itemId, count, makeTime, trustExp;
    private List<Material> materials;
    private List<Material> returns;

    @Entity
    @Getter
    public static final class Material {
        private int id, count;

        public Material() {}

        Material(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public FurnitureMakePlan() {}

    FurnitureMakePlan(
            int makeId,
            int itemId,
            int count,
            int makeTime,
            int trustExp,
            Map<Integer, Integer> costs) {
        format = 1;
        this.makeId = makeId;
        this.itemId = itemId;
        this.count = count;
        this.makeTime = makeTime;
        this.trustExp = trustExp;
        materials = new ArrayList<>();
        costs.forEach((id, amount) -> materials.add(new Material(id, amount)));
    }

    FurnitureMakePlan(
            int makeId,
            int itemId,
            int count,
            int makeTime,
            int trustExp,
            Map<Integer, Integer> costs,
            Map<Integer, Integer> returns) {
        this(makeId, itemId, count, makeTime, trustExp, costs);
        if (!returns.isEmpty()) {
            format = 2;
            this.returns = new ArrayList<>();
            returns.forEach((id, amount) -> this.returns.add(new Material(id, amount)));
        }
    }

    public List<Material> getMaterials() {
        return materials == null ? List.of() : Collections.unmodifiableList(materials);
    }

    public List<Material> getReturns() {
        return returns == null ? List.of() : Collections.unmodifiableList(returns);
    }

    public boolean isValidFor(FurnitureMakeSlotItem slot) {
        if (format != 1 && format != 2
                || makeId <= 0
                || makeId != slot.getMakeId()
                || itemId <= 0
                || count <= 0
                || makeTime < 0
                || makeTime != slot.getDurTime()
                || trustExp < 0
                || materials == null) return false;
        Set<Integer> ids = new HashSet<>();
        if (!materials.stream()
                .allMatch(
                        material ->
                                material != null
                                        && material.id > 0
                                        && material.count > 0
                                        && ids.add(material.id))) return false;
        if (format == 1) return returns == null || returns.isEmpty();
        if (returns == null || returns.isEmpty()) return false;
        Map<Integer, Integer> paid = new HashMap<>();
        materials.forEach(material -> paid.put(material.id, material.count));
        ids.clear();
        return returns.stream()
                .allMatch(
                        value ->
                                value != null
                                        && value.id > 0
                                        && value.count > 0
                                        && value.count <= paid.getOrDefault(value.id, 0)
                                        && ids.add(value.id));
    }
}
