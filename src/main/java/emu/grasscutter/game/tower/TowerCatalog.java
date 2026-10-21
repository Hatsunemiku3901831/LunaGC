package emu.grasscutter.game.tower;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.utils.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Evidence catalogue. "scripted" means resources exist, never a retail-fidelity certificate. */
public final class TowerCatalog {
    private final JsonObject data;

    public TowerCatalog() {
        try (var stream = TowerCatalog.class.getResourceAsStream("/abyss/catalog.json")) {
            if (stream == null) throw new IOException("missing abyss catalogue");
            data =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public JsonObject summary() {
        var result = new JsonObject();
        for (var key :
                List.of(
                        "schemaVersion",
                        "range",
                        "resourceRevision",
                        "sources",
                        "limitations",
                        "periods")) result.add(key, data.get(key).deepCopy());
        return result;
    }

    public JsonObject period(int id) {
        for (var item : data.getAsJsonArray("periods")) {
            var row = item.getAsJsonObject();
            if (row.get("scheduleId").getAsInt() == id) return row.deepCopy();
        }
        throw new IllegalArgumentException("目录没有这一期深渊。");
    }

    public JsonObject details(int id) {
        var result = period(id);
        var floors = new JsonArray();
        for (var fid : result.getAsJsonArray("floorIds")) {
            var floor =
                    data.getAsJsonObject("floors").getAsJsonObject(fid.getAsString()).deepCopy();
            for (var c : floor.getAsJsonArray("chambers")) {
                var chamber = c.getAsJsonObject();
                var scene =
                        data.getAsJsonObject("scenes")
                                .getAsJsonObject(chamber.get("sceneId").getAsString())
                                .deepCopy();
                for (var group : scene.getAsJsonArray("groups")) {
                    for (var monster : group.getAsJsonObject().getAsJsonArray("monsters")) {
                        var m = monster.getAsJsonObject();
                        var stats =
                                data.getAsJsonObject("monsterStats")
                                        .get(m.get("monsterId").getAsString());
                        if (stats != null) m.add("stats", stats.deepCopy());
                    }
                }
                chamber.add("scene", scene);
            }
            floors.add(floor);
        }
        result.add("floors", floors);
        return result;
    }

    /**
     * Recheck the files on every apply; a catalogue row alone cannot authorize an empty dungeon.
     */
    public void validate(TowerBattleProfile profile) throws IOException {
        var detail = details(profile.scheduleId());
        if (!detail.get("status").getAsString().equals("scripted"))
            throw new IllegalArgumentException("这一期缺少完整出怪脚本，已跳过。请查看缺失清单。");
        if (!GameData.getTowerScheduleDataMap().containsKey(profile.scheduleId()))
            throw new IllegalArgumentException("运行资源没有该排期。");
        for (var entry : data.getAsJsonObject("inputs").entrySet()) {
            var path = FileUtils.getResourcePath(entry.getKey());
            if (!Files.isRegularFile(path) || !hash(path).equals(entry.getValue().getAsString()))
                throw new IllegalArgumentException("资源表与目录不一致，请重新导出并重启：" + entry.getKey());
        }
        var targets = new HashSet<String>();
        var checked = new HashSet<String>();
        for (var f : detail.getAsJsonArray("floors")) {
            for (var c : f.getAsJsonObject().getAsJsonArray("chambers")) {
                var chamber = c.getAsJsonObject();
                var level = GameData.getTowerLevelDataMap().get(chamber.get("levelId").getAsInt());
                if (level == null || level.getDungeonId() != chamber.get("dungeonId").getAsInt())
                    throw new IllegalArgumentException("运行关卡与目录不一致，请重新导出目录。");
                var scene = chamber.getAsJsonObject("scene");
                for (var entry : scene.getAsJsonObject("files").entrySet()) {
                    if (!checked.add(entry.getKey())) continue;
                    var relative = entry.getKey().replaceFirst("^Scripts/", "");
                    var path = FileUtils.getScriptPath(relative);
                    if (!Files.isRegularFile(path)
                            || !hash(path).equals(entry.getValue().getAsString()))
                        throw new IllegalArgumentException("脚本缺失或已改变，请重新导出目录：" + entry.getKey());
                }
                for (var g : scene.getAsJsonArray("groups")) {
                    int group = g.getAsJsonObject().get("groupId").getAsInt();
                    for (var m : g.getAsJsonObject().getAsJsonArray("monsters")) {
                        var monster = m.getAsJsonObject();
                        int config = monster.get("configId").getAsInt();
                        targets.add(group + ":" + config);
                        var override = profile.override(group, config);
                        int mid =
                                override != null && override.monsterId() > 0
                                        ? override.monsterId()
                                        : monster.get("monsterId").getAsInt();
                        if (!GameData.getMonsterDataMap().containsKey(mid))
                            throw new IllegalArgumentException("运行资源没有怪物：" + mid);
                        int effectiveLevel =
                                profile.level(level.getMonsterLevel() + 1, group, config);
                        if (!GameData.getMonsterCurveDataMap().containsKey(effectiveLevel))
                            throw new IllegalArgumentException("运行资源没有等级成长曲线：" + effectiveLevel);
                    }
                }
            }
        }
        for (var m : profile.monsters()) {
            if (!targets.contains(m.groupId() + ":" + m.configId()))
                throw new IllegalArgumentException(
                        "覆盖目标不属于本期第 8–12 层：" + m.groupId() + ":" + m.configId());
        }
    }

    private static String hash(Path path) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
