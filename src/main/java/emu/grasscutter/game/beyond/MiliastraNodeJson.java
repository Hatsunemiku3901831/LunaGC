package emu.grasscutter.game.beyond;

import com.google.gson.*;
import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Typed GIA node documents. Unknown fields and the unedited source are retained, never executed.
 */
public final class MiliastraNodeJson {
    private static final Descriptor ROOT = MiliastraNodeProtocol.Root.getDescriptor();
    private static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FORMAT = "lunagc.gia.v1";
    private static final Set<Integer> GRAPH_TYPES = Set.of(9, 10, 11, 12, 22, 23, 29, 46, 47);

    private MiliastraNodeJson() {}

    public static byte[] decode(byte[] bytes) {
        var root = parseGia(bytes);
        var result = new JsonObject();
        result.addProperty("format", FORMAT);
        result.addProperty("version", Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt(4)));
        result.add("data", project(root, 0, new int[] {0}));
        result.addProperty("source", Base64.getEncoder().encodeToString(bytes));
        byte[] json = JSON.toJson(result).getBytes(StandardCharsets.UTF_8);
        if (json.length > MiliastraFile.MAX_BYTES) throw invalid("结构化 JSON 超过 32 MiB；原始 GIA 已保留。");
        encode(json); // Never publish a projection that cannot be restored within the same bounds.
        return json;
    }

    public static byte[] encode(byte[] bytes) {
        try {
            if (bytes.length > MiliastraFile.MAX_BYTES) throw invalid("结构化 JSON 最大为 32 MiB。");
            var document = object(MiliastraGraph.readJson(bytes, 2000000), "结构化节点 JSON");
            keys(document, Set.of("format", "version", "data", "source"));
            if (!FORMAT.equals(text(document.get("format"), "format")))
                throw invalid("结构化节点 JSON 格式标记不匹配。");
            long version = integer(document.get("version"), 32, true).longValue();
            var message = restore(ROOT, object(document.get("data"), "data"), 0, new int[] {0});
            validateRoot(message);
            if (document.has("source")) {
                byte[] source = binary(document.get("source"), "source");
                var original = parseGia(source);
                if (version == Integer.toUnsignedLong(ByteBuffer.wrap(source).getInt(4))
                        && message.equals(original)) return source;
            }
            byte[] payload = message.toByteArray();
            if (payload.length > MiliastraFile.MAX_BYTES - 24) throw invalid("转换后的 GIA 超过 32 MiB。");
            byte[] result =
                    ByteBuffer.allocate(payload.length + 24)
                            .putInt(payload.length + 20)
                            .putInt((int) version)
                            .putInt(0x326)
                            .putInt(3)
                            .putInt(payload.length)
                            .put(payload)
                            .putInt(0x679)
                            .array();
            parseGia(result); // Recheck container bounds, metadata and the reconstructed root.
            return result;
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw invalid("结构化节点 JSON 的结构或参数类型不合法。");
        }
    }

    public static MiliastraGraph.Preview inspect(byte[] json) {
        var root = parseGia(encode(json));
        var unit = child(root, "graph");
        int which = ((EnumValueDescriptor) unit.getField(field(unit, "which"))).getNumber();
        String environment =
                switch (which) {
                    case 10 -> "client:boolean";
                    case 11 -> "client:skill";
                    case 47 -> "client:integer";
                    case 22 -> "server:status";
                    case 23 -> "server:class";
                    case 46 -> "server:item";
                    case 12 -> "composite";
                    case 29 -> "structure";
                    default -> "server";
                };
        int nodes = 0, connections = 0;
        if (unit.hasField(field(unit, "graph"))) {
            var graph = child(child(child(unit, "graph"), "inner"), "graph");
            nodes = graph.getRepeatedFieldCount(field(graph, "nodes"));
            for (int i = 0; i < nodes; i++) {
                var node = (Message) graph.getRepeatedField(field(graph, "nodes"), i);
                for (int j = 0; j < node.getRepeatedFieldCount(field(node, "pins")); j++) {
                    var pin = (Message) node.getRepeatedField(field(node, "pins"), j);
                    connections += pin.getRepeatedFieldCount(field(pin, "connects"));
                }
            }
        }
        return new MiliastraGraph.Preview(
                (String) unit.getField(field(unit, "name")),
                environment,
                nodes,
                connections,
                List.of("结构化 JSON 使用参考 proto 1.5.1（标注游戏 6.2）；保留未知字段，尚未核验当前 7.1 导入与运行。"));
    }

    private static DynamicMessage parseGia(byte[] bytes) {
        MiliastraFile.inspect(bytes, "nodes.gia");
        if (ByteBuffer.wrap(bytes).getInt(12) != 3) throw invalid("节点转换仅支持 GIA。");
        try {
            var scan = CodedInputStream.newInstance(bytes, 20, bytes.length - 24);
            int tag, roots = 0;
            while ((tag = scan.readTag()) != 0) {
                if (tag == 10 && ++roots > 1) throw invalid("节点 GIA 只能有一个主图，实体集合应使用实体生成器。");
                if (!scan.skipField(tag)) throw invalid("GIA 根字段不合法。");
            }
            var input = CodedInputStream.newInstance(bytes, 20, bytes.length - 24);
            input.setRecursionLimit(64);
            var root = DynamicMessage.parseFrom(ROOT, input);
            input.checkLastTagWas(0);
            validateRoot(root);
            return root;
        } catch (IOException e) {
            throw invalid("节点 protobuf 结构损坏或嵌套超限。");
        }
    }

    private static void validateRoot(Message root) {
        if (!root.hasField(field(root, "graph"))) throw invalid("GIA 中没有主节点图。");
        var unit = child(root, "graph");
        int which = ((EnumValueDescriptor) unit.getField(field(unit, "which"))).getNumber();
        if (!GRAPH_TYPES.contains(which)) throw invalid("当前 GIA 主资产不是已识别的节点／复合图／结构定义。");
        var kind = unit.getOneofFieldDescriptor(unit.getDescriptorForType().getOneofs().get(0));
        if (kind == null) throw invalid("节点 GIA 缺少图内容。");
        if ((which == 12 && !kind.getName().equals("compositeDef"))
                || (which == 29 && !kind.getName().equals("structureDef"))
                || (which != 12 && which != 29 && !kind.getName().equals("graph")))
            throw invalid("节点类型与图内容不匹配。");
    }

    private static JsonObject project(Message message, int depth, int[] count) {
        bound(depth, count);
        var result = new JsonObject();
        for (var entry : message.getAllFields().entrySet()) {
            var field = entry.getKey();
            if (field.isRepeated()) {
                var array = new JsonArray();
                for (var item : (List<?>) entry.getValue()) {
                    bound(depth, count);
                    array.add(projectValue(field, item, depth, count));
                }
                result.add(field.getName(), array);
            } else result.add(field.getName(), projectValue(field, entry.getValue(), depth, count));
        }
        if (!message.getUnknownFields().asMap().isEmpty())
            result.addProperty(
                    "$unknown", Base64.getEncoder().encodeToString(message.getUnknownFields().toByteArray()));
        return result;
    }

    private static JsonElement projectValue(
            FieldDescriptor field, Object value, int depth, int[] count) {
        bound(depth, count);
        return switch (field.getJavaType()) {
            case MESSAGE -> project((Message) value, depth + 1, count);
            case BYTE_STRING -> new JsonPrimitive(
                    Base64.getEncoder().encodeToString(((ByteString) value).toByteArray()));
            case ENUM -> new JsonPrimitive(((EnumValueDescriptor) value).getNumber());
            case BOOLEAN -> new JsonPrimitive((Boolean) value);
            case STRING -> new JsonPrimitive((String) value);
            case INT -> unsigned(field)
                    ? new JsonPrimitive(Integer.toUnsignedLong((Integer) value))
                    : new JsonPrimitive((Integer) value);
            case LONG -> new JsonPrimitive(
                    unsigned(field) ? Long.toUnsignedString((Long) value) : Long.toString((Long) value));
            case FLOAT -> {
                if (!Float.isFinite((Float) value)) throw invalid("GIA 包含无法用 JSON 表示的浮点值。");
                yield new JsonPrimitive((Float) value);
            }
            case DOUBLE -> {
                if (!Double.isFinite((Double) value)) throw invalid("GIA 包含无法用 JSON 表示的浮点值。");
                yield new JsonPrimitive((Double) value);
            }
        };
    }

    private static DynamicMessage restore(
            Descriptor descriptor, JsonObject object, int depth, int[] count) {
        bound(depth, count);
        var builder = DynamicMessage.newBuilder(descriptor);
        var oneofs = new HashSet<OneofDescriptor>();
        for (var entry : object.entrySet()) {
            if (entry.getKey().equals("$unknown")) {
                try {
                    var input = CodedInputStream.newInstance(binary(entry.getValue(), "$unknown"));
                    input.setRecursionLimit(64);
                    var fields = UnknownFieldSet.parseFrom(input);
                    for (var unknown : fields.asMap().entrySet()) {
                        var known = descriptor.findFieldByNumber(unknown.getKey());
                        if (known != null && recognizedWire(known, unknown.getValue())) {
                            throw invalid("$unknown 不能覆盖已定义字段。");
                        }
                    }
                    builder.setUnknownFields(fields);
                } catch (IOException e) {
                    throw invalid("$unknown protobuf 损坏或嵌套超限。");
                }
                continue;
            }
            var field = descriptor.findFieldByName(entry.getKey());
            if (field == null)
                throw invalid(
                        "未知 JSON 字段："
                                + descriptor.getName()
                                + "."
                                + entry.getKey()
                                + "；使用 $unknown 保留未知 protobuf。");
            if (field.getContainingOneof() != null && !oneofs.add(field.getContainingOneof()))
                throw invalid("oneof 不能同时提供多个值。");
            if (field.isRepeated()) {
                if (!entry.getValue().isJsonArray()) throw invalid(field.getName() + " 应为数组。");
                for (var item : entry.getValue().getAsJsonArray())
                    builder.addRepeatedField(field, restoreValue(field, item, depth, count));
            } else builder.setField(field, restoreValue(field, entry.getValue(), depth, count));
        }
        return builder.build();
    }

    private static Object restoreValue(
            FieldDescriptor field, JsonElement value, int depth, int[] count) {
        bound(depth, count);
        return switch (field.getJavaType()) {
            case MESSAGE -> restore(
                    field.getMessageType(), object(value, field.getName()), depth + 1, count);
            case BYTE_STRING -> ByteString.copyFrom(binary(value, field.getName()));
            case STRING -> text(value, field.getName());
            case BOOLEAN -> {
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    throw invalid(field.getName() + " 应为布尔值。");
                yield value.getAsBoolean();
            }
            case INT -> integer(value, 32, unsigned(field)).intValue();
            case LONG -> integer(value, 64, unsigned(field)).longValue();
            case ENUM -> field
                    .getEnumType()
                    .findValueByNumberCreatingIfUnknown(integer(value, 32, false).intValue());
            case FLOAT -> {
                var number = numeric(value);
                float f = number.getAsFloat();
                if (!Float.isFinite(f)) throw invalid("浮点值超出 float32。");
                yield f;
            }
            case DOUBLE -> {
                var number = numeric(value);
                double d = number.getAsDouble();
                if (!Double.isFinite(d)) throw invalid("浮点值超出 float64。");
                yield d;
            }
        };
    }

    private static BigInteger integer(JsonElement value, int bits, boolean unsigned) {
        if (value == null || !value.isJsonPrimitive()) throw invalid("整数字段应为整数或十进制字符串。");
        var primitive = value.getAsJsonPrimitive();
        if (!primitive.isString() && !primitive.isNumber()) throw invalid("整数字段应为整数或十进制字符串。");
        try {
            BigInteger number =
                    primitive.isString()
                            ? new BigInteger(primitive.getAsString())
                            : primitive.getAsBigDecimal().toBigIntegerExact();
            BigInteger minimum = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
            BigInteger maximum =
                    BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
            if (number.compareTo(minimum) < 0 || number.compareTo(maximum) > 0)
                throw invalid("整数字段超出 " + (unsigned ? "uint" : "int") + bits + "。");
            return number;
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid("整数字段应为范围内的整数。");
        }
    }

    private static JsonPrimitive numeric(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid("浮点字段应为数值。");
        return value.getAsJsonPrimitive();
    }

    private static boolean unsigned(FieldDescriptor field) {
        return switch (field.getType()) {
            case UINT32, UINT64, FIXED32, FIXED64 -> true;
            default -> false;
        };
    }

    private static boolean recognizedWire(FieldDescriptor field, UnknownFieldSet.Field value) {
        boolean matches =
                switch (field.getType()) {
                    case FLOAT, FIXED32, SFIXED32 -> !value.getFixed32List().isEmpty();
                    case DOUBLE, FIXED64, SFIXED64 -> !value.getFixed64List().isEmpty();
                    case STRING, BYTES, MESSAGE -> !value.getLengthDelimitedList().isEmpty();
                    case GROUP -> !value.getGroupList().isEmpty();
                    default -> !value.getVarintList().isEmpty();
                };
        return matches
                || (field.isRepeated() && field.isPackable() && !value.getLengthDelimitedList().isEmpty());
    }

    private static byte[] binary(JsonElement value, String label) {
        try {
            return Base64.getDecoder().decode(text(value, label));
        } catch (IllegalArgumentException e) {
            throw invalid(label + " 应为有效 Base64。");
        }
    }

    private static String text(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid(label + " 应为字符串。");
        return value.getAsString();
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw invalid(label + " 应为对象。");
        return value.getAsJsonObject();
    }

    private static FieldDescriptor field(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }

    private static Message child(Message message, String name) {
        return (Message) message.getField(field(message, name));
    }

    private static void keys(JsonObject object, Set<String> allowed) {
        for (var key : object.keySet()) if (!allowed.contains(key)) throw invalid("未知 JSON 属性：" + key);
    }

    private static void bound(int depth, int[] count) {
        if (depth > 64 || ++count[0] > 2000000) throw invalid("节点结构深度或字段数量超限。");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
