package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;

import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Raw wire fixtures from the supplied client, independent of the new generated builders. */
class InvestigationProtocolTest {
    private record Fixture(String message, Map<String, Object> expected, byte[] wire) {}

    private static Map<Integer, Object> fields(Object... pairs) {
        var result = new LinkedHashMap<Integer, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((Integer) pairs[i], pairs[i + 1]);
        return result;
    }

    private static byte[] wire(Map<Integer, Object> fields) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (var entry : fields.entrySet()) {
            if (entry.getValue() instanceof byte[] child)
                output.writeByteArray(entry.getKey(), child);
            else output.writeUInt32(entry.getKey(), (Integer) entry.getValue());
        }
        output.flush();
        return bytes.toByteArray();
    }

    private static byte[] target(int state) throws Exception {
        // Native HPDFKIKJMCJ: offsets 0x18/1c/20/24/28 and tags 5/8/6/2/13.
        return wire(fields(5, 10004, 8, 6000038, 6, state, 2, 7, 13, 12));
    }

    private static byte[] chapter(int state) throws Exception {
        // Native CNCJMKCKFPE: offsets 0x18/1c/20/24 and tags 1/7/15/13.
        return wire(fields(1, 12, 7, 10004, 15, state, 13, 7));
    }

    private static Stream<Fixture> fixtures() throws Exception {
        var target =
                Map.<String, Object>of(
                        "investigation_id",
                        10004,
                        "quest_id",
                        6000038,
                        "state",
                        2,
                        "progress",
                        7,
                        "total_progress",
                        12);
        var chapter =
                Map.<String, Object>of(
                        "id", 10004, "state", 2, "progress", 7, "total_progress", 12);
        return Stream.of(
                new Fixture("Investigation", chapter, chapter(2)),
                new Fixture("InvestigationTarget", target, target(2)),
                new Fixture(
                        "PlayerInvestigationAllInfoNotify",
                        Map.of(
                                "investigation_list",
                                List.of(chapter),
                                "investigation_target_list",
                                List.of(target)),
                        wire(fields(2, chapter(2), 10, target(2)))),
                new Fixture(
                        "PlayerInvestigationNotify",
                        Map.of("investigation_list", List.of(chapter)),
                        wire(fields(6, chapter(2)))),
                new Fixture(
                        "PlayerInvestigationTargetNotify",
                        Map.of("investigation_target_list", List.of(target)),
                        wire(fields(1, target(2)))),
                new Fixture(
                        "TakeInvestigationTargetRewardReq",
                        Map.of("quest_id", 6000038),
                        wire(fields(9, 6000038))),
                new Fixture(
                        "TakeInvestigationTargetRewardRsp",
                        Map.of("quest_id", 6000038, "retcode", 5),
                        wire(fields(14, 6000038, 9, 5))),
                new Fixture(
                        "TakeInvestigationRewardRsp",
                        Map.of("id", 10004, "retcode", 5),
                        wire(fields(3, 10004, 10, 5))));
    }

    private static Message parse(String message, byte[] wire) throws Exception {
        var outer = Class.forName("emu.grasscutter.net.proto." + message + "OuterClass$" + message);
        return (Message) outer.getMethod("parseFrom", byte[].class).invoke(null, (Object) wire);
    }

    private static void values(Message message, Map<String, Object> expected) {
        for (var entry : expected.entrySet()) {
            var field = message.getDescriptorForType().findFieldByName(entry.getKey());
            assertNotNull(field, entry.getKey());
            var actual = message.getField(field);
            if (entry.getValue() instanceof List<?> children) {
                var messages = (List<?>) actual;
                assertEquals(children.size(), messages.size());
                for (int i = 0; i < children.size(); i++) {
                    @SuppressWarnings("unchecked")
                    var child = (Map<String, Object>) children.get(i);
                    values((Message) messages.get(i), child);
                }
            } else if (actual instanceof com.google.protobuf.Descriptors.EnumValueDescriptor value)
                assertEquals(entry.getValue(), value.getNumber(), entry.getKey());
            else assertEquals(entry.getValue(), actual, entry.getKey());
        }
        assertTrue(message.getUnknownFields().asMap().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void decodesNativeWireAndPreservesItsMeaningOnRoundTrip(Fixture fixture) throws Exception {
        var decoded = parse(fixture.message(), fixture.wire());
        values(decoded, fixture.expected());
        values(parse(fixture.message(), decoded.toByteArray()), fixture.expected());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void preservesEachNativeStateValue(int state) throws Exception {
        var target = parse("InvestigationTarget", target(state));
        var chapter = parse("Investigation", chapter(state));
        var targetState = target.getDescriptorForType().findFieldByName("state");
        var chapterState = chapter.getDescriptorForType().findFieldByName("state");
        assertEquals(
                state,
                ((com.google.protobuf.Descriptors.EnumValueDescriptor) target.getField(targetState))
                        .getNumber());
        assertEquals(
                state,
                ((com.google.protobuf.Descriptors.EnumValueDescriptor)
                                chapter.getField(chapterState))
                        .getNumber());
    }

    @Test
    void targetRewardOpcodeMatchesTheClientsLiteralGetter() {
        assertEquals(2703, PacketOpcodes.TakeInvestigationTargetRewardReq);
        assertEquals(8841, PacketOpcodes.TakeInvestigationRewardReq);
    }

    @Test
    void rejectsATruncatedRequestInsteadOfTreatingItAsAClaim() throws Exception {
        var failure =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                parse(
                                        "TakeInvestigationTargetRewardReq",
                                        new byte[] {0x48, (byte) 0x80}));
        assertInstanceOf(
                com.google.protobuf.InvalidProtocolBufferException.class, failure.getCause());
    }
}
