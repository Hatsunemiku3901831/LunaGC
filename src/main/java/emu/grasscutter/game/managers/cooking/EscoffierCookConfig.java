package emu.grasscutter.game.managers.cooking;

import com.google.gson.*;

import java.util.*;

/** Confirmed cooking constants; missing reward tables are not replaced by a guessed food pool. */
public final class EscoffierCookConfig {
    private static final String PARAM = "CONST_VALUE_AVATAR_ESCOFFIER_SKILL_COOK_PARAM";
    private static final String DROP = "CONST_VALUE_AVATAR_ESCOFFIER_SKILL_COOK_DROP_PARAM";

    public record Tier(int weight, int dropId) {}

    private final int weeklyLimit, avatarId;
    private final long totalWeight;
    private final List<Tier> tiers;

    private EscoffierCookConfig(int weeklyLimit, int avatarId, List<Tier> tiers) {
        this.weeklyLimit = weeklyLimit;
        this.avatarId = avatarId;
        this.tiers = List.copyOf(tiers);
        this.totalWeight = tiers.stream().mapToLong(Tier::weight).sum();
    }

    public int weeklyLimit() {
        return weeklyLimit;
    }

    public int avatarId() {
        return avatarId;
    }

    public long totalWeight() {
        return totalWeight;
    }

    public List<Tier> tiers() {
        return tiers;
    }

    public static EscoffierCookConfig parse(JsonArray rows) {
        if (rows == null) throw new IllegalArgumentException("Missing cooking constants");
        JsonArray param = null, drop = null;
        for (var row : rows) {
            if (!row.isJsonObject()) continue;
            var object = row.getAsJsonObject();
            var name = object.get("name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString())
                continue;
            var key = name.getAsString();
            if (!PARAM.equals(key) && !DROP.equals(key)) continue;
            var value = object.get("value");
            if (value == null || !value.isJsonArray())
                throw new IllegalArgumentException("Invalid " + key);
            if (PARAM.equals(key)) {
                if (param != null) throw new IllegalArgumentException("Duplicate " + PARAM);
                param = value.getAsJsonArray();
            } else {
                if (drop != null) throw new IllegalArgumentException("Duplicate " + DROP);
                drop = value.getAsJsonArray();
            }
        }
        if (param == null || param.size() < 4 || drop == null || drop.size() != 6)
            throw new IllegalArgumentException("Incomplete cooking constants");
        // Other PARAM positions have not been paired with their client consumers yet.
        int limit = positive(param.get(0)), avatar = positive(param.get(3));
        var tiers = new ArrayList<Tier>(3);
        for (int i = 0; i < 3; i++) {
            int weight = integer(drop.get(i));
            if (weight < 0) throw new IllegalArgumentException("Negative cooking weight");
            tiers.add(new Tier(weight, positive(drop.get(i + 3))));
        }
        var config = new EscoffierCookConfig(limit, avatar, tiers);
        if (config.totalWeight == 0) throw new IllegalArgumentException("Empty cooking weights");
        return config;
    }

    private static int positive(JsonElement value) {
        int result = integer(value);
        if (result <= 0) throw new IllegalArgumentException("Nonpositive cooking constant");
        return result;
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive())
            throw new IllegalArgumentException("Invalid cooking integer");
        return Integer.parseInt(value.getAsString());
    }
}
