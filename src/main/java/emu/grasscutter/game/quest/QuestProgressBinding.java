package emu.grasscutter.game.quest;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.excels.quest.QuestData.QuestContentCondition;

import lombok.Getter;

import java.util.*;

/** Rebinds identified objectives without assigning a retired count to a different condition. */
public final class QuestProgressBinding {
    private QuestProgressBinding() {}

    @Entity(useDiscriminator = false)
    public static final class RetiredProgress {
        @Getter private String condition;
        @Getter private int occurrence;
        @Getter private int progress;

        @Deprecated // Morphia only.
        public RetiredProgress() {}

        private RetiredProgress(Slot slot, int progress) {
            this.condition = slot.condition();
            this.occurrence = slot.occurrence();
            this.progress = progress;
        }
    }

    private record Slot(String condition, int occurrence) {}

    public record Binding(
            int[] progress,
            List<String> keys,
            List<RetiredProgress> retired,
            int[] legacy,
            boolean changed) {}

    public static List<String> keys(List<QuestContentCondition> conditions) {
        if (conditions == null) return List.of();
        return conditions.stream()
                .map(
                        condition ->
                                condition.getType().getValue()
                                        + "|"
                                        + Arrays.toString(condition.getParam())
                                        + "|"
                                        + QuestContentProgress.target(condition)
                                        + "|"
                                        + Objects.toString(condition.getParamStr(), ""))
                .toList();
    }

    private static List<Slot> slots(List<String> keys) {
        Map<String, Integer> occurrences = new HashMap<>();
        return keys.stream()
                .map(key -> new Slot(key, occurrences.merge(key, 1, Integer::sum) - 1))
                .toList();
    }

    public static Binding bind(
            int[] saved,
            List<String> previousKeys,
            List<String> currentKeys,
            List<RetiredProgress> retired,
            int[] legacy) {
        boolean identified =
                saved != null
                        && previousKeys != null
                        && saved.length == previousKeys.size()
                        && previousKeys.stream().noneMatch(Objects::isNull);
        if (identified && previousKeys.equals(currentKeys)) {
            return new Binding(saved, previousKeys, retired, legacy, false);
        }

        // Old versions had positional flags only. Retain the established same-sized convention.
        if (previousKeys == null && saved != null && saved.length == currentKeys.size()) {
            return new Binding(saved, currentKeys, retired, legacy, true);
        }

        Map<Slot, Integer> available = new LinkedHashMap<>();
        if (retired != null) {
            for (var entry : retired) {
                if (entry != null && entry.condition != null && entry.occurrence >= 0) {
                    available.putIfAbsent(
                            new Slot(entry.condition, entry.occurrence), entry.progress);
                }
            }
        }
        if (identified) {
            var oldSlots = slots(previousKeys);
            for (int i = 0; i < saved.length; i++) available.put(oldSlots.get(i), saved[i]);
        } else if (saved != null && legacy == null) {
            // There is no reliable identity for these values. Preserve the original for recovery.
            legacy = saved.clone();
        }

        int[] progress = new int[currentKeys.size()];
        var newSlots = slots(currentKeys);
        for (int i = 0; i < progress.length; i++) {
            var recorded = available.remove(newSlots.get(i));
            if (recorded != null) progress[i] = recorded;
        }
        var remaining =
                available.entrySet().stream()
                        .filter(entry -> entry.getValue() != 0)
                        .map(entry -> new RetiredProgress(entry.getKey(), entry.getValue()))
                        .toList();
        return new Binding(
                progress, currentKeys, remaining.isEmpty() ? null : remaining, legacy, true);
    }
}
