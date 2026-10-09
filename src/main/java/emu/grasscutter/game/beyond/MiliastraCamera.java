package emu.grasscutter.game.beyond;

import com.google.gson.*;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetCamera;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetDefinitions;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;

/** Camera configuration authoring from pinned template literals and the MIT asset schema. */
public final class MiliastraCamera {
    public record Camera(int guid, MiliastraAssetCamera.CameraData data) {}

    private static final class Batch extends ArrayList<Camera> {
        long payloadBytes;
    }

    private static final JsonObject TEMPLATES = load();
    private static final Set<String> FIELDS =
            Set.of(
                    "preset",
                    "guid",
                    "name",
                    "cameraMode",
                    "defaultDistance",
                    "minDistance",
                    "maxDistance",
                    "fieldOfView",
                    "viewpointOffset",
                    "followRotation",
                    "field14",
                    "minHorizontalAngle",
                    "maxHorizontalAngle",
                    "minPitchAngle",
                    "maxPitchAngle",
                    "ignoreCollision",
                    "horizontalAngle");

    private MiliastraCamera() {}

    public static JsonObject templates() {
        return TEMPLATES.deepCopy();
    }

    private static JsonObject load() {
        try (var stream =
                MiliastraCamera.class.getResourceAsStream("/miliastra/camera-templates.json")) {
            if (stream == null) throw new IOException("Missing camera templates");
            return MiliastraGraph.readJson(stream.readAllBytes()).getAsJsonObject();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static List<Camera> model(byte[] bytes) {
        if (bytes.length > 8 * 1024 * 1024) throw bad("镜头 JSON 最大为 8 MiB。");
        try {
            var root = MiliastraGraph.readJson(bytes, 2000000).getAsJsonObject();
            keys(root, Set.of("cameras", "variations"));
            var result = new Batch();
            if (root.has("cameras"))
                for (var item : root.getAsJsonArray("cameras")) add(result, camera(item.getAsJsonObject()));
            if (root.has("variations"))
                for (var item : root.getAsJsonArray("variations"))
                    variations(result, item.getAsJsonObject());
            if (result.isEmpty()) throw bad("请输入镜头配置或批量变体。");
            var explicit = new HashSet<Integer>();
            for (var c : result) if (c.guid() != 0 && !explicit.add(c.guid())) throw bad("镜头 GUID 重复。");
            return List.copyOf(result);
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw bad("镜头 JSON 的结构或参数类型不合法。");
        }
    }

    private static void add(Batch out, Camera value) {
        if (out.size() >= MiliastraFile.MAX_ASSETS) throw bad("每次最多生成 100000 个镜头。");
        int size = asset(value, Integer.MAX_VALUE).getSerializedSize();
        out.payloadBytes +=
                CodedOutputStream.computeTagSize(1) + CodedOutputStream.computeUInt32SizeNoTag(size) + size;
        if (out.payloadBytes > MiliastraFile.MAX_BYTES - 24) throw bad("生成文件超过 32 MiB。");
        out.add(value);
    }

    private static void variations(Batch out, JsonObject spec) {
        keys(spec, Set.of("camera", "parameter", "from", "to", "step", "namePrefix"));
        var base = spec.has("camera") ? spec.getAsJsonObject("camera").deepCopy() : new JsonObject();
        if (base.has("guid")) throw bad("批量变体请通过 GUID 起点分配编号，不接受固定 guid。");
        String parameter = string(spec.get("parameter"));
        if (!Set.of("horizontalAngle", "pitchAngle", "distance", "fieldOfView").contains(parameter))
            throw bad("未知镜头变体参数：" + parameter);
        var from = decimal(spec.get("from"));
        var to = decimal(spec.get("to"));
        var step = decimal(spec.get("step"));
        if (step.signum() <= 0 || from.compareTo(to) > 0) throw bad("批量变体需要正步长且起点不大于终点。");
        camera(base); // Validate the complete base, including otherwise unused fields.
        float low =
                parameter.equals("horizontalAngle")
                        ? -180
                        : parameter.equals("pitchAngle") ? -89 : parameter.equals("distance") ? .1f : 30;
        float high =
                parameter.equals("horizontalAngle")
                        ? 180
                        : parameter.equals("pitchAngle") ? 89 : parameter.equals("distance") ? 50 : 60;
        range(from.floatValue(), low, high, parameter);
        range(to.floatValue(), low, high, parameter);
        var count = to.subtract(from).divideToIntegralValue(step).add(BigDecimal.ONE);
        if (count.compareTo(BigDecimal.valueOf(MiliastraFile.MAX_ASSETS - out.size())) > 0)
            throw bad("批量变体数量超过 100000。");
        String prefix = spec.has("namePrefix") ? string(spec.get("namePrefix")) : parameter + "-";
        int n = count.intValueExact();
        float previous = Float.NaN;
        for (int i = 0; i < n; i++) {
            var value = from.add(step.multiply(BigDecimal.valueOf(i)));
            float actual = value.floatValue();
            if (i > 0 && actual == previous) throw bad("步长小于 float32 分辨率，生成值发生重合。");
            previous = actual;
            var raw = base.deepCopy();
            raw.addProperty("name", prefix + value.stripTrailingZeros().toPlainString());
            for (String field :
                    switch (parameter) {
                        case "horizontalAngle" -> List.of("minHorizontalAngle", "maxHorizontalAngle");
                        case "pitchAngle" -> List.of("minPitchAngle", "maxPitchAngle");
                        case "distance" -> List.of("defaultDistance", "minDistance", "maxDistance");
                        default -> List.of("fieldOfView");
                    }) raw.addProperty(field, value);
            add(out, camera(raw));
        }
    }

    private static Camera camera(JsonObject raw) {
        keys(raw, FIELDS);
        String preset = raw.has("preset") ? string(raw.get("preset")) : "thirdPerson";
        JsonObject data = null;
        for (var row : TEMPLATES.getAsJsonArray("templates"))
            if (row.getAsJsonObject().get("id").getAsString().equals(preset))
                data = row.getAsJsonObject().getAsJsonObject("defaults").deepCopy();
        if (data == null) throw bad("未知镜头模板：" + preset);
        for (var entry : raw.entrySet())
            if (!Set.of("preset", "guid").contains(entry.getKey())) {
                if (entry.getKey().equals("viewpointOffset")) {
                    var v = entry.getValue().getAsJsonObject();
                    keys(v, Set.of("x", "y", "z"));
                    for (var axis : v.entrySet())
                        data.getAsJsonObject("viewpointOffset").add(axis.getKey(), axis.getValue());
                } else data.add(entry.getKey(), entry.getValue());
            }
        String name = string(data.get("name"));
        if (name.length() > 4096) throw bad("镜头名称最多 4096 字符。");
        int mode = integer(data.get("cameraMode"));
        if (mode < 1 || mode > 5) throw bad("镜头模式必须为 1–5。");
        int guid = raw.has("guid") ? integer(raw.get("guid")) : 0;
        if (raw.has("guid") && guid <= 0) throw bad("镜头 GUID 应为正 int32。");
        float distance = number(data, "defaultDistance"),
                min = number(data, "minDistance"),
                max = number(data, "maxDistance");
        for (float d : new float[] {distance, min, max}) range(d, .1f, 50, "视距");
        if (min > max || distance < min || distance > max) throw bad("默认视距应在最小与最大视距之间。");
        float fov = number(data, "fieldOfView");
        range(fov, 30, 60, "视野");
        float hmin = number(data, "minHorizontalAngle"),
                hmax = number(data, "maxHorizontalAngle"),
                horizontal = number(data, "horizontalAngle");
        for (float a : new float[] {hmin, hmax, horizontal}) range(a, -180, 180, "水平角");
        float pmin = number(data, "minPitchAngle"), pmax = number(data, "maxPitchAngle");
        for (float a : new float[] {pmin, pmax}) range(a, -89, 89, "俯仰角");
        if (hmin > hmax || pmin > pmax) throw bad("角度下限不能大于上限。");
        float field14 = number(data, "field14");
        range(field14, 0, 1, "field14");
        var offset = data.getAsJsonObject("viewpointOffset");
        var vector =
                MiliastraAssetCamera.ViewpointOffset.newBuilder()
                        .setX(number(offset, "x"))
                        .setY(number(offset, "y"))
                        .setZ(number(offset, "z"));
        var builder =
                MiliastraAssetCamera.CameraData.newBuilder()
                        .setName(name)
                        .setCameraMode(mode == 5 ? 0 : mode)
                        .setDefaultDistance(distance)
                        .setMinDistance(min)
                        .setMaxDistance(max)
                        .setFieldOfView(fov)
                        .setViewpointOffset(vector)
                        .setFollowRotation(bool(data.get("followRotation")))
                        .setField14(field14)
                        .setMinHorizontalAngle(hmin)
                        .setMaxHorizontalAngle(hmax)
                        .setMinPitchAngle(pmin)
                        .setMaxPitchAngle(pmax)
                        .setHorizontalAngle(horizontal)
                        .setIgnoreCollision(bool(data.get("ignoreCollision")) ? 1f : 0f);
        return new Camera(guid, builder.build());
    }

    static byte[] encode(List<Camera> cameras, int firstGuid) throws IOException {
        if (cameras.isEmpty() || cameras.size() > MiliastraFile.MAX_ASSETS || firstGuid < 1)
            throw bad("镜头数量或 GUID 起点不合法。");
        var used = new HashSet<Integer>();
        long next = firstGuid;
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (var c : cameras) {
            long allocated = c.guid() == 0 ? next++ : c.guid();
            if (allocated < 1 || allocated > Integer.MAX_VALUE || !used.add((int) allocated))
                throw bad("镜头 GUID 重复或超过正 int32。");
            output.writeByteArray(1, asset(c, (int) allocated).toByteArray());
            output.flush();
            if (bytes.size() > MiliastraFile.MAX_BYTES - 24) throw bad("生成文件超过 32 MiB。");
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

    private static MiliastraAssetDefinitions.Asset asset(Camera c, int guid) {
        var meta = MiliastraAssetDefinitions.AssetMeta.newBuilder().setField2(25).setAssetId(guid);
        var detail =
                MiliastraAssetCamera.CameraDetail.newBuilder().setCameraId(guid).setCameraData(c.data());
        return MiliastraAssetDefinitions.Asset.newBuilder()
                .setMeta(meta)
                .setName(c.data().getName())
                .setType(MiliastraAssetDefinitions.Asset.AssetType.CAMERA)
                .setCameraConfig(MiliastraAssetCamera.CameraConfig.newBuilder().addCameraDetails(detail))
                .build();
    }

    private static float number(JsonObject object, String key) {
        return decimal(object.get(key)).floatValue();
    }

    private static BigDecimal decimal(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw bad("镜头参数应为数值。");
        var d = value.getAsBigDecimal();
        float f = d.floatValue();
        if (!Float.isFinite(f) || (f == 0 && d.signum() != 0)) throw bad("镜头数值超出 float32 有限范围。");
        return d;
    }

    private static int integer(JsonElement v) {
        try {
            return decimal(v).intValueExact();
        } catch (ArithmeticException e) {
            throw bad("镜头整数应为 int32。");
        }
    }

    private static String string(JsonElement v) {
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())
            throw bad("镜头文本参数应为字符串。");
        return v.getAsString();
    }

    private static boolean bool(JsonElement v) {
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
            throw bad("镜头开关应为布尔值。");
        return v.getAsBoolean();
    }

    private static void range(float v, float low, float high, String field) {
        if (v < low || v > high) throw bad(field + " 应在 " + low + " 与 " + high + " 之间。");
    }

    private static void keys(JsonObject o, Set<String> allowed) {
        for (var key : o.keySet()) if (!allowed.contains(key)) throw bad("未知镜头生成参数：" + key);
    }

    private static IllegalArgumentException bad(String text) {
        return new IllegalArgumentException(text);
    }
}
