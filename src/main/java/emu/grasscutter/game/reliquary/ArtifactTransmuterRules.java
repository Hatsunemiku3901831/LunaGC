package emu.grasscutter.game.reliquary;

import com.google.gson.*;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Costs exported with the client, expressed in thousandths to preserve fractional progress. */
public record ArtifactTransmuterRules(
        int gadgetItemId,
        int elixirItemId,
        int minRank,
        int minLevel,
        int maxArtifactBatch,
        long requiredUnits,
        int cycleLimit,
        List<Long> artifactUnits,
        Map<Integer, Long> materialUnits) {
    public static final int SCALE = 1000;
    private static volatile ArtifactTransmuterRules current;

    public static ArtifactTransmuterRules current() {
        var value = current;
        if (value != null) return value;
        synchronized (ArtifactTransmuterRules.class) {
            if (current == null) reload();
            return current;
        }
    }

    public static synchronized void reload() {
        try {
            current =
                    load(
                            FileUtils.getResourcePath(
                                    "ExcelBinOutput/ConstValueExcelConfigData.json"));
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException(
                    "Artifact transmuter resource constants are unavailable", invalid);
        }
    }

    public static ArtifactTransmuterRules load(Path file) throws IOException {
        var constants = new HashMap<String, JsonArray>();
        try (var reader = Files.newBufferedReader(file)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = element.getAsJsonObject();
                if (!row.has("name")) continue;
                String name = row.get("name").getAsString();
                if (!name.startsWith("CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_")) continue;
                if (constants.put(name, row.getAsJsonArray("value")) != null)
                    throw new IllegalArgumentException("Duplicate artifact constant: " + name);
            }
        }
        var first =
                Objects.requireNonNull(
                        constants.get("CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_1"));
        var second =
                Objects.requireNonNull(
                        constants.get("CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_2"));
        var third =
                Objects.requireNonNull(
                        constants.get("CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_3"));
        int gadget = first.get(0).getAsInt(), elixir = first.get(3).getAsInt();
        int rank = second.get(0).getAsInt(), level = second.get(1).getAsInt();
        int batch = second.get(2).getAsInt(), limit = second.get(4).getAsInt();
        long required = units(second.get(3).getAsString());
        var curve = new ArrayList<Long>();
        for (var point : second.get(5).getAsString().split(";", -1)) curve.add(units(point));
        var materials = new LinkedHashMap<Integer, Long>();
        for (var pair : third.get(1).getAsString().split(";", -1)) {
            var parts = pair.split(",", -1);
            if (parts.length != 2)
                throw new IllegalArgumentException("Invalid extraction material");
            int id = Integer.parseInt(parts[0]);
            long gain = units(parts[1]);
            if (id <= 0 || gain <= 0 || materials.put(id, gain) != null)
                throw new IllegalArgumentException("Invalid extraction material cost");
        }
        if (gadget != ArtifactTransmuter.GADGET_ITEM_ID
                || elixir <= 0
                || rank <= 0
                || level < 1
                || level > curve.size()
                || batch <= 0
                || batch > 1000
                || required <= 0
                || required / SCALE > Integer.MAX_VALUE
                || required % SCALE != 0
                || limit <= 0
                || limit > 1000
                || curve.stream().anyMatch(x -> x < 0)
                || curve.get(level - 1) <= 0
                || materials.isEmpty())
            throw new IllegalArgumentException("Invalid artifact transmuter constants");
        return new ArtifactTransmuterRules(
                gadget,
                elixir,
                rank,
                level,
                batch,
                required,
                limit,
                List.copyOf(curve),
                Map.copyOf(materials));
    }

    private static long units(String number) {
        return new BigDecimal(number).multiply(BigDecimal.valueOf(SCALE)).longValueExact();
    }
}
