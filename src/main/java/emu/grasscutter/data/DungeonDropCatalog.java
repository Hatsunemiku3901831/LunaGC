package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import java.io.*;
import java.util.*;

/** Loads configured legacy rewards as one snapshot, keeping the previous catalog on failure. */
public final class DungeonDropCatalog {
    private DungeonDropCatalog() {}

    public static synchronized int reload() throws IOException {
        try (var reader = DataLoader.loadReader("DungeonDrop.json")) {
            return reload(reader);
        }
    }

    public static synchronized int reload(Reader reader) throws IOException {
        var snapshot = new Int2ObjectOpenHashMap<List<DungeonDropEntry>>();
        try {
            var rows = JsonUtils.loadToList(reader, DungeonDrop.class);
            require(rows != null && !rows.isEmpty(), "Empty dungeon drop catalog");
            for (var row : rows) {
                require(row != null && row.getDungeonId() > 0, "Invalid dungeon ID");
                int id = row.getDungeonId();
                require(!snapshot.containsKey(id), "Duplicate dungeon ID " + id);
                require(
                        row.getDrops() != null && !row.getDrops().isEmpty(),
                        "Empty drops for " + id);
                var entries = new ArrayList<DungeonDropEntry>();
                for (var entry : row.getDrops()) {
                    require(entry != null, "Null drop group for " + id);
                    var counts = entry.getCounts();
                    require(counts != null && !counts.isEmpty(), "Missing counts for " + id);
                    int previous = -1;
                    for (var count : counts) {
                        require(
                                count != null
                                        && count >= 0
                                        && count > previous
                                        && count < Integer.MAX_VALUE,
                                "Invalid counts for " + id);
                        previous = count;
                    }
                    // Sparse count arrays describe an inclusive range, matching rollRewards.
                    long candidates = (long) counts.get(counts.size() - 1) - counts.get(0) + 1;
                    var items = entry.getItems();
                    require(
                            items != null
                                    && !items.isEmpty()
                                    && items.stream().allMatch(item -> item != null && item > 0),
                            "Invalid items for " + id);
                    validateWeights(entry.getProbabilities(), candidates, id);
                    validateWeights(entry.getItemProbabilities(), items.size(), id);
                    var copy = new DungeonDropEntry();
                    copy.setCounts(List.copyOf(counts));
                    copy.setItems(List.copyOf(items));
                    copy.setProbabilities(copyWeights(entry.getProbabilities()));
                    copy.setItemProbabilities(copyWeights(entry.getItemProbabilities()));
                    copy.setMpDouble(entry.isMpDouble());
                    entries.add(copy);
                }
                snapshot.put(id, List.copyOf(entries));
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid DungeonDrop.json: " + invalid.getMessage(), invalid);
        }
        GameData.installDungeonDropData(snapshot);
        Grasscutter.getLogger()
                .info("Loaded {} dungeon reward tables from DungeonDrop.json", snapshot.size());
        return snapshot.size();
    }

    private static List<Integer> copyWeights(List<Integer> weights) {
        return weights == null ? null : List.copyOf(weights);
    }

    private static void validateWeights(List<Integer> weights, long size, int id)
            throws IOException {
        if (weights == null) return;
        require(weights.size() == size, "Weight count mismatch for " + id);
        long sum = 0;
        for (var weight : weights) {
            require(weight != null && weight >= 0, "Invalid weight for " + id);
            sum += weight;
        }
        require(sum > 0 && sum < Integer.MAX_VALUE, "Invalid weight total for " + id);
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
