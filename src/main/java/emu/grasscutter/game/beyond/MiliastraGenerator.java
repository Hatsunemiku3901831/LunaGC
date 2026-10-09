package emu.grasscutter.game.beyond;

import com.google.gson.*;
import com.google.protobuf.CodedOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import javax.imageio.ImageIO;

/** Static entity authoring, derived from the documented GIA entity layout, not game execution. */
public final class MiliastraGenerator {
    public record Vector(float x, float y, float z) {}

    public record Block(
            int templateId,
            int guid,
            String name,
            Vector position,
            Vector rotation,
            Vector scale,
            boolean isStatic) {}

    public record Palette(int id, String name, List<Integer> color, double size, Vector scale) {}

    public record ImageOptions(
            int width,
            int height,
            boolean keepAspect,
            float unit,
            int alphaThreshold,
            String plane,
            Vector origin) {
        public static ImageOptions defaults() {
            return new ImageOptions(240, 240, true, .1f, 128, "XY", new Vector(0, 0, 0));
        }
    }

    private static final List<Palette> PALETTE = loadPalette();

    private MiliastraGenerator() {}

    public static List<Palette> palette() {
        return PALETTE;
    }

    public static List<Block> model(byte[] bytes) {
        try {
            return readModel(bytes);
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw invalid("实体 JSON 的结构或参数类型不合法。");
        }
    }

    private static List<Block> readModel(byte[] bytes) {
        if (bytes.length > 8 * 1024 * 1024) throw invalid("实体 JSON 最大为 8 MiB。");
        var root = MiliastraGraph.readJson(bytes, 2000000).getAsJsonObject();
        keys(root, Set.of("blocks"));
        var array = root.getAsJsonArray("blocks");
        if (array == null || array.isEmpty() || array.size() > MiliastraFile.MAX_ASSETS)
            throw invalid("请输入 1–100000 个实体。");
        var result = new ArrayList<Block>();
        for (var value : array) {
            var block = value.getAsJsonObject();
            keys(block, Set.of("templateId", "guid", "name", "position", "rotation", "scale", "static"));
            int template = integer(block.get("templateId"), "模板 ID");
            int guid = block.has("guid") ? integer(block.get("guid"), "GUID") : 0;
            if (template < 1 || (block.has("guid") && guid < 1))
                throw invalid("模板 ID 和指定的 GUID 必须为正 int32。");
            String name = block.has("name") ? string(block.get("name"), "名称") : "";
            if (name.length() > 4096) throw invalid("实体名称最多 4096 字符。");
            Vector position = vector(block, "position", new Vector(0, 0, 0));
            Vector rotation = vector(block, "rotation", new Vector(0, 0, 0));
            Vector scale = vector(block, "scale", new Vector(1, 1, 1));
            if (scale.x() <= 0 || scale.y() <= 0 || scale.z() <= 0) throw invalid("缩放必须为正值。");
            boolean isStatic = !block.has("static") || bool(block.get("static"), "static");
            result.add(new Block(template, guid, name, position, rotation, scale, isStatic));
        }
        return List.copyOf(result);
    }

    public static List<Block> image(byte[] bytes, ImageOptions options) throws IOException {
        if (bytes.length > 16 * 1024 * 1024) throw invalid("输入图片最大为 16 MiB。");
        if (options.width() < 1
                || options.height() < 1
                || options.width() > 1000
                || options.height() > 1000
                || (long) options.width() * options.height() > MiliastraFile.MAX_ASSETS)
            throw invalid("输出尺寸需为 1–1000，每张最多 100000 个像素。");
        if (!Float.isFinite(options.unit())
                || options.unit() <= 0
                || options.alphaThreshold() < 0
                || options.alphaThreshold() > 255
                || !Set.of("XY", "XZ", "YZ").contains(options.plane())) throw invalid("像素画参数不合法。");
        finite(options.origin());
        BufferedImage original;
        try (var input =
                new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("图片格式不受支持或内容损坏。");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) <= 0
                        || reader.getHeight(0) <= 0
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000)
                    throw invalid("输入图片最多 1600 万像素。");
                try {
                    original = reader.read(0);
                } catch (javax.imageio.IIOException e) {
                    throw invalid("图片内容损坏。");
                }
            } finally {
                reader.dispose();
            }
        } catch (javax.imageio.IIOException e) {
            throw invalid("图片内容损坏。");
        }
        int width = options.width(), height = options.height();
        if (options.keepAspect()) {
            double ratio =
                    Math.min((double) width / original.getWidth(), (double) height / original.getHeight());
            width = Math.max(1, (int) Math.floor(original.getWidth() * ratio));
            height = Math.max(1, (int) Math.floor(original.getHeight() * ratio));
        }
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
            original.flush();
        }
        var result = new ArrayList<Block>();
        var colors = new HashMap<Integer, Palette>();
        try {
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int rgba = image.getRGB(x, height - 1 - y);
                    if ((rgba >>> 24) < options.alphaThreshold()) continue;
                    int rgb = rgba & 0xFFFFFF;
                    var template = colors.computeIfAbsent(rgb, MiliastraGenerator::nearest);
                    float horizontal = x * options.unit(), vertical = y * options.unit();
                    var origin = options.origin();
                    Vector position =
                            switch (options.plane()) {
                                case "XY" -> new Vector(origin.x() + horizontal, origin.y() + vertical, origin.z());
                                case "XZ" -> new Vector(origin.x() + horizontal, origin.y(), origin.z() + vertical);
                                case "YZ" -> new Vector(origin.x(), origin.y() + horizontal, origin.z() + vertical);
                                default -> throw invalid("未知平面。");
                            };
                    finite(position);
                    var base = template.scale();
                    var scale =
                            new Vector(
                                    (float) (base.x() / template.size() * options.unit()),
                                    (float) (base.y() / template.size() * options.unit()),
                                    (float) (base.z() / template.size() * options.unit()));
                    finite(scale);
                    if (scale.x() <= 0 || scale.y() <= 0 || scale.z() <= 0) throw invalid("归一化缩放超出可用范围。");
                    result.add(
                            new Block(
                                    template.id(),
                                    0,
                                    "Pixel_" + x + "_" + y,
                                    position,
                                    new Vector(0, 0, 0),
                                    scale,
                                    true));
                }
        } finally {
            image.flush();
        }
        if (result.isEmpty()) throw invalid("图片没有达到透明度阈值的有效像素。");
        return List.copyOf(result);
    }

    static byte[] encode(List<Block> blocks, int firstGuid) throws IOException {
        if (blocks.isEmpty() || blocks.size() > MiliastraFile.MAX_ASSETS || firstGuid < 1)
            throw invalid("实体数量或 GUID 起点不合法。");
        var ids = new HashSet<Integer>();
        int next = firstGuid;
        var bytes = new ByteArrayOutputStream();
        var root = CodedOutputStream.newInstance(bytes);
        for (var block : blocks) {
            if (block == null
                    || block.templateId() < 1
                    || block.name() == null
                    || block.name().length() > 4096
                    || block.scale() == null
                    || block.scale().x() <= 0
                    || block.scale().y() <= 0
                    || block.scale().z() <= 0) {
                throw invalid("实体模板、名称或缩放不合法。");
            }
            int guid = block.guid() == 0 ? next++ : block.guid();
            if (guid < 1 || !ids.add(guid)) throw invalid("生成的 GUID 越界或重复。");
            String name = block.name().isBlank() ? "Entity_" + guid : block.name();
            byte[] info =
                    message(
                            out -> {
                                out.writeInt32(2, 1);
                                out.writeInt32(3, 2);
                                out.writeInt32(4, guid);
                            });
            byte[] transform =
                    message(
                            out -> {
                                out.writeByteArray(1, vector(block.position()));
                                out.writeByteArray(2, vector(block.rotation()));
                                out.writeByteArray(3, vector(block.scale()));
                                out.writeUInt32(501, -1);
                            });
            byte[] component =
                    message(
                            out -> {
                                out.writeInt32(1, 1);
                                out.writeByteArray(11, transform);
                            });
            byte[] nameProperty =
                    message(
                            out -> {
                                out.writeString(1, name);
                                if (block.isStatic()) out.writeInt32(2, 1);
                            });
            byte[] property =
                    message(
                            out -> {
                                out.writeInt32(1, 1);
                                out.writeByteArray(11, nameProperty);
                            });
            byte[] reference =
                    message(
                            out -> {
                                out.writeInt32(1, block.templateId());
                                out.writeInt32(2, 1);
                            });
            byte[] data =
                    message(
                            out -> {
                                out.writeInt32(1, guid);
                                out.writeByteArray(2, reference);
                                out.writeByteArray(5, property);
                                out.writeByteArray(6, component);
                                out.writeInt32(8, block.templateId());
                            });
            byte[] entity =
                    message(
                            out -> {
                                out.writeByteArray(1, data);
                                out.writeInt32(4, block.templateId());
                            });
            root.writeByteArray(
                    1,
                    message(
                            out -> {
                                out.writeByteArray(1, info);
                                out.writeString(3, name);
                                out.writeInt32(5, 3);
                                out.writeByteArray(12, entity);
                            }));
            root.flush();
            if (bytes.size() > MiliastraFile.MAX_BYTES - 24) throw invalid("生成文件超过 32 MiB。");
        }
        byte[] payload = bytes.toByteArray();
        return ByteBuffer.allocate(payload.length + 24)
                .putInt(payload.length + 20)
                .putInt(1)
                .putInt(0x326)
                .putInt(3)
                .putInt(payload.length)
                .put(payload)
                .putInt(0x679)
                .array();
    }

    private static List<Palette> loadPalette() {
        try (var input =
                MiliastraGenerator.class.getResourceAsStream("/miliastra/block-palette.json")) {
            if (input == null) throw new IllegalStateException("Missing Miliastra block palette");
            var rows =
                    MiliastraGraph.readJson(input.readAllBytes()).getAsJsonObject().getAsJsonArray("blocks");
            var result = new ArrayList<Palette>();
            for (var value : rows) {
                var row = value.getAsJsonObject();
                var scale = row.getAsJsonArray("scale");
                var colors = new ArrayList<Integer>();
                for (var color : row.getAsJsonArray("color")) colors.add(color.getAsInt());
                result.add(
                        new Palette(
                                row.get("id").getAsInt(),
                                row.get("name").getAsString(),
                                List.copyOf(colors),
                                row.get("size").getAsDouble(),
                                new Vector(
                                        scale.get(0).getAsFloat(),
                                        scale.get(1).getAsFloat(),
                                        scale.get(2).getAsFloat())));
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load block palette", e);
        }
    }

    static Palette nearest(int rgb) {
        float[] target = Color.RGBtoHSB((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, null);
        Palette best = null;
        double minimum = Double.POSITIVE_INFINITY;
        for (var block : PALETTE) {
            float[] hsv =
                    Color.RGBtoHSB(block.color().get(0), block.color().get(1), block.color().get(2), null);
            double distance =
                    target[1] * target[1]
                            + hsv[1] * hsv[1]
                            - 2 * target[1] * hsv[1] * Math.cos((target[0] - hsv[0]) * 2 * Math.PI)
                            + Math.pow(target[2] - hsv[2], 2);
            if (distance < minimum) {
                minimum = distance;
                best = block;
            }
        }
        return Objects.requireNonNull(best);
    }

    private static Vector vector(JsonObject parent, String key, Vector fallback) {
        if (!parent.has(key)) return fallback;
        var vector = parent.getAsJsonObject(key);
        keys(vector, Set.of("x", "y", "z"));
        return new Vector(
                number(vector.get("x"), key + ".x"),
                number(vector.get("y"), key + ".y"),
                number(vector.get("z"), key + ".z"));
    }

    private static byte[] vector(Vector vector) throws IOException {
        finite(vector);
        return message(
                out -> {
                    if (Float.floatToRawIntBits(vector.x()) != 0) out.writeFloat(1, vector.x());
                    if (Float.floatToRawIntBits(vector.y()) != 0) out.writeFloat(2, vector.y());
                    if (Float.floatToRawIntBits(vector.z()) != 0) out.writeFloat(3, vector.z());
                });
    }

    private static void finite(Vector vector) {
        if (vector == null
                || !Float.isFinite(vector.x())
                || !Float.isFinite(vector.y())
                || !Float.isFinite(vector.z())) throw invalid("坐标、旋转或缩放超出 float32 有限范围。");
    }

    private static float number(JsonElement value, String label) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Float.isFinite(value.getAsFloat())) throw invalid(label + " 应为 float32 有限数值。");
        return value.getAsFloat();
    }

    private static int integer(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid(label + " 应为 int32。");
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) {
            throw invalid(label + " 应为 int32。");
        }
    }

    private static String string(JsonElement value, String label) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid(label + " 应为字符串。");
        return value.getAsString();
    }

    private static boolean bool(JsonElement value, String label) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw invalid(label + " 应为布尔值。");
        return value.getAsBoolean();
    }

    private static void keys(JsonObject object, Set<String> allowed) {
        for (var key : object.keySet()) if (!allowed.contains(key)) throw invalid("未知的实体生成参数：" + key);
    }

    private interface Writer {
        void write(CodedOutputStream output) throws IOException;
    }

    private static byte[] message(Writer writer) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        writer.write(output);
        output.flush();
        return bytes.toByteArray();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
