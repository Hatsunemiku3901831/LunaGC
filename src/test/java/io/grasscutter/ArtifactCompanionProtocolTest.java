package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Message;

import emu.grasscutter.net.packet.BasePacket;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Native reader/consumer fixtures: a snapshot can carry several candidates, or clear them all. */
class ArtifactCompanionProtocolTest {
    Message parse(String name, String hex) {
        try {
            var type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
            return (Message)
                    type.getMethod("parseFrom", byte[].class)
                            .invoke(null, (Object) HexFormat.of().parseHex(hex));
        } catch (ReflectiveOperationException failure) {
            return fail("Missing companion message: " + name, failure);
        }
    }

    Object field(Message message, String name) {
        var descriptor = message.getDescriptorForType().findFieldByName(name);
        assertNotNull(descriptor, name);
        return message.getField(descriptor);
    }

    @Test
    void snapshotHasTwoIndependentCandidateListsAndSeparateProgress() {
        var hex = "1a0b10ac021a026566520214161a0910ad021a016752011c6005";
        var message = parse("ReliquaryOfferCompanionNotify", hex);
        assertArrayEquals(HexFormat.of().parseHex(hex), message.toByteArray());
        assertEquals(5, field(message, "progress"));
        var list = (List<?>) field(message, "candidate_list");
        assertEquals(2, list.size());
        var first = (Message) list.get(0);
        assertEquals(300L, field(first, "reliquary_guid"));
        assertEquals(List.of(101, 102), field(first, "append_prop_id_list"));
        assertEquals(List.of(20, 22), field(first, "append_prop_type_list"));
        var second = (Message) list.get(1);
        assertEquals(301L, field(second, "reliquary_guid"));
        assertEquals(List.of(103), field(second, "append_prop_id_list"));
        assertEquals(List.of(28), field(second, "append_prop_type_list"));
    }

    @Test
    void nativeUnpackedListsAndUnsignedGuidAreAccepted() {
        var message = parse("ReliquaryOfferCandidate", "10ffffffffffffffffff011865186650145016");
        assertEquals(-1L, field(message, "reliquary_guid"));
        assertEquals(List.of(101, 102), field(message, "append_prop_id_list"));
        assertEquals(List.of(20, 22), field(message, "append_prop_type_list"));
        assertArrayEquals(
                HexFormat.of().parseHex("10ffffffffffffffffff011a02656652021416"),
                message.toByteArray());
    }

    @Test
    void progressKeepsAllUnsignedBits() {
        var message = parse("ReliquaryOfferCompanionNotify", "60ffffffff0f");
        assertEquals(0xffff_ffffL, Integer.toUnsignedLong((int) field(message, "progress")));
        assertTrue(((List<?>) field(message, "candidate_list")).isEmpty());
    }

    @Test
    void clearingSnapshotDoesNotInventAGuidZeroCandidateOrProgress() throws Exception {
        var type =
                Class.forName(
                        "emu.grasscutter.server.packet.send.PacketReliquaryOfferCompanionNotify");
        var packet = (BasePacket) type.getConstructor().newInstance();
        assertEquals(228, packet.getOpcode());
        assertArrayEquals(new byte[0], packet.getData());
        var empty = parse("ReliquaryOfferCompanionNotify", "");
        assertEquals(0, field(empty, "progress"));
        assertTrue(((List<?>) field(empty, "candidate_list")).isEmpty());
    }
}
