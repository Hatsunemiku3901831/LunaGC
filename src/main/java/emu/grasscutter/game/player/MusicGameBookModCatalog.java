package emu.grasscutter.game.player;

import com.google.gson.JsonParser;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Mod identity and positive exclusive groups from the active client resource export. */
public final class MusicGameBookModCatalog {
    private final Map<Integer, Integer> exclusiveGroups;

    private MusicGameBookModCatalog(Map<Integer, Integer> exclusiveGroups) {
        this.exclusiveGroups = Map.copyOf(exclusiveGroups);
    }

    /** The native settings sender sorts a unique selection; group zero is unrestricted. */
    public List<Integer> select(Collection<Integer> requested) {
        var selection = new TreeSet<Integer>();
        var groups = new HashSet<Integer>();
        for (var id : requested) {
            var group = id == null ? null : exclusiveGroups.get(id);
            if (group == null || !selection.add(id) || group > 0 && !groups.add(group))
                throw new IllegalArgumentException("Invalid or incompatible music book mods");
        }
        return List.copyOf(selection);
    }

    public static MusicGameBookModCatalog current() {
        return load(
                FileUtils.getResourcePath("ExcelBinOutput/MusicGameBookModExcelConfigData.json"));
    }

    public static MusicGameBookModCatalog load(Path path) {
        try {
            var groups = new HashMap<Integer, Integer>();
            for (var value : JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                var row = value.getAsJsonObject();
                int id = row.get("id").getAsBigDecimal().intValueExact();
                int group =
                        row.has("KKLNILPFAIP")
                                ? row.get("KKLNILPFAIP").getAsBigDecimal().intValueExact()
                                : 0;
                if (id <= 0 || group < 0 || groups.put(id, group) != null)
                    throw new IllegalArgumentException("Invalid music book mod definition");
            }
            if (groups.isEmpty()) throw new IllegalArgumentException("Missing music book mods");
            return new MusicGameBookModCatalog(groups);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Music game book mod resources are invalid", invalid);
        }
    }
}
