package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Message;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Current request writers, response readers, and the equipment page's named dust events. */
class ArtifactDustProtocolTest {
    Message parse(String name, String hex) {
        try {
            var type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
            return (Message)
                    type.getMethod("parseFrom", byte[].class)
                            .invoke(null, (Object) HexFormat.of().parseHex(hex));
        } catch (ReflectiveOperationException failure) {
            return fail("Missing verified dust message: " + name, failure);
        }
    }

    Object field(Message message, String name) {
        var descriptor = message.getDescriptorForType().findFieldByName(name);
        assertNotNull(descriptor, name);
        return message.getField(descriptor);
    }

    void bytes(String hex, Message message) {
        assertArrayEquals(HexFormat.of().parseHex(hex), message.toByteArray());
    }

    @Test
    void reshapeRequestContainsGuidAndFightPropertySelections() {
        var message = parse("ReliquaryDustReq", "30ac026a021416");
        assertEquals(300L, field(message, "reliquary_guid"));
        assertEquals(List.of(20, 22), field(message, "append_prop_type_list"));
        bytes("30ac026a021416", message);
    }

    @Test
    void unpackedSelectionsKeepAllUnsignedGuidBits() {
        var message = parse("ReliquaryDustReq", "30ffffffffffffffffff0168146816");
        assertEquals(-1L, field(message, "reliquary_guid"));
        assertEquals(List.of(20, 22), field(message, "append_prop_type_list"));
        bytes("30ffffffffffffffffff016a021416", message);
    }

    @Test
    void reshapeResponseEchoesTheArtifactAndItsError() {
        var message = parse("ReliquaryDustRsp", "50ac026008");
        assertEquals(300L, field(message, "reliquary_guid"));
        assertEquals(8, field(message, "retcode"));
        bytes("50ac026008", message);
    }

    @Test
    void selectionTrueAdoptsNewAndOmittedFalseKeepsOld() {
        var adopt = parse("ReliquaryDustSelectReq", "700178ac02");
        assertEquals(300L, field(adopt, "reliquary_guid"));
        assertEquals(true, field(adopt, "is_new"));
        bytes("700178ac02", adopt);
        var keep = parse("ReliquaryDustSelectReq", "78ac02");
        assertEquals(false, field(keep, "is_new"));
        assertEquals(300L, field(keep, "reliquary_guid"));
        bytes("78ac02", keep);
    }

    @Test
    void selectionResponseHasOnlyItsVerifiedErrorField() {
        var message = parse("ReliquaryDustSelectRsp", "2008");
        assertEquals(8, field(message, "retcode"));
        assertEquals(1, message.getDescriptorForType().getFields().size());
        bytes("2008", message);
    }

    @Test
    void namesMapToVerifiedNativePacketGetters() throws Exception {
        for (var entry :
                Map.of(
                                "ReliquaryDustReq",
                                9185,
                                "ReliquaryDustRsp",
                                9807,
                                "ReliquaryDustSelectReq",
                                28022,
                                "ReliquaryDustSelectRsp",
                                4210)
                        .entrySet()) {
            assertEquals(
                    entry.getValue(), PacketOpcodes.class.getField(entry.getKey()).getInt(null));
        }
    }

    @Test
    void responsePacketsPreserveTheRequestSequence() throws Exception {
        for (var entry :
                Map.of("ReliquaryDustRsp", 9807, "ReliquaryDustSelectRsp", 4210).entrySet()) {
            var message = parse(entry.getKey(), "");
            var type = Class.forName("emu.grasscutter.server.packet.send.Packet" + entry.getKey());
            var packet =
                    (BasePacket)
                            type.getConstructor(message.getClass(), int.class)
                                    .newInstance(message, 73);
            assertEquals(entry.getValue(), packet.getOpcode());
            assertEquals(
                    73,
                    emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead.parseFrom(
                                    packet.getHeader())
                            .getClientSequenceId());
            assertArrayEquals(new byte[0], packet.getData());
        }
    }
}
