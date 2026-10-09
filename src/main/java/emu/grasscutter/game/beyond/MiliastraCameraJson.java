package emu.grasscutter.game.beyond;

import com.google.gson.*;
import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetDefinitions.Asset;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Camera projections backed by complete source bytes; edits never reconstruct other assets. */
public final class MiliastraCameraJson {
    private static final String FORMAT = "lunagc.camera.v1";
    private static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Part(int start, int end, int tag, int assetIndex, Asset camera) {}

    private static final Set<String> ROW =
            Set.of("assetIndex", "detailIndex", "assetGuid", "cameraGuid", "assetName", "data");

    private MiliastraCameraJson() {}

    public static byte[] decode(byte[] source) {
        var rows = new JsonArray();
        for (var part : parts(source)) {
            if (part.camera() == null) continue;
            var asset = part.camera();
            var details = asset.getCameraConfig().getCameraDetailsList();
            for (int i = 0; i < details.size(); i++) {
                if (rows.size() >= MiliastraFile.MAX_ASSETS) throw bad("镜头详情超过 100000 项。");
                var detail = details.get(i);
                var row = new JsonObject();
                row.addProperty("assetIndex", part.assetIndex());
                row.addProperty("detailIndex", i);
                row.addProperty("assetGuid", Integer.toUnsignedLong(asset.getMeta().getAssetId()));
                row.addProperty("cameraGuid", Integer.toUnsignedLong(detail.getCameraId()));
                row.addProperty("assetName", asset.getName());
                row.add("data", project(detail.getCameraData()));
                rows.add(row);
            }
        }
        if (rows.isEmpty()) throw bad("GIA 中没有可提取的完整镜头配置。");
        var document = new JsonObject();
        document.addProperty("format", FORMAT);
        document.addProperty("source", Base64.getEncoder().encodeToString(source));
        document.add("cameras", rows);
        byte[] bytes = JSON.toJson(document).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MiliastraFile.MAX_BYTES) throw bad("镜头 JSON 超过 32 MiB，原始 GIA 已保留。");
        encode(bytes); // Only publish a projection that can restore its original source.
        return bytes;
    }

    public static byte[] encode(byte[] bytes) {
        if (bytes.length > MiliastraFile.MAX_BYTES) throw bad("镜头 JSON 最大为 32 MiB。");
        try {
            var document = MiliastraGraph.readJson(bytes, 2000000).getAsJsonObject();
            keys(document, Set.of("format", "source", "cameras"));
            if (!FORMAT.equals(text(document.get("format")))) throw bad("镜头 JSON 格式标记不匹配。");
            String encoded = text(document.get("source"));
            if (encoded.length() > ((MiliastraFile.MAX_BYTES + 2L) / 3) * 4) throw bad("原始来源超过 32 MiB。");
            byte[] source;
            try {
                source = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
                throw bad("镜头 JSON 来源不是合法 Base64。");
            }
            var parts = parts(source);
            var rows = document.getAsJsonArray("cameras");
            if (rows == null || rows.isEmpty() || rows.size() > MiliastraFile.MAX_ASSETS)
                throw bad("镜头列表数量不合法。");
            var body = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(body);
            int cursor = 0;
            boolean changed = false;
            for (var part : parts) {
                if (part.camera() == null) {
                    output.writeRawBytes(source, part.start(), part.end() - part.start());
                    continue;
                }
                var asset = part.camera();
                var config = asset.getCameraConfig().toBuilder();
                String assetName = null;
                for (int i = 0; i < config.getCameraDetailsCount(); i++) {
                    if (cursor >= rows.size()) throw bad("不能增删镜头详情，请使用生成器创建新镜头。");
                    var row = rows.get(cursor++).getAsJsonObject();
                    keys(row, ROW);
                    var detail = config.getCameraDetails(i);
                    if (integer(row.get("assetIndex")) != part.assetIndex()
                            || integer(row.get("detailIndex")) != i
                            || integer(row.get("assetGuid"))
                                    != Integer.toUnsignedLong(asset.getMeta().getAssetId())
                            || integer(row.get("cameraGuid")) != Integer.toUnsignedLong(detail.getCameraId()))
                        throw bad("镜头索引与编号不可修改，请使用复制并重新编号。");
                    String name = text(row.get("assetName"));
                    if (!name.equals(asset.getName())) validText(name);
                    if (assetName != null && !assetName.equals(name)) throw bad("同一资产的 assetName 必须一致。");
                    assetName = name;
                    var updated =
                            (emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCamera.CameraData)
                                    restore(detail.getCameraData(), row.getAsJsonObject("data"));
                    validateEdit(detail.getCameraData(), updated);
                    if (!updated.equals(detail.getCameraData()))
                        config.setCameraDetails(i, detail.toBuilder().setCameraData(updated));
                }
                var updated = asset.toBuilder().setCameraConfig(config);
                if (assetName != null && !assetName.equals(asset.getName())) updated.setName(assetName);
                var value = updated.build();
                if (value.equals(asset))
                    output.writeRawBytes(source, part.start(), part.end() - part.start());
                else {
                    changed = true;
                    output.writeByteArray(part.tag() >>> 3, value.toByteArray());
                }
                output.flush();
                if (body.size() > MiliastraFile.MAX_BYTES - 24) throw bad("编辑后的 GIA 超过 32 MiB。");
            }
            output.flush();
            if (cursor != rows.size()) throw bad("镜头列表与原始来源不一致。");
            if (!changed) return source;
            byte[] payload = body.toByteArray();
            if (payload.length > MiliastraFile.MAX_BYTES - 24) throw bad("编辑后的 GIA 超过 32 MiB。");
            byte[] result =
                    ByteBuffer.allocate(payload.length + 24)
                            .putInt(payload.length + 20)
                            .putInt(1)
                            .putInt(0x326)
                            .putInt(3)
                            .putInt(payload.length)
                            .put(payload)
                            .putInt(0x679)
                            .array();
            parts(result);
            return result;
        } catch (IOException e) {
            throw bad("无法编码镜头配置。");
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw bad("镜头 JSON 的结构或参数类型不合法。");
        }
    }

    private static List<Part> parts(byte[] source) {
        var preview = MiliastraFile.inspect(source, "source.gia");
        if (preview.version() != 1) throw bad("镜头提取仅支持版本 1 GIA。");
        var result = new ArrayList<Part>();
        try {
            var input = CodedInputStream.newInstance(source, 20, source.length - 24);
            input.setRecursionLimit(64);
            int tag, index = 0;
            while ((tag = input.readTag()) != 0) {
                int start = result.isEmpty() ? 20 : result.get(result.size() - 1).end();
                Asset camera = null;
                int assetIndex = -1;
                if (tag == 10 || tag == 18) {
                    assetIndex = index++;
                    byte[] unit = input.readByteArray();
                    var scan = CodedInputStream.newInstance(unit);
                    scan.setRecursionLimit(64);
                    int field, type = 0;
                    boolean data = false;
                    while ((field = scan.readTag()) != 0) {
                        if (field == 40) type = scan.readEnum();
                        else {
                            if (field == 138) data = true;
                            if (!scan.skipField(field)) throw bad("资产根字段损坏。");
                        }
                    }
                    if (type == 13 && data) {
                        var typed = CodedInputStream.newInstance(unit);
                        typed.setRecursionLimit(64);
                        camera = Asset.parseFrom(typed);
                        typed.checkLastTagWas(0);
                        if (!camera.hasCameraConfig()
                                || !camera.hasMeta()
                                || camera.getCameraConfig().getCameraDetailsCount() == 0)
                            throw bad("镜头资产缺少完整配置或元数据。");
                    } else if (type == 13 && tag == 10) throw bad("镜头主资产缺少配置。");
                } else if (!input.skipField(tag)) throw bad("GIA 根字段损坏。");
                result.add(new Part(start, 20 + input.getTotalBytesRead(), tag, assetIndex, camera));
            }
            input.checkLastTagWas(0);
        } catch (IOException e) {
            throw bad("镜头 protobuf 损坏或嵌套过深。");
        }
        return result;
    }

    private static JsonObject project(Message message) {
        var out = new JsonObject();
        for (var f : message.getDescriptorForType().getFields()) {
            Object value = message.getField(f);
            if (value instanceof Message m) out.add(f.getJsonName(), project(m));
            else if (value instanceof Float n) {
                if (!Float.isFinite(n)) throw bad("镜头含非有限数值。");
                out.addProperty(f.getJsonName(), n);
            } else if (f.getType() == FieldDescriptor.Type.UINT32)
                out.addProperty(f.getJsonName(), Integer.toUnsignedLong((Integer) value));
            else if (value instanceof Boolean b) out.addProperty(f.getJsonName(), b);
            else out.addProperty(f.getJsonName(), (String) value);
        }
        return out;
    }

    private static Message restore(Message source, JsonObject data) {
        var fields = source.getDescriptorForType().getFields();
        keys(data, new HashSet<>(fields.stream().map(FieldDescriptor::getJsonName).toList()));
        var builder = source.toBuilder();
        for (var f : fields) {
            var raw = data.get(f.getJsonName());
            Object before = source.getField(f), value;
            switch (f.getType()) {
                case MESSAGE -> value = restore((Message) before, raw.getAsJsonObject());
                case STRING -> {
                    value = text(raw);
                    if (!value.equals(before)) validText((String) value);
                }
                case BOOL -> {
                    if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean())
                        throw bad("镜头开关应为布尔值。");
                    value = raw.getAsBoolean();
                }
                case UINT32 -> value = (int) integer(raw);
                case FLOAT -> {
                    if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber())
                        throw bad("镜头参数应为数值。");
                    float n = raw.getAsFloat();
                    if (!Float.isFinite(n) || n == 0 && raw.getAsBigDecimal().signum() != 0)
                        throw bad("镜头数值超出 float32 有限范围。");
                    value = n;
                }
                default -> throw bad("未知镜头字段类型。");
            }
            if (!value.equals(before)) builder.setField(f, value);
        }
        return builder.build();
    }

    private static void validateEdit(Message before, Message after) {
        boolean distance = false, horizontal = false, pitch = false;
        for (var f : after.getDescriptorForType().getFields()) {
            Object value = after.getField(f);
            if (value.equals(before.getField(f))) continue;
            String name = f.getJsonName();
            if (name.equals("cameraMode") && Integer.toUnsignedLong((Integer) value) > 4)
                throw bad("编辑的原生镜头模式应为 0–4，经典模式为 0。");
            if (!(value instanceof Float v)) continue;
            if (Set.of("defaultDistance", "minDistance", "maxDistance").contains(name)) {
                range(v, .1f, 50, name);
                distance = true;
            } else if (name.equals("fieldOfView")) range(v, 30, 60, name);
            else if (Set.of("horizontalAngle", "minHorizontalAngle", "maxHorizontalAngle")
                    .contains(name)) {
                range(v, -180, 180, name);
                horizontal = !name.equals("horizontalAngle") || horizontal;
            } else if (Set.of("minPitchAngle", "maxPitchAngle").contains(name)) {
                range(v, -89, 89, name);
                pitch = true;
            } else if (name.equals("ignoreCollision")) range(v, 0, 1, name);
        }
        if (distance
                && (number(after, "min_distance") > number(after, "default_distance")
                        || number(after, "default_distance") > number(after, "max_distance")))
            throw bad("默认视距应在最小与最大视距之间。");
        if (horizontal && number(after, "min_horizontal_angle") > number(after, "max_horizontal_angle")
                || pitch && number(after, "min_pitch_angle") > number(after, "max_pitch_angle"))
            throw bad("角度下限不能大于上限。");
    }

    private static float number(Message m, String name) {
        return (Float) m.getField(m.getDescriptorForType().findFieldByName(name));
    }

    private static void range(float n, float low, float high, String field) {
        if (n < low || n > high) throw bad(field + " 应在 " + low + " 与 " + high + " 之间。");
    }

    private static void keys(JsonObject object, Set<String> fields) {
        if (!object.keySet().equals(fields)) throw bad("镜头 JSON 缺少字段或含未知属性。");
    }

    private static String text(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw bad("镜头文本应为字符串。");
        return value.getAsString();
    }

    private static long integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw bad("镜头索引与编号应为整数。");
            long n = value.getAsBigDecimal().longValueExact();
            if (n < 0 || n > 0xffffffffL) throw bad("镜头整数超出 uint32。");
            return n;
        } catch (ArithmeticException e) {
            throw bad("镜头索引与编号应为 uint32 整数。");
        }
    }

    private static void validText(String value) {
        if (value.length() > 4096 || !StandardCharsets.UTF_8.newEncoder().canEncode(value))
            throw bad("镜头名称应为合法 UTF-8，最多 4096 字符。");
    }

    private static IllegalArgumentException bad(String value) {
        return new IllegalArgumentException(value);
    }
}
