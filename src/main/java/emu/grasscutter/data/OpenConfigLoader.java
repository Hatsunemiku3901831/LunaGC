package emu.grasscutter.data;

import com.google.gson.JsonParser;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.utils.JsonUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Current resource definitions take priority over legacy compiled cache entries. */
final class OpenConfigLoader {
    private OpenConfigLoader() {}

    static Map<String, OpenConfigEntry> load(Path cache, Path... directories) {
        var entries = new TreeMap<String, OpenConfigEntry>();
        loadCache(cache, entries);
        for (var directory : directories) {
            if (!Files.isDirectory(directory)) {
                Grasscutter.getLogger()
                        .warn("Open config resource directory unavailable: {}", directory);
                continue;
            }
            try (var files = Files.list(directory)) {
                for (var file :
                        files.filter(Files::isRegularFile)
                                .filter(p -> p.getFileName().toString().endsWith(".json"))
                                .sorted()
                                .toList()) loadSource(file, entries);
            } catch (IOException | RuntimeException e) {
                Grasscutter.getLogger()
                        .warn(
                                "Unable to list open config directory {}: {}",
                                directory,
                                e.getMessage());
            }
        }
        return entries;
    }

    private static void loadCache(Path cache, Map<String, OpenConfigEntry> entries) {
        if (!Files.isRegularFile(cache)) return;
        try (var reader = Files.newBufferedReader(cache)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonArray())
                throw new IllegalArgumentException("Expected a compiled entry array");
            for (var row : root.getAsJsonArray()) {
                try {
                    if (!row.isJsonObject())
                        throw new IllegalArgumentException("Expected an entry object");
                    var entry = JsonUtils.decode(row, OpenConfigEntry.class);
                    if (entry == null || entry.getName() == null || entry.getName().isBlank())
                        throw new IllegalArgumentException("Missing entry name");
                    entries.put(entry.getName(), entry);
                } catch (RuntimeException e) {
                    Grasscutter.getLogger()
                            .warn(
                                    "Ignoring invalid compiled open config row in {}: {}",
                                    cache,
                                    e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            Grasscutter.getLogger()
                    .warn("Unable to read compiled open config {}: {}", cache, e.getMessage());
        }
    }

    private static void loadSource(Path file, Map<String, OpenConfigEntry> entries) {
        try (var reader = Files.newBufferedReader(file)) {
            var root = JsonParser.parseReader(reader);
            // ConfigAffix_Common.json is an empty array placeholder in the current export.
            if (root.isJsonArray() && root.getAsJsonArray().isEmpty()) return;
            if (!root.isJsonObject())
                throw new IllegalArgumentException("Expected a definition object");
            for (var entry : root.getAsJsonObject().entrySet()) {
                String name = entry.getKey();
                if (name.isBlank() || !entry.getValue().isJsonArray()) {
                    Grasscutter.getLogger()
                            .warn("Ignoring invalid open config definition {} in {}", name, file);
                    continue;
                }
                var rows = entry.getValue().getAsJsonArray();
                var valid = new ArrayList<ResourceLoader.OpenConfigData>();
                for (int i = 0; i < rows.size(); i++) {
                    try {
                        if (!rows.get(i).isJsonObject())
                            throw new IllegalArgumentException("Expected an effect object");
                        var row =
                                JsonUtils.decode(rows.get(i), ResourceLoader.OpenConfigData.class);
                        if (row == null || row.$type == null || row.$type.isBlank())
                            throw new IllegalArgumentException("Missing effect type");
                        valid.add(row);
                    } catch (RuntimeException e) {
                        Grasscutter.getLogger()
                                .warn(
                                        "Ignoring invalid open config effect {}[{}] in {}: {}",
                                        name,
                                        i,
                                        file,
                                        e.getMessage());
                    }
                }
                // An explicit empty definition clears the old effects; wholly unreadable rows do
                // not.
                if (rows.isEmpty() || !valid.isEmpty())
                    entries.put(
                            name,
                            new OpenConfigEntry(
                                    name, valid.toArray(new ResourceLoader.OpenConfigData[0])));
            }
        } catch (IOException | RuntimeException e) {
            Grasscutter.getLogger()
                    .warn("Unable to read open config resource {}: {}", file, e.getMessage());
        }
    }
}
