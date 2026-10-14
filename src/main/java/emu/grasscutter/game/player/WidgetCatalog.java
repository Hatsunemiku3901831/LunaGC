package emu.grasscutter.game.player;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Resource slots and groups; verified follower aliases supply missing exports. */
public final class WidgetCatalog {
    public record GroupAbility(String name, String override) {}

    private record Group(boolean team, List<GroupAbility> abilities) {}

    public record Entry(
            int materialId, List<Integer> tags, String group, List<GroupAbility> abilities) {
        public boolean hasGroup() {
            return group != null && !group.isBlank() && !abilities.isEmpty();
        }
    }

    private final Map<Integer, Entry> entries;
    private static volatile WidgetCatalog current;

    private WidgetCatalog(Map<Integer, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Entry get(int id) {
        return entries.get(id);
    }

    public boolean available() {
        return !entries.isEmpty();
    }

    public static WidgetCatalog current() {
        var value = current;
        if (value != null) return value;
        synchronized (WidgetCatalog.class) {
            if (current == null) reload();
            return current;
        }
    }

    public static synchronized void reload() {
        var loaded =
                load(
                        FileUtils.getResourcePath("BinOutput/WidgetNew/ConfigWidgetNew.json"),
                        FileUtils.getResourcePath("BinOutput/AbilityGroup"));
        if (loaded.available() || current == null) current = loaded;
    }

    public static WidgetCatalog load(Path config, Path groupDirectory) {
        var groups = new HashMap<String, Group>();
        try (var stream = WidgetCatalog.class.getResourceAsStream("/widget-ability-groups.json")) {
            if (stream != null) {
                var aliases =
                        JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                for (var entry : aliases.entrySet()) {
                    var abilities = new ArrayList<GroupAbility>();
                    for (var value : entry.getValue().getAsJsonArray())
                        abilities.add(new GroupAbility(value.getAsString(), ""));
                    groups.put(entry.getKey(), new Group(true, List.copyOf(abilities)));
                }
            }
        } catch (IOException | RuntimeException invalid) {
            Grasscutter.getLogger().warn("Could not load follower widget aliases", invalid);
        }
        if (Files.isDirectory(groupDirectory)) {
            try (var paths = Files.list(groupDirectory)) {
                for (var path :
                        paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                                .sorted()
                                .toList()) {
                    try (var reader = Files.newBufferedReader(path)) {
                        var json = JsonParser.parseReader(reader);
                        if (!json.isJsonObject()) continue;
                        for (var entry : json.getAsJsonObject().entrySet()) {
                            if (!entry.getValue().isJsonObject()) continue;
                            var row = entry.getValue().getAsJsonObject();
                            if (!row.has("targetAbilities")
                                    || !row.get("targetAbilities").isJsonArray()) continue;
                            var abilities = new ArrayList<GroupAbility>();
                            for (var element : row.getAsJsonArray("targetAbilities")) {
                                var ability = element.getAsJsonObject();
                                String name = text(ability, "abilityName");
                                if (name != null && !name.isBlank())
                                    abilities.add(
                                            new GroupAbility(
                                                    name,
                                                    Objects.requireNonNullElse(
                                                            text(ability, "abilityOverride"), "")));
                            }
                            groups.put(
                                    entry.getKey(),
                                    new Group(
                                            "ABILITY_GROUP_SOURCE_WIDGET"
                                                            .equals(
                                                                    text(
                                                                            row,
                                                                            "abilityGroupSourceType"))
                                                    && "ABILITY_GROUP_TARGET_TEAM"
                                                            .equals(
                                                                    text(
                                                                            row,
                                                                            "abilityGroupTargetType")),
                                            List.copyOf(abilities)));
                        }
                    } catch (IOException | RuntimeException invalid) {
                        Grasscutter.getLogger()
                                .warn("Could not read widget ability group {}", path, invalid);
                    }
                }
            } catch (IOException invalid) {
                Grasscutter.getLogger()
                        .warn("Could not read widget group directory {}", groupDirectory, invalid);
            }
        }
        var entries = new HashMap<Integer, Entry>();
        try (var reader = Files.newBufferedReader(config)) {
            var map =
                    JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("widgetConfigMap");
            for (var entry : map.entrySet()) {
                var row = entry.getValue().getAsJsonObject();
                var tags = new TreeSet<Integer>();
                if (row.has("tags"))
                    for (var tag : row.getAsJsonArray("tags")) {
                        if ("ActionPanel".equals(tag.getAsString())) tags.add(0);
                        if ("FlyAttach".equals(tag.getAsString())) tags.add(1);
                    }
                int id = Integer.parseInt(entry.getKey());
                String name = text(row, "abilityGroupName");
                var group = groups.get(name);
                boolean team = row.has("isTeam") && row.get("isTeam").getAsBoolean();
                entries.put(
                        id,
                        new Entry(
                                id,
                                List.copyOf(tags),
                                name,
                                team && group != null && group.team()
                                        ? group.abilities()
                                        : List.of()));
            }
        } catch (IOException | RuntimeException invalid) {
            Grasscutter.getLogger().warn("Could not read widget configuration {}", config, invalid);
            entries.clear();
        }
        return new WidgetCatalog(entries);
    }

    private static String text(JsonObject row, String key) {
        var value = row.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
