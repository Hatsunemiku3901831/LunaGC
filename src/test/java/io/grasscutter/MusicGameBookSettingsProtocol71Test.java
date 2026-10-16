package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

/** Independent native writers: request 0x14ebec3d0, response 0x14b9da500/0x14b9da9c0. */
class MusicGameBookSettingsProtocol71Test {
    private static final String SETTINGS = "100118ac0220ffffffff0f40016001680170ffffffff0f";

    private Message prototype(String name) throws Exception {
        return (Message)
                Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                        .getMethod("getDefaultInstance")
                        .invoke(null);
    }

    private Message parse(String name, String bytes) throws Exception {
        return prototype(name).getParserForType().parseFrom(HexFormat.of().parseHex(bytes));
    }

    private Object value(Message message, int number) {
        var field = message.getDescriptorForType().findFieldByNumber(number);
        assertNotNull(field, "Native field " + number);
        return message.getField(field);
    }

    @ParameterizedTest
    @CsvSource({"MusicGameBookSaveSettingsReq,25611", "MusicGameBookSaveSettingsRsp,7502"})
    void packetNumbersComeFromTheTwoNativeLiteralGetters(String name, int opcode) throws Exception {
        assertEquals(opcode, PacketOpcodes.class.getField(name).getInt(null));
    }

    @ParameterizedTest
    @CsvSource({
        "MusicGameBookSaveSettingsReq,NKFEGNKDBBK,14,MESSAGE",
        "MusicGameBookSaveSettingsRsp,NKFEGNKDBBK,3,MESSAGE",
        "MusicGameBookSaveSettingsRsp,retcode,14,INT32"
    })
    void fieldsHaveTheNativeNumbersAndTypes(String name, String field, int number, String kind)
            throws Exception {
        var descriptor = prototype(name).getDescriptorForType().findFieldByName(field);
        assertNotNull(descriptor);
        assertEquals(number, descriptor.getNumber());
        assertEquals(Descriptors.FieldDescriptor.Type.valueOf(kind), descriptor.getType());
        assertFalse(descriptor.isRepeated());
        if (kind.equals("MESSAGE"))
            assertEquals("MusicGameBookSettings", descriptor.getMessageType().getName());
    }

    @Test
    void requestNestsEveryCurrentSettingWithUnsignedWords() throws Exception {
        var request = parse("MusicGameBookSaveSettingsReq", "7217" + SETTINGS);
        var settings = (Message) value(request, 14);
        assertEquals(true, value(settings, 2));
        assertEquals(300, value(settings, 3));
        assertEquals(-1, value(settings, 4));
        assertEquals(true, value(settings, 8));
        assertEquals(true, value(settings, 12));
        assertEquals(true, value(settings, 13));
        assertEquals(-1, value(settings, 14));
        assertTrue(request.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(HexFormat.of().parseHex("7217" + SETTINGS), request.toByteArray());
    }

    @Test
    void responseHasItsOwnSettingsTagAndASignedError() throws Exception {
        var wire = "1a17" + SETTINGS + "70ffffffffffffffffff01";
        var response = parse("MusicGameBookSaveSettingsRsp", wire);
        assertEquals(-1, value(response, 14));
        assertEquals(-1, value((Message) value(response, 3), 14));
        assertTrue(response.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(HexFormat.of().parseHex(wire), response.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({"MusicGameBookSaveSettingsReq,1", "MusicGameBookSaveSettingsRsp,2"})
    void onlyNativeFieldsAreDeclaredAndFutureFieldsSurvive(String name, int count)
            throws Exception {
        var message = parse(name, "f80111");
        assertEquals(count, message.getDescriptorForType().getFields().size());
        assertEquals(List.of(17L), message.getUnknownFields().getField(31).getVarintList());
        assertArrayEquals(HexFormat.of().parseHex("f80111"), message.toByteArray());
    }

    @Test
    void missingAndExplicitEmptySettingsHaveDifferentPresence() throws Exception {
        var empty = parse("MusicGameBookSaveSettingsReq", "");
        var explicit = parse("MusicGameBookSaveSettingsReq", "7200");
        var field = explicit.getDescriptorForType().findFieldByNumber(14);
        assertFalse(empty.hasField(field));
        assertTrue(explicit.hasField(field));
    }
}
