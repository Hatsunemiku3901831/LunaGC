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
            int id, int songId, int maximumScore, int noteCount, int stars, boolean guide) {}

    private final Map<Integer, Beatmap> beatmaps;

    private MusicGameBookCatalog(Map<Integer, Beatmap> beatmaps) {
        this.beatmaps = Map.copyOf(beatmaps);
    }

    public Beatmap beatmap(int id) {
        return beatmaps.get(id);
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
                result.put(
                        id,
                        new Beatmap(
                                id,
                                songOf.getOrDefault(id, 0),
                                positive(score, "JCLCGFHNJMB"),
                                positive(score, "noteCount"),
                                positive(score, "ICFJJKDLPEA"),
                                guideIds.contains(id)));
            }
            return new MusicGameBookCatalog(result);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException(
                    "Music game book catalog resources are invalid", invalid);
        }
    }
}
