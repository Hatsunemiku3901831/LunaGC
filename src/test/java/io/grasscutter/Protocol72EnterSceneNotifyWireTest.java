package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Raw wire projection through production descriptors, with independent native fixture bytes/values.
 * Source IDs: scene-wait-observations.json and scene-nested-observations.json; the full nested wire
 * is retained in scene-nested-final-trace.json/readerFixtures. No production encoder creates these
 * inputs or expectations. Application arithmetic/defaults, outer vector semantics and client
 * acceptance are outside this test.
 */
class Protocol72EnterSceneNotifyWireTest {
    private static final String ROOT = "PlayerEnterSceneNotify";
    private static final List<String> FAMILY = List.of(ROOT, "BGDCNKAKGHI", "NAECHMKLBEI",
            "HIGIKLLJCHJ", "HKDMELCIJGB", "ABJMLEPLFJG", "HJLMHFHHJKM", "PCCNENMODDD");

    // scene-nested: scene-all-nonempty. All 26 root fields and all 18 distinct child fields.
    private static final String ALL_NONEMPTY =
            "08a081808008108c8180800828a4818080083090e4d0b287d3aeeefe0138fc808080084002"
            + "5094818080085880818080086001f002f480808008e8099c81808008e83a9081808008"
            + "e83b8881808008885401f862f880808008c07df0808080086a0d00018080808008ffffffff0f01"
            + "aa1211e585a5e59cbae5b58ce5a597f09f8c9900720f0d0000a03f15000020c01d00007040"
            + "7a0f0d0000a03f15000020c01d00007040da180218019a2e0c520a9880d091019880808008"
            + "da2f0f0d0000a03f15000020c01d00007040ea54410a1308a080808080d3aeeefe01109880d091011801"
            + "3213e5b58ce5a597f09f8c9933343133335f3430006a13e5b58ce5a597f09f8c9933343133335f3234007801"
            + "da6b380a240a0a9880d091019880808008320aa880d09101a8808080083a0aa080d09101a080808008"
            + "58a880d091016a0a9880d091019880808008e27a240a0a9880d091019880808008"
            + "320aa880d09101a8808080083a0aa080d09101a080808008";

    @Test
    void commandAndAllEightRawDescriptorsMatchTheTargetFortyFourFieldClosure() throws Exception {
        assertEquals(27748, PacketOpcodes.PlayerEnterSceneNotify);
        int count = 0;
        for (String name : FAMILY) count += descriptor(name).getFields().size();
        assertEquals(44, count);
        var root = descriptor(ROOT);
        assertTags(root, 1, 2, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 46, 157,
                293, 395, 739, 763, 941, 957, 1345, 1357, 1583, 1723, 1964, 2008);
        assertType(root, Type.UINT32, 1, 2, 5, 7, 10, 11, 13, 46, 157, 941, 957, 1583, 2008);
        assertType(root, Type.UINT64, 6);
        assertType(root, Type.INT32, 8);
        assertType(root, Type.BOOL, 12, 1345);
        assertType(root, Type.STRING, 293);
        assertRepeated(root, Set.of(13));
        assertChild(root, "BGDCNKAKGHI", 14, 15, 763);
        assertChild(root, "NAECHMKLBEI", 395);
        assertChild(root, "HKDMELCIJGB", 739);
        assertChild(root, "HJLMHFHHJKM", 1357);
        assertChild(root, "PCCNENMODDD", 1723);
        assertChild(root, "ABJMLEPLFJG", 1964);

        var vector = descriptor("BGDCNKAKGHI");
        assertTags(vector, 1, 2, 3); assertType(vector, Type.FLOAT, 1, 2, 3);
        assertRepeated(vector, Set.of());
        var flag = descriptor("NAECHMKLBEI");
        assertTags(flag, 3); assertType(flag, Type.BOOL, 3); assertRepeated(flag, Set.of());
        var wide = descriptor("HIGIKLLJCHJ");
        assertTags(wide, 1, 2, 3); assertType(wide, Type.UINT64, 1);
        assertType(wide, Type.UINT32, 2); assertType(wide, Type.BOOL, 3); assertRepeated(wide, Set.of());
        var list = descriptor("HKDMELCIJGB");
        assertTags(list, 10); assertType(list, Type.UINT32, 10); assertRepeated(list, Set.of(10));
        var triple = descriptor("ABJMLEPLFJG");
        assertTags(triple, 1, 6, 7); assertType(triple, Type.UINT32, 1, 6, 7);
        assertRepeated(triple, Set.of(1, 6, 7));
        var strings = descriptor("HJLMHFHHJKM");
        assertTags(strings, 1, 6, 13, 15); assertChild(strings, "HIGIKLLJCHJ", 1);
        assertType(strings, Type.STRING, 6, 13); assertType(strings, Type.BOOL, 15);
        assertRepeated(strings, Set.of());
        var wrapper = descriptor("PCCNENMODDD");
        assertTags(wrapper, 1, 11, 13); assertChild(wrapper, "ABJMLEPLFJG", 1);
        assertType(wrapper, Type.UINT32, 11, 13); assertRepeated(wrapper, Set.of(13));
    }

    @Test
    void completeNativeNonemptyGraphKeepsAllRawValuesAndNonzeroNestedContent() throws Exception {
        var root = parse(ROOT, ALL_NONEMPTY);
        assertEquals(26, root.getAllFields().size());
        assertTrue(root.getUnknownFields().asMap().isEmpty());
        Map<Integer, Long> scalars = Map.ofEntries(
                Map.entry(1, 0x800000A0L), Map.entry(2, 0x8000008CL), Map.entry(5, 0x800000A4L),
                Map.entry(7, 0x8000007CL), Map.entry(10, 0x80000094L), Map.entry(11, 0x80000080L),
                Map.entry(46, 0x80000074L), Map.entry(157, 0x8000009CL), Map.entry(941, 0x80000090L),
                Map.entry(957, 0x80000088L), Map.entry(1583, 0x80000078L), Map.entry(2008, 0x80000070L));
        scalars.forEach((tag, expected) -> assertU32(root, tag, expected));
        assertEquals(0xFEDCBA9876543210L, value(root, 6));
        assertEquals(2, value(root, 8));
        assertEquals(true, value(root, 12)); assertEquals(true, value(root, 1345));
        assertList(root, 13, 0, 1, 0x80000000L, 0xFFFFFFFFL, 1);
        assertEquals("入场嵌套🌙\0", value(root, 293));
        for (int tag : new int[] {14, 15, 763}) {
            var vector = child(root, tag);
            assertFloatBits(vector, 1, 0x3FA00000); assertFloatBits(vector, 2, 0xC0200000);
            assertFloatBits(vector, 3, 0x40700000);
        }
        assertEquals(true, value(child(root, 395), 3));
        assertList(child(root, 739), 10, 0x12340018L, 0x80000018L);
        var strings = child(root, 1357);
        assertEquals("嵌套🌙34133_40\0", value(strings, 6));
        assertEquals("嵌套🌙34133_24\0", value(strings, 13));
        assertEquals(true, value(strings, 15));
        var wide = child(strings, 1);
        assertEquals(0xFEDCBA9800000020L, value(wide, 1));
        assertU32(wide, 2, 0x12340018L); assertEquals(true, value(wide, 3));
        assertThreeLists(child(root, 1964));
        var wrapper = child(root, 1723);
        assertU32(wrapper, 11, 0x12340028L);
        assertList(wrapper, 13, 0x12340018L, 0x80000018L);
        assertThreeLists(child(wrapper, 1));
    }

    @Test
    void nativeOuterScalarBoundaryRetainsUint64MaximumAndEveryUint32Bit() throws Exception {
        // scene-wait: scalar-boundaries; 0xffffffffffffffff is a Java long bit pattern of -1.
        var root = parse(ROOT, "08ffffffff0f10ffffffff0f28ffffffff0f30ffffffffffffffffff01"
                + "38ffffffff0f40ffffffffffffffffff0150ffffffff0f58ffffffff0f6001f002ffffffff0f"
                + "e809ffffffff0fe83affffffff0fe83bffffffff0f885401f862ffffffff0fc07dffffffff0f");
        for (int tag : new int[] {1, 2, 5, 7, 10, 11, 46, 157, 941, 957, 1583, 2008}) {
            assertU32(root, tag, 0xFFFFFFFFL);
        }
        assertEquals(-1L, value(root, 6)); assertEquals(-1, value(root, 8));
        assertEquals(true, value(root, 12)); assertEquals(true, value(root, 1345));
    }

    @Test
    void explicitRawZerosDoNotBecomeTheNativeConstructorsEncodedDefaults() throws Exception {
        // scene-wait: explicit-wire-zero-replaces-encoded-constructor-defaults.
        var root = parse(ROOT, "080010002800300038004000500058006000f00200e80900e83a00e83b00"
                + "885400f86200c07d00");
        for (int tag : new int[] {1, 2, 5, 7, 10, 11, 46, 157, 941, 957, 1583, 2008}) assertU32(root, tag, 0);
        assertEquals(0L, value(root, 6)); assertEquals(0, value(root, 8));
        assertEquals(false, value(root, 12)); assertEquals(false, value(root, 1345));
        assertList(root, 13); assertEquals("", value(root, 293));
    }

    @Test
    void independentInnerUint64MaximumAndBooleanNormalizationSurvive() throws Exception {
        // scene-nested: 36137-independent-max; the inner uint64 is independent of root tag6.
        var wide = parse("HIGIKLLJCHJ", "08ffffffffffffffffff0110ffffffff0f1801");
        assertEquals(-1L, value(wide, 1)); assertU32(wide, 2, 0xFFFFFFFFL);
        assertEquals(true, value(wide, 3));
        // scene-nested: 66040-explicit-false / 66040-bool-two-normalized.
        assertEquals(false, value(parse("NAECHMKLBEI", "1800"), 3));
        assertEquals(true, value(parse("NAECHMKLBEI", "1802"), 3));
    }

    @Test
    void nativeMixedRootListAndRepeatedVectorFragmentsMergeWithoutLosingExplicitZero() throws Exception {
        // scene-wait: duplicate-scalar-mixed-list-nested-merge.
        var root = parse(ROOT, "500150ffffffff0f68016a0602ffffffff0f680072050d0000a03f720515000020c0");
        assertU32(root, 10, 0xFFFFFFFFL); assertList(root, 13, 1, 2, 0xFFFFFFFFL, 0);
        var vector = child(root, 14);
        assertFloatBits(vector, 1, 0x3FA00000); assertFloatBits(vector, 2, 0xC0200000);
        assertFloatBits(vector, 3, 0);
    }

    @Test
    void independentNestedPackedUnpackedAndRepeatedRunsKeepOrderAndDuplicates() throws Exception {
        // scene-nested: 67761-packed / 67761-unpacked / 67761-mixed-duplicates.
        String packed = "520c018080808008ffffffff0f01";
        String unpacked = "500150808080800850ffffffff0f5001";
        assertList(parse("HKDMELCIJGB", packed), 10, 1, 0x80000000L, 0xFFFFFFFFL, 1);
        assertList(parse("HKDMELCIJGB", unpacked), 10, 1, 0x80000000L, 0xFFFFFFFFL, 1);
        assertList(parse("HKDMELCIJGB", packed + unpacked), 10,
                1, 0x80000000L, 0xFFFFFFFFL, 1, 1, 0x80000000L, 0xFFFFFFFFL, 1);
        // scene-wait: packed-list, including explicit0 and127/128 boundary.
        assertList(parse(ROOT, "6a09007f8001ffffffff0f"), 13, 0, 127, 128, 0xFFFFFFFFL);
    }

    @Test
    void nativeDuplicateInnerMessageReplacesExplicitZeroAndPreservesOtherFields() throws Exception {
        // scene-nested: 34133-duplicate-child-explicit-zero, independently hand-built native input.
        var strings = parse("HJLMHFHHJKM", "0a0d0890e4d0b287d3aeeefe01107b0a0410001801");
        var wide = child(strings, 1);
        assertEquals(0xFEDCBA9876543210L, value(wide, 1)); assertU32(wide, 2, 0);
        assertEquals(true, value(wide, 3));
        assertEquals("", value(strings, 6)); assertEquals("", value(strings, 13));
        assertEquals(false, value(strings, 15));
    }

    @Test
    void nativeRepeatedOuterNestedMessageClearsExplicitZeroFalseAndEmptyString() throws Exception {
        // scene-nested: scene-duplicate-nested-explicit-zero = exact scene-all-nonempty + this suffix.
        var root = parse(ROOT, ALL_NONEMPTY + "ea540a0a041000180132007800");
        var strings = child(root, 1357); var wide = child(strings, 1);
        assertEquals(0xFEDCBA9800000020L, value(wide, 1)); assertU32(wide, 2, 0);
        assertEquals(true, value(wide, 3));
        assertEquals("", value(strings, 6)); assertEquals(false, value(strings, 15));
        assertEquals("嵌套🌙34133_24\0", value(strings, 13));
        assertThreeLists(child(root, 1964)); assertThreeLists(child(child(root, 1723), 1));
    }

    @Test
    void nativeVectorAndUnicodeFixturesKeepExactBitsAndNul() throws Exception {
        // scene-wait: nonempty-positions / unicode-nul. No outer vector business alias is asserted.
        var root = parse(ROOT, "720f0d0000a03f15000020c01d000070407a051d20008047da2f050d008000c3");
        assertFloatBits(child(root, 14), 1, 0x3FA00000);
        assertFloatBits(child(root, 14), 2, 0xC0200000);
        assertFloatBits(child(root, 14), 3, 0x40700000);
        assertFloatBits(child(root, 15), 3, 0x47800020);
        assertFloatBits(child(root, 763), 1, 0xC3008000);
        assertEquals("旅人🚀\0", value(parse(ROOT, "aa120be69785e4babaf09f9a8000"), 293));
    }

    @Test
    void nativeWrongWireBranchesPreserveUnknownDataWithoutAssigningTheKnownField() throws Exception {
        // scene-wait: wrong-wire-and-maximum-unknown-tag.
        var root = parse(ROOT, "520178f8ffffff0f80808080808080808001");
        assertU32(root, 10, 0);
        assertEquals("x", root.getUnknownFields().getField(10).getLengthDelimitedList().get(0).toStringUtf8());
        assertEquals(List.of(Long.MIN_VALUE), root.getUnknownFields().getField(536870911).getVarintList());
        // scene-nested: 66040-known-number-wrong-wire-skipped.
        var flag = parse("NAECHMKLBEI", "1dffffffff");
        assertEquals(false, value(flag, 3));
        assertEquals(List.of(-1), flag.getUnknownFields().getField(3).getFixed32List());
    }

    @Test
    void malformedPhysicalWireAndExplicitRecursionLimitsAreRejected() throws Exception {
        // scene-wait rejection fixture IDs: varint-truncated/zero-tag/varint-overflow,
        // truncated-string/truncated-nested/truncated-packed. Native/model provenance stays distinct.
        for (String wire : List.of("5080", "00", "5080808080808080808080", "aa1201", "72050d00", "6a0280")) {
            assertThrows(InvalidProtocolBufferException.class, () -> parse(ROOT, wire), wire);
        }
        // scene-nested: negative-child-length / truncated-child-input.
        for (String wire : List.of("0affffffff0f", "0a0208")) {
            assertThrows(InvalidProtocolBufferException.class, () -> parse("HJLMHFHHJKM", wire), wire);
        }
        // scene-nested: child-recursion-limit, explicit depth0 at known nested field1.
        var input = CodedInputStream.newInstance(hex("0a020801")); input.setRecursionLimit(0);
        assertThrows(InvalidProtocolBufferException.class,
                () -> DynamicMessage.parseFrom(descriptor("HJLMHFHHJKM"), input));
        // INTMAX-empty-child-fixture-accepted was accepted ONLY by the explicit native EOF model.
        // Java sees physical truncation here; this is not an assertion about native refill/EOF ABI.
        assertThrows(InvalidProtocolBufferException.class, () -> parse("HJLMHFHHJKM", "0affffffff07"));
    }

    private static Descriptor descriptor(String name) throws Exception {
        String outer = name.equals(ROOT) ? "PlayerEnterSceneNotifyOuterClass"
                : name.equals("BGDCNKAKGHI") ? "Client72SceneHandshakeNestedOuterClass"
                : "Client72EnterSceneNestedOuterClass";
        return (Descriptor) Class.forName("emu.grasscutter.net.proto." + outer + "$" + name)
                .getMethod("getDescriptor").invoke(null);
    }

    private static DynamicMessage parse(String name, String wire) throws Exception {
        return DynamicMessage.parseFrom(descriptor(name), hex(wire));
    }

    private static void assertTags(Descriptor descriptor, Integer... tags) {
        assertEquals(Set.of(tags), descriptor.getFields().stream().map(FieldDescriptor::getNumber)
                .collect(Collectors.toSet()), descriptor.getFullName());
    }

    private static void assertType(Descriptor descriptor, Type type, int... tags) {
        for (int tag : tags) {
            var field = descriptor.findFieldByNumber(tag);
            assertNotNull(field, descriptor.getFullName() + ":" + tag);
            assertEquals(type, field.getType(), descriptor.getFullName() + ":" + tag);
        }
    }

    private static void assertRepeated(Descriptor descriptor, Set<Integer> repeated) {
        for (var field : descriptor.getFields()) {
            assertEquals(repeated.contains(field.getNumber()), field.isRepeated(), field.getFullName());
            if (field.isRepeated()) assertTrue(field.isPacked(), field.getFullName());
        }
    }

    private static void assertChild(Descriptor owner, String name, int... tags) throws Exception {
        for (int tag : tags) {
            var field = owner.findFieldByNumber(tag);
            assertNotNull(field, owner.getFullName() + ":" + tag);
            assertEquals(Type.MESSAGE, field.getType());
            assertSame(descriptor(name), field.getMessageType(), owner.getFullName() + ":" + tag);
        }
    }

    private static Object value(Message message, int tag) {
        var field = message.getDescriptorForType().findFieldByNumber(tag);
        assertNotNull(field, message.getDescriptorForType().getFullName() + ":" + tag);
        return message.getField(field);
    }

    private static Message child(Message message, int tag) {
        return assertInstanceOf(Message.class, value(message, tag));
    }

    private static void assertU32(Message message, int tag, long expected) {
        assertEquals(expected, Integer.toUnsignedLong((Integer) value(message, tag)), "tag " + tag);
    }

    private static void assertFloatBits(Message message, int tag, int bits) {
        assertEquals(bits, Float.floatToRawIntBits((Float) value(message, tag)), "tag " + tag);
    }

    private static void assertList(Message message, int tag, long... expected) {
        var actual = ((List<?>) value(message, tag)).stream()
                .map(item -> Integer.toUnsignedLong((Integer) item)).toList();
        assertEquals(java.util.Arrays.stream(expected).boxed().toList(), actual, "tag " + tag);
    }

    private static void assertThreeLists(Message message) {
        assertList(message, 1, 0x12340018L, 0x80000018L);
        assertList(message, 6, 0x12340028L, 0x80000028L);
        assertList(message, 7, 0x12340020L, 0x80000020L);
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
