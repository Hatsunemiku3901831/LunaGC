package emu.grasscutter.game.reliquary;

import com.google.gson.*;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Resource eligibility, per-slot dust costs, and repeating blessing thresholds. */
public record ArtifactReshapeRules(
        int selectedCount,
        int minimumLevel,
        int minimumRank,
        int minimumInitialProps,
        int materialId,
        Map<Integer, Integer> threePropCosts,
        Map<Integer, Integer> fourPropCosts,
        Map<Integer, Integer> guarantees,
        NavigableMap<Integer, Integer> thresholds) {
    private static volatile ArtifactReshapeRules current;

    public static ArtifactReshapeRules current() {
        var rules = current;
        if (rules != null) return rules;
        synchronized (ArtifactReshapeRules.class) {
            if (current == null) {
                try {
                    current =
                            load(
                                    FileUtils.getResourcePath(
                                            "ExcelBinOutput/ConstValueExcelConfigData.json"));
                } catch (IOException | RuntimeException failure) {
                    throw new IllegalStateException(
                            "Artifact reshape constants are unavailable", failure);
                }
            }
            return current;
        }
    }

    public static ArtifactReshapeRules load(Path path) throws IOException {
        var rows = new HashMap<String, JsonArray>();
        try (var reader = Files.newBufferedReader(path)) {
            for (var raw : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = raw.getAsJsonObject();
                if (!row.has("name")) continue;
                String name = row.get("name").getAsString();
                if (name.startsWith("CONST_VALUE_RELIQUARY_DUST_PARAM_")
                        && rows.put(name, row.getAsJsonArray("value")) != null)
                    throw new IllegalArgumentException("Duplicate reshape constant");
            }
        }
        var first = Objects.requireNonNull(rows.get("CONST_VALUE_RELIQUARY_DUST_PARAM_1"));
        var second = Objects.requireNonNull(rows.get("CONST_VALUE_RELIQUARY_DUST_PARAM_2"));
        int choices = first.get(0).getAsInt(), material = first.get(2).getAsInt();
        var eligibility = first.get(1).getAsString().split(";", -1);
        if (eligibility.length != 3)
            throw new IllegalArgumentException("Invalid reshape eligibility");
        int level = Integer.parseInt(eligibility[0]),
                rank = Integer.parseInt(eligibility[1]),
                initial = Integer.parseInt(eligibility[2]);
        var three = pairs(first.get(3).getAsString());
        var four = pairs(first.get(4).getAsString());
        var guarantees = pairs(first.get(5).getAsString());
        var thresholds = pairs(second.get(0).getAsString());
        if (choices != 2
                || material <= 0
                || level < 1
                || rank < 1
                || initial != 3
                || !three.keySet().equals(Set.of(1, 2, 3, 4, 5))
                || !four.keySet().equals(three.keySet())
                || !guarantees.containsKey(1)
                || thresholds.isEmpty()
                || thresholds.lastKey() > 10000
                || !guarantees.keySet().containsAll(thresholds.values())
                || guarantees.values().stream().anyMatch(x -> x < 1 || x > 5)
                || three.values().stream().anyMatch(x -> x > thresholds.firstKey())
                || four.values().stream().anyMatch(x -> x > thresholds.firstKey()))
            throw new IllegalArgumentException("Invalid reshape constants");
        return new ArtifactReshapeRules(
                choices,
                level,
                rank,
                initial,
                material,
                Map.copyOf(three),
                Map.copyOf(four),
                Map.copyOf(guarantees),
                Collections.unmodifiableNavigableMap(thresholds));
    }

    private static TreeMap<Integer, Integer> pairs(String source) {
        var values = new TreeMap<Integer, Integer>();
        for (var entry : source.split(";", -1)) {
            var pair = entry.split(",", -1);
            if (pair.length != 2) throw new IllegalArgumentException("Invalid reshape pair");
            int key = Integer.parseInt(pair[0]), value = Integer.parseInt(pair[1]);
            if (key <= 0 || value <= 0 || values.put(key, value) != null)
                throw new IllegalArgumentException("Invalid reshape pair");
        }
        return values;
    }

    public int tier(long progress, int cost) {
        long phase = progress % thresholds.lastKey(), lastDistance = 0;
        int tier = 1;
        for (var threshold : thresholds.entrySet()) {
            long distance = threshold.getKey() - phase;
            if (distance <= 0) distance += thresholds.lastKey();
            if (distance <= cost && distance > lastDistance) {
                lastDistance = distance;
                tier = threshold.getValue();
            }
        }
        return tier;
    }
}
