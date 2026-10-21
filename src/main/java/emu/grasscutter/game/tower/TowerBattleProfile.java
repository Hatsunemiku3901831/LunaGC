package emu.grasscutter.game.tower;

import java.util.*;

/** Immutable settings captured at team selection, retained across waves and retries. */
public record TowerBattleProfile(
        int scheduleId,
        double hp,
        double attack,
        double defense,
        int levelOffset,
        List<MonsterOverride> monsters) {
    public static final TowerBattleProfile DEFAULT =
            new TowerBattleProfile(0, 1, 1, 1, 0, List.of());

    public TowerBattleProfile {
        if (scheduleId < 0) throw new IllegalArgumentException("排期 ID 无效。");
        multiplier(hp);
        multiplier(attack);
        multiplier(defense);
        if (levelOffset < -90 || levelOffset > 100)
            throw new IllegalArgumentException("等级偏移须在 -90 至 100 之间。");
        monsters = monsters == null ? List.of() : List.copyOf(monsters);
        if (monsters.size() > 500) throw new IllegalArgumentException("最多配置 500 个怪物覆盖。");
        var keys = new HashSet<String>();
        for (var monster : monsters) {
            if (!keys.add(monster.groupId() + ":" + monster.configId()))
                throw new IllegalArgumentException("同一组内的怪物配置不能重复。");
        }
    }

    private static void multiplier(double value) {
        if (!Double.isFinite(value) || value < 0.05 || value > 20)
            throw new IllegalArgumentException("血量、攻击和防御倍率须为 0.05 至 20 的有限数字。");
    }

    public record MonsterOverride(
            int groupId,
            int configId,
            int monsterId,
            int level,
            double hp,
            double attack,
            double defense) {
        public MonsterOverride {
            if (groupId <= 0 || configId <= 0 || monsterId < 0 || level < 0 || level > 200)
                throw new IllegalArgumentException("怪物覆盖的组、配置 ID、种类或等级无效。");
            multiplier(hp);
            multiplier(attack);
            multiplier(defense);
        }
    }

    public MonsterOverride override(int group, int config) {
        return monsters.stream()
                .filter(m -> m.groupId() == group && m.configId() == config)
                .findFirst()
                .orElse(null);
    }

    public int level(int base, int group, int config) {
        var override = override(group, config);
        return override != null && override.level() > 0
                ? override.level()
                : Math.max(1, Math.min(200, base + levelOffset));
    }

    public static double floorHp(int floor) {
        return floor >= 12 ? 2.5 : floor >= 8 ? 2 : floor >= 3 ? 1.5 : 1;
    }

    public boolean custom() {
        return hp != 1 || attack != 1 || defense != 1 || levelOffset != 0 || !monsters.isEmpty();
    }

    /**
     * Gson 2.9 cannot safely construct records; all imported data goes through our constructors.
     */
    public static TowerBattleProfile fromJson(com.google.gson.JsonElement input) {
        var object = input.getAsJsonObject();
        var overrides = new ArrayList<MonsterOverride>();
        for (var item : object.getAsJsonArray("monsters")) {
            var m = item.getAsJsonObject();
            overrides.add(
                    new MonsterOverride(
                            integer(m, "groupId"),
                            integer(m, "configId"),
                            integer(m, "monsterId"),
                            integer(m, "level"),
                            m.get("hp").getAsDouble(),
                            m.get("attack").getAsDouble(),
                            m.get("defense").getAsDouble()));
        }
        return new TowerBattleProfile(
                integer(object, "scheduleId"),
                object.get("hp").getAsDouble(),
                object.get("attack").getAsDouble(),
                object.get("defense").getAsDouble(),
                integer(object, "levelOffset"),
                overrides);
    }

    private static int integer(com.google.gson.JsonObject object, String key) {
        return object.get(key).getAsBigDecimal().intValueExact();
    }
}
