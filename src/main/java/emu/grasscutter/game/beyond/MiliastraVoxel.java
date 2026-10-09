package emu.grasscutter.game.beyond;

import com.google.gson.*;
import java.util.*;

/** Converts the reference Blender voxel-array format using the shared block palette. */
public final class MiliastraVoxel {
    public record Options(float unit, int alphaThreshold, MiliastraGenerator.Vector origin) {
        public static Options defaults() {
            return new Options(.01f, 128, new MiliastraGenerator.Vector(0, 0, 0));
        }
    }

    private record Grid(int x, int y, int z) {}

    private MiliastraVoxel() {}

    public static List<MiliastraGenerator.Block> model(byte[] bytes, Options options) {
        if (bytes.length > 8 * 1024 * 1024) throw bad("体素 JSON 最大为 8 MiB。");
        if (options == null
                || !Float.isFinite(options.unit())
                || options.unit() <= 0
                || options.alphaThreshold() < 0
                || options.alphaThreshold() > 255
                || !finite(options.origin())) throw bad("体素边长须为正有限数值，透明度阈值为 0–255，原点须为有限坐标。");
        try {
            var input = MiliastraGraph.readJson(bytes, 2000000).getAsJsonArray();
            if (input.isEmpty() || input.size() > MiliastraFile.MAX_ASSETS)
                throw bad("请输入 1–100000 个体素。");
            var result = new ArrayList<MiliastraGenerator.Block>();
            var grids = new HashSet<Grid>();
            var positions = new HashSet<MiliastraGenerator.Vector>();
            var colors = new HashMap<Integer, MiliastraGenerator.Palette>();
            for (var item : input) {
                var row = item.getAsJsonObject();
                if (!row.keySet().equals(Set.of("x", "y", "z", "color")))
                    throw bad("体素需包含 x、y、z、color，不能包含未知字段。");
                var grid = new Grid(integer(row.get("x")), integer(row.get("y")), integer(row.get("z")));
                if (!grids.add(grid)) throw bad("体素网格坐标重复：" + grid + "。");
                int rgba = color(row.get("color"));
                if ((rgba >>> 24) < options.alphaThreshold()) continue;
                var block = colors.computeIfAbsent(rgba & 0xffffff, MiliastraGenerator::nearest);
                var origin = options.origin();
                float unit = options.unit();
                var position =
                        new MiliastraGenerator.Vector(
                                origin.x() + grid.x() * unit,
                                origin.y() + grid.y() * unit,
                                origin.z() + grid.z() * unit);
                var base = block.scale();
                var scale =
                        new MiliastraGenerator.Vector(
                                (float) (base.x() / block.size() * unit),
                                (float) (base.y() / block.size() * unit),
                                (float) (base.z() / block.size() * unit));
                if (!finite(position)
                        || !finite(scale)
                        || scale.x() <= 0
                        || scale.y() <= 0
                        || scale.z() <= 0) throw bad("体素位置或归一化缩放超出 float32 可用范围。");
                if (!positions.add(position)) throw bad("不同网格坐标在 float32 中重合，请调整原点、边长或网格坐标。");
                result.add(
                        new MiliastraGenerator.Block(
                                block.id(),
                                0,
                                "Block_" + grid.x() + "_" + grid.y() + "_" + grid.z(),
                                position,
                                new MiliastraGenerator.Vector(0, 0, 0),
                                scale,
                                true));
            }
            if (result.isEmpty()) throw bad("没有达到透明度阈值的有效体素。");
            return List.copyOf(result);
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw bad("体素 JSON 应为坐标和颜色对象的数组。");
        }
    }

    private static int color(JsonElement value) {
        if (value == null) throw bad("体素缺少颜色。");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.startsWith("#")) text = text.substring(1);
            if (!text.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                throw bad("颜色应为 RGB／RGBA 十六进制，或 3／4 项的 0–255 数组。");
            long bits = Long.parseLong(text, 16);
            return text.length() == 6
                    ? (int) bits | 0xff000000
                    : (int) (bits >>> 8) | ((int) bits & 255) << 24;
        }
        if (value.isJsonArray()) {
            var a = value.getAsJsonArray();
            if (a.size() != 3 && a.size() != 4) throw bad("颜色数组需要 3 或 4 项。");
            int[] c = {0, 0, 0, 255};
            for (int i = 0; i < a.size(); i++) {
                c[i] = integer(a.get(i));
                if (c[i] < 0 || c[i] > 255) throw bad("颜色通道应为 0–255 整数。");
            }
            return c[3] << 24 | c[0] << 16 | c[1] << 8 | c[2];
        }
        throw bad("颜色应为十六进制文本或 RGB／RGBA 数组。");
    }

    private static int integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw bad("体素网格坐标和颜色通道须为 int32 整数。");
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) {
            throw bad("体素网格坐标和颜色通道须为 int32 整数。");
        }
    }

    private static boolean finite(MiliastraGenerator.Vector value) {
        return value != null
                && Float.isFinite(value.x())
                && Float.isFinite(value.y())
                && Float.isFinite(value.z());
    }

    private static IllegalArgumentException bad(String value) {
        return new IllegalArgumentException(value);
    }
}
