package io.grasscutter;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read the supplied resource corpus once for the mode and stamina integration checks. */
final class AbilityModeResourceCorpus {
    record Node(Path file, String path, JsonObject data, JsonObject ability) {}

    static final List<Node> modes = new ArrayList<>();
    static final List<Node> costs = new ArrayList<>();
    static final List<Node> nyx = new ArrayList<>();
    private static boolean loaded;

    static synchronized void load() throws IOException {
        if (loaded) return;
        try (var files = Files.walk(Path.of("resources/BinOutput/Ability/Temp"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var text = Files.readString(file);
                if (text.contains("ChangePlayMode")
                        || text.contains("Actor_CostStaminaRatio")
                        || text.contains("\"NyxState\""))
                    collect(file, "", JsonParser.parseString(text), null);
            }
        }
        loaded = true;
    }

    private static void collect(Path file, String path, JsonElement value, JsonObject ability) {
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++)
                collect(file, path + "/" + i, array.get(i), ability);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("abilityName")) ability = object;
            var node = new Node(file, path, object, ability);
            if (object.has("$type") && "ChangePlayMode".equals(object.get("$type").getAsString()))
                modes.add(node);
            if (object.has("properties")
                    && object.get("properties").isJsonObject()
                    && object.getAsJsonObject("properties").has("Actor_CostStaminaRatio"))
                costs.add(node);
            if (object.has("state") && "NyxState".equals(object.get("state").getAsString()))
                nyx.add(node);
            for (var entry : object.entrySet())
                collect(file, path + "/" + entry.getKey(), entry.getValue(), ability);
        }
    }
}
