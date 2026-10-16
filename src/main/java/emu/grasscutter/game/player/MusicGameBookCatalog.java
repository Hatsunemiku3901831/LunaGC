package emu.grasscutter.game.player;

import com.google.gson.*;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Official beatmaps and score ceilings, linked against the active song, album and guide exports.
 */
public final class MusicGameBookCatalog {
    public record Beatmap(
            int id,
            int songId,
            int maximumScore,
            int noteCount,
            int stars,
            boolean guide,
            int difficulty,
            List<Integer> rewardIds) {}

    private final Map<Integer, Beatmap> beatmaps;
    private final Path directory;
    private final int passPercent;

    private MusicGameBookCatalog(Map<Integer, Beatmap> beatmaps, Path directory, int passPercent) {
        this.beatmaps = Map.copyOf(beatmaps);
        this.directory = directory;
        this.passPercent = passPercent;
    }

    public Beatmap beatmap(int id) {
        return beatmaps.get(id);
    }

    public boolean passes(Beatmap map, long score) {
        return score >= 0
                && score <= map.maximumScore()
                && score * 100 >= (long) map.maximumScore() * passPercent;
    }

    public List<Beatmap> standardClearTargets(Beatmap map) {
        if (map.guide()) return List.of(map);
        return beatmaps.values().stream()
                .filter(
                        candidate ->
                                !candidate.guide()
                                        && candidate.songId() == map.songId()
                                        && candidate.difficulty() <= map.difficulty())
                .sorted(Comparator.comparingInt(Beatmap::difficulty).thenComparingInt(Beatmap::id))
                .toList();
    }

    /**
     * Only read the large reward export when a first clear actually has unpaid resource rewards.
     */
    public Map<Integer, Integer> rewards(Collection<Beatmap> maps) {
        var wanted = new HashSet<Integer>();
        for (var map : maps) wanted.addAll(map.rewardIds());
        if (wanted.isEmpty()) return Map.of();
        try {
            var found = new HashMap<Integer, JsonObject>();
            var rows =
                    JsonParser.parseString(
                                    Files.readString(
                                            directory.resolve("RewardExcelConfigData.json")))
                            .getAsJsonArray();
            for (var value : rows) {
                var row = value.getAsJsonObject();
                int id = row.get("rewardId").getAsBigDecimal().intValueExact();
                if (wanted.contains(id) && found.put(id, row) != null)
                    throw new IllegalArgumentException("Duplicate music book reward");
            }
            if (!found.keySet().equals(wanted))
                throw new IllegalArgumentException("Missing music book reward");
            var items = new TreeMap<Integer, Integer>();
            for (var map : maps)
                for (int id : map.rewardIds()) {
                    var reward = found.get(id);
                    // Current book rewards are virtual item entries. Other reward mechanisms need
                    // an explicit delivery path before they can share this single Player commit.
                    for (var field :
                            List.of(
                                    "hcoin",
                                    "scoin",
                                    "playerExp",
                                    "resin",
                                    "MEBGMPIJIDE",
                                    "NBNIDEOMGOD"))
                        if (reward.has(field) && reward.get(field).getAsBigDecimal().signum() != 0)
                            throw new IllegalArgumentException(
                                    "Unsupported music book reward component");
                    for (var entry : reward.getAsJsonArray("rewardItemList")) {
                        var item = entry.getAsJsonObject();
                        int count = item.get("itemCount").getAsBigDecimal().intValueExact();
                        if (count < 0)
                            throw new IllegalArgumentException("Negative music book reward");
                        if (count == 0) continue;
                        int itemId = positive(item, "itemId");
                        if (itemId != 201 && itemId != 207)
                            throw new IllegalArgumentException(
                                    "Unsupported music book reward item");
                        items.put(itemId, Math.addExact(items.getOrDefault(itemId, 0), count));
                    }
                }
            return Map.copyOf(items);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Music game book rewards are invalid", invalid);
        }
    }

    private static int passPercent(JsonArray constants) {
        JsonObject scores = null;
        for (var value : constants) {
            var row = value.getAsJsonObject();
            if (row.has("name")
                    && row.get("name").getAsString().equals("MGB_CONST_VALUE_SCORE_RATIO")) {
                if (scores != null)
                    throw new IllegalArgumentException("Duplicate music book score ratios");
                scores = row;
            }
        }
        if (scores == null) throw new IllegalArgumentException("Missing music book score ratios");
        int first = 0, last = 0;
        for (var value : scores.getAsJsonArray("value")) {
            var text = value.getAsString().trim();
            if (text.isEmpty()) continue;
            int threshold = new java.math.BigDecimal(text).intValueExact();
            if (threshold <= last || threshold > 100)
                throw new IllegalArgumentException("Invalid music book score ratios");
            if (first == 0) first = threshold;
            last = threshold;
        }
        if (first == 0 || last != 100)
            throw new IllegalArgumentException("Incomplete music book score ratios");
        return first;
    }

    public static MusicGameBookCatalog current() {
        return load(FileUtils.getResourcePath("ExcelBinOutput"));
    }

    private static JsonArray rows(Path directory, String name) throws IOException {
        return JsonParser.parseString(
                        Files.readString(
                                directory.resolve("MusicGameBook" + name + "ExcelConfigData.json")))
                .getAsJsonArray();
    }

    private static int positive(JsonObject row, String field) {
        int value = row.get(field).getAsBigDecimal().intValueExact();
        if (value <= 0) throw new IllegalArgumentException("Invalid music book " + field);
        return value;
    }

    private static Map<Integer, JsonObject> index(JsonArray rows, String field) {
        var result = new HashMap<Integer, JsonObject>();
        for (var value : rows) {
            var row = value.getAsJsonObject();
            if (result.put(positive(row, field), row) != null)
                throw new IllegalArgumentException("Duplicate music book " + field);
        }
        return result;
    }

    public static MusicGameBookCatalog load(Path directory) {
        try {
            var basic = index(rows(directory, "Beatmap"), "id");
            var scores = index(rows(directory, "BeatmapSettle"), "ELHHKOMFEMG");
            var songs = index(rows(directory, "Song"), "MOGEKJFDIFE");
            var albums = index(rows(directory, "Album"), "BCMJONACOPD");
            var guides = index(rows(directory, "Guide"), "guideID");
            int passPercent = passPercent(rows(directory, "ConstValue"));
            if (basic.isEmpty() || !basic.keySet().equals(scores.keySet()))
                throw new IllegalArgumentException(
                        "Music book score export does not match beatmaps");
            var songAlbums = new HashMap<Integer, Integer>();
            for (var entry : albums.entrySet()) {
                for (var song : entry.getValue().getAsJsonArray("GEGLKCJBPGA")) {
                    int id = song.getAsBigDecimal().intValueExact();
                    if (songAlbums.put(id, entry.getKey()) != null || !songs.containsKey(id))
                        throw new IllegalArgumentException("Invalid music book album song");
                }
            }
            if (!songAlbums.keySet().equals(songs.keySet()))
                throw new IllegalArgumentException("Music book album export is incomplete");
            var songOf = new HashMap<Integer, Integer>();
            for (var entry : songs.entrySet()) {
                var song = entry.getValue();
                if (!Objects.equals(songAlbums.get(entry.getKey()), positive(song, "BCMJONACOPD")))
                    throw new IllegalArgumentException("Music book song belongs to another album");
                var ids = song.getAsJsonArray("EEOMALGDFFI");
                if (ids.isEmpty())
                    throw new IllegalArgumentException("Music book song has no beatmap");
                for (var value : ids) {
                    int id = value.getAsBigDecimal().intValueExact();
                    var map = basic.get(id);
                    if (songOf.put(id, entry.getKey()) != null
                            || map == null
                            || positive(map, "MOGEKJFDIFE") != entry.getKey())
                        throw new IllegalArgumentException("Music book song beatmap mismatch");
                }
            }
            var guideIds = new HashSet<Integer>();
            for (var guide : guides.values()) {
                int id = positive(guide, "FFDDBLGFEKC");
                var map = basic.get(id);
                if (!guideIds.add(id)
                        || songOf.containsKey(id)
                        || map == null
                        || map.has("MOGEKJFDIFE"))
                    throw new IllegalArgumentException("Invalid music book guide beatmap");
            }
            var result = new HashMap<Integer, Beatmap>();
            for (var entry : basic.entrySet()) {
                int id = entry.getKey();
                var map = entry.getValue();
                var score = scores.get(id);
                if ((!songOf.containsKey(id) && !guideIds.contains(id))
                        || map.get("jsonPath").getAsString().isBlank())
                    throw new IllegalArgumentException("Unreferenced music book beatmap");
                var rewardIds = new ArrayList<Integer>();
                if (map.has("rewardIdList"))
                    for (var value : map.getAsJsonArray("rewardIdList")) {
                        int reward = value.getAsBigDecimal().intValueExact();
                        if (reward <= 0 || rewardIds.contains(reward))
                            throw new IllegalArgumentException(
                                    "Invalid music book reward reference");
                        rewardIds.add(reward);
                    }
                result.put(
                        id,
                        new Beatmap(
                                id,
                                songOf.getOrDefault(id, 0),
                                positive(score, "JCLCGFHNJMB"),
                                positive(score, "noteCount"),
                                positive(score, "ICFJJKDLPEA"),
                                guideIds.contains(id),
                                positive(map, "MMKEJFCFMBN"),
                                List.copyOf(rewardIds)));
            }
            return new MusicGameBookCatalog(result, directory, passPercent);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException(
                    "Music game book catalog resources are invalid", invalid);
        }
    }
}
