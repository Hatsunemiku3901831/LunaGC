package io.grasscutter;

import static com.google.protobuf.Descriptors.FieldDescriptor.Type.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.packet.send.PacketEnterSceneDoneRsp;
import emu.grasscutter.server.packet.send.PacketEnterSceneReadyRsp;
import emu.grasscutter.server.packet.send.PacketPostEnterSceneRsp;
import emu.grasscutter.server.packet.send.PacketSceneInitFinishRsp;
import java.lang.reflect.InvocationTargetException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent CN72 native vectors; optional is a Java adapter, not recovered target syntax. */
class Protocol72SceneHandshakeTest {
    private record Field(int tag, FieldDescriptor.Type type, String name) {}
    private record Schema(String name, int command, Field[] fields) {}
    private record Response(String name, int tokenTag, int retcodeTag, long[] tokens,
                            int retcode, String[] wire) {}

    // Fixed target role/schema table from scene-handshake-observations.json.
    private static final Schema[] SCHEMAS = {
        new Schema("EnterSceneReadyReq", 5046, new Field[] {new Field(3, UINT32, "MCCMGLDOECH")}),
        new Schema("EnterSceneReadyRsp", 9948, new Field[] {
            new Field(2, UINT32, "MCCMGLDOECH"), new Field(12, INT32, "LDLIEKOAFBP")}),
        new Schema("SceneInitFinishReq", 4905, new Field[] {new Field(11, UINT32, "MCCMGLDOECH")}),
        new Schema("SceneInitFinishRsp", 21392, new Field[] {
            new Field(1, UINT32, "MCCMGLDOECH"), new Field(7, INT32, "LDLIEKOAFBP")}),
        new Schema("EnterSceneDoneReq", 1017, new Field[] {new Field(12, UINT32, "MCCMGLDOECH")}),
        new Schema("EnterSceneDoneRsp", 298, new Field[] {
            new Field(4, UINT32, "MCCMGLDOECH"), new Field(6, INT32, "LDLIEKOAFBP"),
            new Field(12, MESSAGE, "MFLHLEMFIGI")}),
        new Schema("PostEnterSceneReq", 1696, new Field[] {
            new Field(1, DOUBLE, "MHCBMOHFBCK"), new Field(6, UINT32, "MCCMGLDOECH")}),
        new Schema("PostEnterSceneRsp", 26760, new Field[] {
            new Field(2, UINT32, "MCCMGLDOECH"), new Field(6, INT32, "LDLIEKOAFBP")})
    };

    // Raw response tokens for logical tokens 0, 300, UINT32_MAX. Independent
    // native inverse arithmetic is documented in client72_scene_handshake.py;
    // these literals never call a production codec to construct expectations.
    private static final Response[] RESPONSES = {
        new Response("EnterSceneReadyRsp", 2, 12, new long[] {49546, 49374, 49549}, 37941,
            new String[] {"108a830360b5a802", "10de810360b5a802", "108d830360b5a802"}),
        new Response("SceneInitFinishRsp", 1, 7,
            new long[] {4294934352L, 4294934028L, 4294934353L}, -53688,
            new String[] {"08d0fefdff0f38c8dcfcffffffffffff01", "088cfcfdff0f38c8dcfcffffffffffff01",
                "08d1fefdff0f38c8dcfcffffffffffff01"}),
        new Response("EnterSceneDoneRsp", 4, 6, new long[] {14694, 14418, 4294883971L}, -20876,
            new String[] {"20e67230f4dcfeffffffffffff01", "20d27030f4dcfeffffffffffff01",
                "2083f5faff0f30f4dcfeffffffffffff01"}),
        new Response("PostEnterSceneRsp", 2, 6, new long[] {77568, 77788, 4294926857L}, -31949,
            new String[] {"1080de0430b386feffffffffffff01", "10dcdf0430b386feffffffffffff01",
                "1089c4fdff0f30b386feffffffffffff01"})
    };

    private static final String DONE_NESTED = "621d0a0f0d0000a03f15000020c01d00008047"
            + "18ffffffff0f58818080800820ffffffff0f30f4dcfeffffffffffff01";

    @BeforeAll
    static void initializeConfiguration() {
        Grasscutter.getConfig();
    }

    @Test
    void tenDescriptorsMatchTwentyNativeRawFields() throws Exception {
        int count = 0;
        for (var schema : SCHEMAS) {
            assertDescriptor(descriptor(schema.name()), schema.fields());
            count += schema.fields().length;
        }
        var done = descriptor("EnterSceneDoneRsp").findFieldByNumber(12).getMessageType();
        assertEquals("NFIGDMDEDBC", done.getName());
        assertDescriptor(done, new Field[] {
            new Field(1, MESSAGE, "DFDHCAIKMAI"), new Field(3, UINT32, "ADMHCDNJLAI"),
            new Field(11, UINT32, "LLHEBIHGDAE")});
        var position = done.findFieldByNumber(1).getMessageType();
        assertEquals("BGDCNKAKGHI", position.getName());
        assertDescriptor(position, new Field[] {
            new Field(1, FLOAT, "CJMJBINEBAG"), new Field(2, FLOAT, "ALOKJBKPLGA"),
            new Field(3, FLOAT, "MGICHKNKDKO")});
        assertEquals(20, count + done.getFields().size() + position.getFields().size());
    }

    @Test
    void eightCanonicalCommandsRetireThreeUnrelatedCollisions() throws Exception {
        for (var schema : SCHEMAS) {
            assertEquals(schema.command(), PacketOpcodes.class.getField(schema.name()).getInt(null));
            assertEquals(schema.name(), PacketOpcodesUtils.getOpcodeName(schema.command()));
        }
        for (var old : List.of("MonsterChessV2BattleDataNotify", "_MonsterChessV2BattleDataNotify", "DIICIBFPICB")) {
            assertTrue(PacketOpcodes.class.getField(old).getInt(null) < 0, old);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 300, -1})
    void fourResponseBuildersMatchNativeArithmeticAndFixedWire(int token) throws Exception {
        int sample = token == 0 ? 0 : token == 300 ? 1 : 2;
        var player = new SinkPlayer();
        player.setEnterSceneToken(token);
        BasePacket[] packets = {new PacketEnterSceneReadyRsp(player), new PacketSceneInitFinishRsp(player),
            new PacketEnterSceneDoneRsp(player), new PacketPostEnterSceneRsp(player)};
        for (int i = 0; i < RESPONSES.length; i++) {
            var expected = RESPONSES[i];
            assertEquals(new int[] {9948, 21392, 298, 26760}[i], packets[i].getOpcode());
            assertArrayEquals(hex(expected.wire()[sample]), packets[i].getData(), expected.name());
            var parsed = parse(expected.name(), packets[i].getData());
            assertEquals(expected.tokens()[sample], Integer.toUnsignedLong((Integer) field(parsed, expected.tokenTag())));
            assertEquals(expected.retcode(), field(parsed, expected.retcodeTag()));
            assertTrue(parsed.hasField(parsed.getDescriptorForType().findFieldByNumber(expected.tokenTag())));
            assertTrue(parsed.hasField(parsed.getDescriptorForType().findFieldByNumber(expected.retcodeTag())));
        }
    }

    @Test
    void logicalTokensWhoseEncodingIsZeroStillEmitTheNativeTokenField() throws Exception {
        // Native raw-zero reader vectors decode to these logical tokens. In
        // particular Init's 69424 is a normal generated scene-token value.
        int[] logical = {0xFFFF418A, 69424, 14714, 0xFFFF5100};
        String[] wire = {"100060b5a802", "080038c8dcfcffffffffffff01",
            "200030f4dcfeffffffffffff01", "100030b386feffffffffffff01"};
        for (int i = 0; i < RESPONSES.length; i++) {
            var player = new SinkPlayer();
            player.setEnterSceneToken(logical[i]);
            BasePacket packet = switch (i) {
                case 0 -> new PacketEnterSceneReadyRsp(player);
                case 1 -> new PacketSceneInitFinishRsp(player);
                case 2 -> new PacketEnterSceneDoneRsp(player);
                default -> new PacketPostEnterSceneRsp(player);
            };
            assertArrayEquals(hex(wire[i]), packet.getData(), RESPONSES[i].name());
            var parsed = parse(RESPONSES[i].name(), packet.getData());
            var token = parsed.getDescriptorForType().findFieldByNumber(RESPONSES[i].tokenTag());
            assertTrue(parsed.hasField(token));
            assertEquals(0, parsed.getField(token));
        }
    }

    @Test
    void everyTokenAndRetcodeDistinguishesAbsentFromExplicitRawZero() throws Exception {
        String[] tokenZeros = {"1800", "1000", "5800", "0800", "6000", "2000", "3000", "1000"};
        for (int i = 0; i < SCHEMAS.length; i++) {
            var schema = SCHEMAS[i];
            var token = descriptor(schema.name()).findFieldByName("MCCMGLDOECH");
            var missing = parse(schema.name(), new byte[0]);
            var zero = parse(schema.name(), hex(tokenZeros[i]));
            assertFalse(missing.hasField(token), schema.name());
            assertTrue(zero.hasField(token), schema.name());
            assertEquals(0, zero.getField(token));
            assertArrayEquals(hex(tokenZeros[i]), zero.toByteArray());
        }
        String[] retcodeZeros = {"6000", "3800", "3000", "3000"};
        for (int i = 0; i < RESPONSES.length; i++) {
            var response = RESPONSES[i];
            var code = descriptor(response.name()).findFieldByNumber(response.retcodeTag());
            assertFalse(parse(response.name(), new byte[0]).hasField(code));
            var zero = parse(response.name(), hex(retcodeZeros[i]));
            assertTrue(zero.hasField(code));
            assertEquals(0, zero.getField(code));
            // Native omission keeps encoded-success defaults listed above;
            // Java's zero getter alone cannot distinguish that from raw zero.
            assertNotEquals(0, response.retcode());
            assertArrayEquals(hex(retcodeZeros[i]), zero.toByteArray());
        }
    }

    @Test
    void nativeRequestsAndUnknownPostDoublePreserveIndependentBits() throws Exception {
        String[][] vectors = {
            {"EnterSceneReadyReq", "18c4e904", "79044", "18bdc6fdff0f", "4294927165"},
            {"SceneInitFinishReq", "58c09e05", "85824", "58d9c402", "41561"},
            {"EnterSceneDoneReq", "60abb4feff0f", "4294941227", "60c2b3fdff0f", "4294924738"},
            {"PostEnterSceneReq", "30ccaafeff0f", "4294939980", "09010000000000404330c786fcff0f", "4294902599"}
        };
        for (var vector : vectors) {
            var token = descriptor(vector[0]).findFieldByName("MCCMGLDOECH");
            for (int i : new int[] {1, 3}) {
                var parsed = parse(vector[0], hex(vector[i]));
                assertEquals(Long.parseLong(vector[i + 1]), Integer.toUnsignedLong((Integer) parsed.getField(token)));
                assertArrayEquals(hex(vector[i]), parsed.toByteArray());
            }
        }
        var post = parse("PostEnterSceneReq", hex("09010000000000404330c786fcff0f"));
        assertEquals(0x4340000000000001L, Double.doubleToRawLongBits((Double) field(post, 1)));
    }

    @Test
    void doneNonzeroNestedFloatsAndUnsignedValuesMatchNativeReader() throws Exception {
        var done = parse("EnterSceneDoneRsp", hex(DONE_NESTED));
        var child = (Message) field(done, 12);
        var position = (Message) field(child, 1);
        assertEquals(1.25f, field(position, 1));
        assertEquals(-2.5f, field(position, 2));
        assertEquals(65536f, field(position, 3));
        assertEquals(4294967295L, Integer.toUnsignedLong((Integer) field(child, 3)));
        assertEquals(2147483649L, Integer.toUnsignedLong((Integer) field(child, 11)));
        assertEquals(-20876, field(done, 6));
        assertEquals(4294967295L, Integer.toUnsignedLong((Integer) field(done, 4)));
    }

    @Test
    void duplicateNestedExplicitZeroOverwritesWhileOtherFloatsRemain() throws Exception {
        // Exact native done-duplicate-nested-explicit-zero fixture. Ordinary
        // proto3 message merging must not discard the second child's raw zeros.
        var done = parse("EnterSceneDoneRsp", hex(DONE_NESTED + "620b0a050d0000000018005800"));
        var child = (Message) field(done, 12);
        var position = (Message) field(child, 1);
        assertEquals(0f, field(position, 1));
        assertEquals(-2.5f, field(position, 2));
        assertEquals(65536f, field(position, 3));
        assertEquals(0, field(child, 3));
        assertEquals(0, field(child, 11));
    }

    @Test
    void signedErrorsDuplicatesAndWrongWireMatchIndependentNativeInputs() throws Exception {
        String[] maximum = {"10ffffffff0f60ffffffffffffffffff01", "08ffffffff0f38ffffffffffffffffff01",
            "20ffffffff0f30ffffffffffffffffff01", "10ffffffff0f30ffffffffffffffffff01"};
        String[] duplicates = {"1011100060116000", "0811080038113800", "2011200030113000", "1011100030113000"};
        String[] wrong = {"120100620100", "0a01003a0100", "220100320100", "120100320100"};
        for (int i = 0; i < RESPONSES.length; i++) {
            var response = RESPONSES[i];
            var max = parse(response.name(), hex(maximum[i]));
            assertEquals(-1, field(max, response.retcodeTag()));
            assertEquals(4294967295L, Integer.toUnsignedLong((Integer) field(max, response.tokenTag())));
            var repeated = parse(response.name(), hex(duplicates[i]));
            assertEquals(0, field(repeated, response.retcodeTag()));
            assertEquals(0, field(repeated, response.tokenTag()));
            assertTrue(repeated.hasField(repeated.getDescriptorForType().findFieldByNumber(response.retcodeTag())));
            var unknown = parse(response.name(), hex(wrong[i]));
            assertFalse(unknown.hasField(unknown.getDescriptorForType().findFieldByNumber(response.tokenTag())));
            assertFalse(unknown.hasField(unknown.getDescriptorForType().findFieldByNumber(response.retcodeTag())));
            assertFalse(unknown.getUnknownFields().asMap().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "8080", "0a0378", "0d0000"})
    void malformedWireIsRejectedForEveryCanonicalMessage(String wire) {
        for (var schema : SCHEMAS) {
            assertThrows(InvalidProtocolBufferException.class, () -> parse(schema.name(), hex(wire)), schema.name());
        }
    }

    private static void assertDescriptor(Descriptor descriptor, Field[] fields) {
        assertEquals(fields.length, descriptor.getFields().size(), descriptor.getName());
        for (var expected : fields) {
            var actual = descriptor.findFieldByNumber(expected.tag());
            assertNotNull(actual, descriptor.getName() + " tag " + expected.tag());
            assertEquals(expected.name(), actual.getName());
            assertEquals(expected.type(), actual.getType());
            assertFalse(actual.isRepeated());
            if (expected.type() != MESSAGE) {
                assertTrue(actual.toProto().getProto3Optional(), "Java raw presence adapter " + descriptor.getName());
                assertTrue(actual.hasPresence());
            } else if (expected.type() == MESSAGE) {
                assertTrue(actual.hasPresence());
            }
        }
    }

    private static Descriptor descriptor(String name) throws Exception {
        return (Descriptor) messageClass(name).getMethod("getDescriptor").invoke(null);
    }

    private static Class<?> messageClass(String name) throws ClassNotFoundException {
        return Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
    }

    private static Message parse(String name, byte[] wire) throws Exception {
        try {
            return (Message) messageClass(name).getMethod("parseFrom", byte[].class).invoke(null, (Object) wire);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static Object field(Message message, int tag) {
        return message.getField(message.getDescriptorForType().findFieldByNumber(tag));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static final class SinkPlayer extends Player {
        @Override public void unfreezeUnlockedScenePoints() {}
    }
}
