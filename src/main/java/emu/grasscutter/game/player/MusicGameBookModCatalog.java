package emu.grasscutter.game.player;

import com.google.gson.JsonParser;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Mod identity and positive exclusive groups from the active client resource export. */
public final class MusicGameBookModCatalog {
    // Current resource text key for the category whose official scores must not be recorded.
    private static final long LOWER_DIFFICULTY_NAME_HASH = 1759822631L;
    private final Map<Integer, Integer> exclusiveGroups;
    private final Path groupsPath;

    private MusicGameBookModCatalog(Map<Integer, Integer> exclusiveGroups, Path groupsPath) {
        this.exclusiveGroups = Map.copyOf(exclusiveGroups);
        this.groupsPath = groupsPath;
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

    /** Read category membership when admitting a run, independently of exclusive groups. */
    public boolean recordsOfficialScore(Collection<Integer> requested) {
        var selected = select(requested);
        if (selected.isEmpty()) return true;
        try {
            var groups = new HashSet<Integer>();
            var members = new HashSet<Integer>();
            Set<Integer> lower = null;
            for (var value :
                    JsonParser.parseString(Files.readString(groupsPath)).getAsJsonArray()) {
                var row = value.getAsJsonObject();
                int id = row.get("id").getAsBigDecimal().intValueExact();
                long name = row.get("nameTextMapHash").getAsBigDecimal().longValueExact();
                if (id <= 0 || !groups.add(id) || name <= 0 || name > 0xffffffffL)
                    throw new IllegalArgumentException("Invalid music book mod category");
                var category = new HashSet<Integer>();
                for (var entry : row.getAsJsonArray("GHLEGPMMNEG")) {
                    int mod = entry.getAsBigDecimal().intValueExact();
                    if (!exclusiveGroups.containsKey(mod) || !members.add(mod))
                        throw new IllegalArgumentException("Invalid music book category member");
                    category.add(mod);
                }
                if (category.isEmpty())
                    throw new IllegalArgumentException("Empty music mod category");
                if (name == LOWER_DIFFICULTY_NAME_HASH) {
                    if (lower != null)
                        throw new IllegalArgumentException("Ambiguous lower-difficulty mods");
                    lower = category;
                }
            }
            if (lower == null || !members.equals(exclusiveGroups.keySet()))
                throw new IllegalArgumentException("Incomplete music book mod categories");
            return Collections.disjoint(selected, lower);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Music game book mod categories are invalid", invalid);
        }
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
            return new MusicGameBookModCatalog(
                    groups, path.resolveSibling("MusicGameBookModGroupExcelConfigData.json"));
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Music game book mod resources are invalid", invalid);
        }
    }
}
