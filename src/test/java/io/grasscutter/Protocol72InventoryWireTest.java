package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Protocol72InventoryWireTest {
    @Test
    void rootFieldSetMatchesTheIndependentNativeReader() {
        assertEquals(Set.of(2, 8, 14), rootDescriptor().getFields().stream()
                .map(field -> field.getNumber()).collect(Collectors.toSet()));
    }

    @Test
    void independentRootVectorHasOneNonzeroItem() throws Exception {
        var descriptor = rootDescriptor();
        var parsed = DynamicMessage.parseFrom(descriptor, HexFormat.of().parseHex("120b082a1081808080808080107001"));
        assertNotNull(descriptor.findFieldByNumber(2));
        assertEquals(1, parsed.getRepeatedFieldCount(descriptor.findFieldByNumber(2)));
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("independentNativeFixtures")
    void generatedJavaMatchesIndependentNativeObjects(String name, int typeIndex, String wire,
                                                       JsonObject expected) throws Exception {
        var report = evidence();
        var specs = StreamSupport.stream(report.getAsJsonArray("messages").spliterator(), false).map(JsonElement::getAsJsonObject)
                .collect(Collectors.toMap(row -> row.get("typeIndex").getAsInt(), row -> row));
        var descriptor = Client72InventoryWireOuterClass.getDescriptor()
                .findMessageTypeByName(specs.get(typeIndex).get("clientType").getAsString());
        var parsed = DynamicMessage.parseFrom(descriptor, HexFormat.of().parseHex(wire));
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
        assertEquals(expected, snapshot(parsed, specs.get(typeIndex), specs));
    }

    @Test
    void completeRawClosureAndThreeOneofsRetainJavaExplicitZeroPresence() {
        var types = Client72InventoryWireOuterClass.getDescriptor().getMessageTypes();
        assertEquals(15, types.size());
        assertEquals(47, types.stream().mapToInt(type -> type.getFields().size()).sum());
        assertEquals(3, types.stream().flatMap(type -> type.getOneofs().stream())
                .filter(oneof -> !oneof.isSynthetic()).count());
        for (var type : types) {
            for (var field : type.getFields()) {
                if (!field.isRepeated() && field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                    assertTrue(field.toProto().getProto3Optional());
                }
            }
        }
    }

    @Test
    void truncatedNestedLengthRejectsWhileWrongWireRemainsUnknown() throws Exception {
        for (String wire : List.of("12050801", "12ffffffff0f", "12022a05")) {
            assertThrows(InvalidProtocolBufferException.class,
                    () -> DynamicMessage.parseFrom(rootDescriptor(), HexFormat.of().parseHex(wire)));
        }
        var wrongWire = DynamicMessage.parseFrom(rootDescriptor(), HexFormat.of().parseHex("1001"));
        assertTrue(wrongWire.getUnknownFields().hasField(2));
    }

    private static Descriptors.Descriptor rootDescriptor() {
        return Client72InventoryWireOuterClass.getDescriptor().findMessageTypeByName("CAIHCNHAMHB");
    }

    private static JsonObject evidence() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("protocol/cn-7.2/inventory-graph-observations.json")))
                .getAsJsonObject();
    }

    private static Stream<Arguments> independentNativeFixtures() throws Exception {
        return StreamSupport.stream(evidence().getAsJsonArray("fixtures").spliterator(), false).map(JsonElement::getAsJsonObject)
                .map(row -> Arguments.of(row.get("name").getAsString(), row.get("typeIndex").getAsInt(),
                        row.get("wire").getAsString(), row.getAsJsonObject("result")));
    }

    private static JsonObject snapshot(DynamicMessage message, JsonObject spec, Map<Integer, JsonObject> specs) {
        var result = new JsonObject();
        for (JsonElement element : spec.getAsJsonArray("fields")) {
            var row = element.getAsJsonObject();
            var field = message.getDescriptorForType().findFieldByNumber(row.get("tag").getAsInt());
            String name = row.get("clientField").getAsString();
            if (field.isMapField()) {
                var values = new JsonObject();
                for (Object value : (List<?>) message.getField(field)) {
                    var entry = (DynamicMessage) value;
                    int key = (int) entry.getField(field.getMessageType().findFieldByNumber(1));
                    int number = (int) entry.getField(field.getMessageType().findFieldByNumber(2));
                    values.addProperty(Integer.toUnsignedString(key), Integer.toUnsignedLong(number));
                }
                if (values.size() != 0) result.add(name, values);
            } else if (field.isRepeated()) {
                var values = new JsonArray();
                for (Object value : (List<?>) message.getField(field)) {
                    values.add(field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                            ? snapshot((DynamicMessage) value, specs.get(row.get("nestedTypeIndex").getAsInt()), specs)
                            : scalar(field, value));
                }
                if (values.size() != 0) result.add(name, values);
            } else if (message.hasField(field)) {
                Object value = message.getField(field);
                if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                    result.add(name, snapshot((DynamicMessage) value, specs.get(row.get("nestedTypeIndex").getAsInt()), specs));
                } else if (!value.equals(field.getDefaultValue())) {
                    result.add(name, scalar(field, value));
                }
            }
        }
        return result;
    }

    private static JsonElement scalar(Descriptors.FieldDescriptor field, Object value) {
        return switch (field.getType()) {
            case UINT32 -> new JsonPrimitive(Integer.toUnsignedLong((int) value));
            case UINT64 -> new JsonPrimitive(new BigInteger(Long.toUnsignedString((long) value)));
            case BOOL -> new JsonPrimitive((boolean) value);
            default -> throw new AssertionError("Unreviewed inventory scalar " + field.getType());
        };
    }
}
