package emu.grasscutter.game.tps;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import java.util.*;

/** The complete accepted supply, including resource limits, survives an interrupted write. */
@Entity
@Getter
public final class TpsSupplyReceipt {
    private String operationId;
    private long createdAt;
    private int state, sceneId, avatarId;
    private Map<Integer, Integer> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
    private Map<Integer, Integer> effectiveBefore = new LinkedHashMap<>(),
            limits = new LinkedHashMap<>();
    private List<Gain> gains = new ArrayList<>();

    @Entity
    @Getter
    public static final class Gain {
        private int pool, amount;

        public Gain() {}

        Gain(int pool, int amount) {
            this.pool = pool;
            this.amount = amount;
        }
    }

    public TpsSupplyReceipt() {}

    TpsSupplyReceipt(
            long time,
            int scene,
            int avatar,
            Map<Integer, Integer> before,
            Map<Integer, Integer> after,
            Map<Integer, Integer> effective,
            Map<Integer, Integer> limits,
            List<Gain> gains) {
        operationId = UUID.randomUUID().toString();
        createdAt = time;
        state = 1;
        sceneId = scene;
        avatarId = avatar;
        this.before = new LinkedHashMap<>(before);
        this.after = new LinkedHashMap<>(after);
        this.effectiveBefore = new LinkedHashMap<>(effective);
        this.limits = new LinkedHashMap<>(limits);
        this.gains = new ArrayList<>(gains);
        validate();
    }

    void applied() {
        if (state != 1) throw new IllegalStateException("TPS 补给阶段错误。");
        state = 2;
    }

    void validate() {
        require(
                operationId != null
                        && UUID.fromString(operationId).toString().equals(operationId)
                        && createdAt > 0
                        && (state == 1 || state == 2)
                        && sceneId > 0
                        && avatarId > 0,
                "TPS 补给记录缺少有效身份或阶段。");
        require(
                before != null
                        && after != null
                        && limits != null
                        && effectiveBefore != null
                        && gains != null
                        && !gains.isEmpty(),
                "TPS 补给记录缺少完整计划。");
        for (var map : List.of(before, after, effectiveBefore, limits))
            require(
                    map.entrySet().stream()
                            .allMatch(
                                    e ->
                                            e.getKey() != null
                                                    && e.getKey() > 0
                                                    && e.getValue() != null),
                    "TPS 补给快照损坏。");
        var expected = new LinkedHashMap<>(before);
        var touched = new HashSet<Integer>();
        for (var gain : gains) {
            require(
                    gain != null
                            && gain.pool > 0
                            && gain.amount > 0
                            && limits.containsKey(gain.pool)
                            && effectiveBefore.containsKey(gain.pool),
                    "TPS 补给增量损坏。");
            int limit = limits.get(gain.pool), initial = effectiveBefore.get(gain.pool);
            require(
                    limit >= 0
                            && initial >= 0
                            && initial <= limit
                            && initial
                                    == Math.max(
                                            0,
                                            Math.min(before.getOrDefault(gain.pool, limit), limit)),
                    "TPS 补给原始容量或数量不符。");
            long next =
                    (long) (touched.add(gain.pool) ? initial : expected.get(gain.pool))
                            + gain.amount;
            require(next <= limit, "TPS 补给超过冻结容量。");
            expected.put(gain.pool, (int) next);
        }
        require(
                touched.equals(limits.keySet())
                        && touched.equals(effectiveBefore.keySet())
                        && expected.equals(after)
                        && !before.equals(after),
                "TPS 补给结果与计划不符。");
    }

    boolean samePlan(TpsSupplyReceipt other) {
        return other != null
                && Objects.equals(operationId, other.operationId)
                && createdAt == other.createdAt
                && sceneId == other.sceneId
                && avatarId == other.avatarId
                && before.equals(other.before)
                && after.equals(other.after)
                && effectiveBefore.equals(other.effectiveBefore)
                && limits.equals(other.limits)
                && gains.stream()
                        .map(g -> List.of(g.pool, g.amount))
                        .toList()
                        .equals(other.gains.stream().map(g -> List.of(g.pool, g.amount)).toList());
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
