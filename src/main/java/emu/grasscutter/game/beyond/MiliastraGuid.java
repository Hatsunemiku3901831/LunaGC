package emu.grasscutter.game.beyond;

import com.google.protobuf.*;
import emu.grasscutter.game.beyond.proto.MiliastraNodeProtocol;
import emu.grasscutter.game.beyond.proto.assets.MiliastraAssetDefinitions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/** Copies known GIA asset IDs and their typed references. Opaque fields are never guessed. */
public final class MiliastraGuid {
    public record Analysis(List<Long> definitions, Set<Long> references, boolean opaque) {}

    public record Pair(long from, long to) {}

    public record Result(
            byte[] bytes, int guidCount, int changedFields, List<Pair> mapping, List<String> warnings) {}

    private record Part(int start, int end, int tag, byte[] unit) {}

    private record Unit(DynamicMessage message, boolean client, boolean bare) {}

    private record Type(boolean client, int code, int key, int value) {
        static final Type NONE = new Type(false, 0, 0, 0);

        boolean guid() {
            return code == (client ? 14 : 2);
        }

        Type element() {
            return new Type(client, code == (client ? 15 : 7) ? (client ? 14 : 2) : 0, 0, 0);
        }
    }

    private static final Set<Integer> GRAPHS = Set.of(9, 10, 11, 22, 23, 46, 47);
    private static final Descriptors.Descriptor ASSET =
            MiliastraAssetDefinitions.Asset.getDescriptor();
    private static final Descriptors.Descriptor GRAPH =
            MiliastraNodeProtocol.GraphUnit.getDescriptor();
    private final Map<Long, Long> mapping;
    private final LinkedHashSet<Long> definitions = new LinkedHashSet<>();
    private final Set<Long> references = new HashSet<>();
    private long work = 20000000;
    private int changed;
    private boolean opaque;

    private MiliastraGuid(Map<Long, Long> mapping) {
        this.mapping = mapping;
    }

    public static Analysis analyze(byte[] bytes) {
        var scan = new MiliastraGuid(Map.of());
        for (var part : parts(bytes)) {
            if (part.unit() == null) {
                if (part.tag() != 26 && part.tag() != 42) scan.opaque = true;
                continue;
            }
            Unit unit = unit(part);
            scan.define(unit);
            scan.walk(unit.message(), Type.NONE, unit.client(), 0);
        }
        if (scan.definitions.isEmpty()) throw invalid("文件没有可重新编号的完整资产。");
        return new Analysis(List.copyOf(scan.definitions), Set.copyOf(scan.references), scan.opaque);
    }

    public static Result remap(byte[] bytes, int firstGuid) {
        Analysis analysis = analyze(bytes);
        if (firstGuid < 1 || (long) firstGuid + analysis.definitions().size() - 1 > Integer.MAX_VALUE)
            throw invalid("GUID 起点或范围超出正 int32。");
        var mapping = new LinkedHashMap<Long, Long>();
        for (long old : analysis.definitions()) mapping.put(old, firstGuid + (long) mapping.size());
        return remap(bytes, mapping);
    }

    public static Result remap(byte[] bytes, Map<Long, Long> mapping) {
        Analysis analysis = analyze(bytes);
        if (!mapping.keySet().equals(new HashSet<>(analysis.definitions()))
                || new HashSet<>(mapping.values()).size() != mapping.size()
                || mapping.values().stream().anyMatch(v -> v == null || v < 1 || v > Integer.MAX_VALUE))
            throw invalid("映射须完整覆盖资产，且目标为互不重复的正 int32。");
        var reserved = new HashSet<>(analysis.references());
        reserved.removeAll(mapping.keySet());
        if (!Collections.disjoint(reserved, mapping.values())) throw invalid("目标 GUID 与文件中外部资产引用重合。");
        var transform = new MiliastraGuid(Map.copyOf(mapping));
        var payload = new ByteArrayOutputStream();
        try {
            var output = CodedOutputStream.newInstance(payload);
            for (var part : parts(bytes)) {
                if (part.unit() == null) {
                    output.writeRawBytes(bytes, part.start(), part.end() - part.start());
                } else {
                    Unit unit = unit(part);
                    var updated = transform.walk(unit.message(), Type.NONE, unit.client(), 0);
                    if (updated.equals(unit.message()))
                        output.writeRawBytes(bytes, part.start(), part.end() - part.start());
                    else output.writeByteArray(part.tag() >>> 3, updated.toByteArray());
                }
                if (payload.size() > MiliastraFile.MAX_BYTES) throw invalid("重新编号后文件超过 32 MiB。");
            }
            output.flush();
        } catch (IOException e) {
            throw invalid("无法重新编码资产。");
        }
        byte[] body = payload.toByteArray();
        if (body.length > MiliastraFile.MAX_BYTES - 24) throw invalid("重新编号后文件超过 32 MiB。");
        byte[] result =
                transform.changed == 0
                        ? bytes.clone()
                        : ByteBuffer.allocate(body.length + 24)
                                .putInt(body.length + 20)
                                .putInt(ByteBuffer.wrap(bytes).getInt(4))
                                .putInt(0x326)
                                .putInt(3)
                                .putInt(body.length)
                                .put(body)
                                .putInt(0x679)
                                .array();
        var pairs = new ArrayList<Pair>();
        for (long old : analysis.definitions())
            if (pairs.size() < 100) pairs.add(new Pair(old, mapping.get(old)));
        var warnings = new ArrayList<String>();
        warnings.add("仅重新编号已知资产 GUID 与类型明确的引用；未知字段保留，未推测其中的编号。当前客户端导入尚未验证。");
        if (analysis.opaque()) warnings.add("文件含有参考协议未解释的字段，可能还需要在编辑器检查关联引用。");
        return new Result(
                result, mapping.size(), transform.changed, List.copyOf(pairs), List.copyOf(warnings));
    }

    private static List<Part> parts(byte[] bytes) {
        var preview = MiliastraFile.inspect(bytes, "source.gia");
        if (preview.version() != 1) throw invalid("仅支持已知的版本 1 GIA 重新编号。");
        var result = new ArrayList<Part>();
        try {
            var input = CodedInputStream.newInstance(bytes, 20, bytes.length - 24);
            input.setRecursionLimit(64);
            int tag;
            while ((tag = input.readTag()) != 0) {
                // Preserve noncanonical tags too: use the end of the preceding field as the boundary.
                int start = result.isEmpty() ? 20 : result.get(result.size() - 1).end();
                byte[] unit = tag == 10 || tag == 18 ? input.readByteArray() : null;
                if (unit == null && !input.skipField(tag)) throw invalid("负载含未配对的结束组。");
                result.add(new Part(start, 20 + input.getTotalBytesRead(), tag, unit));
            }
            input.checkLastTagWas(0);
        } catch (IOException e) {
            throw invalid("protobuf 负载损坏。");
        }
        return result;
    }

    /** All known references, including metadata beyond the preview and unsupported body types. */
    static Set<Long> knownReferences(byte[] bytes) {
        var scan = new MiliastraGuid(Map.of());
        for (var part : parts(bytes)) {
            if (part.unit() == null) continue;
            Unit unit;
            try {
                unit = unit(part);
            } catch (IllegalArgumentException e) {
                try {
                    unit = new Unit(parse(ASSET, part.unit()), false, true);
                } catch (IOException damaged) {
                    throw invalid("无法扫描归档中的引用。");
                }
            }
            scan.walk(unit.message(), Type.NONE, unit.client(), 0);
        }
        return Set.copyOf(scan.references);
    }

    private static Unit unit(Part part) {
        try {
            var common = parse(ASSET, part.unit());
            int which = (int) number(common, "type");
            boolean bare =
                    part.tag() == 18
                            && common.getAllFields().keySet().stream()
                                    .allMatch(f -> Set.of(1, 2, 3, 5).contains(f.getNumber()))
                            && common.getUnknownFields().asMap().isEmpty();
            if (bare) return new Unit(common, false, true);
            if (which == 3 || which == 13) {
                if (!has(common, which == 3 ? "entity_data" : "camera_config")) throw invalid("实体或镜头缺少内容。");
                return new Unit(common, false, false);
            }
            if (!GRAPHS.contains(which)) throw invalid("资产类型 " + which + " 尚未支持完整 GUID 重新编号。");
            var graph = parse(GRAPH, part.unit());
            if (!has(graph, "graph")) throw invalid("节点图缺少内容。");
            return new Unit(graph, number(child(graph, "id"), "type") == 3, false);
        } catch (IOException e) {
            throw invalid("资产 protobuf 内容损坏或嵌套过深。");
        }
    }

    private static DynamicMessage parse(Descriptors.Descriptor descriptor, byte[] bytes)
            throws IOException {
        var input = CodedInputStream.newInstance(bytes);
        input.setRecursionLimit(64);
        var result = DynamicMessage.parseFrom(descriptor, input);
        input.checkLastTagWas(0);
        return result;
    }

    private void define(Unit unit) {
        if (unit.bare()) return;
        var message = unit.message();
        boolean graph = message.getDescriptorForType() == GRAPH;
        long id = number(child(message, graph ? "id" : "meta"), graph ? "id" : "asset_id");
        definition(id);
        if (graph) {
            var inner = child(child(child(message, "graph"), "inner"), "graph");
            var innerId = child(inner, "id");
            if (number(innerId, "id") != id
                    || number(innerId, "class") != 10000
                    || number(innerId, "kind") != 21001) throw invalid("节点图内外 GUID 不一致，或不是已知的用户节点图。");
        } else if (has(message, "entity_data")) {
            if (number(child(child(message, "entity_data"), "data"), "entity_id") != id)
                throw invalid("实体内外 GUID 不一致。");
        } else {
            boolean matched = false;
            var details = child(message, "camera_config");
            for (var item : repeated(details, "camera_details")) {
                long camera = number(item, "camera_id");
                if (camera == id && !matched) matched = true;
                else definition(camera);
            }
            if (!matched) throw invalid("镜头内容没有对应的主资产 GUID。");
        }
    }

    private void definition(long id) {
        if (id < 1 || id > Integer.MAX_VALUE || !definitions.add(id))
            throw invalid("资产 GUID 无效或重复：" + id);
    }

    private DynamicMessage walk(DynamicMessage message, Type inherited, boolean client, int depth) {
        if (++depth > 64 || --work < 0) throw invalid("引用扫描嵌套或工作量超限。");
        if (!message.getUnknownFields().asMap().isEmpty()) opaque = true;
        String name = message.getDescriptorForType().getName();
        Type type = inherited;
        if (name.equals("VarBase") && has(message, "itemType")) {
            var item = child(message, "itemType");
            if (has(item, "type_client"))
                type = new Type(true, (int) number(child(item, "type_client"), "type"), 0, 0);
            else if (has(item, "type_server")) {
                var server = child(item, "type_server");
                var pair = child(server, "items");
                type =
                        new Type(
                                false,
                                (int) number(server, "type"),
                                (int) number(pair, "key"),
                                (int) number(pair, "value"));
            }
        }
        var builder = message.toBuilder();
        for (var entry : message.getAllFields().entrySet()) {
            if (--work < 0) throw invalid("引用扫描工作量超限。");
            var field = entry.getKey();
            if (field.getType() == Descriptors.FieldDescriptor.Type.BYTES) opaque = true;
            if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                Type next = type;
                String f = field.getName();
                if (name.equals("NodePin") && f.equals("value"))
                    next = new Type(client, (int) number(message, "type"), 0, 0);
                if (name.equals("GraphVariable") && f.equals("values"))
                    next =
                            new Type(
                                    false,
                                    (int) number(message, "type"),
                                    (int) number(message, "keyType"),
                                    (int) number(message, "valueType"));
                if (name.equals("ArrayBaseValue")) next = type.element();
                if (name.equals("MapPairBaseValue"))
                    next = new Type(false, f.equals("key") ? type.key() : type.value(), 0, 0);
                if (name.equals("StructBaseValue")) next = Type.NONE;
                if (field.isRepeated()) {
                    var items = (List<?>) entry.getValue();
                    for (int i = 0; i < items.size(); i++)
                        builder.setRepeatedField(
                                field, i, walk((DynamicMessage) items.get(i), next, client, depth));
                } else
                    builder.setField(field, walk((DynamicMessage) entry.getValue(), next, client, depth));
            } else if (guidField(message, field, type)) {
                long old = ((Number) entry.getValue()).longValue();
                if (old > 0) references.add(old);
                Long target = mapping.get(old);
                if (target != null && target != old) {
                    changed++;
                    Object value =
                            field.getJavaType() == Descriptors.FieldDescriptor.JavaType.LONG
                                    ? (Object) target
                                    : target.intValue();
                    builder.setField(field, value);
                }
            }
        }
        return builder.build();
    }

    private static boolean guidField(
            DynamicMessage message, Descriptors.FieldDescriptor field, Type type) {
        String full = message.getDescriptorForType().getFullName(), name = field.getName();
        return full.endsWith(".AssetMeta") && name.equals("asset_id")
                || full.endsWith(".EntityData") && name.equals("entity_id")
                || full.endsWith(".CameraDetail") && name.equals("camera_id")
                || full.endsWith(".GraphUnit.Id") && name.equals("id")
                || full.endsWith(".NodeGraph.Id") && name.equals("id") && number(message, "class") == 10000
                || full.endsWith(".NodeProperty")
                        && name.equals("nodeId")
                        && number(message, "class") == 10000
                || full.endsWith(".GraphVariable") && name.equals("structId")
                || full.endsWith(".VarBase.ItemType.StructItem") && name.equals("structId")
                || full.endsWith(".VarBase.ItemType.PairItems") && name.equals("structId")
                || full.endsWith(".IdBaseValue") && name.equals("val") && type.guid();
    }

    private static boolean has(DynamicMessage message, String field) {
        return message.hasField(message.getDescriptorForType().findFieldByName(field));
    }

    private static DynamicMessage child(DynamicMessage message, String field) {
        return (DynamicMessage) message.getField(message.getDescriptorForType().findFieldByName(field));
    }

    private static List<DynamicMessage> repeated(DynamicMessage message, String field) {
        @SuppressWarnings("unchecked")
        var result =
                (List<DynamicMessage>)
                        message.getField(message.getDescriptorForType().findFieldByName(field));
        return result;
    }

    private static long number(DynamicMessage message, String field) {
        Object value = message.getField(message.getDescriptorForType().findFieldByName(field));
        return value instanceof Descriptors.EnumValueDescriptor e
                ? e.getNumber()
                : ((Number) value).longValue();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
