package emu.grasscutter.game.managers.mapmark;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketMarkMapRsp;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.lang.Language;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Material pins share the normal map budget; ownership is metadata, never a display-name prefix.
 */
public final class MaterialTracking {
    private record Point(int sceneId, Position position) {}

    private final Supplier<Path> file;
    private final Consumer<Player> persist;
    private String lastContents;
    private Map<Integer, List<Point>> points = Map.of();
    private Map<String, Set<Integer>> aliases = Map.of();

    public MaterialTracking() {
        this(() -> FileUtils.getDataPath("specialty_materials_points.json"), Player::save);
    }

    public MaterialTracking(Path file, Consumer<Player> persist) {
        this(() -> file, persist);
        Objects.requireNonNull(file);
    }

    private MaterialTracking(Supplier<Path> file, Consumer<Player> persist) {
        this.file = Objects.requireNonNull(file);
        this.persist = Objects.requireNonNull(persist);
    }

    private static boolean finite(Position p) {
        return p != null
                && Float.isFinite(p.getX())
                && Float.isFinite(p.getY())
                && Float.isFinite(p.getZ());
    }

    private synchronized void load() {
        String contents;
        try {
            contents = Files.readString(file.get());
        } catch (IOException unavailable) {
            throw new IllegalStateException("材料点数据无法读取。", unavailable);
        }
        if (contents.equals(lastContents)) return;
        var next = new HashMap<Integer, List<Point>>();
        var names = new HashMap<String, Set<Integer>>();
        try {
            var rows = JsonParser.parseString(contents).getAsJsonArray();
            if (rows.size() > 100000) throw new IllegalArgumentException("材料点数量过大。");
            for (var entry : rows) {
                var row = entry.getAsJsonObject();
                int item = row.get("itemId").getAsInt();
                int scene = row.has("sceneId") ? row.get("sceneId").getAsInt() : 3;
                var pos =
                        new Position(
                                row.get("x").getAsFloat(),
                                row.has("y") ? row.get("y").getAsFloat() : 0,
                                row.get("z").getAsFloat());
                if (item <= 0 || scene <= 0 || !finite(pos))
                    throw new IllegalArgumentException("材料点包含无效 ID 或坐标。");
                next.computeIfAbsent(item, k -> new ArrayList<>()).add(new Point(scene, pos));
                if (row.has("name")) {
                    var name = row.get("name").getAsString();
                    int suffix = name.lastIndexOf('#');
                    if (suffix >= 0) name = name.substring(0, suffix);
                    if (!name.isBlank())
                        names.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new HashSet<>())
                                .add(item);
                }
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("材料点数据格式有误。", invalid);
        }
        next.replaceAll((k, v) -> List.copyOf(v));
        points = Map.copyOf(next);
        aliases = Map.copyOf(names);
        lastContents = contents;
    }

    public synchronized int resolve(String query) {
        load();
        if (query == null || query.isBlank()) return 0;
        String token = query.trim();
        try {
            int id = Integer.parseInt(token);
            return points.containsKey(id) ? id : 0;
        } catch (NumberFormatException ignored) {
        }
        var matches = new HashSet<>(aliases.getOrDefault(token.toLowerCase(Locale.ROOT), Set.of()));
        for (int id : points.keySet()) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null) continue;
            var texts = Language.getTextMapKey(item.getNameTextMapHash());
            if (texts == null) continue;
            for (var locale : List.of("CHS", "EN"))
                if (token.equalsIgnoreCase(texts.get(locale))) matches.add(id);
        }
        return matches.size() == 1 ? matches.iterator().next() : 0;
    }

    private static String key(Position pos) {
        return "x" + (int) pos.getX() + "z" + (int) pos.getZ();
    }

    private static double distance(Position a, Position b) {
        double x = (double) a.getX() - b.getX(),
                y = (double) a.getY() - b.getY(),
                z = (double) a.getZ() - b.getZ();
        return x * x + y * y + z * z;
    }

    private static String name(int itemId) {
        var data = GameData.getItemDataMap().get(itemId);
        if (data != null) {
            var texts = Language.getTextMapKey(data.getNameTextMapHash());
            var label = texts == null ? null : texts.get("CHS");
            if (label != null && !label.isBlank()) return label;
        }
        return "材料 " + itemId;
    }

    private static void valid(Player player) {
        if (player == null
                || player.getSession() == null
                || player.getScene() == null
                || player.getScene().getSceneType() != SceneType.SCENE_WORLD
                || !finite(player.getPosition())) throw new IllegalStateException("请在大世界中选择在线玩家。");
    }

    public int track(Player player, int itemId) {
        List<Point> available;
        synchronized (this) {
            load();
            available = points.getOrDefault(itemId, List.of());
        }
        if (player == null) throw new IllegalArgumentException("请选择目标玩家。");
        synchronized (player.getSceneTransferLock()) {
            valid(player);
            var origin = new Position(player.getPosition());
            var ordered =
                    available.stream()
                            .filter(p -> p.sceneId() == player.getSceneId())
                            .sorted(Comparator.comparingDouble(p -> distance(p.position(), origin)))
                            .toList();
            if (ordered.isEmpty()) return 0;
            String label = name(itemId);
            synchronized (player) {
                var marks = player.getMapMarks();
                var next = new HashMap<>(marks);
                next.entrySet()
                        .removeIf(
                                e -> e.getValue() != null && e.getValue().getTrackingItemId() > 0);
                int added = 0;
                for (var point : ordered) {
                    if (next.size() >= MapMarksManager.mapMarkMaxCount) break;
                    String k = key(point.position());
                    if (next.containsKey(k)) continue;
                    var mark =
                            MapMark.materialPoint(
                                    point.sceneId(),
                                    point.position(),
                                    itemId,
                                    label + " #" + (added + 1));
                    next.put(k, mark);
                    added++;
                }
                if (added == 0) return 0;
                publish(player, marks, next);
                return added;
            }
        }
    }

    public int clear(Player player) {
        if (player == null) throw new IllegalArgumentException("请选择目标玩家。");
        synchronized (player) {
            if (player.getSession() == null) throw new IllegalStateException("请选择在线玩家。");
            var marks = player.getMapMarks();
            var next = new HashMap<>(marks);
            next.entrySet()
                    .removeIf(e -> e.getValue() != null && e.getValue().getTrackingItemId() > 0);
            int removed = marks.size() - next.size();
            if (removed == 0) return 0;
            publish(player, marks, next);
            return removed;
        }
    }

    private void publish(Player player, Map<String, MapMark> marks, Map<String, MapMark> next) {
        var previous = new HashMap<>(marks);
        marks.clear();
        marks.putAll(next);
        try {
            persist.accept(player);
        } catch (RuntimeException failure) {
            marks.clear();
            marks.putAll(previous);
            throw failure;
        }
        player.sendPacket(new PacketMarkMapRsp(new HashMap<>(marks)));
    }
}
