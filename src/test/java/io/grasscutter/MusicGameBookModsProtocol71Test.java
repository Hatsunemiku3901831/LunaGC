package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

/** Native codecs 0x14f7718f0/0x14b418d10 and response reader 0x14b418d70. */
class MusicGameBookModsProtocol71Test {
    private Message prototype(String name) throws Exception {
        return (Message)
                Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                        .getMethod("getDefaultInstance")
                        .invoke(null);
    }

    private Message parse(String name, String bytes) throws Exception {
        return prototype(name).getParserForType().parseFrom(HexFormat.of().parseHex(bytes));
    }

    @ParameterizedTest
    @CsvSource({"MusicGameBookSaveModsReq,5759", "MusicGameBookSaveModsRsp,238"})
    void packetNumbersMatchTheNativeLiteralGetters(String name, int opcode) throws Exception {
        assertEquals(opcode, PacketOpcodes.class.getField(name).getInt(null));
    }

    @ParameterizedTest
    @CsvSource({
        "MusicGameBookSaveModsReq,JNJDOLEBBOO,9,UINT32,true",
        "MusicGameBookSaveModsRsp,JNJDOLEBBOO,12,UINT32,true",
        "MusicGameBookSaveModsRsp,retcode,14,INT32,false"
    })
    void onlyTheNativeFieldsHaveTheirNativeTypes(
            String name, String field, int number, String kind, boolean repeated) throws Exception {
        var descriptor = prototype(name).getDescriptorForType().findFieldByName(field);
        assertNotNull(descriptor);
        assertEquals(number, descriptor.getNumber());
        assertEquals(Descriptors.FieldDescriptor.Type.valueOf(kind), descriptor.getType());
        assertEquals(repeated, descriptor.isRepeated());
        if (repeated) assertTrue(descriptor.isPacked());
    }

    @ParameterizedTest
    @CsvSource({"MusicGameBookSaveModsReq,1", "MusicGameBookSaveModsRsp,2"})
    void noInventedFieldsAreDeclaredAndUnknownFieldsSurvive(String name, int count)
            throws Exception {
        var value = parse(name, "f80111");
        assertEquals(count, value.getDescriptorForType().getFields().size());
        assertEquals(List.of(17L), value.getUnknownFields().getField(31).getVarintList());
        assertArrayEquals(HexFormat.of().parseHex("f80111"), value.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({"MusicGameBookSaveModsReq,4a,48,9", "MusicGameBookSaveModsRsp,62,60,12"})
    void packedAndUnpackedWordsRetainAllUnsignedBits(
            String name, String packedTag, String wordTag, int field) throws Exception {
        var packedBytes = packedTag + "08008001ffffffff0f";
        var unpackedBytes = wordTag + "00" + wordTag + "8001" + wordTag + "ffffffff0f";
        var packed = parse(name, packedBytes);
        var unpacked = parse(name, unpackedBytes);
        var descriptor = packed.getDescriptorForType().findFieldByNumber(field);
        assertEquals(List.of(0, 128, -1), packed.getField(descriptor));
        assertEquals(packed, unpacked);
        assertTrue(packed.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(HexFormat.of().parseHex(packedBytes), packed.toByteArray());
    }

    @Test
    void anEmptyRequestRepresentsClearingTheSelection() throws Exception {
        var request = parse("MusicGameBookSaveModsReq", "");
        assertEquals(
                List.of(), request.getField(request.getDescriptorForType().findFieldByNumber(9)));
        assertEquals(0, request.getSerializedSize());
    }

    @Test
    void negativeErrorsUseTheNativeSignedWordEncoding() throws Exception {
        var wire = "70ffffffffffffffffff01";
        var response = parse("MusicGameBookSaveModsRsp", wire);
        assertEquals(-1, response.getField(response.getDescriptorForType().findFieldByNumber(14)));
        assertTrue(response.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(HexFormat.of().parseHex(wire), response.toByteArray());
    }
}
