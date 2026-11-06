package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import emu.grasscutter.net.proto.Client72AvatarWireOuterClass;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Protocol72AvatarWireTest {
    private static final Path DIRECTORY = Path.of("protocol/cn-7.2");
    private static final Evidence EVIDENCE = loadEvidence();
    private record NativeCase(String source, String name, int typeIndex, String wire, JsonObject expected) {}
    private record Evidence(Map<Integer, JsonObject> specs, Map<Integer, Message> defaults, List<NativeCase> cases) {}

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("independentNativeFixtures")
    void actualGeneratedJavaMatchesNativeObjectsAndExportsItsWire(String source, String name, int typeIndex,
                                                                   String wire, JsonObject expected) throws Exception {
        Message parsed = EVIDENCE.defaults().get(typeIndex).getParserForType().parseFrom(HexFormat.of().parseHex(wire));
        assertEquals(expected, snapshot(parsed, EVIDENCE.specs().get(typeIndex)), source + "/" + name);
        byte[] serialized = deterministicWire(parsed);
        Message reparsed = parsed.getParserForType().parseFrom(serialized);
        assertEquals(parsed, reparsed);
        assertArrayEquals(serialized, deterministicWire(reparsed));
        var output = new JsonObject(); output.addProperty("source", source); output.addProperty("name", name);
        output.addProperty("typeIndex", typeIndex); output.addProperty("wire", HexFormat.of().formatHex(serialized));
        output.addProperty("generatedClass", parsed.getClass().getName());
        System.out.println("CN72_AVATAR_WIRE " + output);
    }

    @Test
    void completeDescriptorRetainsAll65Types372FieldsSevenRealOneofsAnd42PackedLists() {
        var types = Client72AvatarWireOuterClass.getDescriptor().getMessageTypes();
        assertEquals("cn72.avatarwire", Client72AvatarWireOuterClass.getDescriptor().getPackage());
        assertEquals(65, types.size()); assertEquals(372, types.stream().mapToInt(type -> type.getFields().size()).sum());
        assertEquals(7, types.stream().flatMap(type -> type.getOneofs().stream()).filter(oneof -> !oneof.isSynthetic()).count());
        assertEquals(42, types.stream().flatMap(type -> type.getFields().stream()).filter(field -> field.isRepeated() && field.isPackable() && field.isPacked()).count());
        assertEquals(13, types.stream().flatMap(type -> type.getFields().stream()).filter(Descriptors.FieldDescriptor::isMapField).count());
        for (var type : types) for (var field : type.getFields()) {
            assertNotEquals(Descriptors.FieldDescriptor.Type.ENUM, field.getType());
            if (!field.isRepeated() && field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE
                    && field.getRealContainingOneof() == null) assertTrue(field.toProto().getProto3Optional());
        }
    }

    @Test
    void twoObjectGroupsAndTag100RetainActiveZeroPresence() throws Exception {
        Message parsed = parse(51050, "080110020800a20600"); var descriptor = parsed.getDescriptorForType();
        assertEquals(2, descriptor.getRealOneofs().size());
        assertTrue(parsed.hasField(descriptor.findFieldByNumber(1))); assertEquals(0, parsed.getField(descriptor.findFieldByNumber(1)));
        assertFalse(parsed.hasField(descriptor.findFieldByNumber(2))); assertTrue(parsed.hasField(descriptor.findFieldByNumber(100)));
        assertEquals(Set.of(1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 100), descriptor.getFields().stream().map(Descriptors.FieldDescriptor::getNumber).collect(Collectors.toSet()));
    }

    @Test
    void optionalScalarPresencePreservesExplicitZeroAcrossBuilderMerge() throws Exception {
        Message before = parse(51614, "080118ffffffffffffffffff014801");
        Message zero = parse(51614, "080018004800");
        Message merged = before.toBuilder().mergeFrom(zero).build();
        assertEquals(new JsonObject(), snapshot(merged, EVIDENCE.specs().get(51614)));
        for (int number : List.of(1, 3, 9)) assertTrue(merged.hasField(merged.getDescriptorForType().findFieldByNumber(number)));
        assertEquals("080018004800", HexFormat.of().formatHex(deterministicWire(merged)));
    }

    @Test
    void fourteenCopiedItemTypesKeepTheExistingIndependentInventorySchema() {
        int count = 0;
        for (var type : Client72InventoryWireOuterClass.getDescriptor().getMessageTypes()) {
            if (type.getName().equals("CAIHCNHAMHB")) continue;
            var avatarType = Client72AvatarWireOuterClass.getDescriptor().findMessageTypeByName(type.getName());
            assertNotNull(avatarType); assertEquals(type.getFields().size(), avatarType.getFields().size()); count++;
            for (var field : type.getFields()) {
                var other = avatarType.findFieldByNumber(field.getNumber()); assertNotNull(other);
                assertEquals(field.getName(), other.getName()); assertEquals(field.getType(), other.getType());
                assertEquals(field.isRepeated(), other.isRepeated()); assertEquals(field.isMapField(), other.isMapField());
                assertEquals(field.isPacked(), other.isPacked()); assertEquals(field.hasPresence(), other.hasPresence());
                if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) assertEquals(field.getMessageType().getName(), other.getMessageType().getName());
                assertEquals(field.getRealContainingOneof() == null, other.getRealContainingOneof() == null);
            }
        }
        assertEquals(14, count);
    }

    @Test
    void floatNegativeZeroAndUint64HighBitsSurviveActualGeneratedSerialization() throws Exception {
        Message zero = parse(80101, "15000000800d00000000"); var first = zero.getDescriptorForType().findFieldByNumber(2);
        assertEquals(0x80000000, Float.floatToRawIntBits((float) zero.getField(first)));
        assertEquals("0d000000001500000080", HexFormat.of().formatHex(deterministicWire(zero)));
        Message full = parse(63939, "088180808080808010"); assertEquals(9007199254740993L, full.getField(full.getDescriptorForType().findFieldByNumber(1)));
        Message unsigned = parse(19829, "10ffffffffffffffffff01"); assertEquals(-1L, unsigned.getField(unsigned.getDescriptorForType().findFieldByNumber(2)));
    }

    @Test
    void protobufRejectsNegativePackedLengthThatNativeBoundedReaderIgnores() {
        // e4 observed signed jle/no-append; protobuf has a stricter length contract.
        assertThrows(InvalidProtocolBufferException.class, () -> parse(89623, "4affffffff0f"));
    }

    @Test
    void malformedLengthsRejectAndWrongWireIsRetainedAsUnknownUnlikeNativeSkip() throws Exception {
        for (String wire : List.of("a206ffffffff0f", "a206030801", "5a020000", "220180", "00"))
            assertThrows(InvalidProtocolBufferException.class, () -> parse(51050, wire));
        Message parsed = parse(51050, "0a00a00607800809");
        assertEquals(new JsonObject(), snapshot(parsed, EVIDENCE.specs().get(51050)));
        assertEquals(Set.of(1, 100, 128), parsed.getUnknownFields().asMap().keySet());
        assertArrayEquals(HexFormat.of().parseHex("0a00a00607800809"), deterministicWire(parsed));
    }

    private static Stream<Arguments> independentNativeFixtures() {
        return EVIDENCE.cases().stream().map(row -> Arguments.of(row.source(), row.name(), row.typeIndex(), row.wire(), row.expected()));
    }

    private static Message parse(int typeIndex, String wire) throws InvalidProtocolBufferException {
        return EVIDENCE.defaults().get(typeIndex).getParserForType().parseFrom(HexFormat.of().parseHex(wire));
    }

    private static byte[] deterministicWire(Message message) throws IOException {
        var bytes = new ByteArrayOutputStream(); var output = CodedOutputStream.newInstance(bytes);
        output.useDeterministicSerialization(); message.writeTo(output); output.flush(); return bytes.toByteArray();
    }

    private static Evidence loadEvidence() {
        try {
            JsonObject complete = readReport("complete-raw"); var specs = new LinkedHashMap<Integer, JsonObject>();
            for (JsonElement element : complete.getAsJsonArray("closureMessages")) {
                var row = element.getAsJsonObject(); specs.put(row.get("typeIndex").getAsInt(), row);
            }
            var defaults = new HashMap<Integer, Message>();
            for (var entry : specs.entrySet()) {
                String className = Client72AvatarWireOuterClass.class.getName() + "$" + entry.getValue().get("clientType").getAsString();
                defaults.put(entry.getKey(), (Message) Class.forName(className).getMethod("getDefaultInstance").invoke(null));
            }
            var reports = new LinkedHashMap<String, JsonObject>(); reports.put("complete-raw", complete);
            for (String name : complete.getAsJsonObject("sourceEvidenceSHA256").keySet()) reports.put(name, readReport(name));
            var cases = new ArrayList<NativeCase>();
            for (var report : reports.entrySet()) for (JsonElement element : report.getValue().getAsJsonArray("fixtures")) {
                var row = element.getAsJsonObject(); int index = row.get("typeIndex").getAsInt(); String name = row.get("name").getAsString();
                if (!specs.containsKey(index) || row.get("result").toString().contains("untracedType") || name.equals("signed-negative-packed-length-no-append")) continue;
                cases.add(new NativeCase(report.getKey(), name, index, row.get("wire").getAsString(), row.getAsJsonObject("result")));
            }
            return new Evidence(specs, defaults, cases);
        } catch (IOException error) { throw new UncheckedIOException(error); }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }

    private static JsonObject readReport(String name) throws IOException {
        return JsonParser.parseString(Files.readString(DIRECTORY.resolve("avatar-" + name + "-observations.json"))).getAsJsonObject();
    }

    private static JsonObject snapshot(Message message, JsonObject spec) {
        var result = new JsonObject();
        for (JsonElement element : spec.getAsJsonArray("fields")) {
            var row = element.getAsJsonObject(); var field = message.getDescriptorForType().findFieldByNumber(row.get("tag").getAsInt());
            String name = row.get("clientField").getAsString();
            if (field.isMapField()) {
                var values = new JsonObject();
                for (Object value : (List<?>) message.getField(field)) {
                    var entry = (Message) value; var keyField = field.getMessageType().findFieldByNumber(1); var valueField = field.getMessageType().findFieldByNumber(2);
                    String key = scalar(keyField, entry.getField(keyField)).getAsString(); Object mapValue = entry.getField(valueField);
                    values.add(key, valueField.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                            ? snapshot((Message) mapValue, EVIDENCE.specs().get(row.get("nestedTypeIndex").getAsInt())) : scalar(valueField, mapValue));
                }
                if (values.size() != 0) result.add(name, values);
            } else if (field.isRepeated()) {
                var values = new JsonArray();
                for (Object value : (List<?>) message.getField(field)) values.add(field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                        ? snapshot((Message) value, EVIDENCE.specs().get(row.get("nestedTypeIndex").getAsInt())) : scalar(field, value));
                if (values.size() != 0) result.add(name, values);
            } else if (message.hasField(field)) {
                Object value = message.getField(field);
                if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) result.add(name, snapshot((Message) value, EVIDENCE.specs().get(row.get("nestedTypeIndex").getAsInt())));
                else if (row.has("discriminatorOffset") || !value.equals(field.getDefaultValue())) result.add(name, scalar(field, value));
            }
        }
        return result;
    }

    private static JsonElement scalar(Descriptors.FieldDescriptor field, Object value) {
        return switch (field.getType()) {
            case UINT32 -> new JsonPrimitive(Integer.toUnsignedLong((int) value));
            case UINT64 -> new JsonPrimitive(new BigInteger(Long.toUnsignedString((long) value)));
            case INT32 -> new JsonPrimitive((int) value);
            case INT64 -> new JsonPrimitive((long) value);
            case FLOAT -> new JsonPrimitive(((Float) value).doubleValue());
            case BOOL -> new JsonPrimitive((boolean) value);
            case STRING -> new JsonPrimitive((String) value);
            default -> throw new AssertionError("Unreviewed Avatar scalar " + field.getType());
        };
    }
}
